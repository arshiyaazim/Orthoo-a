package com.orthoo.ai.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orthoo.ai.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = EmeraldSecondary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = AmberTertiary,
    onTertiary = Color(0xFF1E1B18),
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF78350F),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color.White,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = EmeraldSecondary,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberTertiary,
    onTertiary = Color(0xFF1E1B18),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1E293B),
    error = ErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

val OrthooShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OrthooTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrthooTypography,
        shapes = OrthooShapes,
        content = content
    )
}
