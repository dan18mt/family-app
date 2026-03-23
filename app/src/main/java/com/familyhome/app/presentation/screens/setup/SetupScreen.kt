package com.familyhome.app.presentation.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.presentation.components.LoadingScreen

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) {
        if (state.isDone) onSetupComplete()
    }

    if (state.isLoading) {
        LoadingScreen()
        return
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = "Welcome to FamilyHome",
            style     = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Let's set up the Father account first.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value         = state.name,
            onValueChange = viewModel::onNameChange,
            label         = { Text("Your name") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value             = state.pin,
            onValueChange     = { if (it.length <= 4 && it.all(Char::isDigit)) viewModel.onPinChange(it) },
            label             = { Text("4-digit PIN") },
            singleLine        = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions   = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier          = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value             = state.confirmPin,
            onValueChange     = { if (it.length <= 4 && it.all(Char::isDigit)) viewModel.onConfirmPinChange(it) },
            label             = { Text("Confirm PIN") },
            singleLine        = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions   = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier          = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = viewModel::createFather,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create Family")
        }
    }
}
