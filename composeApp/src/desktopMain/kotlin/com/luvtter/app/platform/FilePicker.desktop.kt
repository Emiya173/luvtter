package com.luvtter.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual class FilePicker actual constructor() {
    actual suspend fun pickImage(): PickedImage? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = "选择要附加的图片"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter(
                "图片 (jpg, jpeg, png, gif, webp)",
                "jpg", "jpeg", "png", "gif", "webp"
            )
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return@withContext null
        val file: File = chooser.selectedFile ?: return@withContext null
        val bytes = file.readBytes()
        PickedImage(
            filename = file.name,
            contentType = guessContentType(file.name),
            bytes = bytes,
        )
    }

    private fun guessContentType(filename: String): String =
        when (filename.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
}
