package com.example.expensemanager.model

import com.google.firebase.Timestamp

data class Notification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "INFO", // INFO, BILL, VAULT
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val houseId: String = ""
)
