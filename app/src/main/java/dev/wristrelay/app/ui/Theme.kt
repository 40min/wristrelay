package dev.wristrelay.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette drawn from the launcher icon: near-black green background with a
// glowing green accent (ic_launcher_background #051309).
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF062811),
    secondary = Color(0xFF86EFAC),
    onSecondary = Color(0xFF062811),
    background = Color(0xFF051309),
    onBackground = Color(0xFFDDF0E2),
    surface = Color(0xFF0C2114),
    onSurface = Color(0xFFDDF0E2),
    surfaceVariant = Color(0xFF143122),
    onSurfaceVariant = Color(0xFFB5D6BF),
    outline = Color(0xFF5F8A6C),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E7A38),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2E9E4F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FBF7),
    onBackground = Color(0xFF102517),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF102517),
    surfaceVariant = Color(0xFFE2F1E6),
    onSurfaceVariant = Color(0xFF3D5C48),
    outline = Color(0xFF7DA589),
)

@Composable
fun WristRelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, content = content)
}
