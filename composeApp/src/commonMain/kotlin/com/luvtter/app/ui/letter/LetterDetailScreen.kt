package com.luvtter.app.ui.letter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.PaperDialog
import com.luvtter.app.ui.common.PaperGhostButton
import com.luvtter.app.ui.common.PaperListRow
import com.luvtter.app.ui.common.PaperLoadingHint
import com.luvtter.app.ui.common.PaperPrimaryButton
import com.luvtter.app.ui.common.PaperToastHost
import com.luvtter.app.ui.common.PaperToastKind
import com.luvtter.app.ui.common.formatLocalDate
import com.luvtter.app.ui.common.formatLocalDateTime
import com.luvtter.app.ui.common.rememberPaperToastState
import com.luvtter.contract.dto.AttachmentDto
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.StickerDto
import org.koin.compose.viewmodel.koinViewModel

private enum class ReadingPhase { Sealed, Opening, Reading, FoldingBack }

@Composable
fun LetterDetailScreen(
    onReply: (recipientHandle: String?) -> Unit,
    onBack: () -> Unit,
    vm: LetterDetailViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showFolderPicker by remember { mutableStateOf(false) }
    LetterDetailContent(
        state = state,
        viewerId = vm.viewerId,
        showFolderPicker = showFolderPicker,
        onReply = onReply,
        onBack = onBack,
        onToggleFavorite = vm::toggleFavorite,
        onShowFolderPicker = { showFolderPicker = true },
        onDismissFolderPicker = { showFolderPicker = false },
        onAssignFolder = { id -> vm.assignFolder(id) { showFolderPicker = false } },
        onHide = { vm.hide(onBack) },
        onUnhide = { vm.unhide(onBack) },
        onClearError = vm::clearError,
        onMarkRead = vm::markRead,
    )
}

