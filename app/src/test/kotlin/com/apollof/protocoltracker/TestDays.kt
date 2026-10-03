package com.apollof.protocoltracker

import java.time.LocalTime
import kotlinx.coroutines.runBlocking

/**
 * Tests that plan from `LocalDate.now()` count days from midnight, so they also pass between midnight and the dev
 * day start (4:00), when Today still shows the day before.
 */
fun AppContainer.dayStartsAtMidnight() = runBlocking {
    settings.update { it.copy(slotTimes = it.slotTimes.copy(dayStart = LocalTime.MIDNIGHT)) }
}
