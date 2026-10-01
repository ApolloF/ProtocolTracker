package com.apollof.protocoltracker.domain

import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Android compiles regexes with ICU, which rejects a brace that is not part of a quantifier or `\p{…}` (e.g. `\{[^}]*}`),
 * while the JVM accepts it. Such a pattern passed every JVM test and crashed the app on first use, so every [Regex] held
 * in a field of a domain class is checked here for ICU's brace rule.
 */
class AndroidRegexTest {
    @Test
    fun everyDomainRegexUsesBracesTheWayIcuAccepts() {
        val patterns = domainRegexes()
        assertTrue(patterns.size > 40, "found only ${patterns.size} regexes; the class scan is broken")
        val bad = patterns.filter { (_, pattern) -> icuBraceError(pattern) != null }
            .map { (owner, pattern) -> "$owner: $pattern (${icuBraceError(pattern)})" }
        assertEquals(emptyList(), bad)
    }

    @Test
    fun checkerFlagsTheCrashingPatternAndAcceptsQuantifiers() {
        assertTrue(icuBraceError("\\{[^}]*}") != null)
        assertTrue(icuBraceError("[a{}]") != null)
        assertEquals(null, icuBraceError("\\{[^\\}]*\\}"))
        assertEquals(null, icuBraceError("\\d{1,2}x{3}y{2,}\\p{L}\\P{Mn}[\\{\\}]"))
    }

    private fun domainRegexes(): List<Pair<String, String>> {
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
                regexesIn(value).map { "$name.${field.name}" to it.pattern }
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

    /** The first brace ICU would refuse, or null. Escapes, `\p{…}`/`\P{…}` and `{n}`, `{n,}`, `{n,m}` are fine. */
    private fun icuBraceError(pattern: String): String? {
        val quantifier = Regex("""\{\d+(,\d*)?\}""")
        var i = 0
        var inClass = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' && i + 1 < pattern.length -> {
                    val next = pattern[i + 1]
                    i += if ((next == 'p' || next == 'P') && pattern.getOrNull(i + 2) == '{') {
                        pattern.indexOf('}', i + 3).takeIf { it > 0 }?.minus(i)?.plus(1) ?: return "open \\p{ at $i"
                    } else 2
                    continue
                }
                c == '[' -> inClass++
                c == ']' && inClass > 0 -> inClass--
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
}
