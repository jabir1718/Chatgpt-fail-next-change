package studio.viso.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VisoColors = darkColorScheme(
    primary = Color(0xFF9B7BFF),
    secondary = Color(0xFF34D8C5),
    tertiary = Color(0xFFFF6FAE),
    background = Color(0xFF080A12),
    surface = Color(0xFF111522),
    surfaceVariant = Color(0xFF1A2030),
    onPrimary = Color.White,
    onSecondary = Color(0xFF04110F),
    onBackground = Color(0xFFF5F6FC),
    onSurface = Color(0xFFF5F6FC),
    onSurfaceVariant = Color(0xFF9AA4B8)
)

@Composable
fun VisoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VisoColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
