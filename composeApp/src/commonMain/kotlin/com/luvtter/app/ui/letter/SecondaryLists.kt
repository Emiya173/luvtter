package com.luvtter.app.ui.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.PaperGhostButton
import com.luvtter.app.ui.common.formatLocalDate
import com.luvtter.contract.dto.LetterSummaryDto

// =====================================================================
// 草稿列表 — 半页未封缄手稿:撕边底、handZh 预览、左上"未封"红章
// =====================================================================

@Composable
fun DraftsList(
    letters: List<LetterSummaryDto>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(tokens.colors.paper, tokens.colors.paperDeep))
            ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(letters, key = { it.id }) { l ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                DraftHalfSheet(
                    letter = l,
                    onClick = { onEdit(l.id) },
                    onDelete = { onDelete(l.id) },
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DraftHalfSheet(
    letter: LetterSummaryDto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    val stationery = Stationery.byId(letter.stationeryCode ?: "cream")
    val tornColor = stationery.tint

    Column(
        modifier = modifier
            .shadow(8.dp, RectangleShape, ambientColor = Color(0xFF1E140A).copy(alpha = 0.10f))
            .background(stationery.tint)
            .stationeryRules(stationery.rule, tokens.colors.inkFaded.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        // 头部:致 / 红章
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "致 · ${letter.recipient?.displayName ?: "未填收件人"}",
                    style = TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 16.sp,
                        color = tokens.colors.ink,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                    ),
                )
                letter.recipientAddressLabel?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "寄到 · $it",
                        style = tokens.typography.meta.copy(
                            fontSize = 10.sp,
                            color = tokens.colors.inkFaded,
                        ),
                    )
                }
            }
            UnsealedStamp()
        }

        Spacer(Modifier.height(18.dp))

        // 手写预览,若无 preview 则灰幽灵字
        val previewText = letter.preview?.takeIf { it.isNotBlank() } ?: "（尚未落笔）"
        Text(
            previewText,
            maxLines = 4,
            style = TextStyle(
                fontFamily = tokens.fonts.handZh,
                fontSize = 16.sp,
                lineHeight = 28.sp,
                color = if (letter.preview.isNullOrBlank()) tokens.colors.inkGhost else tokens.colors.inkSoft,
                letterSpacing = 0.4.sp,
            ),
        )

        Spacer(Modifier.height(18.dp))

        // 底部信息行
        Row(verticalAlignment = Alignment.CenterVertically) {
            val time = letter.sentAt ?: letter.deliveryAt
            Text(
                time?.let { "稿 · ${formatLocalDate(it) ?: it}" } ?: "稿 · 未署日",
                style = tokens.typography.meta.copy(
                    fontSize = 10.sp,
                    color = tokens.colors.inkFaded,
                ),
            )
            Spacer(Modifier.weight(1f))
            PaperGhostButton(label = "弃 · 稿", onClick = onDelete, danger = true)
        }

        Spacer(Modifier.height(8.dp))
        // 撕边底:沿底部的不规则锯齿
        TornBottomEdge(color = tornColor)
    }
}

@Composable
private fun UnsealedStamp() {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 32.dp)
            .border(0.8.dp, tokens.colors.seal, RoundedCornerShape(2.dp))
            .background(tokens.colors.paperRaised.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "未 · 封",
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.colors.seal,
                letterSpacing = 1.sp,
            ),
        )
    }
}

@Composable
private fun TornBottomEdge(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .drawBehind {
                val path = Path()
                val w = size.width
                val h = size.height
                val step = 8f
                path.moveTo(0f, 0f)
                var x = 0f
                var up = true
                while (x < w) {
                    val nextX = (x + step).coerceAtMost(w)
                    val y = if (up) h * 0.45f else h
                    path.lineTo(nextX, y)
                    x = nextX
                    up = !up
                }
                path.lineTo(w, 0f)
                path.close()
                drawPath(path, color = color)
            },
    )
}

// =====================================================================
// 收藏列表 — 书签条:左侧 seal 红丝带 + ★ + 寄件人/预览/时间
// =====================================================================

