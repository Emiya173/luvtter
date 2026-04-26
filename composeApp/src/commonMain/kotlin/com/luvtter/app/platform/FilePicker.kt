package com.luvtter.app.platform

data class PickedImage(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

/**
 * 平台原生文件选择。返回 null 表示用户取消或当前平台暂不支持。
 * Desktop 用 JFileChooser；Android/iOS 暂为 stub,等接入对应 UI 时补 actual。
 */
expect class FilePicker() {
    suspend fun pickImage(): PickedImage?
    /** 选择扫描信文件 (jpg/png/webp + pdf)。 */
    suspend fun pickScan(): PickedImage?
}
