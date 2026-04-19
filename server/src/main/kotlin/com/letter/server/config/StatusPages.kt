package com.letter.server.config

import com.letter.contract.dto.ApiError
import com.letter.contract.dto.ErrorCodes
import com.letter.contract.dto.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

private val logger = KotlinLogging.logger {}

open class ApiException(
    val code: String,
    override val message: String,
    val status: HttpStatusCode = HttpStatusCode.BadRequest,
    val details: Map<String, String>? = null
) : RuntimeException(message)

class NotFoundException(
    code: String = ErrorCodes.NOT_FOUND,
    message: String = "Resource not found"
) : ApiException(code, message, HttpStatusCode.NotFound)

class UnauthorizedException(
    message: String = "Unauthorized"
) : ApiException(ErrorCodes.UNAUTHORIZED, message, HttpStatusCode.Unauthorized)

class ValidationException(
    message: String,
    details: Map<String, String>? = null
) : ApiException(ErrorCodes.VALIDATION_FAILED, message, HttpStatusCode.UnprocessableEntity, details)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                ErrorResponse(ApiError(cause.code, cause.message, cause.details))
            )
        }
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ApiError(
                    ErrorCodes.INTERNAL_ERROR,
                    "服务器内部错误,请稍后重试"
                ))
            )
        }
    }
}
