package com.familyhome.app.data.sync

import com.familyhome.app.domain.model.PrayerReminderDto
import com.familyhome.app.domain.model.SyncPayload
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import javax.inject.Inject

/**
 * HTTP client used by non-host devices to communicate with [SyncServer].
 */
class SyncClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    suspend fun ping(hostIp: String, port: Int): Boolean = runCatching {
        val response = httpClient.get("http://$hostIp:$port/ping")
        response.status.isSuccess()
    }.getOrDefault(false)

    suspend fun pull(hostIp: String, port: Int): SyncPayload {
        return httpClient.get("http://$hostIp:$port/sync/pull").body()
    }

    suspend fun push(hostIp: String, port: Int, payload: SyncPayload) {
        httpClient.post("http://$hostIp:$port/sync/push") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    /**
     * Push a single [PrayerReminderDto] directly to a device's `/notify` endpoint.
     * Works for both the leader's server (port 8765) and member servers (port 8766).
     * Returns `true` on HTTP 2xx, `false` on any error.
     */
    suspend fun pushNotification(targetIp: String, port: Int, reminder: PrayerReminderDto): Boolean =
        runCatching {
            httpClient.post("http://$targetIp:$port/notify") {
                contentType(ContentType.Application.Json)
                setBody(reminder)
            }.status.isSuccess()
        }.getOrDefault(false)
}
