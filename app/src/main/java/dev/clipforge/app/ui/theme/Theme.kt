package dev.clipforge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "Special" by design: a dark, editing-suite palette with one loud accent —
// this is a clip-cutting tool, it should feel like one, not like a generic
// Material sample app.
private val Ignition = Color(0xFFFF5A1F)
private val Void = Color(0xFF0E0E12)
private val SurfaceDark = Color(0xFF1A1A21)

private val ClipForgeDarkColors = darkColorScheme(
    primary = Ignition,
    onPrimary = Color.White,
    background = Void,
    onBackground = Color(0xFFEDEDED),
    surface = SurfaceDark,
    onSurface = Color(0xFFEDEDED),
    secondary = Color(0xFF53D1C3),
)

private val ClipForgeLightColors = lightColorScheme(
    primary = Ignition,
    onPrimary = Color.White,
    secondary = Color(0xFF1C8A7D),
)

@Composable
fun ClipForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ClipForgeDarkColors else ClipForgeLightColors,
        content = content,
    )
}
