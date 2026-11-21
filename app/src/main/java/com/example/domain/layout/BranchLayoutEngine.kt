package com.example.domain.layout

import androidx.compose.runtime.Immutable
import com.example.data.model.BranchNode
import com.example.data.model.NodeStatus
import com.example.data.model.ProgressStats
import com.example.data.model.TreeOperations
import com.example.ui.theme.stateGradientForIndex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// =========================================================================
// Layer 1: Horizontal Trunk Overview Layout Models (Phases + Collapsed Branches)
// =========================================================================

@Immutable
data class TrunkPhaseLayout(
    val phase: BranchNode,
    val phaseIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val progress: ProgressStats,
    val hasActiveDo: Boolean,
    val aboveBranches: List<TrunkBranchPillLayout>,
    val belowBranches: List<TrunkBranchPillLayout>,
) {
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f
    val rightX: Float get() = x + width
    val bottomY: Float get() = y + height
}

@Immutable
data class TrunkBranchPillLayout(
    val branch: BranchNode,
    val phaseId: String,
    val isAbove: Boolean,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val stemStartX: Float,
    val stemStartY: Float,
    val stemEndX: Float,
    val stemEndY: Float,
    val internalNodeCount: Int,
    val progress: ProgressStats,
    val hasActiveDo: Boolean,
    val stateGradientIndex: Int?,
) {
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f
}

@Immutable
data class TrunkOverviewLayoutResult(
    val phases: List<TrunkPhaseLayout>,
    val spineStartX: Float,
    val spineEndX: Float,
    val spineY: Float,
    val addPhaseX: Float,
    val addPhaseY: Float,
    val addPhaseWidth: Float,
    val addPhaseHeight: Float,
    val boundsMinX: Float,
    val boundsMaxX: Float,
    val boundsMinY: Float,
    val boundsMaxY: Float,
)

// =========================================================================
// Layer 2: Drilled-down Independent Mind Map Layout Models
// =========================================================================

@Immutable
data class LayoutNode(
    val node: BranchNode,
    val depth: Int,
    val trackIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val parentAnchorX: Float,
    val parentAnchorY: Float,
    val subtreeTop: Float,
    val subtreeBottom: Float,
    val hasChildren: Boolean,
    val totalDescendants: Int,
    val isInteractiveLeaf: Boolean,
    val hasActiveDoDescendant: Boolean,
    val stateGradientIndex: Int? = null,
    val progress: ProgressStats,
) {
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f
    val rightX: Float get() = x + width
    val bottomY: Float get() = y + height
}

@Immutable
data class LayoutEdge(
    val fromId: String,
    val toId: String,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val colorHex: String,
    val isStrikethroughEdge: Boolean = false,
    val stateGradientIndex: Int? = null,
)

@Immutable
data class SwimlaneBand(
    val trackIndex: Int,
    val title: String,
    val colorHex: String,
    val top: Float,
    val bottom: Float,
)

@Immutable
data class CanvasLayoutResult(
    val nodes: List<LayoutNode>,
    val edges: List<LayoutEdge>,
    val swimlanes: List<SwimlaneBand>,
    val boundsMinX: Float,
    val boundsMaxX: Float,
    val boundsMinY: Float,
    val boundsMaxY: Float,
)

sealed interface DragDropTarget {
    data class Reparent(
        val targetNodeId: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : DragDropTarget

    data class SiblingGap(
        val targetParentId: String,
        val insertIndex: Int,
        val lineStartX: Float,
        val lineEndX: Float,
        val lineY: Float,
    ) : DragDropTarget
}

@Immutable
data class SmartAlignmentGuide(
    val isVertical: Boolean,
    val coordinate: Float,
    val start: Float,
    val end: Float,
)

@Immutable
data class DragEvaluation(
    val dropTarget: DragDropTarget?,
    val guides: List<SmartAlignmentGuide>,
)

object BranchLayoutEngine {

