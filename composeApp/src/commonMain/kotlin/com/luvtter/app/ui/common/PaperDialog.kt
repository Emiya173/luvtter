package com.luvtter.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luvtter.app.theme.LuvtterTheme
import kotlinx.coroutines.delay

private val BackdropColor = Color(0xFF1E140A).copy(alpha = 0.32f)
private const val EnterDurationMs = 200
private const val ExitDurationMs = 180

/**
 * 纸面弹层。统一替换 Material3 AlertDialog 的视觉,保留内容布局自由度。
 *
 * 视觉:平台 Dialog + 自绘半透墨色背幕(与 PaperRightDrawer 同源),内容区淡入 + 轻微缩放。
 * - title:18sp serifZh + 1.6sp 字距;可选 subtitle 用 meta 9sp
 * - content:任意 composable,内置 28dp 水平 padding
 * - actions:底部右对齐
 */
@Composable
fun PaperDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AnimatedScrim(
        onDismissRequest = onDismissRequest,
        contentAlignment = Alignment.Center,
        enter = fadeIn(tween(EnterDurationMs)) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(EnterDurationMs),
        ),
        exit = fadeOut(tween(ExitDurationMs)) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(ExitDurationMs),
        ),
    ) {
        DialogSurface(title = title, subtitle = subtitle, actions = actions, content = content)
    }
}

/**
 * 右抽屉。Search / Notifications 之类内容多但仍是次级面板的弹层。
 *
 * 视觉:右贴边、固定宽、铺满高度;左侧自绘半透墨色背幕,点击或按 ESC 关闭。
 * 进入由右滑入 + 背幕淡入,退出反向。标题/副标题/动作位与 PaperDialog 对齐,内容区可滚。
 */
@Composable
fun PaperRightDrawer(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    width: Dp = 380.dp,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AnimatedScrim(
        onDismissRequest = onDismissRequest,
        contentAlignment = Alignment.CenterEnd,
        enter = slideInHorizontally(tween(EnterDurationMs)) { it },
        exit = slideOutHorizontally(tween(ExitDurationMs)) { it },
    ) {
        DrawerSurface(width = width, title = title, subtitle = subtitle, actions = actions, content = content)
    }
}

/**
 * 内部:`Dialog(usePlatformDefaultWidth=false)` + 自绘背幕 + 进入/退出动画统一壳。
 *
 * 进入:首帧后将 `targetState = true`,触发 enter。
 * 退出:onDismissRequest 触发后将 `targetState = false`,等动画完成再调用真正的 dismiss。
 * 这样可以让退出动画跑完再关 Dialog,避免内容瞬间消失。
 */
@Composable
private fun AnimatedScrim(
    onDismissRequest: () -> Unit,
    contentAlignment: Alignment,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    content: @Composable () -> Unit,
) {
    val transition = remember { MutableTransitionState(initialState = false) }
    LaunchedEffect(Unit) { transition.targetState = true }

    // 当外部要求关闭时,先把 targetState 拍回 false 触发退出动画;真正 dismiss 在 below 等待
    val requestClose: () -> Unit = remember(onDismissRequest) {
        { transition.targetState = false }
    }
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (!transition.targetState && !transition.currentState) {
            // 进入未发生就被关:跳过
            onDismissRequest()
        } else if (!transition.targetState && transition.currentState) {
            delay((ExitDurationMs + 20).toLong())
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = contentAlignment) {
            // 背幕:fade in/out
            AnimatedVisibility(
                visibleState = transition,
                enter = fadeIn(tween(EnterDurationMs)),
                exit = fadeOut(tween(ExitDurationMs)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackdropColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = requestClose,
                        ),
                )
            }
            // 内容:由调用方决定 enter/exit 形态(中央缩放或右滑入)
            AnimatedVisibility(
                visibleState = transition,
                enter = enter,
                exit = exit,
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // 吃掉点击,防止冒泡到背幕
                    ),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DialogSurface(
    title: String,
    subtitle: String?,
    actions: @Composable (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Surface(
        modifier = Modifier
            .widthIn(min = 320.dp, max = 560.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(2.dp),
                ambientColor = Color(0xFF1E140A).copy(alpha = 0.18f),
            ),
        shape = RoundedCornerShape(2.dp),
        color = tokens.colors.paperRaised,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Header(title = title, subtitle = subtitle)
            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                content()
            }
            if (actions != null) {
                Spacer(Modifier.height(20.dp))
                ActionRow(actions)
            }
        }
    }
}

@Composable
private fun DrawerSurface(
    width: Dp,
    title: String,
    subtitle: String?,
    actions: @Composable (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .shadow(
                elevation = 18.dp,
                ambientColor = Color(0xFF1E140A).copy(alpha = 0.25f),
            ),
        color = tokens.colors.paperRaised,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 24.dp)) {
            Header(title = title, subtitle = subtitle)
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 28.dp),
            ) {
                content()
            }
            if (actions != null) {
                Spacer(Modifier.height(16.dp))
                ActionRow(actions)
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?) {
    val tokens = LuvtterTheme.tokens
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        Text(
            title,
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.colors.ink,
                letterSpacing = 1.6.sp,
            ),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = tokens.typography.meta.copy(
                    fontSize = 10.sp,
                    color = tokens.colors.inkFaded,
                ),
            )
        }
    }
}

@Composable
private fun ActionRow(actions: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions()
    }
}
