package com.luvtter.app.ui.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(tokens.colors.paper, tokens.colors.paperDeep),
                ),
            ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        itemsIndexed(letters, key = { _, l -> l.id }) { _, letter ->
            Box(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
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
    val recipientCity = letter.recipientAddressLabel?.takeIf { it.isNotBlank() } ?: "远方"
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
                    "上海",
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
            fromLabel = "沪",
            toLabel = recipientCity.firstOrNull()?.toString() ?: "—",
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

@Composable
private fun StaleOutgoingCard(letter: LetterSummaryDto, onClick: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    val recipientName = letter.recipient?.displayName ?: "—"
    val recipientCity = letter.recipientAddressLabel?.takeIf { it.isNotBlank() } ?: "—"
    val time = formatLocalDate(letter.deliveredAt ?: letter.sentAt) ?: "—"
    val statusLabel = when (letter.status) {
        "delivered" -> "已送达"
        "read" -> "已读"
        "draft" -> "草稿"
        "sealed" -> "已封缄"
        "hidden" -> "已隐藏"
        else -> letter.status
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val spec = Stamps.byId(letter.stampCode ?: "airmail")
        Stamp(spec = spec, size = 32.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "致 · $recipientCity · $recipientName",
                style = tokens.typography.title.copy(fontSize = 16.sp, letterSpacing = 0.64.sp),
                maxLines = 1,
            )
            letter.preview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = tokens.typography.body.copy(fontSize = 13.sp, lineHeight = 19.sp, color = tokens.colors.inkSoft),
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$statusLabel · $time",
                style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkGhost),
            )
        }
    }
}

