package com.familyhome.app.presentation.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.KnockDto
import com.familyhome.app.data.sync.MemberPresenceTracker
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.AvatarInitials
import com.familyhome.app.presentation.components.LowStockBadge
import com.familyhome.app.presentation.components.SectionHeader
import com.familyhome.app.presentation.navigation.Screen
import com.familyhome.app.presentation.theme.BudgetWarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateTo: (Screen) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var roleDialogRequest by remember { mutableStateOf<JoinRequestDto?>(null) }
    var kickTarget        by remember { mutableStateOf<User?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && state.currentUser != null) {
            viewModel.updateProfile(state.currentUser!!.copy(avatarUri = uri.toString()))
        }
    }

    // Role assignment dialog
    roleDialogRequest?.let { request ->
        ApprovalRoleDialog(
            memberName = request.name,
            onDismiss  = { roleDialogRequest = null },
            onAssign   = { role ->
                viewModel.approveJoinRequest(request, role)
                roleDialogRequest = null
            },
        )
    }

    // Kick confirmation dialog
    kickTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            title = { Text("Remove ${member.name}?") },
            text  = { Text("${member.name} will be removed from the family and will no longer be able to sync.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.kickMember(member); kickTarget = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { kickTarget = null }) { Text("Cancel") } },
        )
    }

    LaunchedEffect(state.knockError) {
        if (state.knockError != null) { snackbarHostState.showSnackbar(state.knockError!!); viewModel.dismissKnockError() }
    }
    LaunchedEffect(state.syncError) {
        if (state.syncError != null) { snackbarHostState.showSnackbar(state.syncError!!); viewModel.dismissSyncError() }
    }
    LaunchedEffect(state.kickError) {
        if (state.kickError != null) { snackbarHostState.showSnackbar(state.kickError!!); viewModel.dismissKickError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.currentUser?.role == Role.FATHER) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateTo(Screen.ManageMembers) },
                    icon    = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text    = { Text("Invite Member") },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FamilyHome", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                        state.currentUser?.let {
                            Text("Hello, ${it.name}!", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    state.currentUser?.let { user ->
                        AvatarInitials(
                            name      = user.name,
                            avatarUri = user.avatarUri,
                            modifier  = Modifier.size(36.dp),
                            onClick   = { pickImageLauncher.launch("image/*") },
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    BadgedBox(
                        badge = {
                            if (state.unreadNotificationCount > 0) {
                                Badge { Text(state.unreadNotificationCount.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { onNavigateTo(Screen.Notifications) }) {
                            Icon(Icons.Default.Notifications, "Notifications",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.manualSync() }) {
                            Icon(Icons.Default.Sync, "Sync", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding      = PaddingValues(bottom = 24.dp),
        ) {
            // ── Quick-access grid ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Quick access")
                Spacer(Modifier.height(4.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureTile("Pantry",  Icons.Default.Kitchen,               listOf(Color(0xFF2D6A4F), Color(0xFF52796F)), Modifier.weight(1f)) { onNavigateTo(Screen.Stock) }
                        FeatureTile("Chores",  Icons.Default.CheckCircle,           listOf(Color(0xFF52796F), Color(0xFF74A57F)), Modifier.weight(1f)) { onNavigateTo(Screen.Chores) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureTile("Budget",  Icons.Default.AccountBalanceWallet,  listOf(Color(0xFFC77B41), Color(0xFFE8A87C)), Modifier.weight(1f)) { onNavigateTo(Screen.Expenses) }
                        FeatureTile("AI Chat", Icons.AutoMirrored.Filled.Chat,      listOf(Color(0xFF4A4458), Color(0xFF7B6FA0)), Modifier.weight(1f)) { onNavigateTo(Screen.Chat) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Pending approvals (Father only) ───────────────────────────
            if (state.currentUser?.role == Role.FATHER && state.pendingJoinRequests.isNotEmpty()) {
                item { SectionHeader("Pending Approvals (${state.pendingJoinRequests.size})") }
                items(state.pendingJoinRequests, key = { it.deviceId }) { request ->
                    PendingApprovalCard(
                        request   = request,
                        onApprove = { roleDialogRequest = request },
                        onReject  = { viewModel.rejectJoinRequest(request) },
                    )
                }
            }

            // ── Knocks (Father only) ───────────────────────────────────────
            if (state.currentUser?.role == Role.FATHER && state.pendingKnocks.isNotEmpty()) {
                item { SectionHeader("Waiting to Invite (${state.pendingKnocks.size})") }
                items(state.pendingKnocks, key = { it.deviceId }) { knock ->
                    JoinRequestKnockCard(
                        knock        = knock,
                        isLoading    = state.invitingKnockIds.contains(knock.deviceId),
                        onSendInvite = { viewModel.sendInviteFromKnock(knock) },
                    )
                }
            }

            // ── Low-stock alerts ───────────────────────────────────────────
            if (state.lowStockItems.isNotEmpty()) {
                item { SectionHeader("Low stock (${state.lowStockItems.size})") }
                items(state.lowStockItems.take(3)) { item ->
                    ListItem(
                        headlineContent   = { Text(item.name, style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text("${item.quantity} ${item.unit} left") },
                        trailingContent   = { LowStockBadge() },
                        leadingContent    = {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        },
                    )
                }
                if (state.lowStockItems.size > 3) {
                    item {
                        TextButton(onClick = { onNavigateTo(Screen.Stock) }, modifier = Modifier.padding(start = 8.dp)) {
                            Text("See all ${state.lowStockItems.size} items")
                        }
                    }
                }
            }

            // ── Budget alerts ──────────────────────────────────────────────
            val warnings = state.budgetAlerts.filter { it.isWarning }
            if (warnings.isNotEmpty()) {
                item { SectionHeader("Budget alerts") }
                items(warnings) { alert ->
                    ListItem(
                        headlineContent   = { Text(alert.budget.category?.displayName ?: "Overall budget", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text("${(alert.usageRatio * 100).toInt()}% used") },
                        leadingContent    = {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MonetizationOn, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                            }
                        },
                    )
                }
            }

            // ── Family members ─────────────────────────────────────────────
            if (state.familyMembers.isNotEmpty()) {
                item { SectionHeader("Family (${state.familyMembers.size})") }
                items(state.familyMembers) { member ->
                    val isFather        = state.currentUser?.role == Role.FATHER
                    val isSelf          = member.id == state.currentUser?.id
                    val isOnline        = state.memberLastSeen[member.id]?.let {
                        System.currentTimeMillis() - it < MemberPresenceTracker.ONLINE_THRESHOLD_MS
                    } ?: isSelf // current user's own device is always "online" locally

                    ListItem(
                        headlineContent   = { Text(member.name, style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(member.role.displayName)
                                OnlineDot(isOnline = isOnline)
                                Text(
                                    if (isOnline) "Online" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        },
                        leadingContent = {
                            AvatarInitials(name = member.name, modifier = Modifier.size(40.dp))
                        },
                        trailingContent = {
                            if (isFather && !isSelf) {
                                IconButton(onClick = { kickTarget = member }) {
                                    Icon(Icons.Default.PersonRemove, "Remove member",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineDot(isOnline: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFBDBDBD))
    )
}

@Composable
private fun PendingApprovalCard(request: JoinRequestDto, onApprove: () -> Unit, onReject: () -> Unit) {
    ListItem(
        headlineContent   = { Text(request.name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text("Submitted from ${request.deviceName} — waiting for your approval") },
        leadingContent = {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.HowToReg, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onReject) {
                    Icon(Icons.Default.Close, "Reject", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onApprove) {
                    Icon(Icons.Default.Check, "Approve", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
    )
}

@Composable
private fun ApprovalRoleDialog(memberName: String, onDismiss: () -> Unit, onAssign: (Role) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Role to $memberName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What is ${memberName}'s role in the family?")
                Button(onClick = { onAssign(Role.WIFE) }, modifier = Modifier.fillMaxWidth()) { Text("Partner / Spouse") }
                OutlinedButton(onClick = { onAssign(Role.KID) }, modifier = Modifier.fillMaxWidth()) { Text("Kid / Child") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun JoinRequestKnockCard(knock: KnockDto, isLoading: Boolean, onSendInvite: () -> Unit) {
    ListItem(
        headlineContent   = { Text(knock.deviceName, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text("Wants to join your family") },
        leadingContent = {
            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            else Button(onClick = onSendInvite) { Text("Invite") }
        },
    )
}

@Composable
private fun FeatureTile(
    label: String,
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.height(100.dp),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier            = Modifier.padding(8.dp),
            ) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}
