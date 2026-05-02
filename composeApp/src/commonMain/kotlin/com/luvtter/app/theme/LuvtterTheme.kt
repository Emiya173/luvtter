package com.luvtter.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import luvtter.composeapp.generated.resources.*
import org.jetbrains.compose.resources.Font

/**
 * 顶层主题 —— 把 webui 的纸 / 墨 / 火漆 token 一次性铺到整个 App。
 *
 * 1. `LuvtterTokens` 经 `LocalLuvtterTokens` 暴露给所有 composable;新代码通过
 *    `LuvtterTheme.tokens` / `LuvtterTheme.colors` / 直接 `LocalLuvtterTokens.current` 取色取字距。
 * 2. Material3 `colorScheme` 同步映射:`background`/`surface` 走 paper 系,`primary` 走火漆,
 *    `secondary` 走邮戳青;现有屏在不改代码的前提下只改外观。
 * 3. 现存代码里的 `MaterialTheme.typography.*` 不动,但 body 默认字体已经被换成衬线,字重也对齐。
 */
@Composable
fun LuvtterTheme(content: @Composable () -> Unit) {
    val colors = remember { LuvtterColors() }
    val spacing = remember { LuvtterSpacing() }
    val fonts = rememberLuvtterFontFamilies()
    val typo = remember(fonts, colors.ink) { buildTypography(fonts, colors.ink) }
    val tokens = remember(colors, spacing, fonts, typo) {
        LuvtterTokens(colors, spacing, fonts, typo)
    }

    val materialColors = remember(colors) {
        lightColorScheme(
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paperRaised,
            onSurface = colors.ink,
            surfaceVariant = colors.paperDeep,
            onSurfaceVariant = colors.inkSoft,
            primary = colors.seal,
            onPrimary = colors.paperRaised,
            primaryContainer = colors.sealGlow,
            onPrimaryContainer = colors.paperRaised,
            secondary = colors.stampInk,
            onSecondary = colors.paperRaised,
            outline = colors.rule,
            outlineVariant = colors.ruleSoft,
            error = colors.seal,
            onError = colors.paperRaised,
        )
    }

    val materialTypo = remember(typo) {
        Typography(
            displayLarge = typo.title.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified).merge(typo.title),
            headlineMedium = typo.title,
            titleLarge = typo.title,
            titleMedium = typo.title.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified).merge(typo.title),
            bodyLarge = typo.body,
            bodyMedium = typo.body,
            bodySmall = typo.caption,
            labelSmall = typo.meta,
            labelMedium = typo.meta,
            labelLarge = typo.meta,
        )
    }

    val shapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp),
        )
    }

    CompositionLocalProvider(LocalLuvtterTokens provides tokens) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = materialTypo,
            shapes = shapes,
            content = content,
        )
    }
}

@Composable
private fun rememberLuvtterFontFamilies(): LuvtterFontFamilies {
    val serifZh = FontFamily(
        Font(Res.font.notoserifsc_light, weight = FontWeight.Light),
        Font(Res.font.notoserifsc_regular, weight = FontWeight.Normal),
        Font(Res.font.notoserifsc_medium, weight = FontWeight.Medium),
        Font(Res.font.notoserifsc_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.notoserifsc_bold, weight = FontWeight.Bold),
    )
    val serifEn = FontFamily(
        Font(Res.font.cormorantgaramond_regular, weight = FontWeight.Normal),
        Font(Res.font.cormorantgaramond_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
        Font(Res.font.cormorantgaramond_medium, weight = FontWeight.Medium),
        Font(Res.font.cormorantgaramond_semibold, weight = FontWeight.SemiBold),
    )
    val handZh = FontFamily(Font(Res.font.mashanzheng_regular))
    val handLoose = FontFamily(Font(Res.font.liujianmaocao_regular))
    val mono = FontFamily(
        Font(Res.font.jetbrainsmono_regular, weight = FontWeight.Normal),
        Font(Res.font.jetbrainsmono_medium, weight = FontWeight.Medium),
    )
    return LuvtterFontFamilies(
        serifZh = serifZh,
        serifEn = serifEn,
        handZh = handZh,
        handLoose = handLoose,
        mono = mono,
    )
}

object LuvtterTheme {
    val tokens: LuvtterTokens
        @Composable get() = LocalLuvtterTokens.current
    val colors: LuvtterColors
        @Composable get() = LocalLuvtterTokens.current.colors
    val typography: LuvtterTypography
        @Composable get() = LocalLuvtterTokens.current.typography
    val spacing: LuvtterSpacing
        @Composable get() = LocalLuvtterTokens.current.spacing
}
