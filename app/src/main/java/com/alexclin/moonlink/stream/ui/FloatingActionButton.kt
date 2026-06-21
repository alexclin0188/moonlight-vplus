package com.alexclin.moonlink.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun FloatingActionButton(
    visible: Boolean,
    onToggle: () -> Unit,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> },
    opacity: Int = 100,  // 10-100
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val fabSizePx = with(density) { 36.dp.toPx() }

    var dragOffsetX by remember { mutableFloatStateOf(initialOffsetX) }
    var dragOffsetY by remember { mutableFloatStateOf(initialOffsetY) }

    LaunchedEffect(dragOffsetX, dragOffsetY) {
        onPositionChanged(dragOffsetX, dragOffsetY)
    }

    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        // 可点击/拖拽区域（56dp 触控目标，36dp 显示按钮居中）
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                .size(46.dp)       // 触控目标 56dp
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(
                            -(screenWidthPx - fabSizePx), 0f
                        )
                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(
                            0f, screenHeightPx - fabSizePx
                        )
                    }
                }
                .pointerInput(onToggle) {
                    detectTapGestures(onTap = { onToggle() })
                },
            contentAlignment = Alignment.Center,
        ) {
            // 实际显示的按钮（36dp）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(6.dp, CircleShape)
                    .graphicsLayer(alpha = opacity.coerceIn(10, 100) / 100f)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
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
    }
}
