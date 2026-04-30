package com.example.expensemanager.model
import com.google.firebase.Timestamp

data class Member(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val fcmToken: String = "",
    val houseId: String = "",
    val joinedAt: Timestamp? = null
)