package com.familyhome.app.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.repository.UserRepository
import com.familyhome.app.domain.usecase.user.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean  = false,
    val isSuccess: Boolean  = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    userRepository: UserRepository,
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    val users = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun loginAs(user: User) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val success = loginUseCase(user.id)
            _state.update { it.copy(isLoading = false, isSuccess = success) }
        }
    }
}
