package com.example.expensemanager.repository

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface BrevoApiService {
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    @POST("v3/smtp/email")
    suspend fun sendEmail(
        @Header("api-key") apiKey: String,
        @Body request: BrevoEmailRequest
    ): Response<BrevoResponse>
}

data class BrevoEmailRequest(
    val sender: BrevoSender,
    val to: List<BrevoTo>,
    val subject: String,
    val htmlContent: String
)

data class BrevoSender(val name: String, val email: String)
data class BrevoTo(val email: String)

data class BrevoResponse(
    val messageId: String? = null,
    val code: String? = null,
    val message: String? = null
)