    // Trunk Overview constants (world dp)
    const val PHASE_WIDTH = 224f
    const val PHASE_HEIGHT = 86f
    const val PHASE_HORIZONTAL_GAP = 104f
    const val BRANCH_PILL_HEIGHT = 44f
    const val BRANCH_PILL_VERTICAL_GAP = 14f
    const val BRANCH_STEM_CLEARANCE = 54f
    const val ADD_PHASE_WIDTH = 140f
    const val ADD_PHASE_HEIGHT = 56f

    // Mind Map constants (world dp)
    const val HORIZONTAL_GAP = 78f
    const val SIBLING_VERTICAL_GAP = 22f
    const val TRACK_VERTICAL_GAP = 54f
    const val BASE_NODE_HEIGHT = 54f
    const val EXEC_PROGRESS_EXTRA_HEIGHT = 12f
    const val EXEC_RESULT_LOG_EXTRA_HEIGHT = 30f
    const val VARIANT_TAG_EXTRA_WIDTH = 66f
    const val ALIGNMENT_SNAP_THRESHOLD = 14f

    // =========================================================================
    // LAYER 1: TRUNK OVERVIEW LAYOUT (Horizontal Spine + Milestone Capsules + Above/Below Branch Pills)
    // =========================================================================

    fun computeTrunkOverviewLayout(
        projectRoot: BranchNode,
    ): TrunkOverviewLayoutResult {
        val phases = projectRoot.children.sortedBy { it.orderIndex }
        val spineY = 0f
        val phaseLayouts = mutableListOf<TrunkPhaseLayout>()

        var cursorX = 80f
        var minY = -180f
        var maxY = 180f

        phases.forEachIndexed { phaseIdx, phase ->
            val branches = phase.children.sortedBy { it.orderIndex }
            val aboveRaw = branches.filterIndexed { idx, _ -> idx % 2 == 0 }
            val belowRaw = branches.filterIndexed { idx, _ -> idx % 2 == 1 }

            // Ensure horizontal footprint accounts for widest Branch pill attached to this phase
            val maxPillWidth = branches.maxOfOrNull { measureBranchPillWidth(it) } ?: 0f
            val slotWidth = max(PHASE_WIDTH, maxPillWidth + 24f)
            val phaseX = cursorX + (slotWidth - PHASE_WIDTH) * 0.5f
            val phaseY = spineY - PHASE_HEIGHT * 0.5f
            val phaseCenterX = phaseX + PHASE_WIDTH * 0.5f

            // Stack above branches upwards from phase top
            val abovePills = mutableListOf<TrunkBranchPillLayout>()
            var aboveBottomCursor = phaseY - BRANCH_STEM_CLEARANCE
            aboveRaw.forEach { branch ->
                val pillW = measureBranchPillWidth(branch)
                val pillX = phaseCenterX - pillW * 0.5f
                val pillY = aboveBottomCursor - BRANCH_PILL_HEIGHT
                val progress = TreeOperations.calculateLeafProgressRollup(branch, treatEmptySelfAsLeaf = true)
                abovePills.add(
                    TrunkBranchPillLayout(
                        branch = branch,
                        phaseId = phase.id,
                        isAbove = true,
                        x = pillX,
                        y = pillY,
                        width = pillW,
                        height = BRANCH_PILL_HEIGHT,
                        stemStartX = phaseCenterX,
                        stemStartY = phaseY,
                        stemEndX = phaseCenterX,
                        stemEndY = pillY + BRANCH_PILL_HEIGHT,
                        internalNodeCount = TreeOperations.countActiveInternalNodes(branch),
                        progress = progress,
                        hasActiveDo = TreeOperations.hasActiveDoLeaf(branch),
                        stateGradientIndex = branch.activeVariant()?.gradientIndex,
                    )
                )
                minY = min(minY, pillY - 60f)
                aboveBottomCursor = pillY - BRANCH_PILL_VERTICAL_GAP
            }

            // Stack below branches downwards from phase bottom
            val belowPills = mutableListOf<TrunkBranchPillLayout>()
            var belowTopCursor = phaseY + PHASE_HEIGHT + BRANCH_STEM_CLEARANCE
            belowRaw.forEach { branch ->
                val pillW = measureBranchPillWidth(branch)
                val pillX = phaseCenterX - pillW * 0.5f
                val pillY = belowTopCursor
                val progress = TreeOperations.calculateLeafProgressRollup(branch, treatEmptySelfAsLeaf = true)
                belowPills.add(
                    TrunkBranchPillLayout(
                        branch = branch,
                        phaseId = phase.id,
                        isAbove = false,
                        x = pillX,
                        y = pillY,
                        width = pillW,
                        height = BRANCH_PILL_HEIGHT,
                        stemStartX = phaseCenterX,
                        stemStartY = phaseY + PHASE_HEIGHT,
                        stemEndX = phaseCenterX,
                        stemEndY = pillY,
                        internalNodeCount = TreeOperations.countActiveInternalNodes(branch),
                        progress = progress,
                        hasActiveDo = TreeOperations.hasActiveDoLeaf(branch),
                        stateGradientIndex = branch.activeVariant()?.gradientIndex,
                    )
                )
                maxY = max(maxY, pillY + BRANCH_PILL_HEIGHT + 60f)
                belowTopCursor = pillY + BRANCH_PILL_HEIGHT + BRANCH_PILL_VERTICAL_GAP
            }

            phaseLayouts.add(
                TrunkPhaseLayout(
                    phase = phase,
                    phaseIndex = phaseIdx,
                    x = phaseX,
                    y = phaseY,
                    width = PHASE_WIDTH,
                    height = PHASE_HEIGHT,
                    progress = TreeOperations.calculateLeafProgressRollup(phase, treatEmptySelfAsLeaf = false),
                    hasActiveDo = TreeOperations.hasActiveDoLeaf(phase),
                    aboveBranches = abovePills,
                    belowBranches = belowPills,
                )
            )

            cursorX += slotWidth + PHASE_HORIZONTAL_GAP
        }

        val addPhaseX = cursorX
        val addPhaseY = spineY - ADD_PHASE_HEIGHT * 0.5f
        val spineStartX = phaseLayouts.firstOrNull()?.centerX ?: addPhaseX
        val spineEndX = addPhaseX

        return TrunkOverviewLayoutResult(
            phases = phaseLayouts,
            spineStartX = spineStartX,
            spineEndX = spineEndX,
            spineY = spineY,
            addPhaseX = addPhaseX,
            addPhaseY = addPhaseY,
            addPhaseWidth = ADD_PHASE_WIDTH,
            addPhaseHeight = ADD_PHASE_HEIGHT,
            boundsMinX = 0f,
            boundsMaxX = addPhaseX + ADD_PHASE_WIDTH + 120f,
            boundsMinY = minY,
            boundsMaxY = maxY,
        )
    }

