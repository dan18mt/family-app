package com.familyhome.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.familyhome.app.presentation.theme.LowStockBackground
import com.familyhome.app.presentation.theme.LowStockColor

// ── App logo ─────────────────────────────────────────────────────────────────

/** Rounded square logo tile used on Setup / Tutorial screens. */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Int = 72) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.27f).dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.Home,
            contentDescription = "FamilyHome",
            tint               = Color.White,
            modifier           = Modifier.size((size * 0.55f).dp),
        )
    }
}

// ── Full-screen states ────────────────────────────────────────────────────────

@Composable
fun LoadingScreen() {
    Box(
        modifier         = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(size = 64)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.Warning,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.error,
            modifier           = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurface,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                Text("Try Again")
            }
        }
    }
}

// ── Avatar ────────────────────────────────────────────────────────────────────

@Composable
fun AvatarInitials(
    name: String,
    modifier: Modifier = Modifier,
    avatarUri: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color   = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: (() -> Unit)? = null,
) {
    if (!avatarUri.isNullOrBlank()) {
        AsyncImage(
            model = avatarUri,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        )
    } else {
        val initials = name.trim().split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")

        Box(
            modifier = modifier.clip(CircleShape).background(containerColor)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = initials,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
            )
        }
    }
}

// ── Chips & badges ────────────────────────────────────────────────────────────

@Composable
fun LowStockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LowStockBackground)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text     = "Low stock",
            style    = MaterialTheme.typography.labelSmall,
            color    = LowStockColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

// ── PIN dots ──────────────────────────────────────────────────────────────────

@Composable
fun PinDots(enteredLength: Int, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            val filled = index < enteredLength
            val scale by animateFloatAsState(
                targetValue = if (filled) 1.15f else 1f,
                animationSpec = tween(150),
                label = "pin_dot_scale",
            )
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else        MaterialTheme.colorScheme.surfaceVariant,
                    )
            )
        }
    }
}

// ── Bottom navigation bar ─────────────────────────────────────────────────────

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

private val bottomNavItems = listOf(
    BottomNavItem("home",     "Home",    Icons.Default.Home),
    BottomNavItem("stock",    "Pantry",  Icons.Default.Kitchen),
    BottomNavItem("chores",   "Chores",  Icons.Default.CheckCircle),
    BottomNavItem("expenses", "Budget",  Icons.Default.AccountBalanceWallet),
    BottomNavItem("prayer",   "Ibadah",  Icons.Default.AutoAwesome),
)

@Composable
fun FamilyBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        tonalElevation = 3.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = { if (!selected) onNavigate(item.route) },
                icon     = {
                    Icon(
                        imageVector        = item.icon,
                        contentDescription = item.label,
                        modifier           = Modifier.size(22.dp),
                    )
                },
                label  = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

// ── Page indicator ────────────────────────────────────────────────────────────

@Composable
fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateFloatAsState(
                targetValue   = if (selected) 24f else 8f,
                animationSpec = tween(250),
                label         = "dot_width",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else          MaterialTheme.colorScheme.outlineVariant,
                    )
            )
        }
    }
}
