package com.returnables.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LedgerColors = lightColorScheme(
    primary = Color(0xFF2347DA), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E7FF), onPrimaryContainer = Color(0xFF17234A),
    secondary = Color(0xFF535A8B), secondaryContainer = Color(0xFFEAE8FF),
    background = Color(0xFFF2F2FF), onBackground = Color(0xFF17234A),
    surface = Color.White, onSurface = Color(0xFF17234A),
    onSurfaceVariant = Color(0xFF59617A), outline = Color(0xFF747C94),
    error = Color(0xFFA52C35), errorContainer = Color(0xFFFFE8E9),
)
private val LedgerType = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 39.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 33.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)
@Composable
fun ReturnablesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LedgerColors, typography = LedgerType, content = content)
}
