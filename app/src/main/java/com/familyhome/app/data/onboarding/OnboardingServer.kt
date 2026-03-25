package com.familyhome.app.data.onboarding

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small Ktor server that runs on **member** devices during onboarding (port 8766).
 *
 * Routes:
 *  GET  /info   — returns [DeviceInfoDto] so Father can see the device name.
 *  POST /invite — Father sends an [InviteDto]; triggers UI via [receivedInvite].
 */
@Singleton
class OnboardingServer @Inject constructor() {

    companion object {
        const val PORT = 8766
    }

    private var server: ApplicationEngine? = null
    val isRunning: Boolean get() = server != null

    private val _receivedInvite = MutableStateFlow<InviteDto?>(null)
    val receivedInvite: StateFlow<InviteDto?> = _receivedInvite.asStateFlow()

    fun start(deviceName: String, deviceId: String) {
        if (isRunning) return
        server = embeddedServer(CIO, port = PORT) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                get("/info") {
                    call.respond(DeviceInfoDto(deviceName = deviceName, deviceId = deviceId))
                }
                post("/invite") {
                    val invite = call.receive<InviteDto>()
                    _receivedInvite.value = invite
                    call.respond(mapOf("status" to "received"))
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
    }

    fun clearInvite() {
        _receivedInvite.value = null
    }
}
