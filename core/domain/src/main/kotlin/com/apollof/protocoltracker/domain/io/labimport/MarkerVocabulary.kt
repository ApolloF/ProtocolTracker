package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.model.BloodMarkers
import com.apollof.protocoltracker.domain.model.BloodworkRules

/**
 * The printed names of one marker (import doc §5.4). [aliases] are listed in the AI prompt, as printed; [variants] are
 * matched but not listed; a name holding one of the [never] words is another test; a [confirm] name is kept unlisted
 * with a one-tap option (plain "Glucose"); [promptNote] is the prompt's notes field.
 */
data class MarkerNames(
    val key: String,
    val aliases: List<String>,
    val variants: List<String> = emptyList(),
    val never: Set<String> = emptySet(),
    val confirm: List<String> = emptyList(),
    val promptNote: String? = null,
)

/**
 * A unit a marker is accepted in (import doc §5.6): stored = printed × [factor], also for the lab range. [display] is the
 * spelling the prompt lists. [onlyByName]: accepted only when the printed name is one of the marker's own names.
 */
data class AcceptedUnit(val display: String, val factor: Double, val onlyByName: Boolean = false)

/** What the tables make of a printed name alone (import doc §5.2). */
sealed interface NameMatch {
    /** An alias or variant of [key]. */
    data class Known(val key: String) : NameMatch

    /** A name that may mean [key] but does not say enough (plain "Glucose"). */
    data class Confirm(val key: String) : NameMatch

    /** A global exclusion: a `/` between names, or ratio, quotient, index or verhouding. Never a known marker. */
    data object Excluded : NameMatch

    data object Unknown : NameMatch
}

/**
 * Which marker a result row names, from its printed name and the chatbot's key (import doc §5.3), before its numbers are
 * checked. Only [Agreed] rows may raise questions; [ByName] and [ByKey] rows need clean numbers, else they are unlisted.
 */
sealed interface Identity {
    /** The name and the key agree on [key]. */
    data class Agreed(val key: String) : Identity

    /** The name is one of [key]'s names and the chatbot's key differs or is missing (C3 when clean). */
    data class ByName(val key: String) : Identity

    /** The tables do not know the name; the chatbot's known [key] (C4 when clean). */
    data class ByKey(val key: String) : Identity

    /**
     * An unlisted result. [ruledOut]: the chatbot's known key that the name rules out (C2). [confirm]: the marker the
     * owner may save it as with one tap (C11 when the chatbot's key was that marker).
     */
    data class Unlisted(val ruledOut: String? = null, val confirm: String? = null) : Identity
}

/**
 * Import knowledge about [BloodMarkers] (import doc §5): printed names, never words and accepted units. Names are looked
 * up through [BloodworkRules.normalizeName] and units through [BloodworkRules.normalizeUnit]; matches are exact, never
 * fuzzy. Every marker has an entry (`MarkerVocabularyTest`).
 */
