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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.formatLocalDate
import com.luvtter.contract.dto.LetterSummaryDto

/**
 * 收件箱信封堆叠。webui/screens.jsx Inbox + EnvelopeStackItem。
 *
 * 每封信渲染一个轻微旋转的 EnvelopeThumb,点击进入详情。
 */
@Composable
fun InboxStack(
    letters: List<LetterSummaryDto>,
    isLetterMine: (LetterSummaryDto) -> Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(letters, key = { _, l -> l.id }) { index, letter ->
            EnvelopeStackItem(
                letter = letter,
                mine = isLetterMine(letter),
                index = index,
                onClick = { onOpen(letter.id) },
            )
        }
        item(key = "__empty_foot__") { EmptyFoot() }
    }
}

@Composable
private fun EnvelopeStackItem(
    letter: LetterSummaryDto,
    mine: Boolean,
    index: Int,
    onClick: () -> Unit,
) {
    val rot = (if (index % 2 == 0) -0.4f else 0.5f) + index * 0.15f
    val view = letter.toEnvelopeView(mine)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 620.dp)
            .rotate(rot)
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
