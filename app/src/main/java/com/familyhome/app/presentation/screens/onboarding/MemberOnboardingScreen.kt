package com.familyhome.app.presentation.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MemberOnboardingScreen(
    onDone: () -> Unit,
    viewModel: MemberOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step is MemberOnboardingStep.Done) onDone()
    }

    when (state.step) {
        is MemberOnboardingStep.Scanning        -> ScanningStep(state)
        is MemberOnboardingStep.InviteReceived  -> InviteReceivedStep(state, viewModel)
        is MemberOnboardingStep.FillProfile     -> FillProfileStep(state, viewModel)
        is MemberOnboardingStep.WaitingApproval -> WaitingApprovalStep(state)
        is MemberOnboardingStep.Done            -> Unit
    }
}

// ── Step 1: Scanning / waiting for invite ───────────────────────────────────

@Composable
private fun ScanningStep(state: MemberOnboardingUiState) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Default.Wifi,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text      = "Waiting for Invitation",
            style     = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        if (state.knockSent) {
            Text(
                text      = "Notification sent to the Family Leader.\n\nWaiting for them to send you an invite.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text      = "Make sure your phone is on the same WiFi as the Family Leader's device.\n\nAsk the Family Leader to open FamilyHome and send you an invite.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator()

        if (state.error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text  = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Step 2: Invite received ──────────────────────────────────────────────────

@Composable
private fun InviteReceivedStep(
    state: MemberOnboardingUiState,
    viewModel: MemberOnboardingViewModel,
) {
    val fatherName = state.invite?.fatherName ?: "Father"

    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = "You've been invited!",
            style     = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = "$fatherName has invited you to join the family on FamilyHome.",
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick  = viewModel::acceptInvite,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Accept Invitation")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick  = viewModel::declineInvite,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Decline")
        }
    }
}

// ── Step 3: Fill profile ─────────────────────────────────────────────────────

@Composable
private fun FillProfileStep(
    state: MemberOnboardingUiState,
    viewModel: MemberOnboardingViewModel,
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Create Your Profile",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Father will approve your profile and assign your role.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value           = state.name,
            onValueChange   = viewModel::onNameChange,
            label           = { Text("Your name") },
            singleLine      = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction      = ImeAction.Done,
            ),
            modifier        = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = viewModel::submitProfile,
            enabled  = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Join Family")
            }
        }
    }
}

// ── Step 4: Waiting for Father's approval ────────────────────────────────────

@Composable
private fun WaitingApprovalStep(state: MemberOnboardingUiState) {
    val fatherName = state.invite?.fatherName ?: "Father"

    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            text      = "Waiting for Approval",
            style     = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "$fatherName needs to approve your profile and assign your role.\n\nThis screen will update automatically.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
