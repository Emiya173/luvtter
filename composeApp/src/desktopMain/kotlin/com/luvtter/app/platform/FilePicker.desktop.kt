package com.luvtter.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual class FilePicker actual constructor() {
    actual suspend fun pickImage(): PickedImage? = pickWith(
        title = "选择要附加的图片",
        filterDesc = "图片 (jpg, jpeg, png, gif, webp)",
        extensions = arrayOf("jpg", "jpeg", "png", "gif", "webp"),
    )

    actual suspend fun pickScan(): PickedImage? = pickWith(
        title = "选择扫描信文件",
        filterDesc = "扫描件 (jpg, png, webp, pdf)",
        extensions = arrayOf("jpg", "jpeg", "png", "webp", "pdf"),
    )

    private suspend fun pickWith(
        title: String,
        filterDesc: String,
        extensions: Array<String>,
    ): PickedImage? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter(filterDesc, *extensions)
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
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
}
