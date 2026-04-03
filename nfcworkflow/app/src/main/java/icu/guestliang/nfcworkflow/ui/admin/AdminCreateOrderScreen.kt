package icu.guestliang.nfcworkflow.ui.admin

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.OrderStep
import icu.guestliang.nfcworkflow.ui.components.NfcScannerDialog
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.LocalHazeState
import icu.guestliang.nfcworkflow.utils.haze
import icu.guestliang.nfcworkflow.utils.hazeSource
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.chrisbanes.haze.HazeState

data class DraftStep(
    var targetUidHex: String = "",
    var locationCode: String = "",
    var displayName: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCreateOrderScreen(
    navController: NavController,
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    AppLogger.debug(context, "AdminCreateOrderScreen recomposed", "UI")

    val uiState by viewModel.uiState.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var orderType by remember { mutableStateOf("standard") } // "standard" or "sequence"
    var nfcTag by remember { mutableStateOf("") }
    var locationCode by remember { mutableStateOf("") }
    
    // For sequence orders
    var steps by remember { mutableStateOf(listOf(DraftStep())) }

    // For NFC scanning
    var showNfcDialog by remember { mutableStateOf(false) }
    var scanningForStepIndex by remember { mutableStateOf<Int?>(null) }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }

    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
            navController.popBackStack()
        }
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.admin_create_order)) },
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
                            .padding(horizontal = Dimensions.SpaceL)
                            .imePadding(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = Dimensions.SpaceL)
                        ) {
                            CreateOrderFormFields(
                                title = title,
                                onTitleChange = { title = it },
                                description = description,
                                onDescriptionChange = { description = it },
                                orderType = orderType,
                                onOrderTypeChange = { orderType = it },
                                nfcTag = nfcTag,
                                onNfcTagChange = { nfcTag = it },
                                locationCode = locationCode,
                                onLocationCodeChange = { locationCode = it },
                                steps = steps,
                                onStepsChange = { steps = it },
                                onScanClick = { stepIndex -> 
                                    scanningForStepIndex = stepIndex
                                    showNfcDialog = true
                                }
                            )
                        }

                        Box(
                            modifier = Modifier.weight(0.4f).padding(vertical = Dimensions.SpaceL),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            SubmitButton(
                                isLoading = uiState.isLoading,
                                onClick = dropUnlessResumed {
                                    val finalTag = nfcTag.trim().ifEmpty { null }
                                    val orderSteps = steps.mapIndexed { index, draftStep -> 
                                        OrderStep(
                                            stepIndex = index + 1,
                                            targetUidHex = draftStep.targetUidHex.trim(),
                                            locationCode = draftStep.locationCode.trim().ifEmpty { null },
                                            displayName = draftStep.displayName.trim()
                                        )
                                    }.filter { it.targetUidHex.isNotEmpty() }
                                    
                                    viewModel.createOrder(context, title, description, orderType, finalTag, orderSteps)
                                }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding()
                            )
                            .padding(horizontal = Dimensions.SpaceL)
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
                    ) {
                        Spacer(modifier = Modifier.height(Dimensions.SpaceXS))
                        CreateOrderFormFields(
                            title = title,
                            onTitleChange = { title = it },
                            description = description,
                            onDescriptionChange = { description = it },
                            orderType = orderType,
                            onOrderTypeChange = { orderType = it },
                            nfcTag = nfcTag,
                            onNfcTagChange = { nfcTag = it },
                            locationCode = locationCode,
                            onLocationCodeChange = { locationCode = it },
                            steps = steps,
                            onStepsChange = { steps = it },
                            onScanClick = { stepIndex -> 
                                scanningForStepIndex = stepIndex
                                showNfcDialog = true
                            }
                        )

                        Spacer(modifier = Modifier.height(Dimensions.SpaceS))

                        SubmitButton(
                            isLoading = uiState.isLoading,
                            onClick = dropUnlessResumed {
                                val finalTag = nfcTag.trim().ifEmpty { null }
                                val orderSteps = steps.mapIndexed { index, draftStep -> 
                                    OrderStep(
                                        stepIndex = index + 1,
                                        targetUidHex = draftStep.targetUidHex.trim(),
                                        locationCode = draftStep.locationCode.trim().ifEmpty { null },
                                        displayName = draftStep.displayName.trim()
                                    )
                                }.filter { it.targetUidHex.isNotEmpty() }
                                
                                viewModel.createOrder(context, title, description, orderType, finalTag, orderSteps)
                            }
                        )
                        Spacer(modifier = Modifier.height(Dimensions.SpaceL))
                    }
                }
            }
        }
    }

    if (showNfcDialog) {
        NfcScannerDialog(
            onDismiss = { showNfcDialog = false },
            onScanned = { uidHex, text ->
                if (scanningForStepIndex == null) {
                    nfcTag = uidHex
                    if (locationCode.isBlank() && !text.isNullOrBlank()) {
                        locationCode = text
                    }
                } else {
                    val index = scanningForStepIndex!!
                    val newSteps = steps.toMutableList()
                    if (index in newSteps.indices) {
                        var newCode = newSteps[index].locationCode
                        if (newCode.isBlank() && !text.isNullOrBlank()) {
                            newCode = text
                        }
                        newSteps[index] = newSteps[index].copy(targetUidHex = uidHex, locationCode = newCode)
                        steps = newSteps
                    }
                }
                showNfcDialog = false
            }
        )
    }
}

