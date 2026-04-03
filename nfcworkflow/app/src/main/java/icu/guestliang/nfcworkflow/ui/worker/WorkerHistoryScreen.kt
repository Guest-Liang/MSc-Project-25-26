package icu.guestliang.nfcworkflow.ui.worker

import dev.chrisbanes.haze.HazeState
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.components.CustomDateTimePickerDialog
import icu.guestliang.nfcworkflow.ui.components.NfcScannerDialog
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.getLocalizedStatus
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isFilterExpanded by remember { mutableStateOf(isLandscape) }
    var showNfcDialog by remember { mutableStateOf(false) }

    // Query states
    var orderIdQuery by remember { mutableStateOf("") }
    var uidHexQuery by remember { mutableStateOf("") }
    val selectedActions = remember { mutableStateListOf<String>() }
    val selectedResults = remember { mutableStateListOf<String>() }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }

    val actionOptions = listOf("scan", "completed")
    val resultOptions = listOf("standard_matched", "sequence_step_completed", "mismatch", "out_of_order", "duplicate")

    fun getCurrentQuery(): WorkerHistoryQuery? {
        val orderIds = orderIdQuery.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (orderIds.isEmpty() && selectedActions.isEmpty() && selectedResults.isEmpty() && uidHexQuery.isBlank() && startTime.isBlank() && endTime.isBlank()) return null
        return WorkerHistoryQuery(
            orderId = orderIds.ifEmpty { null },
            action = selectedActions.toList().ifEmpty { null },
            result = selectedResults.toList().ifEmpty { null },
            uidHex = uidHexQuery.ifBlank { null },
            startTime = startTime.ifBlank { null },
            endTime = endTime.ifBlank { null }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.fetchHistory(context)
    }

    LaunchedEffect(isLandscape) {
        isFilterExpanded = isLandscape
    }

    LaunchedEffect(uiState.appendError) {
        uiState.appendError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearAppendError()
        }
    }

    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.worker_view_history)) },
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
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding()
                            )
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
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = uidHexQuery,
                                            onValueChange = { uidHexQuery = it },
                                            label = { Text(stringResource(R.string.admin_order_nfc_hint_optional)) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        IconButton(onClick = { showNfcDialog = true }) {
                                            Icon(Icons.Default.Nfc, contentDescription = "Scan NFC", tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }

                                    Text(stringResource(R.string.worker_history_filter_action_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        actionOptions.forEach { action ->
                                            val isSelected = selectedActions.contains(action)
                                            val localizedAct = if (action == "scan") stringResource(R.string.admin_log_action_scan) else getLocalizedStatus(action)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = dropUnlessResumed {
                                                    if (isSelected) selectedActions.remove(action)
                                                    else selectedActions.add(action)
                                                },
                                                label = { Text(localizedAct) }
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
                                        viewModel.fetchHistory(context, getCurrentQuery())
                                    }) {
                                        Text(stringResource(R.string.admin_search_btn))
                                    }
                                }
                            }
                        }

                        // Results Panel
                        Box(modifier = Modifier.weight(0.55f).fillMaxSize()) {
                            HistoryResultsList(uiState = uiState, onLoadMore = {
                                viewModel.fetchHistory(context, isAppend = true)
                            }, contentPadding = PaddingValues(bottom = Dimensions.SpaceL))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        // Filter Panel Portrait
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = innerPadding.calculateTopPadding() + Dimensions.SpaceS,
                                    start = Dimensions.SpaceL,
                                    end = Dimensions.SpaceL,
                                    bottom = Dimensions.SpaceL
                                ),
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
                                        OutlinedTextField(
                                            value = orderIdQuery,
                                            onValueChange = { orderIdQuery = it },
                                            label = { Text(stringResource(R.string.admin_log_search_order_id)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = uidHexQuery,
                                                onValueChange = { uidHexQuery = it },
                                                label = { Text(stringResource(R.string.admin_order_nfc_hint_optional)) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            IconButton(onClick = { showNfcDialog = true }) {
                                                Icon(Icons.Default.Nfc, contentDescription = "Scan NFC", tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }

                                        Text(stringResource(R.string.worker_history_filter_action_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            actionOptions.forEach { action ->
                                                val isSelected = selectedActions.contains(action)
                                                val localizedAct = if (action == "scan") stringResource(R.string.admin_log_action_scan) else getLocalizedStatus(action)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = dropUnlessResumed {
                                                        if (isSelected) selectedActions.remove(action)
                                                        else selectedActions.add(action)
                                                    },
                                                    label = { Text(localizedAct) }
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
                                                viewModel.fetchHistory(context, getCurrentQuery())
                                            }) {
                                                Text(stringResource(R.string.admin_search_btn))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Results List
                        HistoryResultsList(uiState = uiState, onLoadMore = {
                            viewModel.fetchHistory(context, isAppend = true)
                        }, contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + Dimensions.SpaceL))
                    }
                }
            }
        }
        
        if (showNfcDialog) {
            NfcScannerDialog(
                onDismiss = { showNfcDialog = false },
                onScanned = { uidHex, _ ->
                    uidHexQuery = uidHex
                    showNfcDialog = false
                }
            )
        }
    }
}

@Composable
fun HistoryResultsList(uiState: WorkerUiState, onLoadMore: () -> Unit, contentPadding: PaddingValues) {
    if (uiState.isLoading && uiState.history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.error != null && uiState.history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
        }
    } else {
        val listState = rememberLazyListState()

        val isAtBottom by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItem >= totalItems - 1
            }
        }

        LaunchedEffect(isAtBottom, uiState.history.size) {
            if (isAtBottom && uiState.hasMoreHistory && !uiState.isAppendingHistory && !uiState.isLoading) {
                onLoadMore()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
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
                                val actStr = if (log.action == "scan") stringResource(R.string.admin_log_action_scan) else getLocalizedStatus(log.action)
                                Text(text = stringResource(R.string.admin_log_action, actStr), color = MaterialTheme.colorScheme.onSurface)
                                
                                val orderId = log.orderId ?: log.order_id
                                if (orderId != null) {
                                    val targetTitle = log.orderTitle ?: "Order"
                                    Text(text = stringResource(R.string.admin_log_target, targetTitle, orderId.toString()), color = MaterialTheme.colorScheme.onSurface)
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
                                    Text(text = stringResource(R.string.admin_log_result, localizedRes), color = MaterialTheme.colorScheme.onSurface)
                                }
                                
                                if (log.scanUidHex != null) {
                                    Text(text = stringResource(R.string.admin_log_scan_uid, log.scanUidHex), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (log.locationCode != null) {
                                    val locName = log.displayName ?: log.locationCode
                                    Text(text = stringResource(R.string.admin_log_location, locName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                log.timestamp?.let {
                                    Text(text = stringResource(R.string.admin_log_time, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                if (uiState.isAppendingHistory) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.SpaceM),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
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