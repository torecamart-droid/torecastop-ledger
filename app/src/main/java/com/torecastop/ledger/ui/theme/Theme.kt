package com.torecastop.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Custom fonts (Nunito / Inter / Space Mono) are deferred to Phase 2, where the
// font files will be bundled under res/font. For now the default typography is
// used so the project builds without binary assets.
private val AppTypography = Typography()

private val LightColors = lightColorScheme(
    primary = Coral,
    secondary = Teal,
    tertiary = Bronze,
    background = Cloud,
    surface = Cloud,
    surfaceVariant = Mist,
    onPrimary = Cloud,
    onBackground = Ink,
    onSurface = Ink,
    error = ErrorRed,
    onError = Cloud
)

// Dark mode keeps the full brand identity (warm Ink surfaces, Coral/Teal/Bronze
// accents) rather than accepting Material's generic dark defaults. (v1.3)
private val DarkColors = darkColorScheme(
    primary = Coral,
    secondary = Teal,
    tertiary = Bronze,
    background = InkBackground,
    surface = InkSurface,
    surfaceVariant = InkSurfaceVariant,
    onPrimary = Ink,
    onBackground = CloudDim,
    onSurface = CloudDim,
    onSurfaceVariant = CloudMuted,
    error = ErrorRedLight,
    onError = Ink
)

@Composable
fun TorecaStopLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
