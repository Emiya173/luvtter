package com.letter.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letter.contract.dto.AddressDto
import com.letter.contract.dto.FolderDto
import com.letter.contract.dto.LetterSummaryDto
import com.letter.contract.dto.NotificationDto
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCompose: () -> Unit,
    onAddresses: () -> Unit,
    onContacts: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onEditDraft: (String) -> Unit,
    onLogout: () -> Unit,
    vm: HomeViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val user = vm.session.collectAsStateWithLifecycle().value?.user
    val currentAddress = remember(user, state.addresses) {
        state.addresses.firstOrNull { it.id == user?.currentAddressId }
    }
    var showNotifications by remember { mutableStateOf(false) }
    var showSwitchLocation by remember { mutableStateOf(false) }
    var showFinalizeHandle by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(user?.displayName?.let { "你好，$it" } ?: "信件")
                        val handle = user?.handle ?: "—"
                        val needsFinalize = user?.handleFinalized == false
                        Text(
                            if (needsFinalize) "@$handle (临时)" else "@$handle",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    BadgedBox(badge = { if (state.unread > 0) Badge { Text("${state.unread}") } }) {
                        IconButton(onClick = {
                            vm.openNotifications()
                            showNotifications = true
                        }) { Text("铃") }
                    }
                    IconButton(onClick = { showSearch = true }) { Text("搜", style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = onContacts) { Text("人", style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = onAddresses) { Text("址", style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = vm::refreshAll) { Text("⟳") }
                    TextButton(onClick = onLogout) { Text("退出") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCompose, text = { Text("写信") }, icon = { Text("✉") })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "当前位置：" + (currentAddress?.let { "${it.label} · ${it.type}" } ?: "未设置"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showSwitchLocation = true }) { Text("切换位置") }
                    }
                    if (user?.handleFinalized == false) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "当前 handle 是临时的，别人寄信时找不到你",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showFinalizeHandle = true }) { Text("设置专属 handle") }
                        }
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                HomeTab.entries.forEach { t ->
                    Tab(selected = state.tab == t, onClick = { vm.selectTab(t) }, text = { Text(t.label) })
                }
            }
            if (state.tab == HomeTab.Inbox || state.tab == HomeTab.Outbox) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.showHidden,
                        onClick = vm::toggleShowHidden,
                        label = { Text(if (state.showHidden) "查看已隐藏" else "正常视图") }
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
                    onSelect = vm::selectFolder,
                    onCreate = vm::createFolder,
                    onDelete = vm::deleteFolder
                )
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            state.reward?.let {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.letters, key = { it.id }) { l ->
                        val mine = vm.letterOwnedByMe(l)
                        val isDraft = state.tab == HomeTab.Drafts
                        LetterRow(
                            l,
                            mine = mine,
                            onClick = { if (isDraft) onEditDraft(l.id) else onOpenLetter(l.id) },
                            onDeleteDraft = if (isDraft) { { vm.deleteDraft(l.id) } } else null,
                            onExpedite = if (mine && l.status == "in_transit") { { vm.expedite(l.id) } } else null,
                            onHide = if (!state.showHidden && !l.hidden) { { vm.hide(l.id) } } else null,
                            onUnhide = if (state.showHidden || l.hidden) { { vm.unhide(l.id) } } else null
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
            onDismiss = { showNotifications = false },
            onMarkAllRead = vm::markAllNotificationsRead,
            onSwitchToAddress = { addressId ->
                vm.switchCurrentAddress(addressId)
                showNotifications = false
            }
        )
    }

    if (showSwitchLocation) {
        SwitchLocationDialog(
            addresses = state.addresses,
            currentId = user?.currentAddressId,
            onDismiss = { showSwitchLocation = false },
            onPick = { addressId ->
                vm.switchCurrentAddress(addressId)
                showSwitchLocation = false
            }
        )
    }

    if (showFinalizeHandle) {
        FinalizeHandleDialog(
            onDismiss = { showFinalizeHandle = false },
            onSubmit = { input, onError ->
                vm.finalizeHandle(
                    value = input,
                    onDone = { showFinalizeHandle = false },
                    onError = onError
                )
            }
        )
    }

    if (showSearch) {
        SearchDialog(
            onDismiss = { showSearch = false },
            onOpen = { id -> showSearch = false; onOpenLetter(id) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDialog(
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    vm: SearchViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索信件") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        label = { Text("关键词") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !state.busy && state.query.isNotBlank(),
                        onClick = vm::search
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
        time?.let {
            Text(it.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
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
                                Text(n.createdAt.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
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
