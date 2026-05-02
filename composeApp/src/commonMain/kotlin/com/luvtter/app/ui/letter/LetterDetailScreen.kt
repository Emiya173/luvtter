package com.luvtter.app.ui.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.luvtter.app.ui.common.formatLocalDate
import com.luvtter.app.ui.common.formatLocalDateTime
import com.luvtter.contract.dto.AttachmentDto
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.StickerDto
import org.koin.compose.viewmodel.koinViewModel

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
        onUnhide = { vm.unhide(onBack) }
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
) {
    val tokens = LuvtterTheme.tokens
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
            onBack = onBack,
        )

        val d = state.detail
        if (d == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.error != null) {
                    Text(state.error, color = tokens.colors.seal)
                } else {
                    Text("加载中…", color = tokens.colors.inkFaded)
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
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
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewerIsRecipient && (s.status == "delivered" || s.status == "read")) {
            Button(onClick = onReply) { Text("回信") }
            Spacer(Modifier.width(12.dp))
        }
        OutlinedButton(onClick = onToggleFavorite) {
            Text(if (s.isFavorite) "取消收藏" else "收藏此信")
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onShowFolderPicker) { Text("移到分类") }
        if (s.hidden) {
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onUnhide) { Text("恢复") }
        } else if (s.status == "delivered" || s.status == "read" || s.status == "in_transit") {
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onHide) { Text("隐藏") }
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
        OutlinedButton(onClick = { uri.openUri(url) }) {
            Text(if (label == "扫描信") "在浏览器中打开 PDF" else "打开原始文件")
        }
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
