package com.apollof.protocoltracker.domain.model

import java.time.Instant

/** One injection site: the stored [key], the [short] label for chips and rows ("L VG") and the [long] one ("Left ventrogluteal"). */
data class InjectionSite(val key: String, val short: String, val long: String)

/**
 * The fixed site list, top to bottom on the body, each area left then right. Logs store the key, never a label, so labels
 * can change and keys this version does not know (from a later version) are kept and shown as they are.
 */
object InjectionSites {
    val all: List<InjectionSite> = listOf(
        "delt" to "deltoid", "pec" to "pec", "abdomen" to "abdomen", "vg" to "ventrogluteal", "glute" to "glute", "thigh" to "thigh",
    ).flatMap { (key, name) ->
        val short = if (key == "vg") "VG" else key
        listOf(
            InjectionSite("${key}_l", "L $short", "Left $name"),
            InjectionSite("${key}_r", "R $short", "Right $name"),
        )
    }

    private val byKey = all.associateBy { it.key }
    private val index = all.withIndex().associate { (i, site) -> site.key to i }

    fun byKey(key: String): InjectionSite? = byKey[key]

    /** Short label ("R VG"); an unknown key shows as it is. */
    fun label(key: String): String = byKey[key]?.short ?: key

    /** Long label ("Right ventrogluteal"); an unknown key shows as it is. */
    fun longLabel(key: String): String = byKey[key]?.long ?: key

    /** The same area on the other side; null for an unknown key. */
    fun mirror(key: String): String? {
        if (key !in byKey) return null
        return key.dropLast(1) + if (key.endsWith("_l")) "r" else "l"
    }

    /** Catalog position, used for ordering; unknown keys sort after every known one. */
    fun order(key: String): Int = index[key] ?: Int.MAX_VALUE
}

/** A site and when the dose there was taken. */
data class SiteUse(val site: String, val at: Instant)

/**
 * One compound's site history.
 * [last] is the most recent recorded site. [personal] holds the sites used, known ones in catalog order, then unknown ones
 * newest first. [suggestion] is the next site, or null when the newest taken dose has no site (the chain is broken).
 */
data class SiteState(val last: SiteUse, val personal: List<String>, val suggestion: String?)

/** The Site row of a dose: the compound's [state] (null when no site was recorded) and the site it starts at. */
data class SiteChoice(val state: SiteState?, val initial: String?) {
    /** The sites offered before "More": the ones used, the suggestion and the starting one, in catalog order. */
    val offered: List<String> =
        (state?.personal.orEmpty() + listOfNotNull(state?.suggestion, initial)).distinct().sortedBy(InjectionSites::order)

    /** Every site, catalog first, then unknown keys from [offered]. */
    val all: List<String> = InjectionSites.all.map { it.key } + offered.filter { InjectionSites.byKey(it) == null }
}

/**
 * Injection site rotation, per compound. The suggestion is the site that came after your last site the previous time you
 * used it. The first time, it is the same spot on the other side.
 */
object SiteRotation {
    /** How many taken doses count, newest first. */
    const val WINDOW = 24

    /**
     * The site state of one compound from its [logs] (any order; skipped doses are ignored), or null when none of its last
     * [WINDOW] taken doses has a site.
     */
    fun state(logs: List<DoseLog>): SiteState? {
        val taken = logs.filter { it.status == LogStatus.TAKEN }
            .sortedWith(compareByDescending<DoseLog> { it.takenAt }.thenByDescending { it.createdAt })
            .take(WINDOW)
        val used = taken.mapNotNull { log -> log.site?.takeIf { it.isNotBlank() }?.let { SiteUse(it, log.takenAt) } }
        if (used.isEmpty()) return null
        val sites = used.map { it.site }
        val last = sites[0]
        val personal = sites.distinct().sortedBy(InjectionSites::order)
        val intact = !taken[0].site.isNullOrBlank()
        return SiteState(used[0], personal, if (intact) next(sites) else null)
    }

    /** Each compound's state from [logs] of any compounds; compounds without a recorded site are left out. */
    fun byCompound(logs: List<DoseLog>): Map<String, SiteState> =
        logs.groupBy { it.compoundId }.mapNotNull { (id, own) -> state(own)?.let { id to it } }.toMap()

    /**
     * The site choice when logging a dose of [compoundId] from [logs] of any compounds. The dose being edited ([editing])
     * is left out of the history; a taken one starts at its own site (even none), anything else at the suggestion.
     */
    fun forDose(logs: List<DoseLog>, compoundId: String, editing: DoseLog? = null): SiteChoice {
        val state = state(logs.filter { it.compoundId == compoundId && it.id != editing?.id })
        val initial = if (editing?.status == LogStatus.TAKEN) editing.site?.takeIf { it.isNotBlank() } else state?.suggestion
        return SiteChoice(state, initial)
    }

    /** [sites] newest first, not empty. */
    private fun next(sites: List<String>): String? {
        val last = sites[0]
        val previous = (1 until sites.size).firstOrNull { sites[it] == last }
        val followed = previous?.let { sites[it - 1] }?.takeIf { it != last }
        return followed ?: InjectionSites.mirror(last)
    }
}

/**
 * How a dose write treats the injection site. [Keep] leaves the site a re-logged dose already has (none for a new one);
 * [Set] writes the given key, and null or blank clears it.
 */
sealed interface SiteWrite {
    data object Keep : SiteWrite
    data class Set(val site: String?) : SiteWrite

    /** The site to store, given the one already [stored]. */
    fun resolve(stored: String?): String? = when (this) {
        Keep -> stored
        is Set -> site?.takeIf { it.isNotBlank() }
    }
}
