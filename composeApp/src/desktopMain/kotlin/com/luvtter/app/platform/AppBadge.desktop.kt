package com.luvtter.app.platform

import java.awt.Taskbar

// AWT Taskbar 在 macOS 下能正经显示 dock badge,
// Windows / 多数 Linux DE 上 ICON_BADGE_TEXT 不被支持 —— isSupported 守门后静默跳过。
private val taskbar: Taskbar? = runCatching {
    if (Taskbar.isTaskbarSupported()) Taskbar.getTaskbar() else null
}.getOrNull()

actual fun setAppBadgeCount(count: Int) {
    val tb = taskbar ?: return
    runCatching {
        if (tb.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) {
            tb.setIconBadge(if (count > 0) count.toString() else null)
        }
    }
}