    private fun measureBranchPillWidth(branch: BranchNode): Float {
        val visualChars = branch.title.sumOf { ch -> if (ch.code > 127) 2L else 1L }.toFloat()
        val textDp = visualChars * 7.6f
        return (textDp + 86f).coerceIn(132f, 236f)
    }

    // =========================================================================
    // LAYER 2: INDEPENDENT DRILLED-DOWN MIND MAP LAYOUT (Auto-Layout + Dynamic Push)
    // =========================================================================

    fun computeMindMapLayout(
        branchRoot: BranchNode,
        isExecutionMode: Boolean,
    ): CanvasLayoutResult {
        val nodes = mutableListOf<LayoutNode>()
        val edges = mutableListOf<LayoutEdge>()
        val swimlanes = mutableListOf<SwimlaneBand>()

        val rootGradient = branchRoot.activeVariant()?.gradientIndex
        val rootSize = measureNodeSize(branchRoot, depth = 0, isExecutionMode = isExecutionMode)
        val rootProgress = TreeOperations.calculateLeafProgressRollup(branchRoot, treatEmptySelfAsLeaf = true)
        val rootVisibleChildren = if (branchRoot.isCollapsed) emptyList() else branchRoot.children.sortedBy { it.orderIndex }

        if (rootVisibleChildren.isEmpty()) {
            val rootLayout = LayoutNode(
                node = branchRoot,
                depth = 0,
                trackIndex = -1,
                x = 0f,
                y = -rootSize.second * 0.5f,
                width = rootSize.first,
                height = rootSize.second,
                parentAnchorX = 0f,
                parentAnchorY = -rootSize.second * 0.5f,
                subtreeTop = -rootSize.second * 0.5f,
                subtreeBottom = rootSize.second * 0.5f,
                hasChildren = branchRoot.children.isNotEmpty(),
                totalDescendants = TreeOperations.countDescendants(branchRoot),
                isInteractiveLeaf = TreeOperations.isInteractiveLeaf(branchRoot),
                hasActiveDoDescendant = TreeOperations.hasActiveDoLeaf(branchRoot),
                stateGradientIndex = rootGradient,
                progress = rootProgress,
            )
            nodes.add(rootLayout)
            return buildResult(nodes, edges, swimlanes)
        }

        val subtreeHeights = mutableMapOf<String, Float>()
        fun measureSubtreeHeight(node: BranchNode, depth: Int): Float {
            val (_, h) = measureNodeSize(node, depth, isExecutionMode)
            val visibleKids = if (node.isCollapsed) emptyList() else node.children.sortedBy { it.orderIndex }
            val result = if (visibleKids.isEmpty()) {
                h
            } else {
                val childrenSum = visibleKids.sumOf { measureSubtreeHeight(it, depth + 1).toDouble() }.toFloat() +
                    SIBLING_VERTICAL_GAP * (visibleKids.size - 1)
                max(h, childrenSum)
            }
            subtreeHeights[node.id] = result
            return result
        }

        val trackHeights = rootVisibleChildren.map { measureSubtreeHeight(it, 1) }
        val totalTracksHeight = trackHeights.sum() + TRACK_VERTICAL_GAP * (rootVisibleChildren.size - 1)
        val startY = -totalTracksHeight * 0.5f

        val rootY = -rootSize.second * 0.5f
        val rootLayout = LayoutNode(
            node = branchRoot,
            depth = 0,
            trackIndex = -1,
            x = 0f,
            y = rootY,
            width = rootSize.first,
            height = rootSize.second,
            parentAnchorX = 0f,
            parentAnchorY = rootY,
            subtreeTop = startY,
            subtreeBottom = startY + totalTracksHeight,
            hasChildren = branchRoot.children.isNotEmpty(),
            totalDescendants = TreeOperations.countDescendants(branchRoot),
            isInteractiveLeaf = TreeOperations.isInteractiveLeaf(branchRoot),
            hasActiveDoDescendant = TreeOperations.hasActiveDoLeaf(branchRoot),
            stateGradientIndex = rootGradient,
            progress = rootProgress,
        )
        nodes.add(rootLayout)

        fun placeSubtree(
            node: BranchNode,
            depth: Int,
            trackIndex: Int,
            x: Float,
            bandTop: Float,
            parentLayout: LayoutNode,
            inheritedGradientIndex: Int?,
        ) {
            val activeGradient = node.activeVariant()?.gradientIndex ?: inheritedGradientIndex
            val (w, h) = measureNodeSize(node, depth, isExecutionMode)
            val mySubtreeH = subtreeHeights[node.id] ?: h
            val myY = bandTop + (mySubtreeH - h) * 0.5f

            val layoutNode = LayoutNode(
                node = node,
                depth = depth,
                trackIndex = trackIndex,
                x = x,
                y = myY,
                width = w,
                height = h,
                parentAnchorX = parentLayout.rightX,
                parentAnchorY = parentLayout.centerY - h * 0.5f,
                subtreeTop = bandTop,
                subtreeBottom = bandTop + mySubtreeH,
                hasChildren = node.children.isNotEmpty(),
                totalDescendants = TreeOperations.countDescendants(node),
                isInteractiveLeaf = TreeOperations.isInteractiveLeaf(node),
                hasActiveDoDescendant = TreeOperations.hasActiveDoLeaf(node),
                stateGradientIndex = activeGradient,
                progress = TreeOperations.calculateLeafProgressRollup(node, treatEmptySelfAsLeaf = true),
            )
            nodes.add(layoutNode)

            val edgeColorHex = activeGradient?.let { stateGradientForIndex(it).accentHex } ?: node.color
            edges.add(
                LayoutEdge(
                    fromId = parentLayout.node.id,
                    toId = node.id,
                    startX = parentLayout.rightX,
                    startY = parentLayout.centerY,
                    endX = layoutNode.x,
                    endY = layoutNode.centerY,
                    colorHex = edgeColorHex,
                    isStrikethroughEdge = node.isStrikethrough,
                    stateGradientIndex = activeGradient,
                )
            )

            val visibleKids = if (node.isCollapsed) emptyList() else node.children.sortedBy { it.orderIndex }
            if (visibleKids.isEmpty()) return

            val totalKidsH = visibleKids.sumOf { (subtreeHeights[it.id] ?: BASE_NODE_HEIGHT).toDouble() }.toFloat() +
                SIBLING_VERTICAL_GAP * (visibleKids.size - 1)
            var childBandTop = bandTop + (mySubtreeH - totalKidsH) * 0.5f
            val childX = x + w + HORIZONTAL_GAP

            for (kid in visibleKids) {
                val kidH = subtreeHeights[kid.id] ?: BASE_NODE_HEIGHT
                placeSubtree(
                    node = kid,
                    depth = depth + 1,
                    trackIndex = trackIndex,
                    x = childX,
                    bandTop = childBandTop,
                    parentLayout = layoutNode,
                    inheritedGradientIndex = activeGradient,
                )
                childBandTop += kidH + SIBLING_VERTICAL_GAP
            }
        }

        var cursorY = startY
        val trackStartX = rootLayout.rightX + HORIZONTAL_GAP + 12f
        rootVisibleChildren.forEachIndexed { idx, trackNode ->
            val bandH = trackHeights[idx]
            val trackGradient = trackNode.activeVariant()?.gradientIndex ?: rootGradient
            val bandColorHex = trackGradient?.let { stateGradientForIndex(it).accentHex } ?: trackNode.color
            swimlanes.add(
                SwimlaneBand(
                    trackIndex = idx,
                    title = trackNode.title,
                    colorHex = bandColorHex,
                    top = cursorY - 14f,
                    bottom = cursorY + bandH + 14f,
                )
            )
            placeSubtree(
                node = trackNode,
                depth = 1,
                trackIndex = idx,
                x = trackStartX,
                bandTop = cursorY,
                parentLayout = rootLayout,
                inheritedGradientIndex = rootGradient,
            )
            cursorY += bandH + TRACK_VERTICAL_GAP
        }

        return buildResult(nodes, edges, swimlanes)
    }

