package icu.guestliang.nfcworkflow.ui.worker

import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.network.ApiResponse
import icu.guestliang.nfcworkflow.network.CompleteOrderRequest
import icu.guestliang.nfcworkflow.network.LogEntry
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.network.ScanErrorData
import icu.guestliang.nfcworkflow.network.ScanRequest
import icu.guestliang.nfcworkflow.network.ScanResponseData
import icu.guestliang.nfcworkflow.network.WorkerSummary
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.net.ConnectException
import java.net.SocketTimeoutException
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class WorkerHistoryQuery(
    val orderId: List<String>? = null,
    val action: List<String>? = null,
    val result: List<String>? = null,
    val uidHex: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

data class WorkerUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val orders: List<Order> = emptyList(),
    val history: List<LogEntry> = emptyList(),
    val allHistory: List<LogEntry> = emptyList(),
    val historySummary: WorkerSummary? = null,
    val isFallbackTriggered: Boolean = false,
    val actionSuccess: Boolean = false,
    val scanResponseData: ScanResponseData? = null,
    val scanErrorData: ScanErrorData? = null
)

class WorkerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WorkerUiState())
    val uiState: StateFlow<WorkerUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun clearFallbackTriggered() {
        _uiState.update { it.copy(isFallbackTriggered = false) }
    }

    fun fetchMyOrders(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.get("worker/orders") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.body()

                if (response.success && response.data != null) {
                    val ordersList: List<Order> = json.decodeFromJsonElement(response.data)
                    _uiState.update { it.copy(isLoading = false, orders = ordersList) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to fetch orders", "WorkerViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun fetchHistory(context: Context, query: WorkerHistoryQuery? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                // Fetch history and summary in parallel
                val historyTask = async {
                    ApiClient.client.get("worker/history") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        query?.let { q ->
                            q.orderId?.takeIf { it.isNotEmpty() }?.let { parameter("orderId", it.joinToString(",")) }
                            q.action?.takeIf { it.isNotEmpty() }?.let { parameter("action", it.joinToString(",")) }
                            q.result?.takeIf { it.isNotEmpty() }?.let { parameter("result", it.joinToString(",")) }
                            q.uidHex?.takeIf { it.isNotBlank() }?.let { parameter("uidHex", it) }
                            q.startTime?.takeIf { it.isNotBlank() }?.let { parameter("startTime", it) }
                            q.endTime?.takeIf { it.isNotBlank() }?.let { parameter("endTime", it) }
                        }
                    }.body<ApiResponse>()
                }

                val summaryTask = async {
                    ApiClient.client.get("worker/history/summary") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }.body<ApiResponse>()
                }

                val historyResponse = historyTask.await()
                val summaryResponse = summaryTask.await()

                var errorMsg: String? = null
                var parsedHistory = emptyList<LogEntry>()
                var parsedSummary: WorkerSummary? = null

                if (historyResponse.success && historyResponse.data != null) {
                    parsedHistory = json.decodeFromJsonElement(historyResponse.data)
                } else {
                    errorMsg = historyResponse.message
                }

                if (summaryResponse.success && summaryResponse.data != null) {
                    parsedSummary = json.decodeFromJsonElement(summaryResponse.data)
                }

                if (errorMsg == null) {
                    if (query == null) {
                        _uiState.update { 
                            it.copy(isLoading = false, history = parsedHistory, allHistory = parsedHistory, historySummary = parsedSummary) 
                        }
                    } else {
                        _uiState.update { 
                            it.copy(isLoading = false, history = parsedHistory, historySummary = parsedSummary) 
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                }
            } catch (e: Exception) {
                if (e is HttpRequestTimeoutException || e is ConnectException || e is SocketTimeoutException) {
                    val currentAllHistory = _uiState.value.allHistory
                    if (query != null && currentAllHistory.isNotEmpty()) {
                        AppLogger.error(context, e, "History API failed, falling back to local filter", "WorkerViewModel")
                        val filtered = currentAllHistory.filter { log ->
                            var match = true
                            query.orderId?.takeIf { it.isNotEmpty() }?.let { if (log.order_id?.toString() !in it && log.orderId?.toString() !in it) match = false }
                            query.action?.takeIf { it.isNotEmpty() }?.let { if (log.action !in it) match = false }
                            query.result?.takeIf { it.isNotEmpty() }?.let { if (log.result !in it) match = false }
                            query.uidHex?.takeIf { it.isNotBlank() }?.let { if (log.scanUidHex?.contains(it, true) != true && log.expectedUidHex?.contains(it, true) != true) match = false }
                            query.startTime?.takeIf { it.isNotBlank() }?.let { start ->
                                if (log.timestamp == null || log.timestamp < start) match = false
                            }
                            query.endTime?.takeIf { it.isNotBlank() }?.let { end ->
                                if (log.timestamp == null || log.timestamp > end) match = false
                            }
                            match
                        }
                        _uiState.update { it.copy(isLoading = false, history = filtered, isFallbackTriggered = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Network timeout/connection failed") }
                    }
                } else {
                    AppLogger.error(context, e, "Failed to fetch history", "WorkerViewModel")
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
                }
            }
        }
    }

    fun scanOrder(context: Context, orderId: Int, uidHex: String, rawText: String? = null, ndefText: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, actionSuccess = false, scanResponseData = null, scanErrorData = null) }
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.post("worker/orders/scan") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(ScanRequest(orderId, uidHex, rawText, ndefText))
                }.body()

                if (response.success) {
                    val scanData = response.data?.let { json.decodeFromJsonElement<ScanResponseData>(it) }
                    _uiState.update { it.copy(isLoading = false, actionSuccess = true, scanResponseData = scanData) }
                    fetchMyOrders(context)
                } else {
                    val errorData = try {
                        response.data?.let { json.decodeFromJsonElement<ScanErrorData>(it) }
                    } catch (e: Exception) { null }
                    _uiState.update { it.copy(isLoading = false, error = response.message, scanErrorData = errorData) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to scan order", "WorkerViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun completeOrder(context: Context, orderId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, actionSuccess = false) }
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.post("worker/orders/complete") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CompleteOrderRequest(orderId))
                }.body()

                if (response.success) {
                    _uiState.update { it.copy(isLoading = false, actionSuccess = true) }
                    fetchMyOrders(context) // Refresh the list
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to complete order", "WorkerViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun resetActionSuccess() {
        _uiState.update { it.copy(actionSuccess = false, scanResponseData = null, scanErrorData = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, actionSuccess = false, scanResponseData = null, scanErrorData = null) }
    }
}
