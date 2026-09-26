package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.Protocol
import com.apollof.protocoltracker.domain.model.compoundOrder
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.OccurrenceRef
import com.apollof.protocoltracker.domain.schedule.PhaseTimeline
import com.apollof.protocoltracker.domain.schedule.SlotTimes
import com.apollof.protocoltracker.domain.schedule.adherence
import com.apollof.protocoltracker.domain.schedule.describeSchedule
import com.apollof.protocoltracker.domain.schedule.occurrences
import com.apollof.protocoltracker.domain.schedule.parseOccurrenceKey
import com.apollof.protocoltracker.domain.schedule.planFigures
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.domain.units.formatNumber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Everything in an export, already formatted; rendered as Markdown or HTML. */
data class Report(
    val generatedAt: Instant,
    val zone: ZoneId,
    val from: LocalDate,
    val to: LocalDate,
    val plan: List<ReportPhase>,
    val adherence: List<ReportAdherence>,
    val bloodPressure: BloodPressureSummary?,
    val days: List<ReportDay>,
)

data class ReportPhase(val name: String, val dates: String, val notes: String, val items: List<ReportPlanItem>)

data class ReportPlanItem(
    val compound: String,
    val category: String,
    /** "500 mg per week". */
    val amount: String,
    /** "250 mg · 1.0 mL". */
    val perDose: String,
    val schedule: String,
    val notes: String,
)

data class ReportAdherence(val compound: String, val scheduled: Int, val taken: Int, val skipped: Int)

data class BloodPressureSummary(val readings: Int, val averageSystolic: Int, val averageDiastolic: Int, val lowest: String, val highest: String)

data class ReportDay(val date: LocalDate, val entries: List<ReportEntry>)

sealed interface ReportEntry {
    val time: LocalTime

    data class Dose(
        override val time: LocalTime,
        val compound: String,
        val amount: String,
        /** "taken", "skipped" or "missed". */
        val status: String,
        val planned: String?,
        val partOfDay: String?,
        val note: String,
    ) : ReportEntry

    data class BloodPressure(override val time: LocalTime, val systolic: Int, val diastolic: Int, val pulse: Int?, val note: String) : ReportEntry

    data class Note(override val time: LocalTime, val text: String) : ReportEntry
}

