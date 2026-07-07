package com.alexclin.moonlink.android.stream.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R

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
            // 实际显示的按钮（36dp），使用 Surface 确保圆形阴影无八边形伪像
            val btnAlpha = opacity.coerceIn(10, 100) / 100f
            Surface(
                modifier = Modifier.size(36.dp).alpha(btnAlpha),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.fab_menu),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
