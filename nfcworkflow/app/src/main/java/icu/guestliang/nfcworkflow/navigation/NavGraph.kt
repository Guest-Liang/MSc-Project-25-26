package icu.guestliang.nfcworkflow.navigation

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.ui.HomeScreen
import icu.guestliang.nfcworkflow.ui.SettingsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Screen(val route: String, val resourceId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.tab_home, Icons.Default.Home)
    object Settings : Screen("settings", R.string.tab_settings, Icons.Default.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.Settings,
)

@Composable
fun NavGraph(navController: NavHostController, modifier: androidx.compose.ui.Modifier) {
    NavHost(navController, startDestination = Screen.Home.route, modifier) {
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
