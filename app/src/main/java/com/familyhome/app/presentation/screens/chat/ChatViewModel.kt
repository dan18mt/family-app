package com.familyhome.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyhome.app.agent.FamilyAgent
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.usecase.user.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: String        = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val isError: Boolean  = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String           = "",
    val isLoading: Boolean          = false,
    val currentUser: User?          = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val familyAgent: FamilyAgent,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = getCurrentUserUseCase()
            _state.update { it.copy(currentUser = user) }
            if (user != null) {
                _state.update { s ->
                    s.copy(
                        messages = s.messages + ChatMessage(
                            content     = "Hi ${user.name}! I'm your family assistant. You can ask me to log chores, update pantry stock, record expenses, or get a summary.",
                            isFromUser  = false,
                        )
                    )
                }
            }
        }
    }

    fun onInputChange(text: String) = _state.update { it.copy(inputText = text) }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return
        val user = _state.value.currentUser ?: return

        _state.update { s ->
            s.copy(
                messages  = s.messages + ChatMessage(content = text, isFromUser = true),
                inputText = "",
                isLoading = true,
            )
        }

        viewModelScope.launch {
            val response = familyAgent.chat(text, user)
            _state.update { s ->
                s.copy(
                    messages  = s.messages + ChatMessage(
                        content    = response.reply,
                        isFromUser = false,
                        isError    = response.isError,
                    ),
                    isLoading = false,
                )
            }
        }
    }
}
