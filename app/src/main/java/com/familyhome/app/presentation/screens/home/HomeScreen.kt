package com.familyhome.app.presentation.screens.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyhome.app.data.onboarding.JoinRequestDto
import com.familyhome.app.data.onboarding.KnockDto
import com.familyhome.app.data.sync.MemberPresenceTracker
import com.familyhome.app.domain.model.AppLanguage
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.presentation.components.AvatarInitials
import com.familyhome.app.presentation.components.DraggableFab
import com.familyhome.app.presentation.components.SectionHeader
import com.familyhome.app.presentation.navigation.Screen
import com.familyhome.app.presentation.theme.BudgetWarningColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateTo: (Screen) -> Unit,
    onNavigateToTab: (String) -> Unit = {},
    currentTabRoute: String = Screen.Home.route,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state              by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState  = remember { SnackbarHostState() }
    var roleDialogRequest  by remember { mutableStateOf<JoinRequestDto?>(null) }
    var kickTarget         by remember { mutableStateOf<User?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val activity           = LocalContext.current as? Activity

    LaunchedEffect(Unit) {
        viewModel.languageChanged.collect { activity?.recreate() }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = viewModel.currentLanguage,
            onSelect  = { lang -> viewModel.setLanguage(lang); showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false },
        )
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && state.currentUser != null) {
            viewModel.updateProfile(state.currentUser!!.copy(avatarUri = uri.toString()))
        }
    }

    roleDialogRequest?.let { request ->
        ApprovalRoleDialog(
            memberName = request.name,
            onDismiss  = { roleDialogRequest = null },
            onAssign   = { role -> viewModel.approveJoinRequest(request, role); roleDialogRequest = null },
        )
    }

    kickTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            title = { Text("Remove ${member.name}?") },
            text  = { Text("${member.name} will be removed and can no longer sync.") },
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
        state.knockError?.let { snackbarHostState.showSnackbar(it); viewModel.dismissKnockError() }
    }
    LaunchedEffect(state.syncError) {
        state.syncError?.let { snackbarHostState.showSnackbar(it); viewModel.dismissSyncError() }
    }
    LaunchedEffect(state.kickError) {
        state.kickError?.let { snackbarHostState.showSnackbar(it); viewModel.dismissKickError() }
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val lowCount     = state.lowStockItems.size
        val budgetCount  = state.budgetAlerts.count { it.isWarning }
        val pendingCount = state.pendingJoinRequests.size + state.pendingKnocks.size
        val warnings     = state.budgetAlerts.filter { it.isWarning }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            // ── Hero banner ──────────────────────────────────────────────────
            item {
                HeroBanner(
                    user            = state.currentUser,
                    unreadCount     = state.unreadNotificationCount,
                    isSyncing       = state.isSyncing,
                    onAvatar        = { pickImageLauncher.launch("image/*") },
                    onNotifications = { onNavigateTo(Screen.Notifications) },
                    onSync          = { viewModel.manualSync() },
                    onLanguage      = { showLanguageDialog = true },
                )
            }

            // ── Quick-alert chips ────────────────────────────────────────────
            if (lowCount > 0 || budgetCount > 0 || pendingCount > 0) {
                item {
                    LazyRow(
                        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (lowCount > 0) item {
                            QuickStatChip(
                                icon    = Icons.Default.Warning,
                                label   = "$lowCount Low Stock",
                                tint    = MaterialTheme.colorScheme.error,
                                bgColor = MaterialTheme.colorScheme.errorContainer,
                                onClick = { onNavigateTo(Screen.Stock) },
                            )
                        }
                        if (budgetCount > 0) item {
                            QuickStatChip(
                                icon    = Icons.Default.MonetizationOn,
                                label   = "$budgetCount Budget Alert",
                                tint    = BudgetWarningColor,
                                bgColor = Color(0xFFFFDEBC),
                                onClick = { onNavigateTo(Screen.Expenses) },
                            )
                        }
                        if (pendingCount > 0) item {
                            QuickStatChip(
                                icon    = Icons.Default.HowToReg,
                                label   = "$pendingCount Pending",
                                tint    = MaterialTheme.colorScheme.primary,
                                bgColor = MaterialTheme.colorScheme.primaryContainer,
                                onClick = { /* scrolls down to pending section */ },
                            )
                        }
                    }
                }
            } else {
                item { Spacer(Modifier.height(16.dp)) }
            }

            // ── Quick access grid ────────────────────────────────────────────
            item {
                SectionHeader("Quick Access")
                Column(
                    modifier              = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureTile(
                            label    = "Pantry",
                            icon     = Icons.Default.Kitchen,
                            gradient = listOf(Color(0xFF2D6A4F), Color(0xFF52796F)),
                            badge    = if (lowCount > 0) "$lowCount low" else null,
                            modifier = Modifier.weight(1f),
                            onClick  = { onNavigateTo(Screen.Stock) },
                        )
                        FeatureTile(
                            label    = "Chores",
                            icon     = Icons.Default.CheckCircle,
                            gradient = listOf(Color(0xFF52796F), Color(0xFF74A57F)),
                            badge    = null,
                            modifier = Modifier.weight(1f),
                            onClick  = { onNavigateTo(Screen.Chores) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureTile(
                            label    = "Budget",
                            icon     = Icons.Default.AccountBalanceWallet,
                            gradient = listOf(Color(0xFFC77B41), Color(0xFFE8A87C)),
                            badge    = if (budgetCount > 0) "$budgetCount alert" else null,
                            modifier = Modifier.weight(1f),
                            onClick  = { onNavigateTo(Screen.Expenses) },
                        )
                        FeatureTile(
                            label    = "Ibadah",
                            icon     = Icons.Default.AutoAwesome,
                            gradient = listOf(Color(0xFF1B4332), Color(0xFF2D6A4F)),
                            badge    = null,
                            modifier = Modifier.weight(1f),
                            onClick  = { onNavigateTo(Screen.Prayer) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Family section ───────────────────────────────────────────────
            if (state.familyMembers.isNotEmpty()) {
                item {
                    SectionHeader("Family (${state.familyMembers.size})")
                    FamilyAvatarRow(
                        members        = state.familyMembers,
                        currentUserId  = state.currentUser?.id,
                        memberLastSeen = state.memberLastSeen,
                        isFather       = state.currentUser?.role == Role.FATHER,
                        onInvite       = { onNavigateTo(Screen.ManageMembers) },
                        onKick         = { kickTarget = it },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Pending approvals (Father only) ──────────────────────────────
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

            // ── Knocks (Father only) ─────────────────────────────────────────
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

            // ── Low-stock alerts ─────────────────────────────────────────────
            if (state.lowStockItems.isNotEmpty()) {
                item { SectionHeader("Low Stock (${state.lowStockItems.size})") }
                items(state.lowStockItems.take(3)) { stockItem ->
                    AlertCard(
                        icon           = Icons.Default.Warning,
                        iconTint       = MaterialTheme.colorScheme.error,
                        iconBgColor    = MaterialTheme.colorScheme.errorContainer,
                        headline       = stockItem.name,
                        supportingText = "${stockItem.quantity} ${stockItem.unit} remaining",
                        onClick        = { onNavigateTo(Screen.Stock) },
                    )
                }
                if (state.lowStockItems.size > 3) {
                    item {
                        TextButton(
                            onClick  = { onNavigateTo(Screen.Stock) },
                            modifier = Modifier.padding(start = 12.dp),
                        ) { Text("See all ${state.lowStockItems.size} items") }
                    }
                }
            }

            // ── Budget alerts ────────────────────────────────────────────────
            if (warnings.isNotEmpty()) {
                item { SectionHeader("Budget Alerts") }
                items(warnings) { alert ->
                    AlertCard(
                        icon           = Icons.Default.MonetizationOn,
                        iconTint       = BudgetWarningColor,
                        iconBgColor    = Color(0xFFFFDEBC),
                        headline       = alert.budget.category?.displayName ?: "Overall budget",
                        supportingText = "${(alert.usageRatio * 100).toInt()}% of budget used",
                        onClick        = { onNavigateTo(Screen.Expenses) },
                    )
                }
            }
        }

        if (state.currentUser?.role == Role.FATHER) {
            DraggableFab(
                onClick        = { onNavigateTo(Screen.ManageMembers) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                shape          = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Invite")
            }
        }
        } // end Box
    }
}

// ── Hero banner ──────────────────────────────────────────────────────────────

@Composable
private fun HeroBanner(
    user: User?,
    unreadCount: Int,
    isSyncing: Boolean,
    onAvatar: () -> Unit,
    onNotifications: () -> Unit,
    onSync: () -> Unit,
    onLanguage: () -> Unit,
) {
    val hour     = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 5  -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    val dateStr = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF1B4332), Color(0xFF2D6A4F))))
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth(),
        ) {
            AvatarInitials(
                name           = user?.name ?: "",
                avatarUri      = user?.avatarUri,
                modifier       = Modifier.size(52.dp),
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor   = Color.White,
                onClick        = onAvatar,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = "$greeting,",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Text(
                    text       = user?.name ?: "FamilyHome",
                    style      = MaterialTheme.typography.titleLarge,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            BadgedBox(badge = {
                if (unreadCount > 0) Badge { Text(unreadCount.toString()) }
            }) {
                IconButton(onClick = onNotifications) {
                    Icon(Icons.Default.Notifications, null, tint = Color.White)
                }
            }
            IconButton(onClick = if (isSyncing) ({}) else onSync) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = Color.White,
                    )
                } else {
                    Icon(Icons.Default.Sync, null, tint = Color.White)
                }
            }
            IconButton(onClick = onLanguage) {
                Icon(Icons.Default.Language, null, tint = Color.White)
            }
        }
    }
}

