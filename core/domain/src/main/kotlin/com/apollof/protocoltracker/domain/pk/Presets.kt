package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.Compound
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.Route
import com.apollof.protocoltracker.domain.model.SupportKind

/** Chip colours chosen to stay distinguishable and ≥3:1 against both light and dark surfaces. */
object CompoundColors {
    val palette: List<Long> = listOf(
        0xFF2E7D6BL, 0xFF3F6FD8L, 0xFFC2453DL, 0xFFB7791FL, 0xFF8A4FD1L, 0xFF1F8FB5L,
        0xFFD0527FL, 0xFF5E8C2EL, 0xFF9A6B3FL, 0xFF6B7280L, 0xFFE0702BL, 0xFF3A9A8FL,
    )
}

/**
 * Preset library. Kinetics of every compound in the PK sheet come from that sheet and its
 * model ([PkSheet], [PkSheetModel], docs/MODELS.md); the few compounds the sheet lacks cite their label
 * or study. F (active fraction) only matters for relative curves. Values are estimates for level curves, not validated
 * concentration models.
 */
object Presets {
    /** Bump when preset data changes; seeding then refreshes presets the user has not edited. */
    const val VERSION = "presets-2026-10b"

    private const val SHEET = "PK sheet"

    private val INJ = CompoundCategory.INJECTABLE_STEROID
    private val ORAL = CompoundCategory.ORAL_STEROID
    private val SUP = CompoundCategory.SUPPORT
    private val PEP = CompoundCategory.PEPTIDE

    /** Time to peak for a compound outside the sheet whose source gives none: a fifth of the half-life (1 h – 2 d). */
    fun estimatedTmaxH(halfLifeH: Double, route: Route): Double =
        if (route == Route.INJECTION) (0.2 * halfLifeH).coerceIn(1.0, 48.0) else 1.5

    private fun compound(
        id: String, common: String, name: String, group: String, category: CompoundCategory, route: Route,
        color: Int, pk: PkParams?, source: String, kind: SupportKind? = null, base: BaseUnit = BaseUnit.MG,
        perMl: Double? = null, perTablet: Double? = null,
    ) = Compound(
        id = "preset:$id", name = name, commonName = common, group = group, category = category, supportKind = kind,
        route = route, baseUnit = base, colorArgb = CompoundColors.palette[color % CompoundColors.palette.size], pk = pk,
        defaultFormulation = Formulation(perMl, perTablet), sourceNote = "$source ($VERSION)", isPreset = true,
    )

    private val IM = Route.INJECTION
    private val PO = Route.ORAL
    private val S = PkSheet

    /** Kinetics from a sheet row; [f] overrides the sheet's F where that is not the ester fraction (EsterFractionTest). */
    private fun sp(row: PkSheet.SheetRow, unit: LevelUnit = LevelUnit.NG_DL, perBaseUnit: Double = 1.0, f: Double? = null) =
        PkSheetModel.pk(row, unit, perBaseUnit, f)