object ReportBuilder {
    /**
     * Builds a report for the days [from]..[to] (inclusive). Doses scheduled on earlier days that have no log
     * are listed as missed; today's open doses are not. [logs] must be all logs (interval schedules restart
     * from the last taken dose).
     */
    fun build(
        protocol: Protocol,
        logs: List<DoseLog>,
        journal: List<JournalEntry>,
        from: LocalDate,
        to: LocalDate,
        now: Instant,
        zone: ZoneId,
        slotTimes: SlotTimes = SlotTimes.DEFAULT,
        locale: Locale = Locale.getDefault(),
    ): Report {
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        val today = now.atZone(zone).toLocalDate()
        val entries = ArrayList<Pair<LocalDate, ReportEntry>>()
        fun local(at: Instant) = at.atZone(zone)

        for (log in logs) {
            if (log.takenAt < start || log.takenAt >= end) continue
            val t = local(log.takenAt)
            val partOfDay = log.occurrenceKey?.let(::parseOccurrenceKey)?.let { (it as? OccurrenceRef.Slotted)?.slot?.label }
            entries += t.toLocalDate() to ReportEntry.Dose(
                time = t.toLocalTime().withSecond(0).withNano(0),
                compound = log.snapshot.displayName,
                amount = describeDose(log.amount, log.snapshot.baseUnit, log.snapshot.formulation),
                status = if (log.status == LogStatus.TAKEN) "taken" else "skipped",
                planned = log.plannedAmount?.takeIf { log.adjusted }?.let { describeDose(it, log.snapshot.baseUnit, log.snapshot.formulation) },
                partOfDay = partOfDay,
                note = log.note,
            )
        }
        val logged = logs.mapNotNullTo(HashSet()) { it.occurrenceKey }
        val anchors = IntervalAnchors.from(logs)
        val missedEnd = minOf(end, today.atStartOfDay(zone).toInstant())
        if (missedEnd > start) for (occ in occurrences(protocol.phases, protocol.items, start, missedEnd, zone, anchors, slotTimes)) {
            if (occ.key in logged) continue
            val compound = protocol.compounds[occ.item.compoundId] ?: continue
            val t = local(occ.at)
            entries += occ.localDate to ReportEntry.Dose(
                time = t.toLocalTime().withSecond(0).withNano(0),
                compound = compound.displayName,
                amount = describeDose(occ.dose, compound.baseUnit, occ.item.formulation),
                status = "missed",
                planned = null,
                partOfDay = occ.slot?.label,
                note = "",
            )
        }
        val readings = ArrayList<JournalEntry.BloodPressure>()
        for (entry in journal) {
            if (entry.at < start || entry.at >= end) continue
            val t = local(entry.at)
            val time = t.toLocalTime().withSecond(0).withNano(0)
            entries += t.toLocalDate() to when (entry) {
                is JournalEntry.BloodPressure -> ReportEntry.BloodPressure(time, entry.systolic, entry.diastolic, entry.pulse, entry.note)
                    .also { readings += entry }
                is JournalEntry.Note -> ReportEntry.Note(time, entry.text)
            }
        }
        val days = entries.groupBy({ it.first }, { it.second }).toSortedMap()
            .map { (date, list) -> ReportDay(date, list.sortedBy { it.time }) }

        return Report(
            generatedAt = now,
            zone = zone,
            from = from,
            to = to,
            plan = planSection(protocol, locale),
            adherence = adherence(protocol.phases, protocol.items, logs, start, minOf(end, today.atStartOfDay(zone).toInstant()), now, zone, anchors, slotTimes)
                .mapNotNull { a ->
                    val item = protocol.items.firstOrNull { it.id == a.itemId } ?: return@mapNotNull null
                    val compound = protocol.compounds[item.compoundId] ?: return@mapNotNull null
                    ReportAdherence(compound.displayName, a.scheduled, a.taken, a.skipped)
                }.sortedBy { it.compound },
            bloodPressure = readings.takeIf { it.isNotEmpty() }?.let { r ->
                val low = r.minWith(compareBy({ it.systolic }, { it.diastolic }))
                val high = r.maxWith(compareBy({ it.systolic }, { it.diastolic }))
                BloodPressureSummary(
                    readings = r.size,
                    averageSystolic = Math.round(r.map { it.systolic }.average()).toInt(),
                    averageDiastolic = Math.round(r.map { it.diastolic }.average()).toInt(),
                    lowest = "${low.systolic}/${low.diastolic}",
                    highest = "${high.systolic}/${high.diastolic}",
                )
            },
            days = days,
        )
    }

    private fun planSection(protocol: Protocol, locale: Locale): List<ReportPhase> {
        fun items(phaseId: String?) = protocol.items.filter { it.phaseId == phaseId }
            .mapNotNull { item -> protocol.compounds[item.compoundId]?.let { item to it } }
            .sortedWith(compareBy(compoundOrder) { it.second })
            .map { (item, compound) ->
                val f = planFigures(item, compound, locale)
                ReportPlanItem(
                    compound = compound.displayName,
                    category = compound.category.label,
                    amount = "${f.total} ${f.totalLabel}",
                    perDose = listOfNotNull(f.perDose, f.detail).joinToString(" · "),
                    schedule = describeSchedule(item.schedule, locale),
                    notes = listOfNotNull(item.notes.takeIf { it.isNotBlank() }, "paused".takeIf { !item.enabled }).joinToString("; "),
                )
            }
        val timeline = PhaseTimeline(protocol.phases)
        val phases = timeline.phases.map { phase ->
            val end = timeline.effectiveEnd(phase)
            ReportPhase(phase.name.ifBlank { "Unnamed phase" }, "${phase.startDate} – ${end ?: "open"}", phase.notes, items(phase.id))
        }
        val always = items(null)
        return (if (always.isEmpty()) emptyList() else listOf(ReportPhase("All phases", "", "", always))) + phases
    }
}

private fun String.oneLine(): String = replace(Regex("\\s*\\n\\s*"), " ").trim()

