package com.example.expensemanager.model
import com.google.firebase.Timestamp
import java.util.Date
data class WarrantyItem(
    val id: String = "",
    val houseId: String = "",
    val itemName: String = "",
    val brand: String = "",
    val purchaseDate: Timestamp? = null,
    val warrantyExpiryDate: Timestamp? = null,
    val purchasePrice: Double = 0.0,
    val receiptImageUrls: List<String> = emptyList(),
    val notes: String = "",
    val addedBy: String = ""
) {
    fun warrantyStatus(): String {
        val expiry = warrantyExpiryDate?.toDate() ?: return "EXPIRED"
        val now = Date()
        val daysLeft = ((expiry.time - now.time) / (1000 * 60 * 60 * 24)).toInt()
        return when {
            daysLeft < 0  -> "EXPIRED"
            daysLeft <= 30 -> "EXPIRING"
            else           -> "VALID"
        }
    }
    fun daysUntilExpiry(): Int {
        val expiry = warrantyExpiryDate?.toDate() ?: return -1
        return ((expiry.time - Date().time) / (1000 * 60 * 60 * 24)).toInt()
    }
}