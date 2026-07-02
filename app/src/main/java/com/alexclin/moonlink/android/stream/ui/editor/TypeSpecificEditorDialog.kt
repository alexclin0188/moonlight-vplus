package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.alexclin.moonlink.android.stream.ui.common.CompactChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * 全屏类型专属属性设置对话框。
 * 根据 [element] 的 [ElementType] 展示不同类型的属性：
 * - 十字键/组合键/键盘摇杆：方向值 ↑↓←→
 * - 摇杆类：中值 + 灵敏度
 * - 可移动按键：模式 + 灵敏度 + 触控板模式
 * - 简化信息：模板文本编辑 + 字号 + 恢复默认
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeSpecificEditorDialog(
    element: EditorElement,
    allElements: List<EditorElement> = emptyList(),
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
) {
    // 本地可编辑状态
    var mode by remember(element.elementId) { mutableStateOf(element.mode.toString()) }
    var sense by remember(element.elementId) { mutableStateOf(element.sense.toString()) }
    var middleValue by remember(element.elementId) { mutableStateOf(element.middleValue) }
    var upValue by remember(element.elementId) { mutableStateOf(element.upValue) }
    var downValue by remember(element.elementId) { mutableStateOf(element.downValue) }
    var leftValue by remember(element.elementId) { mutableStateOf(element.leftValue) }
    var rightValue by remember(element.elementId) { mutableStateOf(element.rightValue) }
    var flag1 by remember(element.elementId) { mutableStateOf(element.flag1.toString()) }
    var extraAttributesJson by remember(element.elementId) { mutableStateOf(element.extraAttributesJson) }

    var showKeyPicker by remember { mutableStateOf(false) }
    var directionPickerTarget by remember { mutableStateOf<String?>(null) }

    // 构建更新后的元素
    fun buildUpdated(): EditorElement {
        return element.copy(
            mode = mode.toIntOrNull() ?: element.mode,
            sense = sense.toIntOrNull()?.coerceIn(0, 500) ?: element.sense,
            middleValue = middleValue,
            upValue = upValue,
            downValue = downValue,
            leftValue = leftValue,
            rightValue = rightValue,
            flag1 = flag1.toIntOrNull() ?: element.flag1,
            extraAttributesJson = extraAttributesJson,
        )
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .width(312.dp)
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
                // ── 标题行（含取消/保存按钮） ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${element.type.displayName}属性设置",
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

                // ── 类型专属属性区域 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {

                    // 方向值（十字键/组合键/键盘摇杆）
                    val showDirections = element.type in listOf(
                        ElementType.DIGITAL_PAD,
                        ElementType.DIGITAL_COMBINE_BUTTON,
                        ElementType.DIGITAL_STICK,
                        ElementType.INVISIBLE_DIGITAL_STICK,
                    )
                    if (showDirections) {
                        Text("方向键值", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DirectionValueField("↑", upValue,
                                onClick = { directionPickerTarget = "up" })
                            DirectionValueField("↓", downValue,
                                onClick = { directionPickerTarget = "down" })
                            DirectionValueField("←", leftValue,
                                onClick = { directionPickerTarget = "left" })
                            DirectionValueField("→", rightValue,
                                onClick = { directionPickerTarget = "right" })
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // 摇杆中值 + 灵敏度（Slider）
                    if (element.type in listOf(
                            ElementType.ANALOG_STICK,
                            ElementType.DIGITAL_STICK,
                            ElementType.INVISIBLE_ANALOG_STICK,
                            ElementType.INVISIBLE_DIGITAL_STICK,
                        )) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // 中值（与方向键同一样式：文本在上，输入框在下，宽度64dp）
                            Column(modifier = Modifier.width(64.dp)) {
                                Text("中值", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SmallTextField(value = middleValue,
                                    onValueChange = { middleValue = it },
                                    modifier = Modifier.fillMaxWidth())
                            }
                            // 灵敏度 Slider（占据三个方向键输入框宽度 = 192dp）
                            Column(
                                modifier = Modifier.width(192.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
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
                        Spacer(Modifier.height(4.dp))
                    }

                    // 可移动按键模式 + 触控板模式
                    if (element.type == ElementType.DIGITAL_MOVABLE_BUTTON) {
                        // 模式：Chip 单行布局 — "模式"标签 + "按钮"Chip + "摇杆"Chip
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("模式",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp))
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CompactChip(
                                    label = "按钮",
                                    selected = mode == "0",
                                    onClick = { mode = "0" },
                                    modifier = Modifier.weight(1f),
                                )
                                CompactChip(
                                    label = "摇杆",
                                    selected = mode == "1",
                                    onClick = { mode = "1" },
                                    modifier = Modifier.weight(1f),
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // 触控板模式开关
                        PropertyRow("触控板模式") {
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
                                        // 触控板模式下 mode 固定为 0
                                        if (it) mode = "0"
                                    } catch (_: Exception) {}
                                },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                }
            }
        }
    }

    // ── 键值选择器弹窗（方向值用） ──
    val directionTarget = directionPickerTarget
    if (directionTarget != null) {
        KeyValuePickerDialog(
            onSelect = { selectedValue, _ ->
                when (directionTarget) {
                    "up" -> upValue = selectedValue
                    "down" -> downValue = selectedValue
                    "left" -> leftValue = selectedValue
                    "right" -> rightValue = selectedValue
                }
                directionPickerTarget = null
            },
            onDismiss = { directionPickerTarget = null },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 辅助组件（与 EditorPropertiesPanel.kt 中相同，同包下 internal 化以便复用）
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 将属性值文字展示在 Slider 上方正中央的组件。
 * 用于滚轮面板属性设置对话框中的统一 Slider 展示效果。
 */
@Composable
private fun SliderWithValueAbove(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
    }
}

/** 带标签的行布局 */
@Composable
internal fun PropertyRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp))
        content()
    }
}

