package com.luvtter.shared.network

import com.luvtter.contract.dto.SignGetRequest
import com.luvtter.contract.dto.SignGetResponse
import com.luvtter.contract.dto.SignPutRequest
import com.luvtter.contract.dto.SignPutResponse
import com.luvtter.contract.dto.UploadDoneRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

private val log = KotlinLogging.logger {}

/**
 * 客户端上传链路:
 *   1) signPut(filename, contentType, sizeBytes) → 拿到 objectKey + uploadUrl
 *   2) 经 rawClient PUT 字节到 uploadUrl (无 JWT, 无 ContentNegotiation)
 *   3) notifyUploadDone(objectKey) → 服务端发 upload_done signal
 *   4) 调用方拿 objectKey 去做 letters.addPhoto(objectKey=...)
 */
class MediaApi(
    private val client: HttpClient,
    private val rawClient: HttpClient,
) {
    suspend fun signPut(filename: String, contentType: String, sizeBytes: Long): SignPutResponse =
        client.post("/api/v1/uploads/photo/sign-put") {
            setBody(SignPutRequest(filename = filename, contentType = contentType, sizeBytes = sizeBytes))
        }.unwrap()

    suspend fun signGet(objectKey: String): SignGetResponse =
        client.post("/api/v1/uploads/photo/sign-get") {
            setBody(SignGetRequest(objectKey = objectKey))
        }.unwrap()

    suspend fun notifyUploadDone(objectKey: String, sizeBytes: Long? = null) {
        client.post("/api/v1/uploads/photo/done") {
            setBody(UploadDoneRequest(objectKey = objectKey, sizeBytes = sizeBytes))
        }.ensureSuccess()
    }

    /** 端到端: sign → PUT → done。返回 objectKey。 */
    suspend fun uploadPhoto(filename: String, contentType: String, bytes: ByteArray): String {
        val sized = bytes.size.toLong()
        val signed = signPut(filename, contentType, sized)
        log.info { "media.upload start key=${signed.objectKey} size=$sized type=$contentType" }
        val resp = rawClient.put(signed.uploadUrl) {
            contentType(ContentType.parse(contentType))
            setBody(bytes)
        }
        if (!resp.status.isSuccess()) {
            val body = runCatching { String(resp.bodyAsBytes()) }.getOrDefault("")
            throw RuntimeException("S3 PUT 失败: ${resp.status} ${body.take(200)}")
        }
        runCatching { notifyUploadDone(signed.objectKey, sized) }
            .onFailure { log.warn(it) { "media.done 通知失败,忽略 (附件仍可用)" } }
        log.info { "media.upload done key=${signed.objectKey}" }
        return signed.objectKey
    }

    /** 通过 sign-get 取一份临时下载 URL,然后下载字节(用于预览或缓存)。 */
    suspend fun fetchBytes(objectKey: String): ByteArray {
        val url = signGet(objectKey).url
        return rawClient.get(url).bodyAsBytes()
    }
}
