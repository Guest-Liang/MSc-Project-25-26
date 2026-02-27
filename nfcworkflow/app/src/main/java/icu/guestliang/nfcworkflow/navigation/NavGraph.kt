package icu.guestliang.nfcworkflow.navigation

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.ui.HomeScreen
import icu.guestliang.nfcworkflow.ui.SettingsScreen
import icu.guestliang.nfcworkflow.ui.login.LoginScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Screen(val route: String, val resourceId: Int? = null, val icon: ImageVector? = null) {
    object Login : Screen("login")
    object Home : Screen("home", R.string.tab_home, Icons.Default.Home)
    object Settings : Screen("settings", R.string.tab_settings, Icons.Default.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.Settings,
)

@Composable
fun NavGraph(navController: NavHostController, modifier: androidx.compose.ui.Modifier) {
    NavHost(navController, startDestination = Screen.Login.route, modifier) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }
        composable(Screen.Home.route) { HomeScreen(navController) }
        composable(Screen.Settings.route) { 
            SettingsScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            ) 
        }
    }
}
