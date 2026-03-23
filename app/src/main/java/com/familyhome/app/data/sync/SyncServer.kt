package com.familyhome.app.data.sync

import com.familyhome.app.data.mapper.*
import com.familyhome.app.domain.model.SyncPayload
import com.familyhome.app.domain.repository.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Embedded Ktor HTTP server that runs on the host device (Father's phone).
 * Other devices pull/push data via [SyncClient].
 *
 * To swap in Firebase later: delete this class and [SyncClient], replace
 * [SyncRepositoryImpl] with a Firestore-backed implementation — domain layer unchanged.
 */
class SyncServer @Inject constructor(
    private val userRepository: UserRepository,
    private val stockRepository: StockRepository,
    private val choreRepository: ChoreRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
) {
    private var server: ApplicationEngine? = null

    val isRunning: Boolean get() = server != null

    fun start(port: Int) {
        if (isRunning) return
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                get("/ping") { call.respond("pong") }
                get("/sync/pull") { handlePull(call) }
                post("/sync/push") { handlePush(call) }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
        server = null
    }

    private suspend fun handlePull(call: ApplicationCall) {
        val payload = SyncPayload(
            users          = userRepository.getAllUsers().first().map { it.toDto() },
            stockItems     = stockRepository.getAllItems().first().map { it.toDto() },
            choreLogs      = choreRepository.getChoreHistory(0L).first().map { it.toDto() },
            recurringTasks = choreRepository.getRecurringTasks().first().map { it.toDto() },
            expenses       = expenseRepository.getAllExpenses().first().map { it.toDto() },
            budgets        = budgetRepository.getAllBudgets().first().map { it.toDto() },
        )
        call.respond(payload)
    }

    private suspend fun handlePush(call: ApplicationCall) {
        val payload = call.receive<SyncPayload>()
        mergePayload(payload)
        call.respond(mapOf("status" to "ok"))
    }

    /** Last-write-wins merge: upsert everything received from the client. */
    private suspend fun mergePayload(payload: SyncPayload) {
        payload.users?.let          { userRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.stockItems?.let     { stockRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.choreLogs?.let      { choreRepository.upsertAllLogs(it.map { dto -> dto.toDomain() }) }
        payload.recurringTasks?.let { choreRepository.upsertAllRecurring(it.map { dto -> dto.toDomain() }) }
        payload.expenses?.let       { expenseRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
        payload.budgets?.let        { budgetRepository.upsertAll(it.map { dto -> dto.toDomain() }) }
    }
}
