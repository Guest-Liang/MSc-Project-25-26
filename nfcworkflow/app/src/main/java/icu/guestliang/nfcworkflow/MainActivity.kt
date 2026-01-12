package icu.guestliang.nfcworkflow

import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.ui.theme.NFCWorkFlowTheme
import icu.guestliang.nfcworkflow.ui.view.RootScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.info(this, "MainActivity created", "App")

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val prefs by PrefsDataStore.flow(this).collectAsState(initial = null)
            if (prefs != null) {
                NFCWorkFlowTheme(prefs = prefs!!) {
                    RootScreen()
                }
            }
        }
    }
}
