package com.familyhome.app.domain.model

/**
 * Predefined Sunnah ibadah goals with sahih hadith references, rewards, and reminder windows.
 *
 * [reminderHour]/[reminderMinute] = ideal local time to send the reminder notification.
 * [reminderEndHour]/[reminderEndMinute] = end of the ibadah time window.
 * null means no fixed time window (can be done any time during the day).
 */
enum class SunnahGoal(
    val title: String,
    val titleEn: String,
    val description: String,
    val hadith: String,
    val source: String,
    val dailyTarget: Int,
    val unit: String,
    val unitEn: String,
    /** Reward/benefit text in Indonesian from the relevant hadith. */
    val reward: String,
    /** Reward/benefit text in English. */
    val rewardEn: String,
    /** Emoji representing the reward (shown in achievement badge). */
    val rewardIcon: String,
    /** Hour (24h) to send the reminder notification; null = no fixed window. */
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    /** End of the ibadah time window; null if no end boundary. */
    val reminderEndHour: Int? = null,
    val reminderEndMinute: Int? = null,
) {
    SUNNAH_RAWATIB(
        title       = "Shalat Sunnah Rawatib (12 Rakaat)",
        titleEn     = "Rawatib Sunnah Prayer (12 Rak'ahs)",
        description = "Shalat sunnah sebelum dan sesudah shalat fardhu",
        hadith      = "Barangsiapa yang shalat dua belas rakaat pada siang dan malam, maka Allah akan membangunkan baginya sebuah rumah di surga. (Shahih Muslim no. 728)",
        source      = "HR. Muslim no. 728",
        dailyTarget = 12,
        unit        = "rakaat",
        unitEn      = "rak'ah(s)",
        reward      = "Satu istana di surga",
        rewardEn    = "A palace in Paradise",
        rewardIcon  = "🏰",
        // Spread across the day with fardhu prayers — no single reminder time
        reminderHour = null,
    ),
    TAHAJJUD(
        title       = "Shalat Tahajjud",
        titleEn     = "Tahajjud Night Prayer",
        description = "Shalat malam di sepertiga malam terakhir",
        hadith      = "Rabb kita turun ke langit dunia pada sepertiga malam terakhir, lalu berfirman: 'Siapa yang berdoa kepada-Ku, Aku kabulkan...' (HR. Bukhari no. 1145)",
        source      = "HR. Bukhari no. 1145, Muslim no. 758",
        dailyTarget = 2,
        unit        = "rakaat",
        unitEn      = "rak'ah(s)",
        reward      = "Allah turun ke langit dunia dan mengabulkan doa",
        rewardEn    = "Allah descends to the lowest heaven and answers prayers",
        rewardIcon  = "🌙",
        reminderHour       = 3,
        reminderMinute     = 0,
        reminderEndHour    = 4,
        reminderEndMinute  = 30,
    ),
    DHUHA(
        title       = "Shalat Dhuha",
        titleEn     = "Dhuha Morning Prayer",
        description = "Shalat sunnah di waktu dhuha (pagi)",
        hadith      = "Setiap persendian salah seorang di antara kalian wajib bersedekah... dan dua rakaat Dhuha sudah mencukupi semua itu. (HR. Muslim no. 720)",
        source      = "HR. Muslim no. 720",
        dailyTarget = 2,
        unit        = "rakaat",
        unitEn      = "rak'ah(s)",
        reward      = "Mencukupi sedekah untuk 360 persendian tubuh",
        rewardEn    = "Suffices as charity for all 360 joints of the body",
        rewardIcon  = "☀️",
        reminderHour       = 7,
        reminderMinute     = 0,
        reminderEndHour    = 11,
        reminderEndMinute  = 30,
    ),
    WITR(
        title       = "Shalat Witir",
        titleEn     = "Witr Prayer",
        description = "Shalat sunnah penutup malam (ganjil)",
        hadith      = "Wahai ahli Al-Quran, kerjakanlah shalat witir, sesungguhnya Allah itu Maha Ganjil dan menyukai yang ganjil. (HR. Abu Dawud no. 1416)",
        source      = "HR. Abu Dawud no. 1416, Tirmidzi no. 453",
        dailyTarget = 1,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "Ibadah yang disukai Allah Yang Maha Ganjil",
        rewardEn    = "A beloved act of worship to Allah, the Odd",
        rewardIcon  = "⭐",
        reminderHour       = 22,
        reminderMinute     = 0,
        reminderEndHour    = 23,
        reminderEndMinute  = 59,
    ),
    TILAWAH(
        title       = "Tilawah Al-Quran",
        titleEn     = "Qur'an Recitation",
        description = "Membaca Al-Quran setiap hari",
        hadith      = "Sebaik-baik kalian adalah yang mempelajari Al-Quran dan mengajarkannya. (HR. Bukhari no. 5027)",
        source      = "HR. Bukhari no. 5027",
        dailyTarget = 1,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "Menjadi sebaik-baik manusia",
        rewardEn    = "Among the best of people",
        rewardIcon  = "📖",
        reminderHour = null, // Any time is good
    ),
    DZIKIR_PAGI(
        title       = "Dzikir Pagi",
        titleEn     = "Morning Remembrance (Dzikr)",
        description = "Dzikir dan doa setelah Subuh",
        hadith      = "Tidaklah segolongan orang duduk berdzikir kepada Allah, melainkan malaikat mengelilingi mereka, rahmat menyelimuti mereka... (HR. Muslim no. 2700)",
        source      = "HR. Muslim no. 2700",
        dailyTarget = 1,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "Malaikat mengelilingi dan rahmat menyelimuti",
        rewardEn    = "Angels surround you and mercy envelops you",
        rewardIcon  = "✨",
        // After Subuh (~5:00 AM), window closes before Dhuhr
        reminderHour       = 5,
        reminderMinute     = 0,
        reminderEndHour    = 9,
        reminderEndMinute  = 0,
    ),
    DZIKIR_PETANG(
        title       = "Dzikir Petang",
        titleEn     = "Evening Remembrance (Dzikr)",
        description = "Dzikir dan doa setelah Ashar",
        hadith      = "Barangsiapa yang mengucapkan 'Subhanallah wa bihamdihi' seratus kali di pagi dan petang hari, tidak ada seorang pun yang datang pada hari Kiamat dengan sesuatu yang lebih baik... (HR. Muslim no. 2692)",
        source      = "HR. Muslim no. 2692",
        dailyTarget = 1,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "Tidak ada yang datang lebih baik pada hari Kiamat",
        rewardEn    = "Nothing better comes on the Day of Resurrection",
        rewardIcon  = "🌅",
        // After Ashr (~15:30), window closes before Maghrib
        reminderHour       = 15,
        reminderMinute     = 30,
        reminderEndHour    = 18,
        reminderEndMinute  = 0,
    ),
    SHALAT_BERJAMAAH(
        title       = "Shalat Fardhu Berjamaah",
        titleEn     = "Congregational Prayer",
        description = "Shalat 5 waktu berjamaah di masjid",
        hadith      = "Shalat berjamaah lebih utama dua puluh tujuh derajat daripada shalat sendirian. (HR. Bukhari no. 645, Muslim no. 650)",
        source      = "HR. Bukhari no. 645, Muslim no. 650",
        dailyTarget = 5,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "27 derajat lebih utama dari shalat sendirian",
        rewardEn    = "27 degrees more virtuous than praying alone",
        rewardIcon  = "🕌",
        reminderHour = null, // 5 different prayer times throughout the day
    ),
    PUASA_SENIN_KAMIS(
        title       = "Puasa Senin & Kamis",
        titleEn     = "Monday & Thursday Fasting",
        description = "Puasa sunnah di hari Senin dan Kamis",
        hadith      = "Amal-amal diperlihatkan pada hari Senin dan Kamis, maka aku suka jika amalku diperlihatkan saat aku sedang berpuasa. (HR. Tirmidzi no. 747)",
        source      = "HR. Tirmidzi no. 747",
        dailyTarget = 1,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "Amal diperlihatkan Allah dalam keadaan berpuasa",
        rewardEn    = "Deeds presented to Allah while fasting",
        rewardIcon  = "🌙",
        reminderHour = null, // Day-specific (Mon/Thu), no universal fixed time
    ),
    SHALAT_RAWATIB_FAJR(
        title       = "Shalat Qabliyah Subuh (2 Rakaat)",
        titleEn     = "Fajr Sunnah Prayer (2 Rak'ahs)",
        description = "Dua rakaat sunnah sebelum Subuh",
        hadith      = "Dua rakaat Fajar (sunnah Subuh) lebih baik daripada dunia dan seisinya. (HR. Muslim no. 725)",
        source      = "HR. Muslim no. 725",
        dailyTarget = 2,
        unit        = "rakaat",
        unitEn      = "rak'ah(s)",
        reward      = "Lebih baik dari dunia dan seisinya",
        rewardEn    = "Better than the world and all it contains",
        rewardIcon  = "🌟",
        // Before Subuh: ~4:30 AM
        reminderHour       = 4,
        reminderMinute     = 30,
        reminderEndHour    = 5,
        reminderEndMinute  = 30,
    ),
    SEDEKAH(
        title       = "Sedekah Harian",
        titleEn     = "Daily Charity (Sadaqah)",
        description = "Bersedekah setiap hari meski sedikit",
        hadith      = "Setiap hari di mana para hamba memasuki waktu pagi, ada dua malaikat yang turun. Salah satunya berdoa: 'Ya Allah, berilah orang yang berinfak itu ganti'... (HR. Bukhari no. 1442)",
        source      = "HR. Bukhari no. 1442, Muslim no. 1010",
        dailyTarget = 1,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "Dua malaikat mendoakan ganti yang berlipat ganda",
        rewardEn    = "Two angels pray for your wealth to be replenished",
        rewardIcon  = "💝",
        reminderHour = null,
    ),
    SHALAT_ISTIKHARAH(
        title       = "Shalat Sunnah Wudhu",
        titleEn     = "Post-Wudu Sunnah Prayer",
        description = "Dua rakaat setelah berwudhu",
        hadith      = "Tidaklah seorang muslim berwudhu kemudian mengerjakan shalat dua rakaat... melainkan surga menjadi wajib baginya. (HR. Muslim no. 234)",
        source      = "HR. Muslim no. 234",
        dailyTarget = 1,
        unit        = "kali",
        unitEn      = "time(s)",
        reward      = "Surga menjadi wajib baginya",
        rewardEn    = "Paradise becomes obligatory for him",
        rewardIcon  = "💧",
        reminderHour = null,
    );

    fun localizedTitle(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") titleEn else title

    fun localizedUnit(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") unitEn else unit

    fun localizedReward(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") rewardEn else reward

    /** Returns a human-readable time window string, e.g. "05:00 – 09:00". Null if no window. */
    fun reminderWindowLabel(): String? {
        val start = reminderHour ?: return null
        val end   = reminderEndHour ?: return null
        val sm    = reminderMinute ?: 0
        val em    = reminderEndMinute ?: 0
        return "%02d:%02d – %02d:%02d".format(start, sm, end, em)
    }
}
