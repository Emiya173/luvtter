package com.luvtter.app.ui.letter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.theme.paperGrain
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 拆封视图。
 *
 * 三相状态机:Sealed(等点击) → Opening(自动 ~2200ms) → 调用 onOpenComplete()。
 *
 * 视觉层叠(从下到上):
 *  1. 信封正面 —— paperGrain + V 折线 + 右上邮戳/邮票,始终可见(postmark/stamp 不被遮挡)
 *  2. 火漆 —— 居中盖在 V 形交点,Opening 时 scale↑ + alpha↓
 *  3. 火漆碎屑 —— Opening 中段沿 4 个方向飞射
 *  4. 信纸 —— 初始 translationY = +1.0 隐于信封下方,Opening 末段 0..-0.04 抽出并罩在信封之上
 *
 * 不再渲染单独的「翻盖」三角层 —— 之前那层 z-order 高于信封正面,初始角度 0° 时把
 * postmark/stamp 整片盖住,而且也不能让人看到信纸从信封里抽出。
 */
enum class UnsealPhase { Sealed, Opening }

@Composable
fun UnsealOverlay(
    view: EnvelopeView,
    phase: UnsealPhase,
    onClickWhenSealed: () -> Unit,
    onOpenComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens

    val sealScale = remember { Animatable(1f) }
    val sealAlpha = remember { Animatable(1f) }
    val shardProgress = remember { Animatable(0f) }
    val paperOffset = remember { Animatable(1f) }   // 1f = 完全藏在信封下方;-0.04 = 几乎完全覆盖
    val paperAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(phase) {
        if (phase != UnsealPhase.Opening) return@LaunchedEffect
        val jobs = listOf(
            scope.async { sealScale.animateTo(1.4f, tween(600, easing = LinearOutSlowInEasing)) },
            scope.async { sealAlpha.animateTo(0f, tween(600, easing = LinearOutSlowInEasing)) },
            scope.async {
                delay(200)
                shardProgress.animateTo(1f, tween(800))
            },
            scope.async {
                delay(600)
                paperAlpha.animateTo(1f, tween(500))
            },
            scope.async {
                delay(600)
                paperOffset.animateTo(-0.04f, tween(1100, easing = LinearOutSlowInEasing))
            },
        )
        jobs.awaitAll()
        delay(120)
        onOpenComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.paperDeep)
            .let {
                if (phase == UnsealPhase.Sealed) it.clickable(onClick = onClickWhenSealed) else it
            },
        contentAlignment = Alignment.Center,
    ) {
        BigEnvelope(
            view = view,
            sealScale = sealScale.value,
            sealAlpha = sealAlpha.value,
            shardProgress = shardProgress.value,
            paperOffsetFraction = paperOffset.value,
            paperAlpha = paperAlpha.value,
            phase = phase,
        )

        if (phase == UnsealPhase.Sealed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp),
            ) {
                Text(
                    "↑ 点 火 漆 拆 开",
                    style = TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 13.sp,
                        color = tokens.colors.inkFaded,
                        letterSpacing = 1.6.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BigEnvelope(
    view: EnvelopeView,
    sealScale: Float,
    sealAlpha: Float,
    shardProgress: Float,
    paperOffsetFraction: Float,
    paperAlpha: Float,
    phase: UnsealPhase,
) {
    val tokens = LuvtterTheme.tokens

    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(0.85f)
            .aspectRatio(1.55f)
            // 整体 clip,信纸滑出时不溢出到背景
            .clip(RectangleShape)
            .shadow(8.dp, RectangleShape, clip = false, ambientColor = Color(0xFF281A0A).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        val envWidth = maxWidth
        val envHeight = maxHeight

        // ── 1. 信封正面 ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color(0xFFFBF7ED), Color(0xFFF2EBD8))))
                .paperGrain(seed = view.partyName.hashCode().toLong()),
        ) {
            // V 折线(浅)
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = tokens.colors.paperEdge.copy(alpha = 0.55f),
                    start = Offset(0f, 0f),
                    end = Offset(w / 2f, h * 0.58f),
                    strokeWidth = 0.6f,
                )
                drawLine(
                    color = tokens.colors.paperEdge.copy(alpha = 0.55f),
                    start = Offset(w / 2f, h * 0.58f),
                    end = Offset(w, 0f),
                    strokeWidth = 0.6f,
                )
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Postmark(
                    city = view.cityOrAddress.take(2),
                    date = view.sentAt,
                    size = (envWidth.value * 0.16f).dp.coerceIn(48.dp, 72.dp),
                    rotateDeg = -10f,
                    ink = tokens.colors.stampInk,
                )
                Stamp(
                    spec = view.stamp,
                    size = (envWidth.value * 0.12f).dp.coerceIn(36.dp, 56.dp),
                )
            }

            // 自/致 标签贴左
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 22.dp),
            ) {
                Text(
                    "自 · FROM",
                    style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    view.partyName,
                    style = tokens.typography.title.copy(
                        fontSize = 17.sp,
                        letterSpacing = 0.6.sp,
                        color = tokens.colors.ink,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── 2. 火漆 ────────────────────────────────────────────────────
        val sealSize = (envWidth.value * 0.18f).dp.coerceIn(56.dp, 96.dp)
        if (sealAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = envHeight * 0.58f - sealSize / 2f)
                    .graphicsLayer {
                        scaleX = sealScale
                        scaleY = sealScale
                        alpha = sealAlpha
                    },
            ) {
                WaxSeal(
                    text = view.partyInitial,
                    size = sealSize,
                    broken = phase == UnsealPhase.Opening,
                )
            }
        }

        // ── 3. 火漆碎屑 ────────────────────────────────────────────────
        if (shardProgress > 0f && shardProgress < 1f) {
            ShardLayer(
                progress = shardProgress,
                center = Offset(envWidth.value / 2f, envHeight.value * 0.58f),
                shardSize = sealSize,
            )
        }

        // ── 4. 信纸 —— 顶层,从信封下方抽出 ──────────────────────────────
        // graphicsLayer 应在最外层,先位移再画背景,让位移把整张纸一起带走
        if (paperAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationY = envHeight.toPx() * paperOffsetFraction
                        alpha = paperAlpha
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .background(Color(0xFFFAF6EC))
                    .padding(horizontal = 22.dp, vertical = 22.dp),
            ) {
                LetterSheetPreview(view = view)
            }
        }
    }
}

