package com.puretv.store.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val StoreDarkColors = darkColorScheme(
    primary = Color(0xFF5CC8FF),
    onPrimary = Color(0xFF00253A),
    primaryContainer = Color(0xFF0B3A52),
    onPrimaryContainer = Color(0xFFC9E9FF),
    secondary = Color(0xFF9FB6C9),
    onSecondary = Color(0xFF0C1B26),
    secondaryContainer = Color(0xFF243746),
    onSecondaryContainer = Color(0xFFD6E6F2),
    tertiary = Color(0xFF7BE0C0),
    onTertiary = Color(0xFF00382A),
    background = Color(0xFF070B11),
    onBackground = Color(0xFFE8F1F8),
    surface = Color(0xFF0C131C),
    onSurface = Color(0xFFE6EFF6),
    surfaceVariant = Color(0xFF16212E),
    onSurfaceVariant = Color(0xFFA3B4C2),
    surfaceContainerLowest = Color(0xFF05090E),
    surfaceContainerLow = Color(0xFF0A1119),
    surfaceContainer = Color(0xFF101925),
    surfaceContainerHigh = Color(0xFF16212E),
    surfaceContainerHighest = Color(0xFF1D2A38),
    outline = Color(0xFF35485A),
    outlineVariant = Color(0xFF22303E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3A0906),
    errorContainer = Color(0xFF561716),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val TvTypography = Typography(
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 38.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Colours that are not part of the M3 roles but define the store look. */
object StorePalette {
    val heroGradient = Brush.horizontalGradient(
        listOf(Color(0xFF0B3A52), Color(0xFF16212E), Color(0xFF101925)),
    )

    val tileGradients: List<List<Color>> = listOf(
        listOf(Color(0xFF2F80ED), Color(0xFF56CCF2)),
        listOf(Color(0xFF8E54E9), Color(0xFF4776E6)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFEB5757), Color(0xFFF2994A)),
        listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
        listOf(Color(0xFFDA22FF), Color(0xFF9733EE)),
    )
}

@Composable
fun PureTVStoreTheme(content: @Composable () -> Unit) {
    // A television is viewed in a dark room; always use the dark palette so a
    // device reporting a light system theme never flashes a white store.
    MaterialTheme(
        colorScheme = StoreDarkColors,
        typography = TvTypography,
        content = content,
    )
}
