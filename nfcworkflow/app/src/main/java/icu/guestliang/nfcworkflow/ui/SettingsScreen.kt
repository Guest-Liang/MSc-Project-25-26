package icu.guestliang.nfcworkflow.ui

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.data.ThemeMode
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.network.ApiClient
import icu.guestliang.nfcworkflow.ui.components.SplicedColumnGroup
import icu.guestliang.nfcworkflow.ui.components.SplicedDropdownWidget
import icu.guestliang.nfcworkflow.ui.components.SplicedJumpPageWidget
import icu.guestliang.nfcworkflow.ui.components.SplicedSwitchWidget
import icu.guestliang.nfcworkflow.ui.theme.Dimensions
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    AppLogger.debug(ctx, "SettingsScreen recomposed", "UI")
    val scope = rememberCoroutineScope()
    val prefsState by PrefsDataStore.flow(ctx).collectAsState(initial = null)

    val currentPrefs = prefsState ?: return

    val themeTitle = stringResource(id = R.string.theme_title)
    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(id = R.string.theme_system),
        ThemeMode.LIGHT to stringResource(id = R.string.theme_light),
        ThemeMode.DARK to stringResource(id = R.string.theme_dark)
    )
    val selectedThemeIndex = themeOptions.indexOfFirst { it.first == currentPrefs.theme }.coerceAtLeast(0)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                top = Dimensions.SpaceS,
                bottom = Dimensions.SpaceL
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceS)
        ) {
            item {
                SplicedColumnGroup(title = stringResource(id = R.string.appearance_title)) {
                    item {
                        SplicedDropdownWidget(
                            icon = Icons.Default.DarkMode,
                            iconPlaceholder = true,
                            title = themeTitle,
                            items = themeOptions.map { it.second },
                            selectedIndex = selectedThemeIndex,
                            onSelectedIndexChange = { index ->
                                val mode = themeOptions[index].first
                                scope.launch {
                                    AppLogger.info(ctx, "Theme changed: $mode", "Settings")
                                    PrefsDataStore.updateTheme(ctx, mode)
                                }
                            }
                        )
                    }
                    item {
                        SplicedSwitchWidget(
                            icon = Icons.Default.Brush,
                            title = stringResource(id = R.string.dynamic_color_title),
                            description = stringResource(id = R.string.dynamic_color_desc),
                            checked = currentPrefs.dynamicColor,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    AppLogger.info(ctx, "Dynamic color changed: $enabled", "Settings")
                                    PrefsDataStore.setDynamicColor(ctx, enabled)
                                }
                            }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(id = R.string.language_title)) {
                    item {
                        SplicedJumpPageWidget(
                            icon = Icons.Default.GTranslate,
                            title = stringResource(id = R.string.language),
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
            }

            item {
                Spacer(modifier = Modifier.padding(top = Dimensions.SpaceL))
                
                Button(
                    onClick = {
                        val currentToken = currentPrefs.token
                        if (!currentToken.isNullOrEmpty()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    AppLogger.info(ctx, "Sending logout request to API...", "Settings")
                                    ApiClient.client.post("auth/logout") {
                                        header(HttpHeaders.Authorization, "Bearer $currentToken")
                                    }
                                    AppLogger.info(ctx, "Logout request sent successfully.", "Settings")
                                } catch (e: Exception) {
                                    AppLogger.error(ctx, e, "Failed to notify API on logout", "Settings")
                                }
                            }
                        } else {
                            AppLogger.info(ctx, "No token found locally, skipping API logout.", "Settings")
                        }

                        scope.launch {
                            PrefsDataStore.clearAuth(ctx)
                            onLogout()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.SpaceL),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.IconSize.M)
                    )
                    Spacer(modifier = Modifier.width(Dimensions.SpaceS))
                    Text(stringResource(id = R.string.settings_logout))
                }
            }
        }
    }
}
