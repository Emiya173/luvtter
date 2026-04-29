package com.luvtter.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 直接来自 webui/styles.css :root,严格保持色值不变。
@Immutable
data class LuvtterColors(
    val paper: Color = Color(0xFFF5F1E7),
    val paperRaised: Color = Color(0xFFFAF6EC),
    val paperDeep: Color = Color(0xFFEDE6D4),
    val paperEdge: Color = Color(0xFFD9CFB5),
    val ink: Color = Color(0xFF1A1814),
    val inkSoft: Color = Color(0xFF3A362E),
    val inkFaded: Color = Color(0xFF6A6456),
    val inkGhost: Color = Color(0xFFA89F89),
    val rule: Color = Color(0xFFD4C9AE),
    val ruleSoft: Color = Color(0xFFE4DCC4),
    val seal: Color = Color(0xFF8B2E1F),
    val sealDark: Color = Color(0xFF6A2214),
    val sealGlow: Color = Color(0xFFA8422E),
    val stampInk: Color = Color(0xFF2B3A5C),
)

@Immutable
data class LuvtterSpacing(
    val s1: Dp = 4.dp,
    val s2: Dp = 8.dp,
    val s3: Dp = 12.dp,
    val s4: Dp = 16.dp,
    val s5: Dp = 24.dp,
    val s6: Dp = 32.dp,
    val s7: Dp = 48.dp,
    val s8: Dp = 64.dp,
)

// 字体在 LuvtterTheme 里通过 @Composable Font(Res.font.X) 装配,这里只持引用。
@Immutable
data class LuvtterFontFamilies(
    val serifZh: FontFamily,    // Noto Serif SC
    val serifEn: FontFamily,    // Cormorant Garamond
    val handZh: FontFamily,     // Ma Shan Zheng
    val handLoose: FontFamily,  // Liu Jian Mao Cao(更松散的手写)
    val mono: FontFamily,       // JetBrains Mono
)

@Immutable
data class LuvtterTypography(
    // 标题 —— 中文衬线 + 0.04em 字距(letterSpacing 用 em 不行,Compose 用 sp 等价值)
    val title: TextStyle,
    // 元信息标签 —— 等宽 + UPPERCASE + 大字距,webui 的 .txt-meta
    val meta: TextStyle,
    // 注脚说明 —— 中文衬线 italic
    val caption: TextStyle,
    // 正文阅读 —— 中文衬线,信纸用
    val body: TextStyle,
    // 手写 —— 楷体 / 装饰
    val handwriting: TextStyle,
    // 品牌 luvtter —— 西文衬线 italic
    val brand: TextStyle,
)

internal fun buildTypography(fonts: LuvtterFontFamilies, ink: Color): LuvtterTypography =
    LuvtterTypography(
        title = TextStyle(
            fontFamily = fonts.serifZh,
            fontWeight = FontWeight.Medium,
            fontSize = 26.sp,
            letterSpacing = 1.04.sp,    // ≈ 0.04em @ 26sp
            color = ink,
        ),
        meta = TextStyle(
            fontFamily = fonts.mono,
            fontSize = 10.5.sp,
            letterSpacing = 1.47.sp,    // ≈ 0.14em
            fontWeight = FontWeight.Normal,
        ),
        caption = TextStyle(
            fontFamily = fonts.serifZh,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
        ),
        body = TextStyle(
            fontFamily = fonts.serifZh,
            fontSize = 17.sp,
            lineHeight = 33.sp,
            letterSpacing = 0.34.sp,
            color = ink,
        ),
        handwriting = TextStyle(
            fontFamily = fonts.handZh,
            fontSize = 17.sp,
            lineHeight = 33.sp,
        ),
        brand = TextStyle(
            fontFamily = fonts.serifEn,
            fontStyle = FontStyle.Italic,
            fontSize = 24.sp,
            letterSpacing = 0.48.sp,    // ≈ 0.02em
        ),
    )

@Immutable
class LuvtterTokens(
    val colors: LuvtterColors,
    val spacing: LuvtterSpacing,
    val fonts: LuvtterFontFamilies,
    val typography: LuvtterTypography,
)

val LocalLuvtterTokens = staticCompositionLocalOf<LuvtterTokens> {
    error("LuvtterTokens not provided — wrap your composition in LuvtterTheme {}")
}
