package com.letter.app.ui.letter

import com.letter.contract.dto.FolderDto
import com.letter.contract.dto.LetterDetailDto
import com.letter.contract.dto.LetterEventDto

data class LetterDetailUiState(
    val detail: LetterDetailDto? = null,
    val events: List<LetterEventDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val error: String? = null
)
