package com.luvtter.app.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.PaperEmptyHint
import com.luvtter.app.ui.common.PaperFieldLabel
import com.luvtter.app.ui.common.PaperGhostButton
import com.luvtter.app.ui.common.PaperInput
import com.luvtter.app.ui.common.PaperListRow
import com.luvtter.app.ui.common.PaperPageScaffold
import com.luvtter.app.ui.common.PaperPrimaryButton
import com.luvtter.app.ui.common.PaperSectionHeader
import com.luvtter.app.ui.common.PaperStatusBar
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    vm: ContactsViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sessionUser = vm.session.collectAsStateWithLifecycle().value?.user
    val serverOnlyFriends = sessionUser?.onlyFriends ?: false
    var onlyFriends by remember(serverOnlyFriends) { mutableStateOf(serverOnlyFriends) }
    ContactsContent(
        state = state,
        onlyFriends = onlyFriends,
        onBack = onBack,
        onOnlyFriendsChange = { v ->
            onlyFriends = v
            vm.setOnlyFriends(v) { onlyFriends = !v }
        },
        onLookupHandleChange = vm::onLookupHandleChange,
        onLookup = vm::lookup,
        onNoteChange = vm::onNoteChange,
        onAddAsContact = vm::addAsContact,
        onBlockLookupResult = vm::blockLookupResult,
        onDeleteContact = vm::deleteContact,
        onUnblock = vm::unblock,
    )
}

@Composable
private fun ContactsContent(
    state: ContactsUiState,
    onlyFriends: Boolean,
    onBack: () -> Unit,
    onOnlyFriendsChange: (Boolean) -> Unit,
    onLookupHandleChange: (String) -> Unit,
    onLookup: () -> Unit,
    onNoteChange: (String) -> Unit,
    onAddAsContact: () -> Unit,
    onBlockLookupResult: () -> Unit,
    onDeleteContact: (String) -> Unit,
    onUnblock: (String) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    PaperPageScaffold(title = "联 · 系 · 人", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp)
                .padding(bottom = 48.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            ) {
                Column {
                    // ── 收信偏好 ──
                    PaperSectionHeader("收 · 信 · 偏 · 好  PREFERENCE")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "仅好友可寄信给我",
                                style = TextStyle(
                                    fontFamily = tokens.fonts.serifZh,
                                    fontSize = 15.sp,
                                    color = tokens.colors.ink,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp,
                                ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "开启后,陌生人无法投递信件至你的信箱",
                                style = tokens.typography.meta.copy(
                                    fontSize = 11.sp,
                                    color = tokens.colors.inkFaded,
                                ),
                            )
                        }
                        Switch(
                            checked = onlyFriends,
                            onCheckedChange = onOnlyFriendsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = tokens.colors.paperRaised,
                                checkedTrackColor = tokens.colors.seal,
                                uncheckedThumbColor = tokens.colors.paperRaised,
                                uncheckedTrackColor = tokens.colors.inkFaded.copy(alpha = 0.5f),
                                uncheckedBorderColor = tokens.colors.inkFaded.copy(alpha = 0.5f),
                            ),
                        )
                    }

                    // ── 查找用户 ──
                    PaperSectionHeader("查 · 寻 · 旧 · 友  SEARCH")
                    PaperFieldLabel("HANDLE")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        PaperInput(
                            value = state.lookupHandle,
                            onValueChange = onLookupHandleChange,
                            placeholder = "@somebody",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.widthIn(min = 12.dp, max = 12.dp))
                        PaperPrimaryButton(
                            label = if (state.loading) "查 · 找 中" else "查 · 找",
                            onClick = onLookup,
                            enabled = !state.loading && state.lookupHandle.isNotBlank(),
                        )
                    }

                    state.lookupResult?.let { lr ->
                        Spacer(Modifier.height(16.dp))
                        LookupCard(
                            displayName = lr.user.displayName,
                            handle = lr.user.handle,
                            acceptsFromMe = lr.acceptsFromMe,
                            note = state.note,
                            loading = state.loading,
                            onNoteChange = onNoteChange,
                            onAdd = onAddAsContact,
                            onBlock = onBlockLookupResult,
                        )
                    }

                    state.status?.let { PaperStatusBar(it) }

                    // ── 联系人列表 ──
                    PaperSectionHeader(
                        "已 · 添 · 加  CONTACTS",
                        hint = if (state.contacts.isEmpty()) null else "${state.contacts.size}",
                    )
                    if (state.contacts.isEmpty()) {
                        PaperEmptyHint("尚未结识。")
                    } else {
                        state.contacts.forEach { c ->
                            PaperListRow {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            c.target.displayName,
                                            style = TextStyle(
                                                fontFamily = tokens.fonts.serifZh,
                                                fontSize = 15.sp,
                                                color = tokens.colors.ink,
                                                fontWeight = FontWeight.Medium,
                                                letterSpacing = 0.4.sp,
                                            ),
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "@${c.target.handle}",
                                            style = tokens.typography.meta.copy(
                                                fontSize = 11.sp,
                                                color = tokens.colors.inkFaded,
                                            ),
                                        )
                                        c.note?.takeIf { it.isNotBlank() }?.let {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                it,
                                                style = tokens.typography.caption.copy(
                                                    fontSize = 12.sp,
                                                    color = tokens.colors.inkSoft,
                                                ),
                                            )
                                        }
                                    }
                                    PaperGhostButton(
                                        label = "删 除",
                                        onClick = { onDeleteContact(c.id) },
                                        danger = true,
                                    )
                                }
                            }
                        }
                    }

                    // ── 屏蔽 ──
                    PaperSectionHeader(
                        "已 · 屏 · 蔽  BLOCKED",
                        hint = if (state.blocks.isEmpty()) null else "${state.blocks.size}",
                    )
                    if (state.blocks.isEmpty()) {
                        PaperEmptyHint("没有屏蔽任何人。")
                    } else {
                        state.blocks.forEach { b ->
                            PaperListRow {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            b.target.displayName,
                                            style = TextStyle(
                                                fontFamily = tokens.fonts.serifZh,
                                                fontSize = 15.sp,
                                                color = tokens.colors.ink,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                        )
                                        Text(
                                            "@${b.target.handle}",
                                            style = tokens.typography.meta.copy(
                                                fontSize = 11.sp,
                                                color = tokens.colors.inkFaded,
                                            ),
                                        )
                                    }
                                    PaperGhostButton(
                                        label = "解 除",
                                        onClick = { onUnblock(b.target.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookupCard(
    displayName: String,
    handle: String,
    acceptsFromMe: Boolean,
    note: String,
    loading: Boolean,
    onNoteChange: (String) -> Unit,
    onAdd: () -> Unit,
    onBlock: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.colors.paperRaised)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            displayName,
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 16.sp,
                color = tokens.colors.ink,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "@$handle",
            style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.inkFaded),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (acceptsFromMe) "○ 可接收你的来信" else "● 对方未接受陌生来信",
            style = tokens.typography.meta.copy(
                fontSize = 11.sp,
                color = if (acceptsFromMe) tokens.colors.inkSoft else tokens.colors.seal,
            ),
        )
        Spacer(Modifier.height(14.dp))
        PaperFieldLabel("备 注（可选）")
        PaperInput(
            value = note,
            onValueChange = onNoteChange,
            placeholder = "如何想起这位朋友",
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperPrimaryButton(
                label = "加 为 联 系 人",
                onClick = onAdd,
                enabled = !loading,
            )
            PaperGhostButton(
                label = "屏 蔽",
                onClick = onBlock,
                enabled = !loading,
                danger = true,
            )
        }
    }
}
