package dev.gentime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2563EB)
private val BrandDark = Color(0xFF1E40AF)

private val LightColors = lightColorScheme(primary = Brand, secondary = BrandDark)
private val DarkColors = darkColorScheme(primary = Brand, secondary = BrandDark)

@Composable
fun GenTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