@Composable
private fun LetterDetailContent(
    state: LetterDetailUiState,
    viewerId: String?,
    showFolderPicker: Boolean,
    onReply: (recipientHandle: String?) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowFolderPicker: () -> Unit,
    onDismissFolderPicker: () -> Unit,
    onAssignFolder: (String?) -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onClearError: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    val toast = rememberPaperToastState()
    LaunchedEffect(state.error) {
        state.error?.let {
            toast.show(it, PaperToastKind.Error, durationMs = 4000L)
            onClearError()
        }
    }

    val d = state.detail
    val letterId = d?.summary?.id
    val isOpenedAlready = d?.summary?.let { it.readAt != null || it.status == "read" } ?: false
    val canUnseal = d != null && viewerId != null && d.summary.recipient?.id == viewerId
    // 仅以 letterId 为 key,避免后台 reload 翻转 readAt 触发 phase 重置(会吞掉 FoldingBack)。
    // 初始相位在 letterId 首次非空时定型,后续不再被外部状态变更覆盖。
    var phase by rememberSaveable(letterId) {
        mutableStateOf(
            if (canUnseal && !isOpenedAlready) ReadingPhase.Sealed else ReadingPhase.Reading,
        )
    }

    val foldProgress = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if (phase == ReadingPhase.FoldingBack) {
            foldProgress.snapTo(0f)
            foldProgress.animateTo(1f, tween(850, easing = LinearOutSlowInEasing))
            onBack()
        }
    }

    val handleBack: () -> Unit = {
        when (phase) {
            ReadingPhase.Reading -> phase = ReadingPhase.FoldingBack
            ReadingPhase.FoldingBack -> Unit  // 动画进行中,忽略重复点击
            else -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.colors.paperDeep),
        ) {
            ReadingTopBar(
                title = state.detail?.summary?.preview?.take(18) ?: "信件",
                trailing = state.detail?.summary?.let { s ->
                    formatLocalDateTime(s.deliveredAt ?: s.deliveryAt ?: s.sentAt) ?: ""
                } ?: "",
                onBack = handleBack,
            )

            if (d == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PaperLoadingHint()
                }
            } else {
                when (phase) {
                    ReadingPhase.Sealed, ReadingPhase.Opening -> {
                        val view = d.toEnvelopeView(mine = false)
                        UnsealOverlay(
                            view = view,
                            phase = if (phase == ReadingPhase.Sealed) UnsealPhase.Sealed else UnsealPhase.Opening,
                            onClickWhenSealed = {
                                // 用户主动拆封 —— 此刻才把 delivered → read,扣减未读计数
                                onMarkRead()
                                phase = ReadingPhase.Opening
                            },
                            onOpenComplete = { phase = ReadingPhase.Reading },
                        )
                    }
                    ReadingPhase.Reading, ReadingPhase.FoldingBack -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 32.dp, vertical = 40.dp)
                                .foldBackTransform(if (phase == ReadingPhase.FoldingBack) foldProgress.value else 0f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LetterPaper(detail = d, stickers = state.stickers)

                            Spacer(Modifier.height(24.dp))

                            ActionBar(
                                detail = d,
                                viewerIsRecipient = viewerId != null && d.summary.recipient?.id == viewerId,
                                onReply = { onReply(d.summary.sender?.handle) },
                                onToggleFavorite = onToggleFavorite,
                                onShowFolderPicker = onShowFolderPicker,
                                onHide = onHide,
                                onUnhide = onUnhide,
                            )

                            if (state.events.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                EventsSection(events = state.events)
                            }
                        }
                    }
                }
            }
        }
        PaperToastHost(toast, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showFolderPicker) {
        val tokens = LuvtterTheme.tokens
        PaperDialog(
            onDismissRequest = onDismissFolderPicker,
            title = "归 · 入 · 卷 · 宗",
            subtitle = "ASSIGN FOLDER",
            actions = { PaperGhostButton(label = "关 闭", onClick = onDismissFolderPicker) },
        ) {
            if (state.folders.isEmpty()) {
                Text(
                    "还没有卷宗,请先到「分 类」标签建一卷。",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 13.sp,
                        color = tokens.colors.inkSoft,
                    ),
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    PaperListRow(onClick = { onAssignFolder(null) }) {
                        Text(
                            "— 移 出 卷 宗 —",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = tokens.fonts.serifZh,
                                fontSize = 14.sp,
                                color = tokens.colors.seal,
                                letterSpacing = 0.5.sp,
                            ),
                        )
                    }
                    state.folders.forEach { f ->
                        PaperListRow(onClick = { onAssignFolder(f.id) }) {
                            Text(
                                f.name,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = tokens.fonts.serifZh,
                                    fontSize = 14.sp,
                                    color = tokens.colors.ink,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingTopBar(title: String, trailing: String, onBack: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.colors.paper)
            .drawBehind {
                drawRect(
                    color = tokens.colors.ruleSoft,
                    topLeft = Offset(0f, size.height - 0.5f),
                    size = androidx.compose.ui.geometry.Size(size.width, 0.5f),
                )
            }
            .padding(horizontal = 56.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text("← 放回", style = tokens.typography.meta.copy(fontSize = 13.sp, color = tokens.colors.inkSoft))
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = tokens.typography.meta.copy(color = tokens.colors.inkFaded))
        Spacer(Modifier.weight(1f))
        Text(trailing, style = tokens.typography.meta.copy(color = tokens.colors.inkGhost))
    }
}

@Composable
private fun LetterPaper(detail: LetterDetailDto, stickers: List<StickerDto>) {
    val tokens = LuvtterTheme.tokens
    val s = detail.summary
    val stationery = Stationery.byId(s.stationeryCode ?: "cream")

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .shadow(20.dp, RectangleShape, ambientColor = Color(0xFF1E140A).copy(alpha = 0.12f))
            .background(stationery.tint)
            .stationeryRules(stationery.rule, tokens.colors.inkFaded)
            .padding(horizontal = 56.dp, vertical = 56.dp),
    ) {
        ReadingHeader(
            recipientName = s.recipient?.displayName ?: "—",
            recipientAddressLabel = s.recipientAddressLabel,
            senderAddressLabel = s.senderAddressLabel,
            sentAt = formatLocalDate(s.sentAt) ?: "—",
            postmarkCity = s.recipient?.displayName?.take(2) ?: "",
            postmarkDate = formatLocalDate(s.sentAt) ?: "—",
        )

        Spacer(Modifier.height(28.dp))
        DividerSoft()
        Spacer(Modifier.height(24.dp))

        ReadingBody(detail = detail)

        if (detail.attachments.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            DividerSoft()
            Spacer(Modifier.height(12.dp))
            AttachmentsSection(detail.attachments, stickers)
        }

        Spacer(Modifier.height(40.dp))
        BottomOrnament(initial = s.sender?.displayName?.firstOrNull()?.toString() ?: "·")
    }
}

@Composable
private fun ReadingHeader(
    recipientName: String,
    recipientAddressLabel: String?,
    senderAddressLabel: String?,
    sentAt: String,
    postmarkCity: String,
    postmarkDate: String,
) {
    val tokens = LuvtterTheme.tokens
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "致 · ${recipientAddressLabel?.takeIf { it.isNotBlank() } ?: ""}",
                style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$recipientName 收",
                style = tokens.typography.caption.copy(
                    fontSize = 14.sp,
                    color = tokens.colors.inkSoft,
                    fontStyle = FontStyle.Italic,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "自 · ${senderAddressLabel?.takeIf { it.isNotBlank() } ?: ""}   ✕   $sentAt",
                style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
            )
        }
        Postmark(
            city = postmarkCity,
            date = postmarkDate,
            size = 78.dp,
            rotateDeg = 8f,
            ink = tokens.colors.stampInk,
        )
    }
}