    // =========================================================================
    // DUAL DROP-TARGET & SMART ALIGNMENT GUIDE EVALUATOR
    // =========================================================================

    fun evaluateDrag(
        root: BranchNode,
        layout: CanvasLayoutResult,
        draggedNodeId: String,
        pointerWorldX: Float,
        pointerWorldY: Float,
    ): DragEvaluation {
        val candidates = layout.nodes.filter { candidate ->
            candidate.node.id != draggedNodeId &&
                !TreeOperations.isDescendant(root, draggedNodeId, candidate.node.id)
        }

        var dropTarget: DragDropTarget? = null
        var bestDist = Float.MAX_VALUE

        for (target in candidates) {
            val withinX = pointerWorldX in (target.x - 24f)..(target.rightX + 24f)
            if (!withinX) continue

            val innerTop = target.y + target.height * 0.22f
            val innerBottom = target.bottomY - target.height * 0.22f
            if (pointerWorldY in innerTop..innerBottom) {
                val dist = abs(pointerWorldY - target.centerY)
                if (dist < bestDist) {
                    bestDist = dist
                    dropTarget = DragDropTarget.Reparent(
                        targetNodeId = target.node.id,
                        left = target.x,
                        top = target.y,
                        right = target.rightX,
                        bottom = target.bottomY,
                    )
                }
            } else if (target.node.parentId != null) {
                val topGapDist = abs(pointerWorldY - (target.y - 8f))
                val bottomGapDist = abs(pointerWorldY - (target.bottomY + 8f))
                val parentId = target.node.parentId
                val parent = TreeOperations.findNode(root, parentId)
                val siblingIdx = parent?.children?.indexOfFirst { it.id == target.node.id } ?: 0

                if (topGapDist < 22f && topGapDist < bestDist) {
                    bestDist = topGapDist
                    dropTarget = DragDropTarget.SiblingGap(
                        targetParentId = parentId,
                        insertIndex = siblingIdx,
                        lineStartX = target.x,
                        lineEndX = target.rightX,
                        lineY = target.y - 9f,
                    )
                } else if (bottomGapDist < 22f && bottomGapDist < bestDist) {
                    bestDist = bottomGapDist
                    dropTarget = DragDropTarget.SiblingGap(
                        targetParentId = parentId,
                        insertIndex = siblingIdx + 1,
                        lineStartX = target.x,
                        lineEndX = target.rightX,
                        lineY = target.bottomY + 9f,
                    )
                }
            }
        }

        val guides = mutableListOf<SmartAlignmentGuide>()
        val alignedX = candidates.firstOrNull { abs(it.centerX - pointerWorldX) <= ALIGNMENT_SNAP_THRESHOLD }
        if (alignedX != null) {
            guides.add(
                SmartAlignmentGuide(
                    isVertical = true,
                    coordinate = alignedX.centerX,
                    start = min(alignedX.y, pointerWorldY) - 40f,
                    end = max(alignedX.bottomY, pointerWorldY) + 40f,
                )
            )
        }
        val alignedY = candidates.firstOrNull { abs(it.centerY - pointerWorldY) <= ALIGNMENT_SNAP_THRESHOLD }
        if (alignedY != null) {
            guides.add(
                SmartAlignmentGuide(
                    isVertical = false,
                    coordinate = alignedY.centerY,
                    start = min(alignedY.x, pointerWorldX) - 40f,
                    end = max(alignedY.rightX, pointerWorldX) + 40f,
                )
            )
        }

        return DragEvaluation(dropTarget = dropTarget, guides = guides)
    }

