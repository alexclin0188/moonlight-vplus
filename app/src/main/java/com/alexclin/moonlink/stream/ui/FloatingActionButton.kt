package com.alexclin.moonlink.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 串流界面悬浮按钮。
 *
 * - 36dp 圆形，不透明，主色背景
 * - 可全屏自由拖动
 * - 松手后停留在最后位置，不自动贴边
 * - 点击触发展开竖向窄条
 */
@Composable
fun FloatingActionButton(
    visible: Boolean,
    onToggle: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val fabSizePx = with(density) { 36.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidthPx - fabSizePx) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(offsetX, offsetY) {
        onPositionChanged(offsetX, offsetY)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(36.dp)
            .shadow(6.dp, CircleShape)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    offsetX = offsetX.coerceIn(0f, screenWidthPx - fabSizePx)
                    offsetY = offsetY.coerceIn(0f, screenHeightPx - fabSizePx)
                }
            }
            .pointerInput(onToggle) {
                detectTapGestures(onTap = { onToggle() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Menu,
            contentDescription = "菜单",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}