@Composable
private fun ReadingBody(detail: LetterDetailDto) {
    val tokens = LuvtterTheme.tokens
    when (detail.contentType) {
        "scan", "handwriting" -> ScannedBody(
            label = if (detail.contentType == "scan") "扫描信" else "手写信",
            url = detail.bodyUrl,
        )
        else -> {
            val text = buildAnnotatedString {
                detail.body?.segments?.forEach { seg ->
                    if (seg.style == "strikethrough") {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(seg.text) }
                    } else append(seg.text)
                }
            }
            if (text.text.isEmpty()) {
                Text(
                    detail.bodyUrl ?: "（无正文）",
                    style = tokens.typography.body.copy(color = tokens.colors.inkFaded),
                )
            } else {
                Text(
                    text,
                    style = tokens.typography.body,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BottomOrnament(initial: String) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(tokens.colors.rule.copy(alpha = 0.4f)),
        )
        WaxSeal(text = initial, size = 42.dp, broken = true)
    }
}

@Composable
private fun DividerSoft() {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(tokens.colors.rule.copy(alpha = 0.4f)),
    )
}

internal fun Modifier.stationeryRules(rule: StationeryRule, lineColor: Color): Modifier =
    this.drawBehind {
        when (rule) {
            StationeryRule.Lines -> {
                val step = 32f
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.12f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5f,
                    )
                    y += step
                }
            }
            StationeryRule.Grid -> {
                val step = 32f
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.5f,
                    )
                    y += step
                }
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 0.5f,
                    )
                    x += step
                }
            }
            StationeryRule.Tatebun, StationeryRule.None -> Unit
        }
    }

@Composable
private fun ActionBar(
    detail: LetterDetailDto,
    viewerIsRecipient: Boolean,
    onReply: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowFolderPicker: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
) {
    val s = detail.summary
    Row(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewerIsRecipient && (s.status == "delivered" || s.status == "read")) {
            PaperPrimaryButton(label = "回 · 信", onClick = onReply)
        }
        PaperGhostButton(
            label = if (s.isFavorite) "取 消 收 藏" else "收 · 藏",
            onClick = onToggleFavorite,
        )
        PaperGhostButton(label = "归 · 卷", onClick = onShowFolderPicker)
        if (s.hidden) {
            PaperGhostButton(label = "复 · 出", onClick = onUnhide)
        } else if (s.status == "delivered" || s.status == "read" || s.status == "in_transit") {
            PaperGhostButton(label = "隐 · 藏", onClick = onHide, danger = true)
        }
    }
}

