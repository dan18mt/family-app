package com.familyhome.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familyhome.app.presentation.theme.LowStockBackground
import com.familyhome.app.presentation.theme.LowStockColor

/** Full-screen loading indicator. */
@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Full-screen error state with a retry button. */
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
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint   = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
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
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** Avatar circle showing initials when no image URI is available. */
@Composable
fun AvatarInitials(
    name: String,
    modifier: Modifier = Modifier,
) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Box(
        modifier            = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment    = Alignment.Center,
    ) {
        Text(
            text  = initials,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Chip-style badge used for low-stock alerts. */
@Composable
fun LowStockBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LowStockBackground)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text  = "Low stock",
            style = MaterialTheme.typography.labelSmall,
            color = LowStockColor,
        )
    }
}

/** Section header used consistently across screens. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Reusable PIN entry row (4 dots + digit buttons handled by caller). */
@Composable
fun PinDots(enteredLength: Int, modifier: Modifier = Modifier) {
    Row(
        modifier            = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment   = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < enteredLength) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

/** Standard bottom bar for the main scaffold tabs. */
@Composable
fun FamilyBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val items = listOf(
        Triple("home",     "Home",    Icons.Default.Warning),
        Triple("stock",    "Pantry",  Icons.Default.Warning),
        Triple("chores",   "Chores",  Icons.Default.Warning),
        Triple("expenses", "Expenses",Icons.Default.Warning),
        Triple("chat",     "Chat",    Icons.Default.Warning),
    )
    NavigationBar {
        items.forEach { (route, label, _) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick  = { onNavigate(route) },
                icon     = { Icon(Icons.Default.Warning, contentDescription = label) },
                label    = { Text(label) },
            )
        }
    }
}
