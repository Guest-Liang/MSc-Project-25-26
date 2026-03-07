package icu.guestliang.nfcworkflow.ui.worker

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.Order
import icu.guestliang.nfcworkflow.ui.components.SplicedBaseWidget
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.getLocalizedStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    LaunchedEffect(Unit) {
        viewModel.fetchMyOrders(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.worker_view_orders)) },
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
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Dimensions.SpaceL)
                    ) {
                        items(uiState.orders) { order ->
                            OrderCard(order = order)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: Order) {
    var expanded by remember { mutableStateOf(false) }

    SplicedColumnGroup(title = stringResource(R.string.admin_order_item_title, order.id ?: 0, order.title)) {
        item {
            SplicedBaseWidget(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = order.title,
                description = stringResource(R.string.admin_order_status, getLocalizedStatus(order.status)),
                iconPlaceholder = true,
                onClick = { expanded = !expanded }
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
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
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!order.nfc_tag.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.admin_order_nfc_tag, order.nfc_tag),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (order.assigned_to != null) {
                            Text(
                                text = stringResource(R.string.admin_order_assigned_to, "", order.assigned_to),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!order.created_at.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.admin_order_created_at, order.created_at),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!order.updated_at.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.admin_order_updated_at, order.updated_at),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            ) {}
        }
    }
}
