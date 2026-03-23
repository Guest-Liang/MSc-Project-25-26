package icu.guestliang.nfcworkflow.ui.components

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.nfc.parseNfcTagData
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.findActivity
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * A reusable NFC scanner dialog that handles permission checks, reader mode, 
 * and data parsing.
 */
@Composable
fun NfcScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (uidHex: String, ndefText: String?) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    
    val nfcNotSupportedStr = stringResource(id = R.string.nfc_not_supported)
    val nfcDisabledStr = stringResource(id = R.string.nfc_disabled_prompt)

    LaunchedEffect(Unit) {
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        if (nfcAdapter == null) {
            Toast.makeText(context, nfcNotSupportedStr, Toast.LENGTH_SHORT).show()
            onDismiss()
        } else if (!nfcAdapter.isEnabled) {
            Toast.makeText(context, nfcDisabledStr, Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            onDismiss()
        }
    }

    DisposableEffect(activity) {
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        
        if (nfcAdapter != null && nfcAdapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or 
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or 
                        NfcAdapter.FLAG_READER_NFC_V
            
            val readerCallback = NfcAdapter.ReaderCallback { tag ->
                val parsedData = parseNfcTagData(tag, context)
                activity.runOnUiThread {
                    onScanned(parsedData.uidHex, parsedData.ndefText)
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
        title = { Text(stringResource(id = R.string.nfc_dialog_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Nfc, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp).padding(bottom = Dimensions.SpaceL),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(id = R.string.nfc_dialog_prompt))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
