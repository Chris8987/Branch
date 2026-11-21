package com.example

import com.example.data.model.BranchNode
import com.example.data.model.NodeStatus
import com.example.data.model.NodeVariant
import com.example.data.model.TreeOperations
import com.example.domain.layout.BranchLayoutEngine
import com.example.domain.layout.DragDropTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchCoreUnitTest {

    private fun starterRoot(): BranchNode = TreeOperations.createStarterBoard("u1").root

    @Test
    fun `strict bottom-up leaf rollup counts only active leaf nodes and ignores parent status`() {
        val root = starterRoot()
        // Starter board leaves:
        // Phase 1: branch_research (2 DONE) + branch_position (1 DONE, 1 DO) = 3/4
        // Phase 2: branch_interaction (1 DONE, 1 DO, 1 PLAN) + branch_visual (2 PLAN) = 1/5
        // Phase 3: branch_beta (2 PLAN) = 0/2
        // Total active leaves across project = 11, done = 4
        val projectRollup = TreeOperations.calculateLeafProgressRollup(root, treatEmptySelfAsLeaf = false)
        assertEquals(11, projectRollup.totalCount)
        assertEquals(4, projectRollup.doneCount)

        // Completed Branch ("branch_research") has 2/2 DONE -> isCompleted == true
        val completedBranch = TreeOperations.findNode(root, "branch_research")!!
        val branchRollup = TreeOperations.calculateLeafProgressRollup(completedBranch)
        assertEquals(2, branchRollup.totalCount)
        assertEquals(2, branchRollup.doneCount)
        assertTrue(branchRollup.isCompleted)

        // Even if a parent's own status field were manually set to DONE, active children drive the rollup strictly!
        val parentWithPendingChild = BranchNode(
            id = "p",
            parentId = "root",
            title = "Parent",
            status = NodeStatus.DONE,
            children = listOf(
                BranchNode(id = "c1", parentId = "p", title = "C1", status = NodeStatus.DONE),
                BranchNode(id = "c2", parentId = "p", title = "C2", status = NodeStatus.PLAN),
            ),
        )
        val rollup = TreeOperations.calculateLeafProgressRollup(parentWithPendingChild)
        assertEquals(2, rollup.totalCount)
        assertEquals(1, rollup.doneCount)
        assertFalse(rollup.isCompleted)
    }

    @Test
    fun `foolproof leaf lock allows toggling only active leaf nodes and locks parents with active children`() {
        val root = starterRoot()
        val phase1 = TreeOperations.findNode(root, "phase_explore")!!
        val branchResearch = TreeOperations.findNode(root, "branch_research")!!
        val leafTask = TreeOperations.findNode(root, "n_p2")!!

        assertFalse("Phase with active branches must be locked", TreeOperations.isInteractiveLeaf(phase1))
        assertFalse("Branch with active leaf children must be locked", TreeOperations.isInteractiveLeaf(branchResearch))
        assertTrue("Active bottom-most leaf must be interactive", TreeOperations.isInteractiveLeaf(leafTask))

        // Strikethrough leaf is also non-interactive until restored
        val struckRoot = TreeOperations.toggleStrikethroughCascading(root, "n_p2")
        val struckLeaf = TreeOperations.findNode(struckRoot, "n_p2")!!
        assertFalse("Strikethrough leaf must not be interactive", TreeOperations.isInteractiveLeaf(struckLeaf))
    }

    @Test
    fun `cascading strikethrough excludes abandoned branches from rollup so parent reaches 100 percent`() {
        val root = starterRoot()
        // In branch_position: n_p1 is DONE, n_p2 is DO (50%).
        // Striking through n_p2 removes it from rollup -> branch_position immediately reaches 100% (1/1)!
        val updated = TreeOperations.toggleStrikethroughCascading(root, "n_p2")
        val branchPos = TreeOperations.findNode(updated, "branch_position")!!
        val rollup = TreeOperations.calculateLeafProgressRollup(branchPos)
        assertEquals(1, rollup.totalCount)
        assertEquals(1, rollup.doneCount)
        assertTrue("Abandoning the only remaining task allows Branch to reach 100%", rollup.isCompleted)

        // Striking through an entire parent cascades to all descendants
        val struckPhase = TreeOperations.toggleStrikethroughCascading(root, "phase_build")
        assertTrue(TreeOperations.findNode(struckPhase, "phase_build")!!.isStrikethrough)
        assertTrue(TreeOperations.findNode(struckPhase, "branch_interaction")!!.isStrikethrough)
        assertTrue(TreeOperations.findNode(struckPhase, "n_i1")!!.isStrikethrough)
    }

    @Test
    fun `trunk overview layout arranges phases horizontally and mounts branches above and below`() {
        val root = starterRoot()
        val overview = BranchLayoutEngine.computeTrunkOverviewLayout(root)
        assertEquals(3, overview.phases.size)

        // Phases are strictly ordered left-to-right without horizontal overlap
        for (i in 0 until overview.phases.lastIndex) {
            assertTrue(overview.phases[i].rightX < overview.phases[i + 1].x)
        }

        // Phase 1 has 2 branches: 1st mounted above spine, 2nd mounted below spine
        val p1 = overview.phases[0]
        assertEquals(1, p1.aboveBranches.size)
        assertEquals(1, p1.belowBranches.size)
        assertTrue(p1.aboveBranches[0].y + p1.aboveBranches[0].height < p1.y)
        assertTrue(p1.belowBranches[0].y > p1.bottomY)

        // Completed branch pill reports isCompleted == true and internal node count == 2
        assertTrue(p1.aboveBranches[0].progress.isCompleted)
        assertEquals(2, p1.aboveBranches[0].internalNodeCount)
    }

    @Test
    fun `state gradient index propagates from parent variant to all descendant branches`() {
        val child = BranchNode(id = "ch1", parentId = "p1", title = "Child", orderIndex = 0)
        val parent = BranchNode(
            id = "p1",
            parentId = "b_root",
            title = "Parent",
            orderIndex = 0,
            variants = listOf(
                NodeVariant(id = "vA", label = "狀態 A", snapshotId = "sA", gradientIndex = 0, isDefault = true),
                NodeVariant(id = "vB", label = "狀態 B", snapshotId = "sB", gradientIndex = 3, isDefault = false),
            ),
            activeVariantId = "vB",
            children = listOf(child),
        )
        val branchRoot = BranchNode(id = "b_root", parentId = "phase_1", title = "Branch", orderIndex = 0, children = listOf(parent))

        val layout = BranchLayoutEngine.computeMindMapLayout(branchRoot, isExecutionMode = false)
        val parentLayout = layout.nodes.first { it.node.id == "p1" }
        val childLayout = layout.nodes.first { it.node.id == "ch1" }
        assertEquals(3, parentLayout.stateGradientIndex)
        assertEquals(3, childLayout.stateGradientIndex)
    }

    @Test
    fun `mind map layout enforces non-overlapping dynamic push across sibling branches`() {
        val branch = TreeOperations.findNode(starterRoot(), "branch_interaction")!!
        val layout = BranchLayoutEngine.computeMindMapLayout(branch, isExecutionMode = true)
        assertEquals(3, layout.swimlanes.size)

        for (i in 0 until layout.swimlanes.lastIndex) {
            val upper = layout.swimlanes[i]
            val lower = layout.swimlanes[i + 1]
            assertTrue(
                "Swimlane $i bottom (${upper.bottom}) must be above swimlane ${i + 1} top (${lower.top})",
                upper.bottom <= lower.top,
            )
        }
    }

    @Test
    fun `deep clone children produces distinct IDs while preserving full hierarchy`() {
        val branch = TreeOperations.findNode(starterRoot(), "branch_interaction")!!
        val cloned = TreeOperations.deepCloneChildren(branch.children, parentId = branch.id)

        assertEquals(branch.children.size, cloned.size)
        assertEquals(branch.children[0].title, cloned[0].title)
        assertNotEquals(branch.children[0].id, cloned[0].id)
        assertEquals(branch.id, cloned[0].parentId)
    }

    @Test
    fun `dual drop target resolves center reparent and gap sibling reorder within branch mind map`() {
        val branch = TreeOperations.findNode(starterRoot(), "branch_interaction")!!
        val layout = BranchLayoutEngine.computeMindMapLayout(branch, isExecutionMode = false)
        val targetNode = layout.nodes.first { it.node.id == "n_i2" }

        val centerEval = BranchLayoutEngine.evaluateDrag(
            root = branch,
            layout = layout,
            draggedNodeId = "n_i1",
            pointerWorldX = targetNode.centerX,
            pointerWorldY = targetNode.centerY,
        )
        assertTrue(centerEval.dropTarget is DragDropTarget.Reparent)
        assertEquals("n_i2", (centerEval.dropTarget as DragDropTarget.Reparent).targetNodeId)

        val topGapEval = BranchLayoutEngine.evaluateDrag(
            root = branch,
            layout = layout,
            draggedNodeId = "n_i1",
            pointerWorldX = targetNode.centerX,
            pointerWorldY = targetNode.y - 4f,
        )
        assertTrue(topGapEval.dropTarget is DragDropTarget.SiblingGap)
        assertEquals("branch_interaction", (topGapEval.dropTarget as DragDropTarget.SiblingGap).targetParentId)
    }

    @Test
    fun `status cycle advances strictly PLAN to DO to DONE to PLAN`() {
        assertEquals(NodeStatus.DO, NodeStatus.PLAN.next())
        assertEquals(NodeStatus.DONE, NodeStatus.DO.next())
        assertEquals(NodeStatus.PLAN, NodeStatus.DONE.next())
    }
}
