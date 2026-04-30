package com.example.expensemanager.model
import com.google.firebase.Timestamp
data class SharedAsset(
    val id: String = "",
    val houseId: String = "",
    val name: String = "",
    val category: String = "SUBSCRIPTION",
    // Possible values: SUBSCRIPTION | UTILITY | MAINTENANCE | OTHER
    val monthlyAmount: Double = 0.0,
    val splitAmong: List<String> = emptyList(),  // list of member UIDs
    val dueDay: Int = 1,            // day of month (1-28)
    val notes: String = "",
    val isActive: Boolean = true,
    val createdBy: String = ""
) {
    /** Cost each member pays */
    fun perPersonAmount(): Double =
        if (splitAmong.isEmpty()) monthlyAmount
        else monthlyAmount / splitAmong.size
    /** Emoji icon for this category */
    fun categoryIcon(): String = when (category) {
        "SUBSCRIPTION"  -> " "
        "UTILITY"       -> " "
        "MAINTENANCE"   -> " "
        else            -> " "
    }
}