package com.luvtter.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luvtter.app.theme.LuvtterTheme

/**
 * 纸面弹层。统一替换 Material3 AlertDialog 的视觉,保留内容布局自由度。
 *
 * - title:18sp serifZh + 1.6sp 字距;可选 subtitle 用 meta 9sp
 * - content:任意 composable,内置 24dp 水平 padding
 * - 底部为 actions slot:推荐用 [PaperDialogConfirm] / [PaperDialogDismiss] 配 PaperPrimaryButton / PaperGhostButton
 *
 * 不强制 confirm/dismiss 双按钮 — 单按钮"关闭"型也可以传一个 action。
 */
@Composable
fun PaperDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    properties: DialogProperties = DialogProperties(),
    actions: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 560.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(2.dp),
                    ambientColor = Color(0xFF1E140A).copy(alpha = 0.18f),
                ),
            shape = RoundedCornerShape(2.dp),
            color = tokens.colors.paperRaised,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                // 标题
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    Text(
                        title,
                        style = TextStyle(
                            fontFamily = tokens.fonts.serifZh,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.colors.ink,
                            letterSpacing = 1.6.sp,
                        ),
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            subtitle,
                            style = tokens.typography.meta.copy(
                                fontSize = 10.sp,
                                color = tokens.colors.inkFaded,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                // 内容
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    content()
                }
                if (actions != null) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}
