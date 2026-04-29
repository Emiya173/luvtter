package com.luvtter.app.platform

// Android launcher badge 由系统通知触发(NotificationManager + channel),
// 应用进程内单独「设置数字」没有跨厂商标准 API。等接入本地通知后再补。
actual fun setAppBadgeCount(count: Int) {
    // no-op
}
