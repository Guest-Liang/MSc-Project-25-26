package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.navigation.Screen
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.components.SplicedJumpPageWidget
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    AppLogger.debug(context, "HomeScreen recomposed", "UI")

    val prefs by PrefsDataStore.flow(context).collectAsState(initial = null)
    val isWorker = prefs?.isWorker ?: false

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(
                top = Dimensions.SpaceS,
                bottom = Dimensions.SpaceL
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
        ) {
            item {
                if (isWorker) {
                    WorkerFunctionsSection(navController = navController)
                } else {
                    AdminFunctionsSection(navController = navController)
                }
            }
        }
    }
}

@Composable
fun AdminFunctionsSection(navController: NavController) {
    SplicedColumnGroup(title = stringResource(id = R.string.admin_api_title)) {
        item {
            SplicedJumpPageWidget(
                title = stringResource(id = R.string.admin_register_worker),
                icon = Icons.Default.PersonAdd,
                iconPlaceholder = true,
                onClick = dropUnlessResumed {
                    navController.navigate(Screen.AdminRegisterWorker.route)
                }
            )
        }
        item {
            SplicedJumpPageWidget(
                title = stringResource(id = R.string.admin_create_order),
                icon = Icons.Default.Add,
                iconPlaceholder = true,
                onClick = dropUnlessResumed {
                    navController.navigate(Screen.AdminCreateOrder.route)
                }
            )
        }
        item {
            SplicedJumpPageWidget(
                title = stringResource(id = R.string.admin_assign_order),
                icon = Icons.Default.AssignmentInd,
                iconPlaceholder = true,
                onClick = dropUnlessResumed {
                    navController.navigate(Screen.AdminAssignOrder.route)
                }
            )
        }
        item {
            SplicedJumpPageWidget(
                title = stringResource(id = R.string.admin_search_orders),
                icon = Icons.Default.Search,
                iconPlaceholder = true,
                onClick = dropUnlessResumed {
                    navController.navigate(Screen.AdminSearchOrders.route)
                }
            )
        }
        item {
            SplicedJumpPageWidget(
                title = stringResource(id = R.string.admin_query_logs),
                icon = Icons.AutoMirrored.Filled.List,
                iconPlaceholder = true,
                onClick = dropUnlessResumed {
                    navController.navigate(Screen.AdminQueryLogs.route)
                }
            )
        }
    }
}

@Composable
fun WorkerFunctionsSection(navController: NavController) {
    SplicedColumnGroup(title = stringResource(id = R.string.worker_api_title)) {
        item {
            SplicedJumpPageWidget(
                title = stringResource(id = R.string.worker_view_orders),
                icon = Icons.AutoMirrored.Filled.Assignment,
                iconPlaceholder = true,
                onClick = dropUnlessResumed {
                    navController.navigate(Screen.WorkerOrders.route)
                }
            )
        }
        item {
            SplicedJumpPageWidget(
                title = stringResource(id = R.string.worker_complete_order),
                icon = Icons.Default.CheckCircle,
                iconPlaceholder = true,
                onClick = dropUnlessResumed {
                    navController.navigate(Screen.WorkerCompleteOrder.route)
                }
            )
        }
    }
}