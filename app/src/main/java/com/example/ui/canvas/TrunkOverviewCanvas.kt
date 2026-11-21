package com.example.ui.canvas

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BranchBoard
import com.example.data.model.WorkMode
import com.example.domain.layout.BranchLayoutEngine
import com.example.domain.layout.TrunkBranchPillLayout
import com.example.domain.layout.TrunkPhaseLayout
import com.example.ui.theme.LocalBranchColors
import com.example.ui.theme.StatusDoGlowColor
import com.example.ui.theme.StatusDoneColor
import com.example.ui.theme.parseNodeHexColor
import com.example.ui.theme.stateGradientForIndex
import kotlin.math.roundToInt

/**
 * Layer 1: Horizontal Trunk Overview Canvas
 * - Clear, weighty horizontal spine connecting project Phases (Milestone Capsules with bottom total progress bar).
 * - Above & below each Phase hang collapsed Branch Start Pills (title + internal active node count).
 * - Completed Branches illuminate into a micro-lit solid completed pill for instant visual achievement feedback.
 * - Tapping any Branch pill drills smoothly into its independent Mind Map workspace.
 * - Pure manual Pinch-to-Zoom & Pan (zero auto-collapse/expand, zero bottom-right FABs).
 */
@Composable
fun TrunkOverviewCanvas(
    board: BranchBoard,
    selectedNodeId: String?,
    editingNodeId: String?,
    onSelectNode: (String?) -> Unit,
    onStartEditNode: (String) -> Unit,
    onCommitEditNode: (String, String) -> Unit,
    onDrillIntoBranch: (String) -> Unit,
    onAddPhase: () -> Unit,
    onAddBranchToPhase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExecutionMode = board.activeMode == WorkMode.EXECUTION
    val branchColors = LocalBranchColors.current
    val density = LocalDensity.current
    val pxPerDp = density.density

    val overviewLayout = remember(board.root) {
        BranchLayoutEngine.computeTrunkOverviewLayout(board.root)
    }

    var scale by remember(board.id) { mutableFloatStateOf(0.95f) }
    var panX by remember(board.id) { mutableFloatStateOf(0f) }
    var panY by remember(board.id) { mutableFloatStateOf(0f) }
    var viewportInitialized by remember(board.id) { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(branchColors.canvasBackground)
            .testTag("trunk_overview_canvas"),
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        LaunchedEffect(board.id, viewportWidthPx, viewportHeightPx) {
            if (!viewportInitialized) {
                scale = 0.94f
                panX = viewportWidthPx * 0.08f
                panY = viewportHeightPx * 0.50f
                viewportInitialized = true
            }
        }

        // Layer A: Architectural Dot Grid + Background Tap Deselect + Pure Manual Zoom/Pan
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onSelectNode(null) })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.45f, 2.2f)
                        val scaleFactor = newScale / oldScale
                        panX = centroid.x - (centroid.x - panX) * scaleFactor + pan.x
                        panY = centroid.y - (centroid.y - panY) * scaleFactor + pan.y
                        scale = newScale
                    }
                },
        ) {
            val spacingPx = 28f * pxPerDp * scale
            if (spacingPx >= 10f) {
                val startX = ((panX % spacingPx) + spacingPx) % spacingPx
                val startY = ((panY % spacingPx) + spacingPx) % spacingPx
                val dotRadius = (1.15f * pxPerDp * scale).coerceIn(0.75f, 2.2f)
                var x = startX
                while (x < size.width) {
                    var y = startY
                    while (y < size.height) {
                        drawCircle(
                            color = branchColors.gridDot,
                            radius = dotRadius,
                            center = Offset(x, y),
                        )
                        y += spacingPx
                    }
                    x += spacingPx
                }
            }
        }

        // Layer B: World-Space Horizontal Trunk Spine, Vertical Branch Stems, Milestone Phases & Branch Pills
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = panX
                    translationY = panY
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            // B1: Horizontal Trunk Spine & Vertical Curved Stems to Branch Pills
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spineY = overviewLayout.spineY * pxPerDp
                val startX = (overviewLayout.spineStartX - 36f) * pxPerDp
                val endX = overviewLayout.spineEndX * pxPerDp

                if (overviewLayout.phases.isNotEmpty()) {
                    // Subtle ambient under-glow along the Trunk spine in Execution Mode
                    if (isExecutionMode) {
                        drawLine(
                            color = StatusDoGlowColor.copy(alpha = 0.20f),
                            start = Offset(startX, spineY),
                            end = Offset(endX, spineY),
                            strokeWidth = 14f * pxPerDp,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Primary weighty horizontal Trunk Spine
                    drawLine(
                        color = branchColors.trunkSpine,
                        start = Offset(startX, spineY),
                        end = Offset(endX, spineY),
                        strokeWidth = 5f * pxPerDp,
                        cap = StrokeCap.Round,
                    )

                    // Dashed tail connecting last phase to "+ Add Phase" pill
                    val lastPhaseRight = (overviewLayout.phases.last().rightX) * pxPerDp
                    val addPhaseLeft = overviewLayout.addPhaseX * pxPerDp
                    drawLine(
                        color = branchColors.edgeDefault,
                        start = Offset(lastPhaseRight, spineY),
                        end = Offset(addPhaseLeft, spineY),
                        strokeWidth = 2.4f * pxPerDp,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * pxPerDp, 7f * pxPerDp)),
                    )
                }

                // Vertical stems connecting Phases to above/below collapsed Branch pills
                overviewLayout.phases.forEach { phaseLayout ->
                    val phaseAccent = parseNodeHexColor(phaseLayout.phase.color)
                    val allPills = phaseLayout.aboveBranches + phaseLayout.belowBranches
                    allPills.forEach { pill ->
                        val sx = pill.stemStartX * pxPerDp
                        val sy = pill.stemStartY * pxPerDp
                        val ex = pill.stemEndX * pxPerDp
                        val ey = pill.stemEndY * pxPerDp
                        val midY = (sy + ey) * 0.5f

                        val stemColor = when {
                            pill.branch.isStrikethrough -> branchColors.edgeDefault.copy(alpha = 0.35f)
                            pill.progress.isCompleted -> StatusDoneColor.copy(alpha = 0.85f)
                            isExecutionMode && pill.hasActiveDo -> StatusDoGlowColor.copy(alpha = 0.88f)
                            else -> phaseAccent.copy(alpha = 0.45f)
                        }

                        val path = Path().apply {
                            moveTo(sx, sy)
                            cubicTo(sx, midY, ex, midY, ex, ey)
                        }
                        drawPath(
                            path = path,
                            color = stemColor,
                            style = Stroke(
                                width = (if (pill.progress.isCompleted) 2.6f else 2f) * pxPerDp,
                                cap = StrokeCap.Round,
                                pathEffect = if (pill.branch.isStrikethrough) {
                                    PathEffect.dashPathEffect(floatArrayOf(5f * pxPerDp, 5f * pxPerDp))
                                } else {
                                    null
                                },
                            ),
                        )

                        // Anchor dot where stem meets the Phase milestone edge
                        drawCircle(
                            color = stemColor,
                            radius = 3.2f * pxPerDp,
                            center = Offset(sx, sy),
                        )
                    }
                }
            }

            // B2: Milestone Phase Capsules & Collapsed Branch Start Pills
            overviewLayout.phases.forEach { phaseLayout ->
                key(phaseLayout.phase.id) {
                    TrunkPhaseMilestoneCard(
                        phaseLayout = phaseLayout,
                        isSelected = phaseLayout.phase.id == selectedNodeId,
                        isEditing = phaseLayout.phase.id == editingNodeId,
                        isExecutionMode = isExecutionMode,
                        onTap = { onSelectNode(phaseLayout.phase.id) },
                        onDoubleTap = { onStartEditNode(phaseLayout.phase.id) },
                        onCommitEdit = { newTitle -> onCommitEditNode(phaseLayout.phase.id, newTitle) },
                        onQuickAddBranch = { onAddBranchToPhase(phaseLayout.phase.id) },
                    )
                }

                (phaseLayout.aboveBranches + phaseLayout.belowBranches).forEach { pillLayout ->
                    key(pillLayout.branch.id) {
                        TrunkBranchStartPill(
                            pillLayout = pillLayout,
                            isSelected = pillLayout.branch.id == selectedNodeId,
                            isEditing = pillLayout.branch.id == editingNodeId,
                            isExecutionMode = isExecutionMode,
                            onDrillIn = { onDrillIntoBranch(pillLayout.branch.id) },
                            onLongPressSelect = { onSelectNode(pillLayout.branch.id) },
                            onCommitEdit = { newTitle -> onCommitEditNode(pillLayout.branch.id, newTitle) },
                        )
                    }
                }
            }

            // B3: "+ Add Phase" Ghost Milestone Pill at the end of the horizontal Trunk
            Surface(
                onClick = onAddPhase,
                shape = RoundedCornerShape(28.dp),
                color = branchColors.nodeSurface.copy(alpha = 0.72f),
                border = BorderStroke(1.2.dp, branchColors.nodeBorder),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (overviewLayout.addPhaseX * pxPerDp).roundToInt(),
                            y = (overviewLayout.addPhaseY * pxPerDp).roundToInt(),
                        )
                    }
                    .size(
                        width = overviewLayout.addPhaseWidth.dp,
                        height = overviewLayout.addPhaseHeight.dp,
                    )
                    .testTag("trunk_add_phase_button"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.trunk_add_phase),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Larger, weighted Milestone Capsule Card along the Horizontal Trunk Spine.
 * Features built-in fine total progress bar flush along the bottom edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrunkPhaseMilestoneCard(
    phaseLayout: TrunkPhaseLayout,
    isSelected: Boolean,
    isEditing: Boolean,
    isExecutionMode: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onCommitEdit: (String) -> Unit,
    onQuickAddBranch: () -> Unit,
) {
    val phase = phaseLayout.phase
    val branchColors = LocalBranchColors.current
    val density = LocalDensity.current
    val pxPerDp = density.density
    val accentColor = remember(phase.color) { parseNodeHexColor(phase.color) }

    val animatedX by animateFloatAsState(
        targetValue = phaseLayout.x,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "phase_x_${phase.id}",
    )
    val animatedY by animateFloatAsState(
        targetValue = phaseLayout.y,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "phase_y_${phase.id}",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = phaseLayout.progress.ratio.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "phase_progress_${phase.id}",
    )

    val isCompleted = phaseLayout.progress.isCompleted && !phase.isStrikethrough
    val hasActiveGlow = isExecutionMode && phaseLayout.hasActiveDo && !phase.isStrikethrough

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor
            hasActiveGlow -> StatusDoGlowColor.copy(alpha = 0.85f)
            isCompleted -> StatusDoneColor.copy(alpha = 0.72f)
            else -> branchColors.nodeBorder
        },
        animationSpec = tween(220),
        label = "phase_border_${phase.id}",
    )

    val capsuleShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (animatedX * pxPerDp).roundToInt(),
                    y = (animatedY * pxPerDp).roundToInt(),
                )
            }
            .size(width = phaseLayout.width.dp, height = phaseLayout.height.dp)
            .shadow(
                elevation = when {
                    hasActiveGlow -> 12.dp
                    isSelected -> 8.dp
                    else -> 4.dp
                },
                shape = capsuleShape,
                ambientColor = if (hasActiveGlow) StatusDoGlowColor else accentColor.copy(alpha = 0.35f),
                spotColor = if (hasActiveGlow) StatusDoGlowColor else accentColor.copy(alpha = 0.35f),
            )
            .clip(capsuleShape)
            .background(
                if (phase.isStrikethrough) {
                    branchColors.nodeSurface.copy(alpha = 0.6f)
                } else {
                    branchColors.nodeSurface
                }
            )
            .border(
                width = if (isSelected || hasActiveGlow) 2.dp else 1.25.dp,
                color = borderColor,
                shape = capsuleShape,
            )
            .combinedClickable(
                onClick = onTap,
                onDoubleClick = onDoubleTap,
            )
            .testTag("trunk_phase_card_${phase.id}"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top eyebrow + progress percentage + quick add branch icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    phase.isStrikethrough -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    isCompleted -> StatusDoneColor
                                    else -> accentColor
                                }
                            ),
                    )
                    Text(
                        text = stringResource(R.string.trunk_phase_index_fmt, phaseLayout.phaseIndex + 1),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (phaseLayout.progress.totalCount > 0 && !phase.isStrikethrough) {
                        Text(
                            text = "${(phaseLayout.progress.ratio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCompleted) StatusDoneColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        onClick = onQuickAddBranch,
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.12f),
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("phase_add_branch_${phase.id}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.trunk_add_branch),
                                tint = accentColor,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            // Phase Title (or inline editor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (isEditing) {
                    InlineTrunkTitleEditor(
                        initialTitle = phase.title,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onCommit = onCommitEdit,
                    )
                } else {
                    Text(
                        text = phase.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (phase.isStrikethrough) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (phase.isStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Built-in fine total progress bar flush at the bottom of the Milestone Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(branchColors.nodeBorder.copy(alpha = 0.55f)),
            ) {
                if (phaseLayout.progress.totalCount > 0 && !phase.isStrikethrough) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(if (isCompleted) StatusDoneColor else accentColor),
                    )
                }
            }
        }
    }
}

