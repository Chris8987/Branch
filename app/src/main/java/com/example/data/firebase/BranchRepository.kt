package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.BranchBoard
import com.example.data.model.VariantSnapshot
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BranchRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user mismatch; refusing cross-user Firestore operation." }
        return uid
    }

    private fun boardsColRef(userId: String) =
        db.collection("users").document(userId).collection("boards")

    private fun boardDocRef(userId: String, boardId: String) =
        boardsColRef(userId).document(boardId)

    private fun snapshotsColRef(userId: String, boardId: String) =
        boardDocRef(userId, boardId).collection("snapshots")

    /**
     * Multi-Project Hub stream: observes all Branch projects owned by [userId], newest first.
     */
    fun observeAllBoards(userId: String): Flow<List<BranchBoard>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/boards"
        emitAll(
            boardsColRef(uid)
                .snapshots()
                .map { querySnapshot ->
                    querySnapshot.documents
                        .mapNotNull { BranchBoard.fromSnapshot(it) }
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBoard(userId: String, boardId: String): Flow<BranchBoard?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/boards/$boardId"
        emitAll(
            boardDocRef(uid, boardId)
                .snapshots()
                .map { snapshot -> BranchBoard.fromSnapshot(snapshot) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeSnapshots(userId: String, boardId: String): Flow<Map<String, VariantSnapshot>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/boards/$boardId/snapshots"
        emitAll(
            snapshotsColRef(uid, boardId)
                .snapshots()
                .map { querySnapshot ->
                    querySnapshot.documents
                        .mapNotNull { VariantSnapshot.fromSnapshot(it) }
                        .associateBy { it.id }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createBoard(userId: String, board: BranchBoard): Result<Unit> {
        val path = "users/$userId/boards/${board.id}"
        return try {
            val uid = requireUserId(userId)
            boardDocRef(uid, board.id).set(board.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameBoard(userId: String, boardId: String, newTitle: String): Result<Unit> {
        val path = "users/$userId/boards/$boardId"
        return try {
            val uid = requireUserId(userId)
            val clean = newTitle.trim().ifBlank { "未命名專案" }.take(120)
            boardDocRef(uid, boardId).update(
                mapOf(
                    "title" to clean,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBoard(userId: String, boardId: String): Result<Unit> {
        val path = "users/$userId/boards/$boardId"
        return try {
            val uid = requireUserId(userId)
            val snapDocs = snapshotsColRef(uid, boardId).get().await().documents
            val batch = db.batch()
            for (snap in snapDocs) {
                batch.delete(snap.reference)
            }
            batch.delete(boardDocRef(uid, boardId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveBoard(userId: String, board: BranchBoard): Result<Unit> {
        val path = "users/$userId/boards/${board.id}"
        return try {
            val uid = requireUserId(userId)
            val docRef = boardDocRef(uid, board.id)
            if (board.createdAt == null) {
                val existing = docRef.get().await()
                if (existing.exists()) {
                    docRef.update(board.toUpdateMap()).await()
                } else {
                    docRef.set(board.toCreateMap(uid)).await()
                }
            } else {
                docRef.update(board.toUpdateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically writes/deletes state variant snapshots together with the updated board root.
     */
    suspend fun commitVariantMutation(
        userId: String,
        board: BranchBoard,
        snapshotsToCreate: List<VariantSnapshot>,
        snapshotsToUpdate: List<VariantSnapshot>,
        snapshotsToDelete: List<String> = emptyList(),
    ): Result<Unit> {
        val path = "users/$userId/boards/${board.id}"
        return try {
            val uid = requireUserId(userId)
            val boardRef = boardDocRef(uid, board.id)
            val batch = db.batch()

            if (board.createdAt == null) {
                batch.set(boardRef, board.toCreateMap(uid))
            } else {
                batch.update(boardRef, board.toUpdateMap())
            }

            for (snap in snapshotsToCreate) {
                val snapRef = snapshotsColRef(uid, board.id).document(snap.id)
                batch.set(snapRef, snap.toCreateMap(uid))
            }
            for (snap in snapshotsToUpdate) {
                val snapRef = snapshotsColRef(uid, board.id).document(snap.id)
                batch.update(snapRef, snap.toUpdateMap())
            }
            for (snapId in snapshotsToDelete) {
                val snapRef = snapshotsColRef(uid, board.id).document(snapId)
                batch.delete(snapRef)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun createConfiguredFirestore(databaseId: String): FirebaseFirestore {
            val instance = FirebaseFirestore.getInstance(databaseId)
            runCatching {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                instance.firestoreSettings = settings
            }
            return instance
        }
    }
}
