package com.familyhome.app.presentation.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.util.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_preference")
    }

    val themePreference = context.dataStore.data
        .map { prefs ->
            when (prefs[THEME_KEY]) {
                "LIGHT" -> ThemePreference.LIGHT
                "DARK"  -> ThemePreference.DARK
                else    -> ThemePreference.SYSTEM
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference.SYSTEM)

    fun setTheme(pref: ThemePreference) {
        viewModelScope.launch {
            context.dataStore.edit { it[THEME_KEY] = pref.name }
        }
    }

    fun cycleTheme() {
        val next = when (themePreference.value) {
            ThemePreference.SYSTEM -> ThemePreference.LIGHT
            ThemePreference.LIGHT  -> ThemePreference.DARK
            ThemePreference.DARK   -> ThemePreference.SYSTEM
        }
        setTheme(next)
    }
}
