package icu.guestliang.nfcworkflow.data

import icu.guestliang.nfcworkflow.logging.AppLogger
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
    private val KEY_TOKEN = stringPreferencesKey("auth_token")
    private val KEY_IS_WORKER = booleanPreferencesKey("is_worker")

    fun flow(context: Context): Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            theme = p[KEY_THEME]?.let { themeName ->
                runCatching { ThemeMode.valueOf(themeName) }
                    .getOrElse { e ->
                        AppLogger.error(context, e, "Failed to parse theme mode", "Prefs")
                        ThemeMode.SYSTEM
                    }
            } ?: ThemeMode.SYSTEM,
            showLogsTab = p[KEY_SHOW_LOGS] ?: true,
            autoRefreshLogs = p[KEY_LOG_AUTO] ?: true,
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
            token = p[KEY_TOKEN],
            isWorker = p[KEY_IS_WORKER] ?: false
        )
    }

    suspend fun updateTheme(context: Context, theme: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAuthToken(context: Context, token: String?, isWorker: Boolean) {
        context.dataStore.edit { prefs ->
            if (token != null) {
                prefs[KEY_TOKEN] = token
            } else {
                prefs.remove(KEY_TOKEN)
            }
            prefs[KEY_IS_WORKER] = isWorker
        }
    }

    suspend fun clearAuth(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_IS_WORKER)
        }
    }
}
