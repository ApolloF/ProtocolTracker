package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PkParams

/** Chip colours chosen to stay distinguishable and ≥3:1 against both light and dark surfaces. */
object CompoundColors {
    val palette: List<Long> = listOf(
        0xFF2E7D6BL, 0xFF3F6FD8L, 0xFFC2453DL, 0xFFB7791FL, 0xFF8A4FD1L, 0xFF1F8FB5L,
        0xFFD0527FL, 0xFF5E8C2EL, 0xFF9A6B3FL, 0xFF6B7280L, 0xFFE0702BL, 0xFF3A9A8FL,
    )
}

/**
 * Preset library. Values are estimates for relative level curves, not validated concentration models.
 * Half-lives are apparent terminal values (ester release included); see docs/MODELS.md for sources.
 * activeFraction = MW(parent) / MW(ester).
 */
object Presets {
    private const val VERSION = "presets-2026-09"

    private fun preset(
        id: String, name: String, group: String, category: CompoundCategory, color: Int,
        absorptionH: Double, eliminationH: Double, fraction: Double = 1.0,
        base: BaseUnit = BaseUnit.MG, perMl: Double? = null, perTablet: Double? = null, source: String,
    ) = Compound(
        id = "preset:$id", name = name, group = group, category = category, baseUnit = base,
        colorArgb = CompoundColors.palette[color % CompoundColors.palette.size],
        pk = PkParams(absorptionH, eliminationH, fraction),
        defaultFormulation = Formulation(perMl, perTablet),
        sourceNote = "$source ($VERSION)", isPreset = true,
    )

    private val INJ = CompoundCategory.INJECTABLE
    private val ORAL = CompoundCategory.ORAL

    val all: List<Compound> = listOf(
        // Testosterone (MW 288.42)
        preset("test-prop", "Testosterone propionate", "Testosterone", INJ, 0, 6.0, 19.0, 0.837, perMl = 100.0,
            source = "Community-cited apparent half-life ≈0.8 d"),
        preset("test-enan", "Testosterone enanthate", "Testosterone", INJ, 0, 8.66, 189.0, 0.720, perMl = 250.0,
            source = "IM popPK, PMC10174206 Table 1: ka 0.08/h, CL 50.6 L/h, V 13 800 L"),
        preset("test-cyp", "Testosterone cypionate", "Testosterone", INJ, 0, 13.6, 92.2, 0.699, perMl = 200.0,
            source = "popPK, PMID 29436172 Table 2: ka 1.22/d, CL/F 2.6 kL/d, V/F 14.4 kL"),
        preset("test-undec", "Testosterone undecanoate (IM)", "Testosterone", INJ, 0, 48.0, 504.0, 0.632, perMl = 250.0,
            source = "Label-reported terminal half-life ≈21 d, tmax ≈7 d"),
        // Nandrolone (MW 274.40)
        preset("npp", "Nandrolone phenylpropionate", "Nandrolone", INJ, 1, 6.0, 65.0, 0.675, perMl = 100.0,
            source = "Community-cited apparent half-life ≈2.7 d"),
        preset("deca", "Nandrolone decanoate", "Nandrolone", INJ, 1, 12.0, 168.0, 0.640, perMl = 250.0,
            source = "Release half-life ≈6–7 d (Wijnand 1985, PMID 3865478)"),
        // Trenbolone (MW 270.37)
        preset("tren-ace", "Trenbolone acetate", "Trenbolone", INJ, 2, 4.0, 24.0, 0.865, perMl = 100.0,
            source = "Community-cited apparent half-life ≈1 d"),
        preset("tren-enan", "Trenbolone enanthate", "Trenbolone", INJ, 2, 10.0, 120.0, 0.707, perMl = 200.0,
            source = "Community-cited apparent half-life ≈5 d"),
        // Drostanolone (MW 304.47)
        preset("mast-prop", "Drostanolone propionate", "Drostanolone", INJ, 3, 6.0, 19.0, 0.844, perMl = 100.0,
            source = "Community-cited apparent half-life ≈0.8 d"),
        preset("mast-enan", "Drostanolone enanthate", "Drostanolone", INJ, 3, 10.0, 120.0, 0.731, perMl = 200.0,
            source = "Community-cited apparent half-life ≈5 d"),
        // Methenolone (MW 302.45), boldenone (MW 286.41)
        preset("primo-enan", "Methenolone enanthate", "Methenolone", INJ, 4, 10.0, 120.0, 0.729, perMl = 100.0,
            source = "Community-cited apparent half-life ≈5 d"),
        preset("eq", "Boldenone undecylenate", "Boldenone", INJ, 5, 24.0, 336.0, 0.633, perMl = 300.0,
            source = "Community-cited apparent half-life ≈14 d"),
        // hCG
        preset("hcg", "hCG (SC)", "hCG", CompoundCategory.HCG, 6, 6.0, 33.0, base = BaseUnit.IU,
            source = "Label terminal half-life ≈29–33 h (choriogonadotropin), tmax 12–24 h"),
        // Aromatase inhibitors
        preset("anastrozole", "Anastrozole", "Anastrozole", CompoundCategory.AI, 7, 0.5, 46.0, perTablet = 1.0,
            source = "Label terminal half-life ≈40–50 h, tmax ≈2 h"),
        preset("letrozole", "Letrozole", "Letrozole", CompoundCategory.AI, 8, 0.4, 48.0, perTablet = 2.5,
            source = "Label terminal half-life ≈2 d, tmax ≈1 h"),
        preset("exemestane", "Exemestane", "Exemestane", CompoundCategory.AI, 9, 0.4, 24.0, perTablet = 25.0,
            source = "Label terminal half-life ≈24 h, tmax ≈1–2 h"),
        // SERMs
        preset("tamoxifen", "Tamoxifen", "Tamoxifen", CompoundCategory.SERM, 10, 1.5, 132.0, perTablet = 20.0,
            source = "Label terminal half-life 5–7 d, tmax ≈5 h"),
        preset("clomiphene", "Clomiphene", "Clomiphene", CompoundCategory.SERM, 11, 2.0, 120.0, perTablet = 50.0,
            source = "Label half-life ≈5 d (isomer mixture)"),
        preset("enclomiphene", "Enclomiphene", "Enclomiphene", CompoundCategory.SERM, 11, 0.7, 10.0, perTablet = 12.5,
            source = "Published half-life ≈10 h, tmax 2–3 h"),
        // Oral androgens
        preset("oxandrolone", "Oxandrolone", "Oxandrolone", ORAL, 4, 0.3, 9.4, perTablet = 10.0,
            source = "Label half-life ≈9.4 h"),
        preset("methandienone", "Methandienone", "Methandienone", ORAL, 2, 0.4, 4.5, perTablet = 10.0,
            source = "Reported half-life 3–6 h"),
        preset("stanozolol", "Stanozolol (oral)", "Stanozolol", ORAL, 3, 0.5, 9.0, perTablet = 10.0,
            source = "Reported half-life ≈9 h"),
        preset("oxymetholone", "Oxymetholone", "Oxymetholone", ORAL, 5, 0.5, 8.5, perTablet = 50.0,
            source = "Reported half-life ≈8–9 h"),
    )

    fun byId(id: String): Compound? = all.firstOrNull { it.id == id }
}
