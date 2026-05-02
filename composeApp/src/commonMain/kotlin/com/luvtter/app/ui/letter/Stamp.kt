package com.luvtter.app.ui.letter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme

/**
 * 邮票。webui/components.jsx Stamp。
 *
 * 长宽比固定 4:5(40x50)。结构:浅色票身 + 内框 + 抽象插画 + LUVTTER / name / name_en + 四边白色齿孔。
 */
@Composable
fun Stamp(
    spec: StampSpec,
    size: Dp = 58.dp,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    val measurer = rememberTextMeasurer()
    val h = size * 1.25f
    val wDp = size.value
    val hDp = wDp * 1.25f

    Canvas(modifier = modifier.size(size, h)) {
        val pxW = this.size.width
        val pxH = this.size.height
        // 票身底色 —— 取 accent 与 paper 之间的浅版
        val body = blend(spec.accent, tokens.colors.paperRaised, 0.85f)

        // 主矩形(留 1f 边给齿孔)
        drawRect(color = body, topLeft = Offset(1f, 1f), size = Size(pxW - 2f, pxH - 2f))
        // 外描边
        drawRect(color = spec.accent, topLeft = Offset(1f, 1f), size = Size(pxW - 2f, pxH - 2f), style = Stroke(width = 0.5f))

        // 内框
        val ix = pxW * 0.0875f
        val iy = pxH * 0.07f
        drawRect(
            color = spec.accent.copy(alpha = 0.6f),
            topLeft = Offset(ix, iy),
            size = Size(pxW - 2 * ix, pxH * 0.86f - iy),
            style = Stroke(width = 0.5f),
        )

        // 插画区(占内框上 50%,留下半给三行文字)
        val illoTop = pxH * 0.10f
        val illoBottom = pxH * 0.55f
        val illoH = illoBottom - illoTop
        drawRect(
            color = spec.accent.copy(alpha = 0.18f),
            topLeft = Offset(pxW * 0.15f, illoTop),
            size = Size(pxW * 0.7f, illoH),
        )
        drawCircle(
            color = spec.accent.copy(alpha = 0.5f),
            radius = pxW * 0.12f,
            center = Offset(pxW * 0.5f, illoTop + illoH * 0.42f),
        )
        val mt = Path().apply {
            val baseY = illoTop + illoH * 0.85f
            moveTo(pxW * 0.15f, baseY)
            quadraticTo(pxW * 0.32f, baseY - illoH * 0.35f, pxW * 0.5f, baseY - illoH * 0.18f)
            quadraticTo(pxW * 0.7f, baseY - illoH * 0.05f, pxW * 0.85f, baseY - illoH * 0.25f)
            lineTo(pxW * 0.85f, baseY)
            close()
        }
        drawPath(mt, color = spec.accent.copy(alpha = 0.32f))

        // 三行文字 —— 按 measure 出的实际行高顺序堆叠,杜绝重叠
        val brandStyle = TextStyle(
            color = spec.accent,
            fontSize = (hDp * 0.04f).sp,
            fontFamily = tokens.fonts.serifZh,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
        val nameStyle = TextStyle(
            color = spec.accent,
            fontSize = (hDp * 0.075f).sp,
            fontFamily = tokens.fonts.serifZh,
            fontWeight = FontWeight.SemiBold,
        )
        val enStyle = TextStyle(
            color = spec.accent.copy(alpha = 0.85f),
            fontSize = (hDp * 0.032f).sp,
            fontFamily = tokens.fonts.mono,
            letterSpacing = 0.2.sp,
        )
        val brand = measurer.measure("LUVTTER", brandStyle)
        val name = measurer.measure(spec.name, nameStyle)
        val en = measurer.measure(spec.nameEn.uppercase(), enStyle)

        // 文字总高 + 行间 2px → 反推起始 y,使三行整体居中在 [illoBottom, frameBottom] 区间
        val gap = pxH * 0.012f
        val totalH = brand.size.height + gap + name.size.height + gap + en.size.height
        val textZoneTop = pxH * 0.56f
        val textZoneBottom = pxH * 0.86f
        val startY = textZoneTop + ((textZoneBottom - textZoneTop) - totalH) / 2f

        var cursorY = startY
        drawText(brand, topLeft = Offset((pxW - brand.size.width) / 2f, cursorY))
        cursorY += brand.size.height + gap
        drawText(name, topLeft = Offset((pxW - name.size.width) / 2f, cursorY))
        cursorY += name.size.height + gap
        drawText(en, topLeft = Offset((pxW - en.size.width) / 2f, cursorY))

        // 齿孔 —— 四边小圆点(用底纸色覆盖)
        drawPerforations(pxW = pxW, pxH = pxH, paper = tokens.colors.paper)
    }
}

private fun DrawScope.drawPerforations(pxW: Float, pxH: Float, paper: Color) {
    val r = pxW * 0.025f
    val step = pxW * 0.05f
    // 上 + 下
    var x = 0f
    while (x <= pxW) {
        drawCircle(color = paper, radius = r, center = Offset(x, 0f))
        drawCircle(color = paper, radius = r, center = Offset(x, pxH))
        x += step
    }
    // 左 + 右
    var y = 0f
    while (y <= pxH) {
        drawCircle(color = paper, radius = r, center = Offset(0f, y))
        drawCircle(color = paper, radius = r, center = Offset(pxW, y))
        y += step
    }
}

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red * (1 - t) + b.red * t,
    green = a.green * (1 - t) + b.green * t,
    blue = a.blue * (1 - t) + b.blue * t,
    alpha = a.alpha * (1 - t) + b.alpha * t,
)
