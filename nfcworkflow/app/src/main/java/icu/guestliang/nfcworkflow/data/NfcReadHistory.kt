package icu.guestliang.nfcworkflow.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.content.Context
import androidx.core.content.edit

@Serializable
data class NfcReadRecord(
    val id: String, // UUID
    val timestamp: Long,
    val uid: String, // from result
    val details: String // The full result string
)

object NfcHistoryManager {
    private const val PREFS_NAME = "nfc_history_prefs"
    private const val KEY_HISTORY = "history_list"

    private val _historyFlow = MutableStateFlow<List<NfcReadRecord>>(emptyList())
    val historyFlow: StateFlow<List<NfcReadRecord>> = _historyFlow.asStateFlow()

    suspend fun loadHistory(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        try {
            val list = Json.decodeFromString<List<NfcReadRecord>>(jsonString)
            _historyFlow.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            _historyFlow.value = emptyList()
        }
    }

    suspend fun addRecord(context: Context, record: NfcReadRecord) = withContext(Dispatchers.IO) {
        val currentList = _historyFlow.value.toMutableList()
        currentList.add(0, record)
        saveHistory(context, currentList)
        _historyFlow.value = currentList
    }

    suspend fun deleteRecords(context: Context, idsToDelete: Set<String>) = withContext(Dispatchers.IO) {
        val currentList = _historyFlow.value.filter { it.id !in idsToDelete }
        saveHistory(context, currentList)
        _historyFlow.value = currentList
    }

    private fun saveHistory(context: Context, list: List<NfcReadRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = Json.encodeToString(list)
        prefs.edit {
            putString(KEY_HISTORY, jsonString)
        }
    }
}
