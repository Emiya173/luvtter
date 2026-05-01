package com.luvtter.app.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luvtter.app.theme.LuvtterTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onSuccess: () -> Unit,
    onGoRegister: () -> Unit,
    vm: LoginViewModel = koinViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onEmailChange = vm::onEmailChange,
        onPasswordChange = vm::onPasswordChange,
        onSubmit = { vm.submit(onSuccess) },
        onGoRegister = onGoRegister,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoRegister: () -> Unit,
) {
    val tokens = LuvtterTheme.tokens
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(tokens.colors.paper, tokens.colors.paperDeep))
            ),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Brand
                Text(
                    "luvtter",
                    style = tokens.typography.brand.copy(
                        fontSize = 36.sp,
                        fontStyle = FontStyle.Italic,
                        color = tokens.colors.ink,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "慢 · 一 · 拍",
                    style = tokens.typography.meta.copy(
                        fontSize = 10.sp,
                        color = tokens.colors.inkFaded,
                    ),
                )
                Spacer(Modifier.height(32.dp))

                // Section divider with caption
                SectionRule("请 · 进 ENTRY")
                Spacer(Modifier.height(28.dp))

                // Email
                FieldLabel("邮 · 件 · 地 · 址")
                Spacer(Modifier.height(6.dp))
                PaperField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    placeholder = "name@example.com",
                    keyboardType = KeyboardType.Email,
                )
                Spacer(Modifier.height(20.dp))

                // Password
                FieldLabel("启 · 信 · 钥")
                Spacer(Modifier.height(6.dp))
                PaperField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    placeholder = "••••••••",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                )

                state.error?.let {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .widthIn(min = 2.dp, max = 2.dp)
                                .background(tokens.colors.seal),
                        )
                        Spacer(Modifier.widthIn(min = 8.dp, max = 8.dp))
                        Text(
                            it,
                            style = tokens.typography.meta.copy(
                                fontSize = 11.sp,
                                color = tokens.colors.seal,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Submit
                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.colors.seal,
                        contentColor = tokens.colors.paperRaised,
                        disabledContainerColor = tokens.colors.seal.copy(alpha = 0.5f),
                        disabledContentColor = tokens.colors.paperRaised.copy(alpha = 0.7f),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text(
                        if (state.loading) "请稍候…" else "封 · 缄 · 入 · 内",
                        maxLines = 1,
                        style = TextStyle(
                            fontFamily = tokens.fonts.serifZh,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                        ),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Switch to register
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "尚无信籍？",
                        style = tokens.typography.meta.copy(
                            fontSize = 11.sp,
                            color = tokens.colors.inkFaded,
                        ),
                    )
                    TextButton(
                        onClick = onGoRegister,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            "↗ 申 领 入 籍",
                            style = TextStyle(
                                fontFamily = tokens.fonts.serifZh,
                                fontSize = 13.sp,
                                color = tokens.colors.ink,
                                letterSpacing = 0.6.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    val tokens = LuvtterTheme.tokens
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = tokens.typography.meta.copy(
                fontSize = 9.sp,
                color = tokens.colors.inkFaded,
            ),
        )
    }
}

@Composable
internal fun SectionRule(label: String) {
    val tokens = LuvtterTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = tokens.colors.ruleSoft,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 0.5f,
                    )
                },
        )
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp),
            style = tokens.typography.meta.copy(
                fontSize = 9.sp,
                color = tokens.colors.inkFaded,
            ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = tokens.colors.ruleSoft,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 0.5f,
                    )
                },
        )
    }
}

@Composable
internal fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    val tokens = LuvtterTheme.tokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = tokens.colors.ink.copy(alpha = 0.6f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.5f,
                )
            }
            .padding(vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = tokens.fonts.serifZh,
                fontSize = 17.sp,
                color = tokens.colors.ink,
                letterSpacing = 0.5.sp,
            ),
            cursorBrush = SolidColor(tokens.colors.seal),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = TextStyle(
                            fontFamily = tokens.fonts.serifZh,
                            fontSize = 17.sp,
                            color = tokens.colors.inkGhost,
                        ),
                    )
                }
                inner()
            },
        )
    }
}