object MarkerVocabulary {
    val names: List<MarkerNames> = listOf(
        MarkerNames(
            "total_testosterone",
            aliases = listOf("Testosteron", "Testosteron totaal", "Totaal testosteron", "Total testosterone"),
            variants = listOf("Testosterone", "Testosterone total", "TT"),
            never = setOf(
                "vrij", "free", "bio", "biobeschikbaar", "bioavailable", "fai", "%", "dht", "dihydrotestosteron",
                "dihydrotestosterone", "speeksel", "saliva", "salivary",
            ),
            promptNote = "not free, bioavailable or salivary testosterone, not DHT",
        ),
        MarkerNames(
            "free_testosterone",
            aliases = listOf("Vrij testosteron", "Vrij testosteron (berekend)", "Free testosterone"),
            variants = listOf("Testosteron vrij", "Berekend vrij testosteron", "Calculated free testosterone", "Free T"),
            never = setOf(
                "fai", "bio", "biobeschikbaar", "bioavailable", "%", "procent", "percent", "t3", "t4", "ft3", "ft4", "psa",
            ),
            promptNote = "not the free androgen index (FAI, vrij testosteron index), not bioavailable or % free " +
                "testosterone, not free T4 or T3",
        ),
        MarkerNames(
            "estradiol",
            aliases = listOf("Oestradiol", "Estradiol", "17-beta-oestradiol", "E2"),
            variants = listOf(
                "17-beta-estradiol", "17β-oestradiol", "17β-estradiol", "Estradiol sensitive", "Estradiol ultrasensitive",
            ),
            never = setOf("estron", "oestron", "estrone", "estriol", "oestriol"),
            promptNote = "not estrone or estriol",
        ),
        MarkerNames(
            "shbg",
            aliases = listOf("SHBG", "Sex hormone binding globulin"),
            variants = listOf("Sex-hormoonbindend globuline"),
        ),
        MarkerNames(
            "lh",
            aliases = listOf("LH", "Luteïniserend hormoon"),
            variants = listOf("Luteinizing hormone", "Luteinising hormone"),
            never = setOf("lhrh"),
        ),
        MarkerNames(
            "fsh",
            aliases = listOf("FSH", "Follikelstimulerend hormoon"),
            variants = listOf("Follikel stimulerend hormoon", "Follicle stimulating hormone"),
        ),
        MarkerNames(
            "prolactin",
            aliases = listOf("Prolactine", "Prolactin", "PRL"),
            never = setOf("macro", "macroprolactine", "macroprolactin", "peg", "monomeer", "monomeric"),
            promptNote = "not macroprolactin or prolactin after PEG",
        ),
        MarkerNames(
            "tsh",
            aliases = listOf("TSH", "Thyreotropine"),
            variants = listOf(
                "Thyrotropine", "Thyrotropin", "Thyreoidstimulerend hormoon", "Thyroid stimulating hormone",
            ),
            never = setOf("t3", "t4", "ft3", "ft4", "trab", "antistoffen", "antibodies", "receptor"),
            promptNote = "not free T4 (vrij T4, FT4) or T3",
        ),
        MarkerNames(
            "hemoglobin",
            aliases = listOf("Hemoglobine", "Hb", "Hemoglobin"),
            variants = listOf("Haemoglobine", "Haemoglobin", "Hgb"),
            never = setOf("a1c", "hba1c", "mch", "mchc", "urine", "geglyceerd", "glycated", "glycohemoglobine"),
            promptNote = "not HbA1c, MCH or MCHC",
        ),
        MarkerNames(
            "hematocrit",
            aliases = listOf("Hematocriet", "Ht", "Hematocrit"),
            variants = listOf("Haematocrit", "Hct", "PCV"),
        ),
        MarkerNames(
            "cholesterol",
            aliases = listOf("Cholesterol", "Cholesterol totaal", "Totaal cholesterol", "Total cholesterol"),
            variants = listOf("Chol"),
            never = setOf("hdl", "ldl", "vldl", "non", "niet"),
            promptNote = "not HDL, LDL or a ratio",
        ),
        MarkerNames(
            "hdl",
            aliases = listOf("HDL-cholesterol", "Cholesterol HDL", "HDL"),
            variants = listOf("HDL cholesterol", "HDL-C"),
            never = setOf("non", "niet"),
            promptNote = "not a ratio",
        ),
        MarkerNames(
            "ldl",
            aliases = listOf("LDL-cholesterol", "Cholesterol LDL", "LDL"),
            variants = listOf("LDL cholesterol", "LDL-C"),
            promptNote = "not a ratio",
        ),
        MarkerNames(
            "non_hdl",
            aliases = listOf("Non-HDL-cholesterol", "Niet-HDL-cholesterol"),
            variants = listOf("Non-HDL cholesterol", "Non HDL", "Non-HDL-C"),
        ),
        MarkerNames(
            "triglycerides",
            aliases = listOf("Triglyceriden", "Triglycerides", "TG"),
            variants = listOf("Triglyceride"),
        ),
        MarkerNames(
            "glucose",
            aliases = listOf("Glucose nuchter", "Nuchtere glucose", "Fasting glucose"),
            variants = listOf("Nuchter glucose", "Glucose fasting"),
            never = setOf("niet", "non", "random", "uur", "2h", "ogtt", "belasting", "urine", "a1c", "hba1c"),
            confirm = listOf("Glucose"),
            promptNote = "only when the report says fasting (nuchter); plain Glucose is other",
        ),
        MarkerNames(
            "creatinine",
            aliases = listOf("Kreatinine", "Creatinine"),
            variants = listOf("Kreat", "Crea"),
            never = setOf(
                "urine", "klaring", "clearance", "kreatinineklaring", "creatinineklaring", "kinase", "albumine", "albumin",
            ),
            promptNote = "not urine creatinine or creatinine clearance",
        ),
        MarkerNames(
            "egfr",
            aliases = listOf("eGFR", "eGFR (CKD-EPI)", "Geschatte GFR"),
            variants = listOf("eGFR MDRD", "Estimated GFR", "Glomerulaire filtratiesnelheid"),
            never = setOf("klaring", "clearance", "kreatinineklaring", "creatinineklaring", "cockcroft"),
            promptNote = "not creatinine clearance",
        ),
        MarkerNames(
            "albumin",
            aliases = listOf("Albumine", "Albumin"),
            variants = listOf("Alb"),
            never = setOf(
                "urine", "micro", "microalbumine", "globuline", "prealbumine", "kreatinine", "creatinine",
            ),
            promptNote = "not urine albumin or the albumin/creatinine ratio",
        ),
        MarkerNames(
            "ast",
            aliases = listOf("ASAT", "ASAT (GOT)", "AST"),
            variants = listOf(
                "GOT", "SGOT", "AST (GOT)", "ASAT/GOT", "Aspartaataminotransferase", "Aspartaat aminotransferase",
                "Aspartate aminotransferase",
            ),
        ),
        MarkerNames(
            "alt",
            aliases = listOf("ALAT", "ALAT (GPT)", "ALT"),
            variants = listOf(
                "GPT", "SGPT", "ALT (GPT)", "ALAT/GPT", "Alanineaminotransferase", "Alanine aminotransferase",
            ),
        ),
        MarkerNames(
            "ggt",
            aliases = listOf("Gamma-GT", "GGT", "γ-GT"),
            variants = listOf("Gamma GT", "G-GT", "Gamma-glutamyltransferase", "Gamma-glutamyltranspeptidase"),
        ),
        MarkerNames(
            "ck",
            aliases = listOf("CK", "Creatinekinase", "CK totaal"),
            variants = listOf("Creatine kinase", "CPK"),
            never = setOf("mb", "ckmb"),
            promptNote = "not CK-MB",
        ),
        MarkerNames(
            "psa",
            aliases = listOf("PSA", "PSA totaal"),
            variants = listOf(
                "Totaal PSA", "Total PSA", "PSA total", "Prostaatspecifiek antigeen", "Prostate specific antigen",
            ),
            never = setOf("vrij", "free", "%"),
            promptNote = "not free PSA or a PSA ratio",
        ),
    )

