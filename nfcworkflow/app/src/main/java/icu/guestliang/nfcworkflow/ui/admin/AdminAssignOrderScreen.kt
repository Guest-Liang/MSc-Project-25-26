package icu.guestliang.nfcworkflow.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import kotlinx.coroutines.joinAll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAssignOrderScreen(
    navController: NavController,
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    AppLogger.debug(context, "AdminAssignOrderScreen recomposed", "UI")

    val uiState by viewModel.uiState.collectAsState()
    var isInitialLoad by remember { mutableStateOf(true) }
    var showAssignResultDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val ordersJob = viewModel.fetchOrders(context)
        val workersJob = viewModel.fetchWorkers(context)
        joinAll(ordersJob, workersJob)
        isInitialLoad = false
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            showAssignResultDialog = it
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            showAssignResultDialog = it
            viewModel.clearMessages()
        }
    }

    if (showAssignResultDialog != null) {
        AlertDialog(
            onDismissRequest = { showAssignResultDialog = null },
            title = { Text(stringResource(R.string.admin_assignment_result_title)) },
            text = { Text(showAssignResultDialog ?: "") },
            confirmButton = {
                TextButton(onClick = { showAssignResultDialog = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_assign_order)) },
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
                isInitialLoad || (uiState.isLoading && uiState.orders.isEmpty()) -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Dimensions.SpaceL),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
                    ) {
                        val displayableOrders = uiState.orders.filter { it.status == "created" || it.status == "assigned" || it.status == "unassigned" }
                        
                        if (displayableOrders.isEmpty() && !uiState.isLoading && !isInitialLoad) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.admin_all_orders_assigned),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(Dimensions.SpaceXXXL)
                                    )
                                }
                            }
                        } else {
                            items(displayableOrders) { order ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.Low)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Dimensions.SpaceL),
                                        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.admin_order_item_title, order.id ?: 0, order.title),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(text = stringResource(R.string.admin_order_description, order.description))

                                        var expanded by remember { mutableStateOf(false) }
                                        var selectedWorkerId by remember(order.assigned_to) { mutableStateOf(order.assigned_to) }

                                        ExposedDropdownMenuBox(
                                            expanded = expanded,
                                            onExpandedChange = { expanded = it }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedWorkerId?.let { id -> uiState.workers.find { it.id == id }?.username } ?: stringResource(R.string.admin_select_worker),
                                                onValueChange = {},
                                                readOnly = true,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                            )

                                            ExposedDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.admin_assign_unassign_btn)) },
                                                    onClick = {
                                                        selectedWorkerId = null
                                                        expanded = false
                                                    }
                                                )
                                                uiState.workers.forEach { worker ->
                                                    DropdownMenuItem(
                                                        text = { Text(worker.username) },
                                                        onClick = {
                                                            selectedWorkerId = worker.id
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (order.id != null) {
                                                    viewModel.assignOrder(context, order.id, selectedWorkerId)
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(stringResource(R.string.admin_assign_btn))
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
