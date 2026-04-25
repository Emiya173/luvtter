package com.luvtter.app.ui.letter

import com.luvtter.contract.dto.FolderDto
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.LetterEventDto
import com.luvtter.contract.dto.StickerDto

data class LetterDetailUiState(
    val detail: LetterDetailDto? = null,
    val events: List<LetterEventDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val stickers: List<StickerDto> = emptyList(),
    val error: String? = null
)
