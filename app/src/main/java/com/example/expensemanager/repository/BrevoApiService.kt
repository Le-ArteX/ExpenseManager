package com.example.expensemanager.repository

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BrevoApiService {
    @POST("v3/smtp/email")
    suspend fun sendEmail(
        @Header("api-key") apiKey: String,
        @Body request: BrevoEmailRequest
    )
}

data class BrevoEmailRequest(
    val sender: BrevoSender,
    val to: List<BrevoTo>,
    val subject: String,
    val htmlContent: String
)

data class BrevoSender(val name: String, val email: String)
data class BrevoTo(val email: String)
