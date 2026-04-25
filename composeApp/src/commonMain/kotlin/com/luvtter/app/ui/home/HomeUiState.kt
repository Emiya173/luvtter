package com.luvtter.app.ui.home

import com.luvtter.contract.dto.AddressDto
import com.luvtter.contract.dto.FolderDto
import com.luvtter.contract.dto.LetterSummaryDto
import com.luvtter.contract.dto.NotificationDto

enum class HomeTab(val label: String) {
    Inbox("收件箱"), Outbox("发件箱"), Drafts("草稿"), Favorites("收藏"), Folders("分类")
}

data class HomeUiState(
    val tab: HomeTab = HomeTab.Inbox,
    val letters: List<LetterSummaryDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val unread: Int = 0,
    val reward: String? = null,
    val showHidden: Boolean = false,

    val addresses: List<AddressDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val selectedFolderId: String? = null,
    val notifications: List<NotificationDto> = emptyList()
)
