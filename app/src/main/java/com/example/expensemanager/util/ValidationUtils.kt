package com.example.expensemanager.util

import android.util.Patterns
object ValidationUtils {
    fun validateEmail(email: String): ValidationResult = when {
        email.isBlank()  -> ValidationResult.Error("Email cannot be empty")
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
            ValidationResult.Error("Please enter a valid email address")
        else -> ValidationResult.Success
    }
    fun validatePassword(password: String): ValidationResult = when {
        password.isBlank()   -> ValidationResult.Error("Password cannot be empty")
        password.length < 6  -> ValidationResult.Error("Password must be at least 6 characters")
        password.length > 100 -> ValidationResult.Error("Password is too long")
        else -> ValidationResult.Success
    }
    fun validatePasswordMatch(pass: String, confirm: String): ValidationResult = when {
        confirm.isBlank() -> ValidationResult.Error("Please confirm your password")
        pass != confirm   -> ValidationResult.Error("Passwords do not match")
        else -> ValidationResult.Success
    }
    fun validateDisplayName(name: String): ValidationResult = when {
        name.isBlank()          -> ValidationResult.Error("Name cannot be empty")
        name.trim().length < 2  -> ValidationResult.Error("Name must be at least 2 characters")
        name.trim().length > 50 -> ValidationResult.Error("Name is too long (max 50 chars)")
        else -> ValidationResult.Success
    }
    fun validateAssetName(name: String): ValidationResult = when {
        name.isBlank()          -> ValidationResult.Error("Asset name cannot be empty")
        name.trim().length < 3  -> ValidationResult.Error("Name must be at least 3 characters")
        name.trim().length > 60 -> ValidationResult.Error("Name is too long (max 60 chars)")
        else -> ValidationResult.Success
    }
    fun validateAmount(amountStr: String): ValidationResult = when {
        amountStr.isBlank()              -> ValidationResult.Error("Amount cannot be empty")
        amountStr.toDoubleOrNull() == null -> ValidationResult.Error("Enter a valid number")
        amountStr.toDouble() <= 0        -> ValidationResult.Error("Amount must be greater than 0")
        amountStr.toDouble() > 1_000_000 -> ValidationResult.Error("Amount seems too large")
        else -> ValidationResult.Success
    }
    fun validateDueDay(dueDayStr: String): ValidationResult = when {
        dueDayStr.isBlank()             -> ValidationResult.Error("Due day cannot be empty")
        dueDayStr.toIntOrNull() == null  -> ValidationResult.Error("Enter a valid day number")
        dueDayStr.toInt() < 1 || dueDayStr.toInt() > 28 ->
            ValidationResult.Error("Day must be between 1 and 28")
        else -> ValidationResult.Success
    }
    fun validateHouseName(name: String): ValidationResult = when {
        name.isBlank()         -> ValidationResult.Error("House name cannot be empty")
        name.trim().length < 3 -> ValidationResult.Error("Name must be at least 3 characters")
        else -> ValidationResult.Success
    }
    fun validateInviteCode(code: String): ValidationResult = when {
        code.isBlank()         -> ValidationResult.Error("Invite code cannot be empty")
        code.trim().length != 6 -> ValidationResult.Error("Code must be exactly 6 characters")
        else -> ValidationResult.Success
    }
    fun validateMemberSelection(count: Int): ValidationResult = when {
        count == 0 -> ValidationResult.Error("Select at least one member to split with")
        else -> ValidationResult.Success
    }
    fun validateCategorySelected(category: String?): ValidationResult = when {
        category.isNullOrBlank() -> ValidationResult.Error("Please select a category")
        else -> ValidationResult.Success
    }
    fun validateItemName(name: String): ValidationResult = when {
        name.isBlank()         -> ValidationResult.Error("Item name cannot be empty")
        name.trim().length < 2 -> ValidationResult.Error("Name must be at least 2 characters")
        else -> ValidationResult.Success
    }
}
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    val isSuccess get() = this is Success
    val errorMessage get() = (this as? Error)?.message
}