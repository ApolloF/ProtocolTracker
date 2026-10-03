package com.apollof.protocoltracker

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Settings shows `BuildConfig.VERSION_NAME`: a plain release version such as `0.5.0`, or the tag without the "v" of a
 * pre-release such as `0.6.0-dev.1`.
 */
class VersionNameTest {
    @Test
    fun versionNameIsAReleaseOrPreReleaseVersion() {
        val pattern = Regex("""\d+\.\d+\.\d+(-[a-z]+\.\d+)?""")
        assertTrue(pattern.matches(BuildConfig.VERSION_NAME), "unexpected version name ${BuildConfig.VERSION_NAME}")
    }
}
