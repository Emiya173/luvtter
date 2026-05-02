package com.luvtter.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.random.Random

/**
 * 模拟 webui `.paper-grain`:三层径向噪点 + 顶部/底部暖色压暗。
 * `seed` 决定纹理随机性 —— 同 seed 出的纹理稳定,不会每帧重抖。
 */
fun Modifier.paperGrain(seed: Long = 1L): Modifier = composed {
    val flecks = remember(seed) { generateFlecks(seed) }
    drawWithCache {
        val w = size.width
        val h = size.height
        val topGlow = Brush.radialGradient(
            colors = listOf(Color(0x33FFFAE6), Color.Transparent),
            center = Offset(w * 0.2f, h * 0.1f),
            radius = max(w, h) * 0.5f,
        )
        val bottomShade = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x14503C14)),
            center = Offset(w * 0.5f, h * 1.0f),
            radius = max(w, h) * 0.95f,
        )
        onDrawWithContent {
            drawContent()
            // 顶部一点暖色高光
            drawRect(brush = topGlow, blendMode = BlendMode.Screen)
            // 底部一点压暗(模仿 webui 的纸张厚度感)
            drawRect(brush = bottomShade, blendMode = BlendMode.Multiply)
            // 噪点纤维
            flecks.forEach { f ->
                drawCircle(
                    color = f.color,
                    radius = f.radius,
                    center = Offset(f.x * w, f.y * h),
                    blendMode = BlendMode.Multiply,
                )
            }
        }
    }
}

private data class Fleck(val x: Float, val y: Float, val radius: Float, val color: Color)

private fun generateFlecks(seed: Long): List<Fleck> {
    val rng = Random(seed)
    val out = ArrayList<Fleck>(280)
    // 三个尺度,对应 styles.css 里 2.5/7/14px 三层径向背景
    repeat(180) {
        out += Fleck(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            radius = 0.5f,
            color = Color(0x1A6E5A32), // rgba(110,90,50,0.10)
        )
    }
    repeat(70) {
        out += Fleck(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            radius = 0.7f,
            color = Color(0x0F8C6E3C), // rgba(140,110,60,0.06)
        )
    }
    repeat(30) {
        out += Fleck(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            radius = 1.0f,
            color = Color(0x0A5A461E), // rgba(90,70,30,0.04)
        )
    }
    return out
}

/**
 * 模拟 webui `.deckle`:四边 8% 渐变内边,模仿手撕纸边缘的赭色阴影。
 */
fun Modifier.deckle(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val edgeColor = Color(0x1A8C6428) // rgba(140,100,40,0.10)
    onDrawWithContent {
        drawContent()
        // 上下
        drawRect(
            brush = Brush.verticalGradient(
                0f to edgeColor,
                0.08f to Color.Transparent,
                0.92f to Color.Transparent,
                1f to edgeColor,
            ),
            size = Size(w, h),
        )
        // 左右
        drawRect(
            brush = Brush.horizontalGradient(
                0f to edgeColor,
                0.08f to Color.Transparent,
                0.92f to Color.Transparent,
                1f to edgeColor,
            ),
            size = Size(w, h),
        )
    }
}

/**
 * 模拟 webui `.paper-sheen`:右上方一片暖色高光,叠在内容之上。
 */
fun Modifier.paperSheen(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val sheen = Brush.radialGradient(
        colors = listOf(Color(0x59FFFAE6), Color.Transparent),
        center = Offset(w * 0.2f, h * 0.1f),
        radius = max(w, h) * 0.6f,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush = sheen, blendMode = BlendMode.Screen)
    }
}

/**
 * 0.5dp 实线分隔线,色 `tokens.rule`。webui `.hair`。
 */
@Composable
fun Modifier.hair(soft: Boolean = false): Modifier {
    val tokens = LuvtterTheme.tokens
    val color = if (soft) tokens.colors.ruleSoft else tokens.colors.rule
    return drawWithCache {
        onDrawWithContent {
            drawContent()
            drawLine(
                color = color,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 0.5f,
            )
        }
    }
}

/**
 * 0.5dp 虚线 —— webui 里 input 下划线 `border-bottom: 0.5px dashed rgba(120,90,40,0.25)` 用得多。
 */
fun Modifier.dashedHair(color: Color = Color(0x40785A28)): Modifier = drawWithCache {
    val effect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
    onDrawWithContent {
        drawContent()
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 0.5f,
            pathEffect = effect,
        )
    }
}

@Suppress("unused")
private val Hairline = 0.5.dp  // 文档值,Compose 里直接用 0.5f px 画

@Suppress("unused")
private fun strokeHair(color: Color) = Stroke(width = 0.5f).let { color }
