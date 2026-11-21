package com.example.ui.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.model.NodeStatus
import com.example.data.model.ProgressStats
import com.example.domain.layout.LayoutNode
import com.example.ui.theme.LocalBranchColors
import com.example.ui.theme.StatusDoColor
import com.example.ui.theme.StatusDoGlowColor
import com.example.ui.theme.StatusDoneColor
import com.example.ui.theme.StatusPlanColor
import com.example.ui.theme.parseNodeHexColor
import com.example.ui.theme.stateGradientForIndex
import kotlin.math.roundToInt

/**
 * Drilled-down Independent Mind Map Node Card:
 * - Strict Leaf-Node Driven Execution:
 *   * Leaf nodes show interactive 3-state ring (Plan -> Do [ambient glow] -> Done [soft dim + Result Log]).
 *   * Parent nodes with active children show non-interactive aggregated progress ring & bottom rollup bar (foolproof lock!).
 * - Exclusive State Gradient palettes inherited across descendants + 3D flip animation on state switch.
 * - Unified smooth collapse/expand entrance & exit transitions across all depths.
 */
@Composable
fun BranchNodeCard(
    layoutNode: LayoutNode,
    isSelected: Boolean,
    isEditing: Boolean,
    isExecutionMode: Boolean,
    isReparentHighlight: Boolean,
    isExiting: Boolean = false,
    flipVersion: Int,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onCommitEdit: (String) -> Unit,
    onCommitResultLog: (String) -> Unit,
    onCycleStatus: () -> Unit,
    onCycleVariant: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (worldPointerX: Float, worldPointerY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val node = layoutNode.node
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val branchColors = LocalBranchColors.current
    val stateGradient = remember(layoutNode.stateGradientIndex) {
        layoutNode.stateGradientIndex?.let { stateGradientForIndex(it) }
    }
    val accentColor = remember(node.color, stateGradient) {
        stateGradient?.accent ?: parseNodeHexColor(node.color)
    }

    // Unified smooth collapse/expand entrance & exit progress
    val presence = remember(node.id) { Animatable(if (layoutNode.depth == 0) 1f else 0f) }
    LaunchedEffect(isExiting) {
        if (isExiting) {
            presence.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
        } else {
            presence.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    // Smooth Dynamic Push position animation
    val animatedX by animateFloatAsState(
        targetValue = layoutNode.x,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "node_x_${node.id}",
    )
    val animatedY by animateFloatAsState(
        targetValue = layoutNode.y,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "node_y_${node.id}",
    )

    // State Variant 3D Flip Animation
    val flipRotationX = remember { Animatable(0f) }
    LaunchedEffect(flipVersion) {
        if (flipVersion > 0) {
            flipRotationX.snapTo(0f)
            flipRotationX.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 380),
            )
            flipRotationX.snapTo(0f)
        }
    }

    var isDragging by remember(node.id) { mutableStateOf(false) }
    var dragOffsetPx by remember(node.id) { mutableStateOf(Offset.Zero) }
    var editingResultLog by remember(node.id) { mutableStateOf(false) }

    val latestLayout by rememberUpdatedState(layoutNode)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragMove by rememberUpdatedState(onDragMove)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

    val isBranchRoot = layoutNode.depth == 0
    val isLeaf = layoutNode.isInteractiveLeaf
    val isStrikethrough = node.isStrikethrough

    // Active Frontier visual states in Execution Mode
    val isActiveDoLeaf = isExecutionMode && !isStrikethrough && isLeaf && node.status == NodeStatus.DO
    val isDoneLeaf = isExecutionMode && !isStrikethrough && isLeaf && node.status == NodeStatus.DONE
    val isAutoCompletedParent = isExecutionMode && !isStrikethrough && !isLeaf && layoutNode.progress.isCompleted

    val p = presence.value
    val displayWorldX = layoutNode.parentAnchorX + (animatedX - layoutNode.parentAnchorX) * p
    val displayWorldY = layoutNode.parentAnchorY + (animatedY - layoutNode.parentAnchorY) * p

    val cardShape = RoundedCornerShape(if (isBranchRoot) 20.dp else 14.dp)

    val contentAlpha = when {
        isStrikethrough -> 0.56f
        isDoneLeaf || isAutoCompletedParent -> 0.72f
        else -> 1f
    }

    val borderColor = when {
        isReparentHighlight -> branchColors.dropGlow
        isSelected -> accentColor
        isActiveDoLeaf -> StatusDoGlowColor.copy(alpha = 0.92f)
        isAutoCompletedParent -> StatusDoneColor.copy(alpha = 0.65f)
        stateGradient != null -> stateGradient.accent.copy(alpha = if (branchColors.isDark) 0.52f else 0.42f)
        isBranchRoot -> accentColor.copy(alpha = 0.55f)
        else -> branchColors.nodeBorder
    }

    val borderWidth = when {
        isReparentHighlight -> 2.4.dp
        isSelected -> 2.dp
        isActiveDoLeaf -> 2.dp
        isBranchRoot -> 1.5.dp
        else -> 1.dp
    }

    val backgroundBrush: Brush = remember(stateGradient, branchColors, isStrikethrough, isActiveDoLeaf) {
        when {
            isStrikethrough -> SolidColor(branchColors.nodeSurface.copy(alpha = 0.75f))
            stateGradient != null -> Brush.horizontalGradient(
                colors = if (branchColors.isDark) {
                    listOf(stateGradient.darkStart, stateGradient.darkEnd)
                } else {
                    listOf(stateGradient.lightStart, stateGradient.lightEnd)
                }
            )
            isActiveDoLeaf -> Brush.horizontalGradient(
                colors = if (branchColors.isDark) {
                    listOf(Color(0xFF1F2235), Color(0xFF2B261C))
                } else {
                    listOf(Color(0xFFFFFFFF), Color(0xFFFFFBEB))
                }
            )
            else -> SolidColor(branchColors.nodeSurface)
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = layoutNode.progress.ratio.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress_${node.id}",
    )

    val pxPerDp = density.density
    val baseOffsetX = (displayWorldX * pxPerDp).roundToInt()
    val baseOffsetY = (displayWorldY * pxPerDp).roundToInt()

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = baseOffsetX + dragOffsetPx.x.roundToInt(),
                    y = baseOffsetY + dragOffsetPx.y.roundToInt(),
                )
            }
            .zIndex(if (isDragging) 30f else if (isSelected) 10f else if (isActiveDoLeaf) 5f else 2f)
            .size(width = layoutNode.width.dp, height = layoutNode.height.dp)
            .graphicsLayer {
                alpha = (p * contentAlpha).coerceIn(0f, 1f)
                val nodeScale = (0.78f + 0.22f * p) * if (isDragging) 1.04f else 1f
                scaleX = nodeScale
                scaleY = nodeScale
                rotationX = flipRotationX.value
                cameraDistance = 14f * density.density
            }
            .shadow(
                elevation = when {
                    isDragging -> 12.dp
                    isActiveDoLeaf -> 12.dp
                    isSelected -> 6.dp
                    isBranchRoot -> 4.dp
                    else -> 1.5.dp
                },
                shape = cardShape,
                ambientColor = if (isActiveDoLeaf) StatusDoGlowColor else accentColor.copy(alpha = 0.25f),
                spotColor = if (isActiveDoLeaf) StatusDoGlowColor else accentColor.copy(alpha = 0.25f),
            )
            .clip(cardShape)
            .background(backgroundBrush)
            .border(borderWidth, borderColor, cardShape)
            .pointerInput(node.id, isEditing, isExiting) {
                if (!isEditing && !isExiting) {
                    detectTapGestures(
                        onTap = { latestOnSingleTap() },
                        onDoubleTap = { latestOnDoubleTap() },
                    )
                }
            }
            .pointerInput(node.id, isBranchRoot, isEditing, isExiting) {
                if (!isBranchRoot && !isEditing && !isExiting) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isDragging = true
                            dragOffsetPx = Offset.Zero
                            latestOnDragStart()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx += dragAmount
                            val curr = latestLayout
                            val worldCenterX = curr.centerX + (dragOffsetPx.x / pxPerDp)
                            val worldCenterY = curr.centerY + (dragOffsetPx.y / pxPerDp)
                            latestOnDragMove(worldCenterX, worldCenterY)
                        },
                        onDragEnd = {
                            isDragging = false
                            dragOffsetPx = Offset.Zero
                            latestOnDragEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffsetPx = Offset.Zero
                            latestOnDragCancel()
                        },
                    )
                }
            }
            .testTag("branch_node_${node.id}"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Smoothly fading Exec Status Ring (strictly interactive ONLY on leaf nodes!)
                AnimatedVisibility(
                    visible = isExecutionMode && !isStrikethrough,
                    enter = fadeIn(tween(240)) + expandHorizontally(tween(240)),
                    exit = fadeOut(tween(180)) + shrinkHorizontally(tween(180)),
                ) {
                    if (isLeaf) {
                        LeafStatusRingButton(
                            status = node.status,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCycleStatus()
                            },
                            nodeId = node.id,
                        )
                    } else {
                        // Foolproof Lock: Non-interactive aggregated progress ring for parents!
                        ParentRollupRingIndicator(
                            progress = layoutNode.progress,
                            hasActiveDo = layoutNode.hasActiveDoDescendant,
                            accentColor = accentColor,
                            nodeId = node.id,
                        )
                    }
                }

                // Accent dot in Draft mode or when Strikethrough
                if (!isExecutionMode || isStrikethrough) {
                    Box(
                        modifier = Modifier
                            .size(if (isBranchRoot) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isStrikethrough) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                } else {
                                    accentColor
                                }
                            ),
                    )
                }

                // Title or Inline Editor
                Box(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        InlineNodeTitleField(
                            initialText = node.title,
                            isRoot = isBranchRoot,
                            onCommit = onCommitEdit,
                        )
                    } else {
                        Text(
                            text = node.title,
                            style = if (isBranchRoot) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleSmall
                            },
                            fontWeight = if (isBranchRoot || isActiveDoLeaf) FontWeight.Bold else FontWeight.Medium,
                            color = if (isStrikethrough) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (isStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Multi-State Flip Tag
                val activeVar = node.activeVariant()
                if (activeVar != null) {
                    VariantFlipTag(
                        label = activeVar.label,
                        accentColor = accentColor,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCycleVariant()
                        },
                        nodeId = node.id,
                    )
                }
            }

            // Exec Mode: Inline single-line Result Log directly beneath Done leaf nodes
            if (isDoneLeaf) {
                ResultLogStrip(
                    nodeId = node.id,
                    resultLog = node.resultLog,
                    isEditing = editingResultLog,
                    onStartEdit = { editingResultLog = true },
                    onCommit = { updatedLog ->
                        editingResultLog = false
                        onCommitResultLog(updatedLog)
                    },
                )
            }

            // Bottom fine Progress Rollup Bar on parent nodes with active children in Execution Mode
            if (isExecutionMode && !isLeaf && !isStrikethrough && layoutNode.progress.totalCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .background(branchColors.nodeBorder.copy(alpha = 0.55f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                if (layoutNode.progress.isCompleted) StatusDoneColor else accentColor
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultLogStrip(
    nodeId: String,
    resultLog: String,
    isEditing: Boolean,
    onStartEdit: () -> Unit,
    onCommit: (String) -> Unit,
) {
    val branchColors = LocalBranchColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(branchColors.nodeSurface.copy(alpha = 0.55f))
            .clickable(enabled = !isEditing, onClick = onStartEdit)
            .padding(horizontal = 12.dp)
            .testTag("result_log_strip_$nodeId"),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (isEditing) {
            val focusRequester = remember { FocusRequester() }
            var tfv by remember(resultLog) {
                mutableStateOf(
                    TextFieldValue(
                        text = resultLog,
                        selection = TextRange(resultLog.length),
                    )
                )
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = tfv,
                onValueChange = { tfv = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(StatusDoneColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit(tfv.text.trim()) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("result_log_input_$nodeId"),
            )
        } else {
            Text(
                text = resultLog.ifBlank { stringResource(R.string.result_log_add_hint) },
                style = MaterialTheme.typography.labelSmall,
                color = if (resultLog.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                } else {
                    StatusDoneColor.copy(alpha = 0.9f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Interactive 3-state ring strictly for active leaf nodes: Plan -> Do -> Done.
 */
@Composable
fun LeafStatusRingButton(
    status: NodeStatus,
    onClick: () -> Unit,
    nodeId: String,
    modifier: Modifier = Modifier,
) {
    val ringDesc = stringResource(R.string.cd_status_ring)
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = ringDesc }
            .testTag("status_ring_$nodeId"),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            NodeStatus.PLAN -> {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(2.dp, StatusPlanColor, CircleShape),
                )
            }
            NodeStatus.DO -> {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(2.2.dp, StatusDoColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(StatusDoGlowColor),
                    )
                }
            }
            NodeStatus.DONE -> {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(StatusDoneColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/**
 * Non-interactive Aggregated Rollup Ring for parent nodes with active children.
 * Enforces strict bottom-up foolproof lock: parents cannot be manually toggled Done!
 */
@Composable
private fun ParentRollupRingIndicator(
    progress: ProgressStats,
    hasActiveDo: Boolean,
    accentColor: Color,
    nodeId: String,
) {
    val rollupDesc = stringResource(R.string.cd_rollup_ring)
    val animatedSweep by animateFloatAsState(
        targetValue = (progress.ratio.coerceIn(0f, 1f)) * 360f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rollup_arc_$nodeId",
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = rollupDesc }
            .testTag("parent_rollup_ring_$nodeId"),
        contentAlignment = Alignment.Center,
    ) {
        if (progress.isCompleted) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(StatusDoneColor.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        } else {
            Canvas(modifier = Modifier.size(20.dp)) {
                val strokeW = 2.4.dp.toPx()
                val arcSize = Size(size.width - strokeW, size.height - strokeW)
                val topLeft = Offset(strokeW / 2f, strokeW / 2f)
                drawArc(
                    color = StatusPlanColor.copy(alpha = 0.35f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW),
                )
                if (animatedSweep > 0f) {
                    drawArc(
                        color = if (hasActiveDo) StatusDoColor else accentColor,
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

@Composable
fun VariantFlipTag(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    nodeId: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
        modifier = modifier.testTag("variant_tag_$nodeId"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Autorenew,
                contentDescription = stringResource(R.string.variant_switch_desc),
                tint = accentColor,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InlineNodeTitleField(
    initialText: String,
    isRoot: Boolean,
    onCommit: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember(initialText) {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length),
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
        textStyle = (if (isRoot) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall).copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(textFieldValue.text) }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag("inline_node_title_input"),
    )
}
