package com.familyhome.app.data.sync

import com.familyhome.app.domain.model.PrayerReminderDto
import com.familyhome.app.domain.repository.SessionRepository
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight HTTP server that runs on **member** devices (port [PORT]).
 *
 * Allows the family leader to push notifications directly to a member over the
 * local Wi-Fi without waiting for the next scheduled sync cycle.
 *
 * Routes:
 *  - `GET  /ping`   → "pong"
 *  - `POST /notify` → receives a [PrayerReminderDto], shows OS notification immediately
 */
@Singleton
class MemberNotifyServer @Inject constructor(
    private val prayerReminderStore: PrayerReminderStore,
    private val sessionRepository: SessionRepository,
) {
    companion object {
        const val PORT = 8766
    }

    private var server: ApplicationEngine? = null
    val isRunning: Boolean get() = server != null

    fun start() {
        if (isRunning) return
        server = embeddedServer(CIO, port = PORT) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                get("/ping") {
                    call.respond("pong")
                }
                post("/notify") {
                    val reminder = call.receive<PrayerReminderDto>()
                    val userId = sessionRepository.getCurrentUserId()
                    if (userId != null && reminder.targetUserId == userId) {
                        prayerReminderStore.mergeReminders(listOf(reminder))
                        prayerReminderStore.processForCurrentUser(userId)
                    }
                    call.respond(mapOf("status" to "ok"))
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
    }
}
