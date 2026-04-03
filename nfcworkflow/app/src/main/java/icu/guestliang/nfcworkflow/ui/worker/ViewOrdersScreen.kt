package icu.guestliang.nfcworkflow.ui.worker

import dev.chrisbanes.haze.HazeState
import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.navigation.Screen
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.ui.components.SplicedBaseWidget
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.getLocalizedStatus
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewOrdersScreen(
    navController: NavController,
    viewModel: WorkerViewModel = viewModel()
) {
    val context = LocalContext.current
    AppLogger.debug(context, "ViewOrdersScreen recomposed", "UI")

    val uiState by viewModel.uiState.collectAsState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) {
        viewModel.fetchMyOrders(context)
    }

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
            viewModel.fetchMyOrders(context, isAppend = true)
        }
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
                    title = { Text(stringResource(R.string.worker_view_orders)) },
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
                    uiState.isLoading && uiState.orders.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.error != null && uiState.orders.isEmpty() -> {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.orders.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.worker_no_orders),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding() + Dimensions.SpaceL
                            )
                        ) {
                            items(uiState.orders) { order ->
                                OrderCard(order = order, navController = navController)
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
        }
    }
}

@Composable
fun OrderCard(order: Order, navController: NavController) {
    var expanded by remember { mutableStateOf(false) }
    
    val orderTypeStr = if (order.orderType == "sequence") 
        stringResource(R.string.worker_order_type_sequence)
    else 
        stringResource(R.string.worker_order_type_standard)

    SplicedColumnGroup(title = stringResource(R.string.admin_order_item_title, order.id ?: 0, order.title)) {
        item {
            SplicedBaseWidget(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = order.title,
                description = stringResource(R.string.admin_order_status, getLocalizedStatus(order.status)) + " | " + orderTypeStr,
                iconPlaceholder = true,
                onClick = dropUnlessResumed { expanded = !expanded }
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_toggle_order_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(visible = expanded) {
            SplicedBaseWidget(
                title = null,
                description = null,
                iconPlaceholder = true,
                descriptionColumnContent = {
                    Column(
                        modifier = Modifier.padding(top = Dimensions.SpaceS),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
                    ) {
                        Text(
                            text = stringResource(R.string.admin_order_description, order.description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        if (order.orderType == "standard") {
                            order.targetUidHex?.let {
                                Text(
                                    text = stringResource(R.string.worker_order_target_uid, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            order.locationCode?.let {
                                Text(
                                    text = stringResource(R.string.worker_order_location, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (order.orderType == "sequence") {
                            Text(
                                text = stringResource(R.string.worker_order_step_progress, order.sequenceCompletedSteps, order.sequenceTotalSteps),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            order.nextDisplayName?.let {
                                Text(
                                    text = stringResource(R.string.worker_order_next_step, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (order.status != "completed") {
                            Button(
                                onClick = dropUnlessResumed { 
                                    order.id?.let { navController.navigate(Screen.WorkerScanOrder.createRoute(it)) }
                                },
                                modifier = Modifier.padding(top = Dimensions.SpaceM)
                            ) {
                                Text(text = stringResource(R.string.worker_scan_to_execute))
                            }
                        }
                    }
                }
            ) {}
        }
    }
}
