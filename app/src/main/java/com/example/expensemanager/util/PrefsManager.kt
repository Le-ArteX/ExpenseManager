package com.example.expensemanager.util

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("flatshare_prefs", Context.MODE_PRIVATE)
    var houseId: String
        get() = prefs.getString("house_id", "") ?: ""
        set(value) = prefs.edit().putString("house_id", value).apply()
    var userId: String
        get() = prefs.getString("user_id", "") ?: ""
        set(value) = prefs.edit().putString("user_id", value).apply()
    var houseName: String
        get() = prefs.getString("house_name", "My Flat") ?: "My Flat"
        set(value) = prefs.edit().putString("house_name", value).apply()
    fun clear() = prefs.edit().clear().apply()
    fun isHouseSetup() = houseId.isNotEmpty()
}
