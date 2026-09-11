package com.rg.quarkcode.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rg.quarkcode.backend.ThemeMode

// Quark identity: deep "Void" dark + warm "Paper" light.
// Expressive evolution: larger radii (sheets 28dp, composer 32dp),
// tonal elevation, dynamic-color option on API 31+.
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

// Expressive shape scale: M12 cards, L16 bubbles/images, L-inc 20 drawer,
// XL28 dialogs/sheets, XXL32 composer.
private val QuarkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun QuarkTheme(
    dark: Boolean? = null,
    mode: ThemeMode? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDynamic = mode == ThemeMode.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && (dark ?: systemDark) -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark ?: systemDark -> VoidScheme
        else -> PaperScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = QuarkShapes,
        content = content
    )
}
