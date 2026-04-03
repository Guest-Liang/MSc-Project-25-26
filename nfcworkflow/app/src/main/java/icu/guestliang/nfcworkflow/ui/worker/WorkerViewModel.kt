package icu.guestliang.nfcworkflow.ui.worker

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.network.ApiResponse
import icu.guestliang.nfcworkflow.network.CompleteOrderRequest
import icu.guestliang.nfcworkflow.network.LogEntry
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.network.PaginatedResponse
import icu.guestliang.nfcworkflow.network.ScanErrorData
import icu.guestliang.nfcworkflow.network.ScanRequest
import icu.guestliang.nfcworkflow.network.ScanResponseData
import icu.guestliang.nfcworkflow.network.WorkerSummary
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
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
    val isAppendingOrders: Boolean = false,
    val isAppendingHistory: Boolean = false,
    val error: String? = null,
    val appendError: String? = null,
    
    val orders: List<Order> = emptyList(),
    val nextOrdersCursor: String? = null,
    val hasMoreOrders: Boolean = true,
    
    val history: List<LogEntry> = emptyList(),
    val nextHistoryCursor: String? = null,
    val hasMoreHistory: Boolean = true,
    val currentHistoryQuery: WorkerHistoryQuery? = null,
    
    val historySummary: WorkerSummary? = null,
    val actionSuccess: Boolean = false,
    val scanResponseData: ScanResponseData? = null,
    val scanErrorData: ScanErrorData? = null
)

class WorkerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WorkerUiState())
    val uiState: StateFlow<WorkerUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Track active requests to avoid mixing old paging requests with new ones
    private var fetchOrdersJob: Job? = null
    private var fetchHistoryJob: Job? = null

    companion object {
        const val MIN_APPEND_DELAY_MS = 500L
    }

    fun clearAppendError() {
        _uiState.update { it.copy(appendError = null) }
    }

    fun fetchMyOrders(context: Context, isAppend: Boolean = false) {
        if (isAppend && (_uiState.value.isAppendingOrders || !_uiState.value.hasMoreOrders)) return
        
        // Cancel previous fetch if we are performing a fresh request
        if (!isAppend) fetchOrdersJob?.cancel()
        
        fetchOrdersJob = viewModelScope.launch {
            if (isAppend) {
                _uiState.update { it.copy(isAppendingOrders = true, appendError = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null, orders = emptyList(), nextOrdersCursor = null, hasMoreOrders = true) }
            }
            
            val startTime = System.currentTimeMillis()
            
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    val msg = context.getString(R.string.err_not_logged_in)
                    if (isAppend) _uiState.update { it.copy(isAppendingOrders = false, appendError = msg) }
                    else _uiState.update { it.copy(isLoading = false, error = msg) }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.get("worker/orders") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    parameter("limit", 6)
                    if (isAppend) {
                        _uiState.value.nextOrdersCursor?.let { parameter("cursor", it) }
                    }
                }.body()

                if (response.success && response.data != null) {
                    val paginatedData: PaginatedResponse<Order> = json.decodeFromJsonElement(response.data)
                    
                    val elapsed = System.currentTimeMillis() - startTime
                    if (isAppend && elapsed < MIN_APPEND_DELAY_MS) {
                        delay(MIN_APPEND_DELAY_MS - elapsed) // Wait to show spinner
                    }
                    
                    if (isAppend) {
                        _uiState.update { 
                            it.copy(
                                isAppendingOrders = false,
                                orders = it.orders + paginatedData.items,
                                nextOrdersCursor = paginatedData.nextCursor,
                                hasMoreOrders = paginatedData.hasMore
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                orders = paginatedData.items,
                                nextOrdersCursor = paginatedData.nextCursor,
                                hasMoreOrders = paginatedData.hasMore
                            )
                        }
                    }
                } else {
                    if (isAppend) _uiState.update { it.copy(isAppendingOrders = false, appendError = response.message) }
                    else _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to fetch orders", "WorkerViewModel")
                val msg = e.message ?: context.getString(R.string.err_worker_fetch_orders_failed)
                if (isAppend) _uiState.update { it.copy(isAppendingOrders = false, appendError = msg) }
                else _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    fun fetchHistory(context: Context, query: WorkerHistoryQuery? = null, isAppend: Boolean = false) {
        if (isAppend && (_uiState.value.isAppendingHistory || !_uiState.value.hasMoreHistory)) return
        
        // Cancel previous fetch if we are performing a fresh request
        if (!isAppend) fetchHistoryJob?.cancel()

        fetchHistoryJob = viewModelScope.launch {
            val effectiveQuery = if (isAppend) _uiState.value.currentHistoryQuery else query

            if (isAppend) {
                _uiState.update { it.copy(isAppendingHistory = true, appendError = null) }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = true, 
                        error = null, 
                        history = emptyList(), 
                        nextHistoryCursor = null, 
                        hasMoreHistory = true,
                        currentHistoryQuery = effectiveQuery
                    ) 
                }
            }
            
            val startTime = System.currentTimeMillis()
            
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    val msg = context.getString(R.string.err_not_logged_in)
                    if (isAppend) _uiState.update { it.copy(isAppendingHistory = false, appendError = msg) }
                    else _uiState.update { it.copy(isLoading = false, error = msg) }
                    return@launch
                }

                // Fetch history
                val historyTask = async {
                    ApiClient.client.get("worker/history") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        parameter("limit", 6)
                        if (isAppend) {
                            _uiState.value.nextHistoryCursor?.let { parameter("cursor", it) }
                        }
                        effectiveQuery?.let { q ->
                            q.orderId?.takeIf { it.isNotEmpty() }?.let { parameter("orderId", it.joinToString(",")) }
                            q.action?.takeIf { it.isNotEmpty() }?.let { parameter("action", it.joinToString(",")) }
                            q.result?.takeIf { it.isNotEmpty() }?.let { parameter("result", it.joinToString(",")) }
                            q.uidHex?.takeIf { it.isNotBlank() }?.let { parameter("uidHex", it) }
                            q.startTime?.takeIf { it.isNotBlank() }?.let { parameter("startTime", it) }
                            q.endTime?.takeIf { it.isNotBlank() }?.let { parameter("endTime", it) }
                        }
                    }.body<ApiResponse>()
                }

                val summaryTask = if (!isAppend) async {
                    ApiClient.client.get("worker/history/summary") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }.body<ApiResponse>()
                } else null

                val historyResponse = historyTask.await()
                val summaryResponse = summaryTask?.await()

                var errorMsg: String? = null
                var paginatedData: PaginatedResponse<LogEntry>? = null
                var parsedSummary: WorkerSummary? = _uiState.value.historySummary

                if (historyResponse.success && historyResponse.data != null) {
                    paginatedData = json.decodeFromJsonElement(historyResponse.data)
                } else {
                    errorMsg = historyResponse.message
                }

                if (summaryResponse?.success == true && summaryResponse.data != null) {
                    parsedSummary = json.decodeFromJsonElement(summaryResponse.data)
                }
                
                val elapsed = System.currentTimeMillis() - startTime
                if (isAppend && elapsed < MIN_APPEND_DELAY_MS) {
                    delay(MIN_APPEND_DELAY_MS - elapsed)
                }

                if (errorMsg == null && paginatedData != null) {
                    if (isAppend) {
                        _uiState.update { 
                            it.copy(
                                isAppendingHistory = false, 
                                history = it.history + paginatedData.items, 
                                nextHistoryCursor = paginatedData.nextCursor,
                                hasMoreHistory = paginatedData.hasMore
                            ) 
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                history = paginatedData.items, 
                                historySummary = parsedSummary,
                                nextHistoryCursor = paginatedData.nextCursor,
                                hasMoreHistory = paginatedData.hasMore
                            ) 
                        }
                    }
                } else {
                    if (isAppend) _uiState.update { it.copy(isAppendingHistory = false, appendError = errorMsg) }
                    else _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                }
            } catch (e: Exception) {
                AppLogger.error(context, e, "Failed to fetch history", "WorkerViewModel")
                val msg = e.message ?: context.getString(R.string.err_worker_fetch_history_failed)
                if (isAppend) _uiState.update { it.copy(isAppendingHistory = false, appendError = msg) }
                else _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    fun scanOrder(context: Context, orderId: Int, uidHex: String, rawText: String? = null, ndefText: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, actionSuccess = false, scanResponseData = null, scanErrorData = null) }
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.err_not_logged_in)) }
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
                _uiState.update { it.copy(isLoading = false, error = e.message ?: context.getString(R.string.err_worker_scan_failed)) }
            }
        }
    }

    fun completeOrder(context: Context, orderId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, actionSuccess = false) }
            try {
                val token = PrefsDataStore.flow(context).firstOrNull()?.token
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.err_not_logged_in)) }
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
                _uiState.update { it.copy(isLoading = false, error = e.message ?: context.getString(R.string.err_worker_complete_failed)) }
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