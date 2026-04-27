package com.familyhome.app.presentation.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ThemeViewModel].
 *
 * Uses a [FakeDataStore] (pure in-memory) so no Android Context or temp files are needed.
 *
 * The most important test is `cycleTheme reads DataStore ground truth not StateFlow value`,
 * which directly reproduces the original bug:
 *
 *   Before fix — [ThemeViewModel.cycleTheme] read [themePreference.value].  In nav-scoped
 *   ViewModel instances (HomeScreen creates one via hiltViewModel()) nobody ever collects
 *   [themePreference], so with [SharingStarted.WhileSubscribed] the upstream DataStore flow
 *   never started and [themePreference.value] was permanently stuck at the initial value
 *   [ThemePreference.SYSTEM].  Every call to cycleTheme() therefore wrote LIGHT (SYSTEM→LIGHT),
 *   leaving the user trapped in light mode no matter how many times they tapped the button.
 *
 *   After fix — cycleTheme() reads DataStore directly via [dataStore.data.first()], so the
 *   result is always based on the persisted truth, regardless of whether [themePreference] has
 *   any subscribers.  [SharingStarted.Eagerly] is also used so the StateFlow stays current.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataStore: FakeDataStore
    private lateinit var viewModel: ThemeViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dataStore = FakeDataStore()
        viewModel = ThemeViewModel(dataStore)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── cycleTheme progression ────────────────────────────────────────────────

    @Nested
    inner class CycleTheme {

        @Test
        fun `SYSTEM to LIGHT`() = runTest {
            // DataStore has no stored key → treated as SYSTEM
            viewModel.cycleTheme()
            advanceUntilIdle()

            assertEquals("LIGHT", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }

        @Test
        fun `LIGHT to DARK`() = runTest {
            dataStore.edit { it[ThemeViewModel.THEME_KEY] = "LIGHT" }

            viewModel.cycleTheme()
            advanceUntilIdle()

            assertEquals("DARK", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }

        @Test
        fun `DARK to SYSTEM`() = runTest {
            dataStore.edit { it[ThemeViewModel.THEME_KEY] = "DARK" }

            viewModel.cycleTheme()
            advanceUntilIdle()

            // ThemePreference.SYSTEM.name == "SYSTEM"
            assertEquals("SYSTEM", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }

        @Test
        fun `full cycle returns to original state after three presses`() = runTest {
            // Start from SYSTEM (empty store)
            viewModel.cycleTheme(); advanceUntilIdle() // → LIGHT
            viewModel.cycleTheme(); advanceUntilIdle() // → DARK
            viewModel.cycleTheme(); advanceUntilIdle() // → SYSTEM

            assertEquals("SYSTEM", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }

        /**
         * Regression test for the original bug.
         *
         * Creates a *fresh* ThemeViewModel whose [themePreference] StateFlow has no
         * subscribers — exactly the situation that HomeScreen's nav-scoped VM was in.
         *
         * Before the fix: [themePreference.value] == SYSTEM (initial, stale) →
         *   cycleTheme wrote LIGHT repeatedly → user stuck.
         * After the fix: cycleTheme reads DataStore directly → sees LIGHT → writes DARK.
         */
        @Test
        fun `cycleTheme reads DataStore ground truth even when StateFlow has no subscribers`() = runTest {
            // Seed DataStore with LIGHT (the user is in light mode)
            dataStore.edit { it[ThemeViewModel.THEME_KEY] = "LIGHT" }

            // Simulate the nav-scoped HomeScreen ThemeViewModel: created fresh, nobody
            // ever collects themePreference — themePreference.value starts at SYSTEM.
            val freshVm = ThemeViewModel(dataStore)

            // The old implementation would read themePreference.value == SYSTEM and write LIGHT.
            // The new implementation reads from DataStore directly and must write DARK.
            freshVm.cycleTheme()
            advanceUntilIdle()

            assertEquals(
                "DARK",
                dataStore.data.first()[ThemeViewModel.THEME_KEY],
                "cycleTheme must advance LIGHT → DARK regardless of StateFlow subscription state",
            )
        }

        @Test
        fun `cycleTheme with unknown stored value treats it as SYSTEM`() = runTest {
            // Corrupt / unknown value falls back to SYSTEM behaviour
            dataStore.edit { it[ThemeViewModel.THEME_KEY] = "SOMETHING_UNEXPECTED" }

            viewModel.cycleTheme()
            advanceUntilIdle()

            assertEquals("LIGHT", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }
    }

    // ── setTheme ─────────────────────────────────────────────────────────────

    @Nested
    inner class SetTheme {

        @Test
        fun `persists LIGHT`() = runTest {
            viewModel.setTheme(ThemePreference.LIGHT)
            advanceUntilIdle()

            assertEquals("LIGHT", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }

        @Test
        fun `persists DARK`() = runTest {
            viewModel.setTheme(ThemePreference.DARK)
            advanceUntilIdle()

            assertEquals("DARK", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }

        @Test
        fun `persists SYSTEM`() = runTest {
            viewModel.setTheme(ThemePreference.SYSTEM)
            advanceUntilIdle()

            assertEquals("SYSTEM", dataStore.data.first()[ThemeViewModel.THEME_KEY])
        }
    }

    // ── themePreference StateFlow ─────────────────────────────────────────────

    @Nested
    inner class ThemePreferenceFlow {

        @Test
        fun `emits LIGHT when LIGHT is persisted`() = runTest {
            dataStore.edit { it[ThemeViewModel.THEME_KEY] = "LIGHT" }
            advanceUntilIdle()

            assertEquals(ThemePreference.LIGHT, viewModel.themePreference.value)
        }

        @Test
        fun `emits DARK when DARK is persisted`() = runTest {
            dataStore.edit { it[ThemeViewModel.THEME_KEY] = "DARK" }
            advanceUntilIdle()

            assertEquals(ThemePreference.DARK, viewModel.themePreference.value)
        }

        @Test
        fun `emits SYSTEM when nothing is persisted`() = runTest {
            // Empty DataStore → SYSTEM
            advanceUntilIdle()

            assertEquals(ThemePreference.SYSTEM, viewModel.themePreference.value)
        }

        @Test
        fun `updates immediately after setTheme`() = runTest {
            viewModel.setTheme(ThemePreference.DARK)
            advanceUntilIdle()

            assertEquals(ThemePreference.DARK, viewModel.themePreference.value)
        }
    }
}

// ── Test double ───────────────────────────────────────────────────────────────

/**
 * Pure in-memory [DataStore] that satisfies [DataStore.updateData] and exposes a
 * [MutableStateFlow] as [data].  No Android Context, no temp files required.
 *
 * The real [edit] extension function from DataStore-Preferences delegates to
 * [updateData], so [edit] calls work transparently on this fake.
 */
private class FakeDataStore : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(_data.value)
        _data.value = updated
        return updated
    }
}
