package com.mua.prayertracker.ui.theme

import android.app.Activity
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = IslamicGreen,
    onPrimary = TextOnGreen,
    primaryContainer = IslamicGreenLight,
    onPrimaryContainer = TextOnGreen,
    secondary = GoldenAccent,
    onSecondary = TextOnGold,
    secondaryContainer = GoldenAccentLight,
    onSecondaryContainer = TextPrimary,
    tertiary = WitrColor,
    onTertiary = TextOnGold,
    background = CreamBackground,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = CreamBackground,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = IslamicGreenLight,
    onPrimary = TextOnGreen,
    primaryContainer = IslamicGreenDark,
    onPrimaryContainer = TextOnGreen,
    secondary = GoldenAccentLight,
    onSecondary = TextPrimary,
    secondaryContainer = GoldenAccentDark,
    onSecondaryContainer = GoldenAccentLight,
    tertiary = WitrColor,
    onTertiary = TextPrimary,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = Color.LightGray,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun PrayerTrackerIslamicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled to maintain our custom Islamic green/gold theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = IslamicGreen.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
