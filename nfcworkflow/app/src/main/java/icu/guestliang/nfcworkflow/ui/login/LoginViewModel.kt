package icu.guestliang.nfcworkflow.ui.login

import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.network.ApiResponse
import icu.guestliang.nfcworkflow.network.LoginRequest
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

sealed class HealthStatus {
    object Checking : HealthStatus()
    object Available : HealthStatus()
    object Unavailable : HealthStatus()
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val token: String?, val isWorker: Boolean) : LoginState()
    data class Error(val isEmptyFields: Boolean = false, val errorMessage: String? = null) : LoginState()
}

class LoginViewModel : ViewModel() {

    private val _healthStatus = MutableStateFlow<HealthStatus>(HealthStatus.Checking)
    val healthStatus: StateFlow<HealthStatus> = _healthStatus

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    init {
        checkApiHealth()
    }

    fun checkApiHealth() {
        viewModelScope.launch {
            _healthStatus.value = HealthStatus.Checking
            try {
                // Ktor throws an exception for non-2xx status codes.
                ApiClient.client.get("healthz")
                _healthStatus.value = HealthStatus.Available
            } catch (_: Exception) {
                // Network error or 5xx
                _healthStatus.value = HealthStatus.Unavailable
            }
        }
    }

    fun login(username: String, password: String, isWorker: Boolean) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error(isEmptyFields = true)
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val response = ApiClient.client.post("auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username = username, password = password))
                }.body<ApiResponse>()

                if (response.success) {
                    val tokenStr = try {
                        response.data?.jsonObject?.get("token")?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        null
                    }
                    _loginState.value = LoginState.Success(token = tokenStr, isWorker = isWorker)
                } else {
                    _loginState.value = LoginState.Error(errorMessage = response.message)
                }
            } catch (e: ClientRequestException) {
                // Handle 4xx HTTP responses (e.g. 401 Unauthorized, 400 Bad Request)
                try {
                    val errorResponse = e.response.body<ApiResponse>()
                    _loginState.value = LoginState.Error(errorMessage = errorResponse.message)
                } catch (_: Exception) {
                    _loginState.value = LoginState.Error(errorMessage = e.message)
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(errorMessage = e.message ?: "Unknown error")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
}
