package com.letter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letter.contract.dto.AddressDto
import com.letter.contract.dto.CreateAddressRequest
import com.letter.contract.dto.VirtualAnchorDto
import com.letter.shared.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressesScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var addresses by remember { mutableStateOf<List<AddressDto>>(emptyList()) }
    var anchors by remember { mutableStateOf<List<VirtualAnchorDto>>(emptyList()) }
    var label by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var anchorId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    suspend fun reload() {
        addresses = container.addresses.list()
        anchors = container.addresses.listAnchors()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure { status = it.message }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的地址") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("新增地址", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("纬度 (real)") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("经度 (real)") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("或选择虚拟坐标:", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                anchors.forEach { a ->
                    FilterChip(
                        selected = anchorId == a.id,
                        onClick = { anchorId = if (anchorId == a.id) null else a.id },
                        label = { Text(a.name) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !loading && label.isNotBlank() && (anchorId != null || (lat.isNotBlank() && lng.isNotBlank())),
                onClick = {
                    loading = true; status = null
                    scope.launch {
                        try {
                            val req = if (anchorId != null) CreateAddressRequest(label = label, type = "virtual", anchorId = anchorId)
                            else CreateAddressRequest(label = label, type = "real", latitude = lat.toDouble(), longitude = lng.toDouble())
                            container.addresses.create(req)
                            label = ""; lat = ""; lng = ""; anchorId = null
                            reload()
                        } catch (e: Exception) { status = e.message } finally { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("创建") }

            status?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("已有地址", style = MaterialTheme.typography.titleSmall)
            LazyColumn {
                items(addresses, key = { it.id }) { a ->
                    ListItem(
                        headlineContent = { Text(a.label + if (a.isDefault) " · 默认" else "") },
                        supportingContent = { Text("${a.type} · " + (a.city ?: a.anchorId ?: "—")) },
                        trailingContent = {
                            Row {
                                if (!a.isDefault) {
                                    TextButton(onClick = {
                                        scope.launch { runCatching { container.addresses.setDefault(a.id); reload() } }
                                    }) { Text("设为默认") }
                                }
                                TextButton(onClick = {
                                    scope.launch { runCatching { container.addresses.delete(a.id); reload() } }
                                }) { Text("删除") }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