/** 小型文字输入框 */
@Composable
internal fun SmallTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
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
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 小型整数输入框 */
@Composable
internal fun SmallIntField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // 本地显示值 + 拒绝键：非法字符被拦截后强制 BasicTextField 重建回退显示
    var displayValue by remember { mutableStateOf(value) }
    var rejectKey by remember { mutableStateOf(0) }
    LaunchedEffect(value) { displayValue = value }

    key(rejectKey) {
        BasicTextField(
            value = displayValue,
            onValueChange = { newVal ->
                val isValid = newVal.isEmpty() || newVal.matches(Regex("-?\\d*"))
                if (isValid) {
                    displayValue = newVal
                    onValueChange(newVal)
                } else {
                    rejectKey++
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 可点击的方向值字段（↑↓←→），点击后打开键值选择器 */
@Composable
internal fun DirectionValueField(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val keyLabel = getKeyLabelByValue(value)
    Column(modifier = Modifier.width(64.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (value.isNotEmpty()) (keyLabel ?: value) else "-",
                style = TextStyle(
                    color = if (value.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  紧凑型颜色选择控件
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 紧凑型颜色选择控件：色块预览 + HEX 输入。
 * @param color 当前颜色值（ARGB int，-1 表示使用默认）
 * @param defaultColor 默认颜色（ARGB int）
 * @param onColorChange 颜色变更回调（返回 ARGB int）
 */
@Composable
private fun CompactColorSwatch(
    color: Int,
    defaultColor: Int,
    onColorChange: (Int) -> Unit,
) {
    var hexText by remember { mutableStateOf("") }
    val effectiveColor = if (color == -1) defaultColor else color
    val displayHex = hexText.ifBlank { colorToHexStr(effectiveColor) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 色块预览
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(androidx.compose.ui.graphics.Color(effectiveColor))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
        )

        // HEX 文本输入
        BasicTextField(
            value = displayHex,
            onValueChange = { newVal ->
                val cleaned = newVal.filter { it in "0123456789ABCDEFabcdef" }.take(8)
                hexText = cleaned.uppercase()
                if (cleaned.length == 6 || cleaned.length == 8) {
                    val parsed = parseHexColorStr("#$cleaned")
                    if (parsed != null) onColorChange(parsed)
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )

        // 重置按钮
        TextButton(
            onClick = { onColorChange(-1); hexText = "" },
            modifier = Modifier.height(30.dp),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text("重置", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

/** ARGB int → #RRGGBB 或 #AARRGGBB */
private fun colorToHexStr(color: Int): String {
    val a = (color shr 24) and 0xFF
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return if (a == 0xFF) {
        "#%02X%02X%02X".format(r, g, b)
    } else {
        "#%02X%02X%02X%02X".format(a, r, g, b)
    }
}

/** #RRGGBB 或 #AARRGGBB → ARGB int */
private fun parseHexColorStr(hex: String): Int? {
    val clean = hex.removePrefix("#").trim()
    if (clean.isEmpty()) return null
    return try {
        when (clean.length) {
            6 -> (0xFF000000 or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}
