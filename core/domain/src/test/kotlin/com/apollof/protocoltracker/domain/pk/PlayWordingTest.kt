package com.apollof.protocoltracker.domain.pk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Guard: no preset the Play build shows carries a banned word (scientific name, common name or display name). A
 * failure names the preset, like ManifestPermissionsTest names the permission.
 */
class PlayWordingTest {
    @Test
    fun noPlayPresetNameHasABannedToken() {
        val hits = PresetChannel.PLAY.presets().mapNotNull { p ->
            listOf(p.name, p.commonName, p.displayName).firstNotNullOfOrNull { text ->
                PlayWording.bannedTokenIn(text)?.let { "${p.id}: \"$text\" contains \"$it\"" }
            }
        }
        if (hits.isNotEmpty()) fail("Banned words in Play preset names:\n" + hits.joinToString("\n"))
    }

    @Test
    fun tokensMatchAtWordStartsIgnoringCase() {
        assertEquals("tren", PlayWording.bannedTokenIn("Tren E (trenbolone enanthate)"))
        assertEquals("steroid", PlayWording.bannedTokenIn("Injectable steroids"))
        assertEquals("sarm", PlayWording.bannedTokenIn("SARMs and research compounds"))
        assertEquals("sus 500", PlayWording.bannedTokenIn("Sus 500"))
        assertEquals("arimidex", PlayWording.bannedTokenIn("Arimidex (anastrozole)"))
        assertEquals("pct", PlayWording.bannedTokenIn("PCT"))
        assertNull(PlayWording.bannedTokenIn("Somatropin"))
        assertNull(PlayWording.bannedTokenIn("human chorionic gonadotropin"))
        assertNull(PlayWording.bannedTokenIn("Testosterone enanthate"))
    }

    @Test
    fun fossNamesWouldFailTheGuard() {
        // The guard has teeth: the FOSS names it exists to keep out of Play are caught.
        listOf("tren-enan", "superdrol", "turinabol", "anastrozole").forEach { id ->
            val p = Presets.byId("preset:$id")!!
            val hit = listOf(p.name, p.commonName).firstNotNullOfOrNull { PlayWording.bannedTokenIn(it) }
            assertNotNull(hit, id)
        }
    }
}
