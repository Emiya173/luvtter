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
    val cream = StationerySpec("cream", "奶白", Color(0xFFFAF6EC), StationeryRule.None)
    val lined = StationerySpec("lined", "横格", Color(0xFFF6F1E3), StationeryRule.Lines)
    val grid = StationerySpec("grid", "方格", Color(0xFFF4EFE1), StationeryRule.Grid)
    val ruled = StationerySpec("ruled", "稿纸", Color(0xFFF5ECE0), StationeryRule.Tatebun)

    val all = listOf(cream, lined, grid, ruled)
    fun byId(id: String): StationerySpec = all.firstOrNull { it.id == id } ?: cream
}

@Immutable
data class FontSpec(val id: String, val name: String, val family: () -> FontFamily)

// 给 ComposeScreen 字体选择器用 —— family 工厂函数延后到 @Composable 里调用拿 LuvtterTokens
