package com.apollof.protocoltracker.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.BuildConfig
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.schedule.IntervalAnchors
import com.apollof.protocoltracker.domain.schedule.Occurrence
import com.apollof.protocoltracker.domain.schedule.occurrences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The dose reminder and the injection site: in dev each injectable's line ends with its suggested site and Taken records
 * exactly the sites shown; stable shows and records none, with its lines unchanged.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSiteTest {
    private val app get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>()
    private val container get() = app.container
    private val dev = BuildConfig.DEV_FEATURES
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val morning = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING)))

    @Before
    fun seed(): Unit = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.repository.seedPresets()
    }

    /** 700 mg per week of Test C (100 mg, 0.5 mL a day), or 500 IU of hCG a day, every morning since yesterday. */
    private suspend fun plan(compound: String = "test-cyp"): List<Occurrence> {
        val start = today.minusDays(1)
        val item = if (compound == "hcg") {
            PlanItem("hcg-item", null, "preset:hcg", Amount(500.0, DoseUnit.IU), DoseBasis.PER_DOSE, Formulation(), morning, startDate = start, sortOrder = 1)
        } else {
            PlanItem("test-item", null, "preset:test-cyp", Amount(700.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 200.0), morning, startDate = start)
        }
        container.repository.saveItem(item)
        val protocol = container.repository.protocolNow()
        return occurrences(
            protocol.phases, listOf(item), start.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant(), zone,
            IntervalAnchors.NONE, container.settings.current().slotTimes,
        )
    }

    /** A taken extra dose of the preset [compound] at [site] two days ago. */
    private suspend fun extra(site: String, compound: String = "test-cyp") {
        val c = container.repository.compounds.first().first { it.id == "preset:$compound" }
        val amount = if (compound == "hcg") Amount(500.0, DoseUnit.IU) else Amount(100.0, DoseUnit.MG)
        container.repository.logUnscheduled(c, amount, c.defaultFormulation, Instant.now().minus(Duration.ofDays(2)), site = SiteWrite.Set(site))
    }

    /** Fires the reminder for [due] and returns the one notification it posts. */
    private suspend fun remind(vararg due: Occurrence): Notification {
        val intent = Intent(app, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_DOSE)
            .putExtra(AlarmReceiver.EXTRA_SLOT, due.last().at.epochSecond)
            .putExtra(AlarmReceiver.EXTRA_KEYS, due.map { it.key }.toTypedArray())
        AlarmReceiver.handle(app, intent)
        return shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
    }

    /** What the notification's Taken or Take all button sends. */
    private fun Notification.takeIntent(): Intent = shadowOf(actions.first { it.title in setOf("Taken", "Take all") }.actionIntent).savedIntent

    private suspend fun Notification.pressTaken() = NotificationActionReceiver.handle(app, takeIntent())

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    private val Notification.lines get() = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }

    private suspend fun storedSites(): Map<String?, String?> = container.repository.allLogsNow().filter { it.planItemId != null }.associate { it.occurrenceKey to it.site }

    /** Both flavors, no site ever picked: the title, text and lines stay as they were, and Taken records no site. */
    @Test
    fun aReminderWithoutSitesIsUnchanged(): Unit = runBlocking {
        val todays = plan().last()
        val notification = remind(todays)
        assertEquals("Morning: Test C (testosterone cypionate)", notification.title)
        assertEquals("100 mg · 0.5 mL", notification.text)
        assertEquals(listOf("Test C (testosterone cypionate) · 100 mg · 0.5 mL"), notification.lines)
        assertNull(notification.takeIntent().getStringArrayExtra(NotificationActionReceiver.EXTRA_SITES))
        notification.pressTaken()
        assertEquals(mapOf<String?, String?>(todays.key to null), storedSites())
    }

    /** After a pin at L VG the line ends "· R VG" in dev and Taken stores vg_r; stable shows the same line as before and stores none. */
    @Test
    fun theReminderShowsAndTakenRecordsTheSuggestedSite(): Unit = runBlocking {
        val todays = plan().last()
        extra("vg_l")
        val notification = remind(todays)
        val suffix = if (dev) " · R VG" else ""
        assertEquals("Morning: Test C (testosterone cypionate)", notification.title)
        assertEquals("100 mg · 0.5 mL$suffix", notification.text)
        assertEquals(listOf("Test C (testosterone cypionate) · 100 mg · 0.5 mL$suffix"), notification.lines)
        notification.pressTaken()
        assertEquals(mapOf<String?, String?>(todays.key to if (dev) "vg_r" else null), storedSites())
    }

    /** Dev: Take all records each line's site, a compound's suggestion on its first dose only; hCG has its own. */
    @Test
    fun takeAllRecordsEachShownSiteOncePerCompound(): Unit = runBlocking {
        assumeTrue(dev)
        val (yesterdays, todays) = plan()
        val hcg = plan("hcg").last()
        extra("vg_l")
        extra("abdomen_l", "hcg")
        val notification = remind(yesterdays, todays, hcg)
        assertEquals(
            listOf(
                "Test C (testosterone cypionate) · 100 mg · 0.5 mL · R VG",
                "Test C (testosterone cypionate) · 100 mg · 0.5 mL",
                "hCG (human chorionic gonadotropin) · 500 IU · R abdomen",
            ),
            notification.lines,
        )
        notification.pressTaken()
        assertEquals(mapOf<String?, String?>(yesterdays.key to "vg_r", todays.key to null, hcg.key to "abdomen_r"), storedSites())
    }

    /** A reminder left open after the dose was logged from Today never overwrites that log. */
    @Test
    fun aStaleReminderNeverOverwritesADoseLoggedFromToday(): Unit = runBlocking {
        val todays = plan().last()
        extra("vg_l")
        val notification = remind(todays)
        container.doseActions.take(todays, site = SiteWrite.Set("delt_l"))
        notification.pressTaken()
        assertEquals(mapOf<String?, String?>(todays.key to "delt_l"), storedSites())
    }
}
