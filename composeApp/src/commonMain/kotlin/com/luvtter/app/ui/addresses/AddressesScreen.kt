package com.luvtter.app.ui.addresses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.ui.common.PaperChip
import com.luvtter.app.ui.common.PaperEmptyHint
import com.luvtter.app.ui.common.PaperFieldLabel
import com.luvtter.app.ui.common.PaperGhostButton
import com.luvtter.app.ui.common.PaperInput
import com.luvtter.app.ui.common.PaperListRow
import com.luvtter.app.ui.common.PaperPageScaffold
import com.luvtter.app.ui.common.PaperPrimaryButton
import com.luvtter.app.ui.common.PaperSectionHeader
import com.luvtter.app.ui.common.PaperStatusBar
import androidx.compose.ui.text.input.KeyboardType
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
        onDelete = vm::delete,
    )
}

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
    onDelete: (String) -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    PaperPageScaffold(title = "我 · 的 · 地 · 址", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp)
                .padding(bottom = 48.dp)
                .widthIn(max = 720.dp),
        ) {
            // ── 新增地址 ──
            PaperSectionHeader("新 · 增 · 地 · 址  NEW")

            PaperFieldLabel("名 称")
            PaperInput(
                value = state.label,
                onValueChange = onLabelChange,
                placeholder = "如:工作室 / 家中 / 母亲家",
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    PaperFieldLabel("纬 度  LAT")
                    PaperInput(
                        value = state.lat,
                        onValueChange = onLatChange,
                        placeholder = "31.2304",
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    PaperFieldLabel("经 度  LNG")
                    PaperInput(
                        value = state.lng,
                        onValueChange = onLngChange,
                        placeholder = "121.4737",
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PaperFieldLabel("或 选 择 虚 拟 坐 标")
            Spacer(Modifier.height(8.dp))
            FlowRowSpaced(spacing = 8.dp) {
                state.anchors.forEach { a ->
                    PaperChip(
                        label = a.name,
                        selected = state.anchorId == a.id,
                        onClick = { onAnchorToggle(a.id) },
                    )
                }
            }

            if (state.anchorId != null) {
                Spacer(Modifier.height(16.dp))
                PaperFieldLabel("虚 拟 距 离  0–1000")
                PaperInput(
                    value = state.virtualDistance,
                    onValueChange = onVirtualDistanceChange,
                    placeholder = "100",
                    keyboardType = KeyboardType.Number,
                )
            }

            Spacer(Modifier.height(24.dp))
            PaperPrimaryButton(
                label = "立 · 此 · 一 · 址",
                onClick = onCreate,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            )

            state.status?.let { PaperStatusBar(it) }

            // ── 已有地址 ──
            PaperSectionHeader(
                "已 · 有 · 地 · 址  STORED",
                hint = if (state.addresses.isEmpty()) null else "${state.addresses.size}",
            )
            if (state.addresses.isEmpty()) {
                PaperEmptyHint("尚未立址。")
            } else {
                state.addresses.forEach { a ->
                    PaperListRow {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        a.label,
                                        style = TextStyle(
                                            fontFamily = tokens.fonts.serifZh,
                                            fontSize = 15.sp,
                                            color = tokens.colors.ink,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.4.sp,
                                        ),
                                    )
                                    if (a.isDefault) {
                                        Spacer(Modifier.widthIn(min = 8.dp, max = 8.dp))
                                        Text(
                                            "默 认",
                                            style = tokens.typography.meta.copy(
                                                fontSize = 9.sp,
                                                color = tokens.colors.seal,
                                            ),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${a.type} · ${a.city ?: a.anchorId ?: "—"}",
                                    style = tokens.typography.meta.copy(
                                        fontSize = 11.sp,
                                        color = tokens.colors.inkFaded,
                                    ),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!a.isDefault) {
                                    PaperGhostButton(
                                        label = "设 默 认",
                                        onClick = { onSetDefault(a.id) },
                                    )
                                }
                                PaperGhostButton(
                                    label = "删 除",
                                    onClick = { onDelete(a.id) },
                                    danger = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 简易行内 wrap 容器:超出宽度时换行,采用 Layout 实现避免引入额外依赖。 */
@Composable
private fun FlowRowSpaced(spacing: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val sp = spacing.roundToPx()
        val maxW = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        var x = 0; var y = 0; var rowH = 0
        val positions = ArrayList<Pair<Int, Int>>(placeables.size)
        placeables.forEach { p ->
            if (x + p.width > maxW && x > 0) {
                x = 0; y += rowH + sp; rowH = 0
            }
            positions += x to y
            x += p.width + sp
            if (p.height > rowH) rowH = p.height
        }
        val totalH = y + rowH
        layout(maxW, totalH) {
            placeables.forEachIndexed { i, p ->
                val (px, py) = positions[i]
                p.place(px, py)
            }
        }
    }
}
