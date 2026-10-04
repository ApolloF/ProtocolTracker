package com.apollof.protocoltracker.domain.pk

/**
 * Parameters from a public Google Sheet of compiled PK parameters (the "PK sheet"), the source of every sheet-based
 * preset. The numbers trace back to the studies it cites.
 *
 * Provenance: the sheet (one tab),
 * https://docs.google.com/spreadsheets/d/1Hrbw5eXb8bw1YfZZmBHHS3IY_j95V5DJsc6-piO__Ns, CSV export of [EXPORTED].
 * The values are copied from the sheet unchanged (days, Cmax in ng/dL per unit dosed, bioavailability F); the export's
 * numeric columns are kept in `core/domain/src/test/resources/pk-sheet-2026-10-04.csv` and
 * `PkSheetTest` checks every row here against it.
 *
 * [SheetRow.multiplier] is not a sheet column: the sheet only says whether a multiplier is used. The values are the
 * per-compound multipliers of the model ([PkSheetModel]), which applies them to every curve.
 * Sheet rows without a preset are listed in docs/MODELS.md.
 */
object PkSheet {
    const val URL = "https://docs.google.com/spreadsheets/d/1Hrbw5eXb8bw1YfZZmBHHS3IY_j95V5DJsc6-piO__Ns"
    const val EXPORTED = "2026-10-04"

    /** The sheet's "Model" column: Advanced = study Cmax and Tmax; Basic = half-life and F only. */
    enum class Model { ADVANCED, BASIC }

    /**
     * One sheet row. [compound] and [type] are the sheet's own labels ("---" = none). Null numbers are "---" or "n/a"
     * in the sheet. [multiplierUsed] is the sheet's "Multiplier Used" (null = "---").
     */
    data class SheetRow(
        val compound: String,
        val type: String,
        val halfLifeD: Double?,
        val cmax: Double?,
        val tmaxD: Double?,
        val bioavailability: Double?,
        val model: Model,
        val multiplierUsed: Boolean?,
        val multiplier: Double,
    )

