package com.luvtter.app.ui.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.formatLocalDate
import com.luvtter.contract.dto.LetterSummaryDto

/**
 * 寄件箱在途列表。webui/compose.jsx Outbox + InTransitCard。
 *
 * 以 transitStage 推算路径进度,头部显示「自 → 致」与 ETA,
 * 中间是 RouteMap(在 P2 沙盒里已实现),底部是邮票 + 阶段说明 + 起讫日期。
 */
@Composable
fun OutboxList(
    letters: List<LetterSummaryDto>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(letters, key = { _, l -> l.id }) { _, letter ->
            Box(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .background(tokens.colors.paperRaised, RoundedCornerShape(2.dp))
                    .border(0.5.dp, tokens.colors.paperEdge, RoundedCornerShape(2.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                if (letter.status == "in_transit") {
                    InTransitCard(letter = letter, onClick = { onOpen(letter.id) })
                } else {
                    StaleOutgoingCard(letter = letter, onClick = { onOpen(letter.id) })
                }
            }
        }
    }
}

@Composable
private fun InTransitCard(letter: LetterSummaryDto, onClick: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    val progress = when (letter.transitStage) {
        "sending" -> 0.12f
        "on_the_way" -> 0.55f
        "arriving" -> 0.88f
        else -> 0.5f
    }
    val stageLabel = when (letter.transitStage) {
        "sending" -> "正离开邮局"
        "on_the_way" -> "路上 · 风正顺"
        "arriving" -> "即将抵达"
        else -> "运输中"
    }
    val recipientName = letter.recipient?.displayName ?: "—"
    val recipientCity = letter.recipientAddressLabel?.takeIf { it.isNotBlank() } ?: "—"
    val senderCity = letter.senderAddressLabel?.takeIf { it.isNotBlank() } ?: "—"
    val sentLabel = formatLocalDate(letter.sentAt) ?: "—"
    val etaLabel = formatLocalDate(letter.deliveryAt) ?: "—"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // Header row —— 自 / 致 / ETA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("自", style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded))
                Text(
                    senderCity,
                    style = tokens.typography.body.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = tokens.colors.inkSoft,
                        letterSpacing = 0.56.sp,
                    ),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "—— 致 ——",
                style = tokens.typography.brand.copy(
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = tokens.colors.inkGhost,
                    letterSpacing = 1.1.sp,
                ),
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("至", style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded))
                Text(
                    "$recipientCity · $recipientName",
                    style = tokens.typography.title.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.72.sp,
                    ),
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("预计 ETA", style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded))
                Text(
                    etaLabel,
                    style = tokens.typography.caption.copy(
                        fontSize = 13.sp,
                        color = tokens.colors.inkSoft,
                        fontStyle = FontStyle.Italic,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        RouteMap(
            progress = progress,
            fromLabel = senderCity,
            toLabel = recipientCity,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(14.dp))

        // 底部:邮票 + 阶段 + 起讫日期
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 4f), 0f)
                    drawLine(
                        color = tokens.colors.rule.copy(alpha = 0.4f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.5f,
                        pathEffect = dash,
                    )
                }
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val spec = Stamps.byId(letter.stampCode ?: "airmail")
            Stamp(spec = spec, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stageLabel, style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded))
                Text(
                    "已行 ${(progress * 100).toInt()}%",
                    style = tokens.typography.caption.copy(
                        fontSize = 12.sp,
                        color = tokens.colors.inkSoft,
                        fontStyle = FontStyle.Italic,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "$sentLabel ────── $etaLabel",
                style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded, letterSpacing = 0.6.sp),
            )
        }
    }
}

