package icu.guestliang.nfcworkflow.ui.view

import icu.guestliang.nfcworkflow.navigation.items
import icu.guestliang.nfcworkflow.ui.HomeScreen
import icu.guestliang.nfcworkflow.ui.NfcReadScreen
import icu.guestliang.nfcworkflow.ui.NfcWriteScreen
import icu.guestliang.nfcworkflow.ui.SettingsScreen
import kotlinx.coroutines.launch
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@Composable
fun MainPagerScreen(navController: NavController, onLogout: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // We use a nested NavController to handle transitions like pushing "History" on top of "NFC Read"
    val nestedNavController = rememberNavController()

    // 如果当前不在第 0 页，拦截返回键，使其平滑滚动回第 0 页
    BackHandler(enabled = pagerState.currentPage != 0 && nestedNavController.previousBackStackEntry == null) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.background
            ) {
                items.forEachIndexed { index, screen ->
                    NavigationRailItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.resourceId)) },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2,
                    userScrollEnabled = false // Prevent swiping if a sub-screen is active
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(navController = navController)
                        1 -> NfcReadScreen(navController = navController)
                        2 -> NfcWriteScreen(navController = navController)
                        3 -> SettingsScreen(onLogout = onLogout)
                    }
                }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    items.forEachIndexed { index, screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.resourceId)) },
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(navController = navController)
                        1 -> NfcReadScreen(navController = navController)
                        2 -> NfcWriteScreen(navController = navController)
                        3 -> SettingsScreen(onLogout = onLogout)
                    }
                }
            }
        }
    }
}
