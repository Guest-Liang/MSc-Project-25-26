package icu.guestliang.nfcworkflow.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import android.util.Base64

object JwtUtils {
    private val json = Json { ignoreUnknownKeys = true }

    fun getExpiryMillis(token: String?): Long {
        if (token == null) return 0L
        val parts = token.split(".")
        if (parts.size < 2) return 0L

        return try {
            val payload = String(Base64.decode(parts[1], Base64.DEFAULT))
            val jsonObject = json.parseToJsonElement(payload).jsonObject
            val exp = jsonObject["exp"]?.jsonPrimitive?.longOrNull
            // JWT exp 是秒，Java 时间戳是毫秒
            if (exp != null) exp * 1000L else 0L
        } catch (e: Exception) {
            0L
        }
    }
}
