package icu.guestliang.nfcworkflow.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQueryLogsScreen(
    navController: NavController,
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    AppLogger.debug(context, "AdminQueryLogsScreen recomposed", "UI")

    val uiState by viewModel.uiState.collectAsState()
    var isInitialLoad by remember { mutableStateOf(true) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.fetchLogs(context).join()
        isInitialLoad = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_query_logs)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isInitialLoad || (uiState.isLoading && uiState.logs.isEmpty()) -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Network Error",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.error_network_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: stringResource(R.string.error_network_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { 
                                isInitialLoad = true
                                viewModel.clearMessages()
                                coroutineScope.launch {
                                    viewModel.fetchLogs(context).join()
                                    isInitialLoad = false
                                }
                            },
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.error_retry_btn))
                            }
                        }
                    }
                }
                else -> {
                    LaunchedEffect(uiState.logs) {
                        if (uiState.logs.isEmpty()) {
                            showEmptyDialog = true
                        }
                    }

                    if (showEmptyDialog) {
                        AlertDialog(
                            onDismissRequest = { showEmptyDialog = false },
                            title = { Text(stringResource(R.string.dialog_empty_state_title)) },
                            text = { Text(stringResource(R.string.admin_logs_no_data)) },
                            confirmButton = {
                                TextButton(onClick = { showEmptyDialog = false; navController.popBackStack() }) {
                                    Text(stringResource(R.string.ok))
                                }
                            }
                        )
                    }

                    if (uiState.logs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.admin_logs_no_data),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.logs, key = { it.id }) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.admin_log_item_title, log.id),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(text = stringResource(R.string.admin_log_action, log.action))
                                        Text(text = stringResource(R.string.admin_log_target, "Order", log.order_id?.toString() ?: "N/A"))
                                        Text(text = stringResource(R.string.admin_log_user, log.operator_id ?: 0))
                                        Text(text = stringResource(R.string.admin_log_time, log.timestamp ?: "N/A"), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
