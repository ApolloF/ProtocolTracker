package com.apollof.protocoltracker.domain.io.labimport

import com.apollof.protocoltracker.domain.io.labimport.DateQuestion.Implausible
import com.apollof.protocoltracker.domain.io.labimport.DateQuestion.NoDate
import com.apollof.protocoltracker.domain.io.labimport.DateQuestion.NotDrawDate
import com.apollof.protocoltracker.domain.io.labimport.DateQuestion.WhichDate
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The value grammar of import doc §3.3 (numbers and values), §3.4 (ranges) and §3.5 (dates). */
class LabValuesTest {
    private val values = LabValues
    private val today = LocalDate.of(2026, 9, 26)

    private fun clear(token: String) = assertIs<NumberRead.Clear>(values.number(token))
    private fun ambiguous(token: String) = assertIs<NumberRead.Ambiguous>(values.number(token))

    // §3.3 separators, rules 1-5

    @Test
    fun spacesBetweenDigitGroupsGroup() {
        assertEquals(LabNumber(1209.0, "1209"), clear("1 209").number)
        assertEquals(LabNumber(1209.5, "1209.5"), clear("1 209,5").number)
        assertEquals(LabNumber(1_209_000.0, "1209000"), clear("1 209 000").number)
        assertEquals(1209.0, clear(LabText.normalize("1\u202F209")).number.value, "a narrow no-break space")
        assertNull(clear("1 209").decimalMark)
        assertEquals(NumberRead.Invalid, values.number("8,5 110"))
        assertEquals(NumberRead.Invalid, values.number("1209 000"))
    }

    @Test
    fun withBothMarksTheLastIsTheDecimalMark() {
        assertEquals(LabNumber(1209.5, "1209.5"), clear("1.209,5").number)
        assertEquals(LabNumber(1209.5, "1209.5"), clear("1,209.5").number)
        assertEquals(LabNumber(1_209_000.25, "1209000.25"), clear("1.209.000,25").number)
        assertNull(clear("1.209,5").decimalMark, "only rule 4 counts as a witness")
        assertEquals(NumberRead.Invalid, values.number("1.2,5"))
        assertEquals(NumberRead.Invalid, values.number("1,209,5.5"))
    }

    @Test
    fun theSameMarkTwiceGroups() {
        assertEquals(LabNumber(1_209_000.0, "1209000"), clear("1.209.000").number)
        assertEquals(LabNumber(1_209_000.0, "1209000"), clear("1,209,000").number)
        assertEquals(NumberRead.Invalid, values.number("12.03.2025"))
    }

    @Test
    fun oneMarkNotBeforeThreeDigitsIsADecimalMark() {
        assertEquals(NumberRead.Clear(LabNumber(8.5, "8.5"), ','), values.number("8,5"))
        assertEquals(NumberRead.Clear(LabNumber(0.49, "0.49"), ','), values.number("0,49"))
        assertEquals(NumberRead.Clear(LabNumber(12.34, "12.34"), '.'), values.number("12.34"))
        assertEquals(NumberRead.Clear(LabNumber(0.198, "0.198"), ','), values.number("0,198"), "after an integer part 0")
        assertEquals(NumberRead.Clear(LabNumber(1234.567, "1234.567"), '.'), values.number("1234.567"))
        assertEquals(NumberRead.Clear(LabNumber(0.5, "0.5"), '.'), values.number(".5"))
        assertEquals(LabNumber(11.0, "11.0"), clear("11,0").number, "the printed digits are kept")
        assertEquals(NumberRead.Clear(LabNumber(96.0, "96"), null), values.number("96"))
    }

    @Test
    fun oneMarkBeforeExactlyThreeDigitsIsAmbiguous() {
        assertEquals(
            NumberRead.Ambiguous(',', LabNumber(1.05, "1.05"), LabNumber(1050.0, "1050")),
            values.number("1,050"),
        )
        assertEquals(
            NumberRead.Ambiguous('.', LabNumber(1.209, "1.209"), LabNumber(1209.0, "1209")),
            values.number("1.209"),
        )
        assertEquals("2.11", ambiguous("2.110").asDecimal.text)
    }

