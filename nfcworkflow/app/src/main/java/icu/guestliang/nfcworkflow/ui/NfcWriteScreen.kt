@file:Suppress("UseStringResource")
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.navigation.NavController

sealed class WriteData(val typeName: String) {
    data class Text(val text: String) : WriteData("Text")
    data class UriRecord(val uri: String, val display: String) : WriteData("URI")
}

@Composable
fun NfcWriteScreen(navController: NavController) {
    val context = LocalContext.current
    AppLogger.debug(context, "NfcWriteScreen recomposed", "UI")

    var isSwitchChecked by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showWriteDialog by remember { mutableStateOf(false) }
    var writeResult by remember { mutableStateOf<String?>(null) }
    
    val writeBuffer = remember { mutableStateListOf<WriteData>() }
    
    var showInputDialog by remember { mutableStateOf(false) }
    var inputDialogType by remember { mutableStateOf("") }
    var inputText1 by remember { mutableStateOf("") }
    
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
                            description = if (writeBuffer.isEmpty()) stringResource(R.string.nfc_write_empty_buffer_desc) else stringResource(R.string.nfc_write_filled_buffer_desc, writeBuffer.size),
                            checked = isSwitchChecked,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (writeBuffer.isEmpty()) {
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
                                        isSwitchChecked = true
                                        showConfirmDialog = true
                                    }
                                } else {
                                    isSwitchChecked = false
                                    showWriteDialog = false
                                    writeResult = null
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
                                inputDialogType = "text"
                                inputText1 = ""
                                showInputDialog = true
                            }
                        )
                    }
                    item {
                        SplicedJumpPageWidget(
                            icon = Icons.Default.Email,
                            title = stringResource(id = R.string.nfc_write_type_email),
                            onClick = {
                                inputDialogType = "email"
                                inputText1 = ""
                                showInputDialog = true
                            }
                        )
                    }
                    item {
                        SplicedJumpPageWidget(
                            icon = Icons.Default.Call,
                            title = stringResource(id = R.string.nfc_write_type_phone),
                            onClick = {
                                inputDialogType = "phone"
                                inputText1 = ""
                                showInputDialog = true
                            }
                        )
                    }
                }
            }

            if (writeBuffer.isNotEmpty()) {
                item {
                    SplicedColumnGroup(title = stringResource(id = R.string.nfc_write_current_content_title)) {
                        writeBuffer.forEachIndexed { index, data ->
                            item {
                                val display = when (data) {
                                    is WriteData.Text -> data.text
                                    is WriteData.UriRecord -> data.display
                                }
                                SplicedJumpPageWidget(
                                    icon = Icons.Default.Nfc,
                                    title = "${data.typeName}: $display",
                                    onClick = {
                                        writeBuffer.removeAt(index)
                                        if (writeBuffer.isEmpty()) {
                                            isSwitchChecked = false
                                            showWriteDialog = false
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

    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(stringResource(id = R.string.nfc_write_input_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = inputText1,
                    onValueChange = { inputText1 = it },
                    label = { Text(inputDialogType.uppercase(Locale.getDefault())) },
                    keyboardOptions = if (inputDialogType == "phone") KeyboardOptions(keyboardType = KeyboardType.Phone) else KeyboardOptions.Default
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputText1.isNotBlank()) {
                        when (inputDialogType) {
                            "text" -> writeBuffer.add(WriteData.Text(inputText1))
                            "email" -> writeBuffer.add(WriteData.UriRecord("mailto:$inputText1", inputText1))
                            "phone" -> writeBuffer.add(WriteData.UriRecord("tel:$inputText1", inputText1))
                        }
                    }
                    showInputDialog = false
                }) {
                    Text(stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showConfirmDialog = false
                isSwitchChecked = false 
            },
            title = { Text(stringResource(id = R.string.nfc_write_confirm_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.nfc_write_confirm_dialog_desc, writeBuffer.size))
                    Spacer(modifier = Modifier.height(Dimensions.SpaceS))
                    writeBuffer.forEach { data ->
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
                    showConfirmDialog = false
                    showWriteDialog = true
                }) {
                    Text(stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showConfirmDialog = false
                    isSwitchChecked = false 
                }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showWriteDialog) {
        NfcWriterDialog(
            onDismiss = {
                showWriteDialog = false
                isSwitchChecked = false
                writeResult = null
            },
            writeBuffer = writeBuffer,
            writeResult = writeResult,
            onNfcWriteResult = { result ->
                writeResult = result
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
        return context.getString(R.string.nfc_write_result_error, e.message ?: "Unknown error")
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
