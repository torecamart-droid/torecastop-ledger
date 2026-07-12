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
    onSurface = Ink
)

private val DarkColors = darkColorScheme(
    primary = Coral,
    secondary = Teal,
    tertiary = Bronze
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
