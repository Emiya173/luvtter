package com.luvtter.app.ui.letter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luvtter.app.theme.LuvtterTheme
import com.luvtter.app.theme.paperGrain

/**
 * P2 沙盒屏 —— 展示 Postmark / Stamp / WaxSeal / EnvelopeThumb / RouteMap / EnvelopeIcon 全部状态。
 * 完成 P3+ 后可以删除此屏 + 路由 + LoginScreen 入口。
 */
@Composable
fun LetterPlaygroundScreen(onBack: () -> Unit) {
    val tokens = LuvtterTheme.tokens
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.colors.paper)
            .verticalScroll(scroll)
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "组件沙盒 · letter primitives",
                style = tokens.typography.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            )
        }

        section("Postmark") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Postmark(city = "上海", date = "腊月十一", size = 74.dp, rotateDeg = -8f)
                Postmark(city = "京都", date = "立春前", size = 90.dp, rotateDeg = 6f)
                Postmark(city = "大理", date = "霜降", size = 60.dp, rotateDeg = -16f)
            }
        }

        section("Stamp(四档)") {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stamps.all.forEach { Stamp(it, size = 56.dp) }
            }
        }

        section("WaxSeal") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                WaxSeal("寻", size = 64.dp)
                WaxSeal("林", size = 52.dp)
                WaxSeal("沈", size = 42.dp, broken = true)
                WaxSeal("己", size = 30.dp, broken = true)
            }
        }

        section("EnvelopeThumb(opened / sealed)") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                EnvelopeThumb(
                    EnvelopeView(
                        direction = EnvelopeView.Direction.Incoming,
                        partyName = "林砚",
                        partyInitial = "林",
                        cityOrAddress = "京都",
                        sentAt = "癸卯年 冬月初七",
                        stamp = Stamps.airmail,
                        opened = true,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                EnvelopeThumb(
                    EnvelopeView(
                        direction = EnvelopeView.Direction.Incoming,
                        partyName = "苏晴",
                        partyInitial = "苏",
                        cityOrAddress = "苏州",
                        sentAt = "今晨",
                        stamp = Stamps.urgent,
                        opened = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                EnvelopeThumb(
                    EnvelopeView(
                        direction = EnvelopeView.Direction.Outgoing,
                        partyName = "未来的自己",
                        partyInitial = "己",
                        cityOrAddress = "同一座城",
                        sentAt = "明日送达",
                        stamp = Stamps.ordinary,
                        opened = true,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        section("RouteMap(三档进度)") {
            Column(
                modifier = Modifier.fillMaxWidth().background(tokens.colors.paperRaised).paperGrain(seed = 42L).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RouteMap(progress = 0.12f, fromLabel = "上海", toLabel = "柏林")
                RouteMap(progress = 0.55f, fromLabel = "上海", toLabel = "京都")
                RouteMap(progress = 0.88f, fromLabel = "上海", toLabel = "苏州")
            }
        }

        section("EnvelopeIcon(独立)") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                EnvelopeIcon()
                EnvelopeIcon(modifier = Modifier.size(48.dp, 30.dp))
                EnvelopeIcon(modifier = Modifier.size(72.dp, 44.dp))
            }
        }
    }
}

@Composable
private fun section(label: String, content: @Composable () -> Unit) {
    val tokens = LuvtterTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = tokens.typography.meta.copy(fontSize = 11.sp, color = tokens.colors.inkFaded),
        )
        Box(modifier = Modifier.wrapContentSize()) { content() }
    }
}
