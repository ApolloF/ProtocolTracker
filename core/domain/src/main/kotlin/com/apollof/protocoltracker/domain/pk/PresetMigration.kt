package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.Rise
import kotlin.math.abs

/**
 * Moves stored kinetics off the presets before the PK sheet (presets-2026-09b and -10a, which differ only in F).
 * Log snapshots and edited presets keep a copy of the kinetics; a copy that still equals the preset's old kinetics was
 * never changed by the user, so it is replaced by the preset's current kinetics. Anything else is the user's own and
 * stays. F is ignored in the comparison: 10a only corrected F.
 */
object PresetMigration {
    /** Preset id (without "preset:") → kinetics as shipped in presets-2026-10a: half-life h, Tmax h, peak, F, unit. */
    private val legacy: Map<String, PkParams> = mapOf(
        "test-enan" to PkParams(172.56, 33.3, 11.3095, 0.72, LevelUnit.NG_DL),
        "test-cyp" to PkParams(165.60000000000002, 108.0, 5.56, 0.7, LevelUnit.NG_DL),
        "test-prop" to PkParams(24.900000000000002, 25.5, 26.0, 0.84, LevelUnit.NG_DL),
        "test-undec" to PkParams(813.5999999999999, 240.0, 1.187, 0.63, LevelUnit.NG_DL),
        "test-pp" to PkParams(60.0, 12.0, 31.099908929173825, 0.69, LevelUnit.NG_DL),
        "test-iso" to PkParams(74.4, 14.880000000000003, 27.261490996821372, 0.75, LevelUnit.NG_DL),
        "test-dec" to PkParams(134.39999999999998, 26.879999999999995, 13.079024847284543, 0.65, LevelUnit.NG_DL),
        "test-susp" to PkParams(33.0, 6.0, 5.067, 1.0, LevelUnit.NG_DL),
        "deca" to PkParams(244.79999999999998, 43.992, 3.99, 0.64, LevelUnit.NG_DL),
        "npp" to PkParams(57.599999999999994, 24.0, 8.91465, 0.67, LevelUnit.NG_DL),
        "tren-ace" to PkParams(36.0, 7.2, null, 0.87, LevelUnit.NG_DL),
        "tren-enan" to PkParams(264.0, 48.0, null, 0.71, LevelUnit.NG_DL),
        "tren-hex" to PkParams(192.0, 38.400000000000006, null, 0.66, LevelUnit.NG_DL),
        "mast-prop" to PkParams(48.0, 9.600000000000001, null, 0.84, LevelUnit.NG_DL),
        "mast-enan" to PkParams(108.0, 21.6, null, 0.73, LevelUnit.NG_DL),
        "primo-enan" to PkParams(252.0, 48.0, null, 0.73, LevelUnit.NG_DL),
        "eq" to PkParams(123.0, 144.0, 1.098, 0.63, LevelUnit.NG_DL),
        "bold-cyp" to PkParams(165.60000000000002, 108.0, 1.0389898174031287, 0.7, LevelUnit.NG_DL),
        "dhb" to PkParams(216.0, 43.2, null, 0.7, LevelUnit.NG_DL),
        "winstrol-depot" to PkParams(82.08, 168.0, 8.12, 1.0, LevelUnit.NG_DL),
        "ment" to PkParams(3.7333439999999998, 1.0, null, 0.87, LevelUnit.NG_DL),
        "oxandrolone" to PkParams(6.6999984, 1.60000008, 772.0, 0.625, LevelUnit.NG_DL),
        "methandienone" to PkParams(5.25, 1.5, null, 0.95, LevelUnit.NG_DL),
        "oxymetholone" to PkParams(7.9833312, 3.4999992, 37.6, 0.95, LevelUnit.NG_DL),
        "stanozolol" to PkParams(9.0, 1.5, 119.67312363752376, 1.0, LevelUnit.NG_DL),
        "turinabol" to PkParams(16.000008, 1.5, null, 1.0, LevelUnit.NG_DL),
        "halotestin" to PkParams(1.9999920000000002, 1.7999999999999998, 800.0, 0.57, LevelUnit.NG_DL),
        "superdrol" to PkParams(10.0000008, 1.5, null, 0.5, LevelUnit.NG_DL),
        "proviron" to PkParams(12.499991999999999, 1.6000008, 12.4, 0.03, LevelUnit.NG_DL),
        "primo-oral" to PkParams(4.9992, 1.5, null, 0.88, LevelUnit.NG_DL),
        "test-undec-oral" to PkParams(18.4000008, 4.9000008, 2.4876225, 0.0683, LevelUnit.NG_DL),
        "anastrozole" to PkParams(46.8, 1.0000008, 3930.0, 0.8, LevelUnit.NG_ML),
        "exemestane" to PkParams(22.6999992, 1.4249999999999998, 57.6, 0.05, LevelUnit.NG_ML),
        "letrozole" to PkParams(33.3499992, 1.8599999999999999, 45.684, 1.0, LevelUnit.NG_ML),
        "tamoxifen" to PkParams(47.400000000000006, 8.2600008, 200.0, 0.15, LevelUnit.NG_ML),
        "clomiphene" to PkParams(120.0, 4.9999991999999995, 40.0, 0.95, LevelUnit.NG_ML),
        "enclomiphene" to PkParams(10.0, 2.5, null, 1.0, LevelUnit.NG_DL),
        "hcg" to PkParams(47.099999999999994, 24.0, 0.20720000000000002, 0.45, LevelUnit.NG_ML),
        "cabergoline" to PkParams(85.9999992, 1.992, 4.03, 0.465, LevelUnit.PG_ML),
        "telmisartan" to PkParams(23.299999200000002, 1.6999992000000002, 770.0625, 0.43, LevelUnit.NG_ML),
        "nebivolol" to PkParams(16.930000800000002, 3.1099992000000003, 11.6, 0.54, LevelUnit.NG_ML),
        "tadalafil" to PkParams(16.9999992, 2.5000008, 1725.0, 1.0, LevelUnit.NG_ML),
        "liothyronine" to PkParams(22.0399992, 2.5000008, 6920.0, 1.0, LevelUnit.NG_DL),
        "clenbuterol" to PkParams(26.560000799999997, 2.5999992, 333.0, 1.0, LevelUnit.PG_ML),
        "semaglutide" to PkParams(156.0, 30.0, 2879.1, 0.89, LevelUnit.NG_ML),
        "semaglutide-oral" to PkParams(12.96, 1.416666672, 232.38, 0.008, LevelUnit.NG_ML),
        "tirzepatide" to PkParams(116.69999999999999, 36.0, 5826.666666, 0.8, LevelUnit.NG_ML),
        "retatrutide" to PkParams(147.3333312, 40.366666992, 11495.37037, 0.8, LevelUnit.NG_ML),
        "mazdutide" to PkParams(434.40000000000003, 72.34992, 7348.3333, 1.0, LevelUnit.NG_ML),
        "liraglutide" to PkParams(13.0, 11.0, 5833.333333333334, 0.55, LevelUnit.NG_ML),
        "cagrilintide" to PkParams(177.0, 48.0, null, 1.0, LevelUnit.NG_DL),
        "hgh" to PkParams(4.1333328, 5.2999992, 207.63333333333333, 0.63, LevelUnit.NG_ML),
        "tesamorelin" to PkParams(0.53, 0.15, 191.55, 0.04, LevelUnit.PG_ML),
        "ipamorelin" to PkParams(2.0, 1.0, null, 1.0, LevelUnit.NG_DL),
        "cjc-1295-dac" to PkParams(166.8, 33.36000000000001, null, 1.0, LevelUnit.NG_DL),
        "pt-141" to PkParams(2.7, 1.0, 4160.0, 1.0, LevelUnit.NG_ML),
    )

    /** The current preset kinetics when [pk] is still the legacy value of preset [compoundId]; otherwise [pk]. */
    fun upgrade(compoundId: String, pk: PkParams?): PkParams? {
        if (pk == null) return null
        val old = legacy[compoundId.removePrefix("preset:")] ?: return pk
        if (!sameKinetics(pk, old)) return pk
        return Presets.byId(compoundId)?.pk ?: pk
    }

    private fun sameKinetics(a: PkParams, b: PkParams): Boolean =
        a.rise == Rise.LINEAR && b.rise == Rise.LINEAR && a.levelUnit == b.levelUnit &&
            close(a.halfLifeH, b.halfLifeH) && close(a.tmaxH, b.tmaxH) &&
            (a.peakPerUnit == null) == (b.peakPerUnit == null) &&
            (a.peakPerUnit == null || close(a.peakPerUnit, b.peakPerUnit!!))

    private fun close(a: Double, b: Double) = abs(a - b) <= 1e-9 * maxOf(abs(a), abs(b))
}
