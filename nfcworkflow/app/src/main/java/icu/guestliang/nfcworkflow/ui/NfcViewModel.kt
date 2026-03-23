package icu.guestliang.nfcworkflow.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.ViewModel

// Moved from NfcWriteScreen
sealed class WriteData(val typeName: String) {
    data class Text(val text: String) : WriteData("Text")
    data class UriRecord(val uri: String, val display: String) : WriteData("URI")
}

data class NfcUiState(
    // Read Screen State
    val showReadHistoryPage: Boolean = false,
    val showReadNfcDialog: Boolean = false,
    val readNfcResult: String? = null,
    val readSelectionMode: Boolean = false,
    val readSelectedIds: Set<String> = emptySet(),
    val showReadDeleteConfirm: Boolean = false,

    // Write Screen State
    val writeBuffer: List<WriteData> = emptyList(),
    val isWriteSwitchChecked: Boolean = false,
    val showWriteConfirmDialog: Boolean = false,
    val showWriteDialog: Boolean = false,
    val showWriteInputDialog: Boolean = false,
    val writeInputDialogType: String = "",
    val writeInputText1: String = "",
    val writeResult: String? = null
)

class NfcViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NfcUiState())
    val uiState: StateFlow<NfcUiState> = _uiState.asStateFlow()

    fun updateState(updater: (NfcUiState) -> NfcUiState) {
        _uiState.update(updater)
    }

    // specific helpers for write buffer
    fun addWriteData(data: WriteData) {
        _uiState.update { state ->
            val newList = state.writeBuffer + data
            state.copy(writeBuffer = newList)
        }
    }

    fun removeWriteData(index: Int) {
        _uiState.update { state ->
            val newList = state.writeBuffer.toMutableList().apply { 
                if (index in indices) removeAt(index) 
            }
            state.copy(writeBuffer = newList)
        }
    }
    
    // specific helpers for read selection
    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val newSelection = state.readSelectedIds.toMutableSet()
            if (newSelection.contains(id)) {
                newSelection.remove(id)
            } else {
                newSelection.add(id)
            }
            state.copy(
                readSelectedIds = newSelection,
                readSelectionMode = newSelection.isNotEmpty() // Auto exit selection mode if empty
            )
        }
    }
    
    fun clearSelection() {
        _uiState.update { state ->
            state.copy(
                readSelectedIds = emptySet(),
                readSelectionMode = false,
                showReadDeleteConfirm = false
            )
        }
    }
}
