package icu.guestliang.nfcworkflow.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val success: Boolean = true,
    val code: Int = 200,
    val message: String = "",
    val data: JsonElement? = null
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class Order(
    val id: Int? = null,
    val title: String,
    val description: String,
    val nfc_tag: String? = null,
    val status: String,
    val assigned_to: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class CompleteOrderRequest(
    val orderId: Int
)

@Serializable
data class CreateOrderRequest(
    val title: String,
    val description: String,
    val tag: String? = null
)

@Serializable
data class AssignOrderRequest(
    val orderId: Int,
    val userId: Int
)

@Serializable
data class WorkerUser(
    val id: Int,
    val username: String,
    val role: String,
    val created_at: String? = null
)

@Serializable
data class LogEntry(
    val id: Int,
    val order_id: Int? = null,
    val action: String,
    val operator_id: Int? = null,
    val timestamp: String? = null
)
