package com.apollof.protocoltracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apollof.protocoltracker.domain.io.labimport.BloodworkImport
import com.apollof.protocoltracker.domain.io.labimport.ImportRead
import com.apollof.protocoltracker.domain.units.DecimalInput
import dalvik.system.DexFile
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.util.regex.Pattern
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Every regex of the app under Android's own engine (ICU). The JVM accepted a pattern ICU refuses (v0.5.0-dev.1–4
 * crashed on opening Bloodwork) and reads `\d` and `\s` as ASCII only, so `AndroidRegexTest` checks the rules on the
 * JVM and this test runs the real thing: it loads every domain class, which compiles each regex property, and
 * recompiles each pattern. All regexes are domain properties (`AndroidRegexTest` enforces it).
 */
@RunWith(AndroidJUnit4::class)
class IcuRegexTest {
    @Test
    fun everyDomainRegexCompiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val names = DexFile(context.packageCodePath).entries().toList().filter { it.startsWith("com.apollof.protocoltracker.domain.") }
        assertTrue(names.size > 100, "found only ${names.size} domain classes in ${context.packageCodePath}")
        val failures = mutableListOf<String>()
        val regexes = names.flatMap { name ->
            val cls = try {
                Class.forName(name, true, context.classLoader)
            } catch (e: Throwable) {
                failures += "$name: ${e.cause ?: e}"
                return@flatMap emptyList()
            }
            val instance = runCatching { cls.getDeclaredField("INSTANCE").get(null) }.getOrNull()
            cls.declaredFields.flatMap { field ->
                val value = runCatching {
                    field.isAccessible = true
                    if (Modifier.isStatic(field.modifiers)) field.get(null) else instance?.let { field.get(it) }
                }.getOrNull()
                regexesIn(value).map { "$name.${field.name}" to it }.toList()
            }
        }
        for ((owner, regex) in regexes) {
            runCatching { Pattern.compile(regex.pattern, regex.toPattern().flags()) }.onFailure { failures += "$owner: ${regex.pattern}: $it" }
        }
        assertEquals(emptyList(), failures)
        assertTrue(regexes.size > 40, "found only ${regexes.size} regexes; the class scan is broken")
    }

    /** ICU's `\d` matches `٢`; the import reads such digits as their ASCII forms instead of crashing in `toDouble`. */
    @Test
    fun otherScriptsDigitsReadLikeAscii() {
        assertTrue(Regex("\\d").matches("٢"), "this engine reads \\d as ASCII; is it ICU?")
        fun outcome(value: String): Any {
            val text = "protocoltracker-bloodwork-1\nlab: Saltro\ndate: 2026-09-21\ntotal_testosterone | Testosteron totaal | $value | nmol/l | |\nend\n"
            val read = assertIs<ImportRead.Found>(BloodworkImport.read(text, LocalDate.of(2026, 10, 3)))
            return read.draft.draws.map { draw -> draw.date to draw.rows.map { it.read } }
        }
        for (variant in listOf("٢٤,١", "۲۴,۱", "२४,१")) assertEquals(outcome("24,1"), outcome(variant), variant)
        assertFalse(DecimalInput.accepts("١٢"))
    }

    private fun regexesIn(value: Any?): Sequence<Regex> = when (value) {
        is Regex -> sequenceOf(value)
        is Iterable<*> -> value.asSequence().flatMap { regexesIn(it) }
        is Array<*> -> value.asSequence().flatMap { regexesIn(it) }
        is Map<*, *> -> (value.keys.asSequence() + value.values.asSequence()).flatMap { regexesIn(it) }
        is Pair<*, *> -> regexesIn(value.first) + regexesIn(value.second)
        else -> emptySequence()
    }
}