    // §3.3 rule 5: the block's style, then elimination

    @Test
    fun twoWitnessesOfOneStyleSettleAmbiguousNumbers() {
        val dutch = DecimalStyle.of(listOf("9,9", "8,5 - 11,0", "↑↑↑ 1.209", "< 190"))
        assertEquals(DecimalStyle(commas = 3, dots = 0), dutch)
        assertEquals(LabNumber(1209.0, "1209"), dutch.reading(ambiguous("1.209")))
        assertEquals(LabNumber(1.05, "1.05"), dutch.reading(ambiguous("1,050")))

        val english = DecimalStyle.of(listOf("9.9", "13.5 - 17.5"))
        assertEquals(LabNumber(1.209, "1.209"), english.reading(ambiguous("1.209")))
        assertEquals(LabNumber(1050.0, "1050"), english.reading(ambiguous("1,050")))
    }

    @Test
    fun oneWitnessIsNotEnough() {
        assertNull(DecimalStyle.of(listOf("9,9", "1.209", "150 - 400")).reading(ambiguous("1.209")))
    }

    @Test
    fun mixedThousandsStylesAreAskedNotGuessed() {
        val mixed = DecimalStyle.of(listOf("9,9", "8,5", "12.34"))
        assertEquals(DecimalStyle(commas = 2, dots = 1), mixed)
        assertNull(mixed.reading(ambiguous("1.209")))
        assertNull(mixed.reading(ambiguous("1,050")))
        assertNull(values.settle(ambiguous("1.209"), mixed), "both readings possible: question Q3")
    }

    @Test
    fun eliminationTakesTheOnlyPossibleReading() {
        val ck = ambiguous("1.209")
        assertEquals(LabNumber(1209.0, "1209"), values.settle(ck, DecimalStyle.NONE) { it >= 10 })
        assertEquals(LabNumber(1.209, "1.209"), values.settle(ck, DecimalStyle.NONE) { it < 10 })
        assertNull(values.settle(ck, DecimalStyle.NONE) { false })
        val dutch = DecimalStyle(commas = 2)
        assertEquals(LabNumber(1209.0, "1209"), values.settle(ck, dutch) { it < 10 }, "the block's style comes first")
    }

    // §3.3 values

    private fun number(value: String, unit: String = "nmol/l") = assertIs<ValueRead.Number>(values.value(value, unit))

    @Test
    fun qualifiersAreStoredAsLessOrMore() {
        for (v in listOf("<0,3", "< 0,3", "<=0,3", "≤ 0,3", LabText.normalize("&lt;0,3"))) {
            val read = number(v)
            assertEquals("<", read.qualifier, v)
            assertEquals(0.3, assertIs<NumberRead.Clear>(read.number).number.value, v)
        }
        for (v in listOf(">90", "> 90", ">=90", "≥90")) assertEquals(">", number(v).qualifier, v)
        assertNull(number("18,4").qualifier)
    }

    @Test
    fun copiedFlagsAreIgnored() {
        val flagged = listOf(
            "18,4 H", "18,4H", "H 18,4", "H18,4", "18,4 (H)", "(L) 18,4", "18,4*", "*18,4", "18,4 !", "↑ 18,4",
            "↑↑↑18,4", "18,4 ↓", "18,4 hoog", "Laag 18,4", "18,4 +", "18,4-", "HH 18,4", "18,4 LL",
        )
        for (v in flagged) {
            val read = number(v)
            assertEquals(NumberRead.Clear(LabNumber(18.4, "18.4"), ','), read.number, v)
            assertNull(read.qualifier, v)
            assertEquals("nmol/l", read.unit, v)
        }
        assertEquals("<", number("< 0,3 L").qualifier)
    }

    @Test
    fun aUnitAfterTheNumberFillsAnEmptyUnitCell() {
        assertEquals("mmol/l", number("5,2 mmol/l", "").unit)
        assertEquals("mmol/l", number("5,2 mmol/L", "mmol/l").unit, "the same unit: the unit cell stays")
        assertEquals("%", number("49%", "").unit)
        assertEquals("mmol/l", number("5,2 mmol/l H", "").unit)
        assertEquals("x 10^9/l", number("7,1 x 10^9/l", "").unit)
        assertEquals("L/L", number("0,49 L/L", "").unit, "a unit's L is not a flag")
        assertEquals("mmol/L", number("6,1 mmol/L", "").unit)
        assertEquals(ValueRead.NotANumber("5,2 mg/dl"), values.value("5,2 mg/dl", "mmol/l"), "a contradicting unit")
    }

    @Test
    fun noValueWordsAreKeptForTheNote() {
        for (v in listOf("", "?", " ? ", "-", "null", "NULL")) assertEquals(ValueRead.NoValue(null), values.value(v, ""), v)
        val words = listOf(
            "volgt", "Onleesbaar", "zie opm.", "zie opmerking", "n.b.", "N/A", "nb", "negatief", "positief",
            "niet bepaald", "niet te bepalen", "hemolytisch", "Gehemolyseerd", "onvoldoende materiaal", "vervallen",
            "geannuleerd", "unreadable",
        )
        for (v in words) assertEquals(ValueRead.NoValue(v), values.value(v, ""), v)
    }

    @Test
    fun negativeNumbersAreNotImported() {
        assertEquals(ValueRead.Negative, values.value("-2,5", "mmol/l"))
        assertEquals(ValueRead.Negative, values.value("< -1", "mmol/l"))
        assertEquals(ValueRead.Negative, values.value(LabText.normalize("−2,5"), "mmol/l"))
    }

    @Test
    fun textThatIsNotANumberIsNotImported() {
        for (v in listOf("ca. 5", "5 zie opm", "5,6 (5,4)", "positief 5", "abc", "12.03.2025", "1,2.5", "H", "5-6", "=5")) {
            assertEquals(ValueRead.NotANumber(v), values.value(v, "mmol/l"), v)
        }
    }

    @Test
    fun anAmbiguousValueStaysAmbiguous() {
        val read = number("↑↑↑ 1.209", "U/l")
        assertEquals(ambiguous("1.209"), read.number)
        assertEquals("U/l", read.unit)
    }

    // §3.4 ranges

    private fun range(text: String, unit: String = "mmol/l", style: DecimalStyle = DecimalStyle.NONE) =
        values.range(text, unit, style)

    private fun found(low: Double?, high: Double?, mens: Boolean = false, unit: String? = null) =
        RangeRead.Found(low, high, mens, unit)

    private fun notUsed(problem: RangeProblem) = RangeRead.NotUsed(problem)

    @Test
    fun rangesWithBothLimits() {
        val both = listOf(
            "8,5 - 11,0", LabText.normalize("8,5–11,0"), "8.5 to 11.0", "8,5 tot 11,0", "8,5-11,0", "8,5 t/m 11,0",
            "8,5 à 11,0",
        )
        for (r in both) assertEquals(found(8.5, 11.0), range(r), r)
        assertEquals(found(0.0, 44.0), range("0 - 44"))
    }

    @Test
    fun rangesWithOnlyAHighLimit() {
        for (r in listOf("< 45", "<45", "≤ 45", "<= 45", "tot 45", "max 45", "maximaal 45", "Max. 45")) {
            assertEquals(found(null, 45.0), range(r), r)
        }
    }

    @Test
    fun rangesWithOnlyALowLimit() {
        for (r in listOf("> 1,0", "≥ 1,0", ">= 1,0", "min 1,0", "minimaal 1,0", "vanaf 1,0", "> 1,0 (target)")) {
            assertEquals(found(1.0, null), range(r), r)
        }
        assertEquals(found(50.0, null), range("> 50 (streefwaarde)"))
        assertEquals(found(4.0, null), range("> 4,0 (Optimaal)"))
    }

