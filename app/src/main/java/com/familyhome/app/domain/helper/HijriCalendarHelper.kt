package com.familyhome.app.domain.helper

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.IslamicCalendar
import com.familyhome.app.domain.model.HijriDateRange

/** Simple container for a Hijri calendar date. */
data class HijriDate(
    val year: Int,
    /** 1-indexed: 1 = Muharram … 12 = Dhu al-Hijjah. */
    val month: Int,
    val day: Int,
)

/**
 * Utility helpers for working with the Hijri (Islamic) calendar.
 *
 * Uses [android.icu.util.IslamicCalendar] (available on API 24+, min SDK here is 26).
 * The civil/algorithmic variant is used for consistency across devices regardless of
 * moon-sighting methodologies.
 */
object HijriCalendarHelper {

    /** Arabic month names used for display. */
    val HIJRI_MONTH_NAMES_ID = listOf(
        "Muharram", "Safar", "Rabi'ul Awwal", "Rabi'ul Akhir",
        "Jumadal Awwal", "Jumadal Akhir", "Rajab", "Sya'ban",
        "Ramadan", "Syawal", "Dzul Qa'dah", "Dzulhijjah",
    )

    val HIJRI_MONTH_NAMES_EN = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Akhir",
        "Jumada al-Awwal", "Jumada al-Akhir", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
    )

    /** Returns the current Hijri date based on the device clock. */
    fun currentHijriDate(): HijriDate = epochMsToHijri(System.currentTimeMillis())

    /** Converts a Gregorian epoch-millisecond timestamp to a Hijri date. */
    fun epochMsToHijri(epochMs: Long): HijriDate {
        val cal = IslamicCalendar().also { it.timeInMillis = epochMs }
        return HijriDate(
            year  = cal.get(IcuCalendar.YEAR),
            month = cal.get(IcuCalendar.MONTH) + 1, // ICU is 0-indexed
            day   = cal.get(IcuCalendar.DAY_OF_MONTH),
        )
    }

    /** Converts a Gregorian epoch-day (days since 1970-01-01) to a Hijri date. */
    fun epochDayToHijri(epochDay: Long): HijriDate =
        epochMsToHijri(epochDay * 86_400_000L)

    /**
     * Returns the [LongRange] of Gregorian epoch-days that correspond to the given
     * [HijriDateRange] in Hijri [year].
     *
     * Returns an empty range if the conversion fails.
     */
    fun epochDayRangeFor(range: HijriDateRange, year: Int): LongRange {
        return try {
            val startCal = IslamicCalendar(year, range.hijriMonth - 1, range.startDay)
            val endCal   = IslamicCalendar(year, range.hijriMonth - 1, range.endDay)
            (startCal.timeInMillis / 86_400_000L)..(endCal.timeInMillis / 86_400_000L)
        } catch (e: Exception) {
            1L..0L // Empty range: start > end
        }
    }

    /** Display string like "Syawal 1447 H" (Indonesian) or "Shawwal 1447 AH" (English). */
    fun currentMonthDisplayName(languageTag: String = java.util.Locale.getDefault().language): String {
        val h = currentHijriDate()
        val monthName = if (languageTag == "en")
            HIJRI_MONTH_NAMES_EN.getOrElse(h.month - 1) { "Month ${h.month}" }
        else
            HIJRI_MONTH_NAMES_ID.getOrElse(h.month - 1) { "Bulan ${h.month}" }
        val suffix = if (languageTag == "en") "AH" else "H"
        return "$monthName ${h.year} $suffix"
    }

    /** Returns the month display name for a given Hijri month number (1-12). */
    fun monthDisplayName(hijriMonth: Int, languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en")
            HIJRI_MONTH_NAMES_EN.getOrElse(hijriMonth - 1) { "Month $hijriMonth" }
        else
            HIJRI_MONTH_NAMES_ID.getOrElse(hijriMonth - 1) { "Bulan $hijriMonth" }
}
