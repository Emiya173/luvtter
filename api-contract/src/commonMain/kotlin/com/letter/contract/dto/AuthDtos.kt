package com.letter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceName: String? = null,
    val platform: String? = null
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val handle: String,
    val handleFinalized: Boolean,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val onlyFriends: Boolean = false,
    val currentAddressId: String? = null
)

@Serializable
data class SetCurrentAddressRequest(val addressId: String)

@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val onlyFriends: Boolean? = null
)

@Serializable
data class HandleAvailability(val handle: String, val available: Boolean)

@Serializable
data class FinalizeHandleRequest(val handle: String)
