package com.luvtter.app.ui.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.theme.paperGrain

/**
 * Inbox / Correspondence 通用信封缩略图。webui/components.jsx EnvelopeThumb。
 * 长宽比固定 1.62:1(黄金比)。
 */
@Immutable
data class EnvelopeView(
    val direction: Direction,
    val partyName: String,         // dir==in 时是寄件人,out 时是收件人
    val partyInitial: String,       // 一字头像 / 火漆盖印字
    val cityOrAddress: String,
    val sentAt: String,
    val stamp: StampSpec,
    val opened: Boolean = true,
) {
    enum class Direction { Incoming, Outgoing }
}

@Composable
fun EnvelopeThumb(
    view: EnvelopeView,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    val shadowAlpha = if (view.opened) 0.04f else 0.08f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.62f)
            .shadow(if (view.opened) 0.dp else 8.dp, RectangleShape, clip = false, ambientColor = Color(0xFF281A0A).copy(alpha = shadowAlpha))
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFBF7ED), Color(0xFFF2EBD8)))
            )
            .paperGrain(seed = view.partyName.hashCode().toLong())
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        val envWidth = maxWidth
        val envHeight = maxHeight
        // 信封内三角对折线(左上 - 中 - 右上)
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 顶部 V 形折线
            drawLine(
                color = tokens.colors.paperEdge.copy(alpha = 0.6f),
                start = Offset(0f, 0f),
                end = Offset(w / 2f, h * 0.58f),
                strokeWidth = 0.6f,
            )
            drawLine(
                color = tokens.colors.paperEdge.copy(alpha = 0.6f),
                start = Offset(w / 2f, h * 0.58f),
                end = Offset(w, 0f),
                strokeWidth = 0.6f,
            )
        }

        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            // 地址栏
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (view.direction == EnvelopeView.Direction.Incoming) "自 · FROM" else "致 · TO",
                    style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = view.partyName,
                    style = tokens.typography.title.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.7.sp,
                        color = tokens.colors.ink,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${view.cityOrAddress} · ${view.sentAt}",
                    style = tokens.typography.caption.copy(
                        fontSize = 12.sp,
                        color = tokens.colors.inkFaded,
                        fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 邮戳 + 邮票横向堆叠,尺寸跟随信封宽度缩放
            val postmarkSize = (envWidth * 0.22f).coerceIn(48.dp, 96.dp)
            val stampSize = (envWidth * 0.16f).coerceIn(36.dp, 68.dp)
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Postmark(
                    city = view.cityOrAddress.take(2),
                    date = view.sentAt,
                    size = postmarkSize,
                    rotateDeg = -10f,
                    ink = tokens.colors.stampInk,
                )
                Stamp(spec = view.stamp, size = stampSize)
            }
        }

        // 未拆 + 收件 → 火漆压在封口 V 形交点。尺寸跟随信封宽度(18%)
        if (!view.opened && view.direction == EnvelopeView.Direction.Incoming) {
            val sealSize = (envWidth * 0.18f).coerceAtLeast(36.dp)
            val flapY = envHeight * 0.58f - sealSize / 2f
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = flapY),
            ) {
                WaxSeal(text = view.partyInitial, size = sealSize)
            }
        }
    }
}
