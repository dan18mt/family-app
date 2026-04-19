package com.familyhome.app.domain.usecase

import com.familyhome.app.data.notification.AlarmScheduler
import com.familyhome.app.domain.model.AssignmentStatus
import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.Frequency
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.ChoreRepository
import com.familyhome.app.domain.usecase.chore.AddRecurringTaskUseCase
import com.familyhome.app.domain.usecase.chore.AssignChoreUseCase
import com.familyhome.app.domain.usecase.chore.CompleteRecurringTaskUseCase
import com.familyhome.app.domain.usecase.chore.DeleteChoreLogUseCase
import com.familyhome.app.domain.usecase.chore.DeleteRecurringTaskUseCase
import com.familyhome.app.domain.usecase.chore.GetChoreAssignmentsUseCase
import com.familyhome.app.domain.usecase.chore.GetChoreHistoryUseCase
import com.familyhome.app.domain.usecase.chore.GetRecurringTasksUseCase
import com.familyhome.app.domain.usecase.chore.LogChoreUseCase
import com.familyhome.app.domain.usecase.chore.RespondToChoreAssignmentUseCase
import com.familyhome.app.domain.usecase.chore.UpdateRecurringTaskUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
private fun user(id: String = "u1", role: Role = Role.FATHER) = User(
    id = id, name = "Test", role = role, parentId = null,
    avatarUri = null, createdAt = 1L,
)

private fun task(
    id: String = "t1",
    assignedTo: String? = "u1",
    scheduledAt: Long? = null,
    reminderMinutesBefore: Int? = null,
) = RecurringTask(
    id = id, taskName = "Wash dishes", frequency = Frequency.DAILY,
    assignedTo = assignedTo, lastDoneAt = null,
    nextDueAt = System.currentTimeMillis() + 86_400_000L,
    scheduledAt = scheduledAt, reminderMinutesBefore = reminderMinutesBefore,
)

private fun assignment(
    id: String = "a1",
    taskId: String = "t1",
    assignedTo: String = "k1",
    assignedBy: String = "f1",
    status: AssignmentStatus = AssignmentStatus.PENDING,
) = ChoreAssignment(
    id = id, taskId = taskId, taskName = "Wash dishes",
    assignedTo = assignedTo, assignedBy = assignedBy,
    status = status, declineReason = null,
    assignedAt = System.currentTimeMillis(), respondedAt = null,
)

class ChoreUseCasesTest {

