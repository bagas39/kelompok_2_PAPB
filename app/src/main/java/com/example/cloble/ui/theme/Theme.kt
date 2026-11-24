package com.example.cloble.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val CloBleLightColorScheme = lightColorScheme(
    primary = LightCloBlePalette.primary,
    onPrimary = LightCloBlePalette.onPrimary,
    primaryContainer = LightCloBlePalette.primaryContainer,
    onPrimaryContainer = LightCloBlePalette.onPrimaryContainer,
    secondary = LightCloBlePalette.primary,
    onSecondary = LightCloBlePalette.onPrimary,
    secondaryContainer = LightCloBlePalette.secondarySurface,
    onSecondaryContainer = LightCloBlePalette.textPrimary,
    tertiary = LightCloBlePalette.starRating,
    onTertiary = LightCloBlePalette.textPrimary,
    background = LightCloBlePalette.background,
    onBackground = LightCloBlePalette.textPrimary,
    surface = LightCloBlePalette.surface,
    onSurface = LightCloBlePalette.textPrimary,
    surfaceVariant = LightCloBlePalette.secondarySurface,
    onSurfaceVariant = LightCloBlePalette.textSecondary,
    outline = LightCloBlePalette.outline,
    outlineVariant = LightCloBlePalette.outline,
    error = LightCloBlePalette.dangerText,
    errorContainer = LightCloBlePalette.dangerContainer
)

private val CloBleDarkColorScheme = darkColorScheme(
    primary = DarkCloBlePalette.primary,
    onPrimary = DarkCloBlePalette.onPrimary,
    primaryContainer = DarkCloBlePalette.primaryContainer,
    onPrimaryContainer = DarkCloBlePalette.onPrimaryContainer,
    secondary = DarkCloBlePalette.primarySoft,
    onSecondary = DarkCloBlePalette.onPrimary,
    secondaryContainer = DarkCloBlePalette.secondarySurface,
    onSecondaryContainer = DarkCloBlePalette.onPrimaryContainer,
    tertiary = DarkCloBlePalette.starRating,
    onTertiary = DarkCloBlePalette.onPrimary,
    background = DarkCloBlePalette.background,
    onBackground = DarkCloBlePalette.textPrimary,
    surface = DarkCloBlePalette.surface,
    onSurface = DarkCloBlePalette.textPrimary,
    surfaceVariant = DarkCloBlePalette.secondarySurface,
    onSurfaceVariant = DarkCloBlePalette.textSecondary,
    outline = DarkCloBlePalette.outline,
    outlineVariant = DarkCloBlePalette.outline,
    error = DarkCloBlePalette.dangerText,
    errorContainer = DarkCloBlePalette.dangerContainer
)

val CloBleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun CloBleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkCloBlePalette else LightCloBlePalette
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CloBleDarkColorScheme
        else -> CloBleLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(activity.window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalCloBlePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CloBleTypography,
            shapes = CloBleShapes,
            content = content
        )
    }
}
