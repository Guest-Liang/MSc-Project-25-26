package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.data.ThemeMode
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    AppLogger.debug(ctx, "SettingsScreen recomposed", "UI")
    val scope = rememberCoroutineScope()
    val prefs by PrefsDataStore.flow(ctx).collectAsState(initial = null)

    if (prefs == null) return

    var showThemeDialog by remember { mutableStateOf(false) }

    val themeTitle = stringResource(id = R.string.theme_title)
    val themeSubtitle = when (prefs!!.theme) {
        ThemeMode.SYSTEM -> stringResource(id = R.string.theme_system)
        ThemeMode.DARK -> stringResource(id = R.string.theme_dark)
        ThemeMode.LIGHT -> stringResource(id = R.string.theme_light)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensions.SpaceL)
            .padding(top = Dimensions.SpaceS, bottom = Dimensions.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceL)
    ) {
        SettingsCard(title = stringResource(id = R.string.appearance_title)) {
            SettingItem(
                icon = Icons.Default.DarkMode,
                title = themeTitle,
                subtitle = themeSubtitle,
                onClick = {
                    showThemeDialog = true
                    AppLogger.info(ctx, "Opening theme settings", "Settings")
                }
            )

            SettingsDivider()

            SwitchSettingItem(
                icon = Icons.Default.Brush,
                title = stringResource(id = R.string.dynamic_color_title),
                summary = stringResource(id = R.string.dynamic_color_desc),
                checked = prefs!!.dynamicColor,
                onChange = { enabled ->
                    scope.launch {
                        AppLogger.info(ctx, "Dynamic color changed: $enabled", "Settings")
                        PrefsDataStore.setDynamicColor(ctx, enabled)
                    }
                }
            )
        }

        SettingsCard(title = stringResource(id = R.string.language_title)) {
            SettingItem(
                icon = Icons.Default.GTranslate,
                title = stringResource(id = R.string.language),
                subtitle = null,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            AppLogger.info(ctx, "Opening app locale settings", "Settings")
                            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                            }
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            AppLogger.error(ctx, e, "Failed to open app locale settings", "Settings")
                        }
                    } else {
                        // 兜底：打开系统语言设置
                        AppLogger.info(ctx, "Opening system locale settings", "Settings")
                        val fallback = Intent(Settings.ACTION_LOCALE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(fallback)
                    }
                }
            )
        }
    }

    if (showThemeDialog) {
        val options = listOf(
            ThemeMode.SYSTEM to stringResource(id = R.string.theme_system),
            ThemeMode.LIGHT to stringResource(id = R.string.theme_light),
            ThemeMode.DARK to stringResource(id = R.string.theme_dark)
        )
        val selectedIndex = options.indexOfFirst { it.first == prefs!!.theme }.coerceAtLeast(0)

        SingleChoiceDialog(
            title = themeTitle,
            options = options.map { it.second },
            selectedIndex = selectedIndex,
            onOptionSelected = { index ->
                val mode = options[index].first
                scope.launch {
                    AppLogger.info(ctx, "Theme changed: $mode", "Settings")
                    PrefsDataStore.updateTheme(ctx, mode)
                }
            },
            onDismiss = {
                showThemeDialog = false
                AppLogger.info(ctx, "Closing theme settings", "Settings")
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(vertical = Dimensions.SpaceS)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.SpaceL, vertical = Dimensions.SpaceS)
            )
            content()
        }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimensions.SpaceL, vertical = Dimensions.SpaceM),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = Dimensions.SpaceL)
                .size(Dimensions.IconSize.M)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(Dimensions.SpaceXXS))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = Dimensions.SpaceL, vertical = Dimensions.SpaceM),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = Dimensions.SpaceL)
                .size(Dimensions.IconSize.M)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Spacer(modifier = Modifier.height(Dimensions.Divider.Thick))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = Dimensions.SpaceS))
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOptionSelected(index)
                                onDismiss()
                            }
                            .padding(vertical = Dimensions.SpaceM),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                        Text(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}
