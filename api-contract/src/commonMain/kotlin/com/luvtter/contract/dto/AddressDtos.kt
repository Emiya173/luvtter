package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class AddressDto(
    val id: String,
    val label: String,
    val type: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val country: String? = null,
    val anchorId: String? = null,
    val anchorLat: Double? = null,
    val anchorLng: Double? = null,
    val virtualDistance: Int? = null,
    val isDefault: Boolean = false
)

@Serializable
data class CreateAddressRequest(
    val label: String,
    val type: String, // "real" | "virtual"
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val country: String? = null,
    val anchorId: String? = null,
    val anchorLat: Double? = null,
    val anchorLng: Double? = null,
    val virtualDistance: Int? = null,
    val isDefault: Boolean = false
)

@Serializable
data class UpdateAddressRequest(
    val label: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val country: String? = null,
    val anchorId: String? = null,
    val anchorLat: Double? = null,
    val anchorLng: Double? = null,
    val virtualDistance: Int? = null
)

@Serializable
data class RecipientAddressDto(
    val id: String,
    val label: String,
    val type: String,
    val isDefault: Boolean = false
)

@Serializable
data class VirtualAnchorDto(
    val id: String,
    val code: String,
    val name: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String? = null
)
