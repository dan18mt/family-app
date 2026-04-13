package com.familyhome.app.domain.model

/**
 * A specific date range in the Hijri (Islamic) calendar.
 *
 * [hijriMonth] is 1-indexed (1 = Muharram … 12 = Dhu al-Hijjah).
 * [startDay] and [endDay] are inclusive, 1-indexed day-of-month values.
 */
data class HijriDateRange(
    val hijriMonth: Int,
    val startDay: Int,
    val endDay: Int,
)

/**
 * Broad category for grouping Islamic calendar events in the UI.
 */
enum class IslamicEventCategory {
    FASTING,   // Puasa sunnah
    PRAYER,    // Shalat sunnah malam
    IBADAH,    // Ibadah umum (i'tikaf, amal shalih, dll.)
}

/**
 * Calendar-based sunnah ibadah that are tied to specific periods in the Hijri (Islamic) calendar.
 *
 * Every entry here is supported by at least one hadith from Sahih Bukhari or Sahih Muslim,
 * or a verse of the Qur'an.
 *
 * Unlike [SunnahGoal] (which tracks daily habits), these events:
 *  - Have a fixed active window in the Hijri calendar.
 *  - Track cumulative day-count towards [totalDaysGoal], not a daily check.
 *
 * @param activeRanges   One or more Hijri date ranges during which this ibadah is valid.
 *                       Ayyamul Bidh uses 12 ranges (one per month).
 * @param totalDaysGoal  Total number of days to complete within the active window
 *                       (e.g. 6 for Syawal fasting, 1 for Arafah fasting).
 */
