package com.luvtter.app.ui.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.theme.paperGrain

/**
 * Inbox 信封缩略图(网格单元版本)。
 *
 * 黄金比 1.62:1 矩形,装饰齐全(V 折线、纸纹、火漆、邮戳、邮票),
 * 尺寸按宽度自适应:配合 LazyVerticalGrid 的 ~320dp 列宽,实际信封约 320×198dp,
 * 比之前列表版(~620×383)缩到三分之一,但视觉密度更高。
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
            .shadow(
                if (view.opened) 1.dp else 6.dp,
                RectangleShape,
                clip = false,
                ambientColor = Color(0xFF281A0A).copy(alpha = shadowAlpha),
            )
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFBF7ED), Color(0xFFF2EBD8)))
            )
            .paperGrain(seed = view.partyName.hashCode().toLong())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        val envWidth = maxWidth
        val envHeight = maxHeight
        // 信封内三角对折线(左上 - 中 - 右上)
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
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
                    style = tokens.typography.meta.copy(fontSize = 8.sp, color = tokens.colors.inkFaded),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = view.partyName,
                    style = tokens.typography.title.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = tokens.colors.ink,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${view.cityOrAddress} · ${view.sentAt}",
                    style = tokens.typography.caption.copy(
                        fontSize = 10.sp,
                        color = tokens.colors.inkFaded,
                        fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 邮戳 + 邮票横向堆叠,尺寸跟随信封宽度缩放(网格版 clamp 上限缩到一半)
            val postmarkSize = (envWidth * 0.22f).coerceIn(36.dp, 60.dp)
            val stampSize = (envWidth * 0.16f).coerceIn(26.dp, 44.dp)
            Row(
                modifier = Modifier.padding(start = 6.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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

        // 未拆 + 收件 → 火漆压在封口 V 形交点。网格版 18% / 28..48dp
        if (!view.opened && view.direction == EnvelopeView.Direction.Incoming) {
            val sealSize = (envWidth * 0.18f).coerceIn(28.dp, 48.dp)
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
