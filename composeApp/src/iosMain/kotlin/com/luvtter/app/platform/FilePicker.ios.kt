package com.luvtter.app.platform

actual class FilePicker actual constructor() {
    /** iOS 端 UI 尚未编写,占位返回 null。接入时改为 UIDocumentPickerViewController / PHPickerViewController。 */
    actual suspend fun pickImage(): PickedImage? = null
    actual suspend fun pickScan(): PickedImage? = null
}
