package com.luvtter.app.ui.letter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luvtter.app.theme.LuvtterTheme
import kotlin.math.roundToInt

/**
 * 在途信件路线图。webui/screens.jsx InTransitCard 的 SVG 部分。
 *
 * 曲线起点(左下) → 终点(右上),进度 [0, 1] 控制实/虚分段比例,信封图标沿曲线漂浮。
 */
@Composable
fun RouteMap(
    progress: Float,
    fromLabel: String,
    toLabel: String,
    height: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    val pathColor = tokens.colors.seal               // 走过的路 —— 火漆红,与未来形成对比
    val futureColor = tokens.colors.inkGhost.copy(alpha = 0.55f)  // 待走的路 —— 浅墨虚线
    val markerColor = tokens.colors.ink
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(3f, 4f), 0f) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // webui 的 path:M 30 70 Q 200 20, 350 50 T 670 40,viewBox 700×96
            val sx = w / 700f
            val sy = h / 96f
            val path = Path().apply {
                moveTo(30f * sx, 70f * sy)
                quadraticTo(200f * sx, 20f * sy, 350f * sx, 50f * sy)
                // T x y 等价于 quadraticTo(reflect, x, y);手动算反射控制点:
                // 上一控制点 (200, 20) 关于 (350, 50) 反射 → (500, 80)
                quadraticTo(500f * sx, 80f * sy, 670f * sx, 40f * sy)
            }

            // 虚线全程
            drawPath(
                path = path,
                color = futureColor,
                style = Stroke(width = 1.4f, pathEffect = dashEffect),
            )
            // 实线已走部分
            val measure = PathMeasure().apply { setPath(path, false) }
            val total = measure.length
            if (progress > 0f) {
                val sub = Path()
                measure.getSegment(0f, total * progress.coerceIn(0f, 1f), sub, true)
                drawPath(sub, color = pathColor, style = Stroke(width = 2.2f))
            }

            // 起点
            drawCircle(color = markerColor, radius = 3f, center = Offset(30f * sx, 70f * sy))
            // 终点(空心)
            drawCircle(color = markerColor, radius = 3f, center = Offset(670f * sx, 40f * sy), style = Stroke(width = 0.8f))
            drawCircle(color = markerColor.copy(alpha = 0.4f), radius = 6f, center = Offset(670f * sx, 40f * sy), style = Stroke(width = 0.4f))
        }

        // 当前位置的信封图标 —— 沿曲线插值
        val measure = remember(maxWidth, maxHeight) {
            with(this) {
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()
                val sx = wPx / 700f
                val sy = hPx / 96f
                val p = Path().apply {
                    moveTo(30f * sx, 70f * sy)
                    quadraticTo(200f * sx, 20f * sy, 350f * sx, 50f * sy)
                    quadraticTo(500f * sx, 80f * sy, 670f * sx, 40f * sy)
                }
                PathMeasure().apply { setPath(p, false) }
            }
        }
        val pos = remember(measure, progress) {
            val t = progress.coerceIn(0f, 1f) * measure.length
            measure.getPosition(t)
        }

        Box(
            modifier = Modifier
                .offset(
                    x = with(androidx.compose.ui.platform.LocalDensity.current) { (pos.x - 18f).toDp() },
                    y = with(androidx.compose.ui.platform.LocalDensity.current) { (pos.y - 11f).toDp() },
                ),
        ) {
            EnvelopeIcon()
        }
    }
}

@Composable
fun EnvelopeIcon(modifier: Modifier = Modifier) {
    val tokens = LuvtterTheme.tokens
    Canvas(modifier = modifier.size(36.dp, 22.dp)) {
        val w = size.width
        val h = size.height
        drawRect(
            color = Color(0xFFFBF7ED),
            topLeft = Offset(0.5f, 0.5f),
            size = Size(w - 1f, h - 1f),
        )
        drawRect(
            color = tokens.colors.paperEdge,
            topLeft = Offset(0.5f, 0.5f),
            size = Size(w - 1f, h - 1f),
            style = Stroke(width = 0.5f),
        )
        // 折角 V 线
        drawLine(
            color = Color(0x668C6428),
            start = Offset(0.5f, 0.5f),
            end = Offset(w / 2f, h * 0.6f),
            strokeWidth = 0.5f,
        )
        drawLine(
            color = Color(0x668C6428),
            start = Offset(w / 2f, h * 0.6f),
            end = Offset(w - 0.5f, 0.5f),
            strokeWidth = 0.5f,
        )
        // 中心一点火漆红
        drawCircle(color = tokens.colors.seal, radius = 1.4f, center = Offset(w / 2f, h * 0.5f))
    }
}
