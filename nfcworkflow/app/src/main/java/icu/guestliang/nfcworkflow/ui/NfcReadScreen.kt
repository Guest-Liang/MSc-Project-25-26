package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.NfcHistoryManager
import icu.guestliang.nfcworkflow.data.NfcReadRecord
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.navigation.Screen
import icu.guestliang.nfcworkflow.nfc.parseNfcTag
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.components.SplicedJumpPageWidget
import icu.guestliang.nfcworkflow.ui.components.SplicedSwitchWidget
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import icu.guestliang.nfcworkflow.utils.findActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.content.Intent
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun NfcReadScreen(navController: NavController, viewModel: NfcViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    
    AppLogger.debug(context, "NfcReadScreen recomposed", "UI")

    LaunchedEffect(Unit) {
        NfcHistoryManager.loadHistory(context)
    }

    val historyList by NfcHistoryManager.historyFlow.collectAsState()

    val nfcNotSupportedStr = stringResource(id = R.string.nfc_not_supported)
    val nfcDisabledStr = stringResource(id = R.string.nfc_disabled_prompt)

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
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
        ) {
            item {
                SplicedColumnGroup(title = stringResource(id = R.string.nfc_read_group_switch)) {
                    item {
                        SplicedSwitchWidget(
                            icon = Icons.Default.Nfc,
                            title = stringResource(id = R.string.nfc_read_title),
                            description = stringResource(id = R.string.nfc_read_desc),
                            checked = uiState.showReadNfcDialog,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                                    if (nfcAdapter == null) {
                                        Toast.makeText(context, nfcNotSupportedStr, Toast.LENGTH_SHORT).show()
                                    } else if (!nfcAdapter.isEnabled) {
                                        Toast.makeText(context, nfcDisabledStr, Toast.LENGTH_SHORT).show()
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    } else {
                                        viewModel.updateState { it.copy(showReadNfcDialog = true) }
                                    }
                                } else {
                                    viewModel.updateState { it.copy(showReadNfcDialog = false, readNfcResult = null) }
                                }
                            }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(id = R.string.nfc_read_group_history)) {
                    item {
                        SplicedJumpPageWidget(
                            icon = Icons.Default.History,
                            title = stringResource(id = R.string.nfc_read_history_title, historyList.size),
                            description = stringResource(id = R.string.nfc_read_history_desc),
                            onClick = { navController.navigate(Screen.NfcReadHistory.route) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showReadNfcDialog) {
        NfcReaderDialog(
            onDismiss = {
                viewModel.updateState { it.copy(showReadNfcDialog = false, readNfcResult = null) }
            },
            nfcResult = uiState.readNfcResult,
            onNfcRead = { result ->
                viewModel.updateState { it.copy(readNfcResult = result) }
                
                val idMatch = Regex("ID: ([\\w:]+)").find(result)
                val simpleUid = idMatch?.groupValues?.get(1) ?: "Unknown UID"

                val record = NfcReadRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    uid = simpleUid,
                    details = result
                )
                coroutineScope.launch {
                    NfcHistoryManager.addRecord(context, record)
                }
            }
        )
    }
}

@Composable
fun NfcReadHistoryPage(viewModel: NfcViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val historyList by NfcHistoryManager.historyFlow.collectAsState()

    NfcReadHistoryScreen(
        historyList = historyList,
        viewModel = viewModel,
        onBack = onBack,
        onDeleteRecords = { ids ->
            coroutineScope.launch {
                NfcHistoryManager.deleteRecords(context, ids)
                viewModel.clearSelection()
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NfcReadHistoryScreen(
    historyList: List<NfcReadRecord>,
    viewModel: NfcViewModel,
    onBack: () -> Unit,
    onDeleteRecords: (Set<String>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
        viewModel.clearSelection()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nfc_read_history_page_title) + " (${historyList.size})") },
                navigationIcon = {
                    IconButton(onClick = { 
                        onBack() 
                        viewModel.clearSelection()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back))
                    }
                },
                actions = {
                    if (uiState.readSelectionMode) {
                        IconButton(onClick = { 
                            viewModel.updateState { it.copy(showReadDeleteConfirm = true) } 
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.nfc_read_history_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (isLandscape) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = Dimensions.SpaceM,
                        end = Dimensions.SpaceM,
                        top = innerPadding.calculateTopPadding() + Dimensions.SpaceM,
                        bottom = innerPadding.calculateBottomPadding() + Dimensions.SpaceM
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceM),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceM),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LongPressHintCard()
                    }
                    items(items = historyList, key = { it.id }) { record ->
                        HistoryCard(
                            record = record,
                            selectionMode = uiState.readSelectionMode,
                            isSelected = uiState.readSelectedIds.contains(record.id),
                            onLongClick = {
                                if (!uiState.readSelectionMode) {
                                    viewModel.updateState { 
                                        it.copy(readSelectionMode = true, readSelectedIds = setOf(record.id)) 
                                    }
                                }
                            },
                            onClick = {
                                if (uiState.readSelectionMode) {
                                    viewModel.toggleSelection(record.id)
                                }
                            },
                            dateString = df.format(Date(record.timestamp))
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Dimensions.SpaceM,
                        end = Dimensions.SpaceM,
                        top = innerPadding.calculateTopPadding() + Dimensions.SpaceM,
                        bottom = innerPadding.calculateBottomPadding() + Dimensions.SpaceL
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceM),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        LongPressHintCard()
                    }
                    items(items = historyList, key = { it.id }) { record ->
                        HistoryCard(
                            record = record,
                            selectionMode = uiState.readSelectionMode,
                            isSelected = uiState.readSelectedIds.contains(record.id),
                            onLongClick = {
                                if (!uiState.readSelectionMode) {
                                    viewModel.updateState { 
                                        it.copy(readSelectionMode = true, readSelectedIds = setOf(record.id)) 
                                    }
                                }
                            },
                            onClick = {
                                if (uiState.readSelectionMode) {
                                    viewModel.toggleSelection(record.id)
                                }
                            },
                            dateString = df.format(Date(record.timestamp))
                        )
                    }
                }
            }
        }
    }

    if (uiState.showReadDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.updateState { it.copy(showReadDeleteConfirm = false) } },
            title = { Text(stringResource(id = R.string.nfc_read_history_delete_title)) },
            text = { Text(stringResource(id = R.string.nfc_read_history_delete_prompt, uiState.readSelectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecords(uiState.readSelectedIds)
                }) {
                    Text(stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.updateState { it.copy(showReadDeleteConfirm = false) } }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun LongPressHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.Radius.M),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.SpaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = Dimensions.SpaceS)
            )
            Text(
                text = stringResource(id = R.string.nfc_read_history_long_press_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(
    record: NfcReadRecord,
    selectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    dateString: String
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimensions.Radius.M))
            .combinedClickable(
                onClick = {
                    if (selectionMode) onClick()
                    else expanded = !expanded
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(Dimensions.Radius.M),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.SpaceM),
            verticalAlignment = Alignment.Top
        ) {
            AnimatedVisibility(
                visible = selectionMode,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
            ) {
                Box(
                    modifier = Modifier.height(Dimensions.Space5XL),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = Dimensions.SpaceM)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.defaultMinSize(minHeight = Dimensions.Space5XL),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "UID: ${record.uid}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimensions.SpaceS))
                        Text(
                            text = record.details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NfcReaderDialog(
    onDismiss: () -> Unit,
    nfcResult: String?,
    onNfcRead: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    DisposableEffect(activity) {
        val nfcAdapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        
        if (nfcAdapter != null && nfcAdapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or 
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or 
                        NfcAdapter.FLAG_READER_NFC_V
            
            val readerCallback = NfcAdapter.ReaderCallback { tag ->
                val result = parseNfcTag(tag, context)
                onNfcRead(result)
            }
            
            nfcAdapter.enableReaderMode(activity, readerCallback, flags, null)
        }

        onDispose {
            nfcAdapter?.disableReaderMode(activity)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.nfc_dialog_title)) },
        text = {
            if (nfcResult == null) {
                Text(stringResource(id = R.string.nfc_dialog_prompt))
            } else {
                Text(stringResource(id = R.string.nfc_dialog_result, nfcResult))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.nfc_dialog_close))
            }
        }
    )
}