/**
 * 已送达 / 已读 / 草稿 / 已封缄 / 已隐藏 等终态卡。
 *
 * 与 InTransitCard 同骨架(头部 · 中段 · 底部 + dashed 顶分隔),让发件箱内两种卡视觉一致:
 * - 头部:致 · 城市 · 收件人(weight 1f),右侧 [完成印章 64×40 旋转 6°],占住右侧空白
 * - 中段:preview 一行,左侧 stampInk 4dp 短 accent 强调
 * - 底部:[Stamp 28] 状态 + 「沿途 N 天」/ 状态文字,右侧「sentAt ──✓── deliveredAt」时间轴
 */
@Composable
private fun StaleOutgoingCard(letter: LetterSummaryDto, onClick: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    val recipientName = letter.recipient?.displayName ?: "—"
    val recipientCity = letter.recipientAddressLabel?.takeIf { it.isNotBlank() } ?: "—"
    val sentLabel = formatLocalDate(letter.sentAt) ?: "—"
    val arriveLabel = formatLocalDate(letter.deliveredAt ?: letter.sentAt) ?: "—"
    val statusLabel = when (letter.status) {
        "delivered" -> "已送达"
        "read" -> "已读"
        "draft" -> "草稿"
        "sealed" -> "已封缄"
        "hidden" -> "已隐藏"
        else -> letter.status
    }
    // 印章短形:已送达 / 已读 → 实印,其余(草稿等)→ 灰
    val isCompleted = letter.status == "delivered" || letter.status == "read"
    val stampColor = if (isCompleted) tokens.colors.stampInk else tokens.colors.inkGhost

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "致",
                    style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
                )
                Text(
                    "$recipientCity · $recipientName",
                    style = tokens.typography.title.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.72.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompletionBadge(label = statusLabel, date = arriveLabel, ink = stampColor)
        }

        // Preview(若有)
        letter.preview?.takeIf { it.isNotBlank() }?.let { preview ->
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .height(28.dp)
                        .width(2.dp)
                        .background(stampColor.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    preview,
                    style = tokens.typography.body.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = tokens.colors.inkSoft,
                        fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 底部:邮票 + 状态 + 时间轴(与 InTransitCard 底部同款 dashed 顶分隔)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 4f), 0f)
                    drawLine(
                        color = tokens.colors.rule.copy(alpha = 0.4f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.5f,
                        pathEffect = dash,
                    )
                }
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val spec = Stamps.byId(letter.stampCode ?: "airmail")
            Stamp(spec = spec, size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(statusLabel, style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded))
                Text(
                    if (isCompleted) "投递完成" else "未送出",
                    style = tokens.typography.caption.copy(
                        fontSize = 12.sp,
                        color = tokens.colors.inkSoft,
                        fontStyle = FontStyle.Italic,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "$sentLabel ──✓── $arriveLabel",
                style = tokens.typography.meta.copy(
                    fontSize = 10.sp,
                    color = tokens.colors.inkFaded,
                    letterSpacing = 0.6.sp,
                ),
            )
        }
    }
}

/**
 * 完成印章:右上角的小方印,与 InTransitCard 右上「预计 ETA」位置对齐,占满右侧空白。
 * 旋转 6°,1px 实色边 + 半透 stampInk 描印,11sp 状态字 + 9sp 日期。
 */
@Composable
private fun CompletionBadge(label: String, date: String, ink: androidx.compose.ui.graphics.Color) {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .rotate(6f)
            .height(40.dp)
            .widthIn(min = 64.dp, max = 84.dp)
            .drawBehind {
                drawRect(
                    color = ink,
                    style = Stroke(width = 1.2f),
                )
                // 内层细描边,印章感
                inset(2f) {
                    drawRect(
                        color = ink.copy(alpha = 0.35f),
                        style = Stroke(width = 0.5f),
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = tokens.typography.title.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ink,
                    letterSpacing = 1.2.sp,
                ),
                maxLines = 1,
            )
            Text(
                date,
                style = tokens.typography.meta.copy(
                    fontSize = 8.sp,
                    color = ink.copy(alpha = 0.75f),
                    letterSpacing = 0.4.sp,
                ),
                maxLines = 1,
            )
        }
    }
}


