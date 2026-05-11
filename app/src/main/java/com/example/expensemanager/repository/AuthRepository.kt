package com.example.expensemanager.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.example.expensemanager.model.Member
import com.example.expensemanager.BuildConfig
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class AuthRepository {
    private val auth = Firebase.auth
    private val db   = Firebase.firestore

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.brevo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val brevoApi = retrofit.create(BrevoApiService::class.java)

    private val BREVO_API_KEY = BuildConfig.BREVO_API_KEY
    private val SENDER_EMAIL  = "mursalinleon2295@gmail.com"

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun sendOtp(email: String): Result<Unit> {
        return try {
            Log.d("OTP_FLOW", "Starting OTP process for $email")
            
            if (BREVO_API_KEY.isBlank()) {
                Log.e("OTP_FLOW", "BREVO_API_KEY is empty! Check local.properties and Sync Gradle.")
                return Result.failure(Exception("API Configuration missing"))
            }

            val otp = (100000..999999).random().toString()
            
            db.collection("otps").document(email).set(mapOf(
                "otp" to otp,
                "createdAt" to Timestamp.now(),
                "email" to email
            )).await()
            
            val emailRequest = BrevoEmailRequest(
                sender = BrevoSender("Expense Manager", SENDER_EMAIL),
                to = listOf(BrevoTo(email)),
                subject = "Your Verification Code",
                htmlContent = """
                    <div style="font-family: sans-serif; text-align: center; padding: 20px;">
                        <h2>Verification Code</h2>
                        <h1 style="color: #008080; font-size: 32px; letter-spacing: 5px;">$otp</h1>
                        <p>Valid for 5 minutes.</p>
                    </div>
                """.trimIndent()
            )

            val response = brevoApi.sendEmail(BREVO_API_KEY, emailRequest)
            if (response.isSuccessful) {
                Log.d("OTP_FLOW", "Email sent successfully to $email")
                Result.success(Unit)
            } else {
                val errorJson = response.errorBody()?.string() ?: "{}"
                Log.e("OTP_FLOW", "Brevo API Error: $errorJson")
                
                val rawMsg = try { JSONObject(errorJson).getString("message") } catch (e: Exception) { errorJson }
                
                // Better error message parsing
                val userMsg = when {
                    rawMsg.contains("api key", ignoreCase = true) -> "Invalid API Key. Update local.properties."
                    rawMsg.contains("unauthorized", ignoreCase = true) -> "IP Blocked or Unauthorized. Check Brevo settings."
                    else -> "Brevo Error: $rawMsg"
                }
                Result.failure(Exception(userMsg))
            }
        } catch (e: Exception) {
            Log.e("OTP_FLOW", "Exception in sendOtp: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(email: String, enteredOtp: String): Boolean {
        return try {
            val doc = db.collection("otps").document(email).get().await()
            if (doc.exists()) {
                val storedOtp = doc.getString("otp")
                val createdAt = doc.getTimestamp("createdAt")
                if (storedOtp == enteredOtp && createdAt != null) {
                    if (Timestamp.now().seconds - createdAt.seconds < 300) {
                        db.collection("otps").document(email).delete()
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) { false }
    }

    suspend fun registerWithEmail(email: String, pass: String, name: String): Result<FirebaseUser> {
        return try {
            val res = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = res.user ?: throw Exception("Registration failed")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
            saveMemberProfile(user, name)
            syncFcmToken() // Ensure token is saved after registration
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun loginWithEmail(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val res = auth.signInWithEmailAndPassword(email, pass).await()
            val user = res.user ?: throw Exception("Login failed")
            syncFcmToken() // Ensure token is saved after login
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val res = auth.signInWithCredential(credential).await()
            val user = res.user ?: throw Exception("Google login failed")
            
            if (res.additionalUserInfo?.isNewUser == true) {
                saveMemberProfile(user, user.displayName ?: "User")
            }
            
            syncFcmToken()
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getMemberProfile(uid: String): Member? {
        return try {
            db.collection("members").document(uid).get().await().toObject(Member::class.java)
        } catch (e: Exception) {
            Log.e("AuthRepo", "Error getting profile: ${e.message}")
            null
        }
    }

    private suspend fun syncFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            updateFcmToken(token)
        } catch (e: Exception) {
            Log.e("AuthRepo", "FCM Token sync failed: ${e.message}")
        }
    }

    suspend fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("members").document(uid).update("fcmToken", token).await()
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

    suspend fun resetPassword(email: String, newPass: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
