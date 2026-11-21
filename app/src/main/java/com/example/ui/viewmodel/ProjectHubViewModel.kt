package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.BranchRepository
import com.example.data.model.BranchBoard
import com.example.data.model.TreeOperations
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HubListState {
    data object Loading : HubListState
    data class Ready(val projects: List<BranchBoard>) : HubListState
    data class Error(val message: String) : HubListState
}

@Immutable
data class ProjectHubUiState(
    val listState: HubListState = HubListState.Loading,
    val transientError: String? = null,
)

class ProjectHubViewModel(
    private val repository: BranchRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val _projects = MutableStateFlow<List<BranchBoard>?>(null)
    private val _streamError = MutableStateFlow<String?>(null)
    private val _transientError = MutableStateFlow<String?>(null)

    private var seededInitialProject = false

    val uiState: StateFlow<ProjectHubUiState> = combine(
        _projects,
        _streamError,
        _transientError,
    ) { list, streamErr, transientErr ->
        val state = when {
            list != null -> HubListState.Ready(list)
            streamErr != null -> HubListState.Error(streamErr)
            else -> HubListState.Loading
        }
        ProjectHubUiState(listState = state, transientError = transientErr)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ProjectHubUiState(listState = HubListState.Loading),
    )

    init {
        observeProjects()
    }

    private fun observeProjects() {
        viewModelScope.launch {
            repository.observeAllBoards(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Failed observing projects", root)
                    _streamError.value = root.message ?: "無法載入專案列表"
                }
                .collect { boards ->
                    _streamError.value = null
                    if (boards.isEmpty() && !seededInitialProject) {
                        seededInitialProject = true
                        val starter = TreeOperations.createStarterBoard(currentUserId)
                        _projects.value = listOf(starter)
                        repository.createBoard(currentUserId, starter).onFailure { err ->
                            _transientError.value = err.message ?: "建立初始專案失敗"
                        }
                    } else {
                        seededInitialProject = true
                        _projects.value = boards
                    }
                }
        }
    }

    fun retry() {
        _streamError.value = null
        observeProjects()
    }

    fun dismissError() {
        _transientError.value = null
    }

    fun createProject(title: String, onCreated: (String) -> Unit) {
        val board = TreeOperations.createNewProjectBoard(currentUserId, title)
        _projects.update { current -> listOf(board) + current.orEmpty() }
        viewModelScope.launch {
            val result = repository.createBoard(currentUserId, board)
            result.onSuccess {
                onCreated(board.id)
            }.onFailure { err ->
                _transientError.value = err.message ?: "新建專案失敗"
            }
        }
    }

    fun renameProject(boardId: String, newTitle: String) {
        val clean = newTitle.trim().take(120)
        if (clean.isEmpty()) return
        _projects.update { list ->
            list?.map { if (it.id == boardId) it.copy(title = clean) else it }
        }
        viewModelScope.launch {
            repository.renameBoard(currentUserId, boardId, clean).onFailure { err ->
                _transientError.value = err.message ?: "重新命名失敗"
            }
        }
    }

    fun deleteProject(boardId: String) {
        _projects.update { list -> list?.filterNot { it.id == boardId } }
        viewModelScope.launch {
            repository.deleteBoard(currentUserId, boardId).onFailure { err ->
                _transientError.value = err.message ?: "刪除專案失敗"
            }
        }
    }

    private companion object {
        const val TAG = "ProjectHubVM"
    }
}
