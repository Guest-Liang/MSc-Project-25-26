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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.components.CustomDateTimePickerDialog
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.getLocalizedStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var showFallbackDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Search query states
    var orderIdQuery by remember { mutableStateOf("") }
    var operatorQuery by remember { mutableStateOf("") }
    val selectedActions = remember { mutableStateListOf<String>() }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }

    var isFilterExpanded by remember { mutableStateOf(false) }

    val actionOptions = listOf("created", "assigned", "unassigned", "completed")

    LaunchedEffect(Unit) {
        viewModel.fetchLogs(context).join()
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
                                value = orderIdQuery,
                                onValueChange = { orderIdQuery = it },
                                label = { Text(stringResource(R.string.admin_log_search_order_id)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = operatorQuery,
                                onValueChange = { operatorQuery = it },
                                label = { Text(stringResource(R.string.admin_log_search_operator)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Action Filters
                            Text(stringResource(R.string.admin_log_search_action_title), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                                actionOptions.forEach { action ->
                                    val isSelected = selectedActions.contains(action)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) selectedActions.remove(action)
                                            else selectedActions.add(action)
                                        },
                                        label = { Text(getLocalizedStatus(action)) }
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
                                TextButton(onClick = {
                                    orderIdQuery = ""
                                    operatorQuery = ""
                                    selectedActions.clear()
                                    startTime = ""
                                    endTime = ""
                                    viewModel.fetchLogs(context, null)
                                }) {
                                    Text(stringResource(R.string.admin_search_clear_btn))
                                }
                                Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                                Button(onClick = {
                                    isFilterExpanded = false
                                    val query = LogSearchQuery(
                                        orderId = orderIdQuery.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
                                        status = selectedActions.toList().takeIf { it.isNotEmpty() },
                                        operator = operatorQuery.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    isInitialLoad || (uiState.isLoading && uiState.logs.isEmpty()) -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                                showEmptyDialog = true
                            }
                        }

                        if (showEmptyDialog) {
                            AlertDialog(
                                onDismissRequest = { showEmptyDialog = false },
                                title = { Text(stringResource(R.string.dialog_empty_state_title)) },
                                text = { Text(stringResource(R.string.admin_logs_no_data)) },
                                confirmButton = {
                                    TextButton(onClick = { showEmptyDialog = false }) {
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
                                contentPadding = PaddingValues(Dimensions.SpaceL),
                                verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
                            ) {
                                items(uiState.logs, key = { it.id }) { log ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
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
                                            Text(text = stringResource(R.string.admin_log_action, getLocalizedStatus(log.action)))
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
