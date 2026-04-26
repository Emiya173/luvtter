package com.luvtter.app.platform

actual class FilePicker actual constructor() {
    /** Android 端 UI 尚未编写,占位返回 null。接入时改为 ActivityResultContracts.GetContent。 */
    actual suspend fun pickImage(): PickedImage? = null
    actual suspend fun pickScan(): PickedImage? = null
}
