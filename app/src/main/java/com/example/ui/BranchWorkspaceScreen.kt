package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.BranchRepository
import com.example.data.model.TreeOperations
import com.example.data.model.WorkMode
import com.example.ui.canvas.BranchCanvas
import com.example.ui.canvas.TrunkOverviewCanvas
import com.example.ui.components.NodeStatesSheet
import com.example.ui.components.TopControlBar
import com.example.ui.theme.LocalBranchColors
import com.example.ui.theme.StatusDoGlowColor
import com.example.ui.viewmodel.BoardUiState
import com.example.ui.viewmodel.BranchViewModel

@Composable
fun BranchWorkspaceScreen(
    currentUserId: String,
    boardId: String,
    onBackToHub: () -> Unit,
) {
    val branchColors = LocalBranchColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: BranchViewModel = viewModel(
        key = "${currentUserId}_$boardId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = BranchRepository.createConfiguredFirestore(databaseId)
                BranchViewModel(
                    repository = BranchRepository(db),
                    currentUserId = currentUserId,
                    boardId = boardId,
                )
            }
        },
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Back priority: Zen Mode -> Exit Drilled Branch to Trunk -> Project Hub
    BackHandler {
        when {
            uiState.isZenMode -> viewModel.exitZenMode()
            uiState.activeBranchId != null -> viewModel.exitToTrunk()
            else -> onBackToHub()
        }
    }

    LaunchedEffect(uiState.transientError) {
        val err = uiState.transientError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.dismissTransientError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(branchColors.canvasBackground),
    ) {
        when (val boardState = uiState.boardState) {
            BoardUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            is BoardUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = boardState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBackToHub) {
                            Text(stringResource(R.string.cd_back_to_hub))
                        }
                        Button(onClick = { viewModel.retryLoad() }) {
                            Text(stringResource(R.string.error_retry))
                        }
                    }
                }
            }

            is BoardUiState.Ready -> {
                val board = boardState.board
                val isExecutionMode = board.activeMode == WorkMode.EXECUTION
                val activeBranchId = uiState.activeBranchId
                val drilledParentPhase = remember(board.root, activeBranchId) {
                    activeBranchId?.let { TreeOperations.findParentPhaseForBranch(board.root, it) }
                }
                val selectedNode = remember(board.root, uiState.selectedNodeId) {
                    uiState.selectedNodeId?.let { TreeOperations.findNode(board.root, it) }
                }
                val isSelectedPhase = selectedNode != null && selectedNode.parentId == board.root.id
                val canDeleteSelected = selectedNode != null &&
                    selectedNode.id != board.root.id &&
                    selectedNode.id != activeBranchId
                val canManageStatesForSelected = selectedNode != null && !isSelectedPhase

                val defaultPhaseTitle = stringResource(R.string.trunk_new_phase_default)
                val defaultBranchTitle = stringResource(R.string.trunk_new_branch_default)
                val defaultNodeTitle = stringResource(R.string.node_new_default)

                // Two-Layer Animated Zoom-in / Zoom-out Transition:
                // null = Layer 1 Horizontal Trunk Overview
                // non-null = Layer 2 Drilled-down Independent Mind Map
                AnimatedContent(
                    targetState = activeBranchId,
                    transitionSpec = {
                        if (targetState != null) {
                            // Zoom-in drill-down: Trunk scales up & fades out; Branch scales up from 0.86f -> 1f
                            (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.86f, animationSpec = tween(280, easing = FastOutSlowInEasing))) togetherWith
                                (fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 1.15f, animationSpec = tween(260, easing = FastOutSlowInEasing)))
                        } else {
                            // Zoom-out return to Trunk: Branch scales down to 0.86f; Trunk settles from 1.14f -> 1f
                            (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 1.14f, animationSpec = tween(280, easing = FastOutSlowInEasing))) togetherWith
                                (fadeOut(tween(210, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 0.86f, animationSpec = tween(250, easing = FastOutSlowInEasing)))
                        }
                    },
                    label = "trunk_branch_drill_transition",
                ) { currentBranchId ->
                    val branchRoot = currentBranchId?.let { TreeOperations.findNode(board.root, it) }
                    if (branchRoot == null) {
                        TrunkOverviewCanvas(
                            board = board,
                            selectedNodeId = uiState.selectedNodeId,
                            editingNodeId = uiState.editingNodeId,
                            onSelectNode = { viewModel.selectNode(it) },
                            onStartEditNode = { viewModel.startEditingNode(it) },
                            onCommitEditNode = { id, title -> viewModel.commitNodeTitle(id, title) },
                            onDrillIntoBranch = { branchId -> viewModel.drillIntoBranch(branchId) },
                            onAddPhase = { viewModel.addPhase(defaultPhaseTitle) },
                            onAddBranchToPhase = { phaseId ->
                                viewModel.addBranchToPhase(phaseId, defaultBranchTitle)
                            },
                        )
                    } else {
                        BranchCanvas(
                            branchRoot = branchRoot,
                            activeMode = board.activeMode,
                            selectedNodeId = uiState.selectedNodeId,
                            editingNodeId = uiState.editingNodeId,
                            flippingNodeIds = uiState.flippingNodeIds,
                            onSelectNode = { viewModel.selectNode(it) },
                            onTapBlankExitToTrunk = { viewModel.exitToTrunk() },
                            onStartEditNode = { viewModel.startEditingNode(it) },
                            onCommitEditNode = { id, title -> viewModel.commitNodeTitle(id, title) },
                            onCommitResultLog = { id, log -> viewModel.updateResultLog(id, log) },
                            onCycleStatus = { viewModel.cycleNodeStatus(it) },
                            onToggleCollapse = { viewModel.toggleCollapse(it) },
                            onQuickAddChild = { parentId ->
                                viewModel.addChildNode(parentId = parentId, defaultTitle = defaultNodeTitle)
                            },
                            onCycleVariant = { viewModel.cycleStateVariant(it) },
                            onApplyDragDrop = { draggedId, target ->
                                viewModel.applyDragDrop(draggedId, target)
                            },
                        )
                    }
                }

                // Ambient Cue Edge Glow Frame (soft luminous perimeter aura when Execution Mode is ON)
                AmbientEdgeGlowOverlay(
                    isExecutionMode = isExecutionMode,
                    isDark = branchColors.isDark,
                )

                // Floating Minimalist Top Chrome (hidden in Zen Full-Screen Mode)
                AnimatedVisibility(
                    visible = !uiState.isZenMode,
                    enter = fadeIn(tween(200)) + slideInVertically { -it / 3 },
                    exit = fadeOut(tween(180)) + slideOutVertically { -it / 3 },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    TopControlBar(
                        projectTitle = board.title,
                        activeMode = board.activeMode,
                        drilledBreadcrumbPhaseTitle = if (activeBranchId != null) {
                            drilledParentPhase?.title ?: board.title
                        } else {
                            null
                        },
                        selectedNode = selectedNode,
                        isSelectedPhase = isSelectedPhase,
                        canDeleteSelected = canDeleteSelected,
                        canManageStatesForSelected = canManageStatesForSelected,
                        showColorPicker = uiState.showColorPicker,
                        onBackToHub = onBackToHub,
                        onExitToTrunk = { viewModel.exitToTrunk() },
                        onToggleExecutionMode = { enabled -> viewModel.setExecutionMode(enabled) },
                        onToggleColorPicker = { viewModel.toggleColorPicker() },
                        onSelectNodeColor = { hex ->
                            selectedNode?.let { viewModel.updateNodeColor(it.id, hex) }
                        },
                        onAddBranchToSelectedPhase = {
                            if (isSelectedPhase && selectedNode != null) {
                                viewModel.addBranchToPhase(selectedNode.id, defaultBranchTitle)
                            }
                        },
                        onDuplicateNode = {
                            selectedNode?.let { viewModel.duplicateNode(it.id) }
                        },
                        onOpenStates = {
                            selectedNode?.let { viewModel.openStatesSheet(it.id) }
                        },
                        onToggleStrikethrough = {
                            selectedNode?.let { viewModel.toggleStrikethrough(it.id) }
                        },
                        onDeleteNode = {
                            selectedNode?.let { viewModel.deleteNode(it.id) }
                        },
                    )
                }

                // Bottom-Left Minimalist Controls: Semi-transparent Zen Toggle + Fixed-Position Undo
                // (Bottom-Right stays 100% free of floating action buttons!)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Pure-icon Zen Full-Screen Toggle
                    Surface(
                        onClick = { viewModel.toggleZenMode() },
                        shape = CircleShape,
                        color = branchColors.nodeSurface.copy(alpha = if (uiState.isZenMode) 0.44f else 0.86f),
                        border = BorderStroke(
                            1.dp,
                            branchColors.nodeBorder.copy(alpha = if (uiState.isZenMode) 0.42f else 0.9f),
                        ),
                        shadowElevation = if (uiState.isZenMode) 0.dp else 2.dp,
                        modifier = Modifier
                            .size(if (uiState.isZenMode) 38.dp else 44.dp)
                            .alpha(if (uiState.isZenMode) 0.62f else 1f)
                            .testTag("zen_mode_toggle_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isZenMode) {
                                    Icons.Rounded.FullscreenExit
                                } else {
                                    Icons.Rounded.Fullscreen
                                },
                                contentDescription = stringResource(
                                    if (uiState.isZenMode) R.string.cd_zen_exit else R.string.cd_zen_enter
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (uiState.isZenMode) 18.dp else 20.dp),
                            )
                        }
                    }

                    // Fixed-position Undo button whenever history exists (hidden in Zen Mode)
                    AnimatedVisibility(
                        visible = uiState.canUndo && !uiState.isZenMode,
                        enter = fadeIn() + scaleIn(initialScale = 0.88f),
                        exit = fadeOut() + scaleOut(targetScale = 0.88f),
                    ) {
                        Surface(
                            onClick = { viewModel.performUndo() },
                            shape = RoundedCornerShape(22.dp),
                            color = branchColors.nodeSurface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, branchColors.nodeBorder),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("undo_action_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = stringResource(R.string.cd_undo),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.action_undo),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Node States ("狀態") Management Sheet
                uiState.statesSheetNodeId?.let { targetId ->
                    val sheetNode = TreeOperations.findNode(board.root, targetId)
                    if (sheetNode != null) {
                        NodeStatesSheet(
                            node = sheetNode,
                            onDismiss = { viewModel.dismissStatesSheet() },
                            onAddState = { viewModel.addStateAutomatically(targetId) },
                            onSelectState = { varId ->
                                viewModel.switchToState(targetId, varId)
                            },
                            onRenameState = { varId, newLabel ->
                                viewModel.renameState(targetId, varId, newLabel)
                            },
                            onSetDefaultState = { varId ->
                                viewModel.setDefaultState(targetId, varId)
                            },
                            onDeleteState = { varId ->
                                viewModel.deleteState(targetId, varId)
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
}

/**
 * Ambient Cue Perimeter Glow:
 * When Execution Mode is toggled ON, casts a delicate, warm luminous halo along the canvas edges
 * without blocking any touch input. Smoothly fades to 0 in Draft matte mode.
 */
@Composable
private fun AmbientEdgeGlowOverlay(
    isExecutionMode: Boolean,
    isDark: Boolean,
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isExecutionMode) (if (isDark) 0.22f else 0.16f) else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "ambient_edge_glow_alpha",
    )
    if (glowAlpha <= 0.005f) return

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ambient_edge_glow_overlay"),
    ) {
        val edgeDepth = 42.dp.toPx()
        val glowColor = StatusDoGlowColor.copy(alpha = glowAlpha)
        val transparent = Color.Transparent

        // Top ambient edge
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(glowColor, transparent),
                startY = 0f,
                endY = edgeDepth,
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, edgeDepth),
        )
        // Bottom ambient edge
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(transparent, glowColor),
                startY = size.height - edgeDepth,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - edgeDepth),
            size = Size(size.width, edgeDepth),
        )
        // Left ambient edge
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(glowColor, transparent),
                startX = 0f,
                endX = edgeDepth,
            ),
            topLeft = Offset.Zero,
            size = Size(edgeDepth, size.height),
        )
        // Right ambient edge
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(transparent, glowColor),
                startX = size.width - edgeDepth,
                endX = size.width,
            ),
            topLeft = Offset(size.width - edgeDepth, 0f),
            size = Size(edgeDepth, size.height),
        )
    }
}
