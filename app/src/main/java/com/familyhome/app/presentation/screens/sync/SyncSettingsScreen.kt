package com.familyhome.app.presentation.screens.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.presentation.theme.SyncConnectedColor
import com.familyhome.app.presentation.theme.SyncDisconnectedColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hostIpInput by remember(state.hostIp) { mutableStateOf(state.hostIp ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title         = { Text("Sync Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier  = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Status card ──────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Sync,
                            null,
                            tint = if (state.isConnected) SyncConnectedColor else SyncDisconnectedColor,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.isConnected) "Connected" else "Not connected",
                            color = if (state.isConnected) SyncConnectedColor else SyncDisconnectedColor,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.lastSyncAt?.let {
                            "Last synced: ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(it))}"
                        } ?: "Never synced",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.syncError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.syncError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Host mode ────────────────────────────────────────────────
            if (state.canHostSync) {
                Text("Host Mode", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.isHostRunning) "Server running on port ${state.serverPort}"
                    else                     "Start the sync server so other devices can connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.localIp?.let { Text("This device IP: $it", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = viewModel::startHostServer,
                        enabled  = !state.isHostRunning,
                        modifier = Modifier.weight(1f),
                    ) { Text("Start Server") }
                    OutlinedButton(
                        onClick  = viewModel::stopHostServer,
                        enabled  = state.isHostRunning,
                        modifier = Modifier.weight(1f),
                    ) { Text("Stop Server") }
                }
                HorizontalDivider()
            }

            // ── Client mode ──────────────────────────────────────────────
            Text("Connect to Host", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value         = hostIpInput,
                onValueChange = { hostIpInput = it },
                label         = { Text("Host IP address (e.g. 192.168.1.5)") },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier      = Modifier.fillMaxWidth(),
            )
            Button(
                onClick  = { viewModel.saveHostIp(hostIpInput) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }

            Button(
                onClick  = viewModel::syncNow,
                enabled  = !state.isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sync Now")
                }
            }
        }
    }
}
