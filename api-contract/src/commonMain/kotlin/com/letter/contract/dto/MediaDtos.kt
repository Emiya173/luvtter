package com.letter.contract.dto

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
