package com.familyhome.app.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state      by viewModel.state.collectAsStateWithLifecycle()
    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Assistant") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Messages
            LazyColumn(
                state   = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
                        }
                    }
                }
            }

            HorizontalDivider()

            // Input row
            Row(
                modifier          = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value         = state.inputText,
                    onValueChange = viewModel::onInputChange,
                    placeholder   = { Text("Ask me anything…") },
                    modifier      = Modifier.weight(1f),
                    maxLines      = 4,
                    enabled       = !state.isLoading,
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick  = viewModel::sendMessage,
                    enabled  = state.inputText.isNotBlank() && !state.isLoading,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.isFromUser
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = when {
                        message.isError -> MaterialTheme.colorScheme.errorContainer
                        isUser          -> MaterialTheme.colorScheme.primaryContainer
                        else            -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(
                        topStart    = if (isUser) 16.dp else 4.dp,
                        topEnd      = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd   = 16.dp,
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text  = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    message.isError -> MaterialTheme.colorScheme.onErrorContainer
                    isUser          -> MaterialTheme.colorScheme.onPrimaryContainer
                    else            -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
