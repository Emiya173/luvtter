package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(val data: T)

@Serializable
data class PagedResponse<T>(
    val data: List<T>,
    val cursor: Cursor? = null
)

@Serializable
data class Cursor(
    val next: String? = null,
    val hasMore: Boolean = false
)

@Serializable
data class ErrorResponse(val error: ApiError)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null
)

object ErrorCodes {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"

    const val USER_NOT_FOUND = "USER_NOT_FOUND"
    const val HANDLE_TAKEN = "HANDLE_TAKEN"
    const val HANDLE_FINALIZED = "HANDLE_FINALIZED"

    const val LETTER_NOT_FOUND = "LETTER_NOT_FOUND"
    const val LETTER_NOT_EDITABLE = "LETTER_NOT_EDITABLE"
    const val DRAFT_SEALED = "DRAFT_SEALED"
    const val INSUFFICIENT_STAMPS = "INSUFFICIENT_STAMPS"
    const val WEIGHT_EXCEEDED = "WEIGHT_EXCEEDED"
    const val RECIPIENT_REFUSES = "RECIPIENT_REFUSES"
}
