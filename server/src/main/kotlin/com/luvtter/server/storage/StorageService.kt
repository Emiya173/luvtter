package com.luvtter.server.storage

import com.luvtter.server.common.newId
import com.luvtter.server.config.ValidationException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.http.Method
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

private val ALLOWED_IMAGE_TYPES = setOf(
    "image/jpeg", "image/png", "image/webp", "image/gif"
)

private val ALLOWED_SCAN_TYPES = setOf(
    "image/jpeg", "image/png", "image/webp", "application/pdf"
)

private val ALLOWED_HANDWRITING_TYPES = setOf(
    "application/json", "image/png"
)

private val EXTENSIONS = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
    "image/gif" to "gif",
    "application/pdf" to "pdf",
    "application/json" to "json"
)

@OptIn(ExperimentalUuidApi::class)
class StorageService(private val cfg: StorageConfig) {

    private val client: MinioClient by lazy {
        MinioClient.builder()
            .endpoint(cfg.endpoint)
            .credentials(cfg.accessKey, cfg.secretKey)
            .region(cfg.region)
            .build()
            .also { ensureBucket(it) }
    }

    private fun ensureBucket(c: MinioClient) {
        runCatching {
            val exists = c.bucketExists(BucketExistsArgs.builder().bucket(cfg.bucket).build())
            if (!exists) {
                c.makeBucket(MakeBucketArgs.builder().bucket(cfg.bucket).build())
                log.info { "storage.bucket-created bucket=${cfg.bucket}" }
            }
        }.onFailure { e -> log.warn(e) { "storage.ensure-bucket-failed bucket=${cfg.bucket}" } }
    }

    fun validatePhotoUpload(contentType: String, sizeBytes: Long) {
        if (contentType !in ALLOWED_IMAGE_TYPES) {
            throw ValidationException("不支持的图片类型: $contentType")
        }
        if (sizeBytes <= 0) throw ValidationException("文件大小必须为正")
        if (sizeBytes > cfg.maxPhotoBytes) {
            throw ValidationException("图片超过上限 ${cfg.maxPhotoBytes / (1024 * 1024)}MB")
        }
    }

    fun newPhotoKey(userId: Uuid, contentType: String): String {
        val ext = EXTENSIONS[contentType] ?: "bin"
        return "users/$userId/photos/${newId()}.$ext"
    }

    fun validateScanUpload(contentType: String, sizeBytes: Long) {
        if (contentType !in ALLOWED_SCAN_TYPES) {
            throw ValidationException("不支持的扫描类型: $contentType (仅 image/jpeg, image/png, image/webp, application/pdf)")
        }
        if (sizeBytes <= 0) throw ValidationException("文件大小必须为正")
        if (sizeBytes > cfg.maxScanBytes) {
            throw ValidationException("扫描件超过上限 ${cfg.maxScanBytes / (1024 * 1024)}MB")
        }
    }

    fun newScanKey(userId: Uuid, contentType: String): String {
        val ext = EXTENSIONS[contentType] ?: "bin"
        return "users/$userId/scans/${newId()}.$ext"
    }

    fun validateHandwritingUpload(contentType: String, sizeBytes: Long) {
        if (contentType !in ALLOWED_HANDWRITING_TYPES) {
            throw ValidationException("不支持的笔迹类型: $contentType (仅 application/json, image/png)")
        }
        if (sizeBytes <= 0) throw ValidationException("文件大小必须为正")
        if (sizeBytes > cfg.maxHandwritingBytes) {
            throw ValidationException("笔迹超过上限 ${cfg.maxHandwritingBytes / (1024 * 1024)}MB")
        }
    }

    fun newHandwritingKey(userId: Uuid, contentType: String): String {
        val ext = EXTENSIONS[contentType] ?: "bin"
        return "users/$userId/handwriting/${newId()}.$ext"
    }

    fun isUserOwnedKey(userId: Uuid, key: String): Boolean =
        key.startsWith("users/$userId/")

    fun newExportKey(userId: Uuid): String =
        "users/$userId/exports/${newId()}.zip"

    /** 服务端直传(用于归档导出 ZIP),不走 presigned PUT。返回写入字节数。 */
    fun uploadBytes(objectKey: String, bytes: ByteArray, contentType: String): Long {
        client.putObject(
            PutObjectArgs.builder()
                .bucket(cfg.bucket)
                .`object`(objectKey)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build()
        )
        return bytes.size.toLong()
    }

    fun presignPut(objectKey: String, contentType: String, ttlSeconds: Int = cfg.uploadTtlSeconds): String =
        client.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(cfg.bucket)
                .`object`(objectKey)
                .expiry(ttlSeconds, TimeUnit.SECONDS)
                .extraHeaders(mapOf("Content-Type" to contentType))
                .build()
        )

    fun presignGet(objectKey: String, ttlSeconds: Int = cfg.downloadTtlSeconds): String =
        client.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(cfg.bucket)
                .`object`(objectKey)
                .expiry(ttlSeconds, TimeUnit.SECONDS)
                .build()
        )

    val downloadTtlSeconds: Int get() = cfg.downloadTtlSeconds
    val uploadTtlSeconds: Int get() = cfg.uploadTtlSeconds
}
