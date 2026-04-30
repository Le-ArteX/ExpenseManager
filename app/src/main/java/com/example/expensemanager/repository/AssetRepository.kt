package com.example.expensemanager.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
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
    private val uid get() = Firebase.auth.currentUser!!.uid
    private fun assetsCol(houseId: String) =
        db.collection("houses").document(houseId).collection("assets")
    private fun billsCol(houseId: String) =
        db.collection("houses").document(houseId).collection("bills")
    //  CREATE
    suspend fun addAsset(houseId: String, asset: SharedAsset): Result<SharedAsset> {
        return try {
            val ref = assetsCol(houseId).add(asset.copy(houseId = houseId, createdBy = uid)).await()
            val finalAsset = asset.copy(id = ref.id, houseId = houseId, createdBy = uid)
            ref.set(finalAsset).await()
            createBillCycle(houseId, finalAsset)   // auto-schedule first bill
            Result.success(finalAsset)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to add asset: ${e.message}"))
        }
    }
    //  READ
    fun getAssets(houseId: String): Flow<List<SharedAsset>> = callbackFlow {
        val reg = assetsCol(houseId).whereEqualTo("isActive", true)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(SharedAsset::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }
    fun getPendingBills(houseId: String): Flow<List<Bill>> = callbackFlow {
        val reg = billsCol(houseId)
            .whereIn("status", listOf("PENDING","PARTIAL","OVERDUE"))
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Bill::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }
    fun getAllBills(houseId: String): Flow<List<Bill>> = callbackFlow {
        val reg = billsCol(houseId)
            .orderBy("dueDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Bill::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }
    //  UPDATE
    suspend fun markMemberPaid(houseId: String, billId: String, memberId: String): Result<Unit> {
        return try {
            val ref = billsCol(houseId).document(billId)
            ref.update("paidBy.$memberId", true).await()
            val bill = ref.get().await().toObject(Bill::class.java)
            if (bill != null) {
                val updated = bill.paidBy.toMutableMap().apply { put(memberId, true) }
                val newStatus = when {
                    updated.values.all { it } -> "PAID"
                    updated.values.any { it } -> "PARTIAL"
                    else -> "PENDING"
                }
                ref.update("status", newStatus).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
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
    //  HELPER: auto-create bill cycle
    private suspend fun createBillCycle(houseId: String, asset: SharedAsset) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, asset.dueDay.coerceIn(1, 28))
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.MONTH, 1)
        }
        val bill = Bill(
            assetId = asset.id, houseId = houseId,
            assetName = asset.name, category = asset.category,
            amount = asset.monthlyAmount,
            dueDate = Timestamp(cal.time),
            paidBy = asset.splitAmong.associateWith { false },
            status = "PENDING", createdAt = Timestamp.now()
        )
        val ref = billsCol(houseId).add(bill).await()
        billsCol(houseId).document(ref.id).update("id", ref.id).await()
    }
}