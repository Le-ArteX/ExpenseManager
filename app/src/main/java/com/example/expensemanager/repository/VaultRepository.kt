package com.example.expensemanager.repository

import androidx.core.net.toUri
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.example.expensemanager.model.WarrantyItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
class VaultRepository {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val uid get() = Firebase.auth.currentUser?.uid ?: ""
    private fun col(houseId: String) =
        db.collection("houses").document(houseId).collection("warrantyVault")

    suspend fun uploadReceiptImage(houseId: String, imageFile: File): Result<String> {
        return try {
            val ref = storage.reference
                .child("receipts/$houseId/${uid}_${System.currentTimeMillis()}.jpg")
            val task = ref.putFile(imageFile.toUri()).await()
            val url = task.storage.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(Exception("Upload failed: ${e.message}"))
        }
    }

    suspend fun saveWarrantyItem(houseId: String, item: WarrantyItem): Result<WarrantyItem> {
        return try {
            val ref = col(houseId).add(item.copy(addedBy = uid)).await()
            val final = item.copy(id = ref.id, houseId = houseId, addedBy = uid)
            ref.set(final).await()
            Result.success(final)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save: ${e.message}"))
        }
    }

    suspend fun addReceiptToItem(houseId: String, itemId: String, imageFile: File): Result<String> {
        return try {
            val urlResult = uploadReceiptImage(houseId, imageFile)
            if (!urlResult.isSuccess) return urlResult
            val url = urlResult.getOrThrow()
            col(houseId).document(itemId)
                .update("receiptImageUrls", FieldValue.arrayUnion(url)).await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to add receipt: ${e.message}"))
        }
    }

    fun getWarrantyItems(houseId: String): Flow<List<WarrantyItem>> = callbackFlow {
        val reg = col(houseId).orderBy("purchaseDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                trySend(snap?.toObjects(WarrantyItem::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun deleteItem(houseId: String, itemId: String): Result<Unit> {
        return try {
            col(houseId).document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Delete failed: ${e.message}"))
        }
    }
}