// ── Quick stat chip ──────────────────────────────────────────────────────────

@Composable
private fun QuickStatChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    bgColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(20.dp),
        color   = bgColor,
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

// ── Feature tile ─────────────────────────────────────────────────────────────

@Composable
private fun FeatureTile(
    label: String,
    icon: ImageVector,
    gradient: List<Color>,
    badge: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.height(110.dp),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(gradient))) {
            Column(
                modifier            = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            if (badge != null) {
                Text(
                    text     = badge,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ── Family avatar row ────────────────────────────────────────────────────────

@Composable
private fun FamilyAvatarRow(
    members: List<User>,
    currentUserId: String?,
    memberLastSeen: Map<String, Long>,
    isFather: Boolean,
    onInvite: () -> Unit,
    onKick: (User) -> Unit,
) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(members, key = { it.id }) { member ->
            val isOnline = memberLastSeen[member.id]?.let {
                System.currentTimeMillis() - it < MemberPresenceTracker.ONLINE_THRESHOLD_MS
            } ?: (member.id == currentUserId)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.width(60.dp),
            ) {
                Box {
                    AvatarInitials(name = member.name, modifier = Modifier.size(48.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFBDBDBD))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = member.name.split(" ").first(),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = member.role.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isFather) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.width(60.dp).clickable { onInvite() },
                ) {
                    Box(
                        modifier         = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Invite",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ── Alert card ───────────────────────────────────────────────────────────────

@Composable
private fun AlertCard(
    icon: ImageVector,
    iconTint: Color,
    iconBgColor: Color,
    headline: String,
    supportingText: String,
    onClick: () -> Unit,
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleSmall)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint     = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Pending approval card ────────────────────────────────────────────────────

@Composable
private fun PendingApprovalCard(
    request: JoinRequestDto,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.HowToReg,
                    null,
                    tint     = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(request.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "From ${request.deviceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onReject, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    "Reject",
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onApprove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Check,
                    "Approve",
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun ApprovalRoleDialog(
    memberName: String,
    onDismiss: () -> Unit,
    onAssign: (Role) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Role to $memberName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What is ${memberName}'s role in the family?")
                Button(onClick = { onAssign(Role.WIFE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Partner / Spouse")
                }
                OutlinedButton(onClick = { onAssign(Role.KID) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Kid / Child")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun JoinRequestKnockCard(
    knock: KnockDto,
    isLoading: Boolean,
    onSendInvite: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(knock.deviceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Wants to join your family",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = onSendInvite, shape = RoundedCornerShape(10.dp)) { Text("Invite") }
            }
        }
    }
}

@Composable
private fun LanguageDialog(
    currentLanguage: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Language, null) },
        title = { Text("Language / Bahasa") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == currentLanguage
                    OutlinedButton(
                        onClick  = { onSelect(lang) },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = if (isSelected)
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(lang.displayName)
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
