package com.familyhome.app.presentation.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.data.sync.SyncRepositoryImpl
import com.familyhome.app.domain.model.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isConnected: Boolean = false,
    val isSyncing: Boolean   = false,
    val lastSyncAt: Long?    = null,
    val syncError: String?   = null,
    val hostIp: String?      = null,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepositoryImpl,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state = _state.asStateFlow()

    init {
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
                    is SyncResult.Success -> s.copy(isSyncing = false, lastSyncAt = result.syncedAt, isConnected = true)
                    is SyncResult.Error   -> s.copy(isSyncing = false, syncError = result.message, isConnected = false)
                }
            }
        }
    }
}
