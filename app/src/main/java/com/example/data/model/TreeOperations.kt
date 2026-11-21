package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class ProgressStats(
    val doneCount: Int,
    val totalCount: Int,
) {
    val ratio: Float
        get() = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()

    val isCompleted: Boolean
        get() = totalCount > 0 && doneCount == totalCount
}

object TreeOperations {

    fun findNode(root: BranchNode, targetId: String): BranchNode? {
        if (root.id == targetId) return root
        for (child in root.children) {
            findNode(child, targetId)?.let { return it }
        }
        return null
    }

    /**
     * Returns the ancestor path from root down to [targetId] inclusive.
     */
    fun findPathToNode(root: BranchNode, targetId: String): List<BranchNode> {
        if (root.id == targetId) return listOf(root)
        for (child in root.children) {
            val childPath = findPathToNode(child, targetId)
            if (childPath.isNotEmpty()) {
                return listOf(root) + childPath
            }
        }
        return emptyList()
    }

    /**
     * Finds the Trunk Phase (level-1 child of project root) that owns [branchId].
     */
    fun findParentPhaseForBranch(root: BranchNode, branchId: String): BranchNode? {
        return root.children.firstOrNull { phase ->
            phase.children.any { it.id == branchId }
        }
    }

    fun countDescendants(node: BranchNode): Int {
        var count = node.children.size
        for (child in node.children) {
            count += countDescendants(child)
        }
        return count
    }

    /**
     * Counts non-strikethrough nodes inside a Branch (including the branch root itself if empty,
     * or all active child/descendant nodes inside the Branch).
     */
    fun countActiveInternalNodes(branch: BranchNode): Int {
        var count = 0
        fun traverse(curr: BranchNode) {
            for (child in curr.children) {
                if (!child.isStrikethrough) {
                    count++
                    traverse(child)
                }
            }
        }
        traverse(branch)
        return count
    }

    fun countTotalNodes(root: BranchNode): Int = 1 + countDescendants(root)

    /**
     * Strict Leaf-Node Driven Rule:
     * A node can manually toggle its status ring ONLY if it is NOT strikethrough
     * and has ZERO active (non-strikethrough) children.
     * Parent nodes with active children are strictly locked and driven bottom-up.
     */
    fun isInteractiveLeaf(node: BranchNode): Boolean {
        if (node.isStrikethrough) return false
        return node.children.none { !it.isStrikethrough }
    }

    /**
     * Strict Bottom-Up Leaf-Node Progress Rollup:
     * - Strikethrough nodes and their entire subtrees are automatically excluded.
     * - Only bottom-most active leaf nodes contribute to [totalCount] and [doneCount].
     * - For a Branch with no active children, the Branch node itself counts as 1 active leaf.
     * - For a Phase or Project Root, counts strictly across all active leaves beneath it
     *   (an empty Phase with no active Branches yields 0/0 rather than 1).
     */
    fun calculateLeafProgressRollup(
        node: BranchNode,
        treatEmptySelfAsLeaf: Boolean = true,
    ): ProgressStats {
        if (node.isStrikethrough) return ProgressStats(doneCount = 0, totalCount = 0)
        val activeChildren = node.children.filterNot { it.isStrikethrough }
        if (activeChildren.isEmpty()) {
            return if (treatEmptySelfAsLeaf) {
                ProgressStats(
                    doneCount = if (node.status == NodeStatus.DONE) 1 else 0,
                    totalCount = 1,
                )
            } else {
                ProgressStats(doneCount = 0, totalCount = 0)
            }
        }

        var done = 0
        var total = 0
        fun collectLeaves(curr: BranchNode) {
            if (curr.isStrikethrough) return
            val activeKids = curr.children.filterNot { it.isStrikethrough }
            if (activeKids.isEmpty()) {
                total++
                if (curr.status == NodeStatus.DONE) done++
            } else {
                for (kid in activeKids) {
                    collectLeaves(kid)
                }
            }
        }
        for (child in activeChildren) {
            collectLeaves(child)
        }
        return ProgressStats(doneCount = done, totalCount = total)
    }

    /**
     * Returns true if [node] or any active leaf inside it is currently in DO status.
     */
    fun hasActiveDoLeaf(node: BranchNode): Boolean {
        if (node.isStrikethrough) return false
        val activeChildren = node.children.filterNot { it.isStrikethrough }
        if (activeChildren.isEmpty()) {
            return node.status == NodeStatus.DO
        }
        return activeChildren.any { hasActiveDoLeaf(it) }
    }

    fun isDescendant(root: BranchNode, ancestorId: String, candidateId: String): Boolean {
        val ancestor = findNode(root, ancestorId) ?: return false
        if (ancestor.id == candidateId) return true
        return findNode(ancestor, candidateId) != null
    }

