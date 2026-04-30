package com.example.expensemanager.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.expensemanager.model.Member
import kotlinx.coroutines.tasks.await
class AuthRepository {
    private val auth = Firebase.auth
    private val db   = Firebase.firestore
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isLoggedIn(): Boolean = auth.currentUser != null
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
            Result.failure(Exception(friendlyAuthError(e.message)))
        }
    }
    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user ?: throw Exception("Login failed"))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyAuthError(e.message)))
        }
    }
    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(cred).await()
            val user = result.user ?: throw Exception("Google sign-in failed")
            if (result.additionalUserInfo?.isNewUser == true) {
                saveMemberProfile(user, user.displayName ?: "User")
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Google sign-in failed: ${e.message}"))
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