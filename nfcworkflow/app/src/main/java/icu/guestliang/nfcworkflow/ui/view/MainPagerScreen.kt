package icu.guestliang.nfcworkflow.ui.view

import icu.guestliang.nfcworkflow.navigation.items
import icu.guestliang.nfcworkflow.ui.HomeScreen
import icu.guestliang.nfcworkflow.ui.NfcScreen
import icu.guestliang.nfcworkflow.ui.SettingsScreen
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController

@Composable
fun MainPagerScreen(navController: NavController, onLogout: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()

    // 如果当前不在第 0 页，拦截返回键，使其平滑滚动回第 0 页
    BackHandler(enabled = pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

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
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 2 // 预加载左右两侧的页面，防止滑动时临时渲染导致掉帧
            ) { page ->
                when (page) {
                    0 -> HomeScreen(navController = navController)
                    1 -> NfcScreen(navController = navController)
                    2 -> SettingsScreen(onLogout = onLogout)
                }
            }
        }
    }
}