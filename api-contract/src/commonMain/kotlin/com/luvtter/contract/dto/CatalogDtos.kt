package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class StampDto(
    val id: String,
    val code: String,
    val name: String,
    val tier: Int,
    val imageUrl: String,
    val weightCapacity: Int,
    val speedFactor: Double,
    val isLimited: Boolean = false
)

@Serializable
data class StationeryDto(
    val id: String,
    val code: String,
    val name: String,
    val backgroundUrl: String,
    val thumbnailUrl: String,
    val isLimited: Boolean = false
)

@Serializable
data class StickerDto(
    val id: String,
    val code: String,
    val name: String,
    val imageUrl: String,
    val weight: Int
)

@Serializable
data class UserAssetDto(
    val assetType: String, // stamp | stationery | sticker
    val assetId: String,
    val quantity: Int
)

@Serializable
data class MyAssetsDto(
    val stamps: List<UserAssetDto>,
    val stationeries: List<UserAssetDto>,
    val stickers: List<UserAssetDto>
)
