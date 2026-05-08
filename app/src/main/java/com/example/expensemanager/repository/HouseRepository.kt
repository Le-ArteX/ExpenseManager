package com.example.expensemanager.repository

import android.util.Log
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
    private val uid get() = Firebase.auth.currentUser?.uid ?: ""

    suspend fun createHouse(name: String, address: String): Result<House> {
        return try {
            val code = (100000..999999).random().toString()
            val houseRef = db.collection("houses").document()
            val house = House(
                id = houseRef.id,
                name = name, 
                address = address,
                adminId = uid, 
                memberIds = listOf(uid),
                inviteCode = code, 
                createdAt = Timestamp.now()
            )

            db.runBatch { batch ->
                batch.set(houseRef, house)
                batch.update(db.collection("members").document(uid), "houseId", houseRef.id)
            }.await()

            Result.success(house)
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
            
            db.runBatch { batch ->
                batch.update(db.collection("houses").document(house.id), "memberIds", FieldValue.arrayUnion(uid))
                batch.update(db.collection("members").document(uid), "houseId", house.id)
            }.await()
            
            Result.success(house)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Failed to join house"))
        }
    }

    suspend fun getHouse(houseId: String): House? {
        if (houseId.isEmpty()) return null
        return try {
            db.collection("houses").document(houseId).get().await()
                .toObject(House::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun listenHouse(houseId: String): Flow<House?> = callbackFlow {
        if (houseId.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val reg = db.collection("houses").document(houseId)
            .addSnapshotListener { snap, err -> 
                if (err != null) {
                    Log.e("HouseRepo", "Listen house error: ${err.message}")
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(House::class.java)) 
            }
        awaitClose { reg.remove() }
    }

    fun listenMembers(houseId: String): Flow<List<Member>> = callbackFlow {
        if (houseId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        // Top-level collection query is generally safe without manual index for single field
        val reg = db.collection("members").whereEqualTo("houseId", houseId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("HouseRepo", "Listen members error: ${err.message}")
                    return@addSnapshotListener
                }
                trySend(snap?.toObjects(Member::class.java) ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun getMemberProfile(uid: String): Member? {
        if (uid.isEmpty()) return null
        return try {
            db.collection("members").document(uid).get().await()
                .toObject(Member::class.java)
        } catch (e: Exception) { null }
    }
}
