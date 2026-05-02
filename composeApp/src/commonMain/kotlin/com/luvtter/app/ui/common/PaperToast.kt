package com.luvtter.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import kotlinx.coroutines.delay

/**
 * 纸面 Toast。webui 里没有 Snackbar 的对等物,这里是端侧补丁。
 *
 * 使用:
 * ```
 * val toast = rememberPaperToastState()
 * Box {
 *     content()
 *     PaperToastHost(toast, modifier = Modifier.align(Alignment.BottomCenter))
 * }
 * LaunchedEffect(state.error) { state.error?.let { toast.show(it, PaperToastKind.Error) } }
 * ```
 */

enum class PaperToastKind { Info, Success, Error }

/**
 * @param durationMs 0 表示持久,直到用户点 ✕ 或下次 show 覆盖。
 * @param actionLabel 非空时在文本右侧出现一个 paper ghost 按钮。
 * @param onAction 点击 actionLabel 触发,默认不会顺手关闭 toast(留给调用方决定);若需要,在回调中自行 dismiss。
 * @param dismissible 是否显示 ✕ 关闭按钮。默认 true。
 */
data class PaperToast(
    val message: String,
    val kind: PaperToastKind = PaperToastKind.Info,
    val durationMs: Long = 2800L,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val dismissible: Boolean = true,
)

@Stable
class PaperToastState {
    var current by mutableStateOf<PaperToast?>(null)
        internal set

    fun show(
        message: String,
        kind: PaperToastKind = PaperToastKind.Info,
        durationMs: Long = 2800L,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        dismissible: Boolean = true,
    ) {
        current = PaperToast(message, kind, durationMs, actionLabel, onAction, dismissible)
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun rememberPaperToastState(): PaperToastState = remember { PaperToastState() }

@Composable
fun PaperToastHost(
    state: PaperToastState,
    modifier: Modifier = Modifier,
) {
    val current = state.current
    // 保留最近一次以让 exit 动画跑完
    var last by remember { mutableStateOf<PaperToast?>(null) }
    LaunchedEffect(current) { if (current != null) last = current }
    LaunchedEffect(current) {
        val cur = current ?: return@LaunchedEffect
        if (cur.durationMs > 0) {
            delay(cur.durationMs)
            if (state.current === cur) state.current = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(160)),
        ) {
            last?.let { ToastSurface(it, onDismiss = { state.dismiss() }) }
        }
    }
}

@Composable
private fun ToastSurface(toast: PaperToast, onDismiss: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    val accent = when (toast.kind) {
        PaperToastKind.Info -> tokens.colors.inkSoft
        PaperToastKind.Success -> tokens.colors.stampInk
        PaperToastKind.Error -> tokens.colors.seal
    }
    Surface(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 520.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(2.dp),
                ambientColor = Color(0xFF1E140A).copy(alpha = 0.18f),
            ),
        shape = RoundedCornerShape(2.dp),
        color = tokens.colors.paperRaised,
    ) {
        Row(
            modifier = Modifier.padding(start = 0.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(32.dp)
                    .background(accent),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                toast.message,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(
                    fontFamily = tokens.fonts.serifZh,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = tokens.colors.ink,
                    letterSpacing = 0.4.sp,
                ),
            )
            if (toast.actionLabel != null && toast.onAction != null) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clickable(onClick = toast.onAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        toast.actionLabel,
                        style = TextStyle(
                            fontFamily = tokens.fonts.serifZh,
                            fontSize = 12.sp,
                            color = tokens.colors.seal,
                            letterSpacing = 0.6.sp,
                        ),
                    )
                }
            }
            if (toast.dismissible) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "✕",
                        style = TextStyle(
                            fontFamily = tokens.fonts.serifZh,
                            fontSize = 13.sp,
                            color = tokens.colors.inkFaded,
                        ),
                    )
                }
            }
        }
    }
}
