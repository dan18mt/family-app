package com.familyhome.app.presentation.screens.login

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.AvatarInitials
import com.familyhome.app.presentation.components.LoadingScreen
import com.familyhome.app.presentation.components.PinDots

@Composable
fun LoginScreen(
    users: List<User>,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onLoginSuccess()
    }

    if (state.isLoading) { LoadingScreen(); return }

    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Who's here?", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // User selection
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 16.dp),
        ) {
            items(users) { user ->
                UserCard(
                    user       = user,
                    isSelected = state.selectedUser?.id == user.id,
                    onClick    = { viewModel.selectUser(user) },
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        if (state.selectedUser != null) {
            Text(
                text  = "PIN for ${state.selectedUser!!.name}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            PinDots(enteredLength = state.pin.length)

            Spacer(Modifier.height(8.dp))

            if (state.error != null) {
                Text(
                    text  = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            PinKeypad(
                onDigit     = viewModel::onPinDigit,
                onBackspace = viewModel::onPinBackspace,
                onConfirm   = viewModel::submitPin,
            )
        }
    }
}

@Composable
private fun UserCard(user: User, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier            = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .border(
                width  = if (isSelected) 2.dp else 0.dp,
                color  = if (isSelected) MaterialTheme.colorScheme.primary
                         else            MaterialTheme.colorScheme.surface,
                shape  = RoundedCornerShape(12.dp),
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarInitials(name = user.name, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text      = user.name,
            style     = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines  = 1,
        )
        Text(
            text      = user.role.displayName,
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f)) {
                        when (key) {
                            ""  -> Spacer(Modifier.fillMaxWidth())
                            "⌫" -> FilledTonalButton(
                                onClick  = onBackspace,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, null)
                            }
                            else -> FilledTonalButton(
                                onClick  = { onDigit(key) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(key, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text("Unlock")
        }
    }
}
