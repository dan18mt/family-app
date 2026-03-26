package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.ChoreAssignmentEntity
import com.familyhome.app.data.local.entity.ChoreLogEntity
import com.familyhome.app.data.local.entity.RecurringTaskEntity
import com.familyhome.app.domain.model.*

fun ChoreLogEntity.toDomain() = ChoreLog(
    id       = id,
    taskName = taskName,
    doneBy   = doneBy,
    doneAt   = doneAt,
    note     = note,
)

fun ChoreLog.toEntity() = ChoreLogEntity(
    id       = id,
    taskName = taskName,
    doneBy   = doneBy,
    doneAt   = doneAt,
    note     = note,
)

fun ChoreLog.toDto() = ChoreLogDto(
    id       = id,
    taskName = taskName,
    doneBy   = doneBy,
    doneAt   = doneAt,
    note     = note,
)

fun ChoreLogDto.toDomain() = ChoreLog(
    id       = id,
    taskName = taskName,
    doneBy   = doneBy,
    doneAt   = doneAt,
    note     = note,
)

fun RecurringTaskEntity.toDomain() = RecurringTask(
    id                    = id,
    taskName              = taskName,
    frequency             = Frequency.valueOf(frequency),
    assignedTo            = assignedTo,
    lastDoneAt            = lastDoneAt,
    nextDueAt             = nextDueAt,
    scheduledAt           = scheduledAt,
    reminderMinutesBefore = reminderMinutesBefore,
)

fun RecurringTask.toEntity() = RecurringTaskEntity(
    id                    = id,
    taskName              = taskName,
    frequency             = frequency.name,
    assignedTo            = assignedTo,
    lastDoneAt            = lastDoneAt,
    nextDueAt             = nextDueAt,
    scheduledAt           = scheduledAt,
    reminderMinutesBefore = reminderMinutesBefore,
)

fun RecurringTask.toDto() = RecurringTaskDto(
    id                    = id,
    taskName              = taskName,
    frequency             = frequency.name,
    assignedTo            = assignedTo,
    lastDoneAt            = lastDoneAt,
    nextDueAt             = nextDueAt,
    scheduledAt           = scheduledAt,
    reminderMinutesBefore = reminderMinutesBefore,
)

fun RecurringTaskDto.toDomain() = RecurringTask(
    id                    = id,
    taskName              = taskName,
    frequency             = Frequency.valueOf(frequency),
    assignedTo            = assignedTo,
    lastDoneAt            = lastDoneAt,
    nextDueAt             = nextDueAt,
    scheduledAt           = scheduledAt,
    reminderMinutesBefore = reminderMinutesBefore,
)

fun ChoreAssignmentEntity.toDomain() = ChoreAssignment(
    id            = id,
    taskId        = taskId,
    taskName      = taskName,
    assignedTo    = assignedTo,
    assignedBy    = assignedBy,
    status        = AssignmentStatus.valueOf(status),
    declineReason = declineReason,
    assignedAt    = assignedAt,
    respondedAt   = respondedAt,
)

fun ChoreAssignment.toEntity() = ChoreAssignmentEntity(
    id            = id,
    taskId        = taskId,
    taskName      = taskName,
    assignedTo    = assignedTo,
    assignedBy    = assignedBy,
    status        = status.name,
    declineReason = declineReason,
    assignedAt    = assignedAt,
    respondedAt   = respondedAt,
)

fun ChoreAssignment.toAssignmentDto() = ChoreAssignmentDto(
    id            = id,
    taskId        = taskId,
    taskName      = taskName,
    assignedTo    = assignedTo,
    assignedBy    = assignedBy,
    status        = status.name,
    declineReason = declineReason,
    assignedAt    = assignedAt,
    respondedAt   = respondedAt,
)

fun ChoreAssignmentDto.toAssignmentDomain() = ChoreAssignment(
    id            = id,
    taskId        = taskId,
    taskName      = taskName,
    assignedTo    = assignedTo,
    assignedBy    = assignedBy,
    status        = AssignmentStatus.valueOf(status),
    declineReason = declineReason,
    assignedAt    = assignedAt,
    respondedAt   = respondedAt,
)
