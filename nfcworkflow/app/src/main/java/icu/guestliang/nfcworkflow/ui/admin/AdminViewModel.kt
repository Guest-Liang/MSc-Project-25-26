package icu.guestliang.nfcworkflow.ui.admin

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.AdminAnalysisSummary
import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.network.ApiResponse
import icu.guestliang.nfcworkflow.network.CreateOrderRequest
import icu.guestliang.nfcworkflow.network.CreateOrderResponseData
import icu.guestliang.nfcworkflow.network.LogEntry
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.network.OrderStep
import icu.guestliang.nfcworkflow.network.PaginatedResponse
import icu.guestliang.nfcworkflow.network.SaveOrderStepsRequest
import icu.guestliang.nfcworkflow.network.WorkerUser
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

data class OrderSearchQuery(
    val title: String? = null,
    val description: String? = null,
    val nfcTag: String? = null,
    val orderType: List<String>? = null,
    val status: List<String>? = null,
    val assigned: List<String>? = null,
    val progress: List<String>? = null,
    val createdStart: String? = null,
    val createdEnd: String? = null,
    val updatedStart: String? = null,
    val updatedEnd: String? = null
)

data class LogSearchQuery(
    val orderId: List<String>? = null,
    val action: List<String>? = null,
    val result: List<String>? = null,
    val operator: List<String>? = null,
    val uidHex: String? = null,
    val orderType: List<String>? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

data class AdminUiState(
    val isLoading: Boolean = false,
    val isAppendingOrders: Boolean = false,
    val isAppendingLogs: Boolean = false,
    val error: String? = null,
    val appendError: String? = null,
    val successMessage: String? = null,
    
    val orders: List<Order> = emptyList(),
    val nextOrdersCursor: String? = null,
    val hasMoreOrders: Boolean = true,
    val currentOrderQuery: OrderSearchQuery? = null,
    
    val workers: List<WorkerUser> = emptyList(),
    
    val logs: List<LogEntry> = emptyList(),
    val nextLogsCursor: String? = null,
    val hasMoreLogs: Boolean = true,
    val currentLogQuery: LogSearchQuery? = null,
    
    val analysisSummary: AdminAnalysisSummary? = null
)

class AdminViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Track active requests to avoid mixing old paging requests with new ones
    private var fetchOrdersJob: Job? = null
    private var fetchLogsJob: Job? = null

    companion object {
        const val MIN_APPEND_DELAY_MS = 500L
        const val DEFAULT_PAGE_SIZE = 5
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
    
    fun clearAppendError() {
        _uiState.update { it.copy(appendError = null) }
    }

    private suspend fun getToken(context: Context): String? {
        return PrefsDataStore.flow(context).firstOrNull()?.token
    }

    fun createOrder(
        context: Context,
        title: String,
        description: String,
        orderType: String,
        targetUidHex: String?,
        steps: List<OrderStep>
    ): Job {
        return viewModelScope.launch {
            if (title.isBlank() || description.isBlank()) {
                _uiState.update { it.copy(error = context.getString(R.string.err_title_desc_empty)) }
                return@launch
            }

            if (orderType == "sequence" && steps.isEmpty()) {
                _uiState.update { it.copy(error = context.getString(R.string.err_sequence_no_steps)) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.err_not_logged_in)) }
                    return@launch
                }

                val createReq = CreateOrderRequest(
                    title = title,
                    description = description,
                    orderType = orderType,
                    targetUidHex = if (orderType == "standard") targetUidHex else null,
                    tag = if (orderType == "standard") targetUidHex else null
                )

                val response: ApiResponse = ApiClient.client.post("admin/orders/create") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(createReq)
                }.body()

                if (response.success && response.data != null) {
                    val dataObj = try {
                        json.decodeFromJsonElement<CreateOrderResponseData>(response.data)
                    } catch (e: Exception) { null }
                    
                    val orderId = dataObj?.orderId
                    if (orderType == "sequence" && orderId != null) {
                        // Save steps
                        val stepsReq = SaveOrderStepsRequest(orderId, steps)
                        val stepsResp: ApiResponse = ApiClient.client.post("admin/orders/steps/save") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody(stepsReq)
                        }.body()
                        
                        if (!stepsResp.success) {
                            _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.err_failed_to_save_steps, stepsResp.message)) }
                            return@launch
                        }
                    }
                    _uiState.update { it.copy(isLoading = false, successMessage = context.getString(R.string.err_order_created_success)) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.error(context, e, "Failed to create order", "AdminViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: context.getString(R.string.err_unknown)) }
            }
        }
    }

    fun fetchOrders(context: Context, query: OrderSearchQuery? = null, isAppend: Boolean = false): Job {
        if (isAppend && (_uiState.value.isAppendingOrders || !_uiState.value.hasMoreOrders)) {
            return viewModelScope.launch { }
        }
        
        // Cancel previous fetch if we are performing a fresh request
        if (!isAppend) fetchOrdersJob?.cancel()
        
        fetchOrdersJob = viewModelScope.launch {
            val effectiveQuery = if (isAppend) _uiState.value.currentOrderQuery else query
            
            if (isAppend) {
                _uiState.update { it.copy(isAppendingOrders = true, appendError = null) }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = true, 
                        error = null, 
                        orders = emptyList(), 
                        nextOrdersCursor = null, 
                        hasMoreOrders = true,
                        currentOrderQuery = effectiveQuery
                    ) 
                }
            }
            
            val startTime = System.currentTimeMillis()
            
            try {
                val token = getToken(context)
                if (token == null) {
                    val msg = context.getString(R.string.err_not_logged_in)
                    if (isAppend) _uiState.update { it.copy(isAppendingOrders = false, appendError = msg) }
                    else _uiState.update { it.copy(isLoading = false, error = msg) }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.get("orders/search") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    parameter("limit", DEFAULT_PAGE_SIZE)
                    if (isAppend) {
                        _uiState.value.nextOrdersCursor?.let { parameter("cursor", it) }
                    }
                    effectiveQuery?.let { q ->
                        q.title?.takeIf { it.isNotBlank() }?.let { parameter("title", it) }
                        q.description?.takeIf { it.isNotBlank() }?.let { parameter("description", it) }
                        q.nfcTag?.takeIf { it.isNotBlank() }?.let { parameter("targetUidHex", it) }
                        q.orderType?.takeIf { it.isNotEmpty() }?.let { parameter("orderType", it.joinToString(",")) }
                        q.status?.takeIf { it.isNotEmpty() }?.let { parameter("status", it.joinToString(",")) }
                        q.assigned?.takeIf { it.isNotEmpty() }?.let { parameter("assigned", it.joinToString(",")) }
                        q.progress?.takeIf { it.isNotEmpty() }?.let { parameter("progress", it.joinToString(",")) }
                        q.createdStart?.takeIf { it.isNotBlank() }?.let { parameter("createdStart", it) }
                        q.createdEnd?.takeIf { it.isNotBlank() }?.let { parameter("createdEnd", it) }
                        q.updatedStart?.takeIf { it.isNotBlank() }?.let { parameter("updatedStart", it) }
                        q.updatedEnd?.takeIf { it.isNotBlank() }?.let { parameter("updatedEnd", it) }
                    }
                }.body()

                if (response.success && response.data != null) {
                    val paginatedData: PaginatedResponse<Order> = json.decodeFromJsonElement(response.data)
                    
                    val elapsed = System.currentTimeMillis() - startTime
                    if (isAppend && elapsed < MIN_APPEND_DELAY_MS) {
                        delay(MIN_APPEND_DELAY_MS - elapsed)
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
                if (e is CancellationException) throw e
                AppLogger.error(context, e, "Failed to fetch orders", "AdminViewModel")
                val msg = e.message ?: context.getString(R.string.err_unknown)
                if (isAppend) _uiState.update { it.copy(isAppendingOrders = false, appendError = msg) }
                else _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
        return fetchOrdersJob!!
    }

    fun fetchWorkers(context: Context): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.err_not_logged_in)) }
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
                if (e is CancellationException) throw e
                AppLogger.error(context, e, "Failed to fetch workers", "AdminViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: context.getString(R.string.err_unknown)) }
            }
        }
    }

    fun assignOrder(context: Context, orderId: Int, workerId: Int?): Job {
        return viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                val token = getToken(context)
                if (token == null) {
                    _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.err_not_logged_in)) }
                    return@launch
                }

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
                    // When assigning an order, refresh with the current query so we don't lose filters
                    fetchOrders(context, _uiState.value.currentOrderQuery).join()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.error(context, e, "Failed to assign order", "AdminViewModel")
                _uiState.update { it.copy(isLoading = false, error = e.message ?: context.getString(R.string.err_unknown)) }
            }
        }
    }

    fun fetchLogs(context: Context, query: LogSearchQuery? = null, isAppend: Boolean = false): Job {
        if (isAppend && (_uiState.value.isAppendingLogs || !_uiState.value.hasMoreLogs)) {
            return viewModelScope.launch { }
        }
        
        // Cancel previous fetch if we are performing a fresh request
        if (!isAppend) fetchLogsJob?.cancel()
        
        fetchLogsJob = viewModelScope.launch {
            val effectiveQuery = if (isAppend) _uiState.value.currentLogQuery else query
            
            if (isAppend) {
                _uiState.update { it.copy(isAppendingLogs = true, appendError = null) }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = true, 
                        error = null, 
                        logs = emptyList(), 
                        nextLogsCursor = null, 
                        hasMoreLogs = true,
                        currentLogQuery = effectiveQuery
                    ) 
                }
            }
            
            val startTime = System.currentTimeMillis()
            
            try {
                val token = getToken(context)
                if (token == null) {
                    val msg = context.getString(R.string.err_not_logged_in)
                    if (isAppend) _uiState.update { it.copy(isAppendingLogs = false, appendError = msg) }
                    else _uiState.update { it.copy(isLoading = false, error = msg) }
                    return@launch
                }

                val response: ApiResponse = ApiClient.client.get("orders/logs") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    parameter("limit", DEFAULT_PAGE_SIZE)
                    if (isAppend) {
                        _uiState.value.nextLogsCursor?.let { parameter("cursor", it) }
                    }
                    effectiveQuery?.let { q ->
                        q.orderId?.takeIf { it.isNotEmpty() }?.let { parameter("orderId", it.joinToString(",")) }
                        q.action?.takeIf { it.isNotEmpty() }?.let { parameter("action", it.joinToString(",")) }
                        q.result?.takeIf { it.isNotEmpty() }?.let { parameter("result", it.joinToString(",")) }
                        q.operator?.takeIf { it.isNotEmpty() }?.let { parameter("workerId", it.joinToString(",")) }
                        q.uidHex?.takeIf { it.isNotBlank() }?.let { parameter("uidHex", it) }
                        q.orderType?.takeIf { it.isNotEmpty() }?.let { parameter("orderType", it.joinToString(",")) }
                        q.startTime?.takeIf { it.isNotBlank() }?.let { parameter("startTime", it) }
                        q.endTime?.takeIf { it.isNotBlank() }?.let { parameter("endTime", it) }
                    }
                }.body()

                if (response.success && response.data != null) {
                    val paginatedData: PaginatedResponse<LogEntry> = json.decodeFromJsonElement(response.data)
                    
                    val elapsed = System.currentTimeMillis() - startTime
                    if (isAppend && elapsed < MIN_APPEND_DELAY_MS) {
                        delay(MIN_APPEND_DELAY_MS - elapsed)
                    }
                    
                    if (isAppend) {
                        _uiState.update { 
                            it.copy(
                                isAppendingLogs = false,
                                logs = it.logs + paginatedData.items,
                                nextLogsCursor = paginatedData.nextCursor,
                                hasMoreLogs = paginatedData.hasMore
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                logs = paginatedData.items,
                                nextLogsCursor = paginatedData.nextCursor,
                                hasMoreLogs = paginatedData.hasMore
                            ) 
                        }
                    }
                } else {
                    if (isAppend) _uiState.update { it.copy(isAppendingLogs = false, appendError = response.message) }
                    else _uiState.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.error(context, e, "Failed to fetch logs", "AdminViewModel")
                val msg = e.message ?: context.getString(R.string.err_unknown)
                if (isAppend) _uiState.update { it.copy(isAppendingLogs = false, appendError = msg) }
                else _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
        return fetchLogsJob!!
    }
    
    fun fetchAnalysisSummary(context: Context): Job {
        return viewModelScope.launch {
            try {
                val token = getToken(context) ?: return@launch
                val response: ApiResponse = ApiClient.client.get("orders/analysis/summary") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.body()

                if (response.success && response.data != null) {
                    val summary: AdminAnalysisSummary = json.decodeFromJsonElement(response.data)
                    _uiState.update { it.copy(analysisSummary = summary) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.error(context, e, "Failed to fetch analysis summary", "AdminViewModel")
            }
        }
    }
}