    fun updateNode(
        root: BranchNode,
        targetId: String,
        transform: (BranchNode) -> BranchNode,
    ): BranchNode {
        if (root.id == targetId) return transform(root)
        var changed = false
        val updatedChildren = root.children.map { child ->
            val next = updateNode(child, targetId, transform)
            if (next !== child) changed = true
            next
        }
        return if (changed) root.copy(children = updatedChildren) else root
    }

    fun addChildNode(
        root: BranchNode,
        parentId: String,
        title: String,
        colorOverride: String? = null,
    ): Pair<BranchNode, BranchNode?> {
        val parent = findNode(root, parentId) ?: return root to null
        val newChild = BranchNode(
            id = generateBranchId("n"),
            parentId = parentId,
            title = title,
            color = colorOverride ?: parent.color,
            orderIndex = parent.children.size,
            status = NodeStatus.PLAN,
            isCollapsed = false,
            isPinned = false,
            isStrikethrough = parent.isStrikethrough,
        )
        val updated = updateNode(root, parentId) { node ->
            val appended = (node.children + newChild).mapIndexed { idx, child ->
                child.copy(orderIndex = idx, parentId = node.id)
            }
            node.copy(isCollapsed = false, children = appended)
        }
        return updated to newChild
    }

    fun deleteNode(root: BranchNode, targetId: String): BranchNode {
        if (root.id == targetId) return root
        val filtered = root.children.filterNot { it.id == targetId }
        val reindexed = if (filtered.size != root.children.size) {
            filtered.mapIndexed { idx, child -> child.copy(orderIndex = idx) }
        } else {
            root.children.map { deleteNode(it, targetId) }
        }
        return root.copy(children = reindexed)
    }

    /**
     * Soft Delete (Strikethrough):
     * Toggling strikethrough on a parent cascades identically to ALL descendant nodes.
     */
    fun toggleStrikethroughCascading(root: BranchNode, targetId: String): BranchNode {
        val target = findNode(root, targetId) ?: return root
        val nextValue = !target.isStrikethrough
        fun applyAll(node: BranchNode): BranchNode = node.copy(
            isStrikethrough = nextValue,
            children = node.children.map { applyAll(it) },
        )
        return updateNode(root, targetId) { applyAll(it) }
    }

    fun deepCloneNode(
        source: BranchNode,
        newParentId: String?,
        newOrderIndex: Int,
        titleSuffix: String = "",
    ): BranchNode {
        val newId = generateBranchId("n")
        val clonedChildren = source.children.mapIndexed { idx, child ->
            deepCloneNode(child, newParentId = newId, newOrderIndex = idx)
        }
        return source.copy(
            id = newId,
            parentId = newParentId,
            title = if (titleSuffix.isNotEmpty()) "${source.title}$titleSuffix" else source.title,
            orderIndex = newOrderIndex,
            isPinned = false,
            variants = emptyList(),
            activeVariantId = null,
            children = clonedChildren,
        )
    }

    fun deepCloneChildren(children: List<BranchNode>, parentId: String): List<BranchNode> {
        return children.sortedBy { it.orderIndex }.mapIndexed { idx, child ->
            deepCloneNode(child, newParentId = parentId, newOrderIndex = idx)
        }
    }

    fun duplicateSubtreeAsSibling(root: BranchNode, targetId: String): Pair<BranchNode, String?> {
        if (root.id == targetId) return root to null
        val target = findNode(root, targetId) ?: return root to null
        val parentId = target.parentId ?: return root to null
        val parent = findNode(root, parentId) ?: return root to null
        val targetIdx = parent.children.indexOfFirst { it.id == targetId }
        if (targetIdx < 0) return root to null

        val cloned = deepCloneNode(
            source = target,
            newParentId = parentId,
            newOrderIndex = targetIdx + 1,
        )
        val updated = updateNode(root, parentId) { p ->
            val mutable = p.children.toMutableList()
            mutable.add(targetIdx + 1, cloned)
            p.copy(
                children = mutable.mapIndexed { idx, child ->
                    child.copy(orderIndex = idx, parentId = p.id)
                }
            )
        }
        return updated to cloned.id
    }

    fun reparentNode(root: BranchNode, draggedId: String, newParentId: String): BranchNode {
        if (draggedId == root.id || draggedId == newParentId) return root
        if (isDescendant(root, draggedId, newParentId)) return root
        val dragged = findNode(root, draggedId) ?: return root
        val stripped = deleteNode(root, draggedId)
        return updateNode(stripped, newParentId) { parent ->
            val updatedDragged = dragged.copy(
                parentId = parent.id,
                orderIndex = parent.children.size,
            )
            val reindexed = (parent.children + updatedDragged).mapIndexed { idx, c ->
                c.copy(orderIndex = idx, parentId = parent.id)
            }
            parent.copy(isCollapsed = false, children = reindexed)
        }
    }