@Composable
private fun CreateOrderFormFields(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    orderType: String,
    onOrderTypeChange: (String) -> Unit,
    nfcTag: String,
    onNfcTagChange: (String) -> Unit,
    locationCode: String,
    onLocationCodeChange: (String) -> Unit,
    steps: List<DraftStep>,
    onStepsChange: (List<DraftStep>) -> Unit,
    onScanClick: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.admin_order_title_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.admin_order_desc_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = Dimensions.App.TextFieldMinLines
        )

        // Order Type Selector
        SplicedColumnGroup(title = stringResource(R.string.admin_order_type_selector_title)) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOrderTypeChange("standard") }
                            .padding(end = Dimensions.SpaceS),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = orderType == "standard", onClick = { onOrderTypeChange("standard") })
                        Text(stringResource(R.string.worker_order_type_standard))
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOrderTypeChange("sequence") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = orderType == "sequence", onClick = { onOrderTypeChange("sequence") })
                        Text(stringResource(R.string.worker_order_type_sequence))
                    }
                }
            }
        }

        if (orderType == "standard") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nfcTag,
                    onValueChange = onNfcTagChange,
                    label = { Text(stringResource(R.string.admin_order_nfc_hint_required)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { onScanClick(null) }, modifier = Modifier.padding(start = Dimensions.SpaceS)) {
                    Icon(Icons.Default.Nfc, contentDescription = "Scan NFC")
                }
            }
            OutlinedTextField(
                value = locationCode,
                onValueChange = onLocationCodeChange,
                label = { Text(stringResource(R.string.admin_order_step_location_code_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else {
            // Sequence Steps
            steps.forEachIndexed { index, step ->
                SplicedColumnGroup(title = stringResource(R.string.admin_order_step_header, index + 1)) {
                    item {
                        Column(modifier = Modifier.padding(Dimensions.SpaceS), verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = step.targetUidHex,
                                    onValueChange = { newVal ->
                                        val newList = steps.toMutableList()
                                        newList[index] = step.copy(targetUidHex = newVal)
                                        onStepsChange(newList)
                                    },
                                    label = { Text(stringResource(R.string.admin_order_step_nfc_hint)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(onClick = { onScanClick(index) }) {
                                    Icon(Icons.Default.Nfc, contentDescription = stringResource(R.string.admin_order_scan_nfc_btn))
                                }
                            }
                            OutlinedTextField(
                                value = step.locationCode,
                                onValueChange = { newVal ->
                                    val newList = steps.toMutableList()
                                    newList[index] = step.copy(locationCode = newVal)
                                    onStepsChange(newList)
                                },
                                label = { Text(stringResource(R.string.admin_order_step_location_code_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = step.displayName,
                                    onValueChange = { newVal ->
                                        val newList = steps.toMutableList()
                                        newList[index] = step.copy(displayName = newVal)
                                        onStepsChange(newList)
                                    },
                                    label = { Text(stringResource(R.string.admin_order_step_display_name_hint)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = {
                                        val newList = steps.toMutableList()
                                        newList.removeAt(index)
                                        onStepsChange(newList)
                                    },
                                    enabled = steps.size > 1
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.admin_order_remove_step_btn), tint = if (steps.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            
            TextButton(
                onClick = {
                    val newList = steps.toMutableList()
                    newList.add(DraftStep())
                    onStepsChange(newList)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = Dimensions.SpaceXS))
                Text(stringResource(R.string.admin_order_add_step_btn))
            }
        }
    }
}

@Composable
private fun SubmitButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimensions.IconSize.M),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.admin_order_create_btn))
        }
    }
}
