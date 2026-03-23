package icu.guestliang.nfcworkflow.ui.login

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.network.ApiResponse
import icu.guestliang.nfcworkflow.network.LoginRequest
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    object Success : RegisterState()
    data class Error(val isEmptyFields: Boolean = false, val errorMessage: String? = null) : RegisterState()
}

class RegisterViewModel : ViewModel() {

    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState

    private suspend fun getAdminAuthToken(context: Context, adminUser: String, adminPass: String): String? {
        val loginResponse = ApiClient.client.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = adminUser, password = adminPass))
        }.body<ApiResponse>()

        if (!loginResponse.success) {
            _registerState.value = RegisterState.Error(errorMessage = context.getString(R.string.err_admin_login_failed, loginResponse.message))
            return null
        }

        val tokenStr = try {
            loginResponse.data?.jsonObject?.get("token")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }

        if (tokenStr.isNullOrEmpty()) {
            _registerState.value = RegisterState.Error(errorMessage = context.getString(R.string.err_admin_no_token))
            return null
        }

        return tokenStr
    }

    fun triggerEmptyFieldsError(context: Context) {
        _registerState.value = RegisterState.Error(isEmptyFields = true, errorMessage = context.getString(R.string.err_empty_fields))
    }

    private fun submitAccountAction(context: Context, targetUser: String, targetPass: String, adminUser: String, adminPass: String, endpoint: String) {
        if (targetUser.isBlank() || targetPass.isBlank() || adminUser.isBlank() || adminPass.isBlank()) {
            _registerState.value = RegisterState.Error(isEmptyFields = true, errorMessage = context.getString(R.string.err_empty_fields))
            return
        }

        viewModelScope.launch {
            _registerState.value = RegisterState.Loading
            try {
                val tokenStr = getAdminAuthToken(context, adminUser, adminPass) ?: return@launch

                val response = ApiClient.client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $tokenStr")
                    setBody(LoginRequest(username = targetUser, password = targetPass))
                }.body<ApiResponse>()

                if (response.success) {
                    _registerState.value = RegisterState.Success
                } else {
                    _registerState.value = RegisterState.Error(errorMessage = response.message)
                }
            } catch (e: ClientRequestException) {
                try {
                    val errorResponse = e.response.body<ApiResponse>()
                    _registerState.value = RegisterState.Error(errorMessage = errorResponse.message)
                } catch (_: Exception) {
                    _registerState.value = RegisterState.Error(errorMessage = e.message)
                }
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error(errorMessage = e.message ?: context.getString(R.string.err_unknown))
            }
        }
    }

    fun registerAdmin(context: Context, newAdminUser: String, newAdminPass: String, adminUser: String, adminPass: String) {
        submitAccountAction(context, newAdminUser, newAdminPass, adminUser, adminPass, "auth/register-admin")
    }

    fun resetAdminPassword(context: Context, adminUserToReset: String, newAdminPass: String, adminUser: String, adminPass: String) {
        submitAccountAction(context, adminUserToReset, newAdminPass, adminUser, adminPass, "auth/reset-admin-password")
    }

    fun registerWorker(context: Context, workerUser: String, workerPass: String, adminUser: String, adminPass: String) {
        submitAccountAction(context, workerUser, workerPass, adminUser, adminPass, "auth/register-worker")
    }

    fun resetWorkerPassword(context: Context, workerUserToReset: String, newWorkerPass: String, adminUser: String, adminPass: String) {
        submitAccountAction(context, workerUserToReset, newWorkerPass, adminUser, adminPass, "auth/reset-worker-password")
    }

    fun resetState() {
        _registerState.value = RegisterState.Idle
    }
}
