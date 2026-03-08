package icu.guestliang.nfcworkflow.ui.worker

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.components.SplicedTextFieldWidget
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteOrderScreen(
    navController: NavController,
    viewModel: WorkerViewModel = viewModel()
) {
    val context = LocalContext.current
    AppLogger.debug(context, "CompleteOrderScreen recomposed", "UI")

    val uiState by viewModel.uiState.collectAsState()
    val orderIdState = rememberTextFieldState()

    var showDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }
    var navigateBackOnDismiss by remember { mutableStateOf(false) }

    val successTitle = stringResource(R.string.worker_complete_result_title)
    val successMessage = stringResource(R.string.worker_order_complete_success)
    
    LaunchedEffect(uiState.actionSuccess) {
        if (uiState.actionSuccess) {
            dialogTitle = successTitle
            dialogMessage = successMessage
            navigateBackOnDismiss = true
            showDialog = true
            viewModel.resetActionSuccess()
        }
    }

    val errorTitle = stringResource(R.string.register_error_title)
    val errorFormat = stringResource(R.string.worker_order_complete_error)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMsg ->
            dialogTitle = errorTitle
            dialogMessage = errorFormat.format(errorMsg)
            navigateBackOnDismiss = false
            showDialog = true
            viewModel.clearMessages()
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = dropUnlessResumed {
                showDialog = false
                if (navigateBackOnDismiss) {
                    navController.popBackStack()
                }
            },
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(
                    onClick = dropUnlessResumed {
                        showDialog = false
                        if (navigateBackOnDismiss) {
                            navController.popBackStack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.worker_complete_order)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed { navController.popBackStack() }) {
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
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.worker_complete_order_desc)) {
                        item {
                            SplicedTextFieldWidget(
                                state = orderIdState,
                                title = stringResource(R.string.worker_order_id),
                                useLabelAsPlaceholder = true,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Numbers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }

                item {
                    val invalidIdMessage = stringResource(R.string.worker_invalid_order_id)
                    Button(
                        onClick = dropUnlessResumed {
                            val orderId = orderIdState.text.toString().toIntOrNull()
                            if (orderId != null) {
                                viewModel.completeOrder(context, orderId)
                            } else {
                                dialogTitle = errorTitle
                                dialogMessage = invalidIdMessage
                                navigateBackOnDismiss = false
                                showDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.SpaceL),
                        enabled = !uiState.isLoading && orderIdState.text.isNotBlank()
                    ) {
                        Text(stringResource(R.string.worker_complete_order))
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
