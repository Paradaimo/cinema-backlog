package com.cb.backlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cb.backlog.data.SupabaseRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MainState {
    data object Loading : MainState
    data object NotAuthed : MainState
    data object NeedSetup : MainState
    data class Ready(val listId: String) : MainState
}

class MainViewModel(private val repo: SupabaseRepo) : ViewModel() {

    private val _state = MutableStateFlow<MainState>(MainState.Loading)
    val state: StateFlow<MainState> = _state

    init {
        viewModelScope.launch {
            repo.sessionStatus.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (repo.currentUserId() == null) {
                _state.update { MainState.NotAuthed }
            } else {
                val membership = runCatching { repo.myMembership() }.getOrNull()
                if (membership == null) {
                    _state.update { MainState.NeedSetup }
                } else {
                    _state.update { MainState.Ready(membership.listId) }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { repo.signOut() }
            _state.update { MainState.NotAuthed }
        }
    }
}
