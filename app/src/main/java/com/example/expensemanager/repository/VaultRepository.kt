package com.example.expensemanager.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.example.expensemanager.model.WarrantyItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

class VaultRepository {
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = Firebase.auth
    
    private val uid get() = auth.currentUser?.uid ?: "anonymous"
    
    private fun col(houseId: String) =
        db.collection("houses").document(houseId.trim()).collection("warrantyVault")

    /**
     * Uploads a receipt image and returns its download URL.
     * Fixed the "Object does not exist" error by polling for the URL after a successful upload.
     */
    suspend fun uploadReceiptImage(houseId: String, imageFile: File): Result<String> {
        val cleanHouseId = houseId.trim()
        if (cleanHouseId.isEmpty() || cleanHouseId == "null") {
            return Result.failure(Exception("Invalid House ID. Please join a house first."))
        }
        
        if (!imageFile.exists()) return Result.failure(Exception("Captured file not found."))

        return try {
            val fileName = "receipt_${System.currentTimeMillis()}_${uid}.jpg"
            val ref = storage.reference.child("receipts").child(cleanHouseId).child(fileName)
            
            Log.d("VaultRepo", "Uploading to Storage: ${ref.path}")
            
            // 1. Perform the upload
            ref.putFile(Uri.fromFile(imageFile)).await()
            
            // 2. Poll for the download URL (Firebase Storage can be eventually consistent)
            var downloadUrl: String? = null
            var lastException: Exception? = null
            
            for (i in 1..5) {
                try {
                    // Try getting URL from the reference
                    downloadUrl = ref.downloadUrl.await().toString()
                    if (downloadUrl != null) break
                } catch (e: Exception) {
                    lastException = e
                    Log.w("VaultRepo", "URL fetch attempt $i failed: ${e.message}")
                    delay(1000L * i) // Incremental delay: 1s, 2s, 3s...
                }
            }

            if (downloadUrl != null) {
                Log.d("VaultRepo", "Upload Success: $downloadUrl")
                Result.success(downloadUrl)
            } else {
                Result.failure(lastException ?: Exception("Uploaded but could not retrieve URL."))
            }
        } catch (e: Exception) {
            Log.e("VaultRepo", "Upload failed completely", e)
            Result.failure(e)
        }
    }

    suspend fun saveWarrantyItem(houseId: String, item: WarrantyItem): Result<WarrantyItem> {
        return try {
            val ref = col(houseId).add(item.copy(addedBy = uid)).await()
            val final = item.copy(id = ref.id, houseId = houseId, addedBy = uid)
            ref.set(final).await()
            Result.success(final)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save item: ${e.message}"))
        }
    }

    suspend fun addReceiptToItem(houseId: String, itemId: String, imageFile: File): Result<String> {
        val urlResult = uploadReceiptImage(houseId, imageFile)
        if (urlResult.isFailure) return urlResult
        
        val url = urlResult.getOrThrow()
        return try {
            col(houseId).document(itemId)
                .update("receiptImageUrls", FieldValue.arrayUnion(url)).await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to link receipt: ${e.message}"))
        }
    }

    fun getWarrantyItems(houseId: String): Flow<List<WarrantyItem>> = callbackFlow {
        if (houseId.isBlank()) {
            trySend(emptyList())
            return@callbackFlow
        }
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
