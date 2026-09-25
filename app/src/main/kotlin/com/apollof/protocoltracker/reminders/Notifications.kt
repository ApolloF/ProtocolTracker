package com.apollof.protocoltracker.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.apollof.protocoltracker.MainActivity
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.units.describeDose
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifications {
    const val CHANNEL_DOSES = "doses"
    const val CHANNEL_SUMMARY = "summary"
    private const val SUMMARY_ID = 1
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DOSES, context.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_doses_description) },
                NotificationChannel(CHANNEL_SUMMARY, context.getString(R.string.channel_summary), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_summary_description) },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Notification id for a time slot (second precision), so re-posting a slot (e.g. after snooze) replaces it. */
    fun slotId(slot: Instant): Int = slot.epochSecond.hashCode().let { if (it == SUMMARY_ID) it + 1 else it }

    fun showDoses(context: Context, slot: Instant, due: List<Occurrence>, compounds: Map<String, Compound>, zone: ZoneId) {
        if (due.isEmpty() || !canPost(context)) return
        val id = slotId(slot)
        val keys = due.map { it.key }
        val lines = due.map { occ ->
            val c = compounds[occ.item.compoundId]
            val dose = c?.let { describeDose(occ.item.dose, it.baseUnit, occ.item.formulation) } ?: ""
            "${c?.name ?: "Dose"} · $dose"
        }
        val time = slot.atZone(zone).format(timeFormat)
        val title = if (due.size == 1) "${lines.first().substringBefore(" · ")} due · $time" else "${due.size} doses due · $time"
        val builder = NotificationCompat.Builder(context, CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle(title)
            .setContentText(if (due.size == 1) lines.first().substringAfter(" · ") else lines.joinToString(", "))
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(slot.toEpochMilli())
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(0, if (due.size == 1) "Taken" else "Take all", action(context, NotificationActionReceiver.ACTION_TAKE, keys, id))
            .addAction(0, "Snooze", action(context, NotificationActionReceiver.ACTION_SNOOZE, keys, id))
            .addAction(0, "Skip", action(context, NotificationActionReceiver.ACTION_SKIP, keys, id))
        post(context, id, builder)
    }

    fun showSummary(context: Context, lines: List<String>) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle(if (lines.isEmpty()) "Nothing scheduled today" else "Today · ${lines.size} doses")
            .setContentText(lines.joinToString(", "))
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        post(context, SUMMARY_ID, builder)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and posting; the Today screen still shows the dose.
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, keys: List<String>, notificationId: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_KEYS, keys.toTypedArray())
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        // The action string already distinguishes the three PendingIntents of one notification.
        return PendingIntent.getBroadcast(context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
