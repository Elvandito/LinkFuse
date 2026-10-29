package dev.linkfuse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Abyss = Color(0xFF0A1220)
private val Panel = Color(0xFF111F33)
private val Edge = Color(0xFF1D3049)
private val Ink = Color(0xFFE9F1F9)
val Dim = Color(0xFF93A7BD)
val Wifi = Color(0xFF3CC9EA)
val Cell = Color(0xFFF5A524)
val Wire = Color(0xFF3DDC97)
val Fuse = Color(0xFFFF7A59)
val Alert = Color(0xFFFF5D6C)
val Okay = Color(0xFF4ADE80)

private val scheme = darkColorScheme(
    primary = Fuse,
    onPrimary = Color(0xFF1A0E08),
    secondary = Wifi,
    tertiary = Wire,
    background = Abyss,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    onSurfaceVariant = Dim,
    outline = Edge,
    error = Alert,
)

@Composable
fun LinkFuseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
