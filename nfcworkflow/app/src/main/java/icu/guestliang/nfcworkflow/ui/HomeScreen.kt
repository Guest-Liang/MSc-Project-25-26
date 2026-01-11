package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.ui.components.SwitchGroup
import icu.guestliang.nfcworkflow.ui.components.SwitchItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .navigationBarsPadding(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SwitchGroup(
                    title = stringResource(id = R.string.example_switch_group_title),
                    items = listOf(
                        SwitchItem(
                            icon = Icons.Default.Info,
                            title = stringResource(id = R.string.example_switch_title),
                            subtitle = stringResource(R.string.example_switch_desc),
                            isChecked = false,
                            onCheckedChange = {}
                        )
                    )
                )
            }
        }
    }
}
