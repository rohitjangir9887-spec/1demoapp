package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AuraDarkColorScheme = darkColorScheme(
    primary = AuraBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF102A4C),
    onPrimaryContainer = AuraCyan,
    secondary = AuraNimGreen,
    onSecondary = Color(0xFF071400),
    secondaryContainer = Color(0xFF182A0E),
    onSecondaryContainer = Color(0xFFA3E635),
    tertiary = AuraAmber,
    onTertiary = Color(0xFF1E1300),
    tertiaryContainer = Color(0xFF2D2007),
    onTertiaryContainer = Color(0xFFFCD34D),
    background = ObsidianBg,
    onBackground = PearlWhite,
    surface = ObsidianSurface,
    onSurface = PearlWhite,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = SlateMist,
    outline = ObsidianCardBorder,
    outlineVariant = SubtleDivider,
    error = AuraCoral,
    onError = Color.White
)

val AuraShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun AuraAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AuraDarkColorScheme,
        typography = AuraTypography,
        shapes = AuraShapes,
        content = content
    )
}
