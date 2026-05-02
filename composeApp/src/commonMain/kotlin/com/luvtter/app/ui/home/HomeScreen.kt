package com.luvtter.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.*
import com.luvtter.app.ui.letter.*
import com.luvtter.contract.dto.*
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
        onConsumeError = vm::clearError,
        onConsumeReward = vm::clearReward,
        onConsumeExport = vm::clearLastExport,
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
    onConsumeError: () -> Unit,
    onConsumeReward: () -> Unit,
    onConsumeExport: () -> Unit,
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

    val toast = rememberPaperToastState()
    LaunchedEffect(state.error) {
        state.error?.let {
            toast.show(it, PaperToastKind.Error, durationMs = 4000L)
            onConsumeError()
        }
    }
    LaunchedEffect(state.reward) {
        state.reward?.let {
            toast.show(it, PaperToastKind.Success, durationMs = 5000L)
            onConsumeReward()
        }
    }
    LaunchedEffect(state.lastExport) {
        state.lastExport?.let { ex ->
            val mins = ex.expiresInSeconds / 60
            toast.show(
                message = "已导出 ${ex.letterCount} 封信 · ${ex.sizeBytes / 1024} KB · 链接 ${mins} 分钟内有效",
                kind = PaperToastKind.Info,
                durationMs = 0L,
                actionLabel = "打 开 下 载",
                onAction = { onOpenExportLink(ex.downloadUrl) },
            )
            onConsumeExport()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                val tokens = LuvtterTheme.tokens
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.colors.seal.copy(alpha = 0.08f))
                        .padding(horizontal = 32.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .width(2.dp)
                            .background(tokens.colors.seal),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "当前 handle 是临时的,别人寄信时找不到你",
                        modifier = Modifier.weight(1f),
                        style = tokens.typography.meta.copy(
                            fontSize = 11.sp,
                            color = tokens.colors.seal,
                        ),
                    )
                    PaperGhostButton(
                        label = "立 此 一 名",
                        onClick = onShowFinalizeHandle,
                        danger = true,
                    )
                }
            }

            if (state.showFirstLetterPrompt) {
                FirstLetterPromptCard(onStart = onStartFirstLetter, onDismiss = onDismissFirstLetterPrompt)
            }

            // 导出结果由 LaunchedEffect(state.lastExport) 推送至底部 PaperToast,持久显示直至用户关闭。

            if (state.tab == HomeTab.Inbox || state.tab == HomeTab.Outbox) {
                val tokens = LuvtterTheme.tokens
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PaperChip(
                        label = if (state.showHidden) "查 看 已 隐 藏" else "正 常 视 图",
                        selected = state.showHidden,
                        onClick = onToggleShowHidden,
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.showHidden) {
                        Text(
                            "已隐藏的信件仍存在于对方的箱子中",
                            style = tokens.typography.meta.copy(
                                fontSize = 10.sp,
                                color = tokens.colors.inkFaded,
                            ),
                        )
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
                PaperLoadingBar()
            }
            if (!state.loading && state.letters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val (title, hint) = when {
                        state.tab == HomeTab.Favorites -> "尚无折角的信件" to "在信件详情里轻折一角,即可收藏"
                        state.tab == HomeTab.Folders && state.folders.isEmpty() -> "尚无卷宗" to "先立一卷,再把信归档"
                        state.tab == HomeTab.Folders && state.selectedFolderId == null -> "选一个卷宗,展信" to null
                        state.tab == HomeTab.Folders -> "此卷尚空" to "把相关的信件归入此卷"
                        state.showHidden -> "无已隐藏的信件" to null
                        state.tab == HomeTab.Inbox && currentAddress != null ->
                            "「${currentAddress.label}」当前空空如也" to "等候来信,或先寄一封以引水"
                        else -> "尚无信件" to null
                    }
                    PaperEmptyState(title = title, hint = hint)
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
            } else if (state.tab == HomeTab.Drafts && !state.showHidden) {
                DraftsList(
                    letters = state.letters,
                    onEdit = onEditDraft,
                    onDelete = onDeleteDraft,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (state.tab == HomeTab.Favorites && !state.showHidden) {
                FavoritesList(
                    letters = state.letters,
                    isLetterMine = isLetterMine,
                    onOpen = onOpenLetter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (state.tab == HomeTab.Folders && !state.showHidden) {
                val folderName = state.folders.firstOrNull { it.id == state.selectedFolderId }?.name
                FolderShelf(
                    folderName = folderName,
                    letters = state.letters,
                    isLetterMine = isLetterMine,
                    onOpen = onOpenLetter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 已隐藏视图:沿用紧凑列表
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
        PaperToastHost(toast, modifier = Modifier.align(Alignment.BottomCenter))
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
        HomeTab.Drafts -> "$lettersCount 封未寄出"
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

@Composable
private fun SearchDialogContent(
    state: SearchUiState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val tokens = LuvtterTheme.tokens
    PaperRightDrawer(
        onDismissRequest = onDismiss,
        title = "搜 · 寻 · 信 · 件",
        subtitle = "SEARCH · 关键词 / 寄件人 / 收件人",
        actions = { PaperGhostButton(label = "关 闭", onClick = onDismiss) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1f)) {
                    PaperInput(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = "落笔何时何字…",
                    )
                }
                Spacer(Modifier.width(12.dp))
                PaperPrimaryButton(
                    label = if (state.busy) "搜 寻 中" else "搜 寻",
                    onClick = onSearch,
                    enabled = !state.busy && state.query.isNotBlank(),
                )
            }
            state.error?.let { PaperStatusBar(it) }
            Spacer(Modifier.height(8.dp))
            if (state.results.isEmpty() && !state.busy && state.query.isNotBlank() && state.error == null) {
                Text(
                    "无匹配。",
                    style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.inkGhost),
                )
            }
            LazyColumn {
                items(state.results, key = { it.id }) { l ->
                    PaperListRow(onClick = { onOpen(l.id) }) {
                        Column {
                            Text(
                                "${l.sender?.displayName ?: "—"} → ${l.recipient?.displayName ?: "—"}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = tokens.fonts.serifZh,
                                    fontSize = 14.sp,
                                    color = tokens.colors.ink,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                ),
                            )
                            l.preview?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    it,
                                    maxLines = 2,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontFamily = tokens.fonts.serifZh,
                                        fontSize = 12.sp,
                                        color = tokens.colors.inkSoft,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
        PaperDialog(
            onDismissRequest = { showNew = false; name = "" },
            title = "新 · 立 · 卷 · 宗",
            subtitle = "NEW FOLDER · 用一个名字归一类信件",
            actions = {
                PaperGhostButton(
                    label = "取 消",
                    onClick = { showNew = false; name = "" },
                )
                PaperPrimaryButton(
                    label = "立 · 卷",
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name); name = ""; showNew = false },
                )
            },
        ) {
            Column {
                PaperFieldLabel("名 称")
                PaperInput(
                    value = name,
                    onValueChange = { name = it.trim() },
                    placeholder = "如:旧友 / 工作 / 致未来的我",
                )
            }
        }
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
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 32.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val who = if (mine) (l.recipient?.displayName ?: "未知收件人") else (l.sender?.displayName ?: "未知寄件人")
            Text(
                who,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = tokens.fonts.serifZh,
                    fontSize = 15.sp,
                    color = tokens.colors.ink,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
            )
            if (l.isFavorite) {
                Spacer(Modifier.width(6.dp))
                Text("★", style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = tokens.colors.seal))
            }
            Spacer(Modifier.weight(1f))
            Text(
                statusLabel(l.status, l.transitStage),
                style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
            )
        }
        l.recipientAddressLabel?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                if (mine) "→ 寄到「$it」" else "→ 收件地址 · $it",
                style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
            )
        }
        l.preview?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                maxLines = 2,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = tokens.fonts.serifZh,
                    fontSize = 12.sp,
                    color = tokens.colors.inkSoft,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        val time = l.deliveredAt ?: l.deliveryAt ?: l.sentAt
        Row(verticalAlignment = Alignment.CenterVertically) {
            time?.let {
                Text(
                    formatLocalDateTime(it) ?: it,
                    style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkGhost),
                )
            }
            if (l.photoCount > 0 || l.stickerCount > 0) {
                Spacer(Modifier.weight(1f))
                if (l.photoCount > 0) {
                    Text(
                        "📷 ${l.photoCount}",
                        style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
                    )
                }
                if (l.photoCount > 0 && l.stickerCount > 0) Spacer(Modifier.width(8.dp))
                if (l.stickerCount > 0) {
                    Text(
                        "🏷 ${l.stickerCount}",
                        style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
                    )
                }
            }
        }
        if (onExpedite != null || onHide != null || onUnhide != null || onDeleteDraft != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onExpedite != null) PaperGhostButton(label = "加 速 到 达", onClick = onExpedite)
                if (onHide != null) PaperGhostButton(label = "隐 藏", onClick = onHide)
                if (onUnhide != null) PaperGhostButton(label = "恢 复", onClick = onUnhide)
                if (onDeleteDraft != null) PaperGhostButton(label = "删 除 草 稿", onClick = onDeleteDraft, danger = true)
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
    val tokens = LuvtterTheme.tokens
    PaperRightDrawer(
        onDismissRequest = onDismiss,
        title = "驿 · 报",
        subtitle = "NOTIFICATIONS · 来自邮局的近况",
        actions = {
            if (notifications.isNotEmpty()) {
                PaperGhostButton(label = "全 部 已 读", onClick = onMarkAllRead)
            }
            PaperGhostButton(label = "关 闭", onClick = onDismiss)
        },
    ) {
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂 无 驿 报。",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = tokens.fonts.serifZh,
                        fontSize = 13.sp,
                        color = tokens.colors.inkGhost,
                        letterSpacing = 0.6.sp,
                    ),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(notifications, key = { it.id }) { n ->
                    PaperListRow {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                n.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = tokens.fonts.serifZh,
                                    fontSize = 14.sp,
                                    color = tokens.colors.ink,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                ),
                            )
                            n.preview?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    it,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontFamily = tokens.fonts.serifZh,
                                        fontSize = 12.sp,
                                        color = tokens.colors.inkSoft,
                                    ),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    formatLocalDateTime(n.createdAt) ?: n.createdAt,
                                    style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
                                )
                                Spacer(Modifier.weight(1f))
                                if (n.addressId != null) {
                                    PaperGhostButton(
                                        label = "切 至 ${n.addressLabel ?: "该地址"}",
                                        onClick = { onSwitchToAddress(n.addressId!!) },
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
private fun FirstLetterPromptCard(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp)
            .background(tokens.colors.paperRaised)
            .drawBehind {
                drawLine(
                    color = tokens.colors.seal,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 2f,
                )
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "给 · 未 · 来 · 的 · 自 · 己,寄 第 一 封 信",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 15.sp,
                color = tokens.colors.ink,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "信会按你选的邮票慢慢上路,几小时到几天后送达。先写一句话给未来打个招呼?",
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = tokens.colors.inkSoft,
                letterSpacing = 0.3.sp,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PaperGhostButton(label = "暂 · 不", onClick = onDismiss)
            PaperPrimaryButton(label = "现 · 在 · 写", onClick = onStart)
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
    val tokens = LuvtterTheme.tokens
    PaperDialog(
        onDismissRequest = onDismiss,
        title = "迁 · 此 · 一 · 址",
        subtitle = "SWITCH LOCATION · 当前所在",
        actions = { PaperGhostButton(label = "关 闭", onClick = onDismiss) },
    ) {
        if (addresses.isEmpty()) {
            Text(
                "还没有地址,请先到「地 址」管理。",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = tokens.fonts.serifZh,
                    fontSize = 13.sp,
                    color = tokens.colors.inkSoft,
                    letterSpacing = 0.4.sp,
                ),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(addresses, key = { it.id }) { a ->
                    PaperListRow(onClick = { onPick(a.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        a.label,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontFamily = tokens.fonts.serifZh,
                                            fontSize = 14.sp,
                                            color = tokens.colors.ink,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.4.sp,
                                        ),
                                    )
                                    if (a.id == currentId) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "当 前",
                                            style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.seal),
                                        )
                                    }
                                }
                                Text(
                                    a.type,
                                    style = tokens.typography.meta.copy(fontSize = 10.sp, color = tokens.colors.inkFaded),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalizeHandleDialog(
    onDismiss: () -> Unit,
    onSubmit: (input: String, onError: (String) -> Unit) -> Unit
) {
    val tokens = LuvtterTheme.tokens
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    PaperDialog(
        onDismissRequest = onDismiss,
        title = "立 · 此 · 一 · 名",
        subtitle = "HANDLE · 一旦确定不可再改",
        actions = {
            PaperGhostButton(label = "取 消", onClick = onDismiss)
            PaperPrimaryButton(
                label = if (loading) "提 交 中" else "立 · 名",
                enabled = !loading && input.isNotBlank(),
                onClick = {
                    loading = true; status = null
                    onSubmit(input) { err ->
                        status = err
                        loading = false
                    }
                },
            )
        },
    ) {
        Column {
            Text(
                "3–20 字符,中英文 / 数字 / 下划线。",
                style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.inkFaded),
            )
            Spacer(Modifier.height(14.dp))
            PaperFieldLabel("HANDLE")
            PaperInput(
                value = input,
                onValueChange = { input = it.trim() },
                placeholder = "@yourname",
            )
            status?.let { PaperStatusBar(it) }
        }
    }
}
