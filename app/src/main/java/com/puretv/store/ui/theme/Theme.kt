package com.puretv.store.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1FF),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004D69),
    onPrimaryContainer = Color(0xFFC4E7FF),
    secondary = Color(0xFFB6C7D6),
    onSecondary = Color(0xFF21323D),
    background = Color(0xFF0B141A),
    onBackground = Color(0xFFE1E9EF),
    surface = Color(0xFF111D24),
    onSurface = Color(0xFFE1E9EF),
    surfaceVariant = Color(0xFF1B2A33),
    onSurfaceVariant = Color(0xFFB8C7D1),
    outline = Color(0xFF8595A0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658C),
    onPrimary = Color.White,
    secondary = Color(0xFF4B6070),
    background = Color(0xFFF6FAFD),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3EDF4),
    onSurfaceVariant = Color(0xFF475761),
)

@Composable
fun PureTVStoreTheme(content: @Composable () -> Unit) {
    // Television UIs read better dark; still honour a light system setting.
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
