package com.letter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.*
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    container: AppContainer,
    onSent: () -> Unit,
    onCancel: () -> Unit,
    replyToLetterId: String? = null,
    prefillRecipientHandle: String? = null
) {
    val scope = rememberCoroutineScope()

    var stamps by remember { mutableStateOf<List<StampDto>>(emptyList()) }
    var addresses by remember { mutableStateOf<List<AddressDto>>(emptyList()) }
    var assets by remember { mutableStateOf<MyAssetsDto?>(null) }
    var contacts by remember { mutableStateOf<List<ContactDto>>(emptyList()) }

    var recipientHandle by remember { mutableStateOf(prefillRecipientHandle.orEmpty()) }
    var content by remember { mutableStateOf("") }
    var stampId by remember { mutableStateOf<String?>(null) }
    var senderAddressId by remember { mutableStateOf<String?>(null) }

    var recipientAddresses by remember { mutableStateOf<List<RecipientAddressDto>>(emptyList()) }
    var recipientAddressId by remember { mutableStateOf<String?>(null) }
    var recipientName by remember { mutableStateOf<String?>(null) }
    var lookupBusy by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun lookupRecipient() {
        if (recipientHandle.isBlank()) return
        lookupBusy = true; status = null
        try {
            val lr = container.contacts.lookup(recipientHandle)
            recipientName = lr.user.displayName
            recipientAddresses = container.addresses.listForRecipient(recipientHandle)
            recipientAddressId = recipientAddresses.firstOrNull { it.isDefault }?.id ?: recipientAddresses.firstOrNull()?.id
        } catch (e: Exception) {
            recipientName = null
            recipientAddresses = emptyList()
            recipientAddressId = null
            status = "找不到收件人: ${e.message}"
        } finally { lookupBusy = false }
    }

    LaunchedEffect(Unit) {
        try {
            stamps = container.catalog.stamps()
            addresses = container.addresses.list()
            assets = container.catalog.myAssets()
            contacts = runCatching { container.contacts.list() }.getOrDefault(emptyList())
            stampId = stamps.firstOrNull()?.id
            senderAddressId = addresses.firstOrNull { it.isDefault }?.id ?: addresses.firstOrNull()?.id
            if (!prefillRecipientHandle.isNullOrBlank()) lookupRecipient()
        } catch (e: Exception) { status = "加载失败: ${e.message}" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (replyToLetterId != null) "回信" else "写一封信") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("取消") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            if (contacts.isNotEmpty()) {
                Text("从联系人选择", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowChips(
                    items = contacts.map {
                        Triple(it.target.handle, "${it.target.displayName} · @${it.target.handle}", recipientHandle == it.target.handle)
                    },
                    onSelect = { handle ->
                        recipientHandle = handle
                        scope.launch { lookupRecipient() }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = recipientHandle, onValueChange = { recipientHandle = it.trim() },
                    label = { Text("收件人 handle") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(enabled = !lookupBusy && recipientHandle.isNotBlank(), onClick = { scope.launch { lookupRecipient() } }) {
                    Text(if (lookupBusy) "查找中" else "查找")
                }
            }
            recipientName?.let {
                Text("→ $it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (recipientAddresses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("寄到对方哪个地址", style = MaterialTheme.typography.labelMedium)
                FlowChips(
                    items = recipientAddresses.map { Triple(it.id, "${it.label} · ${it.type}" + if (it.isDefault) " · 默认" else "", recipientAddressId == it.id) },
                    onSelect = { recipientAddressId = it }
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                label = { Text("信件内容") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                minLines = 6
            )
            Spacer(Modifier.height(16.dp))

            Text("邮票", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowChips(
                items = stamps.map { stamp ->
                    val qty = assets?.stamps?.firstOrNull { it.assetId == stamp.id }?.quantity ?: 0
                    val label = "${stamp.name}×$qty (${stamp.weightCapacity}g)"
                    Triple(stamp.id, label, stampId == stamp.id)
                },
                onSelect = { stampId = it }
            )

            Spacer(Modifier.height(16.dp))
            Text("寄件地址", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (addresses.isEmpty()) {
                Text("尚未创建地址，请先到「址」管理。", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowChips(
                    items = addresses.map { Triple(it.id, "${it.label} · ${it.type}", senderAddressId == it.id) },
                    onSelect = { senderAddressId = it }
                )
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !loading && recipientHandle.isNotBlank() && content.isNotBlank() && stampId != null,
                onClick = {
                    loading = true; status = null
                    scope.launch {
                        try {
                            val draft = container.letters.createDraft(
                                CreateDraftRequest(
                                    recipientHandle = recipientHandle,
                                    recipientAddressId = recipientAddressId,
                                    senderAddressId = senderAddressId,
                                    stampId = stampId,
                                    contentType = "text",
                                    body = LetterBodyText(listOf(TextSegment(content))),
                                    replyToLetterId = replyToLetterId
                                )
                            )
                            val result = container.letters.send(draft.summary.id)
                            status = "已寄出，预计送达: ${result.estimatedDeliveryAt}"
                            onSent()
                        } catch (e: Exception) {
                            status = "寄信失败: ${e.message}"
                        } finally { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "寄送中..." else "寄出") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowChips(items: List<Triple<String, String, Boolean>>, onSelect: (String) -> Unit) {
    Column {
        items.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { (id, label, selected) ->
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(id) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
                    )
                }
            }
        }
    }
}
