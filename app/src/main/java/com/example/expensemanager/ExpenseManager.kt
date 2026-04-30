package com.example.expensemanager

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings

class ExpenseManager : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        val db = FirebaseFirestore.getInstance()
        val settings = firestoreSettings {

            setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                .setSizeBytes(50 * 1024 * 1024)
                .build())
        }
        db.firestoreSettings = settings
    }
}