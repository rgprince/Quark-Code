package com.quark.agent.theme

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
    secondary = Color(0xFFB39DDB),
    surface = Color(0xFF101014),
    surfaceVariant = Color(0xFF1C1C22),
    background = Color(0xFF0B0B0E)
)

private val PaperScheme = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6C4CF1),
    surface = Color(0xFFFFFBF2),
    surfaceVariant = Color(0xFFF1EADF),
    background = Color(0xFFFFFBF2)
)

@Composable
fun QuarkTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) VoidScheme else PaperScheme,
        content = content
    )
}
