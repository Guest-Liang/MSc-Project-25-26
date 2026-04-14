package icu.guestliang.nfcworkflow.navigation

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.data.PrefsDataStore
import icu.guestliang.nfcworkflow.network.AuthManager
import icu.guestliang.nfcworkflow.ui.NfcReadHistoryPage
import icu.guestliang.nfcworkflow.ui.NfcViewModel
import icu.guestliang.nfcworkflow.ui.admin.AdminAssignOrderScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminCreateOrderScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminQueryLogsScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminRegisterWorkerScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminSearchOrdersScreen
import icu.guestliang.nfcworkflow.ui.login.LoginScreen
import icu.guestliang.nfcworkflow.ui.login.RegisterScreen
import icu.guestliang.nfcworkflow.ui.worker.CompleteOrderScreen
import icu.guestliang.nfcworkflow.ui.worker.ViewOrdersScreen
import icu.guestliang.nfcworkflow.ui.worker.WorkerHistoryScreen
import icu.guestliang.nfcworkflow.ui.worker.WorkerScanScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Screen(val route: String, val resourceId: Int? = null, val icon: ImageVector? = null) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ResetPassword : Screen("reset_password")
    object Main : Screen("main")
    object WorkerOrders : Screen("worker_orders")
    object WorkerHistory : Screen("worker_history")
    object WorkerCompleteOrder : Screen("worker_complete_order")
    object WorkerScanOrder : Screen("worker_scan_order/{orderId}") {
        fun createRoute(orderId: Int) = "worker_scan_order/$orderId"
    }
    object AdminRegisterWorker : Screen("admin_register_worker")
    object AdminCreateOrder : Screen("admin_create_order")
    object AdminAssignOrder : Screen("admin_assign_order")
    object AdminSearchOrders : Screen("admin_search_orders")
    object AdminQueryLogs : Screen("admin_query_logs")
    
    // Global sub-page for NFC reading history
    object NfcReadHistory : Screen("nfc_read_history")
}

class BottomNavItem(val route: String, val resourceId: Int, val icon: ImageVector)

val items = listOf(
    BottomNavItem("home", R.string.tab_home, Icons.Default.Home),
    BottomNavItem("nfc_read", R.string.tab_nfc_read, Icons.Default.Nfc),
    BottomNavItem("nfc_write", R.string.tab_nfc_write, Icons.Default.Edit),
    BottomNavItem("settings", R.string.tab_settings, Icons.Default.Settings),
)

val LocalNfcViewModel = compositionLocalOf<NfcViewModel> { error("No NfcViewModel found") }

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: androidx.compose.ui.Modifier,
    startDestination: String = Screen.Login.route
) {
    val nfcViewModel: NfcViewModel = viewModel()
    val context = LocalContext.current

    var showAuthErrorDialog by rememberSaveable { mutableStateOf(false) }
    var authErrorCountdown by rememberSaveable { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        AuthManager.forceLogoutEvent.collect {
            if (navController.currentDestination?.route != Screen.Login.route && !showAuthErrorDialog) {
                showAuthErrorDialog = true
                authErrorCountdown = 5
                while (authErrorCountdown > 0) {
                    delay(1000)
                    authErrorCountdown--
                }
                
                showAuthErrorDialog = false
                PrefsDataStore.clearAuth(context)
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    // Active check: wait until token expiry instead of polling
    LaunchedEffect(Unit) {
        PrefsDataStore.flow(context)
            .map { it.token to it.tokenExpiry }
            .distinctUntilChanged()
            .collectLatest { (token, expiry) ->
                if (token != null && expiry > 0) {
                    val remaining = expiry - System.currentTimeMillis()
                    if (remaining > 0) {
                        delay(remaining)
                    }
                    // Token has expired or was already expired
                    AuthManager.emitForceLogout()
                }
            }
    }

    if (showAuthErrorDialog) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss manually */ },
            title = { Text(stringResource(id = R.string.login_expired_title)) },
            text = { 
                Text(stringResource(id = R.string.login_expired_msg, authErrorCountdown)) 
            },
            confirmButton = { }
        )
    }

    CompositionLocalProvider(LocalNfcViewModel provides nfcViewModel) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                fadeOut(tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(300)
                ) + fadeOut(tween(300))
            }
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onSkip = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onRegister = { navController.navigate(Screen.Register.route) },
                    onResetPassword = { navController.navigate(Screen.ResetPassword.route) }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    isResetPassword = false,
                    onSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ResetPassword.route) {
                RegisterScreen(
                    isResetPassword = true,
                    onSuccess = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Main.route) {
                icu.guestliang.nfcworkflow.ui.view.MainPagerScreen(
                    navController = navController,
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.WorkerOrders.route) {
                ViewOrdersScreen(navController)
            }
            composable(Screen.WorkerHistory.route) {
                WorkerHistoryScreen(navController)
            }
            composable(Screen.WorkerCompleteOrder.route) {
                CompleteOrderScreen(navController)
            }
            composable(Screen.WorkerScanOrder.route) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId")?.toIntOrNull() ?: 0
                WorkerScanScreen(navController, orderId)
            }
            composable(Screen.AdminRegisterWorker.route) {
                AdminRegisterWorkerScreen(navController)
            }
            composable(Screen.AdminCreateOrder.route) {
                AdminCreateOrderScreen(navController)
            }
            composable(Screen.AdminAssignOrder.route) {
                AdminAssignOrderScreen(navController)
            }
            composable(Screen.AdminSearchOrders.route) {
                AdminSearchOrdersScreen(navController)
            }
            composable(Screen.AdminQueryLogs.route) {
                AdminQueryLogsScreen(navController)
            }
            composable(Screen.NfcReadHistory.route) {
                NfcReadHistoryPage(
                    viewModel = LocalNfcViewModel.current,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
