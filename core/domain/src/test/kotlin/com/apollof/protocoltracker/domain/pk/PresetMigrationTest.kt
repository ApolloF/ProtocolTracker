package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PkParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PresetMigrationTest {
    private val testE = Presets.byId("preset:test-enan")!!.pk!!

    @Test
    fun legacyPresetKineticsBecomeTheCurrentOnes() {
        val legacy = PkParams(172.56, 33.3, 11.3095, 0.72, LevelUnit.NG_DL)
        assertEquals(testE, PresetMigration.upgrade("preset:test-enan", legacy))
        // F is ignored: presets-2026-09b differed from -10a only there.
        val nebido = Presets.byId("preset:test-undec")!!.pk
        assertEquals(nebido, PresetMigration.upgrade("preset:test-undec", PkParams(813.5999999999999, 240.0, 1.187, 0.65, LevelUnit.NG_DL)))
        // Basic compounds were relative (no peak) and are now absolute.
        val tren = Presets.byId("preset:tren-enan")!!.pk
        assertEquals(tren, PresetMigration.upgrade("preset:tren-enan", PkParams(264.0, 48.0, null, 0.71, LevelUnit.NG_DL)))
    }

    @Test
    fun userKineticsAndUnknownCompoundsStay() {
        val edited = PkParams(172.56, 33.3, 12.0, 0.72, LevelUnit.NG_DL)
        assertSame(edited, PresetMigration.upgrade("preset:test-enan", edited))
        val custom = PkParams(172.56, 33.3, 11.3095, 0.72, LevelUnit.NG_DL)
        assertSame(custom, PresetMigration.upgrade("custom-1", custom))
        assertSame(testE, PresetMigration.upgrade("preset:test-enan", testE))
        assertNull(PresetMigration.upgrade("preset:test-enan", null))
    }

    @Test
    fun currentPresetKineticsAreLeftAlone() {
        for (preset in Presets.all) assertEquals(preset.pk, PresetMigration.upgrade(preset.id, preset.pk), preset.id)
    }
}
