package com.familyhome.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** App-wide DataStore instance, created once per process. */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "family_home_prefs")
