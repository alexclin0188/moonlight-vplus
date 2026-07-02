package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alexclin.moonlink.android.stream.ui.common.CompactChip
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * 按键属性设置对话框。
 * 用于 DIGITAL_COMMON_BUTTON / DIGITAL_SWITCH_BUTTON / DIGITAL_MOVABLE_BUTTON。
 * - 所有类型：第一行 按键名 + 按键值
 * - MOVABLE_BUTTON 额外：模式 + 触控板模式 + 灵敏度 Slider
 */
@Composable
fun ButtonPropertyDialog(
    title: String,
    element: EditorElement,
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(element.elementId) { mutableStateOf(element.text) }
    var value by remember(element.elementId) { mutableStateOf(element.value) }
    var mode by remember(element.elementId) { mutableStateOf(element.mode.toString()) }
    var sense by remember(element.elementId) { mutableStateOf(element.sense.toString()) }
    var extraAttributesJson by remember(element.elementId) { mutableStateOf(element.extraAttributesJson) }
    var showKeyPicker by remember { mutableStateOf(false) }

    val isMovable = element.type == ElementType.DIGITAL_MOVABLE_BUTTON

    fun buildUpdated(): EditorElement {
        return element.copy(
            text = text,
            value = value,
            mode = mode.toIntOrNull() ?: element.mode,
            sense = sense.toIntOrNull()?.coerceIn(0, 500) ?: element.sense,
            extraAttributesJson = extraAttributesJson,
        )
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val focusManager = LocalFocusManager.current

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
                    // ── 第一行：按键名 + 按键值 ──
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 左半：按键名
                        Column(modifier = Modifier.weight(1f)) {
                            Text("按键名",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                BasicTextField(
                                    value = text,
                                    onValueChange = { text = it.take(10) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                                // 按键名为空时以键值名作为 hint
                                if (text.isEmpty()) {
                                    val hintText = getKeyLabelByValue(value) ?: value
                                    if (hintText.isNotEmpty()) {
                                        Text(
                                            hintText,
                                            style = TextStyle(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        // 右半：按键值
                        Column(modifier = Modifier.weight(1f)) {
                            Text("按键值",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            val keyLabel = getKeyLabelByValue(value) ?: value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                    .clickable { showKeyPicker = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (value.isNotEmpty()) keyLabel else "点击选择",
                                    style = TextStyle(
                                        color = if (value.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // ── 可移动按键专用：模式 + 触控板模式 + 灵敏度 Slider ──
                    if (isMovable) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 左半：模式
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("模式",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                CompactChip(
                                    label = "按钮",
                                    selected = mode == "0",
                                    onClick = { mode = "0" },
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(4.dp))
                                CompactChip(
                                    label = "摇杆",
                                    selected = mode == "1",
                                    onClick = { mode = "1" },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // 右半：触控板模式
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text("触控板模式",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                val isTrackpad = try {
                                    JSONObject(extraAttributesJson).optBoolean("isTrackpadMode", false)
                                } catch (_: Exception) { false }
                                var checked by remember(element.elementId) { mutableStateOf(isTrackpad) }
                                Switch(
                                    checked = checked,
                                    modifier = Modifier.scale(0.8f),
                                    onCheckedChange = {
                                        checked = it
                                        try {
                                            val jo = JSONObject(extraAttributesJson)
                                            jo.put("isTrackpadMode", it)
                                            extraAttributesJson = jo.toString()
                                            if (it) mode = "0"
                                        } catch (_: Exception) {}
                                    },
                                )
                            }
                        }
                        // 灵敏度 Slider
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("灵敏度: ${sense.toIntOrNull() ?: 100}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = (sense.toIntOrNull() ?: 100).toFloat(),
                                onValueChange = { sense = it.roundToInt().toString() },
                                valueRange = 1f..100f,
                                modifier = Modifier.fillMaxWidth().height(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 键值选择器弹窗 ──
    if (showKeyPicker) {
        KeyValuePickerDialog(
            onSelect = { selectedValue, _ ->
                value = selectedValue
                showKeyPicker = false
            },
            onDismiss = { showKeyPicker = false },
        )
    }
}