    // Test E and Test C: the sheet says "No" multiplier, but the model scales them.
    val TEST_E = SheetRow("Testosterone", "Enanthate", 7.19, 11.3095, 1.3875, 0.72, Model.ADVANCED, multiplierUsed = false, multiplier = 0.33)
    val TEST_C = SheetRow("Testosterone", "Cypionate", 6.9, 5.56, 4.5, 0.7, Model.ADVANCED, multiplierUsed = false, multiplier = 0.6)
    val TEST_P = SheetRow("Testosterone", "Propionate", 1.0375, 26.0, 1.0625, 0.84, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    // The sheet's "Testosterone | Trestolone" row (t½ 53 d) is a 53-day Nebido form, mislabelled; it has no preset.
    val TEST_U_CASTOR = SheetRow("Testosterone", "Undecanoate (Caster Oil)", 33.9, 1.187, 10.0, 0.65, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val TEST_U_MCT = SheetRow("Testosterone", "Undecanoate (MCT Oil)", 20.0, 1.85, 8.5, 0.65, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val TEST_U_ORAL = SheetRow("Testosterone", "Undecanoate (Oral)", 0.7666666667, 2.4876225, 0.2041666667, 0.0683, Model.ADVANCED, multiplierUsed = true, multiplier = 0.7583841)
    val TEST_PP = SheetRow("Testosterone", "Phenylpropionate", 2.5, null, null, 0.69, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    // Test Iso: the sheet says "No" multiplier, but the model uses 0.6.
    val TEST_ISO = SheetRow("Testosterone", "Isocaproate", 3.1, null, null, 0.75, Model.BASIC, multiplierUsed = false, multiplier = 0.6)
    val TEST_SUSP = SheetRow("Testosterone", "Suspension", 1.375, 5.067, 0.25, 1.0, Model.ADVANCED, multiplierUsed = true, multiplier = 6.0)
    val TEST_DEC = SheetRow("Testosterone", "Decanoate", 5.6, null, null, 0.65, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val T3 = SheetRow("T3 (Triiodothyronine)", "---", 0.9183333333, 6.92, 0.1041666667, null, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val TREN_E = SheetRow("Trenbolone", "Enanthate", 11.0, null, null, 0.71, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val TREN_A = SheetRow("Trenbolone", "Acetate", 1.5, null, null, 0.87, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val TREN_HEX = SheetRow("Trenbolone", "Hexahydrobenzylcarbonate", 8.0, null, null, 0.66, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val MENT = SheetRow("Trestolone Acetate (MENT)", "---", 0.1555555556, null, null, 0.87, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val MAST_P = SheetRow("Masteron", "Propionate", 2.0, null, null, 0.84, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val MAST_E = SheetRow("Masteron", "Enanthate", 4.5, null, null, 0.73, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val PROVIRON = SheetRow("Mesterolone (Proviron)", "---", 0.5208333333, 12.4, 0.06666666667, 0.03, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val NPP = SheetRow("Nandrolone (Deca/NPP)", "Phenylpropionate", 2.4, 8.91465, 1.0, 0.67, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val DECA = SheetRow("Nandrolone (Deca/NPP)", "Decanoate", 10.2, 3.99, 1.833, 0.73, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val EQ = SheetRow("Boldenone Undecylenate (Equipoise)", "---", 5.125, 1.098, 6.0, 0.63, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val DHB = SheetRow("Dihydroboldenone (DHB)", "---", 9.0, null, null, 1.0, Model.BASIC, multiplierUsed = true, multiplier = 0.7)
    val PRIMO_ORAL = SheetRow("Primobolan", "Oral", 0.2083, null, null, 0.88, Model.BASIC, multiplierUsed = false, multiplier = 1.0)
    val PRIMO_INJ = SheetRow("Primobolan", "Injectable", 10.5, null, null, 0.73, Model.BASIC, multiplierUsed = true, multiplier = 0.5)
    val HALOTESTIN = SheetRow("Halotestin", "---", 0.083333, 800.0, 0.075, 0.57, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val ANADROL = SheetRow("Anadrol", "---", 0.3326388, 37.6, 0.1458333, 0.95, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val DIANABOL = SheetRow("Dianabol", "---", 0.21875, null, null, 0.95, Model.BASIC, multiplierUsed = true, multiplier = 10.0)
    val TURINABOL = SheetRow("Turinabol", "---", 0.666667, null, null, 1.0, Model.BASIC, multiplierUsed = true, multiplier = 7.0)
    val WINSTROL_ORAL = SheetRow("Winstrol", "Oral", 0.375, null, null, 1.0, Model.BASIC, multiplierUsed = true, multiplier = 5.0)
    val WINSTROL_INJ = SheetRow("Winstrol", "Injectable", 3.42, 8.12, 7.0, 1.0, Model.ADVANCED, multiplierUsed = true, multiplier = 1.3)
    // Anavar uses the sheet's Cmax 772; another source gives 676 (docs/MODELS.md, differences).
    val ANAVAR = SheetRow("Anavar", "---", 0.2791666, 772.0, 0.06666667, 0.625, Model.ADVANCED, multiplierUsed = true, multiplier = 0.3)
    val SUPERDROL = SheetRow("Superdrol", "---", 0.4166667, null, null, 0.5, Model.BASIC, multiplierUsed = true, multiplier = 20.0)
    val HCG = SheetRow("HCG (100 IU ≈ 0.01 mg / 10 μg)", "---", 1.9625, 2072.0, 1.0, 0.45, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val HGH = SheetRow("Human Growth Hormone (HGH) (1mg ≈ 3 IU)", "---", 0.1722222222, 622.9, 0.22083333, 0.63, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val SEMAGLUTIDE = SheetRow("Semaglutide", "Injections (Ozempic/Wegovy)", 6.5, 2879.1, 1.25, 0.89, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val SEMAGLUTIDE_ORAL = SheetRow("Semaglutide", "Oral (Rybelsus)", 0.54, 232.38, 0.059027778, 0.008, Model.ADVANCED, multiplierUsed = true, multiplier = 1.5)
    val TIRZEPATIDE = SheetRow("Tirzepatide (Mounjaro)", "---", 4.8625, 5826.67, 1.5, 0.8, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val RETATRUTIDE = SheetRow("Retatrutide", "---", 6.1388888, 11495.37037, 1.681944458, 0.8, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val MAZDUTIDE = SheetRow("Mazdutide", "---", 18.1, 7348.3333, 3.01458, null, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val ARIMIDEX = SheetRow("Arimidex", "---", 1.95, 3930.0, 0.04166666667, 0.8, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val AROMASIN = SheetRow("Aromasin", "---", 0.945833333, 57.6, 0.059375, 0.05, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val CLENBUTEROL = SheetRow("Clenbuterol (Oral)", "---", 1.106666667, 0.333, 0.1083333333, null, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val TAMOXIFEN = SheetRow("Nolvadex (Tamoxifen)", "---", 1.975, 200.0, 0.3441666667, 0.15, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val CLOMID = SheetRow("Clomid", "---", 5.0, 40.0, 0.2083333333, 0.95, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val CABERGOLINE = SheetRow("Cabergoline", "---", 3.583333333, 4.03, 0.083, 0.465, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val LETROZOLE = SheetRow("Letrozole", "---", 1.389583333, 45.684, 0.0775, null, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val NEBIVOLOL = SheetRow("Nebivolol", "---", 0.7054166667, 11.6, 0.1295833333, 0.54, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val TELMISARTAN = SheetRow("Telmistartan", "---", 0.9708333333, 770.0625, 0.07083333333, 0.43, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)
    val TADALAFIL = SheetRow("Tadalafil", "---", 0.7083333333, 1725.0, 0.1041666667, null, Model.ADVANCED, multiplierUsed = false, multiplier = 1.0)

    /** Every row a preset uses, in sheet order. */
    val rows: List<SheetRow> = listOf(
        TEST_E, TEST_C, TEST_P, TEST_U_CASTOR, TEST_U_MCT, TEST_U_ORAL, TEST_PP, TEST_ISO, TEST_SUSP, TEST_DEC, T3,
        TREN_E, TREN_A, TREN_HEX, MENT, MAST_P, MAST_E, PROVIRON, NPP, DECA, EQ, DHB, PRIMO_ORAL, PRIMO_INJ,
        HALOTESTIN, ANADROL, DIANABOL, TURINABOL, WINSTROL_ORAL, WINSTROL_INJ, ANAVAR, SUPERDROL, HCG, HGH,
        SEMAGLUTIDE, SEMAGLUTIDE_ORAL, TIRZEPATIDE, RETATRUTIDE, MAZDUTIDE, ARIMIDEX, AROMASIN, CLENBUTEROL, TAMOXIFEN,
        CLOMID, CABERGOLINE, LETROZOLE, NEBIVOLOL, TELMISARTAN, TADALAFIL,
    )
}
