package com.familyhome.app.data.sync

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
}
