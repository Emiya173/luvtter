package com.luvtter.app.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes

data class PickedImage(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray,
) {
    val sizeBytes: Long get() = bytes.size.toLong()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PickedImage

        if (filename != other.filename) return false
        if (contentType != other.contentType) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (sizeBytes != other.sizeBytes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + sizeBytes.hashCode()
        return result
    }
}

/**
 * 跨平台文件选择,基于 FileKit。返回 null 表示用户取消。
 * Android 端需要在 Activity.onCreate 里 `FileKit.init(this)` 一次,Desktop / iOS 自动可用。
 */
class FilePicker {
    suspend fun pickImage(): PickedImage? = pickWith(
        title = "选择要附加的图片",
        extensions = listOf("jpg", "jpeg", "png", "gif", "webp"),
    )

    suspend fun pickScan(): PickedImage? = pickWith(
        title = "选择扫描信文件",
        extensions = listOf("jpg", "jpeg", "png", "webp", "pdf"),
    )

    private suspend fun pickWith(title: String, extensions: List<String>): PickedImage? {
        val file: PlatformFile = FileKit.openFilePicker(
            type = FileKitType.File(extensions),
            mode = FileKitMode.Single,
            title = title,
        ) ?: return null
        val name = file.name
        val bytes = file.readBytes()
        return PickedImage(
            filename = name,
            contentType = guessContentType(name),
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
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
}
