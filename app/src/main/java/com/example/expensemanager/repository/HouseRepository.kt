package com.example.expensemanager.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.expensemanager.model.House
import com.example.expensemanager.model.Member
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
class HouseRepository {
    private val db  = Firebase.firestore
    private val uid get() = Firebase.auth.currentUser!!.uid
    suspend fun createHouse(name: String, address: String): Result<House> {
        return try {
            val code = (100000..999999).random().toString()
            val house = House(name = name, address = address,
                adminId = uid, memberIds = listOf(uid),
                inviteCode = code, createdAt = Timestamp.now())
            val ref = db.collection("houses").add(house).await()
            val finalHouse = house.copy(id = ref.id)
            ref.set(finalHouse).await()
            db.collection("members").document(uid)
                .update("houseId", ref.id).await()
            Result.success(finalHouse)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to create house: ${e.message}"))
        }
    }
    suspend fun joinHouse(inviteCode: String): Result<House> {
        return try {
            val snap = db.collection("houses")
                .whereEqualTo("inviteCode", inviteCode.trim().uppercase())
                .get().await()
            if (snap.isEmpty) return Result.failure(Exception("Invalid invite code"))
            val house = snap.documents[0].toObject(House::class.java)!!
            if (uid in house.memberIds)
                return Result.failure(Exception("You are already a member of this house"))
            db.collection("houses").document(house.id)
                .update("memberIds", FieldValue.arrayUnion(uid)).await()
            db.collection("members").document(uid)
                .update("houseId", house.id).await()
            Result.success(house)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to join house"))
        }
    }
    fun listenHouse(houseId: String): Flow<House?> = callbackFlow {
        val reg = db.collection("houses").document(houseId)
            .addSnapshotListener { snap, _ -> trySend(snap?.toObject(House::class.java)) }
        awaitClose { reg.remove() }
    }
    fun listenMembers(houseId: String): Flow<List<Member>> = callbackFlow {
        val reg = db.collection("members").whereEqualTo("houseId", houseId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObjects(Member::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }
    suspend fun getMemberProfile(uid: String): Member? {
        return try {
            db.collection("members").document(uid).get().await()
                .toObject(Member::class.java)
        } catch (e: Exception) { null }
    }
}