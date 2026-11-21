package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.BranchRepository
import com.example.data.model.BranchBoard
import com.example.data.model.BranchNode
import com.example.data.model.NodeVariant
import com.example.data.model.TreeOperations
import com.example.data.model.VariantSnapshot
import com.example.data.model.WorkMode
import com.example.data.model.generateBranchId
import com.example.domain.layout.DragDropTarget
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BoardUiState {
    data object Loading : BoardUiState
    data class Ready(val board: BranchBoard) : BoardUiState
    data class Error(val message: String) : BoardUiState
}

private data class UndoEntry(
    val board: BranchBoard,
    val snapshots: Map<String, VariantSnapshot>,
)

@Immutable
data class WorkspaceUiState(
    val boardState: BoardUiState = BoardUiState.Loading,
    /** null = Layer 1 Trunk Overview; non-null = Layer 2 Drilled-down Branch Mind Map */
    val activeBranchId: String? = null,
    val selectedNodeId: String? = null,
    val editingNodeId: String? = null,
    val isZenMode: Boolean = false,
    val showColorPicker: Boolean = false,
    val statesSheetNodeId: String? = null,
    val canUndo: Boolean = false,
    val flippingNodeIds: Map<String, Int> = emptyMap(),
    val transientError: String? = null,
)

class BranchViewModel(
    private val repository: BranchRepository,
    private val currentUserId: String,
    private val boardId: String,
) : ViewModel() {

    private val _localBoard = MutableStateFlow<BranchBoard?>(null)
    private val _snapshots = MutableStateFlow<Map<String, VariantSnapshot>>(emptyMap())
    private val _interactionState = MutableStateFlow(WorkspaceUiState())
    private val _streamError = MutableStateFlow<String?>(null)

    private val undoStack = ArrayDeque<UndoEntry>()
    private var debouncedSaveJob: Job? = null

    val uiState: StateFlow<WorkspaceUiState> = combine(
        _localBoard,
        _interactionState,
        _streamError,
    ) { board, interaction, streamErr ->
        val boardUiState = when {
            board != null -> BoardUiState.Ready(board)
            streamErr != null -> BoardUiState.Error(streamErr)
            else -> BoardUiState.Loading
        }
        // If the drilled-down branch was deleted/undone away, gracefully return to Trunk
        val validActiveBranchId = interaction.activeBranchId?.takeIf { branchId ->
            board != null && TreeOperations.findNode(board.root, branchId) != null
        }
        interaction.copy(
            boardState = boardUiState,
            activeBranchId = validActiveBranchId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WorkspaceUiState(boardState = BoardUiState.Loading),
    )

    init {
        startObserving()
    }

    private fun startObserving() {
        viewModelScope.launch {
            repository.observeBoard(currentUserId, boardId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing Branch board", rootError)
                    _streamError.value = rootError.message ?: "無法載入專案資料"
                }
                .collect { remoteBoard ->
                    if (remoteBoard != null) {
                        _streamError.value = null
                        _localBoard.value = remoteBoard
                    } else if (_localBoard.value == null) {
                        _streamError.value = "找不到此專案"
                    }
                }
        }

        viewModelScope.launch {
            repository.observeSnapshots(currentUserId, boardId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing state snapshots", firestoreError ?: error)
                }
                .collect { snapshotMap ->
                    _snapshots.value = snapshotMap
                }
        }
    }

    fun retryLoad() {
        _streamError.value = null
        startObserving()
    }

    fun dismissTransientError() {
        _interactionState.update { it.copy(transientError = null) }
    }

    // --- Two-Layer Navigation: Trunk Overview <-> Branch Drill-down ---

    fun drillIntoBranch(branchId: String) {
        _interactionState.update {
            it.copy(
                activeBranchId = branchId,
                selectedNodeId = null,
                editingNodeId = null,
                showColorPicker = false,
            )
        }
    }

    fun exitToTrunk() {
        _interactionState.update {
            it.copy(
                activeBranchId = null,
                selectedNodeId = null,
                editingNodeId = null,
                showColorPicker = false,
            )
        }
    }

    // --- Undo History ---

    private fun recordUndoPoint(board: BranchBoard) {
        undoStack.addLast(UndoEntry(board = board, snapshots = _snapshots.value))
        while (undoStack.size > MAX_UNDO_STEPS) {
            undoStack.removeFirst()
        }
        _interactionState.update { it.copy(canUndo = undoStack.isNotEmpty()) }
    }

    fun performUndo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        debouncedSaveJob?.cancel()
        val currentSnapshots = _snapshots.value
        _localBoard.value = previous.board
        _snapshots.value = previous.snapshots
        _interactionState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                editingNodeId = null,
                showColorPicker = false,
            )
        }

        viewModelScope.launch {
            val toCreate = mutableListOf<VariantSnapshot>()
            val toUpdate = mutableListOf<VariantSnapshot>()
            for ((id, snap) in previous.snapshots) {
                if (id !in currentSnapshots) {
                    toCreate.add(snap)
                } else if (currentSnapshots[id] != snap) {
                    toUpdate.add(snap)
                }
            }
            val toDelete = currentSnapshots.keys.filter { it !in previous.snapshots }
            val result = if (toCreate.isEmpty() && toUpdate.isEmpty() && toDelete.isEmpty()) {
                repository.saveBoard(currentUserId, previous.board)
            } else {
                repository.commitVariantMutation(
                    userId = currentUserId,
                    board = previous.board,
                    snapshotsToCreate = toCreate,
                    snapshotsToUpdate = toUpdate,
                    snapshotsToDelete = toDelete,
                )
            }
            result.onFailure { err ->
                _interactionState.update { it.copy(transientError = err.message ?: "復原同步失敗") }
            }
        }
    }

    // --- Zen Full-Screen Mode & Ambient Cue Mode Toggle ---

    fun toggleZenMode() {
        _interactionState.update {
            val entering = !it.isZenMode
            it.copy(
                isZenMode = entering,
                showColorPicker = if (entering) false else it.showColorPicker,
                selectedNodeId = if (entering) null else it.selectedNodeId,
                editingNodeId = if (entering) null else it.editingNodeId,
            )
        }
    }

    fun exitZenMode() {
        _interactionState.update { it.copy(isZenMode = false) }
    }

    fun setExecutionMode(enabled: Boolean) {
        val targetMode = if (enabled) WorkMode.EXECUTION else WorkMode.DRAFT
        mutateBoard(recordUndo = false) { board -> board.copy(activeMode = targetMode) }
    }

    // --- Selection, Editing & Result Log ---

    fun selectNode(nodeId: String?) {
        _interactionState.update { state ->
            state.copy(
                selectedNodeId = nodeId,
                editingNodeId = if (state.editingNodeId == nodeId) state.editingNodeId else null,
                showColorPicker = if (nodeId == null || nodeId != state.selectedNodeId) false else state.showColorPicker,
            )
        }
    }

    fun startEditingNode(nodeId: String) {
        _interactionState.update {
            it.copy(
                selectedNodeId = nodeId,
                editingNodeId = nodeId,
                showColorPicker = false,
            )
        }
    }

    fun commitNodeTitle(nodeId: String, newTitle: String) {
        val cleaned = newTitle.trim().take(200)
        _interactionState.update { it.copy(editingNodeId = null) }
        if (cleaned.isEmpty()) return
        mutateBoard(recordUndo = true) { board ->
            board.copy(
                root = TreeOperations.updateNode(board.root, nodeId) { node ->
                    node.copy(title = cleaned)
                }
            )
        }
    }

    fun updateResultLog(nodeId: String, resultLog: String) {
        val bounded = resultLog.take(500)
        mutateBoard(debounced = true, recordUndo = true) { board ->
            board.copy(
                root = TreeOperations.updateNode(board.root, nodeId) { node ->
                    node.copy(resultLog = bounded)
                }
            )
        }
    }

    fun toggleColorPicker() {
        _interactionState.update { it.copy(showColorPicker = !it.showColorPicker) }
    }

    fun openStatesSheet(nodeId: String) {
        _interactionState.update {
            it.copy(
                statesSheetNodeId = nodeId,
                showColorPicker = false,
            )
        }
    }

    fun dismissStatesSheet() {
        _interactionState.update { it.copy(statesSheetNodeId = null) }
    }

    // --- Trunk Phase & Branch Creation ---

    fun addPhase(defaultTitle: String) {
        var createdId: String? = null
        mutateBoard(recordUndo = true) { board ->
            val phaseNum = board.root.children.size + 1
            val color = TreeOperations.nextPhaseColor(board.root.children.size)
            val title = "$defaultTitle $phaseNum"
            val (nextRoot, created) = TreeOperations.addChildNode(
                root = board.root,
                parentId = board.root.id,
                title = title,
                colorOverride = color,
            )
            createdId = created?.id
            board.copy(root = nextRoot)
        }
        createdId?.let { id ->
            _interactionState.update { it.copy(selectedNodeId = id) }
        }
    }

    fun addBranchToPhase(phaseId: String, defaultTitle: String) {
        var createdId: String? = null
        mutateBoard(recordUndo = true) { board ->
            val (nextRoot, created) = TreeOperations.addChildNode(
                root = board.root,
                parentId = phaseId,
                title = defaultTitle,
            )
            createdId = created?.id
            board.copy(root = nextRoot)
        }
        createdId?.let { id ->
            _interactionState.update { it.copy(selectedNodeId = id) }
        }
    }

    // --- Strict Leaf-Node Driven Execution & Tree Mutations ---

    /**
     * Strict Bottom-Up Rollup & Foolproof Lock:
     * Only active leaf nodes (zero non-strikethrough children) can cycle PLAN -> DO -> DONE.
     * Parent nodes with active children are strictly locked against manual completion.
     */
    fun cycleNodeStatus(nodeId: String) {
        val board = _localBoard.value ?: return
        val target = TreeOperations.findNode(board.root, nodeId) ?: return
        if (!TreeOperations.isInteractiveLeaf(target)) {
            return
        }
        mutateBoard(recordUndo = true) { current ->
            current.copy(
                root = TreeOperations.updateNode(current.root, nodeId) { node ->
                    node.copy(status = node.status.next())
                }
            )
        }
    }

    fun toggleCollapse(nodeId: String) {
        mutateBoard(recordUndo = false) { board ->
            board.copy(
                root = TreeOperations.updateNode(board.root, nodeId) { node ->
                    node.copy(isCollapsed = !node.isCollapsed)
                }
            )
        }
    }

    fun toggleStrikethrough(nodeId: String) {
        mutateBoard(recordUndo = true) { board ->
            board.copy(root = TreeOperations.toggleStrikethroughCascading(board.root, nodeId))
        }
    }

    fun addChildNode(parentId: String, defaultTitle: String = "新節點") {
        mutateBoard(recordUndo = true) { board ->
            val (nextRoot, _) = TreeOperations.addChildNode(
                root = board.root,
                parentId = parentId,
                title = defaultTitle,
            )
            board.copy(root = nextRoot)
        }
    }

    fun updateNodeColor(nodeId: String, hexColor: String) {
        mutateBoard(recordUndo = true) { board ->
            board.copy(
                root = TreeOperations.updateNode(board.root, nodeId) { node ->
                    node.copy(color = hexColor)
                }
            )
        }
        _interactionState.update { it.copy(showColorPicker = false) }
    }

    fun duplicateNode(nodeId: String) {
        var duplicatedId: String? = null
        mutateBoard(recordUndo = true) { board ->
            val (nextRoot, newId) = TreeOperations.duplicateSubtreeAsSibling(board.root, nodeId)
            duplicatedId = newId
            board.copy(root = nextRoot)
        }
        duplicatedId?.let { newId ->
            _interactionState.update { it.copy(selectedNodeId = newId) }
        }
    }

    fun deleteNode(nodeId: String) {
        val currentBoard = _localBoard.value ?: return
        if (currentBoard.root.id == nodeId) return
        mutateBoard(recordUndo = true) { board ->
            board.copy(root = TreeOperations.deleteNode(board.root, nodeId))
        }
        _interactionState.update { state ->
            state.copy(
                activeBranchId = if (state.activeBranchId == nodeId) null else state.activeBranchId,
                selectedNodeId = if (state.selectedNodeId == nodeId) null else state.selectedNodeId,
                editingNodeId = if (state.editingNodeId == nodeId) null else state.editingNodeId,
                showColorPicker = false,
            )
        }
    }

    fun applyDragDrop(draggedNodeId: String, target: DragDropTarget) {
        mutateBoard(recordUndo = true) { board ->
            val nextRoot = when (target) {
                is DragDropTarget.Reparent -> TreeOperations.reparentNode(
                    root = board.root,
                    draggedId = draggedNodeId,
                    newParentId = target.targetNodeId,
                )
                is DragDropTarget.SiblingGap -> TreeOperations.moveToSiblingIndex(
                    root = board.root,
                    draggedId = draggedNodeId,
                    targetParentId = target.targetParentId,
                    insertIndex = target.insertIndex,
                )
            }
            board.copy(root = nextRoot)
        }
    }

    // --- Node States Management (狀態 A / 狀態 B + 改名 / 設為預設 / 刪除 + 專屬漸層) ---

    fun addStateAutomatically(nodeId: String) {
        val board = _localBoard.value ?: return
        val targetNode = TreeOperations.findNode(board.root, nodeId) ?: return
        recordUndoPoint(board)

        val snapshotsToCreate = mutableListOf<VariantSnapshot>()
        val snapshotsToUpdate = mutableListOf<VariantSnapshot>()
        val existingVariants = targetNode.variants.toMutableList()
        val updatedCache = _snapshots.value.toMutableMap()

        if (existingVariants.isEmpty()) {
            val stateAId = generateBranchId("var")
            val snapAId = generateBranchId("snap")
            val stateA = NodeVariant(
                id = stateAId,
                label = "狀態 A",
                snapshotId = snapAId,
                gradientIndex = 0,
                isDefault = true,
            )
            val snapA = VariantSnapshot(
                id = snapAId,
                userId = currentUserId,
                ownerNodeId = targetNode.id,
                variantId = stateAId,
                label = stateA.label,
                children = targetNode.children,
            )
            existingVariants.add(stateA)
            snapshotsToCreate.add(snapA)
            updatedCache[snapAId] = snapA
        } else {
            val activeVar = targetNode.activeVariant() ?: existingVariants.first()
            val prevSnap = updatedCache[activeVar.snapshotId]
            val savedSnap = VariantSnapshot(
                id = activeVar.snapshotId,
                userId = currentUserId,
                ownerNodeId = targetNode.id,
                variantId = activeVar.id,
                label = activeVar.label,
                children = targetNode.children,
                createdAt = prevSnap?.createdAt,
            )
            if (prevSnap == null) snapshotsToCreate.add(savedSnap) else snapshotsToUpdate.add(savedSnap)
            updatedCache[activeVar.snapshotId] = savedSnap
        }

        val nextLetter = ('A'.code + existingVariants.size).coerceAtMost('Z'.code).toChar()
        val newLabel = "狀態 $nextLetter"
        val usedGradients = existingVariants.map { it.gradientIndex }.toSet()
        val nextGradient = (0 until 6).firstOrNull { it !in usedGradients } ?: (existingVariants.size % 6)

        val clonedChildren = TreeOperations.deepCloneChildren(targetNode.children, parentId = targetNode.id)
        val newVarId = generateBranchId("var")
        val newSnapId = generateBranchId("snap")
        val newVariant = NodeVariant(
            id = newVarId,
            label = newLabel,
            snapshotId = newSnapId,
            gradientIndex = nextGradient,
            isDefault = false,
        )
        val newSnapshot = VariantSnapshot(
            id = newSnapId,
            userId = currentUserId,
            ownerNodeId = targetNode.id,
            variantId = newVarId,
            label = newLabel,
            children = clonedChildren,
        )
        existingVariants.add(newVariant)
        snapshotsToCreate.add(newSnapshot)
        updatedCache[newSnapId] = newSnapshot

        val nextRoot = TreeOperations.updateNode(board.root, nodeId) { node ->
            node.copy(
                variants = existingVariants,
                activeVariantId = newVarId,
                isCollapsed = false,
                children = clonedChildren,
            )
        }
        val updatedBoard = board.copy(root = nextRoot)

        _snapshots.value = updatedCache
        _localBoard.value = updatedBoard
        triggerSubtreeFlip(nodeId, updatedBoard)

        viewModelScope.launch {
            repository.commitVariantMutation(
                userId = currentUserId,
                board = updatedBoard,
                snapshotsToCreate = snapshotsToCreate,
                snapshotsToUpdate = snapshotsToUpdate,
            ).onFailure { err ->
                _interactionState.update { it.copy(transientError = err.message ?: "狀態儲存失敗") }
            }
        }
    }

    fun switchToState(nodeId: String, targetVariantId: String) {
        val board = _localBoard.value ?: return
        val targetNode = TreeOperations.findNode(board.root, nodeId) ?: return
        val currentVar = targetNode.activeVariant() ?: return
        if (currentVar.id == targetVariantId) return
        val nextVar = targetNode.variants.firstOrNull { it.id == targetVariantId } ?: return
        recordUndoPoint(board)

        val cache = _snapshots.value.toMutableMap()
        val existingCurrentSnap = cache[currentVar.snapshotId]
        val outgoingSnapshot = VariantSnapshot(
            id = currentVar.snapshotId,
            userId = currentUserId,
            ownerNodeId = targetNode.id,
            variantId = currentVar.id,
            label = currentVar.label,
            children = targetNode.children,
            createdAt = existingCurrentSnap?.createdAt,
        )
        cache[currentVar.snapshotId] = outgoingSnapshot

        val incomingChildren = cache[nextVar.snapshotId]?.children ?: emptyList()

        val nextRoot = TreeOperations.updateNode(board.root, nodeId) { node ->
            node.copy(
                activeVariantId = nextVar.id,
                isCollapsed = false,
                children = incomingChildren,
            )
        }
        val updatedBoard = board.copy(root = nextRoot)

        _snapshots.value = cache
        _localBoard.value = updatedBoard
        triggerSubtreeFlip(nodeId, updatedBoard)

        viewModelScope.launch {
            val createList = if (existingCurrentSnap == null) listOf(outgoingSnapshot) else emptyList()
            val updateList = if (existingCurrentSnap != null) listOf(outgoingSnapshot) else emptyList()
            repository.commitVariantMutation(
                userId = currentUserId,
                board = updatedBoard,
                snapshotsToCreate = createList,
                snapshotsToUpdate = updateList,
            ).onFailure { err ->
                _interactionState.update { it.copy(transientError = err.message ?: "切換狀態失敗") }
            }
        }
    }

    fun cycleStateVariant(nodeId: String) {
        val board = _localBoard.value ?: return
        val targetNode = TreeOperations.findNode(board.root, nodeId) ?: return
        if (targetNode.variants.size < 2) return
        val currentIdx = targetNode.variants.indexOfFirst { it.id == targetNode.activeVariantId }
            .takeIf { it >= 0 } ?: 0
        val nextVar = targetNode.variants[(currentIdx + 1) % targetNode.variants.size]
        switchToState(nodeId, nextVar.id)
    }

    fun renameState(nodeId: String, variantId: String, newLabel: String) {
        val clean = newLabel.trim().take(60)
        if (clean.isEmpty()) return
        val board = _localBoard.value ?: return
        val targetNode = TreeOperations.findNode(board.root, nodeId) ?: return
        val targetVar = targetNode.variants.firstOrNull { it.id == variantId } ?: return
        recordUndoPoint(board)

        val updatedVariants = targetNode.variants.map {
            if (it.id == variantId) it.copy(label = clean) else it
        }
        val updatedBoard = board.copy(
            root = TreeOperations.updateNode(board.root, nodeId) { node ->
                node.copy(variants = updatedVariants)
            }
        )
        val cache = _snapshots.value.toMutableMap()
        val existingSnap = cache[targetVar.snapshotId]
        val updatedSnap = existingSnap?.copy(label = clean)
        if (updatedSnap != null) {
            cache[targetVar.snapshotId] = updatedSnap
        }

        _snapshots.value = cache
        _localBoard.value = updatedBoard

        viewModelScope.launch {
            if (updatedSnap != null) {
                repository.commitVariantMutation(
                    userId = currentUserId,
                    board = updatedBoard,
                    snapshotsToCreate = emptyList(),
                    snapshotsToUpdate = listOf(updatedSnap),
                )
            } else {
                repository.saveBoard(currentUserId, updatedBoard)
            }.onFailure { err ->
                _interactionState.update { it.copy(transientError = err.message ?: "改名失敗") }
            }
        }
    }

    fun setDefaultState(nodeId: String, variantId: String) {
        mutateBoard(recordUndo = true) { board ->
            board.copy(
                root = TreeOperations.updateNode(board.root, nodeId) { node ->
                    node.copy(
                        variants = node.variants.map { v ->
                            v.copy(isDefault = v.id == variantId)
                        }
                    )
                }
            )
        }
    }

    fun deleteState(nodeId: String, variantId: String) {
        val board = _localBoard.value ?: return
        val targetNode = TreeOperations.findNode(board.root, nodeId) ?: return
        if (targetNode.variants.size <= 1) return
        val toRemove = targetNode.variants.firstOrNull { it.id == variantId } ?: return
        recordUndoPoint(board)

        val remaining = targetNode.variants.filterNot { it.id == variantId }.let { list ->
            if (list.none { it.isDefault } && list.isNotEmpty()) {
                list.mapIndexed { idx, v -> v.copy(isDefault = idx == 0) }
            } else {
                list
            }
        }
        val cache = _snapshots.value.toMutableMap()
        cache.remove(toRemove.snapshotId)

        val deletingActive = targetNode.activeVariantId == variantId
        val nextActive = if (deletingActive) {
            remaining.firstOrNull { it.isDefault } ?: remaining.first()
        } else {
            remaining.firstOrNull { it.id == targetNode.activeVariantId } ?: remaining.first()
        }
        val nextChildren = if (deletingActive) {
            cache[nextActive.snapshotId]?.children ?: emptyList()
        } else {
            targetNode.children
        }

        val finalVariants = if (remaining.size <= 1) emptyList() else remaining
        val finalActiveId = if (remaining.size <= 1) null else nextActive.id
        val snapshotsToDelete = buildList {
            add(toRemove.snapshotId)
            if (remaining.size == 1) {
                add(remaining.first().snapshotId)
                cache.remove(remaining.first().snapshotId)
            }
        }

        val updatedBoard = board.copy(
            root = TreeOperations.updateNode(board.root, nodeId) { node ->
                node.copy(
                    variants = finalVariants,
                    activeVariantId = finalActiveId,
                    children = nextChildren,
                )
            }
        )
        _snapshots.value = cache
        _localBoard.value = updatedBoard
        if (deletingActive) triggerSubtreeFlip(nodeId, updatedBoard)

        viewModelScope.launch {
            repository.commitVariantMutation(
                userId = currentUserId,
                board = updatedBoard,
                snapshotsToCreate = emptyList(),
                snapshotsToUpdate = emptyList(),
                snapshotsToDelete = snapshotsToDelete,
            ).onFailure { err ->
                _interactionState.update { it.copy(transientError = err.message ?: "刪除狀態失敗") }
            }
        }
    }

    private fun triggerSubtreeFlip(nodeId: String, board: BranchBoard) {
        val target = TreeOperations.findNode(board.root, nodeId) ?: return
        val idsToFlip = mutableListOf<String>()
        fun collect(n: BranchNode) {
            idsToFlip.add(n.id)
            n.children.forEach { collect(it) }
        }
        collect(target)
        _interactionState.update { state ->
            val updatedMap = state.flippingNodeIds.toMutableMap()
            idsToFlip.forEach { id -> updatedMap[id] = (updatedMap[id] ?: 0) + 1 }
            state.copy(flippingNodeIds = updatedMap)
        }
    }

    // --- Internal Board Mutation & Persistence ---

    private fun mutateBoard(
        debounced: Boolean = false,
        recordUndo: Boolean = false,
        transform: (BranchBoard) -> BranchBoard,
    ) {
        val current = _localBoard.value ?: return
        val next = transform(current)
        if (next == current) return
        if (recordUndo) {
            recordUndoPoint(current)
        }
        _localBoard.value = next

        if (debounced) {
            debouncedSaveJob?.cancel()
            debouncedSaveJob = viewModelScope.launch {
                delay(420L)
                persistBoard(next)
            }
        } else {
            debouncedSaveJob?.cancel()
            viewModelScope.launch {
                persistBoard(next)
            }
        }
    }

    private suspend fun persistBoard(board: BranchBoard) {
        val result = repository.saveBoard(currentUserId, board)
        result.onFailure { err ->
            _interactionState.update { it.copy(transientError = err.message ?: "雲端同步失敗") }
        }
    }

    private companion object {
        const val TAG = "BranchViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MAX_UNDO_STEPS = 40
    }
}