    fun moveToSiblingIndex(
        root: BranchNode,
        draggedId: String,
        targetParentId: String,
        insertIndex: Int,
    ): BranchNode {
        if (draggedId == root.id) return root
        if (isDescendant(root, draggedId, targetParentId)) return root
        val dragged = findNode(root, draggedId) ?: return root
        val originalParent = dragged.parentId?.let { findNode(root, it) }
        val originalIndex = originalParent?.children?.indexOfFirst { it.id == draggedId } ?: -1

        val adjustedIndex = if (dragged.parentId == targetParentId && originalIndex in 0 until insertIndex) {
            insertIndex - 1
        } else {
            insertIndex
        }

        val stripped = deleteNode(root, draggedId)
        return updateNode(stripped, targetParentId) { parent ->
            val mutable = parent.children.toMutableList()
            val clamped = adjustedIndex.coerceIn(0, mutable.size)
            mutable.add(clamped, dragged.copy(parentId = parent.id))
            parent.copy(
                isCollapsed = false,
                children = mutable.mapIndexed { idx, c ->
                    c.copy(orderIndex = idx, parentId = parent.id)
                },
            )
        }
    }

    private val phaseColorCycle = listOf("#2563EB", "#10B981", "#F59E0B", "#8B5CF6", "#14B8A6", "#F43F5E")

    fun nextPhaseColor(existingPhaseCount: Int): String =
        phaseColorCycle[existingPhaseCount % phaseColorCycle.size]

