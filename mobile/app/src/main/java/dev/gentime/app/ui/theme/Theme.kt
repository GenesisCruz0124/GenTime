package dev.gentime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Minimal, professional palette: one blue accent over a cool-neutral slate
// scale. Every Material role is set explicitly so nothing falls back to the
// default purple.
internal val Blue600 = Color(0xFF2563EB)
internal val Blue100 = Color(0xFFDBEAFE)
internal val Blue900 = Color(0xFF1E3A8A)
internal val Slate900 = Color(0xFF0F172A)
internal val Slate600 = Color(0xFF475569)
internal val Slate500 = Color(0xFF64748B)
internal val Slate300 = Color(0xFFCBD5E1)
internal val Slate200 = Color(0xFFE2E8F0)
internal val Slate100 = Color(0xFFF1F5F9)
internal val Slate50 = Color(0xFFF8FAFC)
internal val Red600 = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Slate600,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = Blue600,
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate300,
    outlineVariant = Slate200,
    error = Red600,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Blue900,
    onPrimaryContainer = Blue100,
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0B1220),
    secondaryContainer = Color(0xFF1F2937),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1F2937),
    error = Color(0xFFF87171),
    onError = Color(0xFF0B1220),
)

@Composable
fun GenTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
