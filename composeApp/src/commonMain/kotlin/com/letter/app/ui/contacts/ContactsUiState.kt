package com.letter.app.ui.contacts

import com.letter.contract.dto.BlockDto
import com.letter.contract.dto.ContactDto
import com.letter.contract.dto.LookupResult

data class ContactsUiState(
    val contacts: List<ContactDto> = emptyList(),
    val blocks: List<BlockDto> = emptyList(),
    val lookupHandle: String = "",
    val lookupResult: LookupResult? = null,
    val note: String = "",
    val status: String? = null,
    val loading: Boolean = false
)
