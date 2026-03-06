package icu.guestliang.nfcworkflow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import icu.guestliang.nfcworkflow.R
import java.util.Calendar
import java.util.Locale

@Composable
fun CustomDateTimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    var year by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(cal.get(Calendar.MONTH) + 1) } // 1-12
    var day by remember { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }
    var second by remember { mutableIntStateOf(cal.get(Calendar.SECOND)) }

    val daysInMonth = remember(year, month) {
        val tempCal = Calendar.getInstance()
        tempCal.set(Calendar.YEAR, year)
        tempCal.set(Calendar.MONTH, month - 1)
        tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    LaunchedEffect(daysInMonth) {
        if (day > daysInMonth) {
            day = daysInMonth
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f) // Take up most of the screen width
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.time_picker_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NumberPickerColumn(
                        range = 2000..2030,
                        value = year,
                        onValueChange = { year = it },
                        label = stringResource(R.string.time_picker_year),
                        width = 56.dp // Give year a bit more space
                    )
                    NumberPickerColumn(
                        range = 1..12,
                        value = month,
                        onValueChange = { month = it },
                        label = stringResource(R.string.time_picker_month)
                    )
                    NumberPickerColumn(
                        range = 1..daysInMonth,
                        value = day,
                        onValueChange = { day = it },
                        label = stringResource(R.string.time_picker_day)
                    )
                    NumberPickerColumn(
                        range = 0..23,
                        value = hour,
                        onValueChange = { hour = it },
                        label = stringResource(R.string.time_picker_hour)
                    )
                    NumberPickerColumn(
                        range = 0..59,
                        value = minute,
                        onValueChange = { minute = it },
                        label = stringResource(R.string.time_picker_minute)
                    )
                    NumberPickerColumn(
                        range = 0..59,
                        value = second,
                        onValueChange = { second = it },
                        label = stringResource(R.string.time_picker_second)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second)
                        onConfirm(formatted)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
fun NumberPickerColumn(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    width: androidx.compose.ui.unit.Dp = 40.dp
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = range.indexOf(value).coerceAtLeast(0))

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isNotEmpty()) {
                val viewportCenter = (layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset) / 2
                val closestItem = visibleItemsInfo.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                }
                closestItem?.let {
                    val snappedValue = range.elementAt(it.index)
                    if (snappedValue != value) {
                        onValueChange(snappedValue)
                    }
                    listState.animateScrollToItem(it.index)
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .height(120.dp)
                .width(width),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 40.dp), // Padding to allow top/bottom items to reach center
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(range.toList()) { itemValue ->
                    val isSelected = itemValue == value
                    Text(
                        text = if (range.last > 99) itemValue.toString() else String.format(Locale.getDefault(), "%02d", itemValue),
                        style = if (isSelected) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .clickable {
                                onValueChange(itemValue)
                            }
                    )
                }
            }
        }
    }
}
