package icu.guestliang.nfcworkflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore by preferencesDataStore("nfcworkflow_prefs")

object PrefsDataStore {
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_SHOW_LOGS = booleanPreferencesKey("show_logs_tab")
    private val KEY_LOG_AUTO = booleanPreferencesKey("log_auto_refresh")
    private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

    fun flow(context: Context): Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            theme = p[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            showLogsTab = p[KEY_SHOW_LOGS] ?: true,
            autoRefreshLogs = p[KEY_LOG_AUTO] ?: true,
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun updateTheme(context: Context, theme: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setShowLogs(context: Context, show: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_LOGS] = show }
    }

    suspend fun setLogAutoRefresh(context: Context, auto: Boolean) {
        context.dataStore.edit { it[KEY_LOG_AUTO] = auto }
    }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }
}
