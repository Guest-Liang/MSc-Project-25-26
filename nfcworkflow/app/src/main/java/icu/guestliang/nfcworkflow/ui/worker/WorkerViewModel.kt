package icu.guestliang.nfcworkflow.ui.worker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.network.ApiResponse
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.network.CompleteOrderRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.Json

data class WorkerUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val orders: List<Order> = emptyList(),
    val actionSuccess: Boolean = false
)

class WorkerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WorkerUiState())
    val uiState: StateFlow<WorkerUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

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
        _uiState.update { it.copy(actionSuccess = false) }
    }
}