    @Test
    fun oneRangeWithASexPrefixLosesThePrefix() {
        assertEquals(found(null, 200.0), range("M <200"))
        assertEquals(found(8.6, 10.5), range("M: 8,6-10,5"))
        assertEquals(found(13.5, 17.5), range("man: 13,5 - 17,5"))
        assertEquals(found(6.7, 29.0), range("mannen 6,7 - 29"))
        assertEquals(found(4.0, 5.6), range("M/V: 4,0 - 5,6"), "one range for both sexes")
    }

    @Test
    fun severalRangesBySexGiveTheMensRange() {
        val mens = found(8.5, 11.0, mens = true)
        assertEquals(mens, range("M: 8,5-11,0 V: 7,5-10,0"))
        assertEquals(mens, range("V: 7,5-10,0 M: 8,5-11,0"))
        assertEquals(mens, range("mannen 8,5-11,0 vrouwen 7,5-10,0"))
        assertEquals(mens, range("male 8.5-11.0 female 7.5-10.0"))
        assertEquals(mens, range("M: 8,5-11,0 mmol/l; V: 7,5-10,0 mmol/l"))
    }

    @Test
    fun severalRangesOtherwiseAreNotUsed() {
        val several = notUsed(RangeProblem.SEVERAL)
        assertEquals(several, range("18-49 jaar: 8,6-29; 50+ jaar: 6,7-26"))
        assertEquals(several, range("8,6 - 29 (18-49 j)"))
        assertEquals(several, range("age 18-49: 8.6-29"))
        assertEquals(several, range("8,5-11,0; 7,5-10,0"))
        assertEquals(several, range("M 18-49: 8,6-29 M 50+: 6,7-26"))
        assertEquals(notUsed(RangeProblem.WOMEN_ONLY), range("V: 7,5-10,0"))
        assertEquals(notUsed(RangeProblem.WOMEN_ONLY), range("vrouwen < 150"))
    }

    @Test
    fun emptyOrWordRangesAreNoRange() {
        for (r in listOf("", " ", "-", "negatief", "Negatief", "zie opm.")) assertEquals(RangeRead.None, range(r), r)
    }

    @Test
    fun rangesThatCannotBeUsed() {
        assertEquals(notUsed(RangeProblem.NOT_A_RANGE), range("45"))
        assertEquals(notUsed(RangeProblem.NOT_A_RANGE), range("normaal"))
        assertEquals(notUsed(RangeProblem.NOT_A_RANGE), range("< 45 zie opm"))
        assertEquals(notUsed(RangeProblem.NOT_A_RANGE), range("1.2,5 - 5"))
        assertEquals(notUsed(RangeProblem.LOW_ABOVE_HIGH), range("11,0 - 8,5"))
        assertEquals(notUsed(RangeProblem.NEGATIVE), range("-2,5 - 2,5"))
        assertEquals(notUsed(RangeProblem.NEGATIVE), range("> -5"))
    }

    @Test
    fun aTrailingUnitIsStrippedOrReported() {
        assertEquals(found(8.6, 29.0), range("8,6 - 29,0 nmol/l", "nmol/L"))
        assertEquals(found(60.0, null), range("> 60 ml/min/1,73m2", "ml/min/1,73m2"))
        assertEquals(found(40.0, 50.0), range("40 - 50%", "%"))
        assertEquals(found(null, 40.0, unit = "pg/ml"), range("< 40 pg/ml", "pmol/l"))
        assertEquals(found(0.41, 0.51, unit = "l/l"), range("0,41 - 0,51 l/l", ""))
    }

