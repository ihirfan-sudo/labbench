package com.labbench.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * The palette is taken from the material on the bench rather than from a brand
 * deck: phenol red at physiological pH for the primary accent, the amber of
 * spent, acidified medium for warnings, and trypan blue for secondary actions.
 * A researcher recognises all three on sight, which is the point.
 */
private val PhenolRed = Color(0xFFD93A5F)
private val PhenolRedDim = Color(0xFF9E2743)
private val SpentAmber = Color(0xFFE0A030)
private val TrypanBlue = Color(0xFF3557B7)
private val ViableGreen = Color(0xFF2E9B76)
private val Graphite = Color(0xFF14171C)
private val GraphiteRaised = Color(0xFF1D2229)
private val Bench = Color(0xFFF6F5F2)
private val BenchRaised = Color(0xFFFFFFFF)
private val Ink = Color(0xFF15181D)

private val DarkColors = darkColorScheme(
    primary = PhenolRed,
    onPrimary = Color.White,
    primaryContainer = PhenolRedDim,
    onPrimaryContainer = Color(0xFFFFDCE3),
    secondary = TrypanBlue,
    onSecondary = Color.White,
    tertiary = ViableGreen,
    error = SpentAmber,
    onError = Color(0xFF241A00),
    background = Graphite,
    onBackground = Color(0xFFE6E8EC),
    surface = Graphite,
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = GraphiteRaised,
    onSurfaceVariant = Color(0xFFA9B0BC),
    outline = Color(0xFF3A424F)
)

private val LightColors = lightColorScheme(
    primary = PhenolRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCE3),
    onPrimaryContainer = Color(0xFF54001A),
    secondary = TrypanBlue,
    onSecondary = Color.White,
    tertiary = ViableGreen,
    error = Color(0xFF9A6400),
    background = Bench,
    onBackground = Ink,
    surface = BenchRaised,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEBE6),
    onSurfaceVariant = Color(0xFF56606E),
    outline = Color(0xFFC7C6C1)
)

/**
 * Numbers are the product here, so readouts use a monospaced face with tabular
 * spacing: digits stay in the same column as a countdown ticks, and nothing
 * jitters while you're watching it.
 */
val LabTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
    )
}

val ReadoutStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp
)

val MonoDisplay = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Light,
    fontSize = 64.sp,
    letterSpacing = (-2).sp
)

@Composable
fun LabBenchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = LabTypography, content = content)
}
