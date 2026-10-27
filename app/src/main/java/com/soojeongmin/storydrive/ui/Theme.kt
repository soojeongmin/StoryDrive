package com.soojeongmin.storydrive.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soojeongmin.storydrive.R

@Immutable
data class Tokens(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val text4: Color,
    val divider1: Color,
    val divider2: Color,
    val divider3: Color,
    val btnBasic: Color,
    val btnBasicPressed: Color,
    val btnFilled: Color,
    val btnFilledPressed: Color,
    val onFilled: Color,
    val fieldBg: Color,
    val fieldFocus: Color,
    val active: Color,
    val danger: Color,
    val info: Color,
    val shadow: Color,
)

private val Ink = Color(0xFF131417)

private val Light = Tokens(
    bg = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDEEF2),
    text = Ink,
    text2 = Ink.copy(alpha = 0.84f),
    text3 = Ink.copy(alpha = 0.64f),
    text4 = Ink.copy(alpha = 0.32f),
    divider1 = Ink.copy(alpha = 0.05f),
    divider2 = Ink.copy(alpha = 0.10f),
    divider3 = Ink.copy(alpha = 0.20f),
    btnBasic = Ink.copy(alpha = 0.05f),
    btnBasicPressed = Ink.copy(alpha = 0.10f),
    btnFilled = Color(0xFF44464E),
    btnFilledPressed = Color(0xFF313236),
    onFilled = Color(0xFFFFFFFF),
    fieldBg = Color(0xFFFFFFFF),
    fieldFocus = Color(0xFF313236),
    active = Color(0xFF02C265),
    danger = Color(0xFFF62E24),
    info = Color(0xFF0064FF),
    shadow = Ink.copy(alpha = 0.08f),
)

private val Dark = Tokens(
    bg = Color(0xFF1D1E21),
    surface = Color(0xFF1D1E21),
    surfaceHigh = Color(0xFF313236),
    text = Color.White,
    text2 = Color.White.copy(alpha = 0.84f),
    text3 = Color.White.copy(alpha = 0.64f),
    text4 = Color.White.copy(alpha = 0.32f),
    divider1 = Color.White.copy(alpha = 0.05f),
    divider2 = Color.White.copy(alpha = 0.10f),
    divider3 = Color.White.copy(alpha = 0.20f),
    btnBasic = Color.White.copy(alpha = 0.05f),
    btnBasicPressed = Color.White.copy(alpha = 0.10f),
    btnFilled = Color(0xFFE0E1E6),
    btnFilledPressed = Color(0xFFC7C9CE),
    onFilled = Ink,
    fieldBg = Ink,
    fieldFocus = Color(0xFFEDEEF2),
    active = Color(0xFF00E074),
    danger = Color(0xFFFF4339),
    info = Color(0xFF0082FF),
    shadow = Color.Transparent,
)

val LocalTokens = staticCompositionLocalOf { Light }

object Space {
    val xs = 4.dp
    val s = 8.dp
    val sm = 12.dp
    val m = 16.dp
    val ml = 20.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 40.dp
    val xxxl = 48.dp
    val appBar = 60.dp
    val header = 80.dp
    val margin = 40.dp
}

object Radius {
    val s = RoundedCornerShape(16.dp)
    val icon = RoundedCornerShape(20.dp)
    val m = RoundedCornerShape(24.dp)
    val l = RoundedCornerShape(32.dp)
}

object Motion {
    const val FAST_MS = 150
    val easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

@OptIn(ExperimentalTextApi::class)
private fun asta(weight: Int) = Font(R.font.asta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val AstaSans = FontFamily(asta(400), asta(700), asta(800))

private fun style(size: Int, line: Int, weight: Int) =
    TextStyle(fontFamily = AstaSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), letterSpacing = 0.sp)

object Type {
    val headline = style(48, 64, 800)
    val title = style(40, 52, 700)
    val titleM = style(36, 44, 700)
    val titleS = style(32, 40, 700)
    val bodyL = style(30, 38, 400)
    val bodyLBold = style(30, 38, 700)
    val bodyM = style(28, 36, 400)
    val bodyS = style(26, 34, 400)
    val bodySBold = style(26, 34, 700)
    val label = style(24, 28, 400)
    val buttonL = style(32, 40, 700)
    val buttonS = style(26, 34, 700)
    val field = style(36, 44, 400)
}

@Composable
fun StoryTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val tokens = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = scheme.copy(
            primary = tokens.btnFilled,
            onPrimary = tokens.onFilled,
            background = tokens.bg,
            onBackground = tokens.text,
            surface = tokens.surface,
            onSurface = tokens.text,
            outline = tokens.divider3,
        ),
    ) {
        CompositionLocalProvider(LocalTokens provides tokens, content = content)
    }
}
