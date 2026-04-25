package com.luvtter.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luvtter.contract.dto.LetterSummaryDto
import com.luvtter.shared.network.LetterApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<LetterSummaryDto> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null
)

class SearchViewModel(private val letters: LetterApi) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(v: String) = _state.update { it.copy(query = v) }

    fun search() {
        val q = _state.value.query
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                _state.update { it.copy(results = letters.search(q)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
