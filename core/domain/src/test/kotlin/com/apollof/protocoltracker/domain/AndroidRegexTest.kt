package com.apollof.protocoltracker.domain

import com.apollof.protocoltracker.domain.io.labimport.BloodworkImport
import com.apollof.protocoltracker.domain.io.labimport.ImportRead
import com.apollof.protocoltracker.domain.units.DecimalInput
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.util.regex.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Android compiles and runs regexes with ICU, not the JVM's engine. ICU rejects some patterns the JVM accepts (a brace
 * that is not part of a quantifier crashed Bloodwork in v0.5.0-dev.1–4) and gives `\d`, `\s`, `\w`, `\b` and `(?i)`
 * their Unicode meaning (`\d` matches `٢`, `\s` a no-break space). Every regex lives in a domain property, so this test
 * reaches all of them: it checks each for ICU's syntax and reads the import under ICU's character classes.
 * `IcuRegexTest` (androidTest) runs the same regexes on a device.
 */
class AndroidRegexTest {
    @Test
    fun everyRegexInTheCodebaseIsADomainProperty() {
        val root = File("../..").canonicalFile
        val sources = listOf("app", "core/data", "core/domain").flatMap { module ->
            File(root, "$module/src/main").walk().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue(sources.size > 50, "found only ${sources.size} source files under $root")
        val misplaced = sources.flatMap { file ->
            val inDomain = file.invariantSeparatorsPath.contains("/core/domain/")
            file.readLines().withIndex()
                .filter { (_, line) -> CONSTRUCTION.containsMatchIn(line.substringBefore("//")) && !line.trimStart().startsWith("*") }
                .filter { (_, line) -> !inDomain || !(PROPERTY.containsMatchIn(line) || LIST_ELEMENT.containsMatchIn(line)) }
                .map { (i, line) -> "${file.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList(), misplaced, "build each Regex once, as a property of a domain class, so these tests check it")
    }

    @Test
    fun everyDomainRegexIsOneIcuAccepts() {
        val regexes = domainRegexes()
        assertTrue(regexes.size > 40, "found only ${regexes.size} regexes; the class scan is broken")
        val bad = regexes.mapNotNull { (owner, regex) -> icuError(regex.toPattern())?.let { "$owner: ${regex.pattern} ($it)" } }
        assertEquals(emptyList(), bad)
    }

    @Test
    fun checkerFlagsWhatIcuRefuses() {
        // Each refusal below was seen on an Android 15 emulator; the JVM compiles all of them.
        assertTrue(icuError(Pattern.compile("\\{[^}]*}")) != null)
        assertTrue(icuError(Pattern.compile("a}")) != null)
        assertTrue(icuError(Pattern.compile("\\pL")) != null)
        assertTrue(icuError(Pattern.compile("[a-z--b]")) != null) // ICU: set difference
        assertTrue(icuError(Pattern.compile("a", Pattern.CANON_EQ)) != null)
        assertTrue(icuError(Pattern.compile("\\d", Pattern.UNICODE_CHARACTER_CLASS)) != null)
        assertTrue(icuError(Pattern.compile("(?U)\\d")) != null)
        assertEquals(null, icuError(Pattern.compile("\\{[^\\}]*\\}")))
        assertEquals(null, icuError(Pattern.compile("\\d{1,2}x{3}y{2,}\\p{L}\\P{Mn}[\\{\\}][a-z&&[^b]][\\w-]-{2,}")))
    }

    /**
     * Android's `\d` matched `٢٤,١` as a number that `toDouble` then refused: the import crashed on a phone and passed
     * every JVM test. With ICU's classes swapped in, other scripts' digits and spaces read like their ASCII forms.
     */
    @Test
    fun importReadsTheSameUnderIcuCharacterClasses() = underIcuClasses { regexes ->
        assertTrue(regexes.first { it.pattern == "\\s+" }.matches("\u00A0"), "ICU's classes are not in place")
        for ((variant, ascii) in listOf(
            "٢٤,١" to "24,1", "۲۴" to "24", "२४" to "24", "\uD83A\uDD52\uD83A\uDD54" to "24", "1\u00A0234" to "1 234", "1\u202F234" to "1 234",
        )) {
            assertEquals(outcome(answer(ascii)), outcome(answer(variant)), "value $variant")
        }
        assertEquals(outcome(answer("24,1", range = "8,6 - 29,0")), outcome(answer("24,1", range = "٨,٦ - ٢٩,٠")), "range")
        assertEquals(outcome(answer("24,1", date = "2026-09-21")), outcome(answer("24,1", date = "٢٠٢٦-٠٩-٢١")), "date")
        assertEquals(outcome(answer("24,1", name = "Testostérone")), outcome(answer("24,1", name = "TESTOSTÉRONE")), "name")
    }

    @Test
    fun numberFieldsTakeAsciiDigitsOnly() = underIcuClasses {
        assertTrue(DecimalInput.accepts("12,5") && DecimalInput.accepts("") && DecimalInput.accepts("0.125"))
        assertFalse(DecimalInput.accepts("١٢"), "toDouble cannot read it, so the field must not take it")
    }

    private fun answer(value: String, range: String = "", date: String = "2026-09-21", name: String = "Testosteron totaal") =
        "protocoltracker-bloodwork-1\nlab: Saltro\ndate: $date\ntotal_testosterone | $name | $value | nmol/l | $range |\nend\n"

    /** What a read keeps, without the printed text: each draw's date and time, and each row's reading. */
    private fun outcome(text: String): Any {
        val read = assertIs<ImportRead.Found>(BloodworkImport.read(text, LocalDate.of(2026, 10, 3)))
        return read.draft.draws.map { draw -> Triple(draw.date, draw.time, draw.rows.map { it.read }) }
    }

    /**
     * Runs [block] with every domain regex recompiled the way ICU reads it: Unicode `\d \s \w \b`, POSIX classes and
     * case folding. The JVM's own flags for that are close to ICU's defaults (both follow Unicode TR18).
     */
    private fun underIcuClasses(block: (List<Regex>) -> Unit) {
        val field = Regex::class.java.getDeclaredField("nativePattern").apply { isAccessible = true }
        val saved = domainRegexes().map { it.second }.distinct().associateWith { field.get(it) as Pattern }
        fun icu(regex: Regex, p: Pattern) = field.set(regex, Pattern.compile(p.pattern(), p.flags() or Pattern.UNICODE_CHARACTER_CLASS or Pattern.UNICODE_CASE))
        try {
            saved.forEach { (regex, p) -> icu(regex, p) }
            block(saved.keys.toList())
        } finally {
            saved.forEach { (regex, p) -> field.set(regex, p) }
        }
    }

    private fun domainRegexes(): List<Pair<String, Regex>> {
        val root = File(AndroidRegexTest::class.java.protectionDomain.codeSource.location.toURI())
            .let { File(it.parentFile.parentFile.parentFile, "classes/kotlin/main") }
        require(root.isDirectory) { "domain classes not found at $root" }
        val loader = AndroidRegexTest::class.java.classLoader
        return root.walk().filter { it.isFile && it.extension == "class" }.flatMap { file ->
            val name = file.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
            val cls = runCatching { Class.forName(name, true, loader) }.getOrNull() ?: return@flatMap emptySequence()
            val instance = runCatching { cls.getDeclaredField("INSTANCE").get(null) }.getOrNull()
            cls.declaredFields.asSequence().flatMap { field ->
                val value = runCatching {
                    field.isAccessible = true
                    if (Modifier.isStatic(field.modifiers)) field.get(null) else instance?.let { field.get(it) }
                }.getOrNull()
                regexesIn(value).map { "$name.${field.name}" to it }
            }
        }.toList()
    }

    private fun regexesIn(value: Any?): Sequence<Regex> = when (value) {
        is Regex -> sequenceOf(value)
        is Iterable<*> -> value.asSequence().flatMap { regexesIn(it) }
        is Array<*> -> value.asSequence().flatMap { regexesIn(it) }
        is Map<*, *> -> (value.keys.asSequence() + value.values.asSequence()).flatMap { regexesIn(it) }
        is Pair<*, *> -> regexesIn(value.first) + regexesIn(value.second)
        else -> emptySequence()
    }

    /** The first thing ICU would refuse or read differently, or null. */
    private fun icuError(pattern: Pattern): String? {
        val flags = pattern.flags()
        if (flags and Pattern.CANON_EQ != 0) return "CANON_EQ is not supported"
        if (flags and Pattern.UNICODE_CHARACTER_CLASS != 0) return "UNICODE_CHARACTER_CLASS is not supported"
        return syntaxError(pattern.pattern())
    }

    /** Escapes, `\p{…}`/`\P{…}` and `{n}`, `{n,}`, `{n,m}` are fine; a lone brace, `\pL`, `(?U)` and `--` in a class are not. */
    private fun syntaxError(pattern: String): String? {
        val quantifier = Regex("""\{\d+(,\d*)?\}""")
        var i = 0
        var inClass = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' && i + 1 < pattern.length -> {
                    val next = pattern[i + 1]
                    if (next == 'p' || next == 'P') {
                        if (pattern.getOrNull(i + 2) != '{') return "\\$next without braces at $i"
                        i = pattern.indexOf('}', i + 3).takeIf { it > 0 }?.plus(1) ?: return "open \\p{ at $i"
                    } else {
                        i += 2
                    }
                    continue
                }
                c == '(' && pattern.startsWith("(?", i) -> {
                    val flags = pattern.substring(i + 2).takeWhile { it.isLetter() || it == '-' }
                    if ('U' in flags) return "inline flag U at $i"
                }
                c == '[' -> inClass++
                c == ']' && inClass > 0 -> inClass--
                c == '-' && inClass > 0 && pattern.getOrNull(i + 1) == '-' -> return "-- in a class at $i (ICU: set difference)"
                c == '{' && inClass > 0 -> return "unescaped { in a class at $i"
                c == '}' && inClass > 0 -> return "unescaped } in a class at $i"
                c == '{' -> {
                    val match = quantifier.matchAt(pattern, i) ?: return "unescaped { at $i"
                    i = match.range.last + 1
                    continue
                }
                c == '}' -> return "unescaped } at $i"
            }
            i++
        }
        return null
    }

    private companion object {
        /** A regex built from source: `Regex(…)`, `….toRegex()`, `Pattern.compile(…)`. */
        val CONSTRUCTION = Regex("""(?<![\w.])Regex\(|\.toRegex\(|Pattern\.compile\(""")

        /** `private val NAME = Regex(…)` (any visibility; a typed or a `listOf(` property starts on this line). */
        val PROPERTY = Regex("""^\s*(?:(?:private|internal|public)\s+)?val\s+[A-Z][A-Z0-9_]*\s*(?::[^=]+)?=\s*(?:Regex\(|listOf\(\s*Regex\()""")

        /** An element of a property's list: a line that starts with `Regex(`. */
        val LIST_ELEMENT = Regex("""^\s*Regex\(""")
    }
}
