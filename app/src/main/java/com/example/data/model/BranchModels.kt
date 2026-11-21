package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.UUID

enum class NodeStatus {
    PLAN,
    DO,
    DONE;

    fun next(): NodeStatus = when (this) {
        PLAN -> DO
        DO -> DONE
        DONE -> PLAN
    }

    companion object {
        fun fromRaw(raw: Any?): NodeStatus {
            val name = raw as? String ?: return PLAN
            return entries.firstOrNull { it.name == name } ?: PLAN
        }
    }
}

enum class WorkMode {
    DRAFT,
    EXECUTION;

    companion object {
        fun fromRaw(raw: Any?): WorkMode {
            val name = raw as? String ?: return DRAFT
            return entries.firstOrNull { it.name == name } ?: DRAFT
        }
    }
}

enum class CanvasViewType {
    MIND_MAP,
    TRUNK;

    fun cycleNext(): CanvasViewType = when (this) {
        MIND_MAP -> TRUNK
        TRUNK -> MIND_MAP
    }

    companion object {
        fun fromRaw(raw: Any?): CanvasViewType {
            val name = raw as? String ?: return MIND_MAP
            return entries.firstOrNull { it.name == name } ?: MIND_MAP
        }
    }
}

@Immutable
data class NodeVariant(
    val id: String,
    val label: String,
    val snapshotId: String,
    val gradientIndex: Int = 0,
    val isDefault: Boolean = false,
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "label" to label,
        "snapshotId" to snapshotId,
        "gradientIndex" to gradientIndex,
        "isDefault" to isDefault,
    )

    companion object {
        fun fromFirestoreMap(map: Map<*, *>, fallbackIndex: Int = 0): NodeVariant? {
            val id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val label = (map["label"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val snapshotId = (map["snapshotId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val gradientIndex = (map["gradientIndex"] as? Number)?.toInt() ?: fallbackIndex
            val isDefault = (map["isDefault"] as? Boolean) ?: (fallbackIndex == 0)
            return NodeVariant(
                id = id,
                label = label,
                snapshotId = snapshotId,
                gradientIndex = gradientIndex,
                isDefault = isDefault,
            )
        }
    }
}

@Immutable
data class BranchNode(
    val id: String,
    val parentId: String? = null,
    val title: String,
    val notes: String = "",
    val resultLog: String = "",
    val color: String = "#2563EB",
    val orderIndex: Int = 0,
    val status: NodeStatus = NodeStatus.PLAN,
    val isCollapsed: Boolean = false,
    val isPinned: Boolean = false,
    val isStrikethrough: Boolean = false,
    val variants: List<NodeVariant> = emptyList(),
    val activeVariantId: String? = null,
    val children: List<BranchNode> = emptyList(),
) {
    fun activeVariant(): NodeVariant? {
        if (variants.isEmpty()) return null
        return variants.firstOrNull { it.id == activeVariantId }
            ?: variants.firstOrNull { it.isDefault }
            ?: variants.first()
    }

    /**
     * Serializes to standard hierarchical JSON compatible with Firestore native Maps/Lists
     * and future cross-platform iPad PWA clients.
     */
    fun toFirestoreMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any>(
            "id" to id,
            "title" to title.take(200),
            "notes" to notes.take(10_000),
            "resultLog" to resultLog.take(500),
            "color" to color.ifBlank { "#2563EB" }.take(16),
            "orderIndex" to orderIndex.coerceIn(0, 10_000),
            "status" to status.name,
            "isCollapsed" to isCollapsed,
            "isPinned" to isPinned,
            "isStrikethrough" to isStrikethrough,
            "variants" to variants.take(32).map { it.toFirestoreMap() },
            "children" to children.sortedBy { it.orderIndex }.map { it.toFirestoreMap() },
        )
        if (!parentId.isNullOrBlank()) {
            raw["parentId"] = parentId
        }
        if (!activeVariantId.isNullOrBlank()) {
            raw["activeVariantId"] = activeVariantId
        }
        return raw
    }

    companion object {
        fun fromFirestoreMap(map: Map<*, *>, fallbackParentId: String? = null): BranchNode {
            val id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: generateBranchId("node")
            val parentId = (map["parentId"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackParentId
            val title = (map["title"] as? String) ?: ""
            val notes = (map["notes"] as? String) ?: ""
            val resultLog = (map["resultLog"] as? String) ?: ""
            val color = (map["color"] as? String)?.takeIf { it.isNotBlank() } ?: "#2563EB"
            val orderIndex = (map["orderIndex"] as? Number)?.toInt() ?: 0
            val status = NodeStatus.fromRaw(map["status"])
            val isCollapsed = (map["isCollapsed"] as? Boolean) ?: false
            val isPinned = (map["isPinned"] as? Boolean) ?: false
            val isStrikethrough = (map["isStrikethrough"] as? Boolean) ?: false
            val variantsRaw = map["variants"] as? List<*> ?: emptyList<Any>()
            val variants = variantsRaw.mapIndexedNotNull { idx, item ->
                (item as? Map<*, *>)?.let { NodeVariant.fromFirestoreMap(it, idx) }
            }
            val activeVariantId = (map["activeVariantId"] as? String)?.takeIf { it.isNotBlank() }
            val childrenRaw = map["children"] as? List<*> ?: emptyList<Any>()
            val children = childrenRaw
                .mapNotNull { item -> (item as? Map<*, *>)?.let { fromFirestoreMap(it, id) } }
                .sortedBy { it.orderIndex }

            return BranchNode(
                id = id,
                parentId = parentId,
                title = title,
                notes = notes,
                resultLog = resultLog,
                color = color,
                orderIndex = orderIndex,
                status = status,
                isCollapsed = isCollapsed,
                isPinned = isPinned,
                isStrikethrough = isStrikethrough,
                variants = variants,
                activeVariantId = activeVariantId,
                children = children,
            )
        }
    }
}

@Immutable
data class BranchBoard(
    val id: String = generateBranchId("proj"),
    val userId: String = "",
    val schemaVersion: Int = 2,
    val title: String = "新專案",
    val activeMode: WorkMode = WorkMode.DRAFT,
    val activeView: CanvasViewType = CanvasViewType.MIND_MAP,
    val trunkRevealLevel: Int = 2,
    val root: BranchNode,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "schemaVersion" to schemaVersion.coerceIn(1, 100),
        "title" to title.ifBlank { "Branch" }.take(120),
        "activeMode" to activeMode.name,
        "activeView" to activeView.name,
        "trunkRevealLevel" to trunkRevealLevel.coerceIn(0, 50),
        "root" to root.toFirestoreMap(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "schemaVersion" to schemaVersion.coerceIn(1, 100),
        "title" to title.ifBlank { "Branch" }.take(120),
        "activeMode" to activeMode.name,
        "activeView" to activeView.name,
        "trunkRevealLevel" to trunkRevealLevel.coerceIn(0, 50),
        "root" to root.toFirestoreMap(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): BranchBoard? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val rootMap = data["root"] as? Map<*, *> ?: return null
            return BranchBoard(
                id = doc.id,
                userId = (data["userId"] as? String) ?: "",
                schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 1,
                title = (data["title"] as? String)?.ifBlank { "Branch" } ?: "Branch",
                activeMode = WorkMode.fromRaw(data["activeMode"]),
                activeView = CanvasViewType.fromRaw(data["activeView"]),
                trunkRevealLevel = (data["trunkRevealLevel"] as? Number)?.toInt() ?: 2,
                root = BranchNode.fromFirestoreMap(rootMap, null),
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            )
        }
    }
}

@Immutable
data class VariantSnapshot(
    val id: String,
    val userId: String,
    val ownerNodeId: String,
    val variantId: String,
    val label: String,
    val children: List<BranchNode>,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "ownerNodeId" to ownerNodeId,
        "variantId" to variantId,
        "label" to label.ifBlank { "狀態" }.take(60),
        "children" to children.sortedBy { it.orderIndex }.map { it.toFirestoreMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "label" to label.ifBlank { "狀態" }.take(60),
        "children" to children.sortedBy { it.orderIndex }.map { it.toFirestoreMap() },
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): VariantSnapshot? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val ownerNodeId = (data["ownerNodeId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val variantId = (data["variantId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val label = (data["label"] as? String)?.takeIf { it.isNotBlank() } ?: "狀態"
            val childrenRaw = data["children"] as? List<*> ?: emptyList<Any>()
            val children = childrenRaw
                .mapNotNull { item -> (item as? Map<*, *>)?.let { BranchNode.fromFirestoreMap(it, ownerNodeId) } }
                .sortedBy { it.orderIndex }

            return VariantSnapshot(
                id = doc.id,
                userId = (data["userId"] as? String) ?: "",
                ownerNodeId = ownerNodeId,
                variantId = variantId,
                label = label,
                children = children,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            )
        }
    }
}

fun generateBranchId(prefix: String = "n"): String {
    val token = UUID.randomUUID().toString().replace("-", "").take(12)
    return "${prefix}_$token"
}
