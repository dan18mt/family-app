package com.familyhome.app.data

import android.content.Context
import android.content.res.Configuration
import com.familyhome.app.domain.model.AppLanguage
import java.util.Locale

/**
 * Utility for persisting and applying the user's app-level language preference.
 *
 * Uses SharedPreferences (synchronous) so the locale can be applied in
 * [android.app.Activity.attachBaseContext] before the content view is inflated.
 */
object LocaleHelper {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    /** Returns the persisted language, defaulting to English. */
    fun getStoredLanguage(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.ENGLISH.tag) ?: AppLanguage.ENGLISH.tag
        return AppLanguage.fromTag(tag)
    }

    /** Persists the chosen language. Call [wrap] or recreate the activity to apply it. */
    fun saveLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .apply()
    }

    /**
     * Wraps [context] with a configuration that uses [language]'s locale.
     * Pass the result to [android.app.Activity.attachBaseContext] so all
     * string resources resolve in the chosen language.
     */
    fun wrap(context: Context, language: AppLanguage): Context {
        val locale = Locale(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
