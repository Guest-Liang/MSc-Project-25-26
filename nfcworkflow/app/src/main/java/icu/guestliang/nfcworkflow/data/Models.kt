package icu.guestliang.nfcworkflow.data

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val showLogsTab: Boolean = true,
    val autoRefreshLogs: Boolean = true,
    val dynamicColor: Boolean = true,
)
