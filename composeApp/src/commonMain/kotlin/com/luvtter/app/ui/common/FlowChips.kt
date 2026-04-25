package com.luvtter.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** (id, label, selected) — screens map their domain types onto this triple. */
typealias ChipItem = Triple<String, String, Boolean>

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowChips(items: List<ChipItem>, onSelect: (String) -> Unit) {
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
