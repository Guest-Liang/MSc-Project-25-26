package icu.guestliang.nfcworkflow.navigation

import icu.guestliang.nfcworkflow.R
import icu.guestliang.nfcworkflow.ui.admin.AdminAssignOrderScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminCreateOrderScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminQueryLogsScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminRegisterWorkerScreen
import icu.guestliang.nfcworkflow.ui.admin.AdminSearchOrdersScreen
import icu.guestliang.nfcworkflow.ui.login.LoginScreen
import icu.guestliang.nfcworkflow.ui.login.RegisterScreen
import icu.guestliang.nfcworkflow.ui.worker.CompleteOrderScreen
import icu.guestliang.nfcworkflow.ui.worker.ViewOrdersScreen
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Screen(val route: String, val resourceId: Int? = null, val icon: ImageVector? = null) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ResetPassword : Screen("reset_password")
    object Main : Screen("main") // This will contain the ViewPager for Home, Nfc, Settings
    object WorkerOrders : Screen("worker_orders")
    object WorkerCompleteOrder : Screen("worker_complete_order")
    object AdminRegisterWorker : Screen("admin_register_worker")
    object AdminCreateOrder : Screen("admin_create_order")
    object AdminAssignOrder : Screen("admin_assign_order")
    object AdminSearchOrders : Screen("admin_search_orders")
    object AdminQueryLogs : Screen("admin_query_logs")
}

class BottomNavItem(val route: String, val resourceId: Int, val icon: ImageVector)

val items = listOf(
    BottomNavItem("home", R.string.tab_home, Icons.Default.Home),
    BottomNavItem("nfc", R.string.tab_nfc, Icons.Default.Nfc),
    BottomNavItem("settings", R.string.tab_settings, Icons.Default.Settings),
)

@Composable
fun NavGraph(navController: NavHostController, modifier: androidx.compose.ui.Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier,
        enterTransition = {
            // 子页面进入：从右向左滑入并淡入
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeIn(tween(300))
        },
        exitTransition = {
            // 父页面退出：稍微向左偏移并淡出
            fadeOut(tween(300))
        },
        popEnterTransition = {
            // 父页面返回：从左向右（与子页面进入方向相反）滑入并淡入
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeIn(tween(300))
        },
        popExitTransition = {
            // 子页面返回退出：向中心收缩并淡出
            scaleOut(
                targetScale = 0.85f, // 收缩到 85% 大小
                animationSpec = tween(300)
            ) + fadeOut(tween(300))
        }
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                    }
                },
                onRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onResetPassword = {
                    navController.navigate(Screen.ResetPassword.route)
                }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                isResetPassword = false,
                onSuccess = {
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.ResetPassword.route) {
            RegisterScreen(
                isResetPassword = true,
                onSuccess = {
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Main.route) {
            icu.guestliang.nfcworkflow.ui.view.MainPagerScreen(
                navController = navController,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }
        composable(Screen.WorkerOrders.route) {
            ViewOrdersScreen(navController)
        }
        composable(Screen.WorkerCompleteOrder.route) {
            CompleteOrderScreen(navController)
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
    }
}
