package com.luvtter.app.ui.addresses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddressesScreen(
    onBack: () -> Unit,
    vm: AddressesViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AddressesContent(
        state = state,
        onBack = onBack,
        onLabelChange = vm::onLabelChange,
        onLatChange = vm::onLatChange,
        onLngChange = vm::onLngChange,
        onAnchorToggle = vm::onAnchorToggle,
        onVirtualDistanceChange = vm::onVirtualDistanceChange,
        onCreate = vm::create,
        onSetDefault = vm::setDefault,
        onDelete = vm::delete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressesContent(
    state: AddressesUiState,
    onBack: () -> Unit,
    onLabelChange: (String) -> Unit,
    onLatChange: (String) -> Unit,
    onLngChange: (String) -> Unit,
    onAnchorToggle: (String) -> Unit,
    onVirtualDistanceChange: (String) -> Unit,
    onCreate: () -> Unit,
    onSetDefault: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("我的地址") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("新增地址", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = state.label, onValueChange = onLabelChange, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(value = state.lat, onValueChange = onLatChange, label = { Text("纬度 (real)") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(value = state.lng, onValueChange = onLngChange, label = { Text("经度 (real)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("或选择虚拟坐标:", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                state.anchors.forEach { a ->
                    FilterChip(
                        selected = state.anchorId == a.id,
                        onClick = { onAnchorToggle(a.id) },
                        label = { Text(a.name) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            if (state.anchorId != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.virtualDistance,
                    onValueChange = onVirtualDistanceChange,
                    label = { Text("虚拟距离 (0-1000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = state.canSubmit,
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth()
            ) { Text("创建") }

            state.status?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("已有地址", style = MaterialTheme.typography.titleSmall)
            LazyColumn {
                items(state.addresses, key = { it.id }) { a ->
                    ListItem(
                        headlineContent = { Text(a.label + if (a.isDefault) " · 默认" else "") },
                        supportingContent = { Text("${a.type} · " + (a.city ?: a.anchorId ?: "—")) },
                        trailingContent = {
                            Row {
                                if (!a.isDefault) {
                                    TextButton(onClick = { onSetDefault(a.id) }) { Text("设为默认") }
                                }
                                TextButton(onClick = { onDelete(a.id) }) { Text("删除") }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
