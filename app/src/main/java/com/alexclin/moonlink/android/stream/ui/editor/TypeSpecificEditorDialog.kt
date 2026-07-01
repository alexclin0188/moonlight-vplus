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
 * - 组按键：子按键管理入口 + 子元素可见性 + 隐藏标志 + 可拖拽/永久独立开关
 * - 轮盘按键：分段数 + 分段编辑入口
 * - 简化信息：模板文本编辑 + 字号 + 恢复默认
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeSpecificEditorDialog(
    element: EditorElement,
    allElements: List<EditorElement> = emptyList(),
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
    onManageChildren: (() -> Unit)? = null,
    onManageSegments: (() -> Unit)? = null,
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
    // WheelPad 弹窗模式专用
    var popupText by remember(element.elementId, element.text) { mutableStateOf(element.text) }

    var showKeyPicker by remember { mutableStateOf(false) }
    var directionPickerTarget by remember { mutableStateOf<String?>(null) }

    // 构建更新后的元素
    fun buildUpdated(): EditorElement {
        val base = element.copy(
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
        return if (element.type == ElementType.WHEEL_PAD) {
            base.copy(text = popupText)
        } else {
            base
        }
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    // WheelPad 对话框宽度翻倍，其他类型维持原宽度
    val isWheelPad = element.type == ElementType.WHEEL_PAD
    val dialogWidth = if (isWheelPad) 624.dp else 312.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .width(dialogWidth)
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

                    // 组按键
                    if (element.type == ElementType.GROUP_BUTTON) {
                        // 第一行：子元素可见 + 隐藏本按钮
                        Row(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("子元素可见", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(64.dp))
                                val visible = (sense.toIntOrNull() ?: 1) == 1
                                var checked by remember(element.elementId) { mutableStateOf(visible) }
                                Switch(
                                    checked = checked,
                                    modifier = Modifier.scale(0.8f),
                                    onCheckedChange = {
                                        checked = it
                                        sense = if (it) "1" else "0"
                                    },
                                )
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("隐藏本按钮", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(64.dp))
                                val hidden = (flag1.toIntOrNull() ?: 0) == 1
                                var checked by remember(element.elementId) { mutableStateOf(hidden) }
                                Switch(
                                    checked = checked,
                                    modifier = Modifier.scale(0.8f),
                                    onCheckedChange = {
                                        checked = it
                                        flag1 = if (it) "1" else "0"
                                    },
                                )
                            }
                        }

                        // 第二行：可拖拽 + 永久独立
                        Row(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("可拖拽", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(64.dp))
                                val movable = try {
                                    JSONObject(extraAttributesJson).optBoolean("movableInNormalMode", false)
                                } catch (_: Exception) { false }
                                var checked by remember(element.elementId) { mutableStateOf(movable) }
                                Switch(
                                    checked = checked,
                                    modifier = Modifier.scale(0.8f),
                                    onCheckedChange = {
                                        checked = it
                                        try {
                                            val jo = JSONObject(extraAttributesJson)
                                            jo.put("movableInNormalMode", it)
                                            extraAttributesJson = jo.toString()
                                        } catch (_: Exception) {}
                                    },
                                )
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("永久独立", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(64.dp))
                                val independent = try {
                                    JSONObject(extraAttributesJson).optBoolean("isPermanentlyIndependent", false)
                                } catch (_: Exception) { false }
                                var checked by remember(element.elementId) { mutableStateOf(independent) }
                                Switch(
                                    checked = checked,
                                    modifier = Modifier.scale(0.8f),
                                    onCheckedChange = {
                                        checked = it
                                        try {
                                            val jo = JSONObject(extraAttributesJson)
                                            jo.put("isPermanentlyIndependent", it)
                                            extraAttributesJson = jo.toString()
                                        } catch (_: Exception) {}
                                    },
                                )
                            }
                        }

                        // 子按键管理（放在第二行下面）
                        val childCount = element.value
                            .split(",")
                            .mapNotNull { it.trim().toLongOrNull() }
                            .count { it != -1L }
                        PropertyRow("子按键") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (childCount > 0) "${childCount} 个" else "无",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (childCount > 0) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (onManageChildren != null) {
                                    TextButton(onClick = onManageChildren) {
                                        Text(
                                            if (childCount > 0) "管理" else "添加",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // 轮盘按键
                    if (element.type == ElementType.WHEEL_PAD) {
                        val isPopupNow = popupText.isNotBlank()

                        // ── 中心文字 / 屏幕居中弹出 / 预览组子元素 ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 中心文字：标签+输入框，填满剩余宽度
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("中心文字",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                SmallTextField(
                                    value = popupText,
                                    onValueChange = { popupText = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (isPopupNow) {
                                // 屏幕居中弹出：适应自身内容宽度
                                Row(
                                    modifier = Modifier.wrapContentWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("屏幕居中弹出",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    val checked = (flag1.toIntOrNull() ?: 1) == 1
                                    var sw by remember(element.elementId) { mutableStateOf(checked) }
                                    Switch(
                                        checked = sw,
                                        modifier = Modifier.wrapContentSize().scale(0.8f),
                                        onCheckedChange = {
                                            sw = it
                                            flag1 = if (it) "1" else "0"
                                        },
                                    )
                                }

                                // 预览组子元素：适应自身内容宽度
                                Row(
                                    modifier = Modifier,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("预览组子元素",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    val previewInit = try {
                                        JSONObject(extraAttributesJson).optBoolean("previewGroupChildren", true)
                                    } catch (_: Exception) { true }
                                    var preview by remember(element.elementId) { mutableStateOf(previewInit) }
                                    Switch(
                                        checked = preview,
                                        modifier = Modifier.wrapContentSize().scale(0.8f),
                                        onCheckedChange = {
                                            preview = it
                                            try {
                                                val jo = JSONObject(extraAttributesJson)
                                                jo.put("previewGroupChildren", it)
                                                extraAttributesJson = jo.toString()
                                            } catch (_: Exception) {}
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // 内圈半径 / 段文字颜色 JSON 读取
                        val segTextSizeInit = try {
                            JSONObject(extraAttributesJson).optInt("textSizePercent", 35)
                        } catch (_: Exception) { 35 }
                        val ctrTextSizeInit = try {
                            JSONObject(extraAttributesJson).optInt("centerTextSizePercent", 60)
                        } catch (_: Exception) { 60 }
                        val ntc = try {
                            JSONObject(extraAttributesJson).optInt("normalTextColor", -1)
                        } catch (_: Exception) { -1 }
                        val ctc = try {
                            JSONObject(extraAttributesJson).optInt("centerTextColor", -1)
                        } catch (_: Exception) { -1 }

                        // ── Row 1: 内圈半径 | 分段数 ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                SliderWithValueAbove(
                                    label = "内圈半径",
                                    value = (sense.toIntOrNull() ?: 30).toFloat(),
                                    onValueChange = { sense = it.roundToInt().toString() },
                                    valueRange = 10f..90f,
                                    formatValue = { "${it.roundToInt()}%" },
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                SliderWithValueAbove(
                                    label = "分段数",
                                    value = (mode.toIntOrNull() ?: 8).toFloat(),
                                    onValueChange = { mode = it.roundToInt().toString() },
                                    valueRange = 2f..24f,
                                    steps = 21,
                                    formatValue = { "${it.roundToInt()} 段" },
                                )
                            }
                        }

                        // ── Row 2: 段文字大小 | 中心文字大小 ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // ── 触发器文字大小（中心文字行下方） ──
                            if (isPopupNow) {
                                val triggerSizeInit = try {
                                    JSONObject(extraAttributesJson).optInt("triggerTextSizePercent", 40)
                                } catch (_: Exception) { 40 }
                                Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
                                    SliderWithValueAbove(
                                        label = "触发器文字大小",
                                        value = triggerSizeInit.toFloat(),
                                        onValueChange = {
                                            val v = it.roundToInt()
                                            try {
                                                val jo = JSONObject(extraAttributesJson)
                                                jo.put("triggerTextSizePercent", v)
                                                extraAttributesJson = jo.toString()
                                            } catch (_: Exception) {}
                                        },
                                        valueRange = 5f..150f,
                                        formatValue = { "${it.roundToInt()}%" },
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                SliderWithValueAbove(
                                    label = "段文字大小",
                                    value = segTextSizeInit.toFloat(),
                                    onValueChange = {
                                        val v = it.roundToInt()
                                        try {
                                            val jo = JSONObject(extraAttributesJson)
                                            jo.put("textSizePercent", v)
                                            extraAttributesJson = jo.toString()
                                        } catch (_: Exception) {}
                                    },
                                    valueRange = 10f..100f,
                                    formatValue = { "${it.roundToInt()}%" },
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                SliderWithValueAbove(
                                    label = "中心文字大小",
                                    value = ctrTextSizeInit.toFloat(),
                                    onValueChange = {
                                        val v = it.roundToInt()
                                        try {
                                            val jo = JSONObject(extraAttributesJson)
                                            jo.put("centerTextSizePercent", v)
                                            extraAttributesJson = jo.toString()
                                        } catch (_: Exception) {}
                                    },
                                    valueRange = 10f..150f,
                                    formatValue = { "${it.roundToInt()}%" },
                                )
                            }
                        }

                        // ── Row 3: 段文字颜色 | 中心文字颜色 ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("段文字颜色",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CompactColorSwatch(
                                    color = ntc,
                                    defaultColor = 0xFFFFFFFF.toInt(),
                                    onColorChange = { c ->
                                        try {
                                            val jo = JSONObject(extraAttributesJson)
                                            jo.put("normalTextColor", c)
                                            extraAttributesJson = jo.toString()
                                        } catch (_: Exception) {}
                                    },
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("中心文字颜色",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                CompactColorSwatch(
                                    color = ctc,
                                    defaultColor = 0xFFFFFFFF.toInt(),
                                    onColorChange = { c ->
                                        try {
                                            val jo = JSONObject(extraAttributesJson)
                                            jo.put("centerTextColor", c)
                                            extraAttributesJson = jo.toString()
                                        } catch (_: Exception) {}
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // ── 分段编辑按钮 ──
                        if (onManageSegments != null) {
                            PropertyRow("分段编辑") {
                                TextButton(onClick = onManageSegments) {
                                    Text("编辑分段", style = MaterialTheme.typography.labelSmall)
                                }
                            }
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
