package com.familyhome.app.domain.model

enum class SunnahGoal(
    /** Primary title in Indonesian. */
    val title: String,
    /** Title in English. */
    val titleEn: String,
    val description: String,
    val hadith: String,
    val source: String,
    val dailyTarget: Int,
    /** Unit label in Indonesian. */
    val unit: String,
    /** Unit label in English. */
    val unitEn: String,
) {
    SUNNAH_RAWATIB(
        title = "Shalat Sunnah Rawatib (12 Rakaat)",
        titleEn = "Rawatib Sunnah Prayer (12 Rak'ahs)",
        description = "Shalat sunnah sebelum dan sesudah shalat fardhu",
        hadith = "Barangsiapa yang shalat dua belas rakaat pada siang dan malam, maka Allah akan membangunkan baginya sebuah rumah di surga. (Shahih Muslim no. 728)",
        source = "HR. Muslim no. 728",
        dailyTarget = 12,
        unit = "rakaat",
        unitEn = "rak'ah(s)"
    ),
    TAHAJJUD(
        title = "Shalat Tahajjud",
        titleEn = "Tahajjud Night Prayer",
        description = "Shalat malam di sepertiga malam terakhir",
        hadith = "Rabb kita turun ke langit dunia pada sepertiga malam terakhir, lalu berfirman: 'Siapa yang berdoa kepada-Ku, Aku kabulkan...' (HR. Bukhari no. 1145)",
        source = "HR. Bukhari no. 1145, Muslim no. 758",
        dailyTarget = 2,
        unit = "rakaat",
        unitEn = "rak'ah(s)"
    ),
    DHUHA(
        title = "Shalat Dhuha",
        titleEn = "Dhuha Morning Prayer",
        description = "Shalat sunnah di waktu dhuha (pagi)",
        hadith = "Setiap persendian salah seorang di antara kalian wajib bersedekah... dan dua rakaat Dhuha sudah mencukupi semua itu. (HR. Muslim no. 720)",
        source = "HR. Muslim no. 720",
        dailyTarget = 2,
        unit = "rakaat",
        unitEn = "rak'ah(s)"
    ),
    WITR(
        title = "Shalat Witir",
        titleEn = "Witr Prayer",
        description = "Shalat sunnah penutup malam (ganjil)",
        hadith = "Wahai ahli Al-Quran, kerjakanlah shalat witir, sesungguhnya Allah itu Maha Ganjil dan menyukai yang ganjil. (HR. Abu Dawud no. 1416)",
        source = "HR. Abu Dawud no. 1416, Tirmidzi no. 453",
        dailyTarget = 1,
        unit = "kali",
        unitEn = "time(s)"
    ),
    TILAWAH(
        title = "Tilawah Al-Quran",
        titleEn = "Qur'an Recitation",
        description = "Membaca Al-Quran setiap hari",
        hadith = "Sebaik-baik kalian adalah yang mempelajari Al-Quran dan mengajarkannya. (HR. Bukhari no. 5027)",
        source = "HR. Bukhari no. 5027",
        dailyTarget = 1,
        unit = "kali",
        unitEn = "time(s)"
    ),
    DZIKIR_PAGI(
        title = "Dzikir Pagi",
        titleEn = "Morning Remembrance (Dzikr)",
        description = "Dzikir dan doa setelah Subuh",
        hadith = "Tidaklah segolongan orang duduk berdzikir kepada Allah, melainkan malaikat mengelilingi mereka, rahmat menyelimuti mereka... (HR. Muslim no. 2700)",
        source = "HR. Muslim no. 2700",
        dailyTarget = 1,
        unit = "kali",
        unitEn = "time(s)"
    ),
    DZIKIR_PETANG(
        title = "Dzikir Petang",
        titleEn = "Evening Remembrance (Dzikr)",
        description = "Dzikir dan doa setelah Ashar",
        hadith = "Barangsiapa yang mengucapkan 'Subhanallah wa bihamdihi' seratus kali di pagi dan petang hari, tidak ada seorang pun yang datang pada hari Kiamat dengan sesuatu yang lebih baik... (HR. Muslim no. 2692)",
        source = "HR. Muslim no. 2692",
        dailyTarget = 1,
        unit = "kali",
        unitEn = "time(s)"
    ),
    SHALAT_BERJAMAAH(
        title = "Shalat Fardhu Berjamaah",
        titleEn = "Congregational Prayer",
        description = "Shalat 5 waktu berjamaah di masjid",
        hadith = "Shalat berjamaah lebih utama dua puluh tujuh derajat daripada shalat sendirian. (HR. Bukhari no. 645, Muslim no. 650)",
        source = "HR. Bukhari no. 645, Muslim no. 650",
        dailyTarget = 5,
        unit = "kali",
        unitEn = "time(s)"
    ),
    PUASA_SENIN_KAMIS(
        title = "Puasa Senin & Kamis",
        titleEn = "Monday & Thursday Fasting",
        description = "Puasa sunnah di hari Senin dan Kamis",
        hadith = "Amal-amal diperlihatkan pada hari Senin dan Kamis, maka aku suka jika amalku diperlihatkan saat aku sedang berpuasa. (HR. Tirmidzi no. 747)",
        source = "HR. Tirmidzi no. 747",
        dailyTarget = 1,
        unit = "kali",
        unitEn = "time(s)"
    ),
    SHALAT_RAWATIB_FAJR(
        title = "Shalat Qabliyah Subuh (2 Rakaat)",
        titleEn = "Fajr Sunnah Prayer (2 Rak'ahs)",
        description = "Dua rakaat sunnah sebelum Subuh",
        hadith = "Dua rakaat Fajar (sunnah Subuh) lebih baik daripada dunia dan seisinya. (HR. Muslim no. 725)",
        source = "HR. Muslim no. 725",
        dailyTarget = 2,
        unit = "rakaat",
        unitEn = "rak'ah(s)"
    ),
    SEDEKAH(
        title = "Sedekah Harian",
        titleEn = "Daily Charity (Sadaqah)",
        description = "Bersedekah setiap hari meski sedikit",
        hadith = "Setiap hari di mana para hamba memasuki waktu pagi, ada dua malaikat yang turun. Salah satunya berdoa: 'Ya Allah, berilah orang yang berinfak itu ganti'... (HR. Bukhari no. 1442)",
        source = "HR. Bukhari no. 1442, Muslim no. 1010",
        dailyTarget = 1,
        unit = "kali",
        unitEn = "time(s)"
    ),
    SHALAT_ISTIKHARAH(
        title = "Shalat Sunnah Wudhu",
        titleEn = "Post-Wudu Sunnah Prayer",
        description = "Dua rakaat setelah berwudhu",
        hadith = "Tidaklah seorang muslim berwudhu kemudian mengerjakan shalat dua rakaat... melainkan surga menjadi wajib baginya. (HR. Muslim no. 234)",
        source = "HR. Muslim no. 234",
        dailyTarget = 1,
        unit = "kali",
        unitEn = "time(s)"
    );

    /** Returns the title localized to the current Android locale. */
    fun localizedTitle(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") titleEn else title

    /** Returns the unit localized to the current Android locale. */
    fun localizedUnit(languageTag: String = java.util.Locale.getDefault().language): String =
        if (languageTag == "en") unitEn else unit
}
