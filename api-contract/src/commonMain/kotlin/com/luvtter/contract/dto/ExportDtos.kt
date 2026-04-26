package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

/**
 * 「我的信件」一次性归档导出结果。服务端把当前用户全部 sender / recipient 信件序列化成 ZIP
 * (`manifest.json` + `letters.json`) 落到 MinIO `users/{uid}/exports/...`,
 * 并即时签发一个短期 GET URL 供客户端下载。
 */
@Serializable
data class ExportResultDto(
    val objectKey: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val letterCount: Int,
    val expiresInSeconds: Int,
    val generatedAt: String,
)
