package com.alexclin.moonlink.stream.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.stream.ui.common.PanelAnimations

/**
 * 竖向窄条面板（一级面板），用于竖屏。
 *
 * - 紧贴右边缘，竖直方向填满屏幕高度
 * - 4 个入口按钮上下均匀分布
 * - 宽 60dp，左侧圆角 16dp
 * - 操作/键盘按钮支持按下态高亮
 */
@Composable
fun VerticalBar(
    activeEntry: String?,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(60.dp),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PanelEntry(
                icon = Icons.Default.Settings,
                label = "操作",
                isSelected = activeEntry == "operations",
                onClick = { onEntryClick("operations") },
            )

            PanelEntry(
                icon = Icons.Default.Keyboard,
                label = "键盘",
                isSelected = activeEntry == "keyboard",
                onClick = { onEntryClick("keyboard") },
            )

            PanelEntry(
                icon = Icons.Default.DesktopWindows,
                label = "桌面",
                isSelected = false,
                onClick = { onEntryClick("show_desktop") },
            )

            PanelEntry(
                icon = Icons.Default.Window,
                label = "窗口",
                isSelected = false,
                onClick = { onEntryClick("show_windows") },
            )
        }
    }
}

/**
 * 横向窄条面板（一级面板），用于横屏。
 *
 * - 紧贴底部，水平方向填满屏幕宽度
 * - 4 个入口按钮左右均匀分布
 * - 高 60dp，顶部圆角 16dp
 * - 操作/键盘按钮支持按下态高亮
 */
@Composable
fun HorizontalBar(
    activeEntry: String?,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelEntry(
                icon = Icons.Default.Settings,
                label = "操作",
                isSelected = activeEntry == "operations",
                onClick = { onEntryClick("operations") },
            )

            PanelEntry(
                icon = Icons.Default.Keyboard,
                label = "键盘",
                isSelected = activeEntry == "keyboard",
                onClick = { onEntryClick("keyboard") },
            )

            PanelEntry(
                icon = Icons.Default.DesktopWindows,
                label = "桌面",
                isSelected = false,
                onClick = { onEntryClick("show_desktop") },
            )

            PanelEntry(
                icon = Icons.Default.Window,
                label = "窗口",
                isSelected = false,
                onClick = { onEntryClick("show_windows") },
            )
        }
    }
}

@Composable
internal fun PanelEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = PanelAnimations.buttonPressScale(isPressed),
        label = "pressScale",
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "entryBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        label = "entryFg",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(
                    bgColor,
                    RoundedCornerShape(8.dp),
                ) else Modifier
            )
            .scale(pressScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}
