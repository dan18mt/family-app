package com.familyhome.app.domain.helper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for HijriCalendarHelper.
 *
 * Note: Tests that call conversion methods (epochMsToHijri, epochDayRangeFor, currentHijriDate)
 * depend on android.icu.util.IslamicCalendar and must run as instrumented tests.
 * These tests cover the pure-Kotlin parts: month name arrays and display name logic.
 */
class HijriCalendarHelperTest {

    @Test
    fun `HIJRI_MONTH_NAMES_ID has 12 entries`() {
        assertEquals(12, HijriCalendarHelper.HIJRI_MONTH_NAMES_ID.size)
    }

    @Test
    fun `HIJRI_MONTH_NAMES_EN has 12 entries`() {
        assertEquals(12, HijriCalendarHelper.HIJRI_MONTH_NAMES_EN.size)
    }

    @Test
    fun `Indonesian month names contain expected values`() {
        val names = HijriCalendarHelper.HIJRI_MONTH_NAMES_ID
        assertEquals("Muharram", names[0])
        assertEquals("Ramadan", names[8])
        assertEquals("Dzulhijjah", names[11])
    }

    @Test
    fun `English month names contain expected values`() {
        val names = HijriCalendarHelper.HIJRI_MONTH_NAMES_EN
        assertEquals("Muharram", names[0])
        assertEquals("Ramadan", names[8])
        assertEquals("Dhu al-Hijjah", names[11])
    }

    @Test
    fun `monthDisplayName returns Indonesian name by default for valid month`() {
        assertEquals("Muharram", HijriCalendarHelper.monthDisplayName(1, "id"))
        assertEquals("Ramadan", HijriCalendarHelper.monthDisplayName(9, "id"))
    }

    @Test
    fun `monthDisplayName returns English name when requested`() {
        assertEquals("Dhu al-Hijjah", HijriCalendarHelper.monthDisplayName(12, "en"))
        assertEquals("Shawwal", HijriCalendarHelper.monthDisplayName(10, "en"))
    }

    @Test
    fun `monthDisplayName handles out-of-range month gracefully`() {
        val result = HijriCalendarHelper.monthDisplayName(0, "id")
        assertEquals("Bulan 0", result)

        val resultEn = HijriCalendarHelper.monthDisplayName(13, "en")
        assertEquals("Month 13", resultEn)
    }

    @Test
    fun `month names are in correct order`() {
        val id = HijriCalendarHelper.HIJRI_MONTH_NAMES_ID
        assertEquals("Muharram", id[0])
        assertEquals("Safar", id[1])
        assertEquals("Rajab", id[6])
        assertEquals("Sya'ban", id[7])
        assertEquals("Ramadan", id[8])
        assertEquals("Syawal", id[9])

        val en = HijriCalendarHelper.HIJRI_MONTH_NAMES_EN
        assertEquals("Rajab", en[6])
        assertEquals("Sha'ban", en[7])
    }

    @Test
    fun `HijriDate data class holds values correctly`() {
        val date = HijriDate(year = 1447, month = 9, day = 15)
        assertEquals(1447, date.year)
        assertEquals(9, date.month)
        assertEquals(15, date.day)
    }

    @Test
    fun `HijriDate equality works`() {
        val a = HijriDate(1447, 1, 1)
        val b = HijriDate(1447, 1, 1)
        val c = HijriDate(1447, 1, 2)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `HijriDate copy works`() {
        val date = HijriDate(1447, 9, 1)
        val next = date.copy(day = 2)
        assertEquals(2, next.day)
        assertEquals(9, next.month)
    }
}