    private val mass = listOf(AcceptedUnit("mmol/L", 38.67), AcceptedUnit("mg/dL", 1.0))
    private val iuPerLitre = listOf(
        AcceptedUnit("U/L", 1.0), AcceptedUnit("IU/L", 1.0), AcceptedUnit("E/L", 1.0), AcceptedUnit("IE/L", 1.0),
        AcceptedUnit("mIU/mL", 1.0), AcceptedUnit("mU/mL", 1.0),
    )
    private val enzyme = listOf(
        AcceptedUnit("U/L", 1.0), AcceptedUnit("IU/L", 1.0), AcceptedUnit("E/L", 1.0),
        AcceptedUnit("µkat/L", 60.0), // 1 µkat = 1 µmol/s = 60 U
    )

    /** Accepted units per marker key, in the order the prompt lists them. */
    val units: Map<String, List<AcceptedUnit>> = mapOf(
        "total_testosterone" to listOf(
            AcceptedUnit("nmol/L", 28.84), AcceptedUnit("ng/dL", 1.0), AcceptedUnit("ng/mL", 100.0),
            AcceptedUnit("µg/L", 100.0),
        ),
        "free_testosterone" to listOf(
            AcceptedUnit("pmol/L", 0.2884), AcceptedUnit("nmol/L", 288.4), AcceptedUnit("pg/mL", 1.0),
            AcceptedUnit("ng/L", 1.0), AcceptedUnit("ng/dL", 10.0),
        ),
        "estradiol" to listOf(
            AcceptedUnit("pmol/L", 0.2724), AcceptedUnit("nmol/L", 272.4), AcceptedUnit("pg/mL", 1.0),
            AcceptedUnit("ng/L", 1.0),
        ),
        "shbg" to listOf(AcceptedUnit("nmol/L", 1.0)),
        "lh" to iuPerLitre,
        "fsh" to iuPerLitre,
        "prolactin" to listOf(
            AcceptedUnit("mU/L", 0.0472), AcceptedUnit("mIU/L", 0.0472), AcceptedUnit("mE/L", 0.0472),
            AcceptedUnit("µIU/mL", 0.0472), AcceptedUnit("U/L", 47.2), AcceptedUnit("IU/L", 47.2),
            AcceptedUnit("E/L", 47.2), AcceptedUnit("ng/mL", 1.0), AcceptedUnit("µg/L", 1.0),
        ),
        "tsh" to listOf(
            AcceptedUnit("mU/L", 1.0), AcceptedUnit("mIU/L", 1.0), AcceptedUnit("mE/L", 1.0),
            AcceptedUnit("µIU/mL", 1.0), AcceptedUnit("µU/mL", 1.0),
        ),
        "hemoglobin" to listOf(AcceptedUnit("mmol/L", 1.611), AcceptedUnit("g/dL", 1.0), AcceptedUnit("g/L", 0.1)),
        "hematocrit" to listOf(AcceptedUnit("L/L", 100.0), AcceptedUnit("%", 1.0)),
        "cholesterol" to mass,
        "hdl" to mass,
        "ldl" to mass,
        "non_hdl" to mass,
        "triglycerides" to listOf(AcceptedUnit("mmol/L", 88.57), AcceptedUnit("mg/dL", 1.0)),
        "glucose" to listOf(AcceptedUnit("mmol/L", 18.0), AcceptedUnit("mg/dL", 1.0)),
        "creatinine" to listOf(AcceptedUnit("µmol/L", 0.011312), AcceptedUnit("mg/dL", 1.0)),
        // Bare mL/min can be creatinine clearance, so it counts only under an eGFR name.
        "egfr" to listOf(AcceptedUnit("mL/min/1.73m²", 1.0), AcceptedUnit("mL/min", 1.0, onlyByName = true)),
        "albumin" to listOf(AcceptedUnit("g/L", 0.1), AcceptedUnit("g/dL", 1.0)),
        "ast" to enzyme,
        "alt" to enzyme,
        "ggt" to enzyme,
        "ck" to enzyme,
        "psa" to listOf(AcceptedUnit("µg/L", 1.0), AcceptedUnit("ng/mL", 1.0)),
    )

