package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.nfc.parseNfcTag
import icu.guestliang.nfcworkflow.ui.components.SwitchGroup
import icu.guestliang.nfcworkflow.ui.components.SwitchItem
import icu.guestliang.nfcworkflow.utils.findActivity
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.widget.Toast
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

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    AppLogger.debug(context, "HomeScreen recomposed", "UI")

    var showNfcDialog by remember { mutableStateOf(false) }
    var nfcResult by remember { mutableStateOf<String?>(null) }
    val nfcNotSupportedStr = stringResource(id = R.string.nfc_not_supported)
    val nfcDisabledStr = stringResource(id = R.string.nfc_disabled_prompt)

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
                                if (isChecked) {
                                    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                                    if (nfcAdapter == null) {
                                        Toast.makeText(context, nfcNotSupportedStr, Toast.LENGTH_SHORT).show()
                                    } else if (!nfcAdapter.isEnabled) {
                                        Toast.makeText(context, nfcDisabledStr, Toast.LENGTH_SHORT).show()
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    } else {
                                        showNfcDialog = true
                                    }
                                } else {
                                    showNfcDialog = false
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
            nfcAdapter?.disableReaderMode(activity)
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