    private val choreRepo      = mockk<ChoreRepository>(relaxed = true)
    private val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)

    @Nested
    inner class LogChoreUseCaseTest {

        private val useCase = LogChoreUseCase(choreRepo)

        @Test
        fun `FATHER can log chore for any user`() = runTest {
            val father = user("f1", Role.FATHER)
            coEvery { choreRepo.logChore(any()) } returns Unit

            val result = useCase(father, "Vacuum", "k1", null)

            assertTrue(result.isSuccess)
            assertEquals("Vacuum", result.getOrThrow().taskName)
            assertEquals("k1", result.getOrThrow().doneBy)
        }

        @Test
        fun `WIFE can log chore for any user`() = runTest {
            val wife = user("w1", Role.WIFE)
            coEvery { choreRepo.logChore(any()) } returns Unit

            val result = useCase(wife, "Cook", "k1", null)

            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID can only log chore for themselves`() = runTest {
            val kid = user("k1", Role.KID)
            coEvery { choreRepo.logChore(any()) } returns Unit

            val result = useCase(kid, "Tidy room", "k1", "Tidied up")

            assertTrue(result.isSuccess)
        }

        @Test
        fun `KID cannot log chore for another user`() = runTest {
            val kid = user("k1", Role.KID)

            val result = useCase(kid, "Tidy room", "k2", null)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("only log chores for yourself"))
        }
    }

    @Nested
    inner class GetChoreHistoryUseCaseTest {

        private val useCase = GetChoreHistoryUseCase(choreRepo)

        @Test
        fun `FATHER gets all chore history`() {
            val father = user("f1", Role.FATHER)

            useCase(father, 7)

            coVerify { choreRepo.getChoreHistory(any()) }
        }

        @Test
        fun `WIFE gets all chore history`() {
            val wife = user("w1", Role.WIFE)

            useCase(wife, 7)

            coVerify { choreRepo.getChoreHistory(any()) }
        }

        @Test
        fun `KID gets only their own history`() {
            val kid = user("k1", Role.KID)

            useCase(kid, 7)

            coVerify { choreRepo.getChoreHistoryByUser("k1", any()) }
        }
    }

    @Nested
    inner class GetRecurringTasksUseCaseTest {

        private val useCase = GetRecurringTasksUseCase(choreRepo)

        @Test
        fun `delegates to repository getRecurringTasks`() {
            useCase()
            coVerify { choreRepo.getRecurringTasks() }
        }
    }

    @Nested
    inner class AddRecurringTaskUseCaseTest {

        private val useCase = AddRecurringTaskUseCase(choreRepo)

        @Test
        fun `FATHER can add recurring task`() = runTest {
            val father = user("f1", Role.FATHER)
            coEvery { choreRepo.insertRecurringTask(any()) } returns Unit

            val result = useCase(father, "Sweep floor", Frequency.DAILY, "k1")

            assertTrue(result.isSuccess)
            assertEquals("Sweep floor", result.getOrThrow().taskName)
            assertEquals(Frequency.DAILY, result.getOrThrow().frequency)
        }

        @Test
        fun `WIFE can add recurring task`() = runTest {
            val wife = user("w1", Role.WIFE)
            coEvery { choreRepo.insertRecurringTask(any()) } returns Unit

            assertTrue(useCase(wife, "Cook dinner", Frequency.WEEKLY, null).isSuccess)
        }

        @Test
        fun `KID cannot add recurring task`() = runTest {
            val kid = user("k1", Role.KID)

            val result = useCase(kid, "Tidy room", Frequency.DAILY, "k1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("permission"))
        }

        @Test
        fun `custom scheduledAt is used as nextDueAt when provided`() = runTest {
            val father = user("f1", Role.FATHER)
            val at = System.currentTimeMillis() + 3_600_000L
            coEvery { choreRepo.insertRecurringTask(any()) } returns Unit

            val result = useCase(father, "Task", Frequency.CUSTOM, null, at)

            assertEquals(at, result.getOrThrow().nextDueAt)
            assertEquals(at, result.getOrThrow().scheduledAt)
        }
    }

    @Nested
    inner class AssignChoreUseCaseTest {

        private val useCase = AssignChoreUseCase(choreRepo)

        @Test
        fun `FATHER can assign chore to kid`() = runTest {
            val father = user("f1", Role.FATHER)
            val t = task("t1", null)
            coEvery { choreRepo.insertAssignment(any()) } returns Unit
            coEvery { choreRepo.updateRecurringTask(any()) } returns Unit

            val result = useCase(father, t, "k1")

            assertTrue(result.isSuccess)
            assertEquals(AssignmentStatus.PENDING, result.getOrThrow().status)
            assertEquals("k1", result.getOrThrow().assignedTo)
            assertEquals("f1", result.getOrThrow().assignedBy)
        }

        @Test
        fun `KID cannot assign chores`() = runTest {
            val kid = user("k1", Role.KID)

            val result = useCase(kid, task(), "k2")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("Only parents"))
        }
    }

    @Nested
    inner class RespondToChoreAssignmentUseCaseTest {

        private val useCase = RespondToChoreAssignmentUseCase(choreRepo)

        @Test
        fun `assignee can accept assignment`() = runTest {
            val kid = user("k1", Role.KID)
            val a = assignment(assignedTo = "k1")
            coEvery { choreRepo.updateAssignment(any()) } returns Unit

            val result = useCase.accept(kid, a)

            assertTrue(result.isSuccess)
            coVerify { choreRepo.updateAssignment(match { it.status == AssignmentStatus.ACCEPTED }) }
        }

        @Test
        fun `non-assignee cannot accept assignment`() = runTest {
            val other = user("k2", Role.KID)
            val a = assignment(assignedTo = "k1")

            val result = useCase.accept(other, a)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("only respond to your own"))
        }

        @Test
        fun `assignee can decline assignment with reason`() = runTest {
            val kid = user("k1", Role.KID)
            val a = assignment(assignedTo = "k1")
            coEvery { choreRepo.updateAssignment(any()) } returns Unit

            val result = useCase.decline(kid, a, "I am sick")

            assertTrue(result.isSuccess)
            coVerify { choreRepo.updateAssignment(match { it.status == AssignmentStatus.DECLINED && it.declineReason == "I am sick" }) }
        }

        @Test
        fun `blank decline reason falls back to default text`() = runTest {
            val kid = user("k1", Role.KID)
            val a = assignment(assignedTo = "k1")
            coEvery { choreRepo.updateAssignment(any()) } returns Unit

            useCase.decline(kid, a, "  ")

            coVerify { choreRepo.updateAssignment(match { it.declineReason == "No reason given" }) }
        }

        @Test
        fun `non-assignee cannot decline assignment`() = runTest {
            val other = user("k2", Role.KID)
            val a = assignment(assignedTo = "k1")

            assertTrue(useCase.decline(other, a, "reason").isFailure)
        }
    }

    @Nested
    inner class GetChoreAssignmentsUseCaseTest {

        private val useCase = GetChoreAssignmentsUseCase(choreRepo)

        @Test
        fun `forUser delegates to repository`() {
            useCase.forUser("k1")
            coVerify { choreRepo.getAssignmentsForUser("k1") }
        }

        @Test
        fun `pendingForUser delegates to repository`() {
            useCase.pendingForUser("k1")
            coVerify { choreRepo.getPendingAssignmentsForUser("k1") }
        }

        @Test
        fun `all delegates to repository getAllAssignments`() {
            useCase.all()
            coVerify { choreRepo.getAllAssignments() }
        }
    }

    @Nested
    inner class CompleteRecurringTaskUseCaseTest {

        private val useCase = CompleteRecurringTaskUseCase(choreRepo)

        @Test
        fun `logs chore entry and deletes task`() = runTest {
            val father = user("f1", Role.FATHER)
            val t = task("t1")
            coEvery { choreRepo.logChore(any()) } returns Unit
            coEvery { choreRepo.deleteRecurringTask(t.id) } returns Unit

            val result = useCase(father, t)

            assertTrue(result.isSuccess)
            coVerify { choreRepo.logChore(match { it.taskName == t.taskName && it.doneBy == father.id }) }
            coVerify { choreRepo.deleteRecurringTask(t.id) }
        }
    }

    @Nested
    inner class DeleteChoreLogUseCaseTest {

        private val useCase = DeleteChoreLogUseCase(choreRepo)

        @Test
        fun `FATHER can delete chore log`() = runTest {
            val father = user("f1", Role.FATHER)
            coEvery { choreRepo.deleteChoreLog("log1") } returns Unit

            val result = useCase(father, "log1")

            assertTrue(result.isSuccess)
            coVerify { choreRepo.deleteChoreLog("log1") }
        }

        @Test
        fun `WIFE can delete chore log`() = runTest {
            val wife = user("w1", Role.WIFE)

            assertTrue(useCase(wife, "log1").isSuccess)
        }

        @Test
        fun `KID cannot delete chore log`() = runTest {
            val kid = user("k1", Role.KID)

            val result = useCase(kid, "log1")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("Only parents"))
        }
    }

    @Nested
    inner class UpdateRecurringTaskUseCaseTest {

        private val useCase = UpdateRecurringTaskUseCase(choreRepo, alarmScheduler)

        @Test
        fun `FATHER can update task`() = runTest {
            val father = user("f1", Role.FATHER)
            val t = task("t1")
            coEvery { choreRepo.updateRecurringTask(t) } returns Unit

            val result = useCase(father, t)

            assertTrue(result.isSuccess)
            coVerify { choreRepo.updateRecurringTask(t) }
        }

        @Test
        fun `schedules alarm when both scheduledAt and reminderMinutesBefore are set`() = runTest {
            val father = user("f1", Role.FATHER)
            val t = task("t1", scheduledAt = System.currentTimeMillis() + 3600_000L, reminderMinutesBefore = 15)
            coEvery { choreRepo.updateRecurringTask(t) } returns Unit
            coEvery { alarmScheduler.schedule(t) } returns Unit

            useCase(father, t)

            coVerify { alarmScheduler.schedule(t) }
        }

        @Test
        fun `cancels alarm when scheduledAt is null`() = runTest {
            val father = user("f1", Role.FATHER)
            val t = task("t1", scheduledAt = null, reminderMinutesBefore = null)
            coEvery { choreRepo.updateRecurringTask(t) } returns Unit

            useCase(father, t)

            coVerify { alarmScheduler.cancel(t.id) }
        }

        @Test
        fun `KID cannot update task`() = runTest {
            val kid = user("k1", Role.KID)

            val result = useCase(kid, task())

            assertTrue(result.isFailure)
        }
    }

    @Nested
    inner class DeleteRecurringTaskUseCaseTest {

        private val useCase = DeleteRecurringTaskUseCase(choreRepo, alarmScheduler)

        @Test
        fun `FATHER can delete task and alarm is cancelled`() = runTest {
            val father = user("f1", Role.FATHER)
            val t = task("t1")
            coEvery { choreRepo.deleteRecurringTask(t.id) } returns Unit
            coEvery { alarmScheduler.cancel(t.id) } returns Unit

            val result = useCase(father, t)

            assertTrue(result.isSuccess)
            coVerify { alarmScheduler.cancel(t.id) }
            coVerify { choreRepo.deleteRecurringTask(t.id) }
        }

        @Test
        fun `KID cannot delete task`() = runTest {
            val kid = user("k1", Role.KID)

            assertTrue(useCase(kid, task()).isFailure)
        }
    }
}