    private val byKey = names.associateBy { it.key }

    /** Normalized alias or variant → key. Each name is indexed by its full normalized form. */
    private val known: Map<String, String> = names.flatMap { n ->
        (n.aliases + n.variants).map { BloodworkRules.normalizeName(it).full to n.key }
    }.toMap()

    private val confirmed: Map<String, String> = names.flatMap { n ->
        n.confirm.map { BloodworkRules.normalizeName(it).full to n.key }
    }.toMap()

    /** Accepted units per key by their [BloodworkRules.normalizeUnit] form. */
    private val unitIndex: Map<String, List<Pair<String, AcceptedUnit>>> =
        units.mapValues { (_, list) -> list.map { BloodworkRules.normalizeUnit(it.display) to it } }

    fun names(key: String): MarkerNames? = byKey[key]

    /**
     * The printed name looked up exactly: the full form, then the short form (§5.2). A `/` between names is excluded
     * unless the form that matched holds it itself (`ASAT/GOT`); ratio, quotient, index and verhouding always are.
     */
    fun match(printedName: String): NameMatch {
        val n = BloodworkRules.normalizeName(printedName)
        val words = words(n.full)
        if (words.any { it in EXCLUSION_WORDS }) return NameMatch.Excluded
        val form = listOf(n.full, n.short).firstOrNull { it in known }
        if (SLASH in words && (form == null || SLASH !in words(form))) return NameMatch.Excluded
        if (form != null) return NameMatch.Known(known.getValue(form))
        val confirm = confirmed[n.full] ?: confirmed[n.short]
        return if (confirm != null) NameMatch.Confirm(confirm) else NameMatch.Unknown
    }

