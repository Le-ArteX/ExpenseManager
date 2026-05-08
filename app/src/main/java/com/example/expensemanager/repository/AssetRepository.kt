package com.example.expensemanager.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.expensemanager.model.Bill
import com.example.expensemanager.model.SharedAsset
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class AssetRepository {
    private val db  = Firebase.firestore
    private val uid get() = Firebase.auth.currentUser?.uid ?: ""

    private fun assetsCol(houseId: String) =
        db.collection("houses").document(houseId).collection("assets")
    private fun billsCol(houseId: String) =
        db.collection("houses").document(houseId).collection("bills")

    //  CREATE
    suspend fun addAsset(houseId: String, asset: SharedAsset): Result<SharedAsset> {
        return try {
            val assetRef = assetsCol(houseId).document()
            val finalAsset = asset.copy(id = assetRef.id, houseId = houseId, createdBy = uid)
            assetRef.set(finalAsset).await()
            createBillCycle(houseId, finalAsset)   // auto-schedule first bill
            Result.success(finalAsset)
        } catch (e: Exception) {
            Log.e("AssetRepo", "Add asset error: ${e.message}")
            Result.failure(Exception("Failed to add asset: ${e.message}"))
        }
    }

    //  READ
    fun getAssets(houseId: String): Flow<List<SharedAsset>> = callbackFlow {
        if (houseId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val reg = assetsCol(houseId).whereEqualTo("isActive", true)
            .addSnapshotListener { snap, err ->
                if (err != null) { 
                    Log.e("AssetRepo", "Get assets error: ${err.message}")
                    return@addSnapshotListener 
                }
                trySend(snap?.toObjects(SharedAsset::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // Remove orderBy to avoid index requirement, sort on client-side
    fun getPendingBills(houseId: String): Flow<List<Bill>> = callbackFlow {
        if (houseId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val reg = billsCol(houseId)
            .addSnapshotListener { snap, err ->
                if (err != null) { 
                    Log.e("AssetRepo", "Get bills error: ${err.message}")
                    return@addSnapshotListener 
                }
                val bills = snap?.toObjects(Bill::class.java) ?: emptyList()
                // Filter and sort on client-side
                val pending = bills.filter { it.status in listOf("PENDING", "PARTIAL", "OVERDUE") }
                    .sortedBy { it.dueDate }
                trySend(pending)
            }
        awaitClose { reg.remove() }
    }

    fun getAllBills(houseId: String): Flow<List<Bill>> = callbackFlow {
        if (houseId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val reg = billsCol(houseId)
            .addSnapshotListener { snap, err ->
                if (err != null) { 
                    Log.e("AssetRepo", "Get all bills error: ${err.message}")
                    return@addSnapshotListener 
                }
                val bills = snap?.toObjects(Bill::class.java) ?: emptyList()
                trySend(bills.sortedByDescending { it.dueDate })
            }
        awaitClose { reg.remove() }
    }

    //  UPDATE
    suspend fun markMemberPaid(houseId: String, billId: String, memberId: String): Result<Unit> {
        return try {
            val ref = billsCol(houseId).document(billId)
            val snap = ref.get().await()
            val bill = snap.toObject(Bill::class.java) ?: throw Exception("Bill not found")
            
            val updatedPaidBy = bill.paidBy.toMutableMap()
            updatedPaidBy[memberId] = true
            
            val newStatus = when {
                updatedPaidBy.values.all { it } -> "PAID"
                updatedPaidBy.values.any { it } -> "PARTIAL"
                else -> "PENDING"
            }
            
            ref.update(
                mapOf(
                    "paidBy" to updatedPaidBy,
                    "status" to newStatus
                )
            ).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AssetRepo", "Mark paid error: ${e.message}")
            Result.failure(Exception("Failed to mark paid: ${e.message}"))
        }
    }

    suspend fun deactivateAsset(houseId: String, assetId: String): Result<Unit> {
        return try {
            assetsCol(houseId).document(assetId).update("isActive", false).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to remove: ${e.message}"))
        }
    }

    private suspend fun createBillCycle(houseId: String, asset: SharedAsset) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, asset.dueDay.coerceIn(1, 28))
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.MONTH, 1)
        }
        val billRef = billsCol(houseId).document()
        val bill = Bill(
            id = billRef.id,
            assetId = asset.id, 
            houseId = houseId,
            assetName = asset.name, 
            category = asset.category,
            amount = asset.monthlyAmount,
            dueDate = Timestamp(cal.time),
            paidBy = asset.splitAmong.associateWith { false },
            status = "PENDING", 
            createdAt = Timestamp.now()
        )
        billRef.set(bill).await()
    }
}
