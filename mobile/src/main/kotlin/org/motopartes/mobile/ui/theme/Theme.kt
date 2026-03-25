package org.motopartes.mobile.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFFFB74D)
private val AmberDark = Color(0xFFFFA726)
private val AmberContainer = Color(0xFF4E3A20)
private val OnAmberContainer = Color(0xFFFFDDB3)
private val Surface = Color(0xFF1C1B1F)
private val SurfaceContainer = Color(0xFF252329)
private val Background = Color(0xFF141218)

private val MotopartesColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    primaryContainer = AmberContainer,
    onPrimaryContainer = OnAmberContainer,
    secondary = AmberDark,
    surface = Surface,
    surfaceContainer = SurfaceContainer,
    background = Background,
)

@Composable
fun MotopartesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MotopartesColorScheme,
        content = content
    )
}
