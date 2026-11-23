package com.vpnproject.wgclient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Blue40,
    background = Slate99,
    surface = Slate99
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    background = Slate10,
    surface = Slate10
)

private val AppShapes = Shapes(
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

/** Container + content color pair for the big connect button. */
data class ButtonColors(val container: Color, val content: Color)

@Composable
fun WgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/** grey = disconnected, amber = connecting, green = connected */
@Composable
fun connectButtonColors(state: com.vpnproject.wgclient.vpn.ConnectionState): ButtonColors {
    val dark = isSystemInDarkTheme()
    return when (state) {
        is com.vpnproject.wgclient.vpn.ConnectionState.Disconnected ->
            ButtonColors(if (dark) StateGreyDark else StateGrey, if (dark) Color.Black else Color.White)
        is com.vpnproject.wgclient.vpn.ConnectionState.Connecting ->
            ButtonColors(StateAmber, Color.Black)
        is com.vpnproject.wgclient.vpn.ConnectionState.Connected ->
            ButtonColors(if (dark) StateGreenDark else StateGreen, if (dark) Color.Black else Color.White)
    }
}
