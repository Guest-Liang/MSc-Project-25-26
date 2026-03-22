package icu.guestliang.nfcworkflow.ui.worker

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.components.CustomDateTimePickerDialog
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import kotlinx.coroutines.delay
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkerHistoryScreen(
    navController: NavController,
    viewModel: WorkerViewModel = viewModel()
) {
    val context = LocalContext.current
    AppLogger.debug(context, "WorkerHistoryScreen recomposed", "UI")

    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isFilterExpanded by remember { mutableStateOf(isLandscape) }
    var showFallbackDialog by remember { mutableStateOf(false) }

    // Query states
    var orderIdQuery by remember { mutableStateOf("") }
    var uidHexQuery by remember { mutableStateOf("") }
    val selectedActions = remember { mutableStateListOf<String>() }
    val selectedResults = remember { mutableStateListOf<String>() }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }

    val actionOptions = listOf("scan", "completed")
    val resultOptions = listOf("standard_matched", "sequence_step_completed", "mismatch", "out_of_order", "duplicate")

    LaunchedEffect(Unit) {
        viewModel.fetchHistory(context)
    }

    LaunchedEffect(uiState.isFallbackTriggered) {
        if (uiState.isFallbackTriggered) {
            showFallbackDialog = true
            delay(3000)
            showFallbackDialog = false
            viewModel.clearFallbackTriggered()
        }
    }

    LaunchedEffect(isLandscape) {
        isFilterExpanded = isLandscape
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.worker_view_history)) },
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
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Filter Panel Side
                Card(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxSize()
                        .padding(start = Dimensions.SpaceL, top = Dimensions.SpaceL, bottom = Dimensions.SpaceL, end = Dimensions.SpaceS),
                    shape = RoundedCornerShape(Dimensions.Radius.M),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.Low)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(Dimensions.SpaceL)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = stringResource(R.string.admin_advanced_search),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = Dimensions.SpaceS)
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceM)
                        ) {
                            OutlinedTextField(
                                value = orderIdQuery,
                                onValueChange = { orderIdQuery = it },
                                label = { Text(stringResource(R.string.admin_log_search_order_id)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = uidHexQuery,
                                onValueChange = { uidHexQuery = it },
                                label = { Text(stringResource(R.string.admin_order_nfc_hint_optional)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Text(stringResource(R.string.worker_history_filter_action_title), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                actionOptions.forEach { action ->
                                    val isSelected = selectedActions.contains(action)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = dropUnlessResumed {
                                            if (isSelected) selectedActions.remove(action)
                                            else selectedActions.add(action)
                                        },
                                        label = { Text(action) }
                                    )
                                }
                            }

                            Text(stringResource(R.string.worker_history_filter_result_title), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                resultOptions.forEach { result ->
                                    val isSelected = selectedResults.contains(result)
                                    val localizedRes = when (result) {
                                        "standard_matched" -> stringResource(R.string.worker_history_result_matched)
                                        "sequence_step_completed" -> stringResource(R.string.worker_history_result_step_completed)
                                        "mismatch" -> stringResource(R.string.worker_history_result_mismatch)
                                        "out_of_order" -> stringResource(R.string.worker_history_result_out_of_order)
                                        "duplicate" -> stringResource(R.string.worker_history_result_duplicate)
                                        else -> result
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = dropUnlessResumed {
                                            if (isSelected) selectedResults.remove(result)
                                            else selectedResults.add(result)
                                        },
                                        label = { Text(localizedRes) }
                                    )
                                }
                            }

                            DateTimeSelectorField(
                                label = stringResource(R.string.admin_log_search_start_time),
                                value = startTime,
                                onDateTimeSelected = { startTime = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DateTimeSelectorField(
                                label = stringResource(R.string.admin_log_search_end_time),
                                value = endTime,
                                onDateTimeSelected = { endTime = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = Dimensions.SpaceS),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = dropUnlessResumed {
                                orderIdQuery = ""
                                uidHexQuery = ""
                                selectedActions.clear()
                                selectedResults.clear()
                                startTime = ""
                                endTime = ""
                                viewModel.fetchHistory(context, null)
                            }) {
                                Text(stringResource(R.string.admin_search_clear_btn))
                            }
                            Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                            Button(onClick = dropUnlessResumed {
                                val orderIds = orderIdQuery.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val query = WorkerHistoryQuery(
                                    orderId = orderIds.ifEmpty { null },
                                    action = selectedActions.ifEmpty { null },
                                    result = selectedResults.ifEmpty { null },
                                    uidHex = uidHexQuery.ifBlank { null },
                                    startTime = startTime.ifBlank { null },
                                    endTime = endTime.ifBlank { null }
                                )
                                viewModel.fetchHistory(context, query)
                            }) {
                                Text(stringResource(R.string.admin_search_btn))
                            }
                        }
                    }
                }

                // Results Panel
                Box(modifier = Modifier.weight(0.55f).fillMaxSize()) {
                    HistoryResultsList(uiState = uiState)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Filter Panel Portrait
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.SpaceL),
                    shape = RoundedCornerShape(Dimensions.Radius.M),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.Low)
                ) {
                    Column(modifier = Modifier.padding(Dimensions.SpaceL)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = dropUnlessResumed { isFilterExpanded = !isFilterExpanded })
                                .padding(vertical = Dimensions.SpaceS),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.admin_advanced_search),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isFilterExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = stringResource(R.string.cd_toggle_filters),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = isFilterExpanded) {
                            Column(
                                modifier = Modifier
                                    .padding(top = Dimensions.SpaceL)
                                    .weight(weight = 1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceM)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                    OutlinedTextField(
                                        value = orderIdQuery,
                                        onValueChange = { orderIdQuery = it },
                                        label = { Text(stringResource(R.string.admin_log_search_order_id)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = uidHexQuery,
                                        onValueChange = { uidHexQuery = it },
                                        label = { Text(stringResource(R.string.admin_order_nfc_hint_optional)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                Text(stringResource(R.string.worker_history_filter_action_title), style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                    actionOptions.forEach { action ->
                                        val isSelected = selectedActions.contains(action)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = dropUnlessResumed {
                                                if (isSelected) selectedActions.remove(action)
                                                else selectedActions.add(action)
                                            },
                                            label = { Text(action) }
                                        )
                                    }
                                }

                                Text(stringResource(R.string.worker_history_filter_result_title), style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                    resultOptions.forEach { result ->
                                        val isSelected = selectedResults.contains(result)
                                        val localizedRes = when (result) {
                                            "standard_matched" -> stringResource(R.string.worker_history_result_matched)
                                            "sequence_step_completed" -> stringResource(R.string.worker_history_result_step_completed)
                                            "mismatch" -> stringResource(R.string.worker_history_result_mismatch)
                                            "out_of_order" -> stringResource(R.string.worker_history_result_out_of_order)
                                            "duplicate" -> stringResource(R.string.worker_history_result_duplicate)
                                            else -> result
                                        }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = dropUnlessResumed {
                                                if (isSelected) selectedResults.remove(result)
                                                else selectedResults.add(result)
                                            },
                                            label = { Text(localizedRes) }
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                    DateTimeSelectorField(
                                        label = stringResource(R.string.admin_log_search_start_time),
                                        value = startTime,
                                        onDateTimeSelected = { startTime = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DateTimeSelectorField(
                                        label = stringResource(R.string.admin_log_search_end_time),
                                        value = endTime,
                                        onDateTimeSelected = { endTime = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = dropUnlessResumed {
                                        orderIdQuery = ""
                                        uidHexQuery = ""
                                        selectedActions.clear()
                                        selectedResults.clear()
                                        startTime = ""
                                        endTime = ""
                                        viewModel.fetchHistory(context, null)
                                    }) {
                                        Text(stringResource(R.string.admin_search_clear_btn))
                                    }
                                    Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                    Button(onClick = dropUnlessResumed {
                                        isFilterExpanded = false
                                        val orderIds = orderIdQuery.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        val query = WorkerHistoryQuery(
                                            orderId = orderIds.ifEmpty { null },
                                            action = selectedActions.ifEmpty { null },
                                            result = selectedResults.ifEmpty { null },
                                            uidHex = uidHexQuery.ifBlank { null },
                                            startTime = startTime.ifBlank { null },
                                            endTime = endTime.ifBlank { null }
                                        )
                                        viewModel.fetchHistory(context, query)
                                    }) {
                                        Text(stringResource(R.string.admin_search_btn))
                                    }
                                }
                            }
                        }
                    }
                }

                // Results List
                HistoryResultsList(uiState = uiState)
            }
        }

        if (showFallbackDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showFallbackDialog = false
                    viewModel.clearFallbackTriggered()
                },
                title = { Text(stringResource(R.string.dialog_empty_state_title)) },
                text = { Text(stringResource(R.string.admin_search_fallback_msg)) },
                confirmButton = {
                    TextButton(onClick = dropUnlessResumed { 
                        showFallbackDialog = false 
                        viewModel.clearFallbackTriggered()
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryResultsList(uiState: WorkerUiState) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Dimensions.SpaceL)
        ) {
            // Worker Summary Card
            uiState.historySummary?.let { summary ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.SpaceL, vertical = Dimensions.SpaceS),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.Low)
                    ) {
                        Column(modifier = Modifier.padding(Dimensions.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                            Text(text = stringResource(R.string.worker_history_summary_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.height(Dimensions.SpaceXS))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.worker_history_total_scans, summary.totalScanCount), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.worker_history_successful_scans, summary.successfulScanCount), style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.worker_history_mismatch_count, summary.mismatchCount), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.worker_history_out_of_order_count, summary.outOfOrderCount), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(stringResource(R.string.worker_history_completed_orders, summary.completedOrderCount), style = MaterialTheme.typography.bodyMedium)
                            if (summary.lastScanAt != null) {
                                Text(stringResource(R.string.worker_history_last_scan, summary.lastScanAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            } else {
                                Text(stringResource(R.string.worker_history_no_last_scan), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }

            if (uiState.history.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(Dimensions.SpaceXL), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.admin_logs_no_data),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.history) { log ->
                    SplicedColumnGroup(title = stringResource(R.string.admin_log_item_title, log.id)) {
                        item {
                            Column(
                                modifier = Modifier.padding(Dimensions.SpaceL),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
                            ) {
                                Text(text = stringResource(R.string.admin_log_action, log.action))
                                
                                val orderId = log.orderId ?: log.order_id
                                if (orderId != null) {
                                    val targetTitle = log.orderTitle ?: "Order"
                                    Text(text = stringResource(R.string.admin_log_target, targetTitle, orderId.toString()))
                                }
                                
                                if (log.result != null) {
                                    val localizedRes = when (log.result) {
                                        "standard_matched" -> stringResource(R.string.worker_history_result_matched)
                                        "sequence_step_completed" -> stringResource(R.string.worker_history_result_step_completed)
                                        "mismatch" -> stringResource(R.string.worker_history_result_mismatch)
                                        "out_of_order" -> stringResource(R.string.worker_history_result_out_of_order)
                                        "duplicate" -> stringResource(R.string.worker_history_result_duplicate)
                                        else -> log.result
                                    }
                                    Text(text = "结果: $localizedRes")
                                }
                                
                                if (log.scanUidHex != null) {
                                    Text(text = "扫描 UID: ${log.scanUidHex}", style = MaterialTheme.typography.bodySmall)
                                }

                                if (log.locationCode != null) {
                                    Text(text = "地点: ${log.displayName ?: log.locationCode}", style = MaterialTheme.typography.bodySmall)
                                }
                                
                                log.timestamp?.let {
                                    Text(text = stringResource(R.string.admin_log_time, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateTimeSelectorField(label: String, value: String, onDateTimeSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            label = { Text(label) },
            readOnly = true,
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Select Date and Time") },
            modifier = Modifier.fillMaxWidth(),
            enabled = false, 
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = dropUnlessResumed { showDialog = true })
        )
    }

    if (showDialog) {
        CustomDateTimePickerDialog(
            onDismissRequest = { showDialog = false },
            onConfirm = { resultStr ->
                onDateTimeSelected(resultStr)
                showDialog = false
            }
        )
    }
}
