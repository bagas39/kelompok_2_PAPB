package com.example.cloble.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CloBlePalette(
    val primary: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondarySurface: Color,
    val background: Color,
    val surface: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val starRating: Color,
    val onPrimaryMuted: Color,
    val incomingBubbleSurface: Color,
    val successText: Color,
    val successContainer: Color,
    val warningText: Color,
    val warningContainer: Color,
    val dangerText: Color,
    val dangerContainer: Color,
    val darkScrim: Color
)

object CloBleBrandColors {
    val Cobalt = Color(0xFF2554E6)
    val CobaltDeep = Color(0xFF1D4ED8)
    val IceRing = Color(0xFFEAF4FF)
}

val LightCloBlePalette = CloBlePalette(
    primary = Color(0xFF2554E6),
    primarySoft = Color(0xFF3B6FF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAF4FF),
    onPrimaryContainer = Color(0xFF1D4ED8),
    secondarySurface = Color(0xFFF1F5FB),
    background = Color(0xFFF8FAFD),
    surface = Color(0xFFFFFFFF),
    outline = Color(0xFF7D8EA8),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF475569),
    starRating = Color(0xFFC26D00),
    onPrimaryMuted = Color(0xFFDCE7F5),
    incomingBubbleSurface = Color(0xFFEEEEEE),
    successText = Color(0xFF047857),
    successContainer = Color(0xFFECFDF5),
    warningText = Color(0xFF92400E),
    warningContainer = Color(0xFFFEF3C7),
    dangerText = Color(0xFFB91C1C),
    dangerContainer = Color(0xFFFEF2F2),
    darkScrim = Color(0xFF0B0F19)
)

val DarkCloBlePalette = CloBlePalette(
    primary = Color(0xFF7BA6FF),
    primarySoft = Color(0xFF6B9BFF),
    onPrimary = Color(0xFF071430),
    primaryContainer = Color(0xFF172544),
    onPrimaryContainer = Color(0xFFA8C7FA),
    secondarySurface = Color(0xFF1E293B),
    background = Color(0xFF0B1120),
    surface = Color(0xFF131C2E),
    outline = Color(0xFF64748B),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFA3B3C8),
    starRating = Color(0xFFFBBF24),
    onPrimaryMuted = Color(0xFF0F2752),
    incomingBubbleSurface = Color(0xFF243247),
    successText = Color(0xFF6EE7B7),
    successContainer = Color(0xFF0B2E24),
    warningText = Color(0xFFFCD34D),
    warningContainer = Color(0xFF332208),
    dangerText = Color(0xFFFCA5A5),
    dangerContainer = Color(0xFF3B1218),
    darkScrim = Color(0xFF0B0F19)
)

val LocalCloBlePalette = staticCompositionLocalOf { LightCloBlePalette }

object CloBleColors {
    val Primary: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.primary

    val PrimarySoft: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.primarySoft

    val OnPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.onPrimary

    val PrimaryContainer: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.primaryContainer

    val OnPrimaryContainer: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.onPrimaryContainer

    val SecondarySurface: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.secondarySurface

    val Background: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.background

    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.surface

    val Outline: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.outline

    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.textPrimary

    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.textSecondary

    val StarRating: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.starRating

    val OnPrimaryMuted: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.onPrimaryMuted

    val IncomingBubbleSurface: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.incomingBubbleSurface

    val SuccessText: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.successText

    val SuccessContainer: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.successContainer

    val WarningText: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.warningText

    val WarningContainer: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.warningContainer

    val DangerText: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.dangerText

    val DangerContainer: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.dangerContainer

    val DarkScrim: Color
        @Composable @ReadOnlyComposable get() = LocalCloBlePalette.current.darkScrim
}
