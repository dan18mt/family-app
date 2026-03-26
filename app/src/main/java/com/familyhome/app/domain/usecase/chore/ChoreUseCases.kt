package com.familyhome.app.domain.usecase.chore

import com.familyhome.app.domain.model.AssignmentStatus
import com.familyhome.app.domain.model.ChoreAssignment
import com.familyhome.app.domain.model.ChoreLog
import com.familyhome.app.domain.model.Frequency
import com.familyhome.app.domain.model.RecurringTask
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.permission.PermissionManager
import com.familyhome.app.domain.repository.ChoreRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LogChoreUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(
        actor: User,
        taskName: String,
        doneByUserId: String,
        note: String?,
    ): Result<ChoreLog> {
        if (!PermissionManager.canLogChoreForUser(actor, doneByUserId)) {
            return Result.failure(IllegalStateException("You can only log chores for yourself."))
        }
        val log = ChoreLog(
            id       = UUID.randomUUID().toString(),
            taskName = taskName,
            doneBy   = doneByUserId,
            doneAt   = System.currentTimeMillis(),
            note     = note,
        )
        choreRepository.logChore(log)
        return Result.success(log)
    }
}

class GetChoreHistoryUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    operator fun invoke(actor: User, days: Int): Flow<List<ChoreLog>> {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return when {
            actor.role.name == "FATHER" || actor.role.name == "WIFE" ->
                choreRepository.getChoreHistory(since)
            else -> choreRepository.getChoreHistoryByUser(actor.id, since)
        }
    }
}

class GetRecurringTasksUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    operator fun invoke(): Flow<List<RecurringTask>> = choreRepository.getRecurringTasks()
}

class AddRecurringTaskUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(
        actor: User,
        taskName: String,
        frequency: Frequency,
        assignedTo: String?,
        scheduledAt: Long? = null,
        reminderMinutesBefore: Int? = null,
    ): Result<RecurringTask> {
        if (!PermissionManager.canCreateRecurringTask(actor)) {
            return Result.failure(IllegalStateException("You don't have permission to create recurring tasks."))
        }
        val task = RecurringTask(
            id                    = UUID.randomUUID().toString(),
            taskName              = taskName,
            frequency             = frequency,
            assignedTo            = assignedTo,
            lastDoneAt            = null,
            nextDueAt             = scheduledAt ?: firstDueTimestamp(frequency),
            scheduledAt           = scheduledAt,
            reminderMinutesBefore = reminderMinutesBefore,
        )
        choreRepository.insertRecurringTask(task)
        return Result.success(task)
    }

    private fun firstDueTimestamp(frequency: Frequency): Long {
        val now = System.currentTimeMillis()
        return when (frequency) {
            Frequency.DAILY  -> now + TimeUnit.DAYS.toMillis(1)
            Frequency.WEEKLY -> now + TimeUnit.DAYS.toMillis(7)
            Frequency.CUSTOM -> now + TimeUnit.DAYS.toMillis(1)
        }
    }
}

class AssignChoreUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(
        actor: User,
        task: RecurringTask,
        assignToUserId: String,
    ): Result<ChoreAssignment> {
        if (!PermissionManager.canAssignChore(actor)) {
            return Result.failure(IllegalStateException("Only parents can assign chores."))
        }
        val assignment = ChoreAssignment(
            id            = UUID.randomUUID().toString(),
            taskId        = task.id,
            taskName      = task.taskName,
            assignedTo    = assignToUserId,
            assignedBy    = actor.id,
            status        = AssignmentStatus.PENDING,
            declineReason = null,
            assignedAt    = System.currentTimeMillis(),
            respondedAt   = null,
        )
        choreRepository.insertAssignment(assignment)
        // Also update the task's assignedTo field
        choreRepository.updateRecurringTask(task.copy(assignedTo = assignToUserId))
        return Result.success(assignment)
    }
}

class RespondToChoreAssignmentUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend fun accept(actor: User, assignment: ChoreAssignment): Result<Unit> {
        if (actor.id != assignment.assignedTo) {
            return Result.failure(IllegalStateException("You can only respond to your own assignments."))
        }
        choreRepository.updateAssignment(
            assignment.copy(
                status      = AssignmentStatus.ACCEPTED,
                respondedAt = System.currentTimeMillis(),
            )
        )
        return Result.success(Unit)
    }

    suspend fun decline(actor: User, assignment: ChoreAssignment, reason: String): Result<Unit> {
        if (actor.id != assignment.assignedTo) {
            return Result.failure(IllegalStateException("You can only respond to your own assignments."))
        }
        choreRepository.updateAssignment(
            assignment.copy(
                status        = AssignmentStatus.DECLINED,
                declineReason = reason.ifBlank { "No reason given" },
                respondedAt   = System.currentTimeMillis(),
            )
        )
        return Result.success(Unit)
    }
}

class GetChoreAssignmentsUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    fun forUser(userId: String): Flow<List<ChoreAssignment>> =
        choreRepository.getAssignmentsForUser(userId)

    fun pendingForUser(userId: String): Flow<List<ChoreAssignment>> =
        choreRepository.getPendingAssignmentsForUser(userId)

    fun all(): Flow<List<ChoreAssignment>> =
        choreRepository.getAllAssignments()
}

class CompleteRecurringTaskUseCase @Inject constructor(
    private val choreRepository: ChoreRepository,
) {
    suspend operator fun invoke(actor: User, task: RecurringTask): Result<Unit> {
        val now = System.currentTimeMillis()
        val nextDue = when (task.frequency) {
            Frequency.DAILY  -> now + TimeUnit.DAYS.toMillis(1)
            Frequency.WEEKLY -> now + TimeUnit.DAYS.toMillis(7)
            Frequency.CUSTOM -> now + TimeUnit.DAYS.toMillis(1)
        }
        choreRepository.updateRecurringTask(
            task.copy(lastDoneAt = now, nextDueAt = nextDue)
        )
        // Also write a ChoreLog entry
        choreRepository.logChore(
            ChoreLog(
                id       = UUID.randomUUID().toString(),
                taskName = task.taskName,
                doneBy   = actor.id,
                doneAt   = now,
                note     = "Recurring task completed",
            )
        )
        return Result.success(Unit)
    }
}
