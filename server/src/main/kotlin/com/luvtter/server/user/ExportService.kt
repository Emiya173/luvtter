package com.luvtter.server.user

import com.luvtter.contract.dto.ExportResultDto
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.server.common.now
import com.luvtter.server.config.NotFoundException
import com.luvtter.server.db.Letters
import com.luvtter.server.db.Users
import com.luvtter.server.mail.LetterService
import com.luvtter.server.storage.StorageService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}
private val jsonCodec = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
private data class ExportManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val userId: String,
    val handle: String?,
    val displayName: String?,
    val letterCount: Int,
)

@Serializable
private data class ExportEnvelope(
    val manifest: ExportManifest,
    val letters: List<LetterDetailDto>,
)

@OptIn(ExperimentalUuidApi::class)
class ExportService(
    private val storage: StorageService,
    private val letters: LetterService,
) {

    /**
     * 同步生成 ZIP 并直传到 MinIO,返回签发的下载 URL。
     * ZIP 内含 `manifest.json`(用户元信息 + 计数)和 `letters.json`(全部 detail DTO)。
     * detail 中扫描信 / 手写信 / 图片附件的 `bodyUrl` / `mediaUrl` 是 24h 内的 presigned GET。
     */
    fun exportForUser(userId: Uuid): ExportResultDto {
        val (handle, displayName, letterIds) = transaction {
            val u = Users.selectAll().where { Users.id eq userId }.firstOrNull()
                ?: throw NotFoundException(message = "用户不存在")
            val ids = Letters.selectAll()
                .where { (Letters.senderId eq userId) or (Letters.recipientId eq userId) }
                .orderBy(Letters.createdAt to SortOrder.ASC)
                .map { it[Letters.id] }
            Triple(u[Users.handle], u[Users.displayName], ids)
        }
        val details = letterIds.mapNotNull { lid ->
            runCatching { letters.detailForExport(userId, lid) }.getOrNull()
        }
        val ts = now().toString()
        val envelope = ExportEnvelope(
            manifest = ExportManifest(
                schemaVersion = 1,
                generatedAt = ts,
                userId = userId.toString(),
                handle = handle,
                displayName = displayName,
                letterCount = details.size,
            ),
            letters = details,
        )
        val zipBytes = buildZip(envelope)
        val key = storage.newExportKey(userId)
        val size = storage.uploadBytes(key, zipBytes, "application/zip")
        val url = storage.presignGet(key)
        log.info { "export.create user=$userId key=$key letters=${details.size} size=$size" }
        return ExportResultDto(
            objectKey = key,
            downloadUrl = url,
            sizeBytes = size,
            letterCount = details.size,
            expiresInSeconds = storage.downloadTtlSeconds,
            generatedAt = ts,
        )
    }

    private fun buildZip(envelope: ExportEnvelope): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(jsonCodec.encodeToString(envelope.manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("letters.json"))
            zip.write(jsonCodec.encodeToString(envelope.letters).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return baos.toByteArray()
    }
}
