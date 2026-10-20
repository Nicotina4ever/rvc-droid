package com.rvc.droid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------ scheme */

private val DarkScheme = darkColorScheme(
    primary = RVCColors.accent,
    onPrimary = RVCColors.accentContrast,
    primaryContainer = RVCColors.surface3,
    onPrimaryContainer = RVCColors.text,
    secondary = RVCColors.accent2,
    onSecondary = RVCColors.textInverse,
    secondaryContainer = RVCColors.surface2,
    onSecondaryContainer = RVCColors.text,
    tertiary = RVCColors.info,
    onTertiary = RVCColors.textInverse,
    background = RVCColors.bg,
    onBackground = RVCColors.text,
    surface = RVCColors.surface,
    onSurface = RVCColors.text,
    surfaceVariant = RVCColors.surface2,
    onSurfaceVariant = RVCColors.textDim,
    surfaceTint = RVCColors.accent,
    inverseSurface = RVCColors.text,
    inverseOnSurface = RVCColors.bg,
    error = RVCColors.danger,
    onError = RVCColors.textInverse,
    errorContainer = RVCColors.surface2,
    onErrorContainer = RVCColors.danger,
    outline = RVCColors.border,
    outlineVariant = RVCColors.borderStrong,
    scrim = RVCColors.scrim,
)

/* --------------------------------------------------------------- tipografia */

val RvcTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.5.sp,
    ),
)

private val RvcShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(13.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(25.dp),
)

/* ---------------------------------------------------------------- token/UI */

/** Spaziature ufficiali (allineate ai token CSS del desktop). */
object RvcSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 22.dp
    val xxl = 30.dp
    val xxxl = 40.dp
    val huge = 56.dp
}

/** Raggi ufficiali. */
object RvcRadius {
    val xs = 6.dp
    val sm = 9.dp
    val md = 13.dp
    val lg = 18.dp
    val xl = 25.dp
    val pill = 999.dp
}

/** Altezze ricorrenti per avere ritmo verticale uniforme. */
object RvcSizes {
    val topBar = 56.dp
    val bottomBar = 66.dp
    val control = 36.dp
    val controlSmall = 28.dp
    val icon = 20.dp
    val screenPadding = 14.dp
}

/** Colori semantici derivati, comodi nei componenti nuovi. */
object RvcPalette {
    val primary = RVCColors.accent
    val primaryStrong = RVCColors.accentStrong
    val secondary = RVCColors.accent2
    val card = RVCColors.surface
    val cardAlt = RVCColors.surface2
    val text = RVCColors.text
    val muted = RVCColors.textDim
    val faint = RVCColors.textMute
    val divider = RVCColors.border
    val success = RVCColors.success
    val warning = RVCColors.warning
    val danger = RVCColors.danger
    val info = RVCColors.info
    val focusRing: Color = RVCColors.focus
}

@Composable
fun RVCDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = RvcTypography,
        shapes = RvcShapes,
        content = content,
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) = RVCDroidTheme(content)
