package pl.pickpack.panel.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WarehouseNavy = Color(0xFF1A2332)
val WarehouseSlate = Color(0xFF2A3548)
val WarehouseAmber = Color(0xFFF0C75E)
val WarehouseGreen = Color(0xFF3D9B6E)
val WarehouseRed = Color(0xFFC45C5C)
val WarehouseMuted = Color(0xFF8B97A8)
val WarehousePaper = Color(0xFFF5F2EA)

private val ColorScheme = darkColorScheme(
    primary = WarehouseAmber,
    onPrimary = WarehouseNavy,
    secondary = WarehouseGreen,
    background = WarehouseNavy,
    surface = WarehouseSlate,
    onBackground = Color.White,
    onSurface = Color.White,
    error = WarehouseRed,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)

@Composable
fun PickPackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = AppTypography,
        content = content,
    )
}
