package com.example.spendtracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightBlueColors = lightColorScheme(
    primary = Color(0xFF1769AA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBFF),
    onPrimaryContainer = Color(0xFF082F49),
    secondary = Color(0xFF3F6F8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEEFF),
    onSecondaryContainer = Color(0xFF17384D),
    background = Color(0xFFF5FAFF),
    onBackground = Color(0xFF17232C),
    surface = Color(0xFFF8FBFF),
    onSurface = Color(0xFF17232C),
    surfaceVariant = Color(0xFFE2EAF1),
    outline = Color(0xFF71808D)
)

@Composable
fun ExpenseTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightBlueColors, content = content)
}
