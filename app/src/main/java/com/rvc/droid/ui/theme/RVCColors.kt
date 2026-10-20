package com.rvc.droid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette unificata RVC Portable / RVC-Droid.
 *
 * I valori replicano i design token del client desktop (`web/css/tokens.css`,
 * tema scuro navy/indigo): stesse superfici, stesso accento indigo, stessa
 * semantica per success/warning/danger. I nomi storici restano disponibili
 * come alias per non toccare tutto il codice esistente.
 */
object RVCColors {
    // ------------------------------------------------------------ superfici
    val bg = Color(0xFF070B17)
    val bgElevated = Color(0xFF0B1224)
    val surface = Color(0xFF0E1730)
    val surface2 = Color(0xFF131F3D)
    val surface3 = Color(0xFF1A2850)
    val surfaceHover = Color(0xFF1F2F5A)
    val border = Color(0xFF24345F)
    val borderStrong = Color(0xFF334A80)
    val overlay = Color(0xB8040812)

    // Alias storici (la UI esistente li usa largamente).
    val bgDeep = bg
    val bgSurface = surface
    val bgCard = surface2

    // ---------------------------------------------------------------- testo
    val text = Color(0xFFE9EEFC)
    val textDim = Color(0xFFAAB6D6)
    val textMute = Color(0xFF7E8CB2)
    val textInverse = Color(0xFF080D1A)
    val textPrimary = text
    val textSecondary = textDim

    // --------------------------------------------------------------- accento
    val accent = Color(0xFF6D8DFF)
    val accentStrong = Color(0xFF8FA7FF)
    val accent2 = Color(0xFFA86BFF)
    val accentSoft = Color(0x266D8DFF)
    val accentContrast = Color(0xFF071021)
    val focus = Color(0xFF9DB4FF)

    val accentCyan = accent
    val accentCyanDim = Color(0xFF4A5FAE)
    val accentPurple = accent2
    val sliderActive = accent

    // ------------------------------------------------------------ semantica
    val success = Color(0xFF35D6A2)
    val successSoft = Color(0x2635D6A2)
    val warning = Color(0xFFF6B73C)
    val warningSoft = Color(0x29F6B73C)
    val danger = Color(0xFFFF6B81)
    val dangerSoft = Color(0x29FF6B81)
    val info = Color(0xFF5DB8FF)
    val infoSoft = Color(0x265DB8FF)

    val accentGreen = success
    val accentGreenDim = Color(0xFF1EA47C)
    val accentOrange = warning
    val accentOrangeDim = Color(0xFFB9801F)
    val accentRed = danger
    val accentYellow = warning

    // --------------------------------------------------------- waveform/meter
    val wavePlayed = Color(0xFF8FA7FF)
    val waveRest = Color(0xFF2D3D68)
    val meterLow = success
    val meterMid = warning
    val meterHigh = danger
    val meterBg = surface2

    // ------------------------------------------------------------- controlli
    val knobBg = surface3
    val knobBorder = borderStrong
    val sliderTrack = border

    // --------------------------------------------------------- pannelli/misc
    val panelHeader = surface3
    val chipBg = surface3
    val scrim = Color(0x99000000)
    val transparent = Color(0x00000000)

    /** Converte un colore in hover/soft con alpha controllata. */
    fun soft(color: Color, alpha: Float = 0.15f): Color = color.copy(alpha = alpha)
}
