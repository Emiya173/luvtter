package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class FolderDto(
    val id: String,
    val name: String,
    val icon: String? = null,
    val orderIndex: Int = 0
)

@Serializable
data class CreateFolderRequest(val name: String, val icon: String? = null)

@Serializable
data class UpdateFolderRequest(val name: String? = null, val icon: String? = null)

@Serializable
data class ReorderFoldersRequest(val orderedIds: List<String>)

@Serializable
data class AssignFolderRequest(val folderId: String? = null)

@Serializable
data class DailyRewardDto(val claimed: Boolean, val grants: List<UserAssetDto> = emptyList())
