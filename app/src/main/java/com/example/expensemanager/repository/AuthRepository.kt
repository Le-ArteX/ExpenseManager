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
import java.util.*

class AuthRepository {
    private val auth = Firebase.auth
    private val db   = Firebase.firestore

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isLoggedIn(): Boolean = auth.currentUser != null

    suspend fun sendOtp(email: String): Result<Unit> {
        return try {
            // Generate 6-digit OTP
            val otp = (100000..999999).random().toString()
            
            // Store OTP in Firestore with expiration (e.g., 5 minutes)
            val data = mapOf(
                "otp" to otp,
                "createdAt" to Timestamp.now(),
                "email" to email
            )
            db.collection("otps").document(email).set(data).await()
            
            // NOTE: In a real production app, you would trigger a Firebase Cloud Function
            // here to send an actual email via a service like SendGrid or Mailgun.
            // For this project, we'll log it and assume the user "received" it.
            Log.d("OTP_FLOW", "OTP for $email is: $otp")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(email: String, enteredOtp: String): Boolean {
        return try {
            val doc = db.collection("otps").document(email).get().await()
            if (doc.exists()) {
                val storedOtp = doc.getString("otp")
                val createdAt = doc.getTimestamp("createdAt")
                
                // Check if OTP matches and is not expired (5 mins)
                if (storedOtp == enteredOtp && createdAt != null) {
                    val diff = (Timestamp.now().seconds - createdAt.seconds)
                    if (diff < 300) { // 300 seconds = 5 minutes
                        // OTP is valid, remove it
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
            // NOTE: Firebase Auth doesn't allow changing password by email without a link 
            // or being logged in. A common workaround is to use an admin SDK in a 
            // Cloud Function or re-authenticate. 
            // For this implementation, if they just set the password, we assume success 
            // in a demo environment, but in reality, you'd call a secure backend.
            
            // If the user is logged in, we can update directly
            val user = auth.currentUser
            if (user != null && user.email == email) {
                user.updatePassword(newPass).await()
                Result.success(Unit)
            } else {
                // If not logged in, we'd typically sign them in with a custom token 
                // or use a backend function. 
                Result.failure(Exception("Manual password reset requires backend integration. Link-based reset is recommended for Firebase."))
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
