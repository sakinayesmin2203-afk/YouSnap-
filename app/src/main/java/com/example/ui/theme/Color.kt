package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Instagram Brand Colors
val InstaPink = Color(0xFFE1306C)
val InstaPurple = Color(0xFFC13584)
val InstaOrange = Color(0xFFF56040)
val InstaYellow = Color(0xFFFCAF45)
val InstaBlue = Color(0xFF405DE6)

// Instagram Gradient Brush for buttons, borders, highlights
val InstaGradient = Brush.linearGradient(
    colors = listOf(InstaYellow, InstaOrange, InstaPink, InstaPurple, InstaBlue)
)

// Dark Theme Colors
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1F1F1F)
val OnDarkPrimary = Color(0xFFFFFFFF)
val OnDarkSecondary = Color(0xFFB0B0B0)

// Light Theme Colors (Warm Cream / Light Beige Aesthetic)
val LightBackground = Color(0xFFFAF5ED)
val LightSurface = Color(0xFFF5EFEB)
val LightSurfaceVariant = Color(0xFFEFE8DD)
val OnLightPrimary = Color(0xFF2C221E)
val OnLightSecondary = Color(0xFF7A6E67)

// Warm Cream Custom Palette
val WarmCreamBackground = Color(0xFFFAF5ED)
val WarmCreamSurface = Color(0xFFF5EFEB)
val WarmCreamTextPrimary = Color(0xFF2C221E)
val WarmCreamTextSecondary = Color(0xFF7A6E67)
val WarmCreamAccent = Color(0xFFC48B5E)

// Theme Colors compatible with M3
val InstaPrimary = Color(0xFFE1306C)
val InstaSecondary = Color(0xFF833AB4)
val InstaTertiary = Color(0xFFFCAF45)
val LogOutRed = Color(0xFFED4956)
