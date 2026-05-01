package com.luvtter.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme

/**
 * 通用纸面页面骨架:顶部细线 TopBar + 中部可滚内容 + 可选尾部动作。
 *
 * 用于次级页面（联系人/地址/会话…)。Home 不使用,因为它带左侧栏。
 */
@Composable
fun PaperPageScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = tokens.colors.paper,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaperTopBar(title = title, onBack = onBack, trailing = trailing)
            content()
        }
    }
}

@Composable
fun PaperTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.colors.paper)
            .drawBehind {
                drawRect(
                    color = tokens.colors.ruleSoft,
                    topLeft = Offset(0f, size.height - 0.5f),
                    size = Size(size.width, 0.5f),
                )
            }
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                "← 返回",
                style = tokens.typography.meta.copy(fontSize = 13.sp, color = tokens.colors.inkSoft),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            title,
            style = tokens.typography.title.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.colors.ink,
                letterSpacing = 1.6.sp,
            ),
        )
        Spacer(Modifier.weight(1f))
        Box {
            if (trailing != null) trailing() else Spacer(Modifier.widthIn(min = 60.dp, max = 60.dp))
        }
    }
}

/** 段标:meta 9sp 上头小标题 + 可选右侧 hint。 */
@Composable
fun PaperSectionHeader(label: String, hint: String? = null) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
        )
        Spacer(Modifier.weight(1f))
        if (hint != null) {
            Text(
                hint,
                style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkGhost),
            )
        }
    }
}

/** 标签:meta 9sp。 */
@Composable
fun PaperFieldLabel(text: String) {
    val tokens = LuvtterTheme.tokens
    Text(
        text,
        style = tokens.typography.meta.copy(fontSize = 9.sp, color = tokens.colors.inkFaded),
    )
}

/** 单行/多行 paper 输入:底部 0.5dp 实线、serifZh、seal 红光标。 */
@Composable
fun PaperInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = tokens.colors.ink.copy(alpha = if (enabled) 0.6f else 0.25f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.5f,
                )
            }
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 16.sp,
                color = if (enabled) tokens.colors.ink else tokens.colors.inkFaded,
                letterSpacing = 0.5.sp,
            ),
            cursorBrush = SolidColor(tokens.colors.seal),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = TextStyle(
                            fontFamily = tokens.fonts.serifZh,
                            fontSize = 16.sp,
                            color = tokens.colors.inkGhost,
                        ),
                    )
                }
                inner()
            },
        )
    }
}

/** 主行动按钮:seal 红 + paperRaised 字。 */
@Composable
fun PaperPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = LuvtterTheme.tokens
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tokens.colors.seal,
            contentColor = tokens.colors.paperRaised,
            disabledContainerColor = tokens.colors.seal.copy(alpha = 0.5f),
            disabledContentColor = tokens.colors.paperRaised.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(
            label,
            maxLines = 1,
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            ),
        )
    }
}

/** 描线方框次要按钮(取消/删除等)。color=seal 时表示破坏性。 */
@Composable
fun PaperGhostButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val tokens = LuvtterTheme.tokens
    val color = when {
        !enabled -> tokens.colors.inkGhost
        danger -> tokens.colors.seal
        else -> tokens.colors.inkSoft
    }
    Box(
        modifier = Modifier
            .border(0.5.dp, color, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 13.sp,
                color = color,
                letterSpacing = 0.4.sp,
            ),
        )
    }
}

/** 选项芯片:用于地址 anchors / 会话过滤等。 */
@Composable
fun PaperChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tokens = LuvtterTheme.tokens
    val border = when {
        !enabled -> tokens.colors.inkGhost
        selected -> tokens.colors.ink
        else -> tokens.colors.paperEdge
    }
    val fg = when {
        !enabled -> tokens.colors.inkGhost
        selected -> tokens.colors.ink
        else -> tokens.colors.inkFaded
    }
    Box(
        modifier = Modifier
            .border(0.5.dp, border, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 13.sp,
                color = fg,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = 0.5.sp,
            ),
        )
    }
}

/** 错误/状态条:左侧 seal 红 2dp 短杠 + meta 红字。 */
@Composable
fun PaperStatusBar(text: String, danger: Boolean = true) {
    val tokens = LuvtterTheme.tokens
    val accent = if (danger) tokens.colors.seal else tokens.colors.inkSoft
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(10.dp)
                .widthIn(min = 2.dp, max = 2.dp)
                .background(accent),
        )
        Spacer(Modifier.widthIn(min = 8.dp, max = 8.dp))
        Text(
            text,
            style = tokens.typography.meta.copy(fontSize = 11.sp, color = accent),
        )
    }
}

/** 列表行容器:底部 0.5dp ruleSoft 分隔。 */
@Composable
fun PaperListRow(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .drawBehind {
                drawLine(
                    color = tokens.colors.ruleSoft,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.5f,
                )
            }
            .padding(vertical = 14.dp),
    ) {
        content()
    }
}

@Composable
fun PaperEmptyHint(text: String) {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 13.sp,
                color = tokens.colors.inkGhost,
                letterSpacing = 0.5.sp,
            ),
        )
    }
}

internal val PaperPageMaxWidth = 720.dp
