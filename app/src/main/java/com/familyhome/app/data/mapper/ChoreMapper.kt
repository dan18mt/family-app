package com.familyhome.app.data.mapper

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
    id         = id,
    taskName   = taskName,
    frequency  = Frequency.valueOf(frequency),
    assignedTo = assignedTo,
    lastDoneAt = lastDoneAt,
    nextDueAt  = nextDueAt,
)

fun RecurringTask.toEntity() = RecurringTaskEntity(
    id         = id,
    taskName   = taskName,
    frequency  = frequency.name,
    assignedTo = assignedTo,
    lastDoneAt = lastDoneAt,
    nextDueAt  = nextDueAt,
)

fun RecurringTask.toDto() = RecurringTaskDto(
    id         = id,
    taskName   = taskName,
    frequency  = frequency.name,
    assignedTo = assignedTo,
    lastDoneAt = lastDoneAt,
    nextDueAt  = nextDueAt,
)

fun RecurringTaskDto.toDomain() = RecurringTask(
    id         = id,
    taskName   = taskName,
    frequency  = Frequency.valueOf(frequency),
    assignedTo = assignedTo,
    lastDoneAt = lastDoneAt,
    nextDueAt  = nextDueAt,
)
