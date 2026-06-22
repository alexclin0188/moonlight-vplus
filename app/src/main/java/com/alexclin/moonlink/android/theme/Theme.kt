package com.alexclin.moonlink.android.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary              = md_primary_dark,
    onPrimary            = md_onPrimary_dark,
    primaryContainer     = md_primaryContainer_dark,
    onPrimaryContainer   = md_onPrimaryContainer_dark,
    secondary            = md_secondary_dark,
    onSecondary          = md_onSecondary_dark,
    secondaryContainer   = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    tertiary             = md_tertiary_dark,
    onTertiary           = md_onTertiary_dark,
    background           = md_background_dark,
    onBackground         = md_onBackground_dark,
    surface              = md_surface_dark,
    onSurface            = md_onSurface_dark,
    surfaceVariant       = md_surfaceVariant_dark,
    onSurfaceVariant     = md_onSurfaceVariant_dark,
    outline              = md_outline_dark,
    outlineVariant       = md_outlineVariant_dark,
    error                = md_error_dark,
    onError              = md_onError_dark,
)

private val LightColorScheme = lightColorScheme(
    primary              = md_primary_light,
    onPrimary            = md_onPrimary_light,
    primaryContainer     = md_primaryContainer_light,
    onPrimaryContainer   = md_onPrimaryContainer_light,
    secondary            = md_secondary_light,
    onSecondary          = md_onSecondary_light,
    secondaryContainer   = md_secondaryContainer_light,
    onSecondaryContainer = md_onSecondaryContainer_light,
    tertiary             = md_tertiary_light,
    onTertiary           = md_onTertiary_light,
    background           = md_background_light,
    onBackground         = md_onBackground_light,
    surface              = md_surface_light,
    onSurface            = md_onSurface_light,
    surfaceVariant       = md_surfaceVariant_light,
    onSurfaceVariant     = md_onSurfaceVariant_light,
    outline              = md_outline_light,
    outlineVariant       = md_outlineVariant_light,
    error                = md_error_light,
    onError              = md_onError_light,
)

@Composable
fun MoonLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = MoonLinkTypography,
        content     = content,
    )
}
