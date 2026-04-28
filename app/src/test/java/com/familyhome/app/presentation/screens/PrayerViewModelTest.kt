package com.familyhome.app.presentation.screens

import com.familyhome.app.data.notification.PrayerReminderScheduler
import com.familyhome.app.data.sync.DeletionTracker
import com.familyhome.app.data.sync.MemberPresenceTracker
import com.familyhome.app.data.sync.PrayerReminderStore
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.model.*
import com.familyhome.app.domain.repository.PrayerRepository
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import com.familyhome.app.presentation.screens.prayer.PrayerUiState
import com.familyhome.app.presentation.screens.prayer.PrayerViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var prayerRepository: PrayerRepository
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getFamilyMembersUseCase: GetFamilyMembersUseCase
    private lateinit var deletionTracker: DeletionTracker
    private lateinit var reminderScheduler: PrayerReminderScheduler
    private lateinit var prayerReminderStore: PrayerReminderStore
    private lateinit var syncRepository: SyncRepositoryImpl
    private lateinit var presenceTracker: MemberPresenceTracker

    private fun fatherUser() = User(
        id = "f1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null, createdAt = 1L,
    )

    private fun kidUser() = User(
        id = "k1", name = "Kid", role = Role.KID,
        parentId = "f1", avatarUri = null, createdAt = 2L,
    )

    private fun goalSetting(
        id: String = "g1",
        sunnahKey: String = "SUNNAH_RAWATIB",
        isEnabled: Boolean = true,
        assignedUserIds: List<String>? = null,
        createdBy: String = "f1",
    ) = PrayerGoalSetting(
        id = id, sunnahKey = sunnahKey, isEnabled = isEnabled,
        assignedUserIds = assignedUserIds, reminderEnabled = false,
        createdBy = createdBy, createdAt = 1L,
    )

    private fun prayerLog(
        sunnahKey: String = "SUNNAH_RAWATIB",
        userId: String = "f1",
        epochDay: Long = 19000L,
        completedCount: Int = 12,
    ) = PrayerLog(
        id = "pl1", userId = userId, sunnahKey = sunnahKey,
        epochDay = epochDay, completedCount = completedCount,
        loggedAt = System.currentTimeMillis(),
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        prayerRepository = mockk(relaxed = true)
        getCurrentUserUseCase = mockk()
        getFamilyMembersUseCase = mockk()
        deletionTracker = mockk(relaxed = true)
        reminderScheduler = mockk(relaxed = true)
        prayerReminderStore = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        presenceTracker = mockk()
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun setupDefaultMocks(user: User = fatherUser()) {
        coEvery { getCurrentUserUseCase() } returns user
        every { getFamilyMembersUseCase() } returns flowOf(listOf(fatherUser(), kidUser()))
        every { prayerRepository.getAllGoalSettings() } returns flowOf(emptyList())
        every { prayerRepository.getLogsForDay(any()) } returns flowOf(emptyList())
        every { prayerRepository.getLogsSince(any()) } returns flowOf(emptyList())
        every { presenceTracker.networkOnlineUserIds } returns MutableStateFlow(emptySet())
    }

    private fun createViewModel() = PrayerViewModel(
        prayerRepository = prayerRepository,
        getCurrentUserUseCase = getCurrentUserUseCase,
        getFamilyMembersUseCase = getFamilyMembersUseCase,
        deletionTracker = deletionTracker,
        reminderScheduler = reminderScheduler,
        prayerReminderStore = prayerReminderStore,
        syncRepository = syncRepository,
        presenceTracker = presenceTracker,
    )

    @Test
    fun `init loads current user`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("f1", vm.state.value.currentUser?.id)
    }

    @Test
    fun `init loads family members`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.allUsers.size)
    }

    @Test
    fun `init loads goal settings and sets isLoading false`() = runTest {
        val goals = listOf(goalSetting())
        setupDefaultMocks()
        every { prayerRepository.getAllGoalSettings() } returns flowOf(goals)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.goalSettings.size)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `init loads today logs`() = runTest {
        val logs = listOf(prayerLog())
        setupDefaultMocks()
        every { prayerRepository.getLogsForDay(any()) } returns flowOf(logs)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.todayLogs.size)
    }

    @Test
    fun `toggleGoal flips isEnabled`() = runTest {
        val goal = goalSetting(isEnabled = true)
        setupDefaultMocks()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleGoal(goal)
        advanceUntilIdle()

        coVerify { prayerRepository.updateGoalSetting(match { !it.isEnabled }) }
    }

    @Test
    fun `addGoal inserts new goal for leader`() = runTest {
        setupDefaultMocks()
        every { prayerRepository.getAllGoalSettings() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addGoal("SUNNAH_RAWATIB", null)
        advanceUntilIdle()

        coVerify { prayerRepository.insertGoalSetting(match { it.sunnahKey == "SUNNAH_RAWATIB" }) }
    }

    @Test
    fun `addGoal does not duplicate existing goal for leader`() = runTest {
        val existing = goalSetting(sunnahKey = "SUNNAH_RAWATIB")
        setupDefaultMocks()
        every { prayerRepository.getAllGoalSettings() } returns flowOf(listOf(existing))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addGoal("SUNNAH_RAWATIB", null)
        advanceUntilIdle()

        coVerify(exactly = 0) { prayerRepository.insertGoalSetting(any()) }
    }

    @Test
    fun `removeGoal deletes and records deletion for leader`() = runTest {
        val goal = goalSetting()
        setupDefaultMocks()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.removeGoal(goal)
        advanceUntilIdle()

        coVerify { prayerRepository.deleteGoalSetting("g1") }
        coVerify { deletionTracker.recordPrayerGoalDeletion("g1") }
        coVerify { reminderScheduler.cancel("SUNNAH_RAWATIB") }
    }

    @Test
    fun `removeGoal blocked for kid on non-personal goal`() = runTest {
        setupDefaultMocks(kidUser())
        val familyGoal = goalSetting(createdBy = "f1", assignedUserIds = null)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.removeGoal(familyGoal)
        advanceUntilIdle()

        coVerify(exactly = 0) { prayerRepository.deleteGoalSetting(any()) }
    }

    @Test
    fun `removeGoal allowed for kid on own personal goal`() = runTest {
        setupDefaultMocks(kidUser())
        val personalGoal = goalSetting(createdBy = "k1", assignedUserIds = listOf("k1"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.removeGoal(personalGoal)
        advanceUntilIdle()

        coVerify { prayerRepository.deleteGoalSetting(personalGoal.id) }
    }

    @Test
    fun `logPrayer does nothing without current user`() = runTest {
        coEvery { getCurrentUserUseCase() } returns null
        every { getFamilyMembersUseCase() } returns flowOf(emptyList())
        every { prayerRepository.getAllGoalSettings() } returns flowOf(emptyList())
        every { prayerRepository.getLogsForDay(any()) } returns flowOf(emptyList())
        every { prayerRepository.getLogsSince(any()) } returns flowOf(emptyList())
        every { presenceTracker.networkOnlineUserIds } returns MutableStateFlow(emptySet())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.logPrayer("SUNNAH_RAWATIB")
        advanceUntilIdle()

        coVerify(exactly = 0) { prayerRepository.upsertLog(any()) }
    }

    @Test
    fun `sendReminder stores reminder and attempts direct push`() = runTest {
        setupDefaultMocks()

        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendReminder("k1", "Kid")
        advanceUntilIdle()

        coVerify { prayerReminderStore.addReminder(match { it.targetUserId == "k1" }) }
        coVerify { syncRepository.sendDirectReminder(eq("k1"), any()) }
        assertEquals("Kid", vm.state.value.reminderSentTo)
    }

    @Test
    fun `clearReminderSent resets reminderSentTo`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        vm.sendReminder("k1", "Kid")
        advanceUntilIdle()
        assertNotNull(vm.state.value.reminderSentTo)

        vm.clearReminderSent()
        assertNull(vm.state.value.reminderSentTo)
    }

    @Test
    fun `clearError resets error`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        vm.clearError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `addAssigneeToGoal adds user to assigned list`() = runTest {
        val goal = goalSetting(assignedUserIds = listOf("f1"))
        setupDefaultMocks()
        every { prayerRepository.getAllGoalSettings() } returns flowOf(listOf(goal))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addAssigneeToGoal("g1", "k1")
        advanceUntilIdle()

        coVerify {
            prayerRepository.updateGoalSetting(match {
                it.assignedUserIds?.contains("k1") == true
            })
        }
    }

    @Test
    fun `addAssigneeToGoal is noop for null assignedUserIds (all family)`() = runTest {
        val goal = goalSetting(assignedUserIds = null)
        setupDefaultMocks()
        every { prayerRepository.getAllGoalSettings() } returns flowOf(listOf(goal))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addAssigneeToGoal("g1", "k1")
        advanceUntilIdle()

        coVerify(exactly = 0) { prayerRepository.updateGoalSetting(any()) }
    }

    @Test
    fun `addAssigneeToGoal is noop for already assigned user`() = runTest {
        val goal = goalSetting(assignedUserIds = listOf("f1", "k1"))
        setupDefaultMocks()
        every { prayerRepository.getAllGoalSettings() } returns flowOf(listOf(goal))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addAssigneeToGoal("g1", "k1")
        advanceUntilIdle()

        coVerify(exactly = 0) { prayerRepository.updateGoalSetting(any()) }
    }

    // ── PrayerUiState unit tests ────────────────────────────────────────────

    @Test
    fun `PrayerUiState activeGoalsFor filters by enabled and assigned`() {
        val state = PrayerUiState(
            goalSettings = listOf(
                goalSetting(id = "g1", isEnabled = true, assignedUserIds = null),
                goalSetting(id = "g2", isEnabled = false, assignedUserIds = null),
                goalSetting(id = "g3", isEnabled = true, assignedUserIds = listOf("k1")),
            ),
        )

        val fatherGoals = state.activeGoalsFor("f1")
        assertEquals(1, fatherGoals.size)
        assertEquals("g1", fatherGoals[0].id)

        val kidGoals = state.activeGoalsFor("k1")
        assertEquals(2, kidGoals.size)
    }

    @Test
    fun `PrayerUiState todayLogFor returns matching log`() {
        val log = prayerLog(sunnahKey = "SUNNAH_RAWATIB", userId = "f1")
        val state = PrayerUiState(todayLogs = listOf(log))

        assertNotNull(state.todayLogFor("SUNNAH_RAWATIB", "f1"))
        assertNull(state.todayLogFor("SUNNAH_RAWATIB", "k1"))
        assertNull(state.todayLogFor("OTHER_KEY", "f1"))
    }

    @Test
    fun `PrayerUiState completedTodayCount counts completed goals`() {
        val state = PrayerUiState(
            goalSettings = listOf(
                goalSetting(id = "g1", sunnahKey = "SUNNAH_RAWATIB", assignedUserIds = null),
            ),
            todayLogs = listOf(
                prayerLog(sunnahKey = "SUNNAH_RAWATIB", userId = "f1", completedCount = 12),
            ),
        )

        assertEquals(1, state.completedTodayCount("f1"))
        assertEquals(0, state.completedTodayCount("k1"))
    }

    @Test
    fun `PrayerUiState totalCountForPeriod sums counts`() {
        val state = PrayerUiState(
            monthLogs = listOf(
                prayerLog(epochDay = 100, completedCount = 5),
                prayerLog(epochDay = 101, completedCount = 7),
                prayerLog(epochDay = 200, completedCount = 3), // outside range
            ),
        )

        assertEquals(12, state.totalCountForPeriod("f1", "SUNNAH_RAWATIB", 100, 101))
        assertEquals(5, state.totalCountForPeriod("f1", "SUNNAH_RAWATIB", 100, 100))
    }

    @Test
    fun `PrayerGoalSetting isAssignedTo works correctly`() {
        val allFamily = goalSetting(assignedUserIds = null)
        assertTrue(allFamily.isAssignedTo("anyone"))

        val specific = goalSetting(assignedUserIds = listOf("f1", "k1"))
        assertTrue(specific.isAssignedTo("f1"))
        assertFalse(specific.isAssignedTo("w1"))
    }

    @Test
    fun `PrayerGoalSetting isPersonalGoalOf works correctly`() {
        val personal = goalSetting(createdBy = "k1", assignedUserIds = listOf("k1"))
        assertTrue(personal.isPersonalGoalOf("k1"))
        assertFalse(personal.isPersonalGoalOf("f1"))

        val family = goalSetting(createdBy = "f1", assignedUserIds = null)
        assertFalse(family.isPersonalGoalOf("f1"))
    }

    @Test
    fun `PrayerUiState initial state has correct defaults`() {
        val state = PrayerUiState()
        assertTrue(state.goalSettings.isEmpty())
        assertTrue(state.todayLogs.isEmpty())
        assertTrue(state.monthLogs.isEmpty())
        assertNull(state.currentUser)
        assertTrue(state.allUsers.isEmpty())
        assertTrue(state.isLoading)
        assertNull(state.error)
        assertNull(state.reminderSentTo)
        assertTrue(state.onlineUserIds.isEmpty())
        assertTrue(state.activeIslamicEventKeys.isEmpty())
        assertTrue(state.islamicEvents.isEmpty())
    }
}
