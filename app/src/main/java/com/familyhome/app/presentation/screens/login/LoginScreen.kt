package com.familyhome.app.presentation.screens.login

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.AppLogo
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

    LaunchedEffect(state.isSuccess) { if (state.isSuccess) onLoginSuccess() }
    if (state.isLoading) { LoadingScreen(); return }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))

        // Logo + greeting
        AppLogo(size = 56)
        Spacer(Modifier.height(16.dp))
        Text(
            text  = "FamilyHome",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Who's logging in?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(32.dp))

        // User selection row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 4.dp),
        ) {
            items(users, key = { it.id }) { user ->
                UserCard(
                    user       = user,
                    isSelected = state.selectedUser?.id == user.id,
                    onClick    = { viewModel.selectUser(user) },
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        if (state.selectedUser != null) {
            Text(
                text  = "Enter PIN for ${state.selectedUser!!.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(20.dp))

            PinDots(enteredLength = state.pin.length)

            Spacer(Modifier.height(8.dp))

            if (state.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = state.error!!,
                    color     = MaterialTheme.colorScheme.error,
                    style     = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

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
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label         = "user_border",
    )
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label         = "user_bg",
    )

    Column(
        modifier            = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarInitials(
            name            = user.name,
            modifier        = Modifier.size(52.dp),
            containerColor  = if (isSelected) MaterialTheme.colorScheme.primary
                              else            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor    = if (isSelected) MaterialTheme.colorScheme.onPrimary
                              else            MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = user.name,
            style     = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines  = 1,
            color     = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
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
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier            = Modifier.fillMaxWidth(),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f)) {
                        when (key) {
                            "" -> Spacer(Modifier.fillMaxWidth())
                            "⌫" -> FilledTonalButton(
                                onClick  = onBackspace,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape    = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, null, modifier = Modifier.size(20.dp))
                            }
                            else -> FilledTonalButton(
                                onClick  = { onDigit(key) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape    = RoundedCornerShape(14.dp),
                            ) {
                                Text(key, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        Button(
            onClick  = onConfirm,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
        ) {
            Text("Unlock", style = MaterialTheme.typography.labelLarge)
        }
    }
}
