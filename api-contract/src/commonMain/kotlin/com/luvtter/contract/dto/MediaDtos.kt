package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class SignPutRequest(
    val filename: String,
    val contentType: String,
    val sizeBytes: Long
)

@Serializable
data class SignPutResponse(
    val objectKey: String,
    val uploadUrl: String,
    val getUrl: String,
    val expiresInSeconds: Int
)

@Serializable
data class SignGetRequest(val objectKey: String)

@Serializable
data class SignGetResponse(
    val url: String,
    val expiresInSeconds: Int
)

/**
 * 扫描信 OCR 任务状态。`status ∈ {pending, in_progress, done, failed}`。
 * 扫描信寄出后才会有 ocr_index 任务排队;草稿期间查询会返回 404。
 */
@Serializable
data class OcrTaskStatusDto(
    val taskId: String,
    val status: String,
    val attempts: Int,
    val maxAttempts: Int,
    val lastError: String? = null,
    val finishedAt: String? = null,
)
