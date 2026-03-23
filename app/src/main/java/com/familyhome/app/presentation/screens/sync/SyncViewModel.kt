package com.familyhome.app.presentation.screens.sync

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.BuildConfig
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.model.SyncResult
import com.familyhome.app.domain.permission.PermissionManager
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

data class SyncUiState(
    val isHostRunning: Boolean = false,
    val isConnected: Boolean   = false,
    val isSyncing: Boolean     = false,
    val canHostSync: Boolean   = false,
    val lastSyncAt: Long?      = null,
    val syncError: String?     = null,
    val hostIp: String?        = null,
    val localIp: String?       = null,
    val serverPort: Int        = BuildConfig.SYNC_SERVER_PORT,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepositoryImpl,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            val canHost = user != null && PermissionManager.canHostSync(user)
            _state.update { it.copy(canHostSync = canHost, localIp = getLocalIp()) }
        }

        viewModelScope.launch {
            syncRepository.getHostIpFlow().collect { ip ->
                _state.update { it.copy(hostIp = ip) }
            }
        }

        viewModelScope.launch {
            syncRepository.getLastSyncTimeFlow().collect { ts ->
                _state.update { it.copy(lastSyncAt = ts) }
            }
        }
    }

    fun startHostServer() {
        syncRepository.startHostServer()
        _state.update { it.copy(isHostRunning = true) }
    }

    fun stopHostServer() {
        syncRepository.stopHostServer()
        _state.update { it.copy(isHostRunning = false) }
    }

    fun saveHostIp(ip: String) {
        viewModelScope.launch {
            syncRepository.saveHostIp(ip)
            val connected = syncRepository.ping()
            _state.update { it.copy(isConnected = connected) }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, syncError = null) }
            val result = syncRepository.syncWithHost()
            _state.update { s ->
                when (result) {
                    is SyncResult.Success -> s.copy(
                        isSyncing  = false,
                        lastSyncAt = result.syncedAt,
                        isConnected = true,
                    )
                    is SyncResult.Error -> s.copy(
                        isSyncing  = false,
                        syncError  = result.message,
                        isConnected = false,
                    )
                }
            }
        }
    }

    private fun getLocalIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    }.getOrNull()
}
