package com.example.expensemanager.repository

import android.util.Log
import com.example.expensemanager.BuildConfig
import com.example.expensemanager.model.WarrantyItem
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

class VaultRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    
    // Fallback credentials hardcoded to ensure it works even if local.properties fails
    // Using .contains("localhost") to handle ports like :54321
    private val SUPABASE_URL = if (BuildConfig.SUPABASE_URL.isNotBlank() && !BuildConfig.SUPABASE_URL.contains("localhost")) {
        BuildConfig.SUPABASE_URL
    } else "https://ynlcigkmpdfgswsvntlw.supabase.co"

    private val SUPABASE_KEY = if (BuildConfig.SUPABASE_KEY.isNotBlank()) {
        BuildConfig.SUPABASE_KEY
    } else "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlubGNpZ2ttcGRmZ3N3c3ZudGx3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg2MDgxMDMsImV4cCI6MjA5NDE4NDEwM30.85Ua3ncbml5WF796yPmLznl8UcDLnR4i3M5NLnJ-Bio"

    // Initialize Supabase Client
    private val supabase = try {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Storage)
        }
    } catch (e: Exception) {
        Log.e("VaultRepo", "Supabase Init failed: ${e.message}")
        null
    }

    private val uid get() = auth.currentUser?.uid ?: "anonymous"
    
    private fun col(houseId: String) =
        db.collection("houses").document(houseId.trim()).collection("warrantyVault")

    /**
     * Uploads a receipt image to Supabase Storage and returns its public URL.
     */
    suspend fun uploadReceiptImage(houseId: String, imageFile: File): Result<String> {
        val client = supabase ?: return Result.failure(
            Exception("Supabase client not initialized. Check your credentials.")
        )

        val cleanHouseId = houseId.trim()
        if (cleanHouseId.isEmpty() || cleanHouseId == "null") {
            return Result.failure(Exception("Invalid House ID. Please join a house first."))
        }
        
        if (!imageFile.exists()) return Result.failure(Exception("File not found: ${imageFile.absolutePath}"))

        return try {
            val fileName = "receipt_${System.currentTimeMillis()}_${uid}.jpg"
            val bucketName = "receipts" // Ensure this bucket exists and is public in Supabase
            val path = "$cleanHouseId/$fileName"
            
            Log.d("VaultRepo", "Uploading to Supabase: $path")
            
            val bucket = client.storage.from(bucketName)
            
            // Upload the file as ByteArray
            bucket.upload(path, imageFile.readBytes()) {
                upsert = true
            }
            
            // Get the Public URL
            val publicUrl = bucket.publicUrl(path)
            
            Log.d("VaultRepo", "Supabase Upload Success: $publicUrl")
            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e("VaultRepo", "Supabase Upload failed", e)
            Result.failure(Exception("Supabase Storage Error: ${e.localizedMessage}"))
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
