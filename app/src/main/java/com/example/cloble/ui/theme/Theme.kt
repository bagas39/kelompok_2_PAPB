package com.example.cloble.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val CloBleLightColorScheme = lightColorScheme(
    primary = CloBleColors.Primary,
    onPrimary = CloBleColors.OnPrimary,
    primaryContainer = CloBleColors.PrimaryContainer,
    onPrimaryContainer = CloBleColors.OnPrimaryContainer,
    secondary = CloBleColors.Primary,
    onSecondary = CloBleColors.OnPrimary,
    secondaryContainer = CloBleColors.SecondarySurface,
    onSecondaryContainer = CloBleColors.TextPrimary,
    tertiary = CloBleColors.StarRating,
    onTertiary = CloBleColors.TextPrimary,
    background = CloBleColors.Background,
    onBackground = CloBleColors.TextPrimary,
    surface = CloBleColors.Surface,
    onSurface = CloBleColors.TextPrimary,
    surfaceVariant = CloBleColors.SecondarySurface,
    onSurfaceVariant = CloBleColors.TextSecondary,
    outline = CloBleColors.Outline,
    outlineVariant = CloBleColors.Outline,
    error = CloBleColors.DangerText,
    errorContainer = CloBleColors.DangerContainer
)

private val CloBleDarkColorScheme = darkColorScheme(
    primary = CloBleColors.PrimarySoft,
    onPrimary = Color(0xFF0A1429),
    primaryContainer = Color(0xFF1E325C),
    onPrimaryContainer = Color(0xFFEAF4FF),
    secondary = CloBleColors.PrimarySoft,
    onSecondary = Color(0xFF0A1429),
    secondaryContainer = Color(0xFF1A2336),
    onSecondaryContainer = Color(0xFFEAF4FF),
    tertiary = CloBleColors.StarRating,
    onTertiary = CloBleColors.TextPrimary,
    background = Color(0xFF0F141E),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF151C28),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF1E2738),
    onSurfaceVariant = Color(0xFFB0BAC9),
    outline = Color(0xFF2E3B52),
    outlineVariant = Color(0xFF2E3B52)
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
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CloBleTypography,
        shapes = CloBleShapes,
        content = content
    )
}