    private fun measureNodeSize(
        node: BranchNode,
        depth: Int,
        isExecutionMode: Boolean,
    ): Pair<Float, Float> {
        val visualChars = node.title.sumOf { ch -> if (ch.code > 127) 2L else 1L }.toFloat()
        val charWidth = if (depth == 0) 9.4f else 8.2f
        val statusPadding = if (isExecutionMode) 36f else 12f
        val variantPadding = if (node.variants.isNotEmpty()) VARIANT_TAG_EXTRA_WIDTH else 0f
        val basePadding = if (depth == 0) 56f else 44f
        val minW = if (depth == 0) 158f else 132f
        val maxW = if (depth == 0) 270f else 248f
        val width = (visualChars * charWidth + basePadding + statusPadding + variantPadding).coerceIn(minW, maxW)

        val isLeaf = TreeOperations.isInteractiveLeaf(node)
        val hasProgressBar = isExecutionMode && !isLeaf && !node.isStrikethrough
        val hasResultLog = isExecutionMode && isLeaf && node.status == NodeStatus.DONE && !node.isStrikethrough
        val baseH = if (depth == 0) 64f else BASE_NODE_HEIGHT
        val height = baseH +
            (if (hasProgressBar) EXEC_PROGRESS_EXTRA_HEIGHT else 0f) +
            (if (hasResultLog) EXEC_RESULT_LOG_EXTRA_HEIGHT else 0f)
        return width to height
    }

    private fun buildResult(
        nodes: List<LayoutNode>,
        edges: List<LayoutEdge>,
        swimlanes: List<SwimlaneBand>,
    ): CanvasLayoutResult {
        if (nodes.isEmpty()) {
            return CanvasLayoutResult(nodes, edges, swimlanes, 0f, 300f, -150f, 150f)
        }
        val minX = nodes.minOf { it.x } - 100f
        val maxX = nodes.maxOf { it.rightX } + 160f
        val minY = nodes.minOf { it.y } - 120f
        val maxY = nodes.maxOf { it.bottomY } + 120f
        return CanvasLayoutResult(
            nodes = nodes,
            edges = edges,
            swimlanes = swimlanes,
            boundsMinX = minX,
            boundsMaxX = maxX,
            boundsMinY = minY,
            boundsMaxY = maxY,
        )
    }
}
