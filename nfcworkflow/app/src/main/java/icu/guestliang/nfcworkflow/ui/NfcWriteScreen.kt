package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.components.SplicedJumpPageWidget
import icu.guestliang.nfcworkflow.ui.components.SplicedSwitchWidget
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.util.Locale
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun NfcWriteScreen(navController: NavController, viewModel: NfcViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    AppLogger.debug(context, "NfcWriteScreen recomposed", "UI")

    val nfcNotSupportedStr = stringResource(id = R.string.nfc_not_supported)
    val nfcDisabledStr = stringResource(id = R.string.nfc_disabled_prompt)
    val toastEmptyStr = stringResource(id = R.string.nfc_write_buffer_toast_empty)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(
                top = Dimensions.SpaceS,
                bottom = Dimensions.SpaceL
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
        ) {
            item {
                SplicedColumnGroup(title = stringResource(id = R.string.nfc_write_group_switch)) {
                    item {
                        SplicedSwitchWidget(
                            icon = Icons.Default.Edit,
                            title = stringResource(id = R.string.nfc_write_title),
                            description = if (uiState.writeBuffer.isEmpty()) stringResource(R.string.nfc_write_empty_buffer_desc) else stringResource(R.string.nfc_write_filled_buffer_desc, uiState.writeBuffer.size),
                            checked = uiState.isWriteSwitchChecked,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (uiState.writeBuffer.isEmpty()) {
                                        Toast.makeText(context, toastEmptyStr, Toast.LENGTH_LONG).show()
                                        return@SplicedSwitchWidget
                                    }
                                    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                                    if (nfcAdapter == null) {
                                        Toast.makeText(context, nfcNotSupportedStr, Toast.LENGTH_SHORT).show()
                                    } else if (!nfcAdapter.isEnabled) {
                                        Toast.makeText(context, nfcDisabledStr, Toast.LENGTH_SHORT).show()
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    } else {
                                        viewModel.updateState { it.copy(isWriteSwitchChecked = true, showWriteConfirmDialog = true) }
                                    }
                                } else {
                                    viewModel.updateState { 
                                        it.copy(isWriteSwitchChecked = false, showWriteDialog = false, writeResult = null) 
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(id = R.string.nfc_write_buffer_title)) {
                    item {
                        SplicedJumpPageWidget(
                            icon = Icons.Default.TextFields,
                            title = stringResource(id = R.string.nfc_write_type_text),
                            onClick = {
                                viewModel.updateState { 
                                    it.copy(showWriteInputDialog = true, writeInputDialogType = "text", writeInputText1 = "") 
                                }
                            }
                        )
                    }
                    item {
                        SplicedJumpPageWidget(
                            icon = Icons.Default.Email,
                            title = stringResource(id = R.string.nfc_write_type_email),
                            onClick = {
                                viewModel.updateState { 
                                    it.copy(showWriteInputDialog = true, writeInputDialogType = "email", writeInputText1 = "") 
                                }
                            }
                        )
                    }
                    item {
                        SplicedJumpPageWidget(
                            icon = Icons.Default.Call,
                            title = stringResource(id = R.string.nfc_write_type_phone),
                            onClick = {
                                viewModel.updateState { 
                                    it.copy(showWriteInputDialog = true, writeInputDialogType = "phone", writeInputText1 = "") 
                                }
                            }
                        )
                    }
                }
            }

            if (uiState.writeBuffer.isNotEmpty()) {
                item {
                    SplicedColumnGroup(title = stringResource(id = R.string.nfc_write_current_content_title)) {
                        uiState.writeBuffer.forEachIndexed { index, data ->
                            item {
                                val display = when (data) {
                                    is WriteData.Text -> data.text
                                    is WriteData.UriRecord -> data.display
                                }
                                SplicedJumpPageWidget(
                                    icon = Icons.Default.Nfc,
                                    title = "${data.typeName}: $display",
                                    onClick = {
                                        viewModel.removeWriteData(index)
                                        // If empty after removal, ensure we reset switch status
                                        if (uiState.writeBuffer.size == 1) { // 1 before removal means 0 after
                                            viewModel.updateState { 
                                                it.copy(isWriteSwitchChecked = false, showWriteDialog = false) 
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showWriteInputDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.updateState { it.copy(showWriteInputDialog = false) } },
            title = { Text(stringResource(id = R.string.nfc_write_input_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = uiState.writeInputText1,
                    onValueChange = { newText -> viewModel.updateState { it.copy(writeInputText1 = newText) } },
                    label = { Text(uiState.writeInputDialogType.uppercase(Locale.getDefault())) },
                    keyboardOptions = if (uiState.writeInputDialogType == "phone") KeyboardOptions(keyboardType = KeyboardType.Phone) else KeyboardOptions.Default
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (uiState.writeInputText1.isNotBlank()) {
                        val inputType = uiState.writeInputDialogType
                        val text = uiState.writeInputText1
                        val newRecord = when (inputType) {
                            "text" -> WriteData.Text(text)
                            "email" -> WriteData.UriRecord("mailto:$text", text)
                            "phone" -> WriteData.UriRecord("tel:$text", text)
                            else -> null
                        }
                        if (newRecord != null) {
                            viewModel.addWriteData(newRecord)
                        }
                    }
                    viewModel.updateState { it.copy(showWriteInputDialog = false) }
                }) {
                    Text(stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.updateState { it.copy(showWriteInputDialog = false) } }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (uiState.showWriteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                viewModel.updateState { it.copy(showWriteConfirmDialog = false, isWriteSwitchChecked = false) } 
            },
            title = { Text(stringResource(id = R.string.nfc_write_confirm_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.nfc_write_confirm_dialog_desc, uiState.writeBuffer.size))
                    Spacer(modifier = Modifier.height(Dimensions.SpaceS))
                    uiState.writeBuffer.forEach { data ->
                        val display = when (data) {
                            is WriteData.Text -> data.text
                            is WriteData.UriRecord -> data.display
                        }
                        Text("- ${data.typeName}: $display", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateState { it.copy(showWriteConfirmDialog = false, showWriteDialog = true) }
                }) {
                    Text(stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.updateState { it.copy(showWriteConfirmDialog = false, isWriteSwitchChecked = false) }
                }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (uiState.showWriteDialog) {
        NfcWriterDialog(
            onDismiss = {
                viewModel.updateState { it.copy(showWriteDialog = false, isWriteSwitchChecked = false, writeResult = null) }
            },
            writeBuffer = uiState.writeBuffer,
            writeResult = uiState.writeResult,
            onNfcWriteResult = { result ->
                viewModel.updateState { it.copy(writeResult = result) }
            }
        )
    }
}

@Composable
fun NfcWriterDialog(
    onDismiss: () -> Unit,
    writeBuffer: List<WriteData>,
    writeResult: String?,
    onNfcWriteResult: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    DisposableEffect(activity) {
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        
        if (nfcAdapter != null && nfcAdapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or 
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or 
                        NfcAdapter.FLAG_READER_NFC_V
            
            val readerCallback = NfcAdapter.ReaderCallback { tag ->
                CoroutineScope(Dispatchers.IO).launch {
                    val result = writeNfcTag(tag, writeBuffer, context)
                    withContext(Dispatchers.Main) {
                        onNfcWriteResult(result)
                    }
                }
            }
            
            nfcAdapter.enableReaderMode(activity, readerCallback, flags, null)
        }

        onDispose {
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.nfc_write_dialog_title)) },
        text = {
            if (writeResult == null) {
                Text(stringResource(id = R.string.nfc_write_dialog_prompt))
            } else {
                Text(stringResource(id = R.string.nfc_write_dialog_result, writeResult))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.nfc_write_dialog_close))
            }
        }
    )
}

@SuppressLint("UseStringResource")
fun writeNfcTag(tag: Tag, data: List<WriteData>, context: Context): String {
    val records = data.map { item ->
        when (item) {
            is WriteData.Text -> createTextRecord(item.text, Locale.getDefault(), true)
            is WriteData.UriRecord -> NdefRecord.createUri(item.uri.toUri())
        }
    }.toTypedArray()

    if (records.isEmpty()) return context.getString(R.string.nfc_write_result_no_content)

    val message = NdefMessage(records)

    try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            if (!ndef.isWritable) {
                ndef.close()
                return context.getString(R.string.nfc_write_result_read_only)
            }
            if (ndef.maxSize < message.toByteArray().size) {
                ndef.close()
                return context.getString(R.string.nfc_write_result_not_enough_space)
            }
            ndef.writeNdefMessage(message)
            ndef.close()
            return context.getString(R.string.nfc_write_result_success)
        } else {
            val format = NdefFormatable.get(tag)
            if (format != null) {
                format.connect()
                format.format(message)
                format.close()
                return context.getString(R.string.nfc_write_result_format_success)
            } else {
                return context.getString(R.string.nfc_write_result_unsupported)
            }
        }
    } catch (e: Exception) {
        return context.getString(R.string.nfc_write_result_error, e.message ?: context.getString(R.string.err_unknown))
    }
}

fun createTextRecord(payload: String, locale: Locale, encodeInUtf8: Boolean): NdefRecord {
    val langBytes = locale.language.toByteArray(Charset.forName("US-ASCII"))
    val utfEncoding = if (encodeInUtf8) Charset.forName("UTF-8") else Charset.forName("UTF-16")
    val textBytes = payload.toByteArray(utfEncoding)
    val utfBit = if (encodeInUtf8) 0 else (1 shl 7)
    val status = (utfBit + langBytes.size).toChar()
    
    val data = ByteArray(1 + langBytes.size + textBytes.size)
    data[0] = status.code.toByte()
    System.arraycopy(langBytes, 0, data, 1, langBytes.size)
    System.arraycopy(textBytes, 0, data, 1 + langBytes.size, textBytes.size)
    
    return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), data)
}
