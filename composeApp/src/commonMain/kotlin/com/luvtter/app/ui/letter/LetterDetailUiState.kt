package com.luvtter.app.ui.letter

import com.luvtter.contract.dto.FolderDto
import com.luvtter.contract.dto.LetterDetailDto
import com.luvtter.contract.dto.LetterEventDto

data class LetterDetailUiState(
    val detail: LetterDetailDto? = null,
    val events: List<LetterEventDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val error: String? = null
)
