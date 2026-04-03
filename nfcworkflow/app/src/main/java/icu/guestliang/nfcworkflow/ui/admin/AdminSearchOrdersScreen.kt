package icu.guestliang.nfcworkflow.ui.admin

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.Order
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminSearchOrdersScreen(
    navController: NavController,
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    AppLogger.debug(context, "AdminSearchOrdersScreen recomposed", "UI")

    val uiState by viewModel.uiState.collectAsState()
    var isInitialLoad by remember { mutableStateOf(true) }
    var showEmptyDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var showNfcDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Search query states
    var titleQuery by remember { mutableStateOf("") }
    var descQuery by remember { mutableStateOf("") }
    var targetUidHexQuery by remember { mutableStateOf("") }
    val selectedOrderTypes = remember { mutableStateListOf<String>() }
    val selectedStatuses = remember { mutableStateListOf<String>() }
    val selectedAssigned = remember { mutableStateListOf<String>() }
    val selectedProgress = remember { mutableStateListOf<String>() }
    var createdStart by remember { mutableStateOf("") }
    var createdEnd by remember { mutableStateOf("") }
    var updatedStart by remember { mutableStateOf("") }
    var updatedEnd by remember { mutableStateOf("") }

    // Start with filter expanded if in landscape (it will be on the side), else collapsed
    var isFilterExpanded by remember { mutableStateOf(isLandscape) }
    var showWorkerDialog by remember { mutableStateOf(false) }

    val orderTypeOptions = listOf("standard", "sequence")
    val statusOptions = listOf("created", "assigned", "completed")
    val progressOptions = listOf("not_started", "in_progress", "completed")
    
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }

    fun getCurrentQuery(): OrderSearchQuery? {
        if (titleQuery.isBlank() && descQuery.isBlank() && targetUidHexQuery.isBlank() && 
            selectedOrderTypes.isEmpty() && selectedStatuses.isEmpty() && selectedAssigned.isEmpty() && 
            selectedProgress.isEmpty() && createdStart.isBlank() && createdEnd.isBlank() && 
            updatedStart.isBlank() && updatedEnd.isBlank()) return null
            
        return OrderSearchQuery(
            title = titleQuery,
            description = descQuery,
            nfcTag = targetUidHexQuery,
            orderType = selectedOrderTypes.toList(),
            status = selectedStatuses.toList(),
            assigned = selectedAssigned.toList(),
            progress = selectedProgress.toList(),
            createdStart = createdStart,
            createdEnd = createdEnd,
            updatedStart = updatedStart,
            updatedEnd = updatedEnd
        )
    }

    LaunchedEffect(Unit) {
        viewModel.fetchWorkers(context)
        viewModel.fetchOrders(context).join()
        isInitialLoad = false
    }

    // Automatically manage filter expansion based on orientation changes if desired
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
                    title = { Text(stringResource(R.string.admin_search_orders)) },
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
                                        value = titleQuery,
                                        onValueChange = { titleQuery = it },
                                        label = { Text(stringResource(R.string.admin_order_title_hint)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = descQuery,
                                        onValueChange = { descQuery = it },
                                        label = { Text(stringResource(R.string.admin_order_desc_hint)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = targetUidHexQuery,
                                            onValueChange = { targetUidHexQuery = it },
                                            label = { Text(stringResource(R.string.admin_order_nfc_hint_optional)) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        IconButton(onClick = { showNfcDialog = true }) {
                                            Icon(Icons.Default.Nfc, contentDescription = "Scan NFC")
                                        }
                                    }

                                    // Order Type Filters
                                    Text(stringResource(R.string.admin_search_order_type_title), style = MaterialTheme.typography.bodySmall)
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

                                    // Status Filters
                                    Text(stringResource(R.string.admin_search_status_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        statusOptions.forEach { status ->
                                            val isSelected = selectedStatuses.contains(status)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = dropUnlessResumed {
                                                    if (isSelected) selectedStatuses.remove(status)
                                                    else selectedStatuses.add(status)
                                                },
                                                label = { Text(getLocalizedStatus(status)) }
                                            )
                                        }
                                    }

                                    // Progress Filters
                                    Text(stringResource(R.string.admin_search_progress_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        progressOptions.forEach { prog ->
                                            val isSelected = selectedProgress.contains(prog)
                                            val localizedProg = when(prog) {
                                                "not_started" -> stringResource(R.string.admin_search_progress_not_started)
                                                "in_progress" -> stringResource(R.string.admin_search_progress_in_progress)
                                                "completed" -> stringResource(R.string.admin_search_progress_completed)
                                                else -> prog
                                            }
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = dropUnlessResumed {
                                                    if (isSelected) selectedProgress.remove(prog)
                                                    else selectedProgress.add(prog)
                                                },
                                                label = { Text(localizedProg) }
                                            )
                                        }
                                    }

                                    // Worker Filters
                                    Text(stringResource(R.string.admin_search_worker_title), style = MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                        val unassignedLabel = stringResource(R.string.admin_search_unassigned)
                                        val isNullSelected = selectedAssigned.contains("NULL")
                                        
                                        FilterChip(
                                            selected = isNullSelected,
                                            onClick = dropUnlessResumed {
                                                if (isNullSelected) selectedAssigned.remove("NULL")
                                                else {
                                                    selectedAssigned.clear()
                                                    selectedAssigned.add("NULL")
                                                }
                                            },
                                            label = { Text(unassignedLabel) }
                                        )

                                        OutlinedButton(onClick = dropUnlessResumed { showWorkerDialog = true }) {
                                            val workerText = if (selectedAssigned.isEmpty() || isNullSelected) 
                                                stringResource(R.string.admin_select_worker) 
                                            else
                                                stringResource(R.string.admin_workers_selected, selectedAssigned.size)
                                            Text(workerText)
                                        }
                                    }

                                    // Date Filters (Vertical in Landscape)
                                    DateTimeSelectorField(
                                        label = stringResource(R.string.admin_search_created_start),
                                        value = createdStart,
                                        onDateTimeSelected = { createdStart = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DateTimeSelectorField(
                                        label = stringResource(R.string.admin_search_created_end),
                                        value = createdEnd,
                                        onDateTimeSelected = { createdEnd = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DateTimeSelectorField(
                                        label = stringResource(R.string.admin_search_updated_start),
                                        value = updatedStart,
                                        onDateTimeSelected = { updatedStart = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DateTimeSelectorField(
                                        label = stringResource(R.string.admin_search_updated_end),
                                        value = updatedEnd,
                                        onDateTimeSelected = { updatedEnd = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Spacer(modifier = Modifier.height(Dimensions.SpaceS))
                                }

                                // Search Buttons fixed at bottom of the side panel
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = Dimensions.SpaceS),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = dropUnlessResumed {
                                        titleQuery = ""
                                        descQuery = ""
                                        targetUidHexQuery = ""
                                        selectedOrderTypes.clear()
                                        selectedStatuses.clear()
                                        selectedAssigned.clear()
                                        selectedProgress.clear()
                                        createdStart = ""
                                        createdEnd = ""
                                        updatedStart = ""
                                        updatedEnd = ""
                                        viewModel.fetchOrders(context, null)
                                    }) {
                                        Text(stringResource(R.string.admin_search_clear_btn))
                                    }
                                    Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                    Button(onClick = dropUnlessResumed {
                                        viewModel.fetchOrders(context, getCurrentQuery())
                                    }) {
                                        Text(stringResource(R.string.admin_search_btn))
                                    }
                                }
                            }
                        }

                        // Results Panel
                        Box(modifier = Modifier.weight(0.55f).fillMaxSize()) {
                            OrderResultsList(
                                isInitialLoad = isInitialLoad,
                                uiState = uiState,
                                viewModel = viewModel,
                                coroutineScope = coroutineScope,
                                onOrderSelected = { selectedOrder = it },
                                onLoadMore = {
                                    viewModel.fetchOrders(context, getCurrentQuery(), isAppend = true)
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
                        // Filter Panel for Portrait
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
                                            value = titleQuery,
                                            onValueChange = { titleQuery = it },
                                            label = { Text(stringResource(R.string.admin_order_title_hint)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = descQuery,
                                            onValueChange = { descQuery = it },
                                            label = { Text(stringResource(R.string.admin_order_desc_hint)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = targetUidHexQuery,
                                                onValueChange = { targetUidHexQuery = it },
                                                label = { Text(stringResource(R.string.admin_order_nfc_hint_optional)) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            IconButton(onClick = { showNfcDialog = true }) {
                                                Icon(Icons.Default.Nfc, contentDescription = "Scan NFC")
                                            }
                                        }
                                        
                                        // Order Type Filters
                                        Text(stringResource(R.string.admin_search_order_type_title), style = MaterialTheme.typography.bodySmall)
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

                                        // Status Filters
                                        Text(stringResource(R.string.admin_search_status_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            statusOptions.forEach { status ->
                                                val isSelected = selectedStatuses.contains(status)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = dropUnlessResumed {
                                                        if (isSelected) selectedStatuses.remove(status)
                                                        else selectedStatuses.add(status)
                                                    },
                                                    label = { Text(getLocalizedStatus(status)) }
                                                )
                                            }
                                        }
                                        
                                        // Progress Filters
                                        Text(stringResource(R.string.admin_search_progress_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            progressOptions.forEach { prog ->
                                                val isSelected = selectedProgress.contains(prog)
                                                val localizedProg = when(prog) {
                                                    "not_started" -> stringResource(R.string.admin_search_progress_not_started)
                                                    "in_progress" -> stringResource(R.string.admin_search_progress_in_progress)
                                                    "completed" -> stringResource(R.string.admin_search_progress_completed)
                                                    else -> prog
                                                }
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = dropUnlessResumed {
                                                        if (isSelected) selectedProgress.remove(prog)
                                                        else selectedProgress.add(prog)
                                                    },
                                                    label = { Text(localizedProg) }
                                                )
                                            }
                                        }

                                        // Worker Filters
                                        Text(stringResource(R.string.admin_search_worker_title), style = MaterialTheme.typography.bodySmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            val unassignedLabel = stringResource(R.string.admin_search_unassigned)
                                            val isNullSelected = selectedAssigned.contains("NULL")
                                            
                                            FilterChip(
                                                selected = isNullSelected,
                                                onClick = dropUnlessResumed {
                                                    if (isNullSelected) selectedAssigned.remove("NULL")
                                                    else {
                                                        selectedAssigned.clear()
                                                        selectedAssigned.add("NULL")
                                                    }
                                                },
                                                label = { Text(unassignedLabel) }
                                            )

                                            OutlinedButton(onClick = dropUnlessResumed { showWorkerDialog = true }) {
                                                val workerText = if (selectedAssigned.isEmpty() || isNullSelected) 
                                                    stringResource(R.string.admin_select_worker) 
                                                else 
                                                    "Workers Selected (${selectedAssigned.size})"
                                                Text(workerText)
                                            }
                                        }

                                        // Created Date Filters
                                        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            DateTimeSelectorField(
                                                label = stringResource(R.string.admin_search_created_start),
                                                value = createdStart,
                                                onDateTimeSelected = { createdStart = it },
                                                modifier = Modifier.weight(1f)
                                            )
                                            DateTimeSelectorField(
                                                label = stringResource(R.string.admin_search_created_end),
                                                value = createdEnd,
                                                onDateTimeSelected = { createdEnd = it },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Updated Date Filters
                                        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                            DateTimeSelectorField(
                                                label = stringResource(R.string.admin_search_updated_start),
                                                value = updatedStart,
                                                onDateTimeSelected = { updatedStart = it },
                                                modifier = Modifier.weight(1f)
                                            )
                                            DateTimeSelectorField(
                                                label = stringResource(R.string.admin_search_updated_end),
                                                value = updatedEnd,
                                                onDateTimeSelected = { updatedEnd = it },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = dropUnlessResumed {
                                                titleQuery = ""
                                                descQuery = ""
                                                targetUidHexQuery = ""
                                                selectedOrderTypes.clear()
                                                selectedStatuses.clear()
                                                selectedAssigned.clear()
                                                selectedProgress.clear()
                                                createdStart = ""
                                                createdEnd = ""
                                                updatedStart = ""
                                                updatedEnd = ""
                                                viewModel.fetchOrders(context, null)
                                            }) {
                                                Text(stringResource(R.string.admin_search_clear_btn))
                                            }
                                            Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                            Button(onClick = dropUnlessResumed {
                                                isFilterExpanded = false
                                                viewModel.fetchOrders(context, getCurrentQuery())
                                            }) {
                                                Text(stringResource(R.string.admin_search_btn))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Results List Portrait
                        Box(modifier = Modifier.weight(1f)) {
                            OrderResultsList(
                                isInitialLoad = isInitialLoad,
                                uiState = uiState,
                                viewModel = viewModel,
                                coroutineScope = coroutineScope,
                                onOrderSelected = { selectedOrder = it },
                                onLoadMore = {
                                    viewModel.fetchOrders(context, getCurrentQuery(), isAppend = true)
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
                                val isChecked = selectedAssigned.contains(worker.id.toString())
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = dropUnlessResumed {
                                            if (isChecked) {
                                                selectedAssigned.remove(worker.id.toString())
                                            } else {
                                                selectedAssigned.remove("NULL")
                                                selectedAssigned.add(worker.id.toString())
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
            
            LaunchedEffect(uiState.orders) {
                if (uiState.orders.isEmpty() && !isInitialLoad && !uiState.isLoading) {
                    showEmptyDialog = true
                }
            }

            if (showEmptyDialog) {
                AlertDialog(
                    onDismissRequest = { showEmptyDialog = false },
                    title = { Text(stringResource(R.string.dialog_empty_state_title)) },
                    text = { Text(stringResource(R.string.admin_search_no_results)) },
                    confirmButton = {
                        TextButton(onClick = dropUnlessResumed { showEmptyDialog = false }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
            
            if (showNfcDialog) {
                NfcScannerDialog(
                    onDismiss = { showNfcDialog = false },
                    onScanned = { uidHex, _ ->
                        targetUidHexQuery = uidHex
                        showNfcDialog = false
                    }
                )
            }
        }

        if (selectedOrder != null) {
            OrderDetailDialog(
                order = selectedOrder!!,
                workers = uiState.workers,
                onDismiss = { selectedOrder = null }
            )
        }
    }
}

@Composable
fun OrderResultsList(
    isInitialLoad: Boolean,
    uiState: AdminUiState,
    viewModel: AdminViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onOrderSelected: (Order) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    if (isInitialLoad || (uiState.isLoading && uiState.orders.isEmpty())) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.error != null && uiState.orders.isEmpty()) {
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
                        viewModel.fetchOrders(context).join()
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
    } else {
        if (uiState.orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.admin_search_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

            LaunchedEffect(isAtBottom) {
                if (isAtBottom && uiState.hasMoreOrders && !uiState.isAppendingOrders && !uiState.isLoading) {
                    onLoadMore()
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = Dimensions.SpaceL,
                    start = Dimensions.SpaceL,
                    end = Dimensions.SpaceL,
                    bottom = contentPadding.calculateBottomPadding() + Dimensions.SpaceL
                ),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
            ) {
                items(uiState.orders, key = { it.id ?: it.hashCode() }) { order ->
                    val orderTypeStr = if (order.orderType == "sequence") 
                        stringResource(R.string.worker_order_type_sequence)
                    else 
                        stringResource(R.string.worker_order_type_standard)
                        
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = dropUnlessResumed { onOrderSelected(order) }),
                        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.Low)
                    ) {
                        Row(
                            modifier = Modifier.padding(Dimensions.SpaceL),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
                            ) {
                                Text(
                                    text = stringResource(R.string.admin_order_item_title, order.id ?: 0, order.title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.admin_order_status, getLocalizedStatus(order.status)) + " | " + orderTypeStr, 
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.admin_order_description, order.description),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                if (uiState.isAppendingOrders) {
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
fun OrderDetailDialog(order: Order, workers: List<icu.guestliang.nfcworkflow.network.WorkerUser>, onDismiss: () -> Unit) {
    val orderTypeStr = if (order.orderType == "sequence") 
        stringResource(R.string.worker_order_type_sequence)
    else 
        stringResource(R.string.worker_order_type_standard)
        
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_order_details_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                Text(text = stringResource(R.string.admin_order_item_title, order.id ?: 0, order.title), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SpaceXS))
                
                Text(text = stringResource(R.string.admin_order_type, orderTypeStr), style = MaterialTheme.typography.bodyMedium)
                Text(text = stringResource(R.string.admin_order_status, getLocalizedStatus(order.status)), style = MaterialTheme.typography.bodyMedium)
                Text(text = stringResource(R.string.admin_order_description, order.description), style = MaterialTheme.typography.bodyMedium)
                
                if (order.orderType == "standard") {
                    order.targetUidHex?.let {
                        Text(text = stringResource(R.string.worker_order_target_uid, it), style = MaterialTheme.typography.bodySmall)
                    }
                    order.locationCode?.let {
                        Text(text = stringResource(R.string.worker_order_location, it), style = MaterialTheme.typography.bodySmall)
                    }
                } else if (order.orderType == "sequence") {
                    Text(
                        text = stringResource(R.string.worker_order_step_progress, order.sequenceCompletedSteps, order.sequenceTotalSteps),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                val finalWorkerId = order.assignedTo ?: order.assigned_to
                val workerName = workers.find { it.id == finalWorkerId }?.username
                if (workerName != null) {
                    Text(text = stringResource(R.string.admin_order_assigned_to, workerName, finalWorkerId ?: 0), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(text = stringResource(R.string.admin_order_not_assigned), style = MaterialTheme.typography.bodySmall)
                }

                val createdAtStr = order.assignedAt ?: order.created_at
                if (!createdAtStr.isNullOrBlank()) {
                    Text(text = stringResource(R.string.admin_order_created_at, createdAtStr), style = MaterialTheme.typography.bodySmall)
                }
                if (!order.updated_at.isNullOrBlank()) {
                    Text(text = stringResource(R.string.admin_order_updated_at, order.updated_at), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = dropUnlessResumed { onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        }
    )
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