package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.components.SwitchGroup
import icu.guestliang.nfcworkflow.ui.components.SwitchItem
import java.nio.charset.Charset
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun getUriPrefix(identifierCode: Byte): String {
    return when (identifierCode.toInt()) {
        0x01 -> "http://www."
        0x02 -> "https://www."
        0x03 -> "http://"
        0x04 -> "https://"
        0x05 -> "tel:"
        0x06 -> "mailto:"
        else -> ""
    }
}

fun parseNfcTag(tag: Tag, context: Context): String {
    val sb = StringBuilder()
    
    val techList = tag.techList.joinToString(", ") { it.substringAfterLast('.') }
    sb.append(context.getString(R.string.nfc_tech_supported, techList)).append("\n")
    
    val mifareClassic = MifareClassic.get(tag)
    if (mifareClassic != null) {
        sb.append(context.getString(R.string.nfc_card_type_mifare_classic)).append("\n")
        sb.append(context.getString(R.string.nfc_storage_size, mifareClassic.size)).append("\n")
        sb.append(context.getString(R.string.nfc_sector_count, mifareClassic.sectorCount)).append("\n")
        sb.append(context.getString(R.string.nfc_block_count, mifareClassic.blockCount)).append("\n")
    }

    val mifareUltralight = MifareUltralight.get(tag)
    if (mifareUltralight != null) {
        sb.append(context.getString(R.string.nfc_card_type_mifare_ultralight)).append("\n")
    }

    val nfcA = NfcA.get(tag)
    if (nfcA != null) {
        val atqa = nfcA.atqa.reversedArray().joinToString("") { "%02X".format(it) }
        sb.append(context.getString(R.string.nfc_rf_tech_type_a)).append("\n")
        sb.append(context.getString(R.string.nfc_protocol_atqa, atqa)).append("\n")
        sb.append(context.getString(R.string.nfc_sak, "%02X".format(nfcA.sak))).append("\n")
    }
    
    val idHex = tag.id.joinToString("") { "%02X".format(it) }
    sb.append(context.getString(R.string.nfc_id, idHex)).append("\n")

    val ndef = Ndef.get(tag)
    if (ndef != null) {
        val isWritableStr = if (ndef.isWritable) context.getString(R.string.nfc_yes) else context.getString(R.string.nfc_no)
        val canMakeReadOnlyStr = if (ndef.canMakeReadOnly()) context.getString(R.string.nfc_yes) else context.getString(R.string.nfc_no)
        
        sb.append(context.getString(R.string.nfc_ndef_max_size, ndef.maxSize)).append("\n")
        sb.append(context.getString(R.string.nfc_ndef_is_writable, isWritableStr)).append("\n")
        sb.append(context.getString(R.string.nfc_ndef_can_make_read_only, canMakeReadOnlyStr)).append("\n")
        
        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            if (ndefMessage != null) {
                ndefMessage.records.forEachIndexed { index, record ->
                    sb.append(context.getString(R.string.nfc_record_index, index + 1)).append(" ")
                    if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                        try {
                            val payload = record.payload
                            val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
                            val languageCodeLength = payload[0].toInt() and 51
                            val text = String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, Charset.forName(textEncoding))
                            sb.append(context.getString(R.string.nfc_record_text, text)).append("\n")
                        } catch (e: Exception) {
                            sb.append(context.getString(R.string.nfc_record_text_fail)).append("\n")
                        }
                    } else if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI)) {
                        try {
                            val payload = record.payload
                            val prefix = getUriPrefix(payload[0])
                            val uri = String(payload, 1, payload.size - 1, Charset.forName("UTF-8"))
                            sb.append(context.getString(R.string.nfc_record_uri, prefix + uri)).append("\n")
                        } catch (e: Exception) {
                            sb.append(context.getString(R.string.nfc_record_uri_fail)).append("\n")
                        }
                    } else {
                        sb.append(context.getString(R.string.nfc_record_other, record.tnf.toInt())).append("\n")
                        val payloadHex = record.payload.joinToString("") { "%02X".format(it) }
                        val payloadStr = if (payloadHex.length > 30) payloadHex.take(30) + "..." else payloadHex
                        sb.append(context.getString(R.string.nfc_record_payload, payloadStr)).append("\n")
                    }
                }
            } else {
                sb.append(context.getString(R.string.nfc_no_ndef_data)).append("\n")
            }
        } catch (e: Exception) {
             sb.append(context.getString(R.string.nfc_read_ndef_fail)).append("\n")
        } finally {
            try { ndef.close() } catch (e: Exception) {}
        }
    } else {
        val ndefFormatable = NdefFormatable.get(tag)
        if (ndefFormatable != null) {
             sb.append(context.getString(R.string.nfc_support_ndef_format)).append("\n")
        }
    }
    
    return sb.toString().trim()
}


@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    AppLogger.debug(context, "HomeScreen recomposed", "UI")

    var showNfcDialog by remember { mutableStateOf(false) }
    var nfcResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .navigationBarsPadding(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SwitchGroup(
                    title = stringResource(id = R.string.example_switch_group_title),
                    items = listOf(
                        SwitchItem(
                            icon = Icons.Default.Nfc,
                            title = stringResource(id = R.string.nfc_read_title),
                            subtitle = stringResource(id = R.string.nfc_read_desc),
                            isChecked = showNfcDialog,
                            onCheckedChange = { isChecked ->
                                showNfcDialog = isChecked
                                if (!isChecked) {
                                    nfcResult = null
                                }
                            }
                        )
                    )
                )
            }
        }
    }

    if (showNfcDialog) {
        NfcReaderDialog(
            onDismiss = {
                showNfcDialog = false
                nfcResult = null
            },
            nfcResult = nfcResult,
            onNfcRead = { result ->
                nfcResult = result
            }
        )
    }
}

@Composable
fun NfcReaderDialog(
    onDismiss: () -> Unit,
    nfcResult: String?,
    onNfcRead: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    DisposableEffect(activity) {
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        
        if (nfcAdapter != null) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or 
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or 
                        NfcAdapter.FLAG_READER_NFC_V
            
            val readerCallback = NfcAdapter.ReaderCallback { tag ->
                val result = parseNfcTag(tag, context)
                onNfcRead(result)
            }
            
            nfcAdapter.enableReaderMode(activity, readerCallback, flags, null)
        }

        onDispose {
            if (nfcAdapter != null) {
                nfcAdapter.disableReaderMode(activity)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.nfc_dialog_title)) },
        text = {
            if (nfcResult == null) {
                Text(stringResource(id = R.string.nfc_dialog_prompt))
            } else {
                Text(stringResource(id = R.string.nfc_dialog_result, nfcResult))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.nfc_dialog_close))
            }
        }
    )
}
