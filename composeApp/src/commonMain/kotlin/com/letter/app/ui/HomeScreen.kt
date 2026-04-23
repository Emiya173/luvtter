package com.letter.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.AddressDto
import com.letter.contract.dto.FinalizeHandleRequest
import com.letter.contract.dto.LetterSummaryDto
import com.letter.contract.dto.NotificationDto
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { Inbox("收件箱"), Outbox("发件箱") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onCompose: () -> Unit,
    onAddresses: () -> Unit,
    onContacts: () -> Unit,
    onOpenLetter: (String) -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Inbox) }
    var letters by remember { mutableStateOf<List<LetterSummaryDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var unread by remember { mutableStateOf(0) }
    var reward by remember { mutableStateOf<String?>(null) }
    var showHidden by remember { mutableStateOf(false) }

    var addresses by remember { mutableStateOf<List<AddressDto>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var showNotifications by remember { mutableStateOf(false) }
    var showSwitchLocation by remember { mutableStateOf(false) }
    var showFinalizeHandle by remember { mutableStateOf(false) }

    val user = container.tokens.session.collectAsState().value?.user
    val currentAddress = remember(user, addresses) {
        addresses.firstOrNull { it.id == user?.currentAddressId }
    }

    suspend fun reloadAll() {
        runCatching { addresses = container.addresses.list() }
        runCatching { notifications = container.notifications.list() }
        runCatching { unread = container.notifications.unreadCount() }
    }

    LaunchedEffect(Unit) {
        reloadAll()
        runCatching {
            val r = container.dailyReward.claim(java.util.TimeZone.getDefault().id)
            if (r.claimed) reward = "今日奖励已发放"
        }
    }

    suspend fun reload() {
        loading = true; error = null
        try {
            letters = when (tab) {
                Tab.Inbox -> container.letters.inbox(hidden = showHidden)
                Tab.Outbox -> container.letters.outbox(hidden = showHidden)
            }
        } catch (e: Exception) {
            error = e.message
        } finally { loading = false }
    }

    LaunchedEffect(tab, showHidden, user?.currentAddressId) { reload() }

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
                    BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { notifications = container.notifications.list() }
                                showNotifications = true
                            }
                        }) { Text("铃") }
                    }
                    IconButton(onClick = onContacts) { Text("人", style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = onAddresses) { Text("址", style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = {
                        scope.launch { reload(); reloadAll() }
                    }) { Text("⟳") }
                    TextButton(onClick = onLogout) { Text("退出") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCompose, text = { Text("写信") }, icon = { Text("✉") })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 当前位置 / handle 提示条
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

            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                Tab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showHidden,
                    onClick = { showHidden = !showHidden },
                    label = { Text(if (showHidden) "查看已隐藏" else "正常视图") }
                )
                Spacer(Modifier.weight(1f))
                if (showHidden) {
                    Text("已隐藏的信件仍存在于对方的箱子中", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            reward?.let {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!loading && letters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val hint = when {
                        showHidden -> "没有已隐藏的信件"
                        tab == Tab.Inbox && currentAddress != null -> "「${currentAddress.label}」当前没有信件"
                        else -> "还没有信件"
                    }
                    Text(hint, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(letters, key = { it.id }) { l ->
                        LetterRow(
                            l,
                            mine = tab == Tab.Outbox,
                            onClick = { onOpenLetter(l.id) },
                            onExpedite = if (tab == Tab.Outbox && l.status == "in_transit") {
                                {
                                    scope.launch {
                                        runCatching { container.letters.expedite(l.id, 5) }
                                            .onSuccess { reload() }
                                            .onFailure { error = it.message }
                                    }
                                }
                            } else null,
                            onHide = if (!showHidden && !l.hidden) {
                                {
                                    scope.launch {
                                        runCatching { container.letters.hide(l.id) }
                                            .onSuccess { reload() }
                                            .onFailure { error = it.message }
                                    }
                                }
                            } else null,
                            onUnhide = if (showHidden || l.hidden) {
                                {
                                    scope.launch {
                                        runCatching { container.letters.unhide(l.id) }
                                            .onSuccess { reload() }
                                            .onFailure { error = it.message }
                                    }
                                }
                            } else null
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showNotifications) {
        NotificationsDialog(
            notifications = notifications,
            onDismiss = { showNotifications = false },
            onMarkAllRead = {
                scope.launch {
                    runCatching { container.notifications.markAllRead() }
                    notifications = runCatching { container.notifications.list() }.getOrDefault(emptyList())
                    unread = 0
                }
            },
            onSwitchToAddress = { addressId ->
                scope.launch {
                    runCatching {
                        val u = container.me.setCurrentAddress(addressId)
                        container.tokens.updateUser(u)
                    }
                    showNotifications = false
                }
            }
        )
    }

    if (showSwitchLocation) {
        SwitchLocationDialog(
            addresses = addresses,
            currentId = user?.currentAddressId,
            onDismiss = { showSwitchLocation = false },
            onPick = { addressId ->
                scope.launch {
                    runCatching {
                        val u = container.me.setCurrentAddress(addressId)
                        container.tokens.updateUser(u)
                    }.onFailure { error = it.message }
                    showSwitchLocation = false
                }
            }
        )
    }

    if (showFinalizeHandle) {
        FinalizeHandleDialog(
            container = container,
            onDismiss = { showFinalizeHandle = false },
            onDone = { newUser ->
                container.tokens.updateUser(newUser)
                showFinalizeHandle = false
            }
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
    onUnhide: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val who = if (mine) (l.recipient?.displayName ?: "未知收件人") else (l.sender?.displayName ?: "未知寄件人")
            Text(who, style = MaterialTheme.typography.titleMedium)
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
        if (onExpedite != null || onHide != null || onUnhide != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (onExpedite != null) {
                    TextButton(onClick = onExpedite) { Text("加速到达") }
                }
                if (onHide != null) {
                    TextButton(onClick = onHide) { Text("隐藏") }
                }
                if (onUnhide != null) {
                    TextButton(onClick = onUnhide) { Text("恢复") }
                }
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
    container: AppContainer,
    onDismiss: () -> Unit,
    onDone: (com.letter.contract.dto.UserDto) -> Unit
) {
    val scope = rememberCoroutineScope()
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
                    scope.launch {
                        try {
                            val u = container.me.finalizeHandle(FinalizeHandleRequest(input))
                            onDone(u)
                        } catch (e: Exception) { status = e.message } finally { loading = false }
                    }
                }
            ) { Text(if (loading) "提交中..." else "确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
