package com.luvtter.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luvtter.contract.dto.CreateContactRequest
import com.luvtter.contract.dto.UpdateMeRequest
import com.luvtter.shared.auth.TokenStore
import com.luvtter.shared.network.ContactApi
import com.luvtter.shared.network.MeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contacts: ContactApi,
    private val me: MeApi,
    private val tokens: TokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()
    val session get() = tokens.session

    init { viewModelScope.launch { runCatching { reload() }.onFailure { e -> _state.update { it.copy(status = e.message) } } } }

    fun onLookupHandleChange(v: String) = _state.update { it.copy(lookupHandle = v.trim()) }
    fun onNoteChange(v: String) = _state.update { it.copy(note = v) }

    private suspend fun reload() {
        _state.update {
            it.copy(
                contacts = contacts.list(),
                blocks = runCatching { contacts.listBlocks() }.getOrDefault(emptyList())
            )
        }
    }

    fun lookup() {
        val handle = _state.value.lookupHandle
        if (handle.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = null, lookupResult = null) }
            try {
                _state.update { it.copy(lookupResult = contacts.lookup(handle)) }
            } catch (e: Exception) {
                _state.update { it.copy(status = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun addAsContact() {
        val lr = _state.value.lookupResult ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = null) }
            try {
                contacts.create(CreateContactRequest(targetId = lr.user.id, note = _state.value.note.ifBlank { null }))
                _state.update { it.copy(lookupResult = null, lookupHandle = "", note = "") }
                reload()
            } catch (e: Exception) {
                _state.update { it.copy(status = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun blockLookupResult() {
        val lr = _state.value.lookupResult ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, status = null) }
            try {
                contacts.block(lr.user.id)
                _state.update { it.copy(lookupResult = null, lookupHandle = "", note = "") }
                reload()
            } catch (e: Exception) {
                _state.update { it.copy(status = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { runCatching { contacts.delete(id); reload() } }
    }

    fun unblock(userId: String) {
        viewModelScope.launch {
            runCatching { contacts.unblock(userId); reload() }
                .onFailure { e -> _state.update { it.copy(status = e.message) } }
        }
    }

    fun setOnlyFriends(v: Boolean, onFail: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val u = me.update(UpdateMeRequest(onlyFriends = v))
                tokens.updateUser(u)
            }.onFailure { e ->
                _state.update { it.copy(status = e.message) }
                onFail()
            }
        }
    }
}
