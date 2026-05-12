package com.example.expensemanager.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.example.expensemanager.model.Member
import com.example.expensemanager.BuildConfig
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthRepository {
    private val auth = Firebase.auth
    private val db   = Firebase.firestore

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.brevo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val brevoApi = retrofit.create(BrevoApiService::class.java)

    private val BREVO_API_KEY = BuildConfig.BREVO_API_KEY.trim()
    private val SENDER_EMAIL  = "mursalinleon2295@gmail.com"

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun sendOtp(email: String): Result<Unit> {
        return try {
            val otp = (100000..999999).random().toString()
            db.collection("otps").document(email).set(mapOf(
                "otp" to otp,
                "createdAt" to Timestamp.now(),
                "email" to email
            )).await()
            
            val emailRequest = BrevoEmailRequest(
                sender = BrevoSender("FlatShare Support", SENDER_EMAIL),
                to = listOf(BrevoTo(email)),
                subject = "Your Verification Code: $otp",
                htmlContent = "<h2>Verification Code</h2><h1>$otp</h1>".trimIndent()
            )

            val response = brevoApi.sendEmail(BREVO_API_KEY, emailRequest)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Email failed"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun verifyOtp(email: String, enteredOtp: String): Boolean {
        return try {
            val doc = db.collection("otps").document(email).get().await()
            if (doc.exists()) {
                val storedOtp = doc.getString("otp")
                val createdAt = doc.getTimestamp("createdAt")
                if (storedOtp == enteredOtp && createdAt != null) {
                    if (Timestamp.now().seconds - createdAt.seconds < 300) return true
                }
            }
            false
        } catch (e: Exception) { false }
    }

    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val res = auth.signInWithCredential(credential).await()
            val user = res.user ?: throw Exception("Google login failed")
            
            val profileDoc = db.collection("members").document(user.uid).get().await()
            if (!profileDoc.exists()) saveMemberProfile(user, user.displayName ?: "Google User")
            
            syncFcmToken()
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resetPassword(email: String, newPassword: String? = null): Result<Unit> {
        return try {
            if (newPassword == null) {
                auth.sendPasswordResetEmail(email).await()
                Result.success(Unit)
            } else {
                val user = auth.currentUser
                if (user != null && user.email == email) {
                    user.updatePassword(newPassword).await()
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Re-authentication required."))
                }
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun registerWithEmail(email: String, pass: String, name: String): Result<FirebaseUser> {
        return try {
            val res = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = res.user ?: throw Exception("Registration failed")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
            saveMemberProfile(user, name)
            syncFcmToken()
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun loginWithEmail(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val res = auth.signInWithEmailAndPassword(email, pass).await()
            val user = res.user ?: throw Exception("Login failed")
            syncFcmToken()
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getMemberProfile(uid: String): Member? {
        return try {
            val doc = db.collection("members").document(uid).get().await()
            doc.toObject(Member::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val uid = auth.currentUser?.uid ?: return
            db.collection("members").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge()).await()
            Log.d("AuthRepo", "FCM Token synced successfully")
        } catch (e: Exception) {
            Log.e("AuthRepo", "FCM Sync failed: ${e.message}")
        }
    }

    private suspend fun saveMemberProfile(user: FirebaseUser, displayName: String) {
        val member = Member(
            uid = user.uid, displayName = displayName,
            email = user.email ?: "", photoUrl = user.photoUrl?.toString() ?: "",
            joinedAt = Timestamp.now()
        )
        db.collection("members").document(user.uid).set(member).await()
    }

    fun logout() = auth.signOut()
}
