package com.luvtter.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.formatLocalDateTime
import com.luvtter.app.ui.letter.InboxStack
import com.luvtter.app.ui.letter.OutboxList
import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.FolderDto
import com.luvtter.contract.dto.LetterSummaryDto
import com.luvtter.contract.dto.NotificationDto
import com.luvtter.contract.dto.UserDto
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onCompose: () -> Unit,
    onAddresses: () -> Unit,
    onContacts: () -> Unit,
    onSessions: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val user = vm.session.collectAsStateWithLifecycle().value?.user
    val uriHandler = LocalUriHandler.current
    val onOpenExport: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    var showNotifications by remember { mutableStateOf(false) }
    var showSwitchLocation by remember { mutableStateOf(false) }
    var showFinalizeHandle by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    HomeContent(
        state = state,
        user = user,
        isLetterMine = vm::letterOwnedByMe,
        showNotifications = showNotifications,
        showSwitchLocation = showSwitchLocation,
        showFinalizeHandle = showFinalizeHandle,
        showSearch = showSearch,
        onCompose = onCompose,
        onStartFirstLetter = {
            vm.dismissFirstLetterPrompt()
            onCompose()
        },
        onDismissFirstLetterPrompt = vm::dismissFirstLetterPrompt,
        onAddresses = onAddresses,
        onContacts = onContacts,
        onSessions = onSessions,
        onOpenLetter = onOpenLetter,
        onEditDraft = onEditDraft,
        onLogout = onLogout,
        onOpenNotifications = {
            vm.openNotifications()
            showNotifications = true
        },
        onDismissNotifications = { showNotifications = false },
        onShowSwitchLocation = { showSwitchLocation = true },
        onDismissSwitchLocation = { showSwitchLocation = false },
        onShowFinalizeHandle = { showFinalizeHandle = true },
        onDismissFinalizeHandle = { showFinalizeHandle = false },
        onShowSearch = { showSearch = true },
        onDismissSearch = { showSearch = false },
        onRefreshAll = vm::refreshAll,
        onSelectTab = vm::selectTab,
        onToggleShowHidden = vm::toggleShowHidden,
        onSelectFolder = vm::selectFolder,
        onCreateFolder = vm::createFolder,
        onDeleteFolder = vm::deleteFolder,
        onMarkAllNotificationsRead = vm::markAllNotificationsRead,
        onSwitchCurrentAddress = { addressId ->
            vm.switchCurrentAddress(addressId)
            showNotifications = false
            showSwitchLocation = false
        },
        onFinalizeHandle = { input, onError ->
            vm.finalizeHandle(input, { showFinalizeHandle = false }, onError)
        },
        onOpenLetterFromSearch = { id ->
            showSearch = false
            onOpenLetter(id)
        },
        onDeleteDraft = vm::deleteDraft,
        onExpedite = vm::expedite,
        onHide = vm::hide,
        onUnhide = vm::unhide,
        onRequestExport = {
            vm.requestExport { result -> onOpenExport(result.downloadUrl) }
        },
        onOpenExportLink = onOpenExport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    user: UserDto?,
    isLetterMine: (LetterSummaryDto) -> Boolean,
    showNotifications: Boolean,
    showSwitchLocation: Boolean,
    showFinalizeHandle: Boolean,
    showSearch: Boolean,
    onCompose: () -> Unit,
    onStartFirstLetter: () -> Unit,
    onDismissFirstLetterPrompt: () -> Unit,
    onAddresses: () -> Unit,
    onContacts: () -> Unit,
    onSessions: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit,
    onDismissNotifications: () -> Unit,
    onShowSwitchLocation: () -> Unit,
    onDismissSwitchLocation: () -> Unit,
    onShowFinalizeHandle: () -> Unit,
    onDismissFinalizeHandle: () -> Unit,
    onShowSearch: () -> Unit,
    onDismissSearch: () -> Unit,
    onRefreshAll: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onToggleShowHidden: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onSwitchCurrentAddress: (String) -> Unit,
    onFinalizeHandle: (String, (String) -> Unit) -> Unit,
    onOpenLetterFromSearch: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onExpedite: (String) -> Unit,
    onHide: (String) -> Unit,
    onUnhide: (String) -> Unit,
    onRequestExport: () -> Unit,
    onOpenExportLink: (String) -> Unit,
) {
    val currentAddress = remember(user, state.addresses) {
        state.addresses.firstOrNull { it.id == user?.currentAddressId }
    }
    val handleLabel = user?.handle?.let { "@$it" } ?: "@—"
    val needsFinalize = user?.handleFinalized == false

    Row(modifier = Modifier.fillMaxSize()) {
        HomeSidebar(
            selected = state.tab,
            onSelectTab = onSelectTab,
            unread = state.unread,
            handleLabel = handleLabel,
            needsFinalize = needsFinalize,
            onOpenNotifications = onOpenNotifications,
            onSearch = onShowSearch,
            onContacts = onContacts,
            onAddresses = onAddresses,
            onSessions = onSessions,
            onCompose = onCompose,
            onExport = onRequestExport,
            exporting = state.exporting,
            onRefresh = onRefreshAll,
            onLogout = onLogout,
        )

        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            HomeHeader(
                tab = state.tab,
                lettersCount = state.letters.size,
                unread = state.unread,
                currentAddressLabel = currentAddress?.let { "${it.label} · ${it.type}" } ?: "未设置",
                onSwitchLocation = onShowSwitchLocation,
            )

            if (needsFinalize) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "当前 handle 是临时的，别人寄信时找不到你",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onShowFinalizeHandle) { Text("设置专属 handle") }
                    }
                }
            }

            if (state.showFirstLetterPrompt) {
                FirstLetterPromptCard(onStart = onStartFirstLetter, onDismiss = onDismissFirstLetterPrompt)
            }

            state.lastExport?.let { ex ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已导出 ${ex.letterCount} 封信(${ex.sizeBytes / 1024} KB),链接 ${ex.expiresInSeconds / 60} 分钟内有效",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onOpenExportLink(ex.downloadUrl) }) { Text("打开下载") }
                        }
                        SelectionContainer {
                            Text(
                                ex.downloadUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenExportLink(ex.downloadUrl) },
                            )
                        }
                    }
                }
            }

            if (state.tab == HomeTab.Inbox || state.tab == HomeTab.Outbox) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.showHidden,
                        onClick = onToggleShowHidden,
                        label = { Text(if (state.showHidden) "查看已隐藏" else "正常视图") },
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.showHidden) {
                        Text("已隐藏的信件仍存在于对方的箱子中", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (state.tab == HomeTab.Folders) {
                FolderBar(
                    folders = state.folders,
                    selectedId = state.selectedFolderId,
                    onSelect = onSelectFolder,
                    onCreate = onCreateFolder,
                    onDelete = onDeleteFolder,
                )
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }
            state.reward?.let {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!state.loading && state.letters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val hint = when {
                        state.tab == HomeTab.Favorites -> "还没有收藏的信件"
                        state.tab == HomeTab.Folders && state.folders.isEmpty() -> "还没有分类，先创建一个"
                        state.tab == HomeTab.Folders && state.selectedFolderId == null -> "选择一个分类查看信件"
                        state.tab == HomeTab.Folders -> "该分类暂无信件"
                        state.showHidden -> "没有已隐藏的信件"
                        state.tab == HomeTab.Inbox && currentAddress != null -> "「${currentAddress.label}」当前没有信件"
                        else -> "还没有信件"
                    }
                    Text(hint, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (state.tab == HomeTab.Inbox && !state.showHidden) {
                InboxStack(
                    letters = state.letters,
                    isLetterMine = isLetterMine,
                    onOpen = onOpenLetter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (state.tab == HomeTab.Outbox && !state.showHidden) {
                OutboxList(
                    letters = state.letters,
                    onOpen = onOpenLetter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.letters, key = { it.id }) { l ->
                        val mine = isLetterMine(l)
                        val isDraft = state.tab == HomeTab.Drafts
                        LetterRow(
                            l,
                            mine = mine,
                            onClick = { if (isDraft) onEditDraft(l.id) else onOpenLetter(l.id) },
                            onDeleteDraft = if (isDraft) { { onDeleteDraft(l.id) } } else null,
                            onExpedite = if (mine && l.status == "in_transit") { { onExpedite(l.id) } } else null,
                            onHide = if (!state.showHidden && !l.hidden) { { onHide(l.id) } } else null,
                            onUnhide = if (state.showHidden || l.hidden) { { onUnhide(l.id) } } else null,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showNotifications) {
        NotificationsDialog(
            notifications = state.notifications,
            onDismiss = onDismissNotifications,
            onMarkAllRead = onMarkAllNotificationsRead,
            onSwitchToAddress = onSwitchCurrentAddress
        )
    }

    if (showSwitchLocation) {
        SwitchLocationDialog(
            addresses = state.addresses,
            currentId = user?.currentAddressId,
            onDismiss = onDismissSwitchLocation,
            onPick = onSwitchCurrentAddress
        )
    }

    if (showFinalizeHandle) {
        FinalizeHandleDialog(
            onDismiss = onDismissFinalizeHandle,
            onSubmit = onFinalizeHandle
        )
    }

    if (showSearch) {
        SearchDialog(
            onDismiss = onDismissSearch,
            onOpen = onOpenLetterFromSearch
        )
    }
}

@Composable
private fun HomeHeader(
    tab: HomeTab,
    lettersCount: Int,
    unread: Int,
    currentAddressLabel: String,
    onSwitchLocation: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    val ruleSoft = tokens.colors.ruleSoft
    val title = when (tab) {
        HomeTab.Inbox -> "收件箱"
        HomeTab.Outbox -> "寄件箱"
        HomeTab.Drafts -> "草稿"
        HomeTab.Favorites -> "收藏"
        HomeTab.Folders -> "分类"
    }
    val meta = when (tab) {
        HomeTab.Inbox -> "已收 $lettersCount 封 · 未拆 $unread"
        HomeTab.Outbox -> "在途与既往 $lettersCount 封 · 邮差风雪兼程"
        HomeTab.Drafts -> "${lettersCount} 封未寄出"
        HomeTab.Favorites -> "$lettersCount 封被你折角"
        HomeTab.Folders -> "$lettersCount 封分门别类"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.colors.paper)
            .drawBehind {
                drawLine(
                    color = ruleSoft,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 0.5f,
                )
            }
            .padding(horizontal = 32.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = tokens.typography.title.copy(
                    fontSize = 26.sp,
                    letterSpacing = 1.56.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                meta,
                style = tokens.typography.caption.copy(fontSize = 13.sp, color = tokens.colors.inkFaded),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "当前位置",
                style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    currentAddressLabel,
                    style = tokens.typography.body.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = tokens.colors.inkSoft,
                    ),
                )
                TextButton(
                    onClick = onSwitchLocation,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        "切换",
                        style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.seal),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDialog(
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    vm: SearchViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    SearchDialogContent(
        state = state,
        onDismiss = onDismiss,
        onOpen = onOpen,
        onQueryChange = vm::onQueryChange,
        onSearch = vm::search
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDialogContent(
    state: SearchUiState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索信件") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = { Text("关键词") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !state.busy && state.query.isNotBlank(),
                        onClick = onSearch
                    ) { Text(if (state.busy) "搜索中" else "搜索") }
                }
                state.error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(8.dp))
                if (state.results.isEmpty() && !state.busy && state.query.isNotBlank() && state.error == null) {
                    Text("无匹配", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn {
                    items(state.results, key = { it.id }) { l ->
                        ListItem(
                            headlineContent = {
                                val who = "${l.sender?.displayName ?: "—"} → ${l.recipient?.displayName ?: "—"}"
                                Text(who)
                            },
                            supportingContent = { l.preview?.let { Text(it, maxLines = 2) } },
                            modifier = Modifier.clickable { onOpen(l.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderBar(
    folders: List<FolderDto>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            folders.forEach { f ->
                FilterChip(
                    selected = selectedId == f.id,
                    onClick = { onSelect(if (selectedId == f.id) null else f.id) },
                    label = { Text(f.name) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            TextButton(onClick = { showNew = true }) { Text("+ 新建") }
            Spacer(Modifier.weight(1f))
            if (selectedId != null) {
                TextButton(onClick = { onDelete(selectedId) }) { Text("删除分类") }
            }
        }
    }
    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false; name = "" },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.trim() },
                    label = { Text("名称") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name); name = ""; showNew = false }
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNew = false; name = "" }) { Text("取消") } }
        )
    }
}

@Composable
private fun LetterRow(
    l: LetterSummaryDto,
    mine: Boolean,
    onClick: () -> Unit,
    onExpedite: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onUnhide: (() -> Unit)? = null,
    onDeleteDraft: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val who = if (mine) (l.recipient?.displayName ?: "未知收件人") else (l.sender?.displayName ?: "未知寄件人")
            Text(who, style = MaterialTheme.typography.titleMedium)
            if (l.isFavorite) {
                Spacer(Modifier.width(6.dp))
                Text("★", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = {}, label = { Text(statusLabel(l.status, l.transitStage)) })
        }
        l.recipientAddressLabel?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                if (mine) "→ 寄到「$it」" else "→ 收件地址：$it",
                style = MaterialTheme.typography.labelSmall
            )
        }
        l.preview?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        Spacer(Modifier.height(4.dp))
        val time = l.deliveredAt ?: l.deliveryAt ?: l.sentAt
        Row(verticalAlignment = Alignment.CenterVertically) {
            time?.let {
                Text(formatLocalDateTime(it) ?: it, style = MaterialTheme.typography.labelSmall)
            }
            if (l.photoCount > 0 || l.stickerCount > 0) {
                Spacer(Modifier.weight(1f))
                if (l.photoCount > 0) {
                    Text("📷 ${l.photoCount}", style = MaterialTheme.typography.labelSmall)
                }
                if (l.photoCount > 0 && l.stickerCount > 0) Spacer(Modifier.width(8.dp))
                if (l.stickerCount > 0) {
                    Text("🏷 ${l.stickerCount}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (onExpedite != null || onHide != null || onUnhide != null || onDeleteDraft != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (onExpedite != null) TextButton(onClick = onExpedite) { Text("加速到达") }
                if (onHide != null) TextButton(onClick = onHide) { Text("隐藏") }
                if (onUnhide != null) TextButton(onClick = onUnhide) { Text("恢复") }
                if (onDeleteDraft != null) TextButton(onClick = onDeleteDraft) { Text("删除草稿") }
            }
        }
    }
}

private fun statusLabel(status: String, stage: String?): String = when (status) {
    "draft" -> "草稿"
    "sealed" -> "已封缄"
    "in_transit" -> when (stage) {
        "sending" -> "投递中"
        "on_the_way" -> "在路上"
        "arriving" -> "即将送达"
        else -> "运输中"
    }
    "delivered" -> "已送达"
    "read" -> "已读"
    "hidden" -> "已隐藏"
    else -> status
}

@Composable
private fun NotificationsDialog(
    notifications: List<NotificationDto>,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onSwitchToAddress: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知") },
        text = {
            if (notifications.isEmpty()) {
                Text("暂无通知")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(notifications, key = { it.id }) { n ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(n.title, style = MaterialTheme.typography.bodyMedium)
                            n.preview?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatLocalDateTime(n.createdAt) ?: n.createdAt, style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.weight(1f))
                                if (n.addressId != null) {
                                    TextButton(onClick = { onSwitchToAddress(n.addressId!!) }) { Text("切到 ${n.addressLabel ?: "该地址"}") }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onMarkAllRead) { Text("全部已读") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun FirstLetterPromptCard(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("给未来的自己,寄第一封信", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "信会按你选的邮票慢慢上路,几小时到几天后送达。先写一句话给未来打个招呼?",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("暂不") }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = onStart) { Text("现在写") }
            }
        }
    }
}

@Composable
private fun SwitchLocationDialog(
    addresses: List<AddressDto>,
    currentId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换当前位置") },
        text = {
            if (addresses.isEmpty()) {
                Text("还没有地址，请先到「址」管理。")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(addresses, key = { it.id }) { a ->
                        ListItem(
                            headlineContent = { Text(a.label) },
                            supportingContent = { Text(a.type + if (a.id == currentId) " · 当前" else "") },
                            modifier = Modifier.clickable { onPick(a.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun FinalizeHandleDialog(
    onDismiss: () -> Unit,
    onSubmit: (input: String, onError: (String) -> Unit) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置专属 handle") },
        text = {
            Column {
                Text("3-20 字符，中英文/数字/下划线。一旦确定不可再改。", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.trim() },
                    label = { Text("handle") },
                    singleLine = true
                )
                status?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && input.isNotBlank(),
                onClick = {
                    loading = true; status = null
                    onSubmit(input) { err ->
                        status = err
                        loading = false
                    }
                }
            ) { Text(if (loading) "提交中..." else "确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
