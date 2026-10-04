package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.pk.PkSheet.Model
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PkSheet] against the sheet's CSV export (numeric columns of every row, test resource). */
class PkSheetTest {
    private data class CsvRow(
        val compound: String, val type: String, val halfLifeD: Double?, val cmax: Double?, val tmaxD: Double?,
        val bioavailability: Double?, val model: String, val multiplierUsed: String,
    ) {
        val key get() = "$compound | $type"
    }

    /** Splits one CSV line; quoted fields may contain commas ("1,725"). */
    private fun fields(line: String): List<String> {
        val out = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        for (c in line) when {
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out += field.toString(); field.clear() }
            else -> field.append(c)
        }
        return out + field.toString()
    }

    private fun number(field: String): Double? = if (field == "---" || field == "n/a") null else field.replace(",", "").toDouble()

    private val csv: List<CsvRow> by lazy {
        val stream = assertNotNull(javaClass.getResourceAsStream("/pk-sheet-${PkSheet.EXPORTED}.csv"))
        stream.bufferedReader().readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val f = fields(line)
            CsvRow(f[0], f[1], number(f[2]), number(f[3]), number(f[4]), number(f[5]), f[6], f[7])
        }
    }

    /** Sheet rows without a preset, and why (docs/MODELS.md lists the same). */
    private val notImported = mapOf(
        "Testosterone | Trestolone" to "mislabelled 53-day Nebido form, which the castor-oil row covers",
        "Boldenone | Cypionate" to "empty row (cloned from Test C); the preset uses Test C",
        "DNP | ---" to "not a supported compound",
    )

    @Test
    fun everySheetRowIsImportedOrListedAsNot() {
        val imported = PkSheet.rows.map { "${it.compound} | ${it.type}" }
        assertEquals(imported.size, imported.toSet().size)
        assertEquals(csv.map { it.key }.toSet(), imported.toSet() + notImported.keys)
        assertTrue(imported.none { it in notImported })
    }

    @Test
    fun importedRowsEqualTheSheet() {
        val byKey = csv.associateBy { it.key }
        for (row in PkSheet.rows) {
            val sheet = assertNotNull(byKey["${row.compound} | ${row.type}"], row.compound)
            val name = "${row.compound} ${row.type}"
            assertEquals(sheet.halfLifeD, row.halfLifeD, name)
            assertEquals(sheet.cmax, row.cmax, name)
            assertEquals(sheet.tmaxD, row.tmaxD, name)
            assertEquals(sheet.bioavailability, row.bioavailability, name)
            assertEquals(if (sheet.model == "Basic") Model.BASIC else Model.ADVANCED, row.model, name)
            assertTrue(sheet.model == "Basic" || sheet.model.startsWith("Advanced"), name)
            assertEquals(mapOf("Yes" to true, "No" to false)[sheet.multiplierUsed], row.multiplierUsed, name)
        }
    }

    @Test
    fun advancedRowsHaveCmaxAndTmaxAndBasicRowsNeither() {
        for (row in PkSheet.rows) when (row.model) {
            Model.ADVANCED -> { assertNotNull(row.cmax, row.compound); assertNotNull(row.tmaxD, row.compound) }
            Model.BASIC -> { assertNull(row.cmax, row.compound); assertNull(row.tmaxD, row.compound); assertNotNull(row.bioavailability, row.compound) }
        }
    }

    @Test
    fun rowsFlaggedWithAMultiplierHaveOneAndOnlyTestEsterRowsAreUnflagged() {
        val rows = PkSheet.rows
        assertTrue(rows.filter { it.multiplierUsed == true }.none { it.multiplier == 1.0 })
        // The model scales these although the sheet says "No" (docs/MODELS.md).
        val unflagged = rows.filter { it.multiplier != 1.0 && it.multiplierUsed != true }
        assertEquals(listOf(PkSheet.TEST_E, PkSheet.TEST_C, PkSheet.TEST_ISO), unflagged)
    }
}
