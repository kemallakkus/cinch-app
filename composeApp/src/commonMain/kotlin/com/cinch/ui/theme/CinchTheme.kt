package com.cinch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Para/birikim çağrışımı için koyu yeşil tohum; uyarılar Material'ın hata rengini kullanır.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB1F1CE),
    onPrimaryContainer = Color(0xFF002112),
    secondaryContainer = Color(0xFFD0E8D9),
    onSecondaryContainer = Color(0xFF0B1F15),
    surface = Color(0xFFF6FBF5),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE4EAE4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D5B2),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF125238),
    onPrimaryContainer = Color(0xFFB1F1CE),
    secondaryContainer = Color(0xFF364B3F),
    onSecondaryContainer = Color(0xFFD0E8D9),
    surface = Color(0xFF0F1511),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
)

@Composable
fun CinchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
