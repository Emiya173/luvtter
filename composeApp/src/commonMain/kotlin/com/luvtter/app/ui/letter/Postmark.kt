package com.luvtter.app.ui.letter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Postmark(
    city: String = "",
    date: String = "",
    size: Dp = 74.dp,
    rotateDeg: Float = -8f,
    ink: Color = LuvtterTheme.colors.stampInk,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    val measurer = rememberTextMeasurer()
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(2f, 1.5f), 0f) }
    val sizeDp = size.value

    Canvas(modifier = modifier.size(size).rotate(rotateDeg)) {
        val w = this.size.width
        val cx = w / 2
        val cy = w / 2
        val rOuter = w * 0.46f
        val rInner = w * 0.40f
        val rTop = w * 0.34f
        val rBot = w * 0.34f

        drawCircle(
            color = ink.copy(alpha = 0.85f),
            radius = rOuter,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f, pathEffect = dashEffect),
        )
        drawCircle(
            color = ink.copy(alpha = 0.6f),
            radius = rInner,
            center = Offset(cx, cy),
            style = Stroke(width = 0.8f),
        )

        // 上弧 = LUVTTER · 城市,中心 -90° (正上),按字符宽度排布
        drawArcText(
            text = "LUVTTER · $city",
            measurer = measurer,
            cx = cx, cy = cy, radius = rTop,
            centerAngleDeg = -90f,
            fontSize = (sizeDp * 0.04f).sp,
            color = ink,
            family = tokens.fonts.serifZh,
            facingOut = true,
            gapRatio = 0.25f,
        )
        // 下弧 = 日期,中心 90° (正下)
        drawArcText(
            text = date,
            measurer = measurer,
            cx = cx, cy = cy, radius = rBot,
            centerAngleDeg = 90f,
            fontSize = (sizeDp * 0.036f).sp,
            color = ink,
            family = tokens.fonts.mono,
            facingOut = false,
            gapRatio = 0.18f,
        )

        // 中心 "- 寄 -" (短横已并入文字中,不再单独画 tick)
        val center = measurer.measure(
            text = "- 寄 -",
            style = TextStyle(
                fontSize = (sizeDp * 0.11f).sp,
                fontFamily = tokens.fonts.serifZh,
                fontWeight = FontWeight.Medium,
                color = ink,
            ),
        )
        drawText(
            textLayoutResult = center,
            topLeft = Offset(cx - center.size.width / 2f, cy - center.size.height / 2f),
        )
    }
}

/**
 * 沿圆弧居中排文字。每字按 measure 出来的实际宽度推进角度,
 * 而不是把弧段平均切片 —— 这样宽窄字符不会互相挤压。
 */
private fun DrawScope.drawArcText(
    text: String,
    measurer: TextMeasurer,
    cx: Float,
    cy: Float,
    radius: Float,
    centerAngleDeg: Float,
    fontSize: TextUnit,
    color: Color,
    family: FontFamily,
    facingOut: Boolean,
    gapRatio: Float = 0.06f,
) {
    if (text.isEmpty()) return
    val style = TextStyle(fontSize = fontSize, fontFamily = family, color = color)
    val layouts = text.map { measurer.measure(it.toString(), style) }
    val gap = layouts.firstOrNull()?.size?.width?.let { it * gapRatio } ?: 0f
    val totalWidth = layouts.sumOf { it.size.width.toDouble() }.toFloat() + gap * (layouts.size - 1)
    val totalAngle = totalWidth / radius // 弧度
    // facingOut: 角度沿 +X 方向递增对应文本左→右
    // facingIn:  角度沿 -X 方向递增 (从右向左走过下弧)
    val direction = if (facingOut) 1f else -1f
    val centerRad = centerAngleDeg * PI.toFloat() / 180f
    var cursor = centerRad - direction * totalAngle / 2f

    layouts.forEachIndexed { i, layout ->
        val charW = layout.size.width.toFloat()
        val charAngle = charW / radius
        val mid = cursor + direction * charAngle / 2f
        val x = cx + radius * cos(mid)
        val y = cy + radius * sin(mid)
        val tangentDeg = mid * 180f / PI.toFloat() + 90f
        val charRotation = if (facingOut) tangentDeg else tangentDeg + 180f
        rotate(charRotation, pivot = Offset(x, y)) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
            )
        }
        cursor += direction * (charAngle + gap / radius)
    }
}
