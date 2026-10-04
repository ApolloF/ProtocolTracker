package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.Rise
import com.apollof.protocoltracker.domain.pk.PkSheet.Model
import com.apollof.protocoltracker.domain.pk.PkSheet.SheetRow

/**
 * The level model for sheet-based presets, for one dose (docs/MODELS.md):
 * - Advanced (study Cmax and Tmax): rises linearly to `M × Cmax × dose` at Tmax, then halves every half-life.
 *   Bioavailability is not used.
 * - Basic (no Cmax): first-order absorption with a half-time of t½ / 8, so Tmax = 3/8 t½, towards
 *   `14 × M × F × dose`, which it reaches 87.5 % of at Tmax; then halves every half-life.
 * M is the compound's multiplier. Doses add up. Levels are ng/dL per unit dosed in the sheet's own dose unit.
 */
object PkSheetModel {
    private const val HOURS_PER_DAY = 24.0
    /** Basic curves are scaled by 14 on top of the compound's multiplier. */
    private const val BASIC_SCALE = 14.0
    /** Basic: absorption half-time = t½ / 8 and Tmax = 3 absorption half-times. */
    private const val BASIC_TMAX_PER_HALF_LIFE = 3.0 / 8

    /**
     * Preset kinetics from a sheet [row]. [perBaseUnit] converts the sheet's dose unit to the preset's base unit
     * (1000 for a per-mcg Cmax on a mg preset, 1e-4 for hCG per IU). [activeFraction] is only used for relative curves.
     */
    fun pk(row: SheetRow, unit: LevelUnit = LevelUnit.NG_DL, perBaseUnit: Double = 1.0, activeFraction: Double? = null): PkParams {
        val halfLifeD = requireNotNull(row.halfLifeD) { "${row.compound}: no half-life" }
        val fraction = activeFraction ?: row.bioavailability ?: 1.0
        return when (row.model) {
            Model.ADVANCED -> PkParams(
                halfLifeH = halfLifeD * HOURS_PER_DAY,
                tmaxH = requireNotNull(row.tmaxD) { "${row.compound}: no Tmax" } * HOURS_PER_DAY,
                peakPerUnit = requireNotNull(row.cmax) { "${row.compound}: no Cmax" } * row.multiplier * perBaseUnit,
                activeFraction = fraction,
                levelUnit = unit,
                rise = Rise.LINEAR,
            )
            Model.BASIC -> PkParams(
                halfLifeH = halfLifeD * HOURS_PER_DAY,
                tmaxH = halfLifeD * BASIC_TMAX_PER_HALF_LIFE * HOURS_PER_DAY,
                peakPerUnit = BASIC_SCALE * row.multiplier * requireNotNull(row.bioavailability) { "${row.compound}: no F" } *
                    PkParams.FIRST_ORDER_PEAK_SHARE * perBaseUnit,
                activeFraction = fraction,
                levelUnit = unit,
                rise = Rise.FIRST_ORDER,
            )
        }
    }
}
