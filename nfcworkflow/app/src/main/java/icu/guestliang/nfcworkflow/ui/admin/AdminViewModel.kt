package icu.guestliang.nfcworkflow.ui.admin

import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.network.ApiResponse
import icu.guestliang.nfcworkflow.network.CreateOrderRequest
import icu.guestliang.nfcworkflow.network.LogEntry
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.network.WorkerUser
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class OrderSearchQuery(
    val title: String? = null,
    val description: String? = null,
    val nfcTag: String? = null,
    val status: List<String>? = null,
    val assigned: List<String>? = null,
    val createdStart: String? = null,
    val createdEnd: String? = null,
    val updatedStart: String? = null,
    val updatedEnd: String? = null
)

data class AdminUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val orders: List<Order> = emptyList(),
    val allOrders: List<Order> = emptyList(),
    val isFallbackTriggered: Boolean = false,
    val workers: List<WorkerUser> = emptyList(),
    val logs: List<LogEntry> = emptyList()
)

class AdminViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    fun clearFallbackTriggered() {
        _uiState.update { it.copy(isFallbackTriggered = false) }
    }

    private suspend fun getToken(context: Context): String? {
        return PrefsDataStore.flow(context).firstOrNull()?.token
    }

    fun createOrder(context: Context, title: String, description: String, nfcTag: String?): Job {
        return viewModelScope.launch {
            if (title.isBlank() || description.isBlank()) {
                _uiState.update { it.copy(error = "Title and description cannot be empty") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                val request = CreateOrderRequest(title, description, nfcTag)
                val response: ApiResponse = ApiClient.client.post("admin/orders/create") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()

                if (response.success) {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Order created successfully") }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to create order", "AdminViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun fetchOrders(context: Context, query: OrderSearchQuery? = null): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.get("orders/search") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    query?.let { q ->
                        q.title?.takeIf { it.isNotBlank() }?.let { parameter("title", it) }
                        q.description?.takeIf { it.isNotBlank() }?.let { parameter("description", it) }
                        q.nfcTag?.takeIf { it.isNotBlank() }?.let { parameter("nfc_tag", it) }
                        q.status?.takeIf { it.isNotEmpty() }?.let { parameter("status", it.joinToString(",")) }
                        q.assigned?.takeIf { it.isNotEmpty() }?.let { parameter("assigned", it.joinToString(",")) }
                        q.createdStart?.takeIf { it.isNotBlank() }?.let { parameter("createdStart", it) }
                        q.createdEnd?.takeIf { it.isNotBlank() }?.let { parameter("createdEnd", it) }
                        q.updatedStart?.takeIf { it.isNotBlank() }?.let { parameter("updatedStart", it) }
                        q.updatedEnd?.takeIf { it.isNotBlank() }?.let { parameter("updatedEnd", it) }
                    }
                }.body()

                if (response.success && response.data != null) {
                    val list: List<Order> = try {
                        json.decodeFromJsonElement(response.data)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (query == null) {
                        _uiState.update { it.copy(isLoading = false, orders = list, allOrders = list) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, orders = list) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                if (e is HttpRequestTimeoutException || e is ConnectException || e is SocketTimeoutException) {
                    val currentAllOrders = _uiState.value.allOrders
                    if (query != null && currentAllOrders.isNotEmpty()) {
                        AppLogger.error(context, e, "Search API failed, falling back to local filter", "AdminViewModel")
                        val filtered = currentAllOrders.filter { order ->
                            var match = true
                            query.title?.takeIf { it.isNotBlank() }?.let { if (!order.title.contains(it, true)) match = false }
                            query.description?.takeIf { it.isNotBlank() }?.let { if (!order.description.contains(it, true)) match = false }
                            query.nfcTag?.takeIf { it.isNotBlank() }?.let { if (order.nfc_tag?.contains(it, true) != true) match = false }
                            query.status?.takeIf { it.isNotEmpty() }?.let { if (order.status !in it) match = false }
                            query.assigned?.takeIf { it.isNotEmpty() }?.let { assignedList ->
                                val isUnassigned = order.assigned_to == null
                                val assignedStr = order.assigned_to?.toString()
                                val wantUnassigned = "NULL" in assignedList
                                val matchAssigned = (wantUnassigned && isUnassigned) || (assignedStr != null && assignedStr in assignedList)
                                if (!matchAssigned) match = false
                            }
                            query.createdStart?.takeIf { it.isNotBlank() }?.let { start ->
                                if (order.created_at == null || order.created_at < start) match = false
                            }
                            query.createdEnd?.takeIf { it.isNotBlank() }?.let { end ->
                                if (order.created_at == null || order.created_at > end) match = false
                            }
                            query.updatedStart?.takeIf { it.isNotBlank() }?.let { start ->
                                if (order.updated_at == null || order.updated_at < start) match = false
                            }
                            query.updatedEnd?.takeIf { it.isNotBlank() }?.let { end ->
                                if (order.updated_at == null || order.updated_at > end) match = false
                            }
                            match
                        }
                        _uiState.update { it.copy(isLoading = false, orders = filtered, isFallbackTriggered = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Network timeout/connection failed") }
                    }
                } else {
                    AppLogger.error(context, e, "Failed to fetch orders", "AdminViewModel")
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
                }
            }
        }
    }

    fun fetchWorkers(context: Context): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.get("admin/workers") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.body()

                if (response.success && response.data != null) {
                    val workersJson = try {
                        response.data.jsonObject["WorkerList"]?.jsonArray
                    } catch (e: Exception) {
                        null
                    }
                    val list: List<WorkerUser> = if (workersJson != null) {
                        json.decodeFromJsonElement(workersJson)
                    } else {
                        emptyList()
                    }
                    _uiState.update { it.copy(isLoading = false, workers = list) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to fetch workers", "AdminViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun assignOrder(context: Context, orderId: Int, workerId: Int?): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                // 手动构建 JSON 以确保 userId: null 被发送，避免全局启用 explicitNulls 带来的副作用
                val requestBody = buildJsonObject {
                    put("orderId", JsonPrimitive(orderId))
                    put("userId", if (workerId == null) JsonNull else JsonPrimitive(workerId))
                }

                val response: ApiResponse = ApiClient.client.post("admin/orders/assign") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()

                if (response.success) {
                    _uiState.update { it.copy(isLoading = false, successMessage = response.message) }
                    fetchOrders(context).join()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to assign order", "AdminViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun fetchLogs(context: Context): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.get("orders/logs") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.body()

                if (response.success && response.data != null) {
                    val list: List<LogEntry> = try {
                        json.decodeFromJsonElement(response.data)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    _uiState.update { it.copy(isLoading = false, logs = list) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to fetch logs", "AdminViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }
}
