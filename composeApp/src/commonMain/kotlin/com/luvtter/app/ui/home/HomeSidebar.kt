package com.luvtter.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme

/**
 * 首页左侧 240dp 侧栏。webui/luvtter.html Sidebar。
 *
 * 顶部 brand,中部主导航(收件/发件/草稿/收藏/分类),底部账户与工具按钮。
 */
@Composable
fun HomeSidebar(
    selected: HomeTab,
    onSelectTab: (HomeTab) -> Unit,
    unread: Int,
    handleLabel: String,
    needsFinalize: Boolean,
    onOpenNotifications: () -> Unit,
    onSearch: () -> Unit,
    onContacts: () -> Unit,
    onAddresses: () -> Unit,
    onSessions: () -> Unit,
    onCompose: () -> Unit,
    onExport: () -> Unit,
    exporting: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(tokens.colors.paper)
            .drawBehind {
                drawLine(
                    color = tokens.colors.ruleSoft,
                    start = Offset(size.width - 0.5f, 0f),
                    end = Offset(size.width - 0.5f, size.height),
                    strokeWidth = 0.5f,
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(top = 28.dp, bottom = 24.dp),
    ) {
        // ── Brand block ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp)
                .drawBehind {
                    drawLine(
                        color = tokens.colors.ruleSoft,
                        start = Offset(0f, size.height + 8f),
                        end = Offset(size.width, size.height + 8f),
                        strokeWidth = 0.5f,
                    )
                },
        ) {
            Text(
                "luvtter",
                style = tokens.typography.brand.copy(fontSize = 24.sp, fontStyle = FontStyle.Italic),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "慢 · 一 · 拍",
                style = tokens.typography.meta.copy(
                    fontSize = 9.sp,
                    color = tokens.colors.inkFaded.copy(alpha = 0.7f),
                ),
            )
        }
        Spacer(Modifier.height(20.dp))

        // ── Compose CTA ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCompose)
                    .background(tokens.colors.seal)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✎  写一封信",
                    style = tokens.typography.title.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.colors.paperRaised,
                        letterSpacing = 1.12.sp,
                    ),
                )
            }
        }

        // ── Primary nav ───────────────────────────────────────────
        Column {
            HomeTab.entries.forEach { tab ->
                NavItem(
                    glyph = glyphFor(tab),
                    label = tab.label,
                    selected = tab == selected,
                    badgeCount = if (tab == HomeTab.Inbox) unread else 0,
                    onClick = { onSelectTab(tab) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Secondary actions ─────────────────────────────────────
        SectionHeader("工具 · TOOLS")
        SmallNavItem("搜索", onClick = onSearch)
        SmallNavItem("联系人", onClick = onContacts)
        SmallNavItem("地址", onClick = onAddresses)
        SmallNavItem("会话", onClick = onSessions)
        SmallNavItem(
            label = if (exporting) "导出中…" else "导出全部",
            onClick = onExport,
            enabled = !exporting,
        )
        SmallNavItem("刷新", onClick = onRefresh)

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.weight(1f, fill = false))

        // ── Account footer ────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 16.dp)
                .drawBehind {
                    drawLine(
                        color = tokens.colors.ruleSoft,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.5f,
                    )
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                    TextButton(
                        onClick = onOpenNotifications,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "铃",
                            style = tokens.typography.title.copy(
                                fontSize = 14.sp,
                                color = tokens.colors.inkSoft,
                            ),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onLogout, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("退出", style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.inkFaded))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                handleLabel,
                style = tokens.typography.title.copy(
                    fontSize = 14.sp,
                    color = tokens.colors.ink,
                    letterSpacing = 0.56.sp,
                ),
            )
            if (needsFinalize) {
                Text(
                    "临时 handle",
                    style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.seal),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    val accent = if (selected) tokens.colors.seal else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                drawLine(
                    color = accent,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.5f,
                )
            }
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(
                    width = 0.5.dp,
                    color = if (selected) tokens.colors.ink else tokens.colors.inkFaded,
                    shape = RoundedCornerShape(0.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                glyph,
                maxLines = 1,
                style = tokens.typography.title.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.sp,
                    color = if (selected) tokens.colors.ink else tokens.colors.inkFaded,
                ),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = tokens.typography.title.copy(
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) tokens.colors.ink else tokens.colors.inkFaded,
                letterSpacing = 1.28.sp,
            ),
            modifier = Modifier.weight(1f),
        )
        if (badgeCount > 0) {
            Badge { Text("$badgeCount", style = tokens.typography.meta.copy(fontSize = 9.sp)) }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    val tokens = LuvtterTheme.tokens
    Text(
        label,
        style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 6.dp),
    )
}

@Composable
private fun SmallNavItem(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val tokens = LuvtterTheme.tokens
    val color = if (enabled) tokens.colors.inkFaded else tokens.colors.inkGhost
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = tokens.typography.body.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = color,
                letterSpacing = 0.52.sp,
            ),
        )
    }
}

private fun glyphFor(tab: HomeTab): String = when (tab) {
    HomeTab.Inbox -> "收"
    HomeTab.Outbox -> "寄"
    HomeTab.Drafts -> "稿"
    HomeTab.Favorites -> "藏"
    HomeTab.Folders -> "类"
}
