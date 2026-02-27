package icu.guestliang.nfcworkflow.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)
