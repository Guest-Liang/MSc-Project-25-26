package icu.guestliang.nfcworkflow.ui.components

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.nfc.parseNfcTagData
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.findActivity
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val NFC_SCANNER_TIMEOUT_MS = 60_000L

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
    val lifecycleOwner = LocalLifecycleOwner.current
    var timedOut by remember { mutableStateOf(false) }
    
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

    LaunchedEffect(Unit) {
        delay(NFC_SCANNER_TIMEOUT_MS)
        timedOut = true
    }

    DisposableEffect(activity, lifecycleOwner, timedOut) {
        val currentActivity = activity
        val nfcAdapter = currentActivity?.let { NfcAdapter.getDefaultAdapter(it) }
        val shouldRead = !timedOut && nfcAdapter?.isEnabled == true
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V
        val readerCallback = NfcAdapter.ReaderCallback { tag ->
            val parsedData = parseNfcTagData(tag, context)
            currentActivity?.runOnUiThread {
                onScanned(parsedData.uidHex, parsedData.ndefText)
            }
        }

        fun enableReader() {
            if (shouldRead) {
                nfcAdapter.enableReaderMode(currentActivity, readerCallback, flags, null)
            }
        }

        fun disableReader() {
            if (currentActivity != null && nfcAdapter != null) {
                nfcAdapter.disableReaderMode(currentActivity)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enableReader()
                Lifecycle.Event.ON_PAUSE -> disableReader()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            enableReader()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            disableReader()
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
                Text(stringResource(id = if (timedOut) R.string.nfc_scan_timeout else R.string.nfc_dialog_prompt))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
