package com.wmdhs.taskshell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),
    secondary = Color(0xFF546E7A),
    tertiary = Color(0xFFEF6C00)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFFB0BEC5),
    tertiary = Color(0xFFFFB74D)
)

@Composable
fun TaskshellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography,
        content = content
    )
}
