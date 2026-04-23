package com.letter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.ContactDto
import com.letter.contract.dto.CreateContactRequest
import com.letter.contract.dto.LookupResult
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactDto>>(emptyList()) }
    var lookupHandle by remember { mutableStateOf("") }
    var lookupResult by remember { mutableStateOf<LookupResult?>(null) }
    var note by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    suspend fun reload() {
        contacts = container.contacts.list()
    }

    LaunchedEffect(Unit) { runCatching { reload() }.onFailure { status = it.message } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("联系人") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("通过 handle 查找用户", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = lookupHandle,
                    onValueChange = { lookupHandle = it.trim() },
                    label = { Text("handle") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !loading && lookupHandle.isNotBlank(),
                    onClick = {
                        loading = true; status = null; lookupResult = null
                        scope.launch {
                            try { lookupResult = container.contacts.lookup(lookupHandle) }
                            catch (e: Exception) { status = e.message }
                            finally { loading = false }
                        }
                    }
                ) { Text("查找") }
            }

            lookupResult?.let { lr ->
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${lr.user.displayName} · @${lr.user.handle}", style = MaterialTheme.typography.bodyMedium)
                        Text(if (lr.acceptsFromMe) "可接收你的来信" else "对方未接受陌生来信", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Button(
                            enabled = !loading,
                            onClick = {
                                loading = true; status = null
                                scope.launch {
                                    try {
                                        container.contacts.create(CreateContactRequest(targetId = lr.user.id, note = note.ifBlank { null }))
                                        lookupResult = null; lookupHandle = ""; note = ""
                                        reload()
                                    } catch (e: Exception) { status = e.message }
                                    finally { loading = false }
                                }
                            }
                        ) { Text("加为联系人") }
                    }
                }
            }

            status?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("已添加联系人", style = MaterialTheme.typography.titleSmall)
            LazyColumn {
                items(contacts, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text("${c.target.displayName} · @${c.target.handle}") },
                        supportingContent = { c.note?.let { Text(it) } },
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch { runCatching { container.contacts.delete(c.id); reload() } }
                            }) { Text("删除") }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
