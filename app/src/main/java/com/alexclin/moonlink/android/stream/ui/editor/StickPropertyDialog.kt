package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * 摇杆/十字键属性设置对话框。
 * 用于 ANALOG_STICK / DIGITAL_STICK / INVISIBLE_ANALOG_STICK / INVISIBLE_DIGITAL_STICK / DIGITAL_PAD。
 * - 摇杆：中值 + 灵敏度 Slider + 方向值（数字摇杆）
 * - 十字键：方向值 ↑↓←→（带标签）
 */
@Composable
fun StickPropertyDialog(
    title: String,
    element: EditorElement,
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
) {
    var sense by remember(element.elementId) { mutableStateOf(element.sense.toString()) }
    var middleValue by remember(element.elementId) { mutableStateOf(element.middleValue) }
    var upValue by remember(element.elementId) { mutableStateOf(element.upValue) }
    var downValue by remember(element.elementId) { mutableStateOf(element.downValue) }
    var leftValue by remember(element.elementId) { mutableStateOf(element.leftValue) }
    var rightValue by remember(element.elementId) { mutableStateOf(element.rightValue) }
    var directionPickerTarget by remember { mutableStateOf<String?>(null) }

    val isStick = element.type in listOf(
        ElementType.ANALOG_STICK,
        ElementType.DIGITAL_STICK,
        ElementType.INVISIBLE_ANALOG_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK,
    )
    val isDigitalStick = element.type in listOf(
        ElementType.DIGITAL_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK,
    )
    val isPad = element.type == ElementType.DIGITAL_PAD

    fun buildUpdated(): EditorElement {
        return element.copy(
            sense = sense.toIntOrNull()?.coerceIn(0, 500) ?: element.sense,
            middleValue = middleValue,
            upValue = upValue,
            downValue = downValue,
            leftValue = leftValue,
            rightValue = rightValue,
        )
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .wrapContentHeight()
                .heightIn(max = (screenHeightDp * 0.95f).dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── 标题行 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onSave(buildUpdated()) }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider()

                // ── 内容 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 摇杆：中值 + 灵敏度
                    if (isStick) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KeySlotItem(
                                label = "中值",
                                value = middleValue,
                                onClick = { directionPickerTarget = "middle" },
                                icon = Icons.Default.Keyboard,
                                modifier = Modifier.weight(1f),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("灵敏度: ${sense.toIntOrNull() ?: 30}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp)
                                Slider(
                                    value = (sense.toIntOrNull() ?: 30).toFloat(),
                                    onValueChange = { sense = it.roundToInt().toString() },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.fillMaxWidth().height(16.dp),
                                )
                            }
                        }
                    }

                    // 方向值（数字摇杆无标签，十字键有标签）
                    if (isPad || isDigitalStick) {
                        DirectionValueFields(
                            upValue = upValue,
                            downValue = downValue,
                            leftValue = leftValue,
                            rightValue = rightValue,
                            onUpClick = { directionPickerTarget = "up" },
                            onDownClick = { directionPickerTarget = "down" },
                            onLeftClick = { directionPickerTarget = "left" },
                            onRightClick = { directionPickerTarget = "right" },
                        )
                    }
                }
            }
        }
    }

    // ── 键值选择器弹窗 ──
    val directionTarget = directionPickerTarget
    if (directionTarget != null) {
        KeyValuePickerDialog(
            onSelect = { selectedValue, _ ->
                when (directionTarget) {
                    "up" -> upValue = selectedValue
                    "down" -> downValue = selectedValue
                    "left" -> leftValue = selectedValue
                    "right" -> rightValue = selectedValue
                    "middle" -> middleValue = selectedValue
                }
                directionPickerTarget = null
            },
            onDismiss = { directionPickerTarget = null },
        )
    }
}
