package com.example.expensemanager.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.expensemanager.model.Member
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class AuthRepository {
    private val auth = Firebase.auth
    private val db   = Firebase.firestore

    // Retrofit setup for Brevo
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.brevo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val brevoApi = retrofit.create(BrevoApiService::class.java)

    // Using the long key from your screenshot
    private val BREVO_API_KEY = "xsmtpsib-3fb12ce833b0d3692550a8f86aee7ae75bce5cb22878401347072a2977759c25"
    
    // Updated with your verified email
    private val SENDER_EMAIL  = "mursalinleon2295@gmail.com"

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isLoggedIn(): Boolean = auth.currentUser != null

    suspend fun sendOtp(email: String): Result<Unit> {
        return try {
            val otp = (100000..999999).random().toString()
            
            // 1. Store OTP in Firestore
            val data = mapOf(
                "otp" to otp,
                "createdAt" to Timestamp.now(),
                "email" to email
            )
            db.collection("otps").document(email).set(data).await()
            
            // 2. Send Real Email via Brevo
            val emailRequest = BrevoEmailRequest(
                sender = BrevoSender("Expense Manager", SENDER_EMAIL),
                to = listOf(BrevoTo(email)),
                subject = "Your Verification Code",
                htmlContent = """
                    <html>
                        <body style="font-family: sans-serif; padding: 20px; text-align: center;">
                            <h2 style="color: #333;">Verification Code</h2>
                            <p>Use the code below to verify your account in Expense Manager:</p>
                            <div style="background: #f4f4f4; padding: 20px; display: inline-block; border-radius: 10px; border: 1px solid #ddd;">
                                <h1 style="color: #008080; letter-spacing: 5px; margin: 0;">$otp</h1>
                            </div>
                            <p style="color: #777; margin-top: 20px;">This code will expire in 5 minutes.</p>
                        </body>
                    </html>
                """.trimIndent()
            )

            brevoApi.sendEmail(BREVO_API_KEY, emailRequest)
            
            Log.d("OTP_FLOW", "Real Email sent to $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OTP_FLOW", "Error sending email: ${e.message}")
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
                    val diff = (Timestamp.now().seconds - createdAt.seconds)
                    if (diff < 300) { 
                        db.collection("otps").document(email).delete()
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resetPassword(email: String, newPass: String): Result<Unit> {
        return try {
            val user = auth.currentUser
            if (user != null && user.email == email) {
                user.updatePassword(newPass).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Manual password reset requires backend integration."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(
        email: String, password: String, displayName: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("Registration failed")
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
            ).await()
            saveMemberProfile(user, displayName)
            Result.success(user)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration Error: ${e.message}", e)
            Result.failure(Exception(friendlyAuthError(e.message)))
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user ?: throw Exception("Login failed"))
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login Error: ${e.message}", e)
            Result.failure(Exception(friendlyAuthError(e.message)))
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(cred).await()
            val user = result.user ?: throw Exception("Google sign-in failed")
            
            val doc = db.collection("members").document(user.uid).get().await()
            if (!doc.exists()) {
                saveMemberProfile(user, user.displayName ?: "User")
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google Login Error: ${e.message}", e)
            Result.failure(Exception("Google sign-in failed: ${e.message}"))
        }
    }

    suspend fun getMemberProfile(uid: String): Member? {
        return try {
            db.collection("members").document(uid).get().await().toObject(Member::class.java)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error fetching profile: ${e.message}")
            null
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Could not send reset email"))
        }
    }

    fun logout() = auth.signOut()

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

    private fun friendlyAuthError(msg: String?): String = when {
        msg?.contains("email address is already") == true ->
            "This email is already registered. Please login."
        msg?.contains("no user record") == true ->
            "No account found. Please register first."
        msg?.contains("password is invalid") == true ||
                msg?.contains("wrong-password") == true ->
            "Incorrect password. Please try again."
        msg?.contains("too-many-requests") == true ->
            "Too many failed attempts. Please wait a few minutes."
        msg?.contains("network") == true ->
            "No internet connection. Please check your network."
        else -> msg ?: "Authentication failed. Please try again."
    }
}
