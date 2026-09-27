package com.apollof.protocoltracker

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Settings shows `BuildConfig.VERSION_NAME`. Stable keeps a plain release version; the dev flavor sets its own
 * (`0.4.0-dev`, or the tag without the "v" of a dev pre-release such as `0.5.0-dev.1`), so a dev release never
 * touches the version stable shows.
 */
class VersionNameTest {
    @Test
    fun versionNameMatchesTheFlavor() {
        val pattern = if (BuildConfig.DEV_FEATURES) Regex("""\d+\.\d+\.\d+-dev(\.\d+)?""") else Regex("""\d+\.\d+\.\d+""")
        assertTrue(pattern.matches(BuildConfig.VERSION_NAME), "unexpected version name ${BuildConfig.VERSION_NAME}")
    }
}