@Composable
private fun EventsSection(events: List<com.luvtter.contract.dto.LetterEventDto>) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth(),
    ) {
        Text("途中事件", style = tokens.typography.title.copy(fontSize = 16.sp))
        Spacer(Modifier.height(8.dp))
        events.forEach { e ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(e.title ?: e.eventType, style = tokens.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp))
                e.content?.let {
                    Text(it, style = tokens.typography.body.copy(fontSize = 13.sp, lineHeight = 20.sp, color = tokens.colors.inkSoft))
                }
                Text(
                    formatLocalDateTime(e.visibleAt) ?: e.visibleAt,
                    style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkGhost),
                )
            }
        }
    }
}

@Composable
private fun ScannedBody(label: String, url: String?) {
    val tokens = LuvtterTheme.tokens
    if (url == null) {
        Text("（$label 内容缺失）", style = tokens.typography.body.copy(color = tokens.colors.inkFaded))
        return
    }
    val isImage = Regex("\\.(jpe?g|png|webp|gif)(\\?|$)").containsMatchIn(url)
    Text(label, style = tokens.typography.meta.copy(color = tokens.colors.inkSoft))
    Spacer(Modifier.height(8.dp))
    if (isImage) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
    } else {
        val uri = LocalUriHandler.current
        PaperGhostButton(
            label = if (label == "扫描信") "在 浏 览 器 打 开 PDF" else "打 开 原 始 文 件",
            onClick = { uri.openUri(url) },
        )
    }
}

@Composable
private fun AttachmentsSection(
    attachments: List<AttachmentDto>,
    stickers: List<StickerDto>,
) {
    val tokens = LuvtterTheme.tokens
    val photos = attachments.filter { it.attachmentType == "photo" }
    val stickerAtts = attachments.filter { it.attachmentType == "sticker" }

    if (stickerAtts.isNotEmpty()) {
        Text("贴纸", style = tokens.typography.meta.copy(color = tokens.colors.inkSoft))
        Spacer(Modifier.height(4.dp))
        stickerAtts.forEach { att ->
            val name = stickers.firstOrNull { it.id == att.stickerId }?.name ?: "贴纸"
            Text("· $name · ${att.weight}g", style = tokens.typography.body.copy(fontSize = 13.sp, lineHeight = 20.sp))
        }
        Spacer(Modifier.height(8.dp))
    }
    if (photos.isNotEmpty()) {
        Text("图片", style = tokens.typography.meta.copy(color = tokens.colors.inkSoft))
        Spacer(Modifier.height(4.dp))
        photos.forEach { att ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                val url = att.mediaUrl
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                } else {
                    Text("（缺少图片地址）", style = tokens.typography.body.copy(fontSize = 13.sp))
                }
                Text("${att.weight}g", style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkGhost))
            }
        }
    }
}

private fun LetterDetailDto.toEnvelopeView(mine: Boolean): EnvelopeView {
    val s = summary
    val direction = if (mine) EnvelopeView.Direction.Outgoing else EnvelopeView.Direction.Incoming
    val party = if (mine) s.recipient else s.sender
    val partyName = party?.displayName?.takeIf { it.isNotBlank() } ?: "未知"
    val partyInitial = partyName.firstOrNull()?.toString() ?: "·"
    val city = s.recipientAddressLabel?.takeIf { it.isNotBlank() } ?: "—"
    val time = formatLocalDate(s.deliveredAt ?: s.deliveryAt ?: s.sentAt) ?: "—"
    val stamp = Stamps.byId(s.stampCode ?: "airmail")
    return EnvelopeView(
        direction = direction,
        partyName = partyName,
        partyInitial = partyInitial,
        cityOrAddress = city,
        sentAt = time,
        stamp = stamp,
        opened = s.readAt != null || s.status == "read",
    )
}