/** AI-friendly export: plain Markdown with ISO dates and one entry per line. */
object MarkdownReport {
    fun render(r: Report): String = buildString {
        appendLine("# ProtocolTracker export")
        appendLine()
        appendLine("- Generated: ${r.generatedAt.atZone(r.zone).toLocalDateTime().withNano(0)} (${r.zone.id})")
        appendLine("- Range: ${r.from} to ${r.to}")
        appendLine("- Times are local to ${r.zone.id}. Dose lines read `time · compound · amount · status`, then optional `planned X` (amount was adjusted), part of day and note.")
        appendLine("- Status is taken, skipped or missed (scheduled on an earlier day and never logged). Blood pressure is in mmHg, pulse in bpm.")
        appendLine("- Doses are what was logged in the app; estimated blood levels are not included.")
        appendLine()
        appendLine("## Plan")
        if (r.plan.isEmpty()) appendLine("No plan items.")
        for (phase in r.plan) {
            appendLine()
            appendLine("### ${phase.name}${if (phase.dates.isNotEmpty()) " (${phase.dates})" else ""}")
            if (phase.notes.isNotBlank()) appendLine(phase.notes.oneLine())
            if (phase.items.isEmpty()) { appendLine("No items."); continue }
            appendLine()
            appendLine("| Compound | Category | Amount | Per dose | Schedule | Notes |")
            appendLine("|---|---|---|---|---|---|")
            for (i in phase.items) {
                appendLine(listOf(i.compound, i.category, i.amount, i.perDose, i.schedule, i.notes).joinToString(" | ", "| ", " |") { it.cell() })
            }
        }
        appendLine()
        appendLine("## Summary")
        if (r.adherence.isEmpty() && r.bloodPressure == null) appendLine("Nothing scheduled or measured in this range.")
        for (a in r.adherence) appendLine("- ${a.compound}: ${a.taken} of ${a.scheduled} scheduled doses taken, ${a.skipped} skipped")
        r.bloodPressure?.let {
            appendLine("- Blood pressure: ${it.readings} readings, average ${it.averageSystolic}/${it.averageDiastolic} mmHg, lowest ${it.lowest}, highest ${it.highest}")
        }
        appendLine()
        appendLine("## Journal")
        if (r.days.isEmpty()) appendLine("No entries in this range.")
        for (day in r.days) {
            appendLine()
            appendLine("### ${day.date} (${day.date.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }})")
            for (e in day.entries) appendLine("- " + line(e))
        }
    }

    fun line(e: ReportEntry): String = when (e) {
        is ReportEntry.Dose -> listOfNotNull(
            e.time.toString(), e.compound, e.amount, e.status,
            e.planned?.let { "planned $it" }, e.partOfDay?.lowercase(), e.note.takeIf { it.isNotBlank() }?.let { "note: ${it.oneLine()}" },
        ).joinToString(" · ")
        is ReportEntry.BloodPressure -> listOfNotNull(
            e.time.toString(), "Blood pressure", "${e.systolic}/${e.diastolic} mmHg", e.pulse?.let { "pulse $it" },
            e.note.takeIf { it.isNotBlank() }?.let { "note: ${it.oneLine()}" },
        ).joinToString(" · ")
        is ReportEntry.Note -> "${e.time} · Note · ${e.text.oneLine()}"
    }

    private fun String.cell(): String = oneLine().replace("|", "\\|").ifEmpty { "–" }
}