    val all: List<Compound> = listOf(
        // --- Injectable steroids -------------------------------------------------------------------
        compound("test-enan", "Test E", "testosterone enanthate", "Testosterone", INJ, IM, 0, sp(S.TEST_E),
            "$SHEET: PMC4721027; ajpendo.00502.2001", perMl = 250.0),
        compound("test-cyp", "Test C", "testosterone cypionate", "Testosterone", INJ, IM, 0, sp(S.TEST_C),
            "$SHEET: tau.amegroups.org 11328; psp4.12287", perMl = 200.0),
        compound("test-prop", "Test P", "testosterone propionate", "Testosterone", INJ, IM, 0, sp(S.TEST_P),
            "$SHEET: JCEM 63(6):1361", perMl = 100.0),
        compound("test-undec", "Nebido", "testosterone undecanoate", "Testosterone", INJ, IM, 0, sp(S.TEST_U_CASTOR, f = 0.63),
            "$SHEET (castor oil): jandrol.109.009597; Nebido PI", perMl = 250.0),
        compound("test-undec-mct", "Test U MCT", "testosterone undecanoate in MCT oil", "Testosterone", INJ, IM, 0,
            sp(S.TEST_U_MCT, f = 0.63), "$SHEET: estimated values, no study", perMl = 250.0),
        compound("test-pp", "Test PP", "testosterone phenylpropionate", "Testosterone", INJ, IM, 0, sp(S.TEST_PP),
            "$SHEET: PMC9611952", perMl = 100.0),
        compound("test-iso", "Test Iso", "testosterone isocaproate", "Testosterone", INJ, IM, 0, sp(S.TEST_ISO),
            "$SHEET: PMC9611952", perMl = 100.0),
        compound("test-dec", "Test Dec", "testosterone decanoate", "Testosterone", INJ, IM, 0, sp(S.TEST_DEC),
            SHEET, perMl = 100.0),
        compound("test-susp", "Test Susp", "testosterone suspension", "Testosterone", INJ, IM, 0, sp(S.TEST_SUSP),
            "$SHEET: RMTC interlaboratory study (horses, scaled)", perMl = 100.0),
        compound("deca", "Deca", "nandrolone decanoate", "Nandrolone", INJ, IM, 1, sp(S.DECA, f = 0.64),
            "$SHEET: JCEM 90(5):2624", perMl = 250.0),
        compound("npp", "NPP", "nandrolone phenylpropionate", "Nandrolone", INJ, IM, 1, sp(S.NPP),
            "$SHEET: PMID 9103484", perMl = 100.0),
        compound("tren-ace", "Tren A", "trenbolone acetate", "Trenbolone", INJ, IM, 2, sp(S.TREN_A),
            "$SHEET: S002228602030452X", perMl = 100.0),
        compound("tren-enan", "Tren E", "trenbolone enanthate", "Trenbolone", INJ, IM, 2, sp(S.TREN_E),
            "$SHEET: S002228602030452X", perMl = 200.0),
        compound("tren-hex", "Parabolan", "trenbolone hexahydrobenzylcarbonate", "Trenbolone", INJ, IM, 2, sp(S.TREN_HEX),
            "$SHEET: S002228602030452X", perMl = 76.0),
        compound("mast-prop", "Masteron P", "drostanolone propionate", "Drostanolone", INJ, IM, 3, sp(S.MAST_P),
            "$SHEET: Llewellyn 2011", perMl = 100.0),
        compound("mast-enan", "Masteron E", "drostanolone enanthate", "Drostanolone", INJ, IM, 3, sp(S.MAST_E),
            "$SHEET: low-quality sources", perMl = 200.0),
        compound("primo-enan", "Primobolan Depot", "methenolone enanthate", "Methenolone", INJ, IM, 4, sp(S.PRIMO_INJ),
            "$SHEET: Llewellyn", perMl = 100.0),
        compound("eq", "EQ", "boldenone undecylenate", "Boldenone", INJ, IM, 5, sp(S.EQ),
            "$SHEET: PMID 17348894", perMl = 300.0),
        // The sheet's boldenone cypionate row is empty ("Cloned settings from Testosterone Cypionate"); the preset uses Test C.
        compound("bold-cyp", "Boldenone Cyp", "boldenone cypionate", "Boldenone", INJ, IM, 5, sp(S.TEST_C),
            "$SHEET: kinetics cloned from Test C", perMl = 200.0),
        compound("dhb", "DHB", "1-testosterone cypionate", "1-Testosterone", INJ, IM, 6, sp(S.DHB, f = 0.70),
            "$SHEET: low-quality sources", perMl = 100.0),
        compound("winstrol-depot", "Winstrol Depot", "stanozolol (injectable)", "Stanozolol", INJ, IM, 7, sp(S.WINSTROL_INJ),
            "$SHEET: PMID 17348894", perMl = 50.0),
        compound("ment", "MENT", "trestolone acetate", "Trestolone", INJ, IM, 8, sp(S.MENT),
            "$SHEET: Helsinki thesis", perMl = 50.0),

        // --- Oral steroids -------------------------------------------------------------------------
        compound("oxandrolone", "Anavar", "oxandrolone", "Oxandrolone", ORAL, PO, 4, sp(S.ANAVAR),
            "$SHEET: PMC7134583", perTablet = 10.0),
        compound("methandienone", "Dianabol", "methandienone", "Methandienone", ORAL, PO, 2, sp(S.DIANABOL),
            "$SHEET: reported half-life; no reliable bioavailability", perTablet = 10.0),
        compound("oxymetholone", "Anadrol", "oxymetholone", "Oxymetholone", ORAL, PO, 5, sp(S.ANADROL),
            "$SHEET: oxymetholone GC-MS PK study", perTablet = 50.0),
        compound("stanozolol", "Winstrol", "stanozolol", "Stanozolol", ORAL, PO, 7, sp(S.WINSTROL_ORAL),
            "$SHEET: Llewellyn", perTablet = 10.0),
        compound("turinabol", "Turinabol", "chlorodehydromethyltestosterone", "Chlorodehydromethyltestosterone", ORAL, PO, 9,
            sp(S.TURINABOL), "$SHEET: PMID 1798729", perTablet = 10.0),
        compound("halotestin", "Halotestin", "fluoxymesterone", "Fluoxymesterone", ORAL, PO, 10, sp(S.HALOTESTIN),
            "$SHEET: PMID 4009439", perTablet = 10.0),
        compound("superdrol", "Superdrol", "methasterone", "Methasterone", ORAL, PO, 11, sp(S.SUPERDROL),
            SHEET, perTablet = 10.0),
        compound("proviron", "Proviron", "mesterolone", "Mesterolone", ORAL, PO, 1, sp(S.PROVIRON),
            "$SHEET: Proviron PI (Bayer)", perTablet = 25.0),
        compound("primo-oral", "Primobolan", "methenolone acetate", "Methenolone", ORAL, PO, 4, sp(S.PRIMO_ORAL),
            "$SHEET: low-quality sources", perTablet = 25.0),
        compound("test-undec-oral", "Andriol", "testosterone undecanoate (oral)", "Testosterone", ORAL, PO, 0, sp(S.TEST_U_ORAL),
            "$SHEET: PMC4168025; PMID 3770015", perTablet = 40.0),

        // --- Support ---------------------------------------------------------------------------------
        compound("anastrozole", "Arimidex", "anastrozole", "Anastrozole", SUP, PO, 7,
            sp(S.ARIMIDEX, LevelUnit.NG_ML), "$SHEET: PMID 19470631", SupportKind.AI, perTablet = 1.0),
        compound("exemestane", "Aromasin", "exemestane", "Exemestane", SUP, PO, 8,
            sp(S.AROMASIN, LevelUnit.NG_ML), "$SHEET: PMC1884784; PMID 20678561", SupportKind.AI, perTablet = 25.0),
        compound("letrozole", "Femara", "letrozole", "Letrozole", SUP, PO, 9,
            sp(S.LETROZOLE, LevelUnit.NG_ML), "$SHEET: PMID 16229115", SupportKind.AI, perTablet = 2.5),
        compound("tamoxifen", "Nolvadex", "tamoxifen", "Tamoxifen", SUP, PO, 10,
            sp(S.TAMOXIFEN, LevelUnit.NG_ML), "$SHEET: Nolvadex FDA biopharm review", SupportKind.SERM, perTablet = 20.0),
        compound("clomiphene", "Clomid", "clomiphene", "Clomiphene", SUP, PO, 11,
            sp(S.CLOMID, LevelUnit.NG_ML), "$SHEET: PMID 19033451", SupportKind.SERM, perTablet = 50.0),
        compound("enclomiphene", "", "enclomiphene", "Enclomiphene", SUP, PO, 11,
            PkParams(10.0, 2.5, null, 1.0), "Published half-life ≈10 h, tmax 2–3 h", SupportKind.SERM, perTablet = 12.5),
        // hCG: the sheet's Cmax is per mg with 100 IU ≈ 0.01 mg, so per IU = Cmax × 1e-4.
        compound("hcg", "hCG", "human chorionic gonadotropin", "hCG", SUP, IM, 6,
            sp(S.HCG, LevelUnit.NG_ML, perBaseUnit = 1e-4), "$SHEET: PMC8301557; PMID 12470572",
            SupportKind.FERTILITY, base = BaseUnit.IU),
        compound("cabergoline", "Caber", "cabergoline", "Cabergoline", SUP, PO, 3,
            sp(S.CABERGOLINE, LevelUnit.PG_ML), "$SHEET: PMID 12844325", SupportKind.PROLACTIN, perTablet = 0.5),
        compound("telmisartan", "", "telmisartan", "Telmisartan", SUP, PO, 5,
            sp(S.TELMISARTAN, LevelUnit.NG_ML), "$SHEET: PMID 17009837; PMID 11185629", SupportKind.CARDIO, perTablet = 40.0),
        compound("nebivolol", "", "nebivolol", "Nebivolol", SUP, PO, 1,
            sp(S.NEBIVOLOL, LevelUnit.NG_ML), "$SHEET: PMID 24845234; PMC4576779", SupportKind.CARDIO, perTablet = 5.0),
        compound("tadalafil", "Cialis", "tadalafil", "Tadalafil", SUP, PO, 2,
            sp(S.TADALAFIL, LevelUnit.NG_ML), "$SHEET: PMC1885023", SupportKind.SEXUAL_HEALTH, perTablet = 5.0),
        // T3 and clenbuterol: the sheet's Cmax is per mcg dosed; converted to per mg.
        compound("liothyronine", "T3", "liothyronine", "Liothyronine", SUP, PO, 8,
            sp(S.T3, perBaseUnit = 1000.0), "$SHEET: PMC5167556 (Cmax per mcg)", SupportKind.THYROID, perTablet = 0.025),
        compound("clenbuterol", "Clen", "clenbuterol", "Clenbuterol", SUP, PO, 10,
            sp(S.CLENBUTEROL, LevelUnit.PG_ML, perBaseUnit = 1000.0), "$SHEET: PMC4694390 (Cmax per mcg)", SupportKind.OTHER, perTablet = 0.04),

        // --- Peptides ----------------------------------------------------------------------------------
        compound("semaglutide", "", "semaglutide", "Semaglutide", PEP, IM, 1,
            sp(S.SEMAGLUTIDE, LevelUnit.NG_ML), "$SHEET: PMC7854449; s13300-019-0581-y"),
        compound("semaglutide-oral", "", "semaglutide (oral)", "Semaglutide", PEP, PO, 1,
            sp(S.SEMAGLUTIDE_ORAL, LevelUnit.NG_ML), "$SHEET: Rybelsus EPAR (scaled to match injectable levels)", perTablet = 7.0),
        compound("tirzepatide", "", "tirzepatide", "Tirzepatide", PEP, IM, 4,
            sp(S.TIRZEPATIDE, LevelUnit.NG_ML), "$SHEET: PMC9268041; NBK585056"),
        compound("retatrutide", "", "retatrutide", "Retatrutide", PEP, IM, 6,
            sp(S.RETATRUTIDE, LevelUnit.NG_ML), "$SHEET: Cell Metab S1550-4131(22)00312-6"),
        compound("mazdutide", "", "mazdutide", "Mazdutide", PEP, IM, 11,
            sp(S.MAZDUTIDE, LevelUnit.NG_ML), "$SHEET: PMC9561728 (half-life range 7.3–44.8 d, geometric mean)"),
        compound("liraglutide", "", "liraglutide", "Liraglutide", PEP, IM, 5,
            PkParams(13.0, 11.0, 35.0 / 0.6 * 100, 0.55, LevelUnit.NG_ML), "Victoza label: 0.6 mg SC Cmax 35 ng/mL, tmax 8–12 h, t½ ≈13 h, F ≈55%"),
        compound("cagrilintide", "", "cagrilintide", "Cagrilintide", PEP, IM, 9,
            PkParams(177.0, 48.0, null, 1.0), "Lancet 2021 phase 1b (S014067362100845X): t½ 159–195 h, tmax 24–72 h; relative curve"),
        // HGH: the sheet's Cmax is per mg with 1 mg ≈ 3 IU, so per IU = Cmax / 3.
        compound("hgh", "HGH", "somatropin", "Somatropin", PEP, IM, 3,
            sp(S.HGH, LevelUnit.NG_ML, perBaseUnit = 1.0 / 3), "$SHEET: Pediatr Res 1988; PMC2014216", base = BaseUnit.IU),
        compound("tesamorelin", "", "tesamorelin", "Tesamorelin", PEP, IM, 7,
            PkParams(0.53, 0.15, 3.831 / 2 * 100, 0.04, LevelUnit.PG_ML), "Egrifta label: 2 mg SC Cmax 3 831 pg/mL, tmax 0.15 h, t½ 26–38 min, F ≈4%"),
        compound("ipamorelin", "", "ipamorelin", "Ipamorelin", PEP, IM, 2,
            PkParams(2.0, estimatedTmaxH(2.0, IM), null, 1.0), "Gobburu et al. 1999 (J Clin Pharmacol): t½ ≈2 h; relative curve"),
        compound("cjc-1295", "", "CJC-1295 (no DAC)", "CJC-1295", PEP, IM, 10, null,
            "No reliable human level data"),
        compound("cjc-1295-dac", "", "CJC-1295 DAC", "CJC-1295 DAC", PEP, IM, 10,
            PkParams(166.8, estimatedTmaxH(166.8, IM), null, 1.0), "Teichman et al. 2006 (JCEM): t½ 5.8–8.1 d; relative curve"),
        compound("pt-141", "PT-141", "bremelanotide", "Bremelanotide", PEP, IM, 8,
            PkParams(2.7, 1.0, 72.8 / 1.75 * 100, 1.0, LevelUnit.NG_ML), "Vyleesi label: 1.75 mg SC Cmax 72.8 ng/mL, tmax 1 h, t½ 2.7 h, F ≈100%"),
        compound("mk-677", "MK-677", "ibutamoren", "Ibutamoren", PEP, PO, 0, null,
            "Reported half-lives conflict (≈4–6 h vs ≈24 h); no curve", perTablet = 25.0),
        compound("bpc-157", "", "BPC-157", "BPC-157", PEP, IM, 5, null, "No reliable human level data"),
        compound("tb-500", "", "TB-500", "TB-500", PEP, IM, 6, null, "No reliable human level data"),
        compound("ghk-cu", "", "GHK-Cu", "GHK-Cu", PEP, IM, 11, null, "No reliable human level data"),
        compound("melanotan-2", "", "Melanotan II", "Melanotan II", PEP, IM, 2, null, "No reliable human level data"),
        compound("aod-9604", "", "AOD-9604", "AOD-9604", PEP, IM, 3, null, "No reliable human level data"),
        compound("mots-c", "", "MOTS-c", "MOTS-c", PEP, IM, 4, null, "No reliable human level data"),
    )

    fun byId(id: String): Compound? = all.firstOrNull { it.id == id }
}