@Composable
fun FavoritesList(
    letters: List<LetterSummaryDto>,
    isLetterMine: (LetterSummaryDto) -> Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.paper),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(letters, key = { it.id }) { l ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                FavoriteSlip(
                    letter = l,
                    mine = isLetterMine(l),
                    onClick = { onOpen(l.id) },
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FavoriteSlip(
    letter: LetterSummaryDto,
    mine: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = modifier
            .background(tokens.colors.paperRaised)
            .drawBehind {
                drawLine(
                    color = tokens.colors.ruleSoft,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 0.5f,
                )
            }
            .clickable(onClick = onClick)
            .padding(end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 红丝带
        BookmarkRibbon()
        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "★",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = tokens.colors.seal,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                val who = if (mine) (letter.recipient?.displayName ?: "—") else (letter.sender?.displayName ?: "—")
                Text(
                    who,
                    style = TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 15.sp,
                        color = tokens.colors.ink,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                    ),
                )
            }
            letter.preview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = tokens.colors.inkSoft,
                        letterSpacing = 0.3.sp,
                    ),
                )
            }
        }

        val time = letter.deliveredAt ?: letter.deliveryAt ?: letter.sentAt
        if (time != null) {
            Text(
                formatLocalDate(time) ?: time,
                style = tokens.typography.meta.copy(
                    fontSize = 10.sp,
                    color = tokens.colors.inkFaded,
                ),
            )
        }
    }
}

@Composable
private fun BookmarkRibbon() {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier
            .width(10.dp)
            .fillMaxHeight()
            .drawBehind {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, size.height - 6f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = tokens.colors.seal)
                // 缝线高光
                drawLine(
                    color = tokens.colors.paperRaised.copy(alpha = 0.5f),
                    start = Offset(size.width * 0.5f, 4f),
                    end = Offset(size.width * 0.5f, size.height - 10f),
                    strokeWidth = 0.5f,
                )
            }
            .height(56.dp),
    )
}

// =====================================================================
// 分类列表 — 卷宗目录:每条信件是带顶部小标签的牛皮纸卡片
// =====================================================================

@Composable
fun FolderShelf(
    folderName: String?,
    letters: List<LetterSummaryDto>,
    isLetterMine: (LetterSummaryDto) -> Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(tokens.colors.paper, tokens.colors.paperDeep))
            ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (folderName != null) {
            item(key = "__folder_header__") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    FolderHeader(
                        name = folderName,
                        count = letters.size,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    )
                }
            }
        }
        itemsIndexed(letters, key = { _, l -> l.id }) { index, l ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                FolderEntryCard(
                    letter = l,
                    mine = isLetterMine(l),
                    indexNo = index + 1,
                    onClick = { onOpen(l.id) },
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FolderHeader(name: String, count: Int, modifier: Modifier = Modifier) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .drawBehind {
                    drawLine(
                        color = tokens.colors.ink.copy(alpha = 0.55f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 0.5f,
                    )
                },
        )
        Text(
            "卷 · $name · 共 $count 封",
            modifier = Modifier.padding(horizontal = 14.dp),
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 13.sp,
                color = tokens.colors.ink,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
            ),
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .drawBehind {
                    drawLine(
                        color = tokens.colors.ink.copy(alpha = 0.55f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 0.5f,
                    )
                },
        )
    }
}

@Composable
private fun FolderEntryCard(
    letter: LetterSummaryDto,
    mine: Boolean,
    indexNo: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    val manilaTint = Color(0xFFE8DCB8)

    Column(modifier = modifier) {
        // 顶部标签页(file tab)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(28.dp))
            Box(
                modifier = Modifier
                    .background(manilaTint)
                    .border(0.5.dp, tokens.colors.ink.copy(alpha = 0.3f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(
                    "No. ${indexNo.toString().padStart(2, '0')}",
                    style = tokens.typography.meta.copy(
                        fontSize = 10.sp,
                        color = tokens.colors.ink,
                        letterSpacing = 0.8.sp,
                    ),
                )
            }
        }
        // 卡片主体
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RectangleShape, ambientColor = Color(0xFF1E140A).copy(alpha = 0.10f))
                .background(manilaTint)
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val who = if (mine) (letter.recipient?.displayName ?: "—") else (letter.sender?.displayName ?: "—")
                Text(
                    if (mine) "致 · $who" else "自 · $who",
                    style = TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 15.sp,
                        color = tokens.colors.ink,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                    ),
                )
                if (letter.isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Text("★", style = TextStyle(fontSize = 12.sp, color = tokens.colors.seal))
                }
                Spacer(Modifier.weight(1f))
                val time = letter.deliveredAt ?: letter.deliveryAt ?: letter.sentAt
                if (time != null) {
                    Text(
                        formatLocalDate(time) ?: time,
                        style = tokens.typography.meta.copy(
                            fontSize = 10.sp,
                            color = tokens.colors.inkFaded,
                        ),
                    )
                }
            }
            letter.preview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    maxLines = 2,
                    style = TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = tokens.colors.inkSoft,
                        letterSpacing = 0.3.sp,
                    ),
                )
            }
        }
    }
}

