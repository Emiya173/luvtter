package com.luvtter.server.storage

import com.luvtter.contract.dto.*
import com.luvtter.server.auth.userId
import com.luvtter.server.common.now
import com.luvtter.server.config.NotFoundException
import com.luvtter.server.mail.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val log = KotlinLogging.logger {}

fun Route.mediaRoutes(storage: StorageService) {
    authenticate("auth-jwt") {
        uploadKind(
            storage = storage,
            pathPrefix = "/api/v1/uploads/photo",
            kindLabel = "photo",
            validate = storage::validatePhotoUpload,
            newKey = storage::newPhotoKey
        )
        uploadKind(
            storage = storage,
            pathPrefix = "/api/v1/uploads/scan",
            kindLabel = "scan",
            validate = storage::validateScanUpload,
            newKey = storage::newScanKey
        )
        uploadKind(
            storage = storage,
            pathPrefix = "/api/v1/uploads/handwriting",
            kindLabel = "handwriting",
            validate = storage::validateHandwritingUpload,
            newKey = storage::newHandwritingKey
        )
    }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private fun Route.uploadKind(
    storage: StorageService,
    pathPrefix: String,
    kindLabel: String,
    validate: (String, Long) -> Unit,
    newKey: (kotlin.uuid.Uuid, String) -> String
) {
    route(pathPrefix) {
        post("/sign-put") {
            val uid = call.principal<JWTPrincipal>()!!.userId()
            val req = call.receive<SignPutRequest>()
            validate(req.contentType, req.sizeBytes)
            val key = newKey(uid, req.contentType)
            val putUrl = storage.presignPut(key, req.contentType)
            val getUrl = storage.presignGet(key)
            log.info { "media.sign-put kind=$kindLabel user=$uid key=$key size=${req.sizeBytes}" }
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
            log.info { "media.done kind=$kindLabel user=$uid key=${req.objectKey} size=${req.sizeBytes ?: -1}" }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
