package com.example.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.model.BranchNode
import com.example.data.model.WorkMode
import com.example.domain.layout.BranchLayoutEngine
import com.example.domain.layout.DragDropTarget
import com.example.domain.layout.DragEvaluation
import com.example.domain.layout.LayoutNode
import com.example.ui.theme.LocalBranchColors
import com.example.ui.theme.parseNodeHexColor
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Layer 2: Drilled-down Independent Branch Mind Map Canvas
 * - Focused 100% on the drilled-down Branch subtree (zero background Trunk noise).
 * - Tapping ANY blank canvas area exits smoothly back to the Trunk Overview!
 * - Auto-Layout with Dynamic Push (zero overlap, zero bottom-right FABs).
 * - Pure manual Pinch-to-Zoom & Pan strictly changes scale/pan ONLY (never triggers auto-collapse!).
 * - Unified smooth collapse/expand animation across all depths + 48dp invisible collapse hitboxes.
 */
@Composable
fun BranchCanvas(
    branchRoot: BranchNode,
    activeMode: WorkMode,
    selectedNodeId: String?,
    editingNodeId: String?,
    flippingNodeIds: Map<String, Int>,
    onSelectNode: (String?) -> Unit,
    onTapBlankExitToTrunk: () -> Unit,
    onStartEditNode: (String) -> Unit,
    onCommitEditNode: (String, String) -> Unit,
    onCommitResultLog: (String, String) -> Unit,
    onCycleStatus: (String) -> Unit,
    onToggleCollapse: (String) -> Unit,
    onQuickAddChild: (String) -> Unit,
    onCycleVariant: (String) -> Unit,
    onApplyDragDrop: (String, DragDropTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExecutionMode = activeMode == WorkMode.EXECUTION
    val branchColors = LocalBranchColors.current
    val density = LocalDensity.current
    val pxPerDp = density.density

    val layoutResult = remember(branchRoot, isExecutionMode) {
        BranchLayoutEngine.computeMindMapLayout(
            branchRoot = branchRoot,
            isExecutionMode = isExecutionMode,
        )
    }

    // Exiting-node tracking for unified smooth collapse animations across all depths
    var previousNodesById by remember(branchRoot.id) { mutableStateOf<Map<String, LayoutNode>>(emptyMap()) }
    var exitingNodes by remember(branchRoot.id) { mutableStateOf<List<LayoutNode>>(emptyList()) }

    LaunchedEffect(layoutResult.nodes) {
        val currentIds = layoutResult.nodes.map { it.node.id }.toSet()
        val disappeared = previousNodesById.values.filter { it.node.id !in currentIds }
        previousNodesById = layoutResult.nodes.associateBy { it.node.id }
        if (disappeared.isNotEmpty()) {
            exitingNodes = disappeared
            delay(220L)
            exitingNodes = emptyList()
        }
    }

    var scale by remember(branchRoot.id) { mutableFloatStateOf(0.95f) }
    var panX by remember(branchRoot.id) { mutableFloatStateOf(0f) }
    var panY by remember(branchRoot.id) { mutableFloatStateOf(0f) }
    var viewportInitialized by remember(branchRoot.id) { mutableStateOf(false) }

    var draggingNodeId by remember(branchRoot.id) { mutableStateOf<String?>(null) }
    var dragEvaluation by remember(branchRoot.id) { mutableStateOf<DragEvaluation?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(branchColors.canvasBackground)
            .testTag("branch_mindmap_canvas"),
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        LaunchedEffect(branchRoot.id, viewportWidthPx, viewportHeightPx) {
            if (!viewportInitialized) {
                val contentW = (layoutResult.boundsMaxX - layoutResult.boundsMinX).coerceAtLeast(360f) * pxPerDp
                val contentH = (layoutResult.boundsMaxY - layoutResult.boundsMinY).coerceAtLeast(280f) * pxPerDp
                val fitScale = min(
                    (viewportWidthPx * 0.88f) / contentW,
                    (viewportHeightPx * 0.72f) / contentH,
                ).coerceIn(0.65f, 1.05f)
                scale = fitScale
                panX = viewportWidthPx * 0.14f
                panY = viewportHeightPx * 0.50f
                viewportInitialized = true
            }
        }

        // Layer A: Architectural Dot Grid + Tap Blank Exits to Trunk + Pure Manual Pinch-to-Zoom
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(editingNodeId) {
                    detectTapGestures(
                        onTap = {
                            if (editingNodeId != null) {
                                onSelectNode(null)
                            } else {
                                onTapBlankExitToTrunk()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Pure manual zoom/pan ONLY — strictly zero auto-collapse/expand!
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.35f, 2.5f)
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

        // Layer B: World-Space Transformed Mind Map
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
            // B1: Swimlanes, Smooth Cubic Bezier Edges, Alignment Guides & Drop Highlights
            Canvas(modifier = Modifier.fillMaxSize()) {
                val laneLeft = (layoutResult.boundsMinX + 180f) * pxPerDp
                val laneWidth = (layoutResult.boundsMaxX - layoutResult.boundsMinX).coerceAtLeast(600f) * pxPerDp
                layoutResult.swimlanes.forEach { lane ->
                    val laneColor = parseNodeHexColor(lane.colorHex)
                    drawRoundRect(
                        color = laneColor.copy(alpha = if (branchColors.isDark) 0.055f else 0.04f),
                        topLeft = Offset(laneLeft, lane.top * pxPerDp),
                        size = Size(laneWidth, (lane.bottom - lane.top) * pxPerDp),
                        cornerRadius = CornerRadius(18f * pxPerDp, 18f * pxPerDp),
                    )
                }

                layoutResult.edges.forEach { edge ->
                    val edgeColor = parseNodeHexColor(edge.colorHex)
                    val sx = edge.startX * pxPerDp
                    val sy = edge.startY * pxPerDp
                    val ex = edge.endX * pxPerDp
                    val ey = edge.endY * pxPerDp
                    val midX = (sx + ex) * 0.5f

                    val strokeColor = if (edge.isStrikethroughEdge) {
                        edgeColor.copy(alpha = 0.28f)
                    } else {
                        edgeColor.copy(alpha = 0.58f)
                    }

                    val path = Path().apply {
                        moveTo(sx, sy)
                        cubicTo(midX, sy, midX, ey, ex, ey)
                    }
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(
                            width = 2.2f * pxPerDp,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (edge.isStrikethroughEdge) {
                                PathEffect.dashPathEffect(floatArrayOf(6f * pxPerDp, 6f * pxPerDp))
                            } else {
                                null
                            },
                        ),
                    )
                }

                // Smart Alignment Guides & Dual Drop Target Visuals
                dragEvaluation?.let { eval ->
                    eval.guides.forEach { guide ->
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f * pxPerDp, 6f * pxPerDp))
                        if (guide.isVertical) {
                            drawLine(
                                color = branchColors.guideLine.copy(alpha = 0.72f),
                                start = Offset(guide.coordinate * pxPerDp, guide.start * pxPerDp),
                                end = Offset(guide.coordinate * pxPerDp, guide.end * pxPerDp),
                                strokeWidth = 1.4f * pxPerDp,
                                pathEffect = dash,
                            )
                        } else {
                            drawLine(
                                color = branchColors.guideLine.copy(alpha = 0.72f),
                                start = Offset(guide.start * pxPerDp, guide.coordinate * pxPerDp),
                                end = Offset(guide.end * pxPerDp, guide.coordinate * pxPerDp),
                                strokeWidth = 1.4f * pxPerDp,
                                pathEffect = dash,
                            )
                        }
                    }

                    when (val target = eval.dropTarget) {
                        is DragDropTarget.Reparent -> {
                            val pad = 5f * pxPerDp
                            drawRoundRect(
                                color = branchColors.dropGlow.copy(alpha = 0.16f),
                                topLeft = Offset(target.left * pxPerDp - pad, target.top * pxPerDp - pad),
                                size = Size(
                                    (target.right - target.left) * pxPerDp + pad * 2f,
                                    (target.bottom - target.top) * pxPerDp + pad * 2f,
                                ),
                                cornerRadius = CornerRadius(16f * pxPerDp, 16f * pxPerDp),
                            )
                        }
                        is DragDropTarget.SiblingGap -> {
                            val yPx = target.lineY * pxPerDp
                            val x1 = target.lineStartX * pxPerDp
                            val x2 = target.lineEndX * pxPerDp
                            drawLine(
                                color = branchColors.dropGlow,
                                start = Offset(x1, yPx),
                                end = Offset(x2, yPx),
                                strokeWidth = 3.2f * pxPerDp,
                                cap = StrokeCap.Round,
                            )
                            drawCircle(
                                color = branchColors.dropGlow,
                                radius = 4.5f * pxPerDp,
                                center = Offset(x1, yPx),
                            )
                        }
                        null -> Unit
                    }
                }
            }

            // B2: Exiting Nodes (Unified Smooth Collapse Transition)
            exitingNodes.forEach { oldLayout ->
                key("exit_${oldLayout.node.id}") {
                    BranchNodeCard(
                        layoutNode = oldLayout,
                        isSelected = false,
                        isEditing = false,
                        isExecutionMode = isExecutionMode,
                        isReparentHighlight = false,
                        isExiting = true,
                        flipVersion = 0,
                        onSingleTap = {},
                        onDoubleTap = {},
                        onCommitEdit = {},
                        onCommitResultLog = {},
                        onCycleStatus = {},
                        onCycleVariant = {},
                        onDragStart = {},
                        onDragMove = { _, _ -> },
                        onDragEnd = {},
                        onDragCancel = {},
                    )
                }
            }

            // B3: Active Mind Map Nodes
            val reparentTargetId = (dragEvaluation?.dropTarget as? DragDropTarget.Reparent)?.targetNodeId
            layoutResult.nodes.forEach { layoutNode ->
                val nodeId = layoutNode.node.id
                key(nodeId) {
                    BranchNodeCard(
                        layoutNode = layoutNode,
                        isSelected = nodeId == selectedNodeId,
                        isEditing = nodeId == editingNodeId,
                        isExecutionMode = isExecutionMode,
                        isReparentHighlight = nodeId == reparentTargetId,
                        isExiting = false,
                        flipVersion = flippingNodeIds[nodeId] ?: 0,
                        onSingleTap = { onSelectNode(nodeId) },
                        onDoubleTap = { onStartEditNode(nodeId) },
                        onCommitEdit = { newTitle -> onCommitEditNode(nodeId, newTitle) },
                        onCommitResultLog = { newLog -> onCommitResultLog(nodeId, newLog) },
                        onCycleStatus = { onCycleStatus(nodeId) },
                        onCycleVariant = { onCycleVariant(nodeId) },
                        onDragStart = {
                            draggingNodeId = nodeId
                            dragEvaluation = null
                        },
                        onDragMove = { worldX, worldY ->
                            dragEvaluation = BranchLayoutEngine.evaluateDrag(
                                root = branchRoot,
                                layout = layoutResult,
                                draggedNodeId = nodeId,
                                pointerWorldX = worldX,
                                pointerWorldY = worldY,
                            )
                        },
                        onDragEnd = {
                            val target = dragEvaluation?.dropTarget
                            val dragged = draggingNodeId
                            draggingNodeId = null
                            dragEvaluation = null
                            if (dragged != null && target != null) {
                                onApplyDragDrop(dragged, target)
                            }
                        },
                        onDragCancel = {
                            draggingNodeId = null
                            dragEvaluation = null
                        },
                    )
                }
            }

            // B4: Highest Z-Layer Controls: Collapse/Expand Dots (48dp invisible hitbox!) & Quick "+" Add Child
            layoutResult.nodes.forEach { layoutNode ->
                val node = layoutNode.node
                if (layoutNode.hasChildren) {
                    CollapseExpandDotButton(
                        layoutNode = layoutNode,
                        pxPerDp = pxPerDp,
                        onToggleCollapse = { onToggleCollapse(node.id) },
                    )
                }
                if (node.id == selectedNodeId && draggingNodeId == null) {
                    QuickAddChildButton(
                        layoutNode = layoutNode,
                        pxPerDp = pxPerDp,
                        onAddChild = { onQuickAddChild(node.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapseExpandDotButton(
    layoutNode: LayoutNode,
    pxPerDp: Float,
    onToggleCollapse: () -> Unit,
) {
    val node = layoutNode.node
    val branchColors = LocalBranchColors.current
    val accentColor = parseNodeHexColor(node.color)
    val hitSizeDp = 48f
    val centerX = layoutNode.rightX + 12f
    val centerY = layoutNode.centerY

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = ((centerX - hitSizeDp * 0.5f) * pxPerDp).roundToInt(),
                    y = ((centerY - hitSizeDp * 0.5f) * pxPerDp).roundToInt(),
                )
            }
            .zIndex(50f)
            .size(hitSizeDp.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleCollapse,
            )
            .testTag("collapse_dot_${node.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (node.isCollapsed) 24.dp else 20.dp)
                .clip(CircleShape)
                .background(if (node.isCollapsed) accentColor else branchColors.nodeSurface)
                .border(1.5.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (node.isCollapsed) {
                Text(
                    text = "${layoutNode.totalDescendants}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.action_toggle_collapse),
                    tint = accentColor,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickAddChildButton(
    layoutNode: LayoutNode,
    pxPerDp: Float,
    onAddChild: () -> Unit,
) {
    val node = layoutNode.node
    val accentColor = parseNodeHexColor(node.color)
    val offsetX = if (layoutNode.hasChildren) layoutNode.rightX + 34f else layoutNode.rightX + 12f
    val centerY = layoutNode.centerY

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = ((offsetX - 18f) * pxPerDp).roundToInt(),
                    y = ((centerY - 24f) * pxPerDp).roundToInt(),
                )
            }
            .zIndex(45f)
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onAddChild,
            )
            .testTag("quick_add_child_${node.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = accentColor,
            shadowElevation = 3.dp,
            modifier = Modifier.size(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_add_child),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
