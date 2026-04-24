package icu.guestliang.nfcworkflow.ui.worker

import dev.chrisbanes.haze.HazeState
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.nfc.parseNfcTagData
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.findActivity
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import kotlinx.coroutines.delay
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

private const val WORKER_SCAN_TIMEOUT_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerScanScreen(
    navController: NavController,
    orderId: Int,
    viewModel: WorkerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }
    
    val uiState by viewModel.uiState.collectAsState()
    val isScanning = uiState.isScanActive
    
    val nfcNotSupportedStr = stringResource(id = R.string.nfc_not_supported)
    val nfcDisabledStr = stringResource(id = R.string.nfc_disabled_prompt)

    LaunchedEffect(orderId) {
        viewModel.beginScanSession(orderId)
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        if (nfcAdapter == null) {
            Toast.makeText(context, nfcNotSupportedStr, Toast.LENGTH_SHORT).show()
            viewModel.stopScanSession()
        } else if (!nfcAdapter.isEnabled) {
            Toast.makeText(context, nfcDisabledStr, Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            viewModel.stopScanSession()
        }
    }

    LaunchedEffect(orderId, isScanning, uiState.isLoading) {
        if (isScanning && !uiState.isLoading) {
            delay(WORKER_SCAN_TIMEOUT_MS)
            if (viewModel.uiState.value.scanOrderId == orderId &&
                viewModel.uiState.value.isScanActive &&
                !viewModel.uiState.value.isLoading
            ) {
                viewModel.stopScanSession(timedOut = true)
            }
        }
    }

    DisposableEffect(activity, lifecycleOwner, isScanning, uiState.isLoading) {
        val currentActivity = activity
        val nfcAdapter = currentActivity?.let { NfcAdapter.getDefaultAdapter(it) }
        val shouldRead = isScanning && !uiState.isLoading && nfcAdapter?.isEnabled == true
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V
        val readerCallback = NfcAdapter.ReaderCallback { tag ->
            val parsedData = parseNfcTagData(tag, context)
            currentActivity?.runOnUiThread {
                viewModel.scanOrder(
                    context = context,
                    orderId = orderId,
                    uidHex = parsedData.uidHex,
                    rawText = parsedData.rawText,
                    ndefText = parsedData.ndefText
                )
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

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.worker_scan_to_execute)) },
                    navigationIcon = {
                        IconButton(onClick = dropUnlessResumed { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.haze(alpha = scrollBehavior.state.collapsedFraction)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + Dimensions.SpaceL,
                            bottom = innerPadding.calculateBottomPadding() + Dimensions.SpaceL,
                            start = Dimensions.SpaceL,
                            end = Dimensions.SpaceL
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
                    ) {
                        item {
                            if (uiState.actionSuccess && uiState.scanResponseData != null) {
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
                                                    modifier = Modifier.padding(top = Dimensions.SpaceS),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                        modifier = Modifier.padding(top = Dimensions.SpaceS),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    Text(
                                                        text = stringResource(R.string.worker_scan_error_mismatch),
                                                        modifier = Modifier.padding(top = Dimensions.SpaceS),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (uiState.scanTimedOut) {
                                Box(modifier = Modifier.fillMaxWidth().padding(Dimensions.SpaceXXXL), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.nfc_scan_timeout),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (isScanning) {
                                Box(modifier = Modifier.fillMaxWidth().padding(Dimensions.SpaceXXXL), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.nfc_dialog_prompt),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
