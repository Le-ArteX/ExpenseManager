package com.example.expensemanager.model

import com.google.firebase.Timestamp
data class Bill(
    val id: String = "",
    val assetId: String = "",
    val houseId: String = "",
    val assetName: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val dueDate: Timestamp? = null,
    val status: String = "PENDING",
    val paidBy: Map<String, Boolean> = emptyMap(),
    val createdAt: Timestamp? = null,
    val lastUpdatedBy: String = "" // Added to track who made the change
) {
    fun isPaidBy(uid: String) = paidBy[uid] == true
    fun allPaid() = paidBy.values.all { it }
    fun paidCount() = paidBy.values.count { it }
    fun totalCount() = paidBy.size
    fun perPersonAmount() = if (totalCount() > 0) amount / totalCount() else amount
}
