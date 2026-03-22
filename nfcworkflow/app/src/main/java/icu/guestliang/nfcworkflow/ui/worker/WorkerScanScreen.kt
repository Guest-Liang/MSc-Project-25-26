package icu.guestliang.nfcworkflow.ui.worker

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.nfc.parseNfcTagData
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.findActivity
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerScanScreen(
    navController: NavController,
    orderId: Int,
    viewModel: WorkerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    
    val uiState by viewModel.uiState.collectAsState()
    
    var isScanning by remember { mutableStateOf(true) }
    
    val nfcNotSupportedStr = stringResource(id = R.string.nfc_not_supported)
    val nfcDisabledStr = stringResource(id = R.string.nfc_disabled_prompt)

    LaunchedEffect(Unit) {
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        if (nfcAdapter == null) {
            Toast.makeText(context, nfcNotSupportedStr, Toast.LENGTH_SHORT).show()
            isScanning = false
        } else if (!nfcAdapter.isEnabled) {
            Toast.makeText(context, nfcDisabledStr, Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            isScanning = false
        }
        viewModel.clearMessages()
    }

    DisposableEffect(isScanning) {
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        
        if (isScanning && nfcAdapter != null && nfcAdapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or 
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or 
                        NfcAdapter.FLAG_READER_NFC_V
            
            val readerCallback = NfcAdapter.ReaderCallback { tag ->
                val parsedData = parseNfcTagData(tag, context)
                // Need to perform scan request
                activity.runOnUiThread {
                    viewModel.scanOrder(
                        context = context,
                        orderId = orderId,
                        uidHex = parsedData.uidHex,
                        rawText = parsedData.rawText,
                        ndefText = parsedData.ndefText
                    )
                }
            }
            nfcAdapter.enableReaderMode(activity, readerCallback, flags, null)
        }

        onDispose {
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.worker_scan_to_execute)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimensions.SpaceL),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
                ) {
                    item {
                        if (uiState.actionSuccess && uiState.scanResponseData != null) {
                            isScanning = false
                            val scanData = uiState.scanResponseData!!
                            SplicedColumnGroup(title = stringResource(R.string.worker_complete_result_title)) {
                                item {
                                    Column(modifier = Modifier.padding(Dimensions.SpaceL)) {
                                        if (scanData.completed) {
                                            Text(
                                                text = stringResource(R.string.worker_scan_success_completed),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.worker_scan_success_matched),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (scanData.orderType == "sequence") {
                                            Text(
                                                text = stringResource(R.string.worker_scan_success_sequence, scanData.completedStepIndex ?: 0),
                                                modifier = Modifier.padding(top = Dimensions.SpaceS)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (uiState.error != null) {
                            val errData = uiState.scanErrorData
                            SplicedColumnGroup(title = stringResource(R.string.worker_scan_error_title)) {
                                item {
                                    Column(modifier = Modifier.padding(Dimensions.SpaceL)) {
                                        Text(
                                            text = uiState.error!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        
                                        if (errData != null) {
                                            if (errData.expectedStepIndex != null && errData.scannedStepIndex != null) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.worker_scan_error_out_of_order,
                                                        errData.expectedStepIndex,
                                                        errData.expectedDisplayName ?: "",
                                                        errData.scannedStepIndex
                                                    ),
                                                    modifier = Modifier.padding(top = Dimensions.SpaceS)
                                                )
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.worker_scan_error_mismatch),
                                                    modifier = Modifier.padding(top = Dimensions.SpaceS)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (isScanning) {
                            Text(
                                text = stringResource(R.string.nfc_dialog_prompt),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}
