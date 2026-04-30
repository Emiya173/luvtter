package com.luvtter.app.ui.letter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * 火漆印。webui/components.jsx WaxSeal。
 * - intact:径向渐变圆 + 内细圈
 * - broken:八角碎裂形(读信后)
 * 中央汉字一律按 SemiBold 衬线放在中心。
 */
@Composable
fun WaxSeal(
    text: String = "寻",
    size: Dp = 52.dp,
    broken: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    val measurer = rememberTextMeasurer()
    val highlight = Color(0xFFC84A35)
    val mid = LuvtterTheme.colors.seal
    val deep = LuvtterTheme.colors.sealDark
    val rim = Color(0xFF5A1C10)
    val embossDark = Color(0xCC3A1008)

    val sizeDp = size.value

    Canvas(modifier = modifier.size(size)) {
        val pxW = this.size.width
        val cx = pxW / 2f
        val cy = pxW / 2f
        val gradient = Brush.radialGradient(
            colors = listOf(highlight, mid, deep),
            center = Offset(cx - pxW * 0.15f, cy - pxW * 0.15f),
            radius = pxW * 0.55f,
        )

        if (!broken) {
            drawCircle(brush = gradient, radius = pxW * 0.42f, center = Offset(cx, cy))
            drawCircle(
                color = rim.copy(alpha = 0.6f),
                radius = pxW * 0.34f,
                center = Offset(cx, cy),
                style = Stroke(width = 0.8f),
            )
        } else {
            // 八角碎形 —— 与 webui SVG 一致
            val path = Path().apply {
                moveTo(cx, cy - pxW * 0.40f)         // 0,8
                lineTo(cx + pxW * 0.32f, cy - pxW * 0.20f) // 50,20
                lineTo(cx + pxW * 0.30f, cy + pxW * 0.10f) // 48,40
                lineTo(cx + pxW * 0.13f, cy + pxW * 0.30f) // 38,50
                lineTo(cx, cy + pxW * 0.20f)               // 30,44
                lineTo(cx - pxW * 0.13f, cy + pxW * 0.30f) // 22,50
                lineTo(cx - pxW * 0.30f, cy + pxW * 0.10f) // 12,40
                lineTo(cx - pxW * 0.32f, cy - pxW * 0.20f) // 10,20
                close()
            }
            drawPath(path, brush = gradient)
            // 裂纹 —— 顶到中心
            drawLine(
                color = embossDark.copy(alpha = 0.7f),
                start = Offset(cx, cy - pxW * 0.40f),
                end = Offset(cx, cy + pxW * 0.20f),
                strokeWidth = 0.6f,
            )
        }

        // 中心字 —— intact 直接圆心。broken 形态视觉重心在 cy - 0.05w(蜡上缘 -0.40w,下缘 +0.30w),
        // 这里把文字基线对齐到形状几何中心,而不是 bounding box 中心。
        val textRatio = if (broken) 0.20f else 0.28f
        val textCenterY = if (broken) cy - pxW * 0.05f else cy
        val style = TextStyle(
            color = embossDark,
            fontSize = (sizeDp * textRatio).sp,
            fontFamily = tokens.fonts.serifZh,
            fontWeight = FontWeight.SemiBold,
        )
        val layout = measurer.measure(text, style)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(cx - layout.size.width / 2f, textCenterY - layout.size.height / 2f),
        )
    }
}

