package icu.guestliang.nfcworkflow.utils

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import icu.guestliang.nfcworkflow.ui.theme.LocalUseDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 1. 定义 HazeState 的全局 Provider
val LocalHazeState = compositionLocalOf<HazeState?> { null }

// 2. 作用于顶栏 / 导航栏，渲染模糊滤镜
@Composable
fun Modifier.haze(
    alpha: Float = 1f
): Modifier {
    return LocalHazeState.current?.let { hazeState ->
        val dark = LocalUseDarkTheme.current

        val hazeStyle = HazeStyle(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tint = HazeTint(Color.Transparent)
        )

        hazeEffect(hazeState) {
            style = hazeStyle
            blurRadius = 30.dp
            noiseFactor = if (dark) 0f else 0.2f
            this.alpha = alpha
        }
    } ?: this
}

// 3. 作用于背景内容，抓取它作为模糊的底层画面
@Composable
fun Modifier.hazeSource(): Modifier {
    return LocalHazeState.current?.let {
        hazeSource(it)
    } ?: this
}
