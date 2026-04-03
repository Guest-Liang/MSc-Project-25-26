package icu.guestliang.nfcworkflow.ui.admin

import dev.chrisbanes.haze.HazeState
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.components.CustomDateTimePickerDialog
import icu.guestliang.nfcworkflow.ui.components.NfcScannerDialog
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.getLocalizedStatus
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showNfcDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Search query states
    var orderIdQuery by remember { mutableStateOf("") }
    var uidHexQuery by remember { mutableStateOf("") }
    val selectedWorkers = remember { mutableStateListOf<String>() }
    val selectedOrderTypes = remember { mutableStateListOf<String>() }
    val selectedActions = remember { mutableStateListOf<String>() }
    val selectedResults = remember { mutableStateListOf<String>() }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }

    var isFilterExpanded by remember { mutableStateOf(isLandscape) }
    var showWorkerDialog by remember { mutableStateOf(false) }

    val orderTypeOptions = listOf("standard", "sequence")
    val actionOptions = listOf("created", "assigned", "unassigned", "scan", "completed", "steps_saved", "complete")
    val resultOptions = listOf("standard_matched", "sequence_step_completed", "sequence_steps_saved", "mismatch", "out_of_order", "duplicate", "standard_completed", "sequence_completed", "deprecated_complete_api")
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }

    fun getCurrentQuery(): LogSearchQuery? {
        val orderIds = orderIdQuery.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (orderIds.isEmpty() && selectedWorkers.isEmpty() && selectedOrderTypes.isEmpty() && selectedActions.isEmpty() && selectedResults.isEmpty() && uidHexQuery.isBlank() && startTime.isBlank() && endTime.isBlank()) return null
        return LogSearchQuery(
            orderId = orderIds.ifEmpty { null },
            action = selectedActions.toList().ifEmpty { null },
            result = selectedResults.toList().ifEmpty { null },
            operator = selectedWorkers.toList().ifEmpty { null },
            uidHex = uidHexQuery.ifBlank { null },
            orderType = selectedOrderTypes.toList().ifEmpty { null },
            startTime = startTime.ifBlank { null },
            endTime = endTime.ifBlank { null }
        )
    }

    LaunchedEffect(Unit) {
        val job0 = viewModel.fetchWorkers(context)
        val job1 = viewModel.fetchLogs(context)
        val job2 = viewModel.fetchAnalysisSummary(context)
        job0.join()
        job1.join()
        job2.join()
        isInitialLoad = false
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

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.admin_query_logs)) },
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
                        // Side Panel for Filter
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
                                            label = { Text(stringResource(R.string.admin_log_search_uid_hex)) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        IconButton(onClick = { showNfcDialog = true }) {
                                            Icon(Icons.Default.Nfc, contentDescription = "Scan NFC", tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    
                                    // Worker Filters
                                    Text(stringResource(R.string.admin_search_worker_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        OutlinedButton(onClick = dropUnlessResumed { showWorkerDialog = true }) {
                                            val workerText = if (selectedWorkers.isEmpty()) 
                                                stringResource(R.string.admin_select_worker) 
                                            else
                                                stringResource(R.string.admin_workers_selected, selectedWorkers.size)
                                            Text(workerText)
                                        }
                                    }
                                    
                                    // Order Type Filters
                                    Text(stringResource(R.string.admin_log_search_order_type_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        orderTypeOptions.forEach { type ->
                                            val isSelected = selectedOrderTypes.contains(type)
                                            val localizedLabel = if (type == "standard") stringResource(R.string.worker_order_type_standard) else stringResource(R.string.worker_order_type_sequence)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = dropUnlessResumed {
                                                    if (isSelected) selectedOrderTypes.remove(type)
                                                    else selectedOrderTypes.add(type)
                                                },
                                                label = { Text(localizedLabel) }
                                            )
                                        }
                                    }

                                    // Action Filters
                                    Text(stringResource(R.string.admin_log_search_action_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        actionOptions.forEach { action ->
                                            val isSelected = selectedActions.contains(action)
                                            val localizedAct = when(action) {
                                                "scan" -> stringResource(R.string.admin_log_action_scan)
                                                "complete" -> stringResource(R.string.admin_log_action_complete_deprecated)
                                                "steps_saved" -> stringResource(R.string.admin_log_action_steps_saved)
                                                else -> getLocalizedStatus(action)
                                            }
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
                                    
                                    // Result Filters
                                    Text(stringResource(R.string.admin_log_search_result_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        resultOptions.forEach { res ->
                                            val isSelected = selectedResults.contains(res)
                                            val localizedRes = when (res) {
                                                "standard_matched" -> stringResource(R.string.worker_history_result_matched)
                                                "sequence_step_completed" -> stringResource(R.string.worker_history_result_step_completed)
                                                "sequence_steps_saved" -> stringResource(R.string.admin_log_result_steps_saved)
                                                "mismatch" -> stringResource(R.string.worker_history_result_mismatch)
                                                "out_of_order" -> stringResource(R.string.worker_history_result_out_of_order)
                                                "duplicate" -> stringResource(R.string.worker_history_result_duplicate)
                                                "standard_completed" -> stringResource(R.string.admin_log_result_standard_completed)
                                                "sequence_completed" -> stringResource(R.string.admin_log_result_sequence_completed)
                                                "deprecated_complete_api" -> stringResource(R.string.admin_log_result_deprecated)
                                                else -> res
                                            }
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = dropUnlessResumed {
                                                    if (isSelected) selectedResults.remove(res)
                                                    else selectedResults.add(res)
                                                },
                                                label = { Text(localizedRes) }
                                            )
                                        }
                                    }

                                    // Time Filters (Vertical in Landscape)
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
                                    
                                    Spacer(modifier = Modifier.height(Dimensions.SpaceS))
                                }

                                // Search Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = Dimensions.SpaceS),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = dropUnlessResumed {
                                        orderIdQuery = ""
                                        selectedWorkers.clear()
                                        uidHexQuery = ""
                                        selectedOrderTypes.clear()
                                        selectedActions.clear()
                                        selectedResults.clear()
                                        startTime = ""
                                        endTime = ""
                                        viewModel.fetchLogs(context, null)
                                    }) {
                                        Text(stringResource(R.string.admin_search_clear_btn))
                                    }
                                    Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                    Button(onClick = dropUnlessResumed {
                                        val query = LogSearchQuery(
                                            orderId = orderIdQuery.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
                                            action = selectedActions.toList().takeIf { it.isNotEmpty() },
                                            result = selectedResults.toList().takeIf { it.isNotEmpty() },
                                            operator = selectedWorkers.toList().takeIf { it.isNotEmpty() },
                                            uidHex = uidHexQuery.takeIf { it.isNotBlank() },
                                            orderType = selectedOrderTypes.toList().takeIf { it.isNotEmpty() },
                                            startTime = startTime.takeIf { it.isNotBlank() },
                                            endTime = endTime.takeIf { it.isNotBlank() }
                                        )
                                        viewModel.fetchLogs(context, query)
                                    }) {
                                        Text(stringResource(R.string.admin_search_btn))
                                    }
                                }
                            }
                        }

                        // Results Panel
                        Box(modifier = Modifier.weight(0.55f).fillMaxSize()) {
                            LogResultsList(
                                isInitialLoad = isInitialLoad,
                                uiState = uiState,
                                viewModel = viewModel,
                                coroutineScope = coroutineScope,
                                showEmptyDialog = showEmptyDialog,
                                onShowEmptyDialogChange = { showEmptyDialog = it },
                                onLoadMore = {
                                    viewModel.fetchLogs(context, isAppend = true)
                                },
                                contentPadding = PaddingValues(bottom = Dimensions.SpaceL)
                            )
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
                                                label = { Text(stringResource(R.string.admin_log_search_uid_hex)) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            IconButton(onClick = { showNfcDialog = true }) {
                                                Icon(Icons.Default.Nfc, contentDescription = "Scan NFC", tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }

                                        // Worker Filters
                                        Text(stringResource(R.string.admin_search_worker_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            OutlinedButton(onClick = dropUnlessResumed { showWorkerDialog = true }) {
                                                val workerText = if (selectedWorkers.isEmpty()) 
                                                    stringResource(R.string.admin_select_worker) 
                                                else
                                                    stringResource(R.string.admin_workers_selected, selectedWorkers.size)
                                                Text(workerText)
                                            }
                                        }
                                        
                                        // Order Type Filters
                                        Text(stringResource(R.string.admin_log_search_order_type_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            orderTypeOptions.forEach { type ->
                                                val isSelected = selectedOrderTypes.contains(type)
                                                val localizedLabel = if (type == "standard") stringResource(R.string.worker_order_type_standard) else stringResource(R.string.worker_order_type_sequence)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = dropUnlessResumed {
                                                        if (isSelected) selectedOrderTypes.remove(type)
                                                        else selectedOrderTypes.add(type)
                                                    },
                                                    label = { Text(localizedLabel) }
                                                )
                                            }
                                        }

                                        // Action Filters
                                        Text(stringResource(R.string.admin_log_search_action_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            actionOptions.forEach { action ->
                                                val isSelected = selectedActions.contains(action)
                                                val localizedAct = when(action) {
                                                    "scan" -> stringResource(R.string.admin_log_action_scan)
                                                    "complete" -> stringResource(R.string.admin_log_action_complete_deprecated)
                                                    "steps_saved" -> stringResource(R.string.admin_log_action_steps_saved)
                                                    else -> getLocalizedStatus(action)
                                                }
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
                                        
                                        // Result Filters
                                        Text(stringResource(R.string.admin_log_search_result_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            resultOptions.forEach { res ->
                                                val isSelected = selectedResults.contains(res)
                                                val localizedRes = when (res) {
                                                    "standard_matched" -> stringResource(R.string.worker_history_result_matched)
                                                    "sequence_step_completed" -> stringResource(R.string.worker_history_result_step_completed)
                                                    "sequence_steps_saved" -> stringResource(R.string.admin_log_result_steps_saved)
                                                    "mismatch" -> stringResource(R.string.worker_history_result_mismatch)
                                                    "out_of_order" -> stringResource(R.string.worker_history_result_out_of_order)
                                                    "duplicate" -> stringResource(R.string.worker_history_result_duplicate)
                                                    "standard_completed" -> stringResource(R.string.admin_log_result_standard_completed)
                                                    "sequence_completed" -> stringResource(R.string.admin_log_result_sequence_completed)
                                                    "deprecated_complete_api" -> stringResource(R.string.admin_log_result_deprecated)
                                                    else -> res
                                                }
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = dropUnlessResumed {
                                                        if (isSelected) selectedResults.remove(res)
                                                        else selectedResults.add(res)
                                                    },
                                                    label = { Text(localizedRes) }
                                                )
                                            }
                                        }

                                        // Time Filters
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
                                                selectedWorkers.clear()
                                                uidHexQuery = ""
                                                selectedOrderTypes.clear()
                                                selectedActions.clear()
                                                selectedResults.clear()
                                                startTime = ""
                                                endTime = ""
                                                viewModel.fetchLogs(context, null)
                                            }) {
                                                Text(stringResource(R.string.admin_search_clear_btn))
                                            }
                                            Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                            Button(onClick = dropUnlessResumed {
                                                isFilterExpanded = false
                                                val query = LogSearchQuery(
                                                    orderId = orderIdQuery.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
                                                    action = selectedActions.toList().takeIf { it.isNotEmpty() },
                                                    result = selectedResults.toList().takeIf { it.isNotEmpty() },
                                                    operator = selectedWorkers.toList().takeIf { it.isNotEmpty() },
                                                    uidHex = uidHexQuery.takeIf { it.isNotBlank() },
                                                    orderType = selectedOrderTypes.toList().takeIf { it.isNotEmpty() },
                                                    startTime = startTime.takeIf { it.isNotBlank() },
                                                    endTime = endTime.takeIf { it.isNotBlank() }
                                                )
                                                viewModel.fetchLogs(context, query)
                                            }) {
                                                Text(stringResource(R.string.admin_search_btn))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Results List Portrait
                        Box(modifier = Modifier.fillMaxSize()) {
                            LogResultsList(
                                isInitialLoad = isInitialLoad,
                                uiState = uiState,
                                viewModel = viewModel,
                                coroutineScope = coroutineScope,
                                showEmptyDialog = showEmptyDialog,
                                onShowEmptyDialogChange = { showEmptyDialog = it },
                                onLoadMore = {
                                    viewModel.fetchLogs(context, isAppend = true)
                                },
                                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + Dimensions.SpaceL)
                            )
                        }
                    }
                }
            }
            
            // Common Dialogs
            if (showWorkerDialog) {
                AlertDialog(
                    onDismissRequest = { showWorkerDialog = false },
                    title = { Text(stringResource(R.string.admin_select_worker)) },
                    text = {
                        LazyColumn {
                            items(uiState.workers) { worker ->
                                val isChecked = selectedWorkers.contains(worker.id.toString())
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = dropUnlessResumed {
                                            if (isChecked) {
                                                selectedWorkers.remove(worker.id.toString())
                                            } else {
                                                selectedWorkers.add(worker.id.toString())
                                            }
                                        })
                                        .padding(Dimensions.SpaceS),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = isChecked, onCheckedChange = null)
                                    Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                    Text(worker.username)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = dropUnlessResumed { showWorkerDialog = false }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
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
}

@Composable
fun LogResultsList(
    isInitialLoad: Boolean,
    uiState: AdminUiState,
    viewModel: AdminViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    showEmptyDialog: Boolean,
    onShowEmptyDialogChange: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    when {
        isInitialLoad || (uiState.isLoading && uiState.logs.isEmpty()) -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null && uiState.logs.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimensions.SpaceXXXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Network Error",
                    modifier = Modifier.size(Dimensions.IconSize.XL),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Dimensions.SpaceL))
                Text(
                    text = stringResource(R.string.error_network_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Dimensions.SpaceS))
                Text(
                    text = uiState.error ?: stringResource(R.string.error_network_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Dimensions.SpaceXXL))
                Button(
                    onClick = dropUnlessResumed { 
                        viewModel.clearMessages()
                        coroutineScope.launch {
                            val job1 = viewModel.fetchLogs(context)
                            val job2 = viewModel.fetchAnalysisSummary(context)
                            job1.join()
                            job2.join()
                        }
                    },
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimensions.IconSize.M),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                        Text(stringResource(R.string.error_retry_btn))
                    }
                }
            }
        }
        else -> {
            LaunchedEffect(uiState.logs) {
                if (uiState.logs.isEmpty() && !isInitialLoad && !uiState.isLoading) {
                    onShowEmptyDialogChange(true)
                }
            }

            if (showEmptyDialog) {
                AlertDialog(
                    onDismissRequest = { onShowEmptyDialogChange(false) },
                    title = { Text(stringResource(R.string.dialog_empty_state_title)) },
                    text = { Text(stringResource(R.string.admin_logs_no_data)) },
                    confirmButton = {
                        TextButton(onClick = dropUnlessResumed { onShowEmptyDialogChange(false) }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }

            val listState = rememberLazyListState()

            val isAtBottom by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisibleItem >= totalItems - 1
                }
            }

            LaunchedEffect(isAtBottom) {
                if (isAtBottom && uiState.hasMoreLogs && !uiState.isAppendingLogs && !uiState.isLoading) {
                    onLoadMore()
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                // Show Global Analysis Summary
                uiState.analysisSummary?.let { summary ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimensions.SpaceL, vertical = Dimensions.SpaceS),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.Low)
                        ) {
                            Column(modifier = Modifier.padding(Dimensions.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                Text(text = stringResource(R.string.admin_analysis_summary_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.height(Dimensions.SpaceXS))
                                
                                val totals = summary.totals
                                if (totals != null) {
                                    Text(stringResource(R.string.admin_analysis_total_orders, totals.totalOrders, totals.standardOrders, totals.sequenceOrders), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.admin_analysis_completed_orders, totals.completedOrders), style = MaterialTheme.typography.bodyMedium)
                                }
                                
                                val scans = summary.scans
                                if (scans != null) {
                                    Text(stringResource(R.string.admin_analysis_total_scans, scans.totalScanEvidenceCount), style = MaterialTheme.typography.bodyMedium)
                                    val successCount = scans.standardMatchedCount + scans.sequenceStepCompletedCount
                                    Text(stringResource(R.string.admin_analysis_success_scans, successCount, scans.standardMatchedCount, scans.sequenceStepCompletedCount), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                if (uiState.logs.isEmpty()) {
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
                    items(uiState.logs, key = { it.id }) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.SpaceL, vertical = Dimensions.SpaceS),
                            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.Low)
                        ) {
                            Column(
                                modifier = Modifier.padding(Dimensions.SpaceL),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceXXS)
                            ) {
                                Text(
                                    text = stringResource(R.string.admin_log_item_title, log.id),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                val actStr = when(log.action) {
                                    "scan" -> stringResource(R.string.admin_log_action_scan)
                                    "complete" -> stringResource(R.string.admin_log_action_complete_deprecated)
                                    "steps_saved" -> stringResource(R.string.admin_log_action_steps_saved)
                                    else -> getLocalizedStatus(log.action)
                                }
                                Text(text = stringResource(R.string.admin_log_action, actStr), color = MaterialTheme.colorScheme.onSurface)
                                
                                val orderId = log.orderId ?: log.order_id
                                val orderTitle = log.orderTitle ?: "Order"
                                Text(text = stringResource(R.string.admin_log_target, orderTitle, orderId?.toString() ?: "N/A"), color = MaterialTheme.colorScheme.onSurface)
                                
                                val opId = log.workerId ?: log.operator_id
                                if (opId != null) {
                                    Text(text = stringResource(R.string.admin_log_user, opId), color = MaterialTheme.colorScheme.onSurface)
                                }
                                
                                if (log.result != null) {
                                    val localizedRes = when (log.result) {
                                        "standard_matched" -> stringResource(R.string.worker_history_result_matched)
                                        "sequence_step_completed" -> stringResource(R.string.worker_history_result_step_completed)
                                        "sequence_steps_saved" -> stringResource(R.string.admin_log_result_steps_saved)
                                        "mismatch" -> stringResource(R.string.worker_history_result_mismatch)
                                        "out_of_order" -> stringResource(R.string.worker_history_result_out_of_order)
                                        "duplicate" -> stringResource(R.string.worker_history_result_duplicate)
                                        "standard_completed" -> stringResource(R.string.admin_log_result_standard_completed)
                                        "sequence_completed" -> stringResource(R.string.admin_log_result_sequence_completed)
                                        "deprecated_complete_api" -> stringResource(R.string.admin_log_result_deprecated)
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

                                Text(text = stringResource(R.string.admin_log_time, log.timestamp ?: "N/A"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (uiState.isAppendingLogs) {
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
            enabled = false, // disabled to enforce clicking the Box instead of typing
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        // Invisible clickable overlay
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