package com.letter.app.ui.addresses

import com.letter.contract.dto.AddressDto
import com.letter.contract.dto.VirtualAnchorDto

data class AddressesUiState(
    val addresses: List<AddressDto> = emptyList(),
    val anchors: List<VirtualAnchorDto> = emptyList(),
    val label: String = "",
    val lat: String = "",
    val lng: String = "",
    val anchorId: String? = null,
    val virtualDistance: String = "100",
    val status: String? = null,
    val loading: Boolean = false
) {
    val canSubmit: Boolean get() =
        !loading && label.isNotBlank() && (anchorId != null || (lat.isNotBlank() && lng.isNotBlank()))
}
