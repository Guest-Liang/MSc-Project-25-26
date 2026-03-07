package icu.guestliang.nfcworkflow.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.ui.components.CustomDateTimePickerDialog
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var showFallbackDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Search query states
    var titleQuery by remember { mutableStateOf("") }
    var descQuery by remember { mutableStateOf("") }
    var nfcTagQuery by remember { mutableStateOf("") }
    val selectedStatuses = remember { mutableStateListOf<String>() }
    val selectedAssigned = remember { mutableStateListOf<String>() }
    var createdStart by remember { mutableStateOf("") }
    var createdEnd by remember { mutableStateOf("") }
    var updatedStart by remember { mutableStateOf("") }
    var updatedEnd by remember { mutableStateOf("") }

    var isFilterExpanded by remember { mutableStateOf(false) }
    var showWorkerDialog by remember { mutableStateOf(false) }

    val statusOptions = listOf("created", "assigned", "unassigned", "completed")

    LaunchedEffect(Unit) {
        viewModel.fetchWorkers(context)
        viewModel.fetchOrders(context).join()
        isInitialLoad = false
    }

    LaunchedEffect(uiState.isFallbackTriggered) {
        if (uiState.isFallbackTriggered) {
            showFallbackDialog = true
            delay(3000)
            showFallbackDialog = false
            viewModel.clearFallbackTriggered()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_search_orders)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Panel
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
                            .clickable { isFilterExpanded = !isFilterExpanded }
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
                            contentDescription = "Toggle Filters",
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
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                OutlinedTextField(
                                    value = descQuery,
                                    onValueChange = { descQuery = it },
                                    label = { Text(stringResource(R.string.admin_order_desc_hint)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = nfcTagQuery,
                                    onValueChange = { nfcTagQuery = it },
                                    label = { Text(stringResource(R.string.admin_order_nfc_hint)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            // Status Filters
                            Text(stringResource(R.string.admin_search_status_title), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                statusOptions.forEach { status ->
                                    val isSelected = selectedStatuses.contains(status)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) selectedStatuses.remove(status)
                                            else selectedStatuses.add(status)
                                        },
                                        label = { Text(status) }
                                    )
                                }
                            }

                            // Worker Filters
                            Text(stringResource(R.string.admin_search_worker_title), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS), verticalArrangement = Arrangement.Center) {
                                val unassignedLabel = stringResource(R.string.admin_search_unassigned)
                                val isNullSelected = selectedAssigned.contains("NULL")
                                
                                FilterChip(
                                    selected = isNullSelected,
                                    onClick = {
                                        if (isNullSelected) selectedAssigned.remove("NULL")
                                        else {
                                            selectedAssigned.clear()
                                            selectedAssigned.add("NULL")
                                        }
                                    },
                                    label = { Text(unassignedLabel) }
                                )

                                OutlinedButton(onClick = { showWorkerDialog = true }) {
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
                                TextButton(onClick = {
                                    titleQuery = ""
                                    descQuery = ""
                                    nfcTagQuery = ""
                                    selectedStatuses.clear()
                                    selectedAssigned.clear()
                                    createdStart = ""
                                    createdEnd = ""
                                    updatedStart = ""
                                    updatedEnd = ""
                                    viewModel.fetchOrders(context, null)
                                }) {
                                    Text(stringResource(R.string.admin_search_clear_btn))
                                }
                                Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                Button(onClick = {
                                    isFilterExpanded = false
                                    val query = OrderSearchQuery(
                                        title = titleQuery,
                                        description = descQuery,
                                        nfcTag = nfcTagQuery,
                                        status = selectedStatuses,
                                        assigned = selectedAssigned,
                                        createdStart = createdStart,
                                        createdEnd = createdEnd,
                                        updatedStart = updatedStart,
                                        updatedEnd = updatedEnd
                                    )
                                    viewModel.fetchOrders(context, query)
                                }) {
                                    Text(stringResource(R.string.admin_search_btn))
                                }
                            }
                        }
                    }
                }
            }

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
                                        .clickable {
                                            if (isChecked) {
                                                selectedAssigned.remove(worker.id.toString())
                                            } else {
                                                selectedAssigned.remove("NULL")
                                                selectedAssigned.add(worker.id.toString())
                                            }
                                        }
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
                        TextButton(onClick = { showWorkerDialog = false }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
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
                        TextButton(onClick = { 
                            showFallbackDialog = false 
                            viewModel.clearFallbackTriggered()
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }

            // Results List
            if (isInitialLoad || uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
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
                        onClick = { 
                            isInitialLoad = true
                            viewModel.clearMessages()
                            coroutineScope.launch {
                                viewModel.fetchOrders(context).join()
                                isInitialLoad = false
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
                LaunchedEffect(uiState.orders) {
                    if (uiState.orders.isEmpty()) {
                        showEmptyDialog = true
                    }
                }

                if (showEmptyDialog) {
                    AlertDialog(
                        onDismissRequest = { showEmptyDialog = false },
                        title = { Text(stringResource(R.string.dialog_empty_state_title)) },
                        text = { Text(stringResource(R.string.admin_search_no_results)) },
                        confirmButton = {
                            TextButton(onClick = { showEmptyDialog = false }) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    )
                }

                if (uiState.orders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.admin_search_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Dimensions.SpaceL),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
                    ) {
                        items(uiState.orders, key = { it.id ?: it.hashCode() }) { order ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOrder = order },
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
                                        Text(text = stringResource(R.string.admin_order_status, order.status), style = MaterialTheme.typography.bodyMedium)
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
                    }
                }
            }
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

@Composable
fun OrderDetailDialog(order: Order, workers: List<icu.guestliang.nfcworkflow.network.WorkerUser>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_order_details_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                Text(text = stringResource(R.string.admin_order_item_title, order.id ?: 0, order.title), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SpaceXS))
                
                Text(text = stringResource(R.string.admin_order_status, order.status), style = MaterialTheme.typography.bodyMedium)
                Text(text = stringResource(R.string.admin_order_description, order.description), style = MaterialTheme.typography.bodyMedium)
                
                if (!order.nfc_tag.isNullOrBlank()) {
                    Text(text = stringResource(R.string.admin_order_nfc_tag, order.nfc_tag), style = MaterialTheme.typography.bodySmall)
                }

                val workerName = workers.find { it.id == order.assigned_to }?.username
                if (workerName != null) {
                    Text(text = stringResource(R.string.admin_order_assigned_to, workerName, order.assigned_to ?: 0), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(text = stringResource(R.string.admin_order_not_assigned), style = MaterialTheme.typography.bodySmall)
                }

                if (!order.created_at.isNullOrBlank()) {
                    Text(text = stringResource(R.string.admin_order_created_at, order.created_at), style = MaterialTheme.typography.bodySmall)
                }
                if (!order.updated_at.isNullOrBlank()) {
                    Text(text = stringResource(R.string.admin_order_updated_at, order.updated_at), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
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
                .clickable { showDialog = true }
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
