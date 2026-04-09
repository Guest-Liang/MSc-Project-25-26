package icu.guestliang.nfcworkflow

import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.logging.AppLogger
import icu.guestliang.nfcworkflow.navigation.NavGraph
import icu.guestliang.nfcworkflow.navigation.Screen
import icu.guestliang.nfcworkflow.ui.theme.NFCWorkFlowTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.info(this, "MainActivity created", "App")

        enableEdgeToEdge()

        setContent {
            val prefs by PrefsDataStore.flow(this).collectAsState(initial = null)
            prefs?.let { currentPrefs ->
                NFCWorkFlowTheme(prefs = currentPrefs) {
                    val navController = rememberNavController()
                    val startDest = if (!currentPrefs.token.isNullOrEmpty()) Screen.Main.route else Screen.Login.route
                    NavGraph(
                        navController = navController,
                        modifier = Modifier.fillMaxSize(),
                        startDestination = startDest
                    )
                }
            }
        }
    }
}
