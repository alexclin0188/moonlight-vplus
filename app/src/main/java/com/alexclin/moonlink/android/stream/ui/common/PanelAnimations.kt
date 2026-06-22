package com.alexclin.moonlink.android.stream.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

object PanelAnimations {

    /** 竖向窄条进入：从右滑入 */
    val verticalBarEnter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(200))

    /** 竖向窄条退出：向右滑出 */
    val verticalBarExit = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(150))

    /** 悬浮按钮出现 */
    val fabEnter = fadeIn(animationSpec = tween(200)) + scaleIn(
        initialScale = 0.5f,
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    )

    /** 悬浮按钮消失 */
    val fabExit = fadeOut(animationSpec = tween(150)) + scaleOut(
        targetScale = 0.5f,
        animationSpec = tween(150)
    )

    /** 子面板进入：从右滑入 */
    val subPanelEnter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(200))

    /** 子面板退出：向右滑出 */
    val subPanelExit = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(150))

    /** 按钮按下缩放系数 */
    fun buttonPressScale(target: Boolean): Float = if (target) 0.95f else 1f
}
