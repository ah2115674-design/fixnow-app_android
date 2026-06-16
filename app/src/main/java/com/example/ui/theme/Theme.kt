package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandEmerald,
    secondary = LightGreenAccent,
    tertiary = AccentAmber,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = BrandCharcoal,
    onTertiary = BrandCharcoal,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardSlate,
    onSurfaceVariant = TextSecDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandEmerald,
    secondary = BrandCharcoal,
    tertiary = AccentAmber,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = BrandCharcoal,
    onTertiary = BrandCharcoal,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = CardSlate,
    onSurfaceVariant = TextSecLight

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We disable dynamic color on newer systems to ensure our highly custom
    // Pakistan Emerald Green branding is visible, consistent, and recognizable.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
