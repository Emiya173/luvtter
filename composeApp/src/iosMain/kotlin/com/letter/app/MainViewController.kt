package com.letter.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

actual val apiBaseUrl: String = "http://localhost:8080"

/**
 * 由 Swift 端调用,返回 UIViewController 嵌入到 iOS 项目中。
 * Swift 侧用法:
 *   let controller = MainViewControllerKt.MainViewController()
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
