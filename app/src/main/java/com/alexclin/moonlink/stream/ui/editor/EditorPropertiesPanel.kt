package com.alexclin.moonlink.stream.ui.editor

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexclin.moonlink.stream.ui.editor.KeyValuePickerDialog
import com.alexclin.moonlink.stream.ui.editor.getKeyLabelByValue
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════════════════════════════
//  属性编辑面板
// ════════════════════════════════════════════════════════════════════════════

/**
 * 选中元素的属性编辑面板。
 *
 * 分组展示所有可编辑属性，支持：
 * - 文字/数值输入
 * - 滑块（透明度、文字大小比例）
 * - 颜色预览 + 十六进制输入
 * - 类型专属属性（方向值、模式、灵敏度等）
 *
 * @param element 当前选中的元素（快照，编辑期间不跟随外部变化）
 * @param onSave 保存变更
 * @param onCancel 放弃变更
 */
/** 属性编辑面板的可折叠区段 */
private enum class Section {
    BASIC,
    POSITION,
    APPEARANCE,
    COLORS,
    TYPE_SPECIFIC,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorPropertiesPanel(
    element: EditorElement,
    onSave: (EditorElement) -> Unit,
    onCancel: () -> Unit,
) {
    // ── 本地可编辑状态 ──
    var text by remember(element.elementId) { mutableStateOf(element.text) }
    var value by remember(element.elementId) { mutableStateOf(element.value) }
    var centralX by remember(element.elementId) { mutableStateOf(element.centralX.toString()) }
    var centralY by remember(element.elementId) { mutableStateOf(element.centralY.toString()) }
    var width by remember(element.elementId) { mutableStateOf(element.width.toString()) }
    var height by remember(element.elementId) { mutableStateOf(element.height.toString()) }
    var layer by remember(element.elementId) { mutableStateOf(element.layer.toString()) }
    var radius by remember(element.elementId) { mutableStateOf(element.radius.toString()) }
    var thick by remember(element.elementId) { mutableStateOf(element.thick.toString()) }
    var opacity by remember(element.elementId) { mutableStateOf(element.opacity.toFloat()) }
    var textSizePercent by remember(element.elementId) { mutableStateOf(element.textSizePercent.toFloat()) }

    // 颜色（十六进制）
    var normalColorHex by remember(element.elementId) { mutableStateOf(colorToHex(element.normalColor)) }
    var pressedColorHex by remember(element.elementId) { mutableStateOf(colorToHex(element.pressedColor)) }
    var bgColorHex by remember(element.elementId) { mutableStateOf(colorToHex(element.backgroundColor)) }
    var normalTextColorHex by remember(element.elementId) { mutableStateOf(colorToHex(element.normalTextColor)) }
    var pressedTextColorHex by remember(element.elementId) { mutableStateOf(colorToHex(element.pressedTextColor)) }

    // 类型专属
    var mode by remember(element.elementId) { mutableStateOf(element.mode.toString()) }
    var sense by remember(element.elementId) { mutableStateOf(element.sense.toString()) }
    var middleValue by remember(element.elementId) { mutableStateOf(element.middleValue) }
    var upValue by remember(element.elementId) { mutableStateOf(element.upValue) }
    var downValue by remember(element.elementId) { mutableStateOf(element.downValue) }
    var leftValue by remember(element.elementId) { mutableStateOf(element.leftValue) }
    var rightValue by remember(element.elementId) { mutableStateOf(element.rightValue) }
    var flag1 by remember(element.elementId) { mutableStateOf(element.flag1.toString()) }

    var collapsedSections by remember(element.elementId) {
        mutableStateOf(setOf<Section>())
    }
    var showKeyPicker by remember { mutableStateOf(false) }
    var directionPickerTarget by remember { mutableStateOf<String?>(null) }

    // ── 构建保存后的 Element（委托到顶层纯函数） ──
    fun buildUpdatedElement(): EditorElement = buildUpdatedElement(
        element = element,
        text = text,
        value = value,
        centralX = centralX,
        centralY = centralY,
        width = width,
        height = height,
        layer = layer,
        radius = radius,
        thick = thick,
        opacity = opacity,
        textSizePercent = textSizePercent,
        normalColorHex = normalColorHex,
        pressedColorHex = pressedColorHex,
        bgColorHex = bgColorHex,
        normalTextColorHex = normalTextColorHex,
        pressedTextColorHex = pressedTextColorHex,
        mode = mode,
        sense = sense,
        middleValue = middleValue,
        upValue = upValue,
        downValue = downValue,
        leftValue = leftValue,
        rightValue = rightValue,
        flag1 = flag1,
    )

    // ── 界面 ──
    Box {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── 标题行 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("属性编辑", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("取消", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { onSave(buildUpdatedElement()) }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("保存", style = MaterialTheme.typography.labelSmall)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ═══ 基本属性 ═══
                SectionHeader("基本", Section.BASIC, collapsedSections, { collapsedSections = it })
                if (Section.BASIC !in collapsedSections) {
                    PropertyRow("文字") {
                        SmallTextField(value = text, onValueChange = { text = it },
                            modifier = Modifier.weight(1f))
                    }
                    PropertyRow("键值") {
                        val keyLabel = getKeyLabelByValue(value) ?: value
                        Row(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                .clickable { showKeyPicker = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (value.isNotEmpty()) "$keyLabel  ($value)" else "点击选择",
                                style = TextStyle(
                                    color = if (value.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text("选择", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    PropertyRow("图层") {
                        SmallIntField(value = layer, onValueChange = { layer = it },
                            modifier = Modifier.width(80.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ═══ 位置 & 尺寸 ═══
                SectionHeader("位置 & 尺寸", Section.POSITION, collapsedSections, { collapsedSections = it })
                if (Section.POSITION !in collapsedSections) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IntFieldWithLabel("X", centralX, { centralX = it })
                        IntFieldWithLabel("Y", centralY, { centralY = it })
                        IntFieldWithLabel("W", width, { width = it })
                        IntFieldWithLabel("H", height, { height = it })
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ═══ 外观 ═══
                SectionHeader("外观", Section.APPEARANCE, collapsedSections, { collapsedSections = it })
                if (Section.APPEARANCE !in collapsedSections) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IntFieldWithLabel("圆角", radius, { radius = it }, suffix = "px")
                        IntFieldWithLabel("边框", thick, { thick = it }, suffix = "px")
                    }
                    Spacer(Modifier.height(4.dp))

                    // 透明度
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("透明度", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(64.dp))
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${opacity.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp))
                    }

                    // 文字大小比例
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("文字大小", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(64.dp))
                        Slider(
                            value = textSizePercent,
                            onValueChange = { textSizePercent = it },
                            valueRange = 10f..150f,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${textSizePercent.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ═══ 颜色 ═══
                SectionHeader("颜色", Section.COLORS, collapsedSections, { collapsedSections = it })
                if (Section.COLORS !in collapsedSections) {
                    ColorRow("正常色", normalColorHex, { normalColorHex = it },
                        parseHexColor(normalColorHex))
                    ColorRow("按下色", pressedColorHex, { pressedColorHex = it },
                        parseHexColor(pressedColorHex))
                    ColorRow("背景色", bgColorHex, { bgColorHex = it },
                        parseHexColor(bgColorHex))
                    ColorRow("文字色", normalTextColorHex, { normalTextColorHex = it },
                        parseHexColor(normalTextColorHex))
                    ColorRow("按下文字色", pressedTextColorHex, { pressedTextColorHex = it },
                        parseHexColor(pressedTextColorHex))
                    Spacer(Modifier.height(4.dp))
                }

                // ═══ 类型专属属性 ═══
                val typeSpecificName = when (element.type) {
                    ElementType.DIGITAL_PAD -> "方向键设置"
                    ElementType.ANALOG_STICK, ElementType.DIGITAL_STICK,
                    ElementType.INVISIBLE_ANALOG_STICK, ElementType.INVISIBLE_DIGITAL_STICK -> "摇杆设置"
                    ElementType.DIGITAL_COMBINE_BUTTON -> "组合键设置"
                    ElementType.DIGITAL_MOVABLE_BUTTON -> "可移动按键设置"
                    ElementType.GROUP_BUTTON -> "组按键设置"
                    else -> null
                }
                if (typeSpecificName != null) {
                    SectionHeader(typeSpecificName, Section.TYPE_SPECIFIC, collapsedSections, { collapsedSections = it })
                    if (Section.TYPE_SPECIFIC !in collapsedSections) {
                        // 方向值（十字键/摇杆/组合键通用）
                        val showDirections = element.type in listOf(
                            ElementType.DIGITAL_PAD,
                            ElementType.DIGITAL_COMBINE_BUTTON,
                            ElementType.DIGITAL_STICK,
                            ElementType.INVISIBLE_DIGITAL_STICK,
                        )
                        if (showDirections) {
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
                        }

                        // 摇杆中值
                        if (element.type in listOf(
                                ElementType.ANALOG_STICK,
                                ElementType.DIGITAL_STICK,
                                ElementType.INVISIBLE_ANALOG_STICK,
                                ElementType.INVISIBLE_DIGITAL_STICK,
                            )) {
                            PropertyRow("中值") {
                                SmallTextField(value = middleValue, onValueChange = { middleValue = it },
                                    modifier = Modifier.weight(1f))
                            }
                            PropertyRow("灵敏度") {
                                SmallIntField(value = sense, onValueChange = { sense = it },
                                    modifier = Modifier.width(80.dp))
                            }
                        }

                        // 可移动按键模式
                        if (element.type == ElementType.DIGITAL_MOVABLE_BUTTON) {
                            PropertyRow("模式 (0=按钮,1=摇杆)") {
                                SmallIntField(value = mode, onValueChange = { mode = it },
                                    modifier = Modifier.width(80.dp))
                            }
                            PropertyRow("灵敏度") {
                                SmallIntField(value = sense, onValueChange = { sense = it },
                                    modifier = Modifier.width(80.dp))
                            }
                        }

                        // 组按键标志
                        if (element.type == ElementType.GROUP_BUTTON) {
                            PropertyRow("隐藏标志") {
                                SmallIntField(value = flag1, onValueChange = { flag1 = it },
                                    modifier = Modifier.width(80.dp))
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                    }
                }

                // 底部间距
                Spacer(Modifier.height(16.dp))
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

        // ── 方向值选择器弹窗 ──
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
}

// ════════════════════════════════════════════════════════════════════════════
//  辅助组件
// ════════════════════════════════════════════════════════════════════════════

/** 可折叠的分组标题 */
@Composable
private fun SectionHeader(
    title: String,
    section: Section,
    collapsed: Set<Section>,
    onToggle: (Set<Section>) -> Unit,
) {
    val isCollapsed = section in collapsed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onToggle(if (isCollapsed) collapsed - section else collapsed + section)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f).height(1.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** 带标签的行布局 */
@Composable
private fun PropertyRow(
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
private fun SmallTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions.Default,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 小型整数输入框 */
@Composable
private fun SmallIntField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = { newVal ->
            if (newVal.isEmpty() || newVal.all { it.isDigit() || it == '-' }) {
                onValueChange(newVal)
            }
        },
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 带标签的整数输入（用于 FlowRow） */
@Composable
private fun IntFieldWithLabel(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String = "",
) {
    Column(modifier = Modifier.width(72.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallIntField(value = value, onValueChange = onValueChange,
                modifier = Modifier.weight(1f))
            if (suffix.isNotEmpty()) {
                Text(suffix, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

/** 可点击的方向值字段（↑↓←→），点击后打开键值选择器 */
@Composable
private fun DirectionValueField(
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

/** 颜色行：色块预览 + 十六进制输入 */
@Composable
private fun ColorRow(
    label: String,
    hex: String,
    onHexChange: (String) -> Unit,
    parsedColor: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp))

        // 色块预览
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (parsedColor != null) Color(parsedColor) else Color.Transparent)
                .border(
                    1.dp,
                    if (parsedColor != null) Color.Transparent else Color.Red,
                    RoundedCornerShape(4.dp),
                ),
        )

        Spacer(Modifier.width(8.dp))

        // 带 # 前缀的十六进制输入
        Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        BasicTextField(
            value = hex.removePrefix("#"),
            onValueChange = { h ->
                val clean = h.uppercase().filter { it in "0123456789ABCDEF" || it in "abcdef" }
                    .take(8)
                onHexChange(if (clean.isEmpty()) "" else "#$clean")
            },
            singleLine = true,
            textStyle = TextStyle(
                color = if (parsedColor != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier
                .width(100.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  构建更新后的 Element（表单 → 数据模型）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 将属性编辑面板的表单状态合并到原始 [element] 上，生成最终保存的 [EditorElement]。
 *
 * 所有字符串字段优先尝试解析为数字，解析失败时回退到原始值。
 * 此函数为纯函数，无副作用，便于单元测试。
 */
internal fun buildUpdatedElement(
    element: EditorElement,
    text: String,
    value: String,
    centralX: String,
    centralY: String,
    width: String,
    height: String,
    layer: String,
    radius: String,
    thick: String,
    opacity: Float,
    textSizePercent: Float,
    normalColorHex: String,
    pressedColorHex: String,
    bgColorHex: String,
    normalTextColorHex: String,
    pressedTextColorHex: String,
    mode: String,
    sense: String,
    middleValue: String,
    upValue: String,
    downValue: String,
    leftValue: String,
    rightValue: String,
    flag1: String,
): EditorElement {
    return element.copy(
        text = text,
        value = value,
        centralX = centralX.toIntOrNull() ?: element.centralX,
        centralY = centralY.toIntOrNull() ?: element.centralY,
        width = width.toIntOrNull()?.coerceIn(10, 5000) ?: element.width,
        height = height.toIntOrNull()?.coerceIn(10, 5000) ?: element.height,
        layer = layer.toIntOrNull()?.coerceIn(0, 999) ?: element.layer,
        radius = radius.toIntOrNull()?.coerceIn(0, 500) ?: element.radius,
        thick = thick.toIntOrNull()?.coerceIn(0, 100) ?: element.thick,
        opacity = opacity.roundToInt().coerceIn(0, 100),
        textSizePercent = textSizePercent.roundToInt().coerceIn(10, 150),
        normalColor = parseHexColor(normalColorHex) ?: element.normalColor,
        pressedColor = parseHexColor(pressedColorHex) ?: element.pressedColor,
        backgroundColor = parseHexColor(bgColorHex) ?: element.backgroundColor,
        normalTextColor = parseHexColor(normalTextColorHex) ?: element.normalTextColor,
        pressedTextColor = parseHexColor(pressedTextColorHex) ?: element.pressedTextColor,
        mode = mode.toIntOrNull() ?: element.mode,
        sense = sense.toIntOrNull()?.coerceIn(0, 500) ?: element.sense,
        middleValue = middleValue,
        upValue = upValue,
        downValue = downValue,
        leftValue = leftValue,
        rightValue = rightValue,
        flag1 = flag1.toIntOrNull() ?: element.flag1,
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  颜色工具
// ════════════════════════════════════════════════════════════════════════════

/**
 * 将 ARGB int 转换为 #AARRGGBB 格式的十六进制字符串。
 * 如果 alpha 为 FF，则省略 alpha → #RRGGBB。
 */
internal fun colorToHex(color: Int): String {
    val alpha = (color shr 24) and 0xFF
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    return if (alpha == 0xFF) {
        "#%02X%02X%02X".format(red, green, blue)
    } else {
        "#%02X%02X%02X%02X".format(alpha, red, green, blue)
    }
}

/**
 * 将十六进制字符串解析为 ARGB int。
 * 支持格式：RRGGBB、AARRGGBB、#RRGGBB、#AARRGGBB。
 */
internal fun parseHexColor(hex: String): Int? {
    val clean = hex.removePrefix("#").trim()
    if (clean.isEmpty()) return null
    return try {
        when (clean.length) {
            6 -> { // RRGGBB → FFrrggbb
            (0xFF000000 or clean.toLong(16)).toInt()
            }
            8 -> { // AARRGGBB
            clean.toLong(16).toInt()
            }
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}
