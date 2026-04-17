package br.tec.wrcoder.meucondominio.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF38A169),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF002B63),
    primaryContainer = Color(0xFF003F8F),
    secondary = Color(0xFF68D391),
    background = Color(0xFF0F1115),
    surface = Color(0xFF1A1D22),
)

@Composable
fun AppTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
