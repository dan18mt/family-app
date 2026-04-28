package com.familyhome.app.presentation.screens

import app.cash.turbine.test
import com.familyhome.app.data.notification.AlarmScheduler
import com.familyhome.app.data.sync.DeletionTracker
import com.familyhome.app.domain.model.*
import com.familyhome.app.domain.usecase.chore.*
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import com.familyhome.app.domain.usecase.user.GetFamilyMembersUseCase
import com.familyhome.app.presentation.screens.chores.ChoresUiState
import com.familyhome.app.presentation.screens.chores.ChoresViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChoresViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    private lateinit var getFamilyMembersUseCase: GetFamilyMembersUseCase
    private lateinit var logChoreUseCase: LogChoreUseCase
    private lateinit var getChoreHistoryUseCase: GetChoreHistoryUseCase
    private lateinit var getRecurringTasksUseCase: GetRecurringTasksUseCase
    private lateinit var completeRecurringTaskUseCase: CompleteRecurringTaskUseCase
    private lateinit var addRecurringTaskUseCase: AddRecurringTaskUseCase
    private lateinit var updateRecurringTaskUseCase: UpdateRecurringTaskUseCase
    private lateinit var deleteRecurringTaskUseCase: DeleteRecurringTaskUseCase
    private lateinit var deleteChoreLogUseCase: DeleteChoreLogUseCase
    private lateinit var assignChoreUseCase: AssignChoreUseCase
    private lateinit var respondToAssignmentUseCase: RespondToChoreAssignmentUseCase
    private lateinit var getChoreAssignmentsUseCase: GetChoreAssignmentsUseCase
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var deletionTracker: DeletionTracker

    private fun fatherUser() = User(
        id = "f1", name = "Father", role = Role.FATHER,
        parentId = null, avatarUri = null, createdAt = 1L,
    )

    private fun kidUser() = User(
        id = "k1", name = "Kid", role = Role.KID,
        parentId = "f1", avatarUri = null, createdAt = 2L,
    )

    private fun sampleTask() = RecurringTask(
        id = "t1", taskName = "Wash dishes", frequency = Frequency.DAILY,
        assignedTo = "k1", lastDoneAt = null, nextDueAt = System.currentTimeMillis() + 86400000,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        getCurrentUserUseCase = mockk()
        getFamilyMembersUseCase = mockk()
        logChoreUseCase = mockk()
        getChoreHistoryUseCase = mockk()
        getRecurringTasksUseCase = mockk()
        completeRecurringTaskUseCase = mockk()
        addRecurringTaskUseCase = mockk()
        updateRecurringTaskUseCase = mockk()
        deleteRecurringTaskUseCase = mockk()
        deleteChoreLogUseCase = mockk()
        assignChoreUseCase = mockk()
        respondToAssignmentUseCase = mockk()
        getChoreAssignmentsUseCase = mockk()
        alarmScheduler = mockk(relaxed = true)
        deletionTracker = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun setupDefaultMocks(user: User = fatherUser()) {
        coEvery { getCurrentUserUseCase() } returns user
        every { getFamilyMembersUseCase() } returns flowOf(listOf(fatherUser(), kidUser()))
        every { getChoreHistoryUseCase(any(), any()) } returns flowOf(emptyList())
        every { getRecurringTasksUseCase() } returns flowOf(emptyList())
        every { getChoreAssignmentsUseCase.pendingForUser(any()) } returns flowOf(emptyList())
        every { getChoreAssignmentsUseCase.all() } returns flowOf(emptyList())
    }

    private fun createViewModel() = ChoresViewModel(
        getCurrentUserUseCase = getCurrentUserUseCase,
        getFamilyMembersUseCase = getFamilyMembersUseCase,
        logChoreUseCase = logChoreUseCase,
        getChoreHistoryUseCase = getChoreHistoryUseCase,
        getRecurringTasksUseCase = getRecurringTasksUseCase,
        completeRecurringTaskUseCase = completeRecurringTaskUseCase,
        addRecurringTaskUseCase = addRecurringTaskUseCase,
        updateRecurringTaskUseCase = updateRecurringTaskUseCase,
        deleteRecurringTaskUseCase = deleteRecurringTaskUseCase,
        deleteChoreLogUseCase = deleteChoreLogUseCase,
        assignChoreUseCase = assignChoreUseCase,
        respondToAssignmentUseCase = respondToAssignmentUseCase,
        getChoreAssignmentsUseCase = getChoreAssignmentsUseCase,
        alarmScheduler = alarmScheduler,
        deletionTracker = deletionTracker,
    )

    @Test
    fun `init loads current user and sets state`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.currentUser)
        assertEquals("f1", state.currentUser?.id)
        assertFalse(state.isLoading)
    }

    @Test
    fun `init loads family members`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.allUsers.size)
    }

    @Test
    fun `init loads recurring tasks`() = runTest {
        val tasks = listOf(sampleTask())
        setupDefaultMocks()
        every { getRecurringTasksUseCase() } returns flowOf(tasks)

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.recurringTasks.size)
        assertEquals("Wash dishes", vm.state.value.recurringTasks[0].taskName)
    }

    @Test
    fun `logChore calls use case and does not set error on success`() = runTest {
        setupDefaultMocks()
        coEvery { logChoreUseCase(any(), any(), any(), any()) } returns Result.success(
            ChoreLog(id = "cl1", taskName = "Mop", doneBy = "f1", doneAt = 1L, note = null)
        )

        val vm = createViewModel()
        advanceUntilIdle()

        vm.logChore("Mop", null)
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        coVerify { logChoreUseCase(fatherUser(), "Mop", "f1", null) }
    }

    @Test
    fun `logChore sets error on failure`() = runTest {
        setupDefaultMocks()
        coEvery { logChoreUseCase(any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("Permission denied"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.logChore("Mop", null)
        advanceUntilIdle()

        assertEquals("Permission denied", vm.state.value.error)
    }

    @Test
    fun `logChore does nothing when no current user`() = runTest {
        coEvery { getCurrentUserUseCase() } returns null
        every { getFamilyMembersUseCase() } returns flowOf(emptyList())
        every { getRecurringTasksUseCase() } returns flowOf(emptyList())
        every { getChoreAssignmentsUseCase.all() } returns flowOf(emptyList())

        val vm = createViewModel()
        advanceUntilIdle()

        vm.logChore("Mop", null)
        advanceUntilIdle()

        coVerify(exactly = 0) { logChoreUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `completeTask calls use case and records deletion`() = runTest {
        setupDefaultMocks()
        val task = sampleTask()
        coEvery { completeRecurringTaskUseCase(any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.completeTask(task)
        advanceUntilIdle()

        coVerify { completeRecurringTaskUseCase(fatherUser(), task) }
        coVerify { alarmScheduler.cancel(task.id) }
        coVerify { deletionTracker.recordRecurringTaskDeletion(task.id) }
    }

    @Test
    fun `addScheduledTask calls use case and schedules alarm on success`() = runTest {
        setupDefaultMocks()
        val taskWithAlarm = sampleTask().copy(
            scheduledAt = System.currentTimeMillis() + 86400000,
            reminderMinutesBefore = 15,
        )
        coEvery { addRecurringTaskUseCase(any(), any(), any(), any(), any(), any()) } returns
            Result.success(taskWithAlarm)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addScheduledTask("Wash dishes", Frequency.DAILY, "k1", taskWithAlarm.scheduledAt, 15)
        advanceUntilIdle()

        coVerify { alarmScheduler.schedule(taskWithAlarm) }
        assertNull(vm.state.value.error)
    }

    @Test
    fun `addScheduledTask sets error on failure`() = runTest {
        setupDefaultMocks()
        coEvery { addRecurringTaskUseCase(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(SecurityException("Not allowed"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.addScheduledTask("Task", Frequency.DAILY, null, null, null)
        advanceUntilIdle()

        assertEquals("Not allowed", vm.state.value.error)
    }

    @Test
    fun `deleteTask calls use case and records deletion on success`() = runTest {
        setupDefaultMocks()
        val task = sampleTask()
        coEvery { deleteRecurringTaskUseCase(any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteTask(task)
        advanceUntilIdle()

        coVerify { deleteRecurringTaskUseCase(fatherUser(), task) }
        coVerify { deletionTracker.recordRecurringTaskDeletion(task.id) }
    }

    @Test
    fun `deleteTask sets error on failure`() = runTest {
        setupDefaultMocks()
        coEvery { deleteRecurringTaskUseCase(any(), any()) } returns
            Result.failure(SecurityException("Not allowed"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.deleteTask(sampleTask())
        advanceUntilIdle()

        assertEquals("Not allowed", vm.state.value.error)
    }

    @Test
    fun `acceptAssignment calls respondToAssignmentUseCase`() = runTest {
        setupDefaultMocks()
        val assignment = ChoreAssignment(
            id = "a1", taskId = "t1", taskName = "Wash", assignedTo = "f1",
            assignedBy = "f1", status = AssignmentStatus.PENDING,
            declineReason = null, assignedAt = 1L, respondedAt = null,
        )
        coEvery { respondToAssignmentUseCase.accept(any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.acceptAssignment(assignment)
        advanceUntilIdle()

        coVerify { respondToAssignmentUseCase.accept(fatherUser(), assignment) }
    }

    @Test
    fun `declineAssignment calls respondToAssignmentUseCase`() = runTest {
        setupDefaultMocks()
        val assignment = ChoreAssignment(
            id = "a1", taskId = "t1", taskName = "Wash", assignedTo = "f1",
            assignedBy = "f1", status = AssignmentStatus.PENDING,
            declineReason = null, assignedAt = 1L, respondedAt = null,
        )
        coEvery { respondToAssignmentUseCase.decline(any(), any(), any()) } returns Result.success(Unit)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.declineAssignment(assignment, "Too busy")
        advanceUntilIdle()

        coVerify { respondToAssignmentUseCase.decline(fatherUser(), assignment, "Too busy") }
    }

    @Test
    fun `clearError resets error to null`() = runTest {
        setupDefaultMocks()
        coEvery { logChoreUseCase(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("Oops"))

        val vm = createViewModel()
        advanceUntilIdle()

        vm.logChore("Task", null)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.clearError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `changeHistoryRange reloads history with new days`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(7, vm.state.value.historyDays)

        vm.changeHistoryRange(30)
        advanceUntilIdle()

        verify { getChoreHistoryUseCase(fatherUser(), 30) }
        assertEquals(30, vm.state.value.historyDays)
    }

    @Test
    fun `initial state has correct defaults`() {
        val state = ChoresUiState()
        assertTrue(state.history.isEmpty())
        assertTrue(state.recurringTasks.isEmpty())
        assertTrue(state.pendingAssignments.isEmpty())
        assertTrue(state.allAssignments.isEmpty())
        assertNull(state.currentUser)
        assertTrue(state.allUsers.isEmpty())
        assertEquals(7, state.historyDays)
        assertTrue(state.isLoading)
        assertNull(state.error)
    }
}
