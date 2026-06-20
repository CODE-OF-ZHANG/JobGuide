package com.zhiyin.jobguide.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FutureColorScheme = lightColorScheme(
    primary = NeonCyan,
    onPrimary = Void,
    secondary = IonLime,
    onSecondary = Void,
    tertiary = SignalMagenta,
    background = Void,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelLift,
    onSurfaceVariant = TextMuted,
    outline = CircuitLine,
    error = AlertCoral
)

@Composable
fun JobGuideTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FutureColorScheme,
        typography = Typography,
        content = content
    )
}