/**
 * Collapsed Branch Start Pill hanging above or below a Phase on the Trunk.
 * - Shows ONLY Branch name + compact internal node count badge.
 * - Completed micro-feedback: turns micro-lit / solid completed appearance when 100% of internal leaves are DONE!
 * - Tap -> Zoom-in Drill-down into independent Mind Map.
 * - Long-press -> Select on Trunk for quick toolbar actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrunkBranchStartPill(
    pillLayout: TrunkBranchPillLayout,
    isSelected: Boolean,
    isEditing: Boolean,
    isExecutionMode: Boolean,
    onDrillIn: () -> Unit,
    onLongPressSelect: () -> Unit,
    onCommitEdit: (String) -> Unit,
) {
    val branch = pillLayout.branch
    val branchColors = LocalBranchColors.current
    val haptic = LocalHapticFeedback.current
    val pxPerDp = LocalDensity.current.density

    val stateGradient = remember(pillLayout.stateGradientIndex) {
        pillLayout.stateGradientIndex?.let { stateGradientForIndex(it) }
    }
    val accentColor = remember(branch.color, stateGradient) {
        stateGradient?.accent ?: parseNodeHexColor(branch.color)
    }

    val isCompleted = pillLayout.progress.isCompleted && !branch.isStrikethrough
    val hasActiveDoGlow = isExecutionMode && pillLayout.hasActiveDo && !isCompleted && !branch.isStrikethrough

    val animatedX by animateFloatAsState(
        targetValue = pillLayout.x,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pill_x_${branch.id}",
    )
    val animatedY by animateFloatAsState(
        targetValue = pillLayout.y,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pill_y_${branch.id}",
    )

    val pillShape = RoundedCornerShape(22.dp)

    // Completed micro-lit solid fill vs matte resting capsule
    val backgroundBrush = when {
        branch.isStrikethrough -> SolidColor(branchColors.nodeSurface.copy(alpha = 0.55f))
        isCompleted -> Brush.horizontalGradient(
            colors = listOf(
                StatusDoneColor,
                Color(0xFF059669),
            )
        )
        stateGradient != null -> Brush.horizontalGradient(
            colors = if (branchColors.isDark) {
                listOf(stateGradient.darkStart, stateGradient.darkEnd)
            } else {
                listOf(stateGradient.lightStart, stateGradient.lightEnd)
            }
        )
        else -> SolidColor(branchColors.nodeSurface)
    }

    val borderColor = when {
        isSelected -> accentColor
        isCompleted -> Color(0xFF34D399)
        hasActiveDoGlow -> StatusDoGlowColor.copy(alpha = 0.9f)
        else -> branchColors.nodeBorder
    }

    val titleColor = when {
        branch.isStrikethrough -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isCompleted -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (animatedX * pxPerDp).roundToInt(),
                    y = (animatedY * pxPerDp).roundToInt(),
                )
            }
            .size(width = pillLayout.width.dp, height = pillLayout.height.dp)
            .shadow(
                elevation = when {
                    isCompleted -> 9.dp
                    hasActiveDoGlow -> 8.dp
                    isSelected -> 6.dp
                    else -> 2.dp
                },
                shape = pillShape,
                ambientColor = when {
                    isCompleted -> StatusDoneColor
                    hasActiveDoGlow -> StatusDoGlowColor
                    else -> Color.Black.copy(alpha = 0.14f)
                },
                spotColor = when {
                    isCompleted -> StatusDoneColor
                    hasActiveDoGlow -> StatusDoGlowColor
                    else -> Color.Black.copy(alpha = 0.14f)
                },
            )
            .clip(pillShape)
            .background(backgroundBrush)
            .border(
                width = if (isSelected || isCompleted || hasActiveDoGlow) 1.6.dp else 1.dp,
                color = borderColor,
                shape = pillShape,
            )
            .combinedClickable(
                onClick = { if (!isEditing) onDrillIn() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPressSelect()
                },
            )
            .testTag("trunk_branch_pill_${branch.id}"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Left status micro-indicator: solid check when completed, glowing dot when DO, subtle accent dot otherwise
            when {
                isCompleted -> {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                hasActiveDoGlow -> {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(StatusDoGlowColor),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (branch.isStrikethrough) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                } else {
                                    accentColor.copy(alpha = 0.78f)
                                }
                            ),
                    )
                }
            }

            // Branch Title
            Box(modifier = Modifier.weight(1f)) {
                if (isEditing) {
                    InlineTrunkTitleEditor(
                        initialTitle = branch.title,
                        textColor = titleColor,
                        onCommit = onCommitEdit,
                    )
                } else {
                    Text(
                        text = branch.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Medium,
                        color = titleColor,
                        textDecoration = if (branch.isStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Compact internal node count badge (shows done/total in Exec mode, or internal node count in Draft)
            val badgeText = if (isExecutionMode && pillLayout.progress.totalCount > 0) {
                "${pillLayout.progress.doneCount}/${pillLayout.progress.totalCount}"
            } else {
                "${pillLayout.internalNodeCount}"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isCompleted) {
                            Color.White.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        }
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCompleted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun InlineTrunkTitleEditor(
    initialTitle: String,
    textColor: Color,
    onCommit: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember(initialTitle) {
        mutableStateOf(
            TextFieldValue(
                text = initialTitle,
                selection = TextRange(0, initialTitle.length),
            )
        )
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    BasicTextField(
        value = textFieldValue,
        onValueChange = { textFieldValue = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(textFieldValue.text) }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag("trunk_inline_title_editor"),
    )
}