    @Test
    fun limitsFollowTheBlocksSeparators() {
        val dutch = DecimalStyle(commas = 2)
        assertEquals(found(null, 1000.0), range("< 1.000", "U/l", dutch))
        assertEquals(notUsed(RangeProblem.UNCLEAR), range("< 1.000", "U/l"))
        assertEquals(found(1.005, 1.03), range("1,005 - 1,030", "", dutch))
        assertEquals(found(150.0, 400.0), range("150 - 400", "10^9/l"))
    }

    // §3.5 dates

    private fun date(line: String) = DrawDates.readLine(line, today)!!

    private fun assertDate(expected: LocalDate, line: String, time: LocalTime? = null) {
        val read = date(line)
        assertNull(read.question, line)
        assertEquals(expected, read.date, line)
        assertEquals(time, read.time, line)
    }

    private fun assertAsked(question: DateQuestion, line: String) {
        val read = date(line)
        assertEquals(question, read.question, line)
        assertNull(read.date, "$line: a date question leaves the date empty")
    }

    private val march12 = LocalDate.of(2025, 3, 12)

    @Test
    fun isoDatesWithOrWithoutATime() {
        val quarterPast8 = LocalTime.of(8, 15)
        assertDate(march12, "date: 2025-03-12")
        assertDate(march12, "date: 2025-03-12 08:15", quarterPast8)
        assertDate(march12, "date: 2025-03-12 8:15", quarterPast8)
        assertDate(march12, "date: 2025-03-12 08.15", quarterPast8)
        assertDate(march12, "date: 2025-03-12 08:15:30", quarterPast8)
        assertDate(march12, "date: 2025-03-12T08:15", quarterPast8)
        assertDate(march12, "date: 2025-03-12 08:15 uur", quarterPast8)
        assertDate(march12, "date: 2025-03-12 7:50 AM", LocalTime.of(7, 50))
        assertDate(march12, "date: 2025-03-12 7:50 pm", LocalTime.of(19, 50))
        assertDate(march12, "date: 2025-03-12 12:30 PM", LocalTime.of(12, 30))
        assertDate(march12, "DATE : 2025-03-12 00:00", null)
        assertDate(march12, "date: 2025-03-12 12:00 AM", null)
    }

    @Test
    fun dayFirstAndMonthNameDates() {
        for (d in listOf(
            "12-03-2025", "12.03.2025", "12-03-25", "wo 12-03-2025", "12 mrt 2025", "12 maart 2025", "12 Mar 2025",
            "Mar 12, 2025", "12-mrt-2025", "12 mrt. 2025", "Wednesday, 12 March 2025",
        )) {
            assertDate(march12, "date: $d")
        }
        assertDate(march12, "date: woensdag 12-03-2025 08:15", LocalTime.of(8, 15))
        assertDate(LocalDate.of(2025, 3, 25), "date: 25/03/2025")
        assertDate(LocalDate.of(2025, 3, 25), "date: 03/25/2025 7:50 AM", LocalTime.of(7, 50))
    }

    @Test
    fun aSlashDateThatReadsBothWaysIsAsked() {
        assertAsked(WhichDate("12/03/2025", listOf(march12, LocalDate.of(2025, 12, 3))), "date: 12/03/2025")
        assertEquals(march12, date("date: 12/03/2025").parsedDate)
        assertDate(march12, "date: 03/12/2025 07:50 AM", LocalTime.of(7, 50))
        assertDate(LocalDate.of(2025, 5, 5), "date: 05/05/2025")
    }

    @Test
    fun twoDigitYearsAtTheCenturyEdge() {
        assertDate(today, "date: 26-09-26")
        assertDate(LocalDate.of(2000, 1, 1), "date: 01-01-00")
        assertDate(LocalDate.of(1999, 1, 1), "date: 01-01-99")
        assertAsked(Implausible(LocalDate.of(1927, 1, 1), future = false), "date: 01-01-27")
    }

