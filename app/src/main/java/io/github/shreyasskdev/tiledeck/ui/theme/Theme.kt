package io.github.shreyasskdev.tiledeck.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),   // follows system automatically
    dynamicColor: Boolean = true,                 // wallpaper-driven personalization
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme = when {
        // Android 12+ → pull palette from the user's wallpaper
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Fallbacks
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,          // was ExpressiveTypography
        shapes = ExpressiveShapes,
        content = content,
    )
}