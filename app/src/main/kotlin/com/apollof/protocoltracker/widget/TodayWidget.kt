package com.apollof.protocoltracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.apollof.protocoltracker.MainActivity
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.container
import com.apollof.protocoltracker.data.Palette
import com.apollof.protocoltracker.domain.schedule.AgendaStatus
import com.apollof.protocoltracker.domain.schedule.AgendaWindows
import com.apollof.protocoltracker.domain.schedule.buildAgenda
import com.apollof.protocoltracker.domain.units.describeDose
import com.apollof.protocoltracker.ui.theme.materialScheme
import com.apollof.protocoltracker.ui.theme.trackerColors

private data class WidgetRow(val key: String, val name: String, val detail: String, val overdue: Boolean)

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val now = c.clock()
        val protocol = c.repository.protocolNow()
        val zone = c.zone()
        val logs = c.repository.logsSinceNow(now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().minus(AgendaWindows.missedLookback))
        val agenda = buildAgenda(protocol.phases, protocol.items, logs, now, zone, c.repository.anchorsNow(), c.settings.current().slotTimes)
        fun row(e: com.apollof.protocoltracker.domain.schedule.AgendaEntry, label: String): WidgetRow? {
            val occ = e.occurrence ?: return null
            val compound = protocol.compounds[occ.item.compoundId] ?: return null
            return WidgetRow(
                key = occ.key, name = compound.displayName,
                detail = "$label · ${describeDose(occ.dose, compound.baseUnit, occ.item.formulation)}",
                overdue = e.status == AgendaStatus.MISSED,
            )
        }
        val rows = agenda.missed.mapNotNull { row(it, it.occurrence!!.localDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())) } +
            agenda.groups.flatMap { g -> g.pending.mapNotNull { row(it, g.label) } }
        val done = agenda.doneToday
        // The app's scheme, following the system light/dark mode; the wallpaper scheme uses Glance's dynamic colours.
        val prefs = c.settings.current()
        val colors = if (prefs.palette == Palette.DYNAMIC) null else ColorProviders(
            light = materialScheme(trackerColors(prefs.palette, dark = false)),
            dark = materialScheme(trackerColors(prefs.palette, dark = true, pureBlack = prefs.pureBlack)),
        )
        provideContent {
            if (colors == null) GlanceTheme { Content(rows, done) } else GlanceTheme(colors = colors) { Content(rows, done) }
        }
    }

    @Composable
    private fun Content(rows: List<WidgetRow>, done: Int) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background).cornerRadius(20.dp).padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface))
                Spacer(GlanceModifier.defaultWeight())
                Text("$done done", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (rows.isEmpty()) {
                Text("Nothing left today", style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                LazyColumn {
                    items(rows, itemId = { it.key.hashCode().toLong() }) { row -> RowItem(row) }
                }
            }
        }
    }

    @Composable
    private fun RowItem(row: WidgetRow) {
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // An empty ring, like the in-app check: pending doses never look taken.
            Box(
                modifier = GlanceModifier.size(48.dp)
                    .clickable(actionRunCallback<CheckAction>(actionParametersOf(CheckAction.KEY to row.key)))
                    .semantics { contentDescription = "Mark ${row.name} taken" },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.widget_check_ring), contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.outline), modifier = GlanceModifier.size(36.dp),
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Text(row.name, maxLines = 1, style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = GlanceTheme.colors.onSurface))
                Text(
                    (if (row.overdue) "Missed · " else "") + row.detail, maxLines = 1,
                    style = TextStyle(fontSize = 12.sp, color = if (row.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = TodayWidget().updateAll(context)
    }
}

class CheckAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY] ?: return
        context.container.doseActions.takeKeys(listOf(key))
        TodayWidget().update(context, glanceId)
    }

    companion object {
        val KEY = ActionParameters.Key<String>("occurrence_key")
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
