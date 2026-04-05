package com.familyhome.app.domain.model

enum class AppLanguage(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    INDONESIAN("id", "Indonesia");

    companion object {
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}
