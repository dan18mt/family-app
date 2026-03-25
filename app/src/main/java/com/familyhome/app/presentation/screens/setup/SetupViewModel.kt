package com.familyhome.app.presentation.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.usecase.user.CreateUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { CHOOSE, SETUP_FATHER }

data class SetupUiState(
    val step: SetupStep        = SetupStep.CHOOSE,
    val name: String           = "",
    val pin: String            = "",
    val confirmPin: String     = "",
    val isLoading: Boolean     = false,
    val error: String?         = null,
    val isDone: Boolean        = false,
    val navigateToJoin: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val createUserUseCase: CreateUserUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state = _state.asStateFlow()

    fun onCreateFamilyChosen()    = _state.update { it.copy(step = SetupStep.SETUP_FATHER) }
    fun onJoinFamilyChosen()      = _state.update { it.copy(navigateToJoin = true) }
    fun onJoinNavigated()         = _state.update { it.copy(navigateToJoin = false) }

    fun onNameChange(value: String)       = _state.update { it.copy(name = value, error = null) }
    fun onPinChange(value: String)        = _state.update { it.copy(pin = value, error = null) }
    fun onConfirmPinChange(value: String) = _state.update { it.copy(confirmPin = value, error = null) }

    fun createFather() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Name cannot be empty.") }
            return
        }
        if (s.pin.length != 4) {
            _state.update { it.copy(error = "PIN must be 4 digits.") }
            return
        }
        if (s.pin != s.confirmPin) {
            _state.update { it.copy(error = "PINs do not match.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = createUserUseCase(
                name      = s.name,
                role      = Role.FATHER,
                pin       = s.pin,
                avatarUri = null,
                parentId  = null,
                actor     = null,
            )
            _state.update { state ->
                state.copy(
                    isLoading = false,
                    isDone    = result.isSuccess,
                    error     = result.exceptionOrNull()?.message,
                )
            }
        }
    }
}
