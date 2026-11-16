package com.catetin.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = PineDeep,
    secondary = Amber,
    onSecondary = Color(0xFF3A2600),
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Color(0xFF2D1C00),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFCF6),
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE7DD),
    onSurfaceVariant = InkMuted,
    tertiary = Sky,
    error = Coral,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD8C8),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1B4E45),
    onPrimaryContainer = Color(0xFFB4F1E2),
    secondary = Color(0xFFFFC96B),
    onSecondary = Color(0xFF3E2A00),
    secondaryContainer = Color(0xFF5A4200),
    onSecondaryContainer = Color(0xFFFFE1A0),
    background = PaperDark,
    onBackground = Color(0xFFE4ECE8),
    surface = Color(0xFF18221F),
    onSurface = Color(0xFFE4ECE8),
    surfaceVariant = Color(0xFF2A3532),
    onSurfaceVariant = Color(0xFFB9C7C2),
    tertiary = Color(0xFF9DCBE8),
    error = Color(0xFFFFB4A8),
)

@Composable
fun CatetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = CatetTypography,
        shapes = CatetShapes,
        content = content,
    )
}
