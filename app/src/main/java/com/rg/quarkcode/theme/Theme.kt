package com.rg.quarkcode.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

// Quark identity: deep "Void" dark + warm "Paper" light.
// Deliberately NOT AndCode's flat dev-dark: indigo pulse accent + larger radii.
private val VoidScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = Color(0xFF2A3B5C),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB39DDB),
    secondaryContainer = Color(0xFF2A2438),
    onSecondaryContainer = Color(0xFFE2D9F5),
    tertiary = Color(0xFF7DD6C2),
    tertiaryContainer = Color(0xFF1E3A34),
    onTertiaryContainer = Color(0xFFBFE9DF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF101014),
    surfaceVariant = Color(0xFF1C1C22),
    surfaceContainerLowest = Color(0xFF0B0B0E),
    surfaceContainerLow = Color(0xFF141419),
    surfaceContainer = Color(0xFF1C1C22),
    surfaceContainerHigh = Color(0xFF23232B),
    surfaceContainerHighest = Color(0xFF2C2C36),
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF26262E),
    background = Color(0xFF0B0B0E)
)

private val PaperScheme = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF1A2B5C),
    secondary = Color(0xFF6C4CF1),
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF2A1E5C),
    tertiary = Color(0xFF0E7C66),
    tertiaryContainer = Color(0xFFBFE9DF),
    onTertiaryContainer = Color(0xFF08382E),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFFFBF2),
    surfaceVariant = Color(0xFFF1EADF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1E6),
    surfaceContainer = Color(0xFFF1EADF),
    surfaceContainerHigh = Color(0xFFEAE1D3),
    surfaceContainerHighest = Color(0xFFE3D8C5),
    outline = Color(0xFFB8AE9D),
    outlineVariant = Color(0xFFD8CFBE),
    background = Color(0xFFFFFBF2)
)

@Composable
fun QuarkTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark ?: systemDark) VoidScheme else PaperScheme,
        content = content
    )
}
