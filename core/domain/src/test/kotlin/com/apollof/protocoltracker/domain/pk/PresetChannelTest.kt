package com.apollof.protocoltracker.domain.pk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetChannelTest {
    private val allIds = Presets.all.map { it.id.removePrefix("preset:") }

    @Test
    fun everyPlayIdIsAPresetAndThereAre45() {
        assertEquals(45, PLAY_PRESET_IDS.size)
        assertEquals(emptySet(), PLAY_PRESET_IDS - allIds.toSet(), "unknown ids in PLAY_PRESET_IDS")
        assertEquals(PLAY_PRESET_IDS, PresetChannel.PLAY.presets().map { it.id.removePrefix("preset:") }.toSet())
    }

    @Test
    fun fossAllowsAll78() {
        assertEquals(78, Presets.all.size)
        assertTrue(Presets.all.all { PresetChannel.FOSS.allows(it.id) })
        assertEquals(Presets.all, PresetChannel.FOSS.presets())
    }

    @Test
    fun listedCompoundsAreNotInPlay() {
        val out = listOf(
            "superdrol", "turinabol", "tren-ace", "tren-enan", "tren-hex",
            "ostarine", "ligandrol", "andarine", "testolone", "cardarine", "s-23",
            "clenbuterol", "mk-677",
        )
        out.forEach { id ->
            assertTrue(id in allIds, "$id is not a preset")
            assertFalse(PresetChannel.PLAY.allows(id), "$id must not be in Play")
            assertFalse(PresetChannel.PLAY.allows("preset:$id"), "$id must not be in Play")
        }
    }

    @Test
    fun playShowsInnOrCompoundNamesOnlyExceptHcg() {
        val play = PresetChannel.PLAY.presets().associateBy { it.id.removePrefix("preset:") }
        assertEquals("hCG", play.getValue("hcg").commonName)
        play.filterKeys { it != "hcg" }.forEach { (id, p) -> assertEquals("", p.commonName, id) }
        assertEquals("Testosterone enanthate", play.getValue("test-enan").displayName)
        assertEquals("Anastrozole", play.getValue("anastrozole").displayName)
        assertEquals("Somatropin", play.getValue("hgh").displayName)
        assertEquals("Bremelanotide", play.getValue("pt-141").displayName)
    }

    @Test
    fun channelChangesNamesOnlyNeverKinetics() {
        PresetChannel.PLAY.presets().forEach { p ->
            val original = Presets.byId(p.id)!!
            assertEquals(original.copy(commonName = p.commonName), p, p.id)
        }
    }

    @Test
    fun customCompoundsKeepTheirNames() {
        val custom = Presets.all.first().copy(id = "custom-1", commonName = "Mine", isPreset = false)
        assertEquals(custom, PresetChannel.PLAY.present(custom))
    }
}
