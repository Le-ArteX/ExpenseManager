package com.example.expensemanager.model
import com.google.firebase.Timestamp

data class House(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val adminId: String = "",
    val memberIds: List<String> = emptyList(),
    val inviteCode: String = "",
    val createdAt: Timestamp? = null
)