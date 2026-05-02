package com.luvtter.app.ui.letter

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// 来源:webui/data.jsx STAMPS / STATIONERY / FONTS_ZH。
// 这些是「展示用」固定数据,服务端 catalog API 后续应替换。

@Immutable
data class StampSpec(
    val id: String,
    val name: String,
    val nameEn: String,
    val speed: String,
    val days: String,
    val capacity: Int,
    val accent: Color,
)

object Stamps {
    val ordinary = StampSpec("ordinary", "平信", "Ordinary", "最慢", "7–14 日", 10, Color(0xFF5A6F52))
    val airmail = StampSpec("airmail", "航空", "Airmail", "中等", "3–7 日", 20, Color(0xFF2B3A5C))
    val express = StampSpec("express", "特快", "Express", "较快", "1–3 日", 30, Color(0xFF8A5A2B))
    val urgent = StampSpec("urgent", "限时", "Urgent", "最快", "数小时", 50, Color(0xFF8B2E1F))

    val all = listOf(ordinary, airmail, express, urgent)
    fun byId(id: String): StampSpec = all.firstOrNull { it.id == id } ?: airmail
}

enum class StationeryRule { None, Lines, Grid, Tatebun }

@Immutable
data class StationerySpec(
    val id: String,
    val name: String,
    val tint: Color,
    val rule: StationeryRule,
)

object Stationery {
    // 与 server V3__seed_catalog.sql 的 code 对齐:classic / linen / kraft
    // 同时保留旧名(cream/lined/grid/ruled)作为别名,方便组件直接引用
    val classic = StationerySpec("classic", "经典素白", Color(0xFFFAF6EC), StationeryRule.None)
    val linen = StationerySpec("linen", "亚麻横格", Color(0xFFF1EAD4), StationeryRule.Lines)
    val kraft = StationerySpec("kraft", "牛皮方格", Color(0xFFE8D9B3), StationeryRule.Grid)
    // 备用稿纸视觉(竖格)。当前服务端无 ruled 种子,但 byId 可识别。
    val ruled = StationerySpec("ruled", "稿纸", Color(0xFFF5ECE0), StationeryRule.Tatebun)

    // 旧别名,保留兼容性
    val cream = classic
    val lined = linen
    val grid = kraft

    val all = listOf(classic, linen, kraft, ruled)

    /** 兼容旧 code:cream → classic / lined → linen / grid → kraft。 */
    private fun normalize(id: String): String = when (id) {
        "cream" -> "classic"
        "lined" -> "linen"
        "grid" -> "kraft"
        else -> id
    }

    fun byId(id: String): StationerySpec =
        all.firstOrNull { it.id == normalize(id) } ?: classic
}

@Immutable
data class FontSpec(val id: String, val name: String, val family: () -> FontFamily)

// 给 ComposeScreen 字体选择器用 —— family 工厂函数延后到 @Composable 里调用拿 LuvtterTokens
