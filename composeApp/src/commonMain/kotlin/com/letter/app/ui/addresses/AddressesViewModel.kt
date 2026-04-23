package com.letter.app.ui.addresses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letter.contract.dto.CreateAddressRequest
import com.letter.shared.network.AddressApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddressesViewModel(private val addresses: AddressApi) : ViewModel() {
    private val _state = MutableStateFlow(AddressesUiState())
    val state: StateFlow<AddressesUiState> = _state.asStateFlow()

    init { viewModelScope.launch { runCatching { reload() }.onFailure { e -> _state.update { it.copy(status = e.message) } } } }

    fun onLabelChange(v: String) = _state.update { it.copy(label = v) }
    fun onLatChange(v: String) = _state.update { it.copy(lat = v) }
    fun onLngChange(v: String) = _state.update { it.copy(lng = v) }
    fun onAnchorToggle(id: String) = _state.update { it.copy(anchorId = if (it.anchorId == id) null else id) }
    fun onVirtualDistanceChange(v: String) = _state.update { it.copy(virtualDistance = v.filter(Char::isDigit).take(4)) }

    private suspend fun reload() {
        _state.update {
            it.copy(
                addresses = addresses.list(),
                anchors = addresses.listAnchors()
            )
        }
    }

    fun create() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = null) }
            try {
                val req = if (s.anchorId != null) CreateAddressRequest(
                    label = s.label,
                    type = "virtual",
                    anchorId = s.anchorId,
                    virtualDistance = s.virtualDistance.toIntOrNull()?.coerceIn(0, 1000) ?: 100
                ) else CreateAddressRequest(
                    label = s.label, type = "real",
                    latitude = s.lat.toDouble(), longitude = s.lng.toDouble()
                )
                addresses.create(req)
                _state.update { it.copy(label = "", lat = "", lng = "", anchorId = null) }
                reload()
            } catch (e: Exception) {
                _state.update { it.copy(status = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { runCatching { addresses.setDefault(id); reload() } }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { addresses.delete(id); reload() } }
    }
}
