package com.familyhome.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val lightColorScheme = lightColorScheme(
    primary              = PrimaryLight,
    onPrimary            = OnPrimaryLight,
    primaryContainer     = PrimaryContainerLight,
    onPrimaryContainer   = OnPrimaryContainerLight,
    secondary            = SecondaryLight,
    onSecondary          = OnSecondaryLight,
    secondaryContainer   = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary             = TertiaryLight,
    error                = ErrorLight,
    background           = BackgroundLight,
    surface              = SurfaceLight,
    onBackground         = OnBackgroundLight,
    onSurface            = OnSurfaceLight,
)

private val darkColorScheme = darkColorScheme(
    primary              = PrimaryDark,
    onPrimary            = OnPrimaryDark,
    primaryContainer     = PrimaryContainerDark,
    onPrimaryContainer   = OnPrimaryContainerDark,
    secondary            = SecondaryDark,
    onSecondary          = OnSecondaryDark,
    tertiary             = TertiaryDark,
    error                = ErrorDark,
    background           = BackgroundDark,
    surface              = SurfaceDark,
    onBackground         = OnBackgroundDark,
    onSurface            = OnSurfaceDark,
)

@Composable
fun FamilyHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // statusBarColor is deprecated on API 35+; enableEdgeToEdge() in MainActivity
            // handles the scrim. We only control light/dark icon appearance here.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
