package com.familyhome.app.presentation.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.AppLogo
import com.familyhome.app.presentation.components.AvatarInitials
import com.familyhome.app.presentation.components.LoadingScreen

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

        Text(
            text  = "Tap your name to log in",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        // User selection row — tap to instantly log in
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(horizontal = 4.dp),
        ) {
            items(users, key = { it.id }) { user ->
                UserCard(
                    user    = user,
                    onClick = { viewModel.loginAs(user) },
                )
            }
        }
    }
}

@Composable
private fun UserCard(user: User, onClick: () -> Unit) {
    Column(
        modifier            = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarInitials(
            name           = user.name,
            modifier       = Modifier.size(52.dp),
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor   = MaterialTheme.colorScheme.primary,
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