/** Readable export: one self-contained HTML page that prints cleanly. */
object HtmlReport {
    fun render(r: Report): String = buildString {
        append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<title>ProtocolTracker report ${r.from} – ${r.to}</title><style>").append(CSS).append("</style></head><body><main>")
        append("<header><p class=\"meta\">${esc("${r.from} – ${r.to} · generated ${r.generatedAt.atZone(r.zone).toLocalDate()} · times in ${r.zone.id}")}</p>")
        append("<h1>ProtocolTracker report</h1></header>")

        append("<section><h2>Plan</h2>")
        if (r.plan.isEmpty()) append("<p class=\"empty\">No plan items.</p>")
        for (phase in r.plan) {
            append("<h3>${esc(phase.name)}")
            if (phase.dates.isNotEmpty()) append(" <span class=\"meta\">${esc(phase.dates)}</span>")
            append("</h3>")
            if (phase.notes.isNotBlank()) append("<p>${esc(phase.notes)}</p>")
            if (phase.items.isEmpty()) { append("<p class=\"empty\">No items.</p>"); continue }
            append("<table><thead><tr><th>Compound</th><th>Amount</th><th>Per dose</th><th>Schedule</th><th>Notes</th></tr></thead><tbody>")
            for (i in phase.items) {
                append("<tr><td><strong>${esc(i.compound)}</strong><br><span class=\"meta\">${esc(i.category)}</span></td>")
                append("<td class=\"num\">${esc(i.amount)}</td><td class=\"num\">${esc(i.perDose)}</td>")
                append("<td>${esc(i.schedule)}</td><td>${esc(i.notes)}</td></tr>")
            }
            append("</tbody></table>")
        }
        append("</section>")

        append("<section><h2>Summary</h2>")
        if (r.adherence.isEmpty() && r.bloodPressure == null) append("<p class=\"empty\">Nothing scheduled or measured in this range.</p>")
        if (r.adherence.isNotEmpty()) {
            append("<table><thead><tr><th>Compound</th><th>Taken</th><th>Skipped</th><th>Scheduled</th></tr></thead><tbody>")
            for (a in r.adherence) {
                append("<tr><td>${esc(a.compound)}</td><td class=\"num\">${a.taken}</td><td class=\"num\">${a.skipped}</td><td class=\"num\">${a.scheduled}</td></tr>")
            }
            append("</tbody></table>")
        }
        r.bloodPressure?.let {
            append("<p>Blood pressure: ${it.readings} readings, average <strong class=\"num\">${it.averageSystolic}/${it.averageDiastolic}</strong> mmHg, ")
            append("lowest <span class=\"num\">${esc(it.lowest)}</span>, highest <span class=\"num\">${esc(it.highest)}</span>.</p>")
        }
        append("</section>")

        append("<section><h2>Journal</h2>")
        if (r.days.isEmpty()) append("<p class=\"empty\">No entries in this range.</p>")
        for (day in r.days) {
            append("<h3>${day.date} <span class=\"meta\">${day.date.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }}</span></h3>")
            append("<table class=\"day\"><tbody>")
            for (e in day.entries) {
                append("<tr><td class=\"num time\">${e.time}</td>")
                when (e) {
                    is ReportEntry.Dose -> {
                        append("<td><strong>${esc(e.compound)}</strong>")
                        val details = listOfNotNull(e.partOfDay, e.planned?.let { "planned $it" }, e.note.takeIf { it.isNotBlank() })
                        if (details.isNotEmpty()) append("<br><span class=\"meta\">${esc(details.joinToString(" · "))}</span>")
                        append("</td><td class=\"num\">${esc(e.amount)}</td><td class=\"status ${e.status}\">${e.status}</td>")
                    }
                    is ReportEntry.BloodPressure -> {
                        append("<td><strong>Blood pressure</strong>")
                        if (e.note.isNotBlank()) append("<br><span class=\"meta\">${esc(e.note)}</span>")
                        append("</td><td class=\"num\">${e.systolic}/${e.diastolic} mmHg</td><td>${e.pulse?.let { "pulse $it" } ?: ""}</td>")
                    }
                    is ReportEntry.Note -> append("<td colspan=\"3\"><strong>Note</strong><br>${esc(e.text)}</td>")
                }
                append("</tr>")
            }
            append("</tbody></table>")
        }
        append("</section></main></body></html>")
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "<br>")

    private const val CSS = """
:root{--bg:#F3F4F2;--surface:#FFFFFF;--ink:#151917;--muted:#56605B;--line:#DCE1DE;--accent:#1E6B5C;--warn:#9A5B00}
@media (prefers-color-scheme:dark){:root{--bg:#101312;--surface:#191D1B;--ink:#E7ECE9;--muted:#9AA59F;--line:#2A302D;--accent:#7CCBB5;--warn:#E9B45A}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.45 "IBM Plex Sans",system-ui,sans-serif}
main{max-width:860px;margin:0 auto;padding:24px 16px 48px}h1{font-size:28px;font-weight:600;margin:4px 0 0}
h2{font-size:13px;letter-spacing:.06em;text-transform:uppercase;margin:32px 0 8px}h3{font-size:16px;margin:20px 0 8px}
.meta{color:var(--muted);font-size:13px;font-weight:400}.empty{color:var(--muted)}
.num{font-family:"IBM Plex Mono",ui-monospace,monospace;font-variant-numeric:tabular-nums;white-space:nowrap}
table{width:100%;border-collapse:collapse;background:var(--surface);border:1px solid var(--line);border-radius:10px;overflow:hidden;margin:8px 0}
th,td{text-align:left;vertical-align:top;padding:8px 12px;border-top:1px solid var(--line)}th{font-size:12px;color:var(--muted);font-weight:600;border-top:0}
.time{width:64px;color:var(--muted)}.status{white-space:nowrap}.status.missed{color:var(--warn);font-weight:600}.status.skipped{color:var(--muted)}
@media print{body{background:#fff;color:#000}table{break-inside:auto}tr{break-inside:avoid}}
"""
}
