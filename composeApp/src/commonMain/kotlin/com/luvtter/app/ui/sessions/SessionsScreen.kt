package com.luvtter.app.ui.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.PaperEmptyHint
import com.luvtter.app.ui.common.PaperGhostButton
import com.luvtter.app.ui.common.PaperListRow
import com.luvtter.app.ui.common.PaperPageScaffold
import com.luvtter.app.ui.common.PaperSectionHeader
import com.luvtter.app.ui.common.PaperStatusBar
import com.luvtter.app.ui.common.formatLocalDateTime
import com.luvtter.contract.dto.SessionDto
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    vm: SessionsViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    SessionsContent(
        state = state,
        onBack = onBack,
        onRefresh = vm::refresh,
        onRevoke = vm::revoke,
        onClearError = vm::clearError,
    )
}

@Composable
private fun SessionsContent(
    state: SessionsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit,
    onClearError: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    PaperPageScaffold(
        title = "已 · 登 · 录 · 设 · 备",
        onBack = onBack,
        trailing = {
            TextButton(
                enabled = !state.loading,
                onClick = onRefresh,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    if (state.loading) "刷 新 中" else "刷 新",
                    style = tokens.typography.meta.copy(
                        fontSize = 12.sp,
                        color = if (state.loading) tokens.colors.inkGhost else tokens.colors.inkSoft,
                    ),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp)
                .padding(bottom = 48.dp)
                .widthIn(max = 720.dp),
        ) {
            PaperSectionHeader("说 · 明  NOTE")
            Text(
                "撤销某条会话后,对应设备的 refresh token 立即失效,需要重新登录。" +
                    "已签发的 access token 直到过期(默认 1 小时)仍可能有效。",
                style = TextStyle(
                    fontFamily = tokens.fonts.serifZh,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = tokens.colors.inkFaded,
                    letterSpacing = 0.3.sp,
                ),
            )

            state.error?.let {
                PaperStatusBar(it)
                Spacer(Modifier.height(4.dp))
                Row {
                    PaperGhostButton(label = "知 道 了", onClick = onClearError)
                }
            }

            PaperSectionHeader(
                "活 · 跃 · 会 · 话  ACTIVE",
                hint = if (state.sessions.isEmpty()) null else "${state.sessions.size}",
            )
            when {
                state.loading && state.sessions.isEmpty() -> PaperEmptyHint("加载中…")
                state.sessions.isEmpty() -> PaperEmptyHint("尚无活跃会话。")
                else -> state.sessions.forEach { s ->
                    PaperListRow {
                        SessionRow(
                            session = s,
                            revoking = state.revokingId == s.id,
                            onRevoke = { onRevoke(s.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionDto, revoking: Boolean, onRevoke: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val device = session.deviceName ?: "未知设备"
            val platform = session.platform?.let { " · $it" } ?: ""
            Text(
                "$device$platform",
                style = TextStyle(
                    fontFamily = tokens.fonts.serifZh,
                    fontSize = 15.sp,
                    color = tokens.colors.ink,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "最近活跃 · ${formatLocalDateTime(session.lastActiveAt) ?: session.lastActiveAt}",
                style = tokens.typography.meta.copy(
                    fontSize = 11.sp,
                    color = tokens.colors.inkFaded,
                ),
            )
            Text(
                "到期 · ${formatLocalDateTime(session.expiresAt) ?: session.expiresAt}",
                style = tokens.typography.meta.copy(
                    fontSize = 11.sp,
                    color = tokens.colors.inkGhost,
                ),
            )
        }
        PaperGhostButton(
            label = if (revoking) "撤 销 中" else "撤 销",
            onClick = onRevoke,
            enabled = !revoking,
            danger = true,
        )
    }
}