    /**
     * The marker a row names (§5.3). [chatbotKey] is the key cell after [LabText.normalizeKey]; `other`, an unknown key
     * or null counts as no key. The table wins over the key; the key alone counts only for a name the tables do not
     * know and that holds none of the key's `never` words.
     */
    fun identify(printedName: String, chatbotKey: String?): Identity {
        val key = chatbotKey?.takeIf { BloodMarkers.find(it) != null }
        return when (val m = match(printedName)) {
            NameMatch.Excluded -> Identity.Unlisted(ruledOut = key)
            is NameMatch.Confirm -> Identity.Unlisted(confirm = m.key)
            is NameMatch.Known -> if (m.key == key) Identity.Agreed(m.key) else Identity.ByName(m.key)
            NameMatch.Unknown -> when {
                key == null -> Identity.Unlisted()
                hasNeverWord(key, printedName) -> Identity.Unlisted(ruledOut = key)
                else -> Identity.ByKey(key)
            }
        }
    }

    /** Whether [printedName] holds one of [key]'s `never` words; `%` matches any word with a percent sign. */
    fun hasNeverWord(key: String, printedName: String): Boolean {
        val never = byKey[key]?.never ?: return false
        return words(BloodworkRules.normalizeName(printedName).full).any { word ->
            word in never || ("%" in never && '%' in word)
        }
    }

    /**
     * The accepted unit of [key] that [printedUnit] spells, or null. [byName]: the printed name is one of [key]'s own
     * names ([Identity.Agreed] or [Identity.ByName]), which [AcceptedUnit.onlyByName] units need.
     */
    fun unit(key: String, printedUnit: String, byName: Boolean): AcceptedUnit? {
        val normalized = BloodworkRules.normalizeUnit(printedUnit)
        return unitIndex[key]?.firstOrNull { (u, accepted) -> u == normalized && (byName || !accepted.onlyByName) }
            ?.second
    }

    /** [key]'s accepted units with one per distinct factor, the first spelling kept: the candidates of a missing or wrong unit. */
    fun candidates(key: String, byName: Boolean): List<AcceptedUnit> =
        units[key].orEmpty().filter { byName || !it.onlyByName }.distinctBy { it.factor }

    /**
     * Whether [printedUnit] rules [key] out: it holds one of the key's `never` words or a global exclusion word (a free
     * testosterone in `%` is the percentage free, another quantity; a `ratio` is never a marker).
     */
    fun neverUnit(key: String, printedUnit: String): Boolean {
        val never = byKey[key]?.never.orEmpty()
        return BloodworkRules.normalizeUnit(printedUnit).split(UNIT_SEPARATORS).any { part ->
            part.isNotEmpty() && (part in never || part in EXCLUSION_WORDS || ("%" in never && '%' in part))
        }
    }

    private fun words(form: String): List<String> = form.split(' ').filter { it.isNotEmpty() }

    private const val SLASH = "/"
    private val EXCLUSION_WORDS = setOf("ratio", "quotient", "index", "verhouding")
    private val UNIT_SEPARATORS = Regex("[^a-z0-9%]+")
}
