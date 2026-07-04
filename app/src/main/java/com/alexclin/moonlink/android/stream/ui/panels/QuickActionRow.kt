package com.alexclin.moonlink.android.stream.ui.panels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.common.MoonLinkQuickActions
import com.alexclin.moonlink.android.stream.ui.common.getActionIcon
import com.alexclin.moonlink.android.stream.ui.common.getActionLabel
import com.alexclin.moonlink.android.util.QuickActionRegistry
import com.limelight.binding.input.KeyboardTranslator
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil

@Composable
fun QuickActionRow(
    engine: StreamEngine,
    configIds: List<String>,
    onEditClick: () -> Unit,
) {
    val context = LocalContext.current
    val visibleIds = configIds.take(if (engine.prefConfig.showPauseStream) 3 else 4)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "快捷操作",
                style = MaterialTheme.typography.labelMedium.copy(
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onEditClick,
                modifier = Modifier.height(26.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(
                    "编辑",
                    style = MaterialTheme.typography.labelSmall.copy(
                        lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(visibleIds.size) { index ->
                val id = visibleIds[index]

                val isActive = when (id) {
                    "toggle_audio" -> !engine.isAudioMuted
                    "toggle_hdr" -> engine.isHdrEnabled
                    "toggle_mic" -> engine.prefConfig.enableMic
                    "toggle_pip" -> engine.prefConfig.enablePip
                    "toggle_adaptive_bitrate" -> engine.prefConfig.enableAdaptiveBitrate
                    "toggle_gyro" -> engine.prefConfig.gyroToRightStick || engine.prefConfig.gyroToMouse
                    "toggle_perf" -> engine.prefConfig.enablePerfOverlay
                    else -> null
                }
                val icon = getActionIcon(id, isActive)
                val label = getActionLabel(id)
                val isAvailable = id != "toggle_mic" || engine.prefConfig.enableMic

                QuickActionChip(
                    modifier = Modifier.padding(end = 4.dp),
                    label = label,
                    icon = icon,
                    isActive = isActive,
                    isAvailable = isAvailable,
                    isFixed = false,
                    onClick = {
                        if (isAvailable) {
                            executeAction(id, engine)
                        } else {
                            ToastUtil.show(
                                context,
                                "麦克风功能不可用，需要PC端安装对应虚拟驱动",
                                Toast.LENGTH_SHORT,
                            )
                        }
                    },
                )
            }

            item {
                QuickActionChip(
                    modifier = Modifier.padding(end = 4.dp),
                    label = "终止串流",
                    icon = Icons.Default.PowerSettingsNew,
                    isFixed = true,
                    onClick = { engine.disconnectAndQuit() },
                )
            }
            if (engine.prefConfig.showPauseStream) {
                item {
                    QuickActionChip(
                        modifier = Modifier.padding(end = 4.dp),
                        label = "暂停串流",
                        icon = Icons.Default.ExitToApp,
                        isFixed = true,
                        onClick = { engine.disconnect() },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector?,
    isActive: Boolean? = null,
    isAvailable: Boolean = true,
    isFixed: Boolean,
    editMode: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val scale by animateFloatAsState(if (editMode) 1.05f else 1f, label = "chipScale")
    val chipShape = RoundedCornerShape(12.dp)

    val bgColor = when {
        isFixed -> MaterialTheme.colorScheme.secondaryContainer
        isActive == true -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isActive == true -> MaterialTheme.colorScheme.onPrimaryContainer
        !isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isAvailable) 1f else 0.5f
            }
            .clip(chipShape)
            .size(56.dp),
        shape = chipShape,
        color = bgColor,
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                )
            }

            if (editMode && onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除",
                        modifier = Modifier.size(12.dp),
                        tint = Color.Red,
                    )
                }
            }
        }
    }
}

private fun executeAction(id: String, engine: StreamEngine) {
    when (id) {
        "send_win" -> engine.sendKeys(shortArrayOf(KeyboardTranslator.VK_LWIN.toShort()))
        "send_esc" -> engine.sendKeys(shortArrayOf(KeyboardTranslator.VK_ESCAPE.toShort()))
        "toggle_hdr" -> engine.sendKeys(shortArrayOf(
            KeyboardTranslator.VK_LWIN.toShort(),
            KeyboardTranslator.VK_MENU.toShort(),
            'B'.code.toShort(),
        ))
        "toggle_audio" -> engine.toggleAudioMute()
        "toggle_mic" -> engine.toggleMicrophoneButton()
        "send_sleep" -> {
            engine.sendKeys(shortArrayOf(KeyboardTranslator.VK_LWIN.toShort(), 88.toShort()))
            // Sleep command U + S sent after delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                engine.sendKeys(shortArrayOf(85.toShort(), 83.toShort()))
            }, 100)
        }
        "send_tab" -> engine.sendKeys(shortArrayOf(KeyboardTranslator.VK_TAB.toShort()))
        "send_alt_tab" -> engine.sendKeys(shortArrayOf(
            KeyboardTranslator.VK_MENU.toShort(),
            KeyboardTranslator.VK_TAB.toShort(),
        ))
        "send_alt_f4" -> engine.sendKeys(shortArrayOf(
            KeyboardTranslator.VK_MENU.toShort(),
            (KeyboardTranslator.VK_F1 + 3).toShort(),
        ))
        "toggle_keyboard" -> engine.toggleKeyboard()
        "toggle_controller" -> { /* toggleVirtualController: 待实现 */ }
        "toggle_perf" -> engine.togglePerformanceOverlay()
        MoonLinkQuickActions.TOGGLE_PIP -> engine.togglePip()
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE -> engine.toggleAdaptiveBitrate()
        MoonLinkQuickActions.TOGGLE_GYRO -> engine.toggleGyro()
    }
}
