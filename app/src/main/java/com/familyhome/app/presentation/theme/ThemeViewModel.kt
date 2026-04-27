package com.familyhome.app.presentation.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@HiltViewModel
class ThemeViewModel @Inject constructor(
    // Injected directly so the ViewModel is testable without an Android Context.
    // AppModule provides the singleton DataStore<Preferences> instance.
    internal val dataStore: DataStore<Preferences>,
) : ViewModel() {

    companion object {
        internal val THEME_KEY = stringPreferencesKey("theme_preference")
    }

    /**
     * The current persisted theme preference.
     *
     * Uses [SharingStarted.Eagerly] so the upstream DataStore flow starts immediately
     * when the ViewModel is created — [themePreference.value] is always up-to-date
     * regardless of whether any composable is currently observing this flow.
     *
     * Previously [SharingStarted.WhileSubscribed] was used, which meant [value] was
     * stuck at the [ThemePreference.SYSTEM] initial value in any VM instance whose
     * [themePreference] flow had no subscribers (e.g. the nav-scoped ThemeViewModel
     * in HomeScreen). [cycleTheme] then always read SYSTEM, and always wrote LIGHT,
     * leaving the user trapped in light mode.
     */
    val themePreference = dataStore.data
        .map { prefs ->
            when (prefs[THEME_KEY]) {
                "LIGHT" -> ThemePreference.LIGHT
                "DARK"  -> ThemePreference.DARK
                else    -> ThemePreference.SYSTEM
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)

    fun setTheme(pref: ThemePreference) {
        viewModelScope.launch {
            dataStore.edit { it[THEME_KEY] = pref.name }
        }
    }

    /**
     * Advance to the next theme in the cycle: SYSTEM → LIGHT → DARK → SYSTEM.
     *
     * Reads the ground truth directly from [DataStore] instead of from
     * [themePreference.value].  This makes the function correct even if the StateFlow
     * has not yet received its first emission (possible during the brief window between
     * ViewModel creation and the first DataStore read).
     */
    fun cycleTheme() {
        viewModelScope.launch {
            val stored = dataStore.data.first()[THEME_KEY]
            val current = when (stored) {
                "LIGHT" -> ThemePreference.LIGHT
                "DARK"  -> ThemePreference.DARK
                else    -> ThemePreference.SYSTEM
            }
            val next = when (current) {
                ThemePreference.SYSTEM -> ThemePreference.LIGHT
                ThemePreference.LIGHT  -> ThemePreference.DARK
                ThemePreference.DARK   -> ThemePreference.SYSTEM
            }
            dataStore.edit { it[THEME_KEY] = next.name }
        }
    }
}
