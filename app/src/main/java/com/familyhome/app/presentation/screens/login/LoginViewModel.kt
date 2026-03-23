package com.familyhome.app.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.UserRepository
import com.familyhome.app.domain.usecase.user.ValidatePinUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val selectedUser: User? = null,
    val pin: String         = "",
    val isLoading: Boolean  = false,
    val error: String?      = null,
    val isSuccess: Boolean  = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    userRepository: UserRepository,
    private val validatePinUseCase: ValidatePinUseCase,
) : ViewModel() {

    val users = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun selectUser(user: User) = _state.update { it.copy(selectedUser = user, pin = "", error = null) }

    fun onPinDigit(digit: String) {
        val current = _state.value.pin
        if (current.length < 4) {
            _state.update { it.copy(pin = current + digit, error = null) }
        }
    }

    fun onPinBackspace() {
        val current = _state.value.pin
        if (current.isNotEmpty()) {
            _state.update { it.copy(pin = current.dropLast(1), error = null) }
        }
    }

    fun submitPin() {
        val s = _state.value
        val user = s.selectedUser ?: return
        if (s.pin.length != 4) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val valid = validatePinUseCase(user.id, s.pin)
            _state.update {
                it.copy(
                    isLoading = false,
                    isSuccess = valid,
                    error     = if (!valid) "Incorrect PIN. Try again." else null,
                    pin       = if (!valid) "" else it.pin,
                )
            }
        }
    }
}
