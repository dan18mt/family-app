package com.familyhome.app.presentation.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.domain.model.Role

@Composable
fun FatherOnboardingScreen(
    onDone: () -> Unit,
    viewModel: FatherOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step is FatherOnboardingStep.Done) onDone()
    }

    when (state.step) {
        is FatherOnboardingStep.Discovering -> DiscoveringStep(state, viewModel)
        is FatherOnboardingStep.Approving   -> ApprovingStep(state, viewModel)
        is FatherOnboardingStep.Done        -> Unit
    }
}

// ── Step 1: Discovering member devices ──────────────────────────────────────

@Composable
private fun DiscoveringStep(
    state: FatherOnboardingUiState,
    viewModel: FatherOnboardingViewModel,
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = "Find Family Members",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text  = "Devices on the same WiFi running FamilyHome in join mode will appear below. Tap Invite to send them an invitation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.discoveredDevices.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Scanning for family members...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.discoveredDevices, key = { it.device.serviceName }) { deviceUi ->
                    DiscoveredDeviceCard(
                        deviceUi     = deviceUi,
                        isLoading    = state.isLoadingInvite.contains(deviceUi.device.serviceName),
                        onInvite     = { viewModel.sendInvite(deviceUi) },
                    )
                }
            }
        }

        if (state.error != null) {
            Text(
                text  = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val invitedCount = state.discoveredDevices.count { it.inviteSent }
        Button(
            onClick  = viewModel::moveToApproving,
            modifier = Modifier.fillMaxWidth(),
            enabled  = true,
        ) {
            Text(
                if (invitedCount > 0) "Review Join Requests ($invitedCount invited)"
                else "Skip — No Members to Add"
            )
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(
    deviceUi: DiscoveredDeviceUi,
    isLoading: Boolean,
    onInvite: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier            = Modifier.padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Column {
                    Text(
                        text  = deviceUi.deviceInfo?.deviceName ?: deviceUi.device.serviceName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text  = deviceUi.device.hostAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                deviceUi.inviteSent -> Icon(
                    Icons.Default.Check,
                    contentDescription = "Invited",
                    tint = MaterialTheme.colorScheme.primary,
                )
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> IconButton(onClick = onInvite) {
                    Icon(Icons.Default.Send, contentDescription = "Send invite")
                }
            }
        }
    }
}

// ── Step 2: Approving join requests ─────────────────────────────────────────

@Composable
private fun ApprovingStep(
    state: FatherOnboardingUiState,
    viewModel: FatherOnboardingViewModel,
) {
    var roleDialogRequest by remember { mutableStateOf<JoinRequestDto?>(null) }

    roleDialogRequest?.let { request ->
        RoleAssignmentDialog(
            memberName = request.name,
            onDismiss  = { roleDialogRequest = null },
            onAssign   = { role ->
                viewModel.approveRequest(request, role)
                roleDialogRequest = null
            },
        )
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = "Join Requests",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text  = "Family members who accepted your invitation will appear here. Approve them and assign a role.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.pendingRequests.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Waiting for members to submit their profiles...",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.pendingRequests, key = { it.deviceId }) { request ->
                    JoinRequestCard(
                        request  = request,
                        onApprove = { roleDialogRequest = request },
                        onReject  = { viewModel.rejectRequest(request) },
                    )
                }
            }
        }

        Button(
            onClick  = viewModel::finish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done — Go to FamilyHome")
        }
    }
}

@Composable
private fun JoinRequestCard(
    request: JoinRequestDto,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = request.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text  = request.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onApprove) {
                    Icon(Icons.Default.Check, contentDescription = "Approve", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun RoleAssignmentDialog(
    memberName: String,
    onDismiss: () -> Unit,
    onAssign: (Role) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Role to $memberName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Who is $memberName in the family?")
                Button(
                    onClick  = { onAssign(Role.WIFE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Wife / Partner") }
                OutlinedButton(
                    onClick  = { onAssign(Role.KID) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Kid / Child") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
