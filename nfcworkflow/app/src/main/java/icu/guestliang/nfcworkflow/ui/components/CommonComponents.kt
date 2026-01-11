package icu.guestliang.nfcworkflow.ui.components

import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    ElevatedCard(modifier) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) }
        )
        HorizontalDivider()
        content()
    }
}

