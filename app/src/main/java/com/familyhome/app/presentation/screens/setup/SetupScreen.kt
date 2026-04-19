package com.familyhome.app.presentation.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.presentation.components.AppLogo
import com.familyhome.app.presentation.components.LoadingScreen

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    onJoinFamily: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) { if (state.isDone) onSetupComplete() }
    LaunchedEffect(state.navigateToJoin) {
        if (state.navigateToJoin) { viewModel.onJoinNavigated(); onJoinFamily() }
    }

    if (state.isLoading) { LoadingScreen(); return }

    when (state.step) {
        SetupStep.CHOOSE       -> ChooseStep(viewModel)
        SetupStep.SETUP_FATHER -> LeaderSetupStep(state, viewModel)
    }
}

// ── Step 1: Welcome / choose path ────────────────────────────────────────────

@Composable
private fun ChooseStep(viewModel: SetupViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Gradient hero header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(size = 80)
                Spacer(Modifier.height(20.dp))
                Text(
                    text  = "FamilyHome",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Your family, all in one place",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }

        // Action area
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 28.dp)
                .padding(top = 36.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "Let's get started",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text  = "Are you setting up a new family, or joining one that already exists?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = viewModel::onCreateFamilyChosen,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Text("Create a Family", style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick  = viewModel::onJoinFamilyChosen,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Text("Join a Family", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── Step 2: Family Leader profile form ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaderSetupStep(
    state: SetupUiState,
    viewModel: SetupViewModel,
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = viewModel::onBackToChoose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text  = "Create your",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = "Leader Profile",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text  = "You'll be the Family Leader — the admin who invites and manages the family.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value         = state.name,
                onValueChange = viewModel::onNameChange,
                label         = { Text("Your name") },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus(); viewModel.createFather() }
                ),
            )

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text  = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick  = { focusManager.clearFocus(); viewModel.createFather() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Text("Create Family", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
