package icu.guestliang.nfcworkflow.network

import icu.guestliang.nfcworkflow.data.ApiConstants
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    val client = HttpClient(Android) {
        expectSuccess = true // Ensure non-2xx responses throw exceptions

        defaultRequest {
            url(ApiConstants.BASE_URL)
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 5000
        }

        install(HttpCallValidator) {
            handleResponseExceptionWithRequest { exception, _ ->
                val clientException = exception as? ClientRequestException ?: return@handleResponseExceptionWithRequest
                if (clientException.response.status == HttpStatusCode.Unauthorized) {
                    AuthManager.emitForceLogout()
                }
            }
        }
    }
}
