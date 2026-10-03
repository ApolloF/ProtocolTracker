package com.apollof.protocoltracker

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import kotlinx.coroutines.runBlocking

/**
 * Waits until the database meets [condition]. Idles the UI before each check: a write launched from the UI resumes
 * on Robolectric's paused main looper, which a plain `runBlocking` check never lets run.
 */
fun ComposeContentTestRule.waitForData(timeoutMillis: Long = 15_000, condition: suspend () -> Boolean) =
    waitUntil(timeoutMillis) { waitForIdle(); runBlocking { condition() } }
