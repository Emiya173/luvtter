package com.letter.app.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        onUnblock = vm::unblock
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onUnblock: (String) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("联系人") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("仅好友可寄信给我", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = onlyFriends,
                        onCheckedChange = onOnlyFriendsChange
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("通过 handle 查找用户", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.lookupHandle,
                    onValueChange = onLookupHandleChange,
                    label = { Text("handle") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !state.loading && state.lookupHandle.isNotBlank(),
                    onClick = onLookup
                ) { Text("查找") }
            }

            state.lookupResult?.let { lr ->
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${lr.user.displayName} · @${lr.user.handle}", style = MaterialTheme.typography.bodyMedium)
                        Text(if (lr.acceptsFromMe) "可接收你的来信" else "对方未接受陌生来信", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = state.note, onValueChange = onNoteChange, label = { Text("备注（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Button(enabled = !state.loading, onClick = onAddAsContact) { Text("加为联系人") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(enabled = !state.loading, onClick = onBlockLookupResult) { Text("屏蔽") }
                        }
                    }
                }
            }

            state.status?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("已添加联系人", style = MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.contacts, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text("${c.target.displayName} · @${c.target.handle}") },
                        supportingContent = { c.note?.let { Text(it) } },
                        trailingContent = {
                            TextButton(onClick = { onDeleteContact(c.id) }) { Text("删除") }
                        }
                    )
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("已屏蔽用户", style = MaterialTheme.typography.titleSmall)
            if (state.blocks.isEmpty()) {
                Text("没有屏蔽任何人", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn {
                    items(state.blocks, key = { it.id }) { b ->
                        ListItem(
                            headlineContent = { Text("${b.target.displayName} · @${b.target.handle}") },
                            trailingContent = {
                                TextButton(onClick = { onUnblock(b.target.id) }) { Text("取消屏蔽") }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
