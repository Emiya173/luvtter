package com.luvtter.app.ui.letter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.formatLocalDateTime
import com.luvtter.contract.dto.LetterEventDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * 进入详情页且 [enabled] 时,把该信尚未在本次会话展示过的拟真事件依次以明信片浮层飘入,
 * 自动消逝。点击浮层提前结束当前张,继续下一张。展示过的 id 写进 [shownIds] 永久驻留,
 * 同一信件二次进入不再触发。下方"途中事件"列表始终可回看。
 *
 * 跨进程持久化(已读 mark 上服务端)未做 —— 当前仅会话内去重。
 */
@Composable
fun EventOverlay(
    events: List<LetterEventDto>,
    enabled: Boolean,
    shownIds: MutableList<String>,
    onEventRead: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!enabled || events.isEmpty()) return
    // 服务端 read_at 非空 = 已展示过,客户端会话内 shownIds 二次去重(乐观更新)
    val pending = remember(events, enabled) {
        events.filter { it.readAt == null && it.id !in shownIds }
    }
    if (pending.isEmpty()) return

    var current by remember(pending) { mutableStateOf(0) }
    var advanceSignal by remember(pending) { mutableStateOf(0) }

    if (current >= pending.size) return
    val event = pending[current]

    val offsetY = remember(event.id) { Animatable(-160f) }
    val offsetX = remember(event.id) { Animatable(80f) }
    val rotation = remember(event.id) { Animatable(-14f) }
    val scale = remember(event.id) { Animatable(0.82f) }
    val alpha = remember(event.id) { Animatable(0f) }

    LaunchedEffect(event.id, advanceSignal) {
        val enter = tween<Float>(durationMillis = 520, easing = LinearOutSlowInEasing)
        val enterRot = tween<Float>(durationMillis = 520, easing = FastOutSlowInEasing)
        listOf(
            launch { offsetY.animateTo(0f, enter) },
            launch { offsetX.animateTo(0f, enter) },
            launch { scale.animateTo(1f, enter) },
            launch { rotation.animateTo(-3f, enterRot) },
            launch { alpha.animateTo(1f, tween(360)) },
        ).joinAll()
        delay(3500)
        listOf(
            launch { alpha.animateTo(0f, tween(360)) },
            launch { offsetY.animateTo(40f, tween(420, easing = FastOutSlowInEasing)) },
            launch { rotation.animateTo(2f, tween(420)) },
        ).joinAll()
        shownIds.add(event.id)
        onEventRead(event.id)
        current += 1
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Postcard(
            event = event,
            modifier = Modifier
                .padding(top = 88.dp, end = 32.dp)
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = rotation.value
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { advanceSignal += 1 },
        )
    }
}

@Composable
private fun Postcard(event: LetterEventDto, modifier: Modifier = Modifier) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .shadow(18.dp, RoundedCornerShape(2.dp), ambientColor = Color(0xFF1E140A).copy(alpha = 0.25f))
            .clip(RoundedCornerShape(2.dp))
            .background(tokens.colors.paperRaised)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            "途 · 中",
            style = tokens.typography.meta.copy(
                fontSize = 9.sp,
                color = tokens.colors.stampInk.copy(alpha = 0.75f),
                letterSpacing = 2.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            event.title ?: eventTypeLabel(event.eventType),
            style = tokens.typography.title.copy(
                fontSize = 15.sp,
                color = tokens.colors.ink,
            ),
        )
        event.content?.takeIf { it.isNotBlank() }?.let { c ->
            Spacer(Modifier.height(6.dp))
            Text(
                c,
                style = tokens.typography.body.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = tokens.colors.inkSoft,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            formatLocalDateTime(event.visibleAt) ?: event.visibleAt,
            style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkGhost),
        )
    }
}

private fun eventTypeLabel(type: String): String = when (type) {
    "early" -> "提前到达"
    "wear" -> "途中磨损"
    "postcard" -> "途中明信片"
    else -> type
}
