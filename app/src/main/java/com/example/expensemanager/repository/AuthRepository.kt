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

class AuthRepository {
    private val auth = Firebase.auth
    private val db   = Firebase.firestore

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.brevo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val brevoApi = retrofit.create(BrevoApiService::class.java)

    // Ensure the key is trimmed to avoid invisible character issues
    private val BREVO_API_KEY = BuildConfig.BREVO_API_KEY.trim()
    
    // IMPORTANT: This email must be verified as a sender in your Brevo account dashboard.
    private val SENDER_EMAIL  = "mursalinleon2295@gmail.com"

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Generates a 6-digit OTP and sends it via Brevo email.
     * Check Logcat for "OTP_FLOW" to debug if email is not arriving.
     */
    suspend fun sendOtp(email: String): Result<Unit> {
        return try {
            Log.d("OTP_FLOW", "Generating OTP for $email. Key prefix: ${BREVO_API_KEY.take(5)}")
            
            if (BREVO_API_KEY.isBlank() || BREVO_API_KEY == "null") {
                return Result.failure(Exception("Brevo API Key is missing. Check local.properties and Sync Gradle."))
            }

            val otp = (100000..999999).random().toString()
            
            // 1. Store OTP in Firestore with a timestamp (expires in 5 mins)
            db.collection("otps").document(email).set(mapOf(
                "otp" to otp,
                "createdAt" to Timestamp.now(),
                "email" to email
            )).await()
            
            // 2. Prepare Brevo Email Request
            val emailRequest = BrevoEmailRequest(
                sender = BrevoSender("FlatShare Support", SENDER_EMAIL),
                to = listOf(BrevoTo(email)),
                subject = "Your Verification Code: $otp",
                htmlContent = """
                    <div style="font-family: sans-serif; text-align: center; padding: 40px; background-color: #f8f9fa;">
                        <div style="background: white; padding: 30px; border-radius: 12px; border: 1px solid #dee2e6; display: inline-block;">
                            <h2 style="color: #00695C; margin-top: 0;">Verification Code</h2>
                            <p style="color: #6c757d;">Use this code to verify your identity on FlatShare.</p>
                            <h1 style="color: #004D40; font-size: 42px; letter-spacing: 10px; margin: 20px 0;">$otp</h1>
                            <p style="color: #adb5bd; font-size: 12px;">This code is valid for 5 minutes.</p>
                        </div>
                    </div>
                """.trimIndent()
            )

            // 3. Send email via Brevo API
            val response = brevoApi.sendEmail(BREVO_API_KEY, emailRequest)
            if (response.isSuccessful) {
                Log.d("OTP_FLOW", "Email sent successfully to $email")
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "{}"
                Log.e("OTP_FLOW", "Brevo API Error ${response.code()}: $errorBody")
                
                val msg = try { 
                    val obj = JSONObject(errorBody)
                    obj.optString("message", obj.optString("error", "Email delivery failed"))
                } catch (e: Exception) { "Service error: ${response.message()}" }
                
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e("OTP_FLOW", "Network error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifies the OTP stored in Firestore.
     */
    suspend fun verifyOtp(email: String, enteredOtp: String): Boolean {
        return try {
            val doc = db.collection("otps").document(email).get().await()
            if (doc.exists()) {
                val storedOtp = doc.getString("otp")
                val createdAt = doc.getTimestamp("createdAt")
                if (storedOtp == enteredOtp && createdAt != null) {
                    // Check if OTP is less than 5 minutes old
                    if (Timestamp.now().seconds - createdAt.seconds < 300) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) { 
            Log.e("OTP_FLOW", "Verification error: ${e.message}")
            false 
        }
    }

    /**
     * Sign in with Google and automatically register the user in Firestore.
     */
    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val res = auth.signInWithCredential(credential).await()
            val user = res.user ?: throw Exception("Google login failed")
            
            // Check if profile exists, create it if first time
            val profileDoc = db.collection("members").document(user.uid).get().await()
            if (!profileDoc.exists()) {
                saveMemberProfile(user, user.displayName ?: "Google User")
            }
            
            syncFcmToken()
            Result.success(user)
        } catch (e: Exception) { 
            Log.e("AuthRepo", "Google login failed", e)
            Result.failure(e) 
        }
    }

    /**
     * Standard Firebase password reset flow.
     * If newPassword is null, it sends the official Firebase reset email.
     * If newPassword is provided, it attempts to update it (requires auth or oobCode).
     */
    suspend fun resetPassword(email: String, newPassword: String? = null): Result<Unit> {
        return try {
            if (newPassword == null) {
                auth.sendPasswordResetEmail(email).await()
                Result.success(Unit)
            } else {
                // Note: Direct password update requires the user to be signed in or 
                // using a valid Firebase Action Code (oobCode).
                // For a custom OTP flow, you typically use a backend or sign the user in temporarily.
                // For now, we'll return failure if trying to set directly without auth.
                val user = auth.currentUser
                if (user != null && user.email == email) {
                    user.updatePassword(newPassword).await()
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Re-authentication required to set a new password directly."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    private suspend fun syncFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val uid = auth.currentUser?.uid ?: return
            db.collection("members").document(uid).update("fcmToken", token).await()
        } catch (e: Exception) { }
    }

    private suspend fun saveMemberProfile(user: FirebaseUser, displayName: String) {
        val member = Member(
            uid = user.uid, displayName = displayName,
            email = user.email ?: "", photoUrl = user.photoUrl?.toString() ?: "",
            joinedAt = Timestamp.now()
        )
        db.collection("members").document(user.uid).set(member).await()
    }

    suspend fun getMemberProfile(uid: String): Member? {
        return try {
            db.collection("members").document(uid).get().await().toObject(Member::class.java)
        } catch (e: Exception) { null }
    }

    fun logout() = auth.signOut()
}
