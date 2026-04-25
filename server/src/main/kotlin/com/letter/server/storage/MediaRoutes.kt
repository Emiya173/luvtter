package com.letter.server.storage

import com.letter.contract.dto.ApiResponse
import com.letter.contract.dto.SignGetRequest
import com.letter.contract.dto.SignGetResponse
import com.letter.contract.dto.SignPutRequest
import com.letter.contract.dto.SignPutResponse
import com.letter.contract.dto.SignalDto
import com.letter.contract.dto.UploadDoneRequest
import com.letter.server.auth.userId
import com.letter.server.common.now
import com.letter.server.config.NotFoundException
import com.letter.server.mail.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val log = KotlinLogging.logger {}

fun Route.mediaRoutes(storage: StorageService) {
    authenticate("auth-jwt") {
        route("/api/v1/uploads/photo") {
            post("/sign-put") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<SignPutRequest>()
                storage.validatePhotoUpload(req.contentType, req.sizeBytes)
                val key = storage.newPhotoKey(uid, req.contentType)
                val putUrl = storage.presignPut(key, req.contentType)
                val getUrl = storage.presignGet(key)
                log.info { "media.sign-put user=$uid key=$key size=${req.sizeBytes}" }
                call.respond(
                    ApiResponse(
                        SignPutResponse(
                            objectKey = key,
                            uploadUrl = putUrl,
                            getUrl = getUrl,
                            expiresInSeconds = storage.uploadTtlSeconds
                        )
                    )
                )
            }
            post("/sign-get") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<SignGetRequest>()
                if (!storage.isUserOwnedKey(uid, req.objectKey)) {
                    throw NotFoundException(message = "对象不存在")
                }
                val url = storage.presignGet(req.objectKey)
                call.respond(
                    ApiResponse(
                        SignGetResponse(url = url, expiresInSeconds = storage.downloadTtlSeconds)
                    )
                )
            }
            post("/done") {
                val uid = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<UploadDoneRequest>()
                if (!storage.isUserOwnedKey(uid, req.objectKey)) {
                    throw NotFoundException(message = "对象不存在")
                }
                NotificationService.emitSignal(
                    uid,
                    SignalDto(
                        type = "upload_done",
                        objectKey = req.objectKey,
                        sizeBytes = req.sizeBytes,
                        ts = now().toString()
                    )
                )
                log.info { "media.done user=$uid key=${req.objectKey} size=${req.sizeBytes ?: -1}" }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
