package com.luvtter.app.platform

// iOS 设置 applicationIconBadgeNumber 需要先 UNUserNotificationCenter 取得 .badge 授权,
// 当前还没接通知通道,先 no-op。
actual fun setAppBadgeCount(count: Int) {
    // no-op
}
