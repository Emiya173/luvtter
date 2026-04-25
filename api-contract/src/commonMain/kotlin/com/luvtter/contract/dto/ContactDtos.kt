package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class ContactDto(
    val id: String,
    val target: UserDto,
    val note: String? = null,
    val relation: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class CreateContactRequest(
    val targetId: String,
    val note: String? = null,
    val relation: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class UpdateContactRequest(
    val note: String? = null,
    val relation: String? = null,
    val tags: List<String>? = null
)

@Serializable
data class LookupResult(
    val user: UserDto,
    val acceptsFromMe: Boolean
)

@Serializable
data class BlockDto(
    val id: String,
    val target: UserDto
)

@Serializable
data class CreateBlockRequest(val targetId: String)
