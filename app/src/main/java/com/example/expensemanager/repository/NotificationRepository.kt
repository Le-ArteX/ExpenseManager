package com.example.expensemanager.repository

import com.example.expensemanager.model.Notification
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {
    private val db = Firebase.firestore

    private fun col(houseId: String) =
        db.collection("houses").document(houseId).collection("notifications")

    suspend fun addNotification(houseId: String, notification: Notification): Result<Unit> {
        return try {
            val ref = col(houseId).document()
            ref.set(notification.copy(id = ref.id)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNotifications(houseId: String): Flow<List<Notification>> = callbackFlow {
        if (houseId.isBlank()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = col(houseId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val notifications = snap?.toObjects(Notification::class.java) ?: emptyList()
                trySend(notifications)
            }
        awaitClose { listener.remove() }
    }

    suspend fun markAsRead(houseId: String, notificationId: String) {
        try {
            col(houseId).document(notificationId).update("isRead", true).await()
        } catch (e: Exception) {

        }
    }

    suspend fun deleteNotification(houseId: String, notificationId: String): Result<Unit> {
        return try {
            col(houseId).document(notificationId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
