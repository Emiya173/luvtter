package com.luvtter.app.ui.common

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 把服务端返回的 ISO-8601(`Instant`/`OffsetDateTime` 字符串,通常带 Z 或 offset)
 * 解析后转到系统时区,渲染成「yyyy-MM-dd HH:mm:ss」。解析失败回退原串。
 */
fun formatLocalDateTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return formatLdt(ldt)
}

/** 仅日期:yyyy-MM-dd。 */
fun formatLocalDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${pad4(ldt.year)}-${pad2(ldt.month.number)}-${pad2(ldt.day)}"
}

private fun formatLdt(t: LocalDateTime): String =
    "${pad4(t.year)}-${pad2(t.month.number)}-${pad2(t.day)} " +
        "${pad2(t.hour)}:${pad2(t.minute)}:${pad2(t.second)}"

private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()
private fun pad4(n: Int): String = n.toString().padStart(4, '0')
