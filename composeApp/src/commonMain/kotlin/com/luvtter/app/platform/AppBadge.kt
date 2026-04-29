package com.luvtter.app.platform

/**
 * 把应用图标上的未读 badge 设置为 [count]。0 表示清除。
 * 实际效果按平台:
 * - Desktop(macOS):dock 图标右上角红圈数字
 * - Desktop(Linux/Windows):AWT Taskbar 不支持时静默
 * - Android / iOS:暂未接入(launcher badge 与系统通知绑定,需另起通道)
 */
expect fun setAppBadgeCount(count: Int)