    /**
     * Creates a new project structured natively for Trunk (Phases) -> Branches -> Leaf Tasks.
     */
    fun createNewProjectBoard(userId: String, title: String): BranchBoard {
        val boardId = generateBranchId("proj")
        val rootId = generateBranchId("root")
        val p1Id = generateBranchId("phase")
        val p2Id = generateBranchId("phase")
        val b1Id = generateBranchId("branch")
        val b2Id = generateBranchId("branch")
        val cleanTitle = title.ifBlank { "未命名專案" }.take(120)

        return BranchBoard(
            id = boardId,
            userId = userId,
            schemaVersion = 3,
            title = cleanTitle,
            activeMode = WorkMode.DRAFT,
            activeView = CanvasViewType.TRUNK,
            trunkRevealLevel = 2,
            root = BranchNode(
                id = rootId,
                parentId = null,
                title = cleanTitle,
                color = "#334155",
                orderIndex = 0,
                children = listOf(
                    BranchNode(
                        id = p1Id,
                        parentId = rootId,
                        title = "階段一 · 探索規劃",
                        color = "#2563EB",
                        orderIndex = 0,
                        children = listOf(
                            BranchNode(
                                id = b1Id,
                                parentId = p1Id,
                                title = "核心拆解",
                                color = "#2563EB",
                                orderIndex = 0,
                                children = listOf(
                                    BranchNode(
                                        id = generateBranchId("n"),
                                        parentId = b1Id,
                                        title = "釐清主要目標",
                                        color = "#2563EB",
                                        orderIndex = 0,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    BranchNode(
                        id = p2Id,
                        parentId = rootId,
                        title = "階段二 · 執行交付",
                        color = "#10B981",
                        orderIndex = 1,
                        children = listOf(
                            BranchNode(
                                id = b2Id,
                                parentId = p2Id,
                                title = "首波推進",
                                color = "#10B981",
                                orderIndex = 0,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    /**
     * Starter Project showcasing:
     * - Horizontal Trunk with 3 Milestone Phases
     * - Completed Branch ("需求調研", 2/2 DONE -> micro-lit solid completed feedback on Trunk)
     * - Active In-Progress Branch ("互動設計", includes DO glowing leaf & DONE result log)
     * - Planning Branches ready to drill down
     */
    fun createStarterBoard(userId: String): BranchBoard {
        val rootId = "root_main"
        val phase1Id = "phase_explore"
        val phase2Id = "phase_build"
        val phase3Id = "phase_launch"

        val b1aId = "branch_research"
        val b1bId = "branch_position"
        val b2aId = "branch_interaction"
        val b2bId = "branch_visual"
        val b3aId = "branch_beta"

        val root = BranchNode(
            id = rootId,
            parentId = null,
            title = "Branch 產品路線圖",
            color = "#334155",
            orderIndex = 0,
            children = listOf(
                BranchNode(
                    id = phase1Id,
                    parentId = rootId,
                    title = "階段一 · 策略探索",
                    color = "#2563EB",
                    orderIndex = 0,
                    children = listOf(
                        BranchNode(
                            id = b1aId,
                            parentId = phase1Id,
                            title = "需求調研",
                            color = "#2563EB",
                            orderIndex = 0,
                            children = listOf(
                                BranchNode(
                                    id = "n_r1",
                                    parentId = b1aId,
                                    title = "訪談核心使用者",
                                    resultLog = "完成 8 位深度訪談，收斂 3 大痛點",
                                    color = "#2563EB",
                                    orderIndex = 0,
                                    status = NodeStatus.DONE,
                                ),
                                BranchNode(
                                    id = "n_r2",
                                    parentId = b1aId,
                                    title = "收斂雙層架構規格",
                                    resultLog = "確立主幹總覽 × 心智圖下鑽流程",
                                    color = "#2563EB",
                                    orderIndex = 1,
                                    status = NodeStatus.DONE,
                                ),
                            ),
                        ),
                        BranchNode(
                            id = b1bId,
                            parentId = phase1Id,
                            title = "核心定位",
                            color = "#2563EB",
                            orderIndex = 1,
                            children = listOf(
                                BranchNode(
                                    id = "n_p1",
                                    parentId = b1bId,
                                    title = "定義極簡視覺語言",
                                    resultLog = "採用紙墨光影與環境微光提示",
                                    color = "#2563EB",
                                    orderIndex = 0,
                                    status = NodeStatus.DONE,
                                ),
                                BranchNode(
                                    id = "n_p2",
                                    parentId = b1bId,
                                    title = "撰寫產品宣言",
                                    color = "#2563EB",
                                    orderIndex = 1,
                                    status = NodeStatus.DO,
                                ),
                            ),
                        ),
                    ),
                ),
                BranchNode(
                    id = phase2Id,
                    parentId = rootId,
                    title = "階段二 · 體驗建構",
                    color = "#10B981",
                    orderIndex = 1,
                    children = listOf(
                        BranchNode(
                            id = b2aId,
                            parentId = phase2Id,
                            title = "互動架構",
                            color = "#10B981",
                            orderIndex = 0,
                            children = listOf(
                                BranchNode(
                                    id = "n_i1",
                                    parentId = b2aId,
                                    title = "主幹下鑽縮放動畫",
                                    resultLog = "60/120fps 絲滑過渡驗證完成",
                                    color = "#10B981",
                                    orderIndex = 0,
                                    status = NodeStatus.DONE,
                                ),
                                BranchNode(
                                    id = "n_i2",
                                    parentId = b2aId,
                                    title = "葉節點進度彙整引擎",
                                    color = "#10B981",
                                    orderIndex = 1,
                                    status = NodeStatus.DO,
                                ),
                                BranchNode(
                                    id = "n_i3",
                                    parentId = b2aId,
                                    title = "節點多狀態漸層切換",
                                    color = "#10B981",
                                    orderIndex = 2,
                                    status = NodeStatus.PLAN,
                                ),
                            ),
                        ),
                        BranchNode(
                            id = b2bId,
                            parentId = phase2Id,
                            title = "光影氛圍打磨",
                            color = "#10B981",
                            orderIndex = 1,
                            children = listOf(
                                BranchNode(
                                    id = "n_v1",
                                    parentId = b2bId,
                                    title = "開工模式邊緣微光",
                                    color = "#10B981",
                                    orderIndex = 0,
                                    status = NodeStatus.PLAN,
                                ),
                                BranchNode(
                                    id = "n_v2",
                                    parentId = b2bId,
                                    title = "完成態實心膠囊回饋",
                                    color = "#10B981",
                                    orderIndex = 1,
                                    status = NodeStatus.PLAN,
                                ),
                            ),
                        ),
                    ),
                ),
                BranchNode(
                    id = phase3Id,
                    parentId = rootId,
                    title = "階段三 · 發布驗證",
                    color = "#F59E0B",
                    orderIndex = 2,
                    children = listOf(
                        BranchNode(
                            id = b3aId,
                            parentId = phase3Id,
                            title = "公開測試",
                            color = "#F59E0B",
                            orderIndex = 0,
                            children = listOf(
                                BranchNode(
                                    id = "n_l1",
                                    parentId = b3aId,
                                    title = "整理測試回饋清單",
                                    color = "#F59E0B",
                                    orderIndex = 0,
                                    status = NodeStatus.PLAN,
                                ),
                                BranchNode(
                                    id = "n_l2",
                                    parentId = b3aId,
                                    title = "正式版上架檢核",
                                    color = "#F59E0B",
                                    orderIndex = 1,
                                    status = NodeStatus.PLAN,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        return BranchBoard(
            id = "proj_starter",
            userId = userId,
            schemaVersion = 3,
            title = "Branch 產品路線圖",
            activeMode = WorkMode.DRAFT,
            activeView = CanvasViewType.TRUNK,
            trunkRevealLevel = 2,
            root = root,
        )
    }
}