enum class IslamicCalendarSunnah(
    val title: String,
    val titleEn: String,
    val description: String,
    val descriptionEn: String,
    val hadith: String,
    val hadithEn: String,
    val source: String,
    val sourceEn: String,
    val activeRanges: List<HijriDateRange>,
    val totalDaysGoal: Int,
    val category: IslamicEventCategory,
    val rewardIcon: String,
    val reward: String,
    val rewardEn: String,
) {

    // ── Muharram ────────────────────────────────────────────────────────────────

    PUASA_TASUA(
        title          = "Puasa Tasu'a (9 Muharram)",
        titleEn        = "Tasu'a Fast (9 Muharram)",
        description    = "Puasa sunnah pada tanggal 9 Muharram",
        descriptionEn  = "Recommended fast on the 9th of Muharram",
        hadith         = "Sungguh, seandainya aku masih hidup tahun depan, aku pasti akan berpuasa pada hari kesembilan (Muharram).",
        hadithEn       = "If I am alive next year, I will certainly fast on the ninth (of Muharram) as well.",
        source         = "HR. Muslim no. 1134",
        sourceEn       = "Sahih Muslim, no. 1134",
        activeRanges   = listOf(HijriDateRange(hijriMonth = 1, startDay = 9, endDay = 9)),
        totalDaysGoal  = 1,
        category       = IslamicEventCategory.FASTING,
        rewardIcon     = "🌙",
        reward         = "Mengikuti sunnah Nabi & membedakan dari kaum Yahudi",
        rewardEn       = "Following the Prophet's Sunnah & differentiating from the Jews",
    ),

    PUASA_ASYURA(
        title          = "Puasa Asyura (10 Muharram)",
        titleEn        = "Ashura Fast (10 Muharram)",
        description    = "Puasa sunnah pada tanggal 10 Muharram — menghapus dosa setahun lalu",
        descriptionEn  = "Recommended fast on the 10th of Muharram — expiates sins of the past year",
        hadith         = "Puasa hari Asyura, aku berharap kepada Allah agar menghapus dosa setahun yang lalu.",
        hadithEn       = "Fasting on the day of 'Ashura', I hope that Allah will expiate the sins of the past year.",
        source         = "HR. Muslim no. 1162",
        sourceEn       = "Sahih Muslim, no. 1162",
        activeRanges   = listOf(HijriDateRange(hijriMonth = 1, startDay = 10, endDay = 10)),
        totalDaysGoal  = 1,
        category       = IslamicEventCategory.FASTING,
        rewardIcon     = "✨",
        reward         = "Menghapus dosa setahun yang lalu",
        rewardEn       = "Expiates sins of the past year",
    ),

    // ── Sya'ban ─────────────────────────────────────────────────────────────────

    PUASA_SHABAN(
        title          = "Puasa Sya'ban",
        titleEn        = "Sha'ban Fasts",
        description    = "Perbanyak puasa di bulan Sya'ban mengikuti kebiasaan Nabi ﷺ",
        descriptionEn  = "Increase fasting in Sha'ban following the Prophet's ﷺ practice",
        hadith         = "Tidak ada bulan yang paling banyak Rasulullah ﷺ berpuasa di dalamnya selain bulan Sya'ban.",
        hadithEn       = "There was no month in which the Messenger of Allah ﷺ fasted more than Sha'ban.",
        source         = "HR. Bukhari no. 1969, Muslim no. 1156",
        sourceEn       = "Sahih Bukhari no. 1969, Sahih Muslim no. 1156",
        activeRanges   = listOf(HijriDateRange(hijriMonth = 8, startDay = 1, endDay = 29)),
        totalDaysGoal  = 15,
        category       = IslamicEventCategory.FASTING,
        rewardIcon     = "⭐",
        reward         = "Mengikuti kebiasaan Nabi ﷺ — bulan persiapan menyambut Ramadan",
        rewardEn       = "Following the Prophet ﷺ — a month to prepare for Ramadan",
    ),

    // ── Ramadan ─────────────────────────────────────────────────────────────────

    QIYAMUL_LAIL_RAMADAN(
        title          = "Qiyamul Lail Ramadan (Tarawih)",
        titleEn        = "Ramadan Night Prayer (Tarawih)",
        description    = "Shalat malam di bulan Ramadan — menghapus dosa yang telah lalu",
        descriptionEn  = "Night prayers in Ramadan — past sins forgiven",
        hadith         = "Barangsiapa yang mendirikan shalat malam Ramadan karena iman dan mengharap pahala, maka diampuni dosa-dosanya yang telah lalu.",
        hadithEn       = "Whoever prays during the nights of Ramadan out of faith and seeking reward, his previous sins will be forgiven.",
        source         = "HR. Bukhari no. 2013, Muslim no. 759",
        sourceEn       = "Sahih Bukhari no. 2013, Sahih Muslim no. 759",
        activeRanges   = listOf(HijriDateRange(hijriMonth = 9, startDay = 1, endDay = 30)),
        totalDaysGoal  = 30,
        category       = IslamicEventCategory.PRAYER,
        rewardIcon     = "🌙",
        reward         = "Diampuni dosa-dosa yang telah lalu",
        rewardEn       = "Previous sins forgiven",
    ),

    ITIKAF_RAMADAN(
        title          = "I'tikaf 10 Hari Terakhir Ramadan",
        titleEn        = "I'tikaf — Last 10 Days of Ramadan",
        description    = "Berdiam di masjid pada 10 malam terakhir Ramadan untuk beribadah",
        descriptionEn  = "Secluding oneself in the mosque during the last 10 nights of Ramadan",
        hadith         = "Nabi ﷺ selalu beri'tikaf pada sepuluh hari terakhir bulan Ramadan sampai beliau wafat.",
        hadithEn       = "The Prophet ﷺ used to observe I'tikaf in the last ten days of Ramadan until he passed away.",
        source         = "HR. Bukhari no. 2026, Muslim no. 1172",
        sourceEn       = "Sahih Bukhari no. 2026, Sahih Muslim no. 1172",
        activeRanges   = listOf(HijriDateRange(hijriMonth = 9, startDay = 21, endDay = 30)),
        totalDaysGoal  = 10,
        category       = IslamicEventCategory.IBADAH,
        rewardIcon     = "🕌",
        reward         = "Sunnah yang selalu dijaga Nabi hingga akhir hayat beliau",
        rewardEn       = "A Sunnah the Prophet observed until the end of his life",
    ),

    // ── Syawal ───────────────────────────────────────────────────────────────────

    PUASA_SYAWAL(
        title          = "Puasa 6 Hari Syawal",
        titleEn        = "Six Days of Shawwal Fasting",
        description    = "Puasa 6 hari di bulan Syawal setelah Ramadan — seperti puasa setahun penuh",
        descriptionEn  = "Fasting 6 days in Shawwal after Ramadan — like fasting the entire year",
        hadith         = "Barangsiapa yang berpuasa Ramadan kemudian meneruskannya dengan 6 hari dari bulan Syawal, maka ia seperti orang yang berpuasa sepanjang tahun.",
        hadithEn       = "Whoever fasts Ramadan and follows it with six days of Shawwal, it will be as if he fasted the entire year.",
        source         = "HR. Muslim no. 1164",
        sourceEn       = "Sahih Muslim, no. 1164",
        // Syawal day 1 is Eid al-Fitr — fasting is forbidden. Start from day 2.
        activeRanges   = listOf(HijriDateRange(hijriMonth = 10, startDay = 2, endDay = 30)),
        totalDaysGoal  = 6,
        category       = IslamicEventCategory.FASTING,
        rewardIcon     = "🏆",
        reward         = "Seperti berpuasa sepanjang tahun",
        rewardEn       = "Equivalent to fasting the entire year",
    ),

    // ── Ayyamul Bidh ─────────────────────────────────────────────────────────────

    PUASA_AYYAMUL_BIDH(
        title          = "Puasa Ayyamul Bidh (13–15 setiap bulan)",
        titleEn        = "Ayyamul Bidh Fast (13–15 of every month)",
        description    = "Puasa pada hari-hari putih (malam terang bulan) setiap bulan",
        descriptionEn  = "Fasting the white days (bright nights) of every month",
        hadith         = "Rasulullah ﷺ memerintahkan aku untuk berpuasa tiga hari setiap bulan: tanggal 13, 14, dan 15.",
        hadithEn       = "The Messenger of Allah ﷺ used to command me to fast three days every month: the 13th, 14th, and 15th.",
        source         = "HR. Tirmidzi no. 761 (hasan sahih), Abu Dawud no. 2449",
        sourceEn       = "Tirmidhi no. 761 (hasan sahih), Abu Dawud no. 2449",
        // Every month (1-12), days 13-15
        activeRanges   = (1..12).map { month -> HijriDateRange(hijriMonth = month, startDay = 13, endDay = 15) },
        totalDaysGoal  = 3,
        category       = IslamicEventCategory.FASTING,
        rewardIcon     = "🌕",
        reward         = "Seperti berpuasa sepanjang masa",
        rewardEn       = "Like fasting perpetually",
    ),

    // ── Dzulhijjah ───────────────────────────────────────────────────────────────

    PUASA_DZULHIJJAH_AWWAL(
        title          = "Puasa 8 Hari Pertama Dzulhijjah",
        titleEn        = "First 8 Days of Dhul-Hijjah Fasting",
        description    = "Perbanyak amal shalih di 10 hari pertama Dzulhijjah — hari-hari terbaik di sisi Allah",
        descriptionEn  = "Increase good deeds in the first 10 days of Dhul-Hijjah — the best days to Allah",
        hadith         = "Tidak ada hari-hari yang amal shalih di dalamnya lebih dicintai Allah melebihi sepuluh hari (pertama) bulan Dzulhijjah.",
        hadithEn       = "There are no days on which good deeds are more beloved to Allah than these ten days (of Dhul-Hijjah).",
        source         = "HR. Bukhari no. 969",
        sourceEn       = "Sahih Bukhari, no. 969",
        // Days 1-8; day 9 (Arafah) has its own stronger-emphasis entry
        activeRanges   = listOf(HijriDateRange(hijriMonth = 12, startDay = 1, endDay = 8)),
        totalDaysGoal  = 8,
        category       = IslamicEventCategory.FASTING,
        rewardIcon     = "🌟",
        reward         = "Amal shalih paling dicintai Allah",
        rewardEn       = "Good deeds most beloved to Allah",
    ),

    PUASA_ARAFAH(
        title          = "Puasa Arafah (9 Dzulhijjah)",
        titleEn        = "Arafah Fast (9 Dhul-Hijjah)",
        description    = "Puasa sunnah paling utama — menghapus dosa dua tahun (tahun lalu & mendatang)",
        descriptionEn  = "The most virtuous voluntary fast — expiates sins of two years",
        hadith         = "Puasa hari Arafah, aku berharap kepada Allah agar menghapus dosa setahun yang lalu dan setahun yang akan datang.",
        hadithEn       = "Fasting the day of Arafah, I hope that Allah will expiate the sins of the year before it and the year after it.",
        source         = "HR. Muslim no. 1162",
        sourceEn       = "Sahih Muslim, no. 1162",
        activeRanges   = listOf(HijriDateRange(hijriMonth = 12, startDay = 9, endDay = 9)),
        totalDaysGoal  = 1,
        category       = IslamicEventCategory.FASTING,
        rewardIcon     = "💎",
        reward         = "Menghapus dosa dua tahun (lalu & mendatang)",
        rewardEn       = "Expiates sins of two years (past & upcoming)",
    );

    // ── Helpers ─────────────────────────────────────────────────────────────────

    fun localizedTitle(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") titleEn else title

    fun localizedDescription(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") descriptionEn else description

    fun localizedHadith(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") hadithEn else hadith

    fun localizedSource(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") sourceEn else source

    fun localizedReward(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") rewardEn else reward

    /**
     * Returns the single active range for the given Hijri month, or null if this sunnah
     * is not active in that month.
     */
    fun rangeForMonth(hijriMonth: Int): HijriDateRange? =
        activeRanges.firstOrNull { it.hijriMonth == hijriMonth }

    /** True if every range in [activeRanges] covers the same month (i.e. monthly-recurring). */
    val isMonthlyRecurring: Boolean
        get() = activeRanges.size == 12 && activeRanges.map { it.hijriMonth }.toSet().size == 12
}
