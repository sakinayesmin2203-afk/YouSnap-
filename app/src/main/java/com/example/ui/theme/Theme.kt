package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.sp

val LocalFontScale = compositionLocalOf { 1.0f }

private val DarkColorScheme = darkColorScheme(
    primary = OnDarkPrimary,
    onPrimary = DarkBackground,
    secondary = OnDarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = OnDarkPrimary,
    onSurface = OnDarkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    error = LogOutRed
)

private val LightColorScheme = lightColorScheme(
    primary = OnLightPrimary,
    onPrimary = LightBackground,
    secondary = OnLightSecondary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = OnLightPrimary,
    onSurface = OnLightPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSecondary,
    error = LogOutRed
)

@Composable
fun YouSnapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val baseTypography = Typography
    val scaledTypography = androidx.compose.material3.Typography(
        bodyLarge = baseTypography.bodyLarge.copy(
            fontSize = (baseTypography.bodyLarge.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.bodyLarge.lineHeight.value * fontScale).sp
        ),
        bodyMedium = baseTypography.bodyMedium.copy(
            fontSize = (baseTypography.bodyMedium.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.bodyMedium.lineHeight.value * fontScale).sp
        ),
        bodySmall = baseTypography.bodySmall.copy(
            fontSize = (baseTypography.bodySmall.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.bodySmall.lineHeight.value * fontScale).sp
        ),
        titleLarge = baseTypography.titleLarge.copy(
            fontSize = (baseTypography.titleLarge.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.titleLarge.lineHeight.value * fontScale).sp
        ),
        titleMedium = baseTypography.titleMedium.copy(
            fontSize = (baseTypography.titleMedium.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.titleMedium.lineHeight.value * fontScale).sp
        ),
        titleSmall = baseTypography.titleSmall.copy(
            fontSize = (baseTypography.titleSmall.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.titleSmall.lineHeight.value * fontScale).sp
        ),
        labelLarge = baseTypography.labelLarge.copy(
            fontSize = (baseTypography.labelLarge.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.labelLarge.lineHeight.value * fontScale).sp
        ),
        labelMedium = baseTypography.labelMedium.copy(
            fontSize = (baseTypography.labelMedium.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.labelMedium.lineHeight.value * fontScale).sp
        ),
        labelSmall = baseTypography.labelSmall.copy(
            fontSize = (baseTypography.labelSmall.fontSize.value * fontScale).sp,
            lineHeight = (baseTypography.labelSmall.lineHeight.value * fontScale).sp
        )
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalFontScale provides fontScale
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
