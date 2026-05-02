package com.luvtter.app.ui.letter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.formatLocalDate
import com.luvtter.contract.dto.LetterSummaryDto

/**
 * 收件箱信封网格。每行 N 列(由可用宽度自适应,最小列宽 320dp),
 * 桌面宽屏可显 2-3 列,信封感保留(纸纹 + 折线 + 火漆 + 邮戳邮票)但单封尺寸大幅缩减。
 */
@Composable
fun InboxStack(
    letters: List<LetterSummaryDto>,
    isLetterMine: (LetterSummaryDto) -> Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(letters, key = { it.id }) { letter ->
            EnvelopeStackItem(
                letter = letter,
                mine = isLetterMine(letter),
                onClick = { onOpen(letter.id) },
            )
        }
        item(key = "__empty_foot__", span = { GridItemSpan(maxLineSpan) }) { EmptyFoot() }
    }
}

@Composable
private fun EnvelopeStackItem(
    letter: LetterSummaryDto,
    mine: Boolean,
    onClick: () -> Unit,
) {
    val view = letter.toEnvelopeView(mine)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        EnvelopeThumb(view = view)
    }
}

@Composable
private fun EmptyFoot() {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(1.dp))
        Text(
            text = "── 邮差还在路上 ──",
            style = tokens.typography.caption.copy(fontSize = 12.sp, color = tokens.colors.inkFaded),
        )
    }
}

private fun LetterSummaryDto.toEnvelopeView(mine: Boolean): EnvelopeView {
    val direction = if (mine) EnvelopeView.Direction.Outgoing else EnvelopeView.Direction.Incoming
    val party = if (mine) recipient else sender
    val partyName = party?.displayName?.takeIf { it.isNotBlank() } ?: "未知"
    val partyInitial = partyName.firstOrNull()?.toString() ?: "·"
    val city = recipientAddressLabel?.takeIf { it.isNotBlank() } ?: "—"
    val time = formatLocalDate(deliveredAt ?: deliveryAt ?: sentAt) ?: "—"
    val stamp = Stamps.byId(stampCode ?: "airmail")
    val opened = readAt != null || status == "read"
    return EnvelopeView(
        direction = direction,
        partyName = partyName,
        partyInitial = partyInitial,
        cityOrAddress = city,
        sentAt = time,
        stamp = stamp,
        opened = opened,
    )
}
