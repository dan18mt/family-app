package com.familyhome.app.domain.model

data class ChoreLog(
    val id: String,
    val taskName: String,
    val doneBy: String,
    val doneAt: Long,
    val note: String?,
)