    @Test
    fun theDateMustMatchThePrintedDateReadDayFirst() {
        assertAsked(
            WhichDate("04-03-2025", listOf(LocalDate.of(2025, 4, 3), LocalDate.of(2025, 3, 4))),
            "date: 2025-04-03 | 04-03-2025",
        )
        assertEquals(LocalDate.of(2025, 4, 3), date("date: 2025-04-03 | 04-03-2025").parsedDate)
        assertAsked(
            WhichDate("12/03/2025", listOf(LocalDate.of(2025, 12, 3), march12)),
            "date: 2025-12-03 | Afname: 12/03/2025",
        )
        assertDate(march12, "date: 2025-03-12 08:15 | Afnamedatum: 12-03-2025 08:15", LocalTime.of(8, 15))
        assertDate(march12, "date: 2025-03-12 | Datum van afname: 12.03.25 00:00")
        assertDate(march12, "date: 2025-03-12 | Afname 12 maart 2025")
        assertDate(march12, "date: 2025-03-12 07:50 | Date Collected: 03/12/2025 07:50 AM", LocalTime.of(7, 50))
        assertDate(LocalDate.of(2025, 3, 25), "date: 2025-03-25 | 25/03/2025")
        assertDate(march12, "date: 2025-03-12 | Afnamedatum: 2025-03-12")
        assertDate(march12, "date: 2025-03-12 | Afnamedatum")
        assertEquals("", date("date: 2025-03-12").printed)
        assertEquals("Afnamedatum: 12-03-2025 08:15", date("date: 2025-03-12 | Afnamedatum: 12-03-2025 08:15 ").printed)
    }

    @Test
    fun receivedDatesAreAcceptedWithACaption() {
        val received = date("date: 2025-03-13 | Ontvangstdatum: 13-03-2025")
        assertEquals(LocalDate.of(2025, 3, 13), received.date)
        assertTrue(received.received)
        assertTrue(date("date: 2025-03-13 | Received 03/13/2025").received)
        assertFalse(date("date: 2025-03-12 | Afnamedatum: 12-03-2025").received)
        assertFalse(date("date: 2025-03-12 | Datum: 12-03-2025").received)
    }

    @Test
    fun otherDatesThanTheDrawAreAsked() {
        assertAsked(NotDrawDate("Geboortedatum"), "date: 2003-02-27 | Geboortedatum: 27-02-2003")
        assertEquals(LocalDate.of(2003, 2, 27), date("date: 2003-02-27 | Geboortedatum: 27-02-2003").parsedDate)
        assertAsked(NotDrawDate("Uitslagdatum"), "date: 2025-03-14 | Uitslagdatum 14-03-2025")
        assertAsked(NotDrawDate("DOB"), "date: 2025-03-14 | DOB: 14.03.2025")
        assertAsked(NotDrawDate("Date reported"), "date: 2025-03-14 | Date reported: 03/14/2025")
        assertAsked(NotDrawDate("printed"), "date: 2025-03-14 | 14-03-2025 (printed)")
    }

    @Test
    fun aMissingOrUnreadableDateIsAsked() {
        for (line in listOf(
            "date: ?", "date:", "date: ? | Afnamedatum: 12-03-2025", "date: morgen", "date: 2025-02-30",
            "date: 2025-03-12 of 13", "date: 2025-03-12 25:00",
        )) {
            assertAsked(NoDate, line)
            assertNull(date(line).parsedDate, line)
        }
    }

    @Test
    fun datesInTheFutureOrBefore1990AreAsked() {
        assertDate(today.plusDays(1), "date: 2026-09-27")
        assertAsked(Implausible(today.plusDays(2), future = true), "date: 2026-09-28")
        assertAsked(Implausible(LocalDate.of(1989, 12, 31), future = false), "date: 1989-12-31")
        assertDate(LocalDate.of(1990, 1, 1), "date: 1990-01-01")
    }

    @Test
    fun onlyDateLinesAreRead() {
        assertNull(DrawDates.readLine("lab: Saltro", today))
        assertNull(DrawDates.readLine("ck | CK | 12", today))
    }
}
