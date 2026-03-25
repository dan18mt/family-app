package com.familyhome.app.presentation.screens.tutorial

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familyhome.app.presentation.components.PageIndicator

private data class TutorialPage(
    val icon: ImageVector,
    val iconTint: @Composable () -> androidx.compose.ui.graphics.Color,
    val iconBackground: @Composable () -> androidx.compose.ui.graphics.Color,
    val title: String,
    val description: String,
)

@Composable
private fun tutorialPages(): List<TutorialPage> = listOf(
    TutorialPage(
        icon            = Icons.Default.Home,
        iconTint        = { MaterialTheme.colorScheme.onPrimary },
        iconBackground  = { MaterialTheme.colorScheme.primary },
        title           = "Welcome to FamilyHome!",
        description     = "Your private family hub. Track chores, groceries, expenses, and chat — all in one place, just for your family.",
    ),
    TutorialPage(
        icon            = Icons.Default.Kitchen,
        iconTint        = { MaterialTheme.colorScheme.onTertiaryContainer },
        iconBackground  = { MaterialTheme.colorScheme.tertiaryContainer },
        title           = "Pantry Tracker",
        description     = "Keep track of what's in the fridge and pantry. You'll get alerts when anything runs low so nobody is ever caught off guard.",
    ),
    TutorialPage(
        icon            = Icons.Default.CheckCircle,
        iconTint        = { MaterialTheme.colorScheme.onSecondaryContainer },
        iconBackground  = { MaterialTheme.colorScheme.secondaryContainer },
        title           = "Chores & Tasks",
        description     = "Assign household chores to family members, set recurring tasks, and log completions. Keep the home running smoothly.",
    ),
    TutorialPage(
        icon            = Icons.Default.AccountBalanceWallet,
        iconTint        = { MaterialTheme.colorScheme.onPrimaryContainer },
        iconBackground  = { MaterialTheme.colorScheme.primaryContainer },
        title           = "Family Budget",
        description     = "Log every expense together. Set spending limits by category and get notified before you overspend.",
    ),
    TutorialPage(
        icon            = Icons.AutoMirrored.Filled.Chat,
        iconTint        = { MaterialTheme.colorScheme.onSecondary },
        iconBackground  = { MaterialTheme.colorScheme.secondary },
        title           = "AI Family Assistant",
        description     = "Ask the built-in AI assistant anything — what's running low, who did chores this week, how much was spent. It knows your family.",
    ),
)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val pages = tutorialPages()
    var current by remember { mutableIntStateOf(0) }
    val isLast = current == pages.lastIndex

    Column(
        modifier            = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Skip button
        Box(
            modifier        = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TextButton(onClick = onDone) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Animated page content
        AnimatedContent(
            targetState   = current,
            modifier      = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
                val exit  = slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                enter togetherWith exit
            },
            label = "tutorial_page",
        ) { pageIndex ->
            TutorialPageContent(page = pages[pageIndex])
        }

        // Bottom controls
        Column(
            modifier            = Modifier.padding(horizontal = 32.dp).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageIndicator(count = pages.size, current = current)

            Button(
                onClick = {
                    if (isLast) onDone() else current++
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text  = if (isLast) "Get Started" else "Next",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TutorialPageContent(page: TutorialPage) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier         = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(page.iconBackground()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = page.icon,
                contentDescription = null,
                tint               = page.iconTint(),
                modifier           = Modifier.size(56.dp),
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text      = page.title,
            style     = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text      = page.description,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