/** 信纸抽出过程中露出的占位内容:抬头 + 几道横线模拟正文。Reading 切换后会被真正的 LetterPaper 替代。 */
@Composable
private fun LetterSheetPreview(view: EnvelopeView) {
    val tokens = LuvtterTheme.tokens
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "亲启 · ${view.partyName}",
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 15.sp,
                color = tokens.colors.inkSoft,
                letterSpacing = 0.6.sp,
                fontStyle = FontStyle.Italic,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val step = 28f
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = tokens.colors.inkFaded.copy(alpha = 0.18f),
                    start = Offset(0f, y),
                    end = Offset(w * 0.92f, y),
                    strokeWidth = 0.5f,
                )
                y += step
            }
        }
    }
}

@Composable
private fun ShardLayer(
    progress: Float,
    center: Offset,
    shardSize: Dp,
) {
    val tokens = LuvtterTheme.tokens
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        val angles = listOf(-60f, 30f, 150f, 240f)
        val delays = listOf(0f, 0.08f, 0.16f, 0.24f)
        angles.forEachIndexed { i, deg ->
            val localProg = ((progress - delays[i]) / (1f - delays[i])).coerceIn(0f, 1f)
            if (localProg <= 0f) return@forEachIndexed
            val flightDp = (60f + i * 20f)
            val rad = deg * (kotlin.math.PI / 180f).toFloat()
            val dx = cos(rad) * flightDp * localProg
            val dy = sin(rad) * flightDp * localProg
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { center.x.toDp() } + dx.dp - shardSize * 0.18f,
                        y = with(density) { center.y.toDp() } + dy.dp - shardSize * 0.18f,
                    )
                    .size(shardSize * 0.36f)
                    .graphicsLayer {
                        rotationZ = 180f * localProg * (if (i % 2 == 0) 1f else -1f)
                        alpha = 1f - localProg
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(w * 0.5f, 0f)
                        lineTo(w, h * 0.45f)
                        lineTo(w * 0.65f, h)
                        lineTo(w * 0.2f, h * 0.85f)
                        lineTo(0f, h * 0.35f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFC84A35), tokens.colors.seal, tokens.colors.sealDark),
                            center = Offset(w * 0.4f, h * 0.4f),
                            radius = w * 0.7f,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 折回动画。Reading → onBack 之间短促播放,留个收束感后立刻让导航 pop 接手。
 * 仅做轻量 alpha + 微下沉,避免大位移带来的"长尾"。
 */
fun Modifier.foldBackTransform(progress: Float): Modifier =
    this.graphicsLayer {
        val p = progress.coerceIn(0f, 1f)
        translationY = size.height * 0.08f * p
        alpha = 1f - p
    }
