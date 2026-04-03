package icu.guestliang.nfcworkflow.ui.admin

import dev.chrisbanes.haze.HazeState
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import kotlinx.coroutines.joinAll
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) {
        val assignableQuery = OrderSearchQuery(status = listOf("created", "assigned", "unassigned"))
        val ordersJob = viewModel.fetchOrders(context, query = assignableQuery)
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
                TextButton(onClick = dropUnlessResumed { showAssignResultDialog = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.admin_assign_order)) },
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
                when {
                    isInitialLoad || (uiState.isLoading && uiState.orders.isEmpty()) -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    else -> {
                        // Keep filtering locally just in case, but rely on query for main filtering
                        val displayableOrders = uiState.orders.filter { it.status == "created" || it.status == "assigned" || it.status == "unassigned" }
                        
                        if (displayableOrders.isEmpty() && !uiState.isLoading && !uiState.isAppendingOrders && !uiState.hasMoreOrders) {
                            Box(modifier = Modifier.fillMaxWidth().padding(innerPadding), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.admin_all_orders_assigned),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(Dimensions.SpaceXXXL)
                                )
                            }
                        } else {
                            val gridState = rememberLazyStaggeredGridState()
                            val isAtBottom by remember {
                                derivedStateOf {
                                    val layoutInfo = gridState.layoutInfo
                                    val totalItems = layoutInfo.totalItemsCount
                                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    totalItems > 0 && lastVisibleItem >= totalItems - 1
                                }
                            }

                            LaunchedEffect(isAtBottom) {
                                if (isAtBottom && uiState.hasMoreOrders && !uiState.isAppendingOrders && !uiState.isLoading) {
                                    viewModel.fetchOrders(context, isAppend = true)
                                }
                            }

                            val columns = if (isLandscape) 2 else 1
                            LazyVerticalStaggeredGrid(
                                state = gridState,
                                columns = StaggeredGridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = innerPadding.calculateTopPadding() + Dimensions.SpaceL,
                                    bottom = innerPadding.calculateBottomPadding() + Dimensions.SpaceL,
                                    start = Dimensions.SpaceL,
                                    end = Dimensions.SpaceL
                                ),
                                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceL),
                                verticalItemSpacing = Dimensions.SpaceL
                            ) {
                                items(displayableOrders, key = { it.id ?: it.hashCode() }) { order ->
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
                                            val finalAssignedTo = order.assignedTo ?: order.assigned_to
                                            var selectedWorkerId by remember(finalAssignedTo) { mutableStateOf(finalAssignedTo) }

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
                                                        onClick = dropUnlessResumed {
                                                            selectedWorkerId = null
                                                            expanded = false
                                                        }
                                                    )
                                                    uiState.workers.forEach { worker ->
                                                        DropdownMenuItem(
                                                            text = { Text(worker.username) },
                                                            onClick = dropUnlessResumed {
                                                                selectedWorkerId = worker.id
                                                                expanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = dropUnlessResumed {
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

                                if (uiState.isAppendingOrders) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
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
            }
        }
    }
}
