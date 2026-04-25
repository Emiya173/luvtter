package com.luvtter.app.ui.contacts

import com.luvtter.contract.dto.BlockDto
import com.luvtter.contract.dto.ContactDto
import com.luvtter.contract.dto.LookupResult

data class ContactsUiState(
    val contacts: List<ContactDto> = emptyList(),
    val blocks: List<BlockDto> = emptyList(),
    val lookupHandle: String = "",
    val lookupResult: LookupResult? = null,
    val note: String = "",
    val status: String? = null,
    val loading: Boolean = false
)
