package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonDefaults.ContentPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 属性编辑面板 —— 6 列网格布局（匹配设计文档）。
 *
 * Row 1: 按键名 | X坐标 | Y坐标 | 不透明度 | 滑块(0%-100%) | 删除按钮
 * Row 2: 键值  | W宽(px) | H高(px) | 文字大小 | 滑块(10%-150%) | 复制按钮
 * Row 3: 图层 | 粗细(px) | 圆角(px) | 按键类型 | 颜色自定义 | 专属属性设置 | 保存
 *
 * @param atTop 面板是否在屏幕顶部（影响圆角方向）
 */
@Composable
fun EditorPropertiesPanel(
    element: EditorElement,
    atTop: Boolean = false,
    onSave: (EditorElement) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenColorEditor: (EditorElement) -> Unit,
    onOpenTypeSpecificEditor: (EditorElement) -> Unit,
    /** 属性变化时实时回调，用于触发画布更新预览 */
    onElementChanged: ((EditorElement) -> Unit)? = null,
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
    // 如果高宽一致且圆角=宽度/2，则自动识别为圆形
    var isCircle by remember(element.elementId) {
        mutableStateOf(element.isCircle || (element.height == element.width && element.radius == element.width / 2))
    }

    // 十字键/摇杆：按键名、按键值禁用，圆形强制关闭
    val isPadOrStick = element.type in listOf(
        ElementType.DIGITAL_PAD,
        ElementType.ANALOG_STICK,
        ElementType.DIGITAL_STICK,
        ElementType.INVISIBLE_ANALOG_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK,
    )
    LaunchedEffect(element.elementId) {
        if (isPadOrStick) isCircle = false
    }

    // ── 实时同步画布拖拽/缩放产生的变化 ──
    LaunchedEffect(element.centralX) { centralX = element.centralX.toString() }
    LaunchedEffect(element.centralY) { centralY = element.centralY.toString() }
    LaunchedEffect(element.width) { width = element.width.toString() }
    LaunchedEffect(element.height) { height = element.height.toString() }

    var showKeyPicker by remember { mutableStateOf(false) }

    /** 获取当前宽度整数值 */
    fun currentWidth(): Int = width.toIntOrNull()?.coerceIn(10, 5000) ?: element.width

    fun snapshot() = element.copy(
        text = text,
        value = value,
        centralX = centralX.toIntOrNull() ?: element.centralX,
        centralY = centralY.toIntOrNull() ?: element.centralY,
        width = currentWidth(),
        // 圆形模式：高度=宽度，圆角=宽度/2
        height = if (isCircle) currentWidth() else (height.toIntOrNull()?.coerceIn(10, 5000) ?: element.height),
        layer = layer.toIntOrNull()?.coerceIn(0, 999) ?: element.layer,
        radius = if (isCircle) (currentWidth() / 2).coerceIn(0, 500) else (radius.toIntOrNull()?.coerceIn(0, 500) ?: element.radius),
        thick = thick.toIntOrNull()?.coerceIn(0, 100) ?: element.thick,
        opacity = opacity.roundToInt().coerceIn(0, 100),
        textSizePercent = textSizePercent.roundToInt().coerceIn(10, 150),
        isCircle = isCircle,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        shadowElevation = 8.dp,
        shape = if (atTop) {
            RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        },
    ) {
        val paddingValues = PaddingValues(6.dp,3.dp,6.dp,3.dp)
        val rowHeight = 36.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            // ── 每列各自独立（所有 3 行对齐）：
            //   Lbl1(1f) + Input1(1f) + Lbl2(1f) + Input2(1f) + Lbl3(1f) + Input3(1f) + Lbl4(1f) + Content(3f) + Btn
            //   Lbl1: 按键名 / 键值 / 按键类型
            //   Input1: 输入框 / 选择框 / 类型值
            //   Lbl2: X坐标 / W宽 / 粗细
            //   Input2: X值 / W值 / 粗细值
            //   Lbl3: Y坐标 / H高 / 圆角
            //   Input3: Y值 / H值 / 圆角值
            //   Lbl4: 不透明度 / 文字大小 / 图层
            //   Content: R1/R2 滑块, R3 图层输入+颜色+专属按钮
            //   Btn: 保存 / 复制 / 删除

            // ═══════════════════════════════════════════════════════
            // Row 1: 按键名 | X坐标 | Y坐标 | 不透明度滑块 | 删除
            // ═══════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Lbl1: 按键名（右对齐）
                GridLabel("按键名", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input1: 输入框
                InlineTextField(value = text, onValueChange = { text = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp), enabled = !isPadOrStick)
                // Lbl1: 键值（右对齐）
                GridLabel("按键值", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input1: 选择框
                val keyLabel = getKeyLabelByValue(value) ?: value
                val keyAlpha = if (isPadOrStick) 0.38f else 1f
                Box(
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = keyAlpha))
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = keyAlpha), RoundedCornerShape(4.dp))
                        .clickable(enabled = !isPadOrStick) { showKeyPicker = true }
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (value.isNotEmpty()) keyLabel else "-",
                            style = TextStyle(fontSize = 9.sp,
                                color = (if (value.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = keyAlpha)),
                            maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown,
                            contentDescription = "选择键值",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = keyAlpha))
                    }
                }
                // Lbl2: 粗细（右对齐）
                GridLabel("边框粗细", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input2: 粗细值
                StepperIntField(value = thick, onValueChange = { thick = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp))

                TextButton(onClick = { onOpenColorEditor(snapshot()) },
                    modifier = Modifier.weight(1.5f).wrapContentHeight(),
                    contentPadding = ButtonDefaults.TextButtonContentPadding) {
                    Text("颜色自定义", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                // Lbl4: 不透明度（右对齐）
                GridLabel("不透明度", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Content: 滑块（占多格）
                Box(Modifier.weight(3f).padding(end = 4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${opacity.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 8.sp)
                        Slider(value = opacity, onValueChange = { opacity = it; onElementChanged?.invoke(snapshot()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.height(16.dp).fillMaxWidth())
                    }
                }
                // Btn: 保存（绿色）
                TextButton(onClick = { onSave(snapshot()) }, modifier = Modifier.wrapContentHeight(),
                    contentPadding = paddingValues) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("保存", style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                }
            }

            // ═══════════════════════════════════════════════════════
            // Row 2: 键值 | W宽 | H高 | 文字大小滑块 | 复制
            // ═══════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Lbl2: W宽（右对齐）
                GridLabel("按钮宽度", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input2: W值
                StepperIntField(value = width, onValueChange = { width = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp))
                // Lbl3: H高（右对齐）
                GridLabel("按钮高度", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input3: H值（圆形模式禁用，显示宽度值）
                StepperIntField(value = if (isCircle) width else height,
                    onValueChange = { height = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp),
                    enabled = !isCircle)
                // Lbl3: 圆角（右对齐）
                GridLabel("边框圆角", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input3: 圆角值
                StepperIntField(value = if (isCircle) (currentWidth() / 2).toString() else radius,
                    onValueChange = { radius = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp),
                    enabled = !isCircle)
                // 圆形开关 + 文字，整体可点击（十字键/摇杆时强制关闭且禁用）
                val circleEnabled = !isPadOrStick
                val circleAlpha = if (circleEnabled) 1f else 0.38f
                Row(
                    modifier = Modifier.weight(1.5f)
                        .alpha(circleAlpha)
                        .then(if (circleEnabled) Modifier.clickable {
                            isCircle = !isCircle
                            if (isCircle) {
                                val w = currentWidth()
                                height = w.toString()
                                radius = (w / 2).toString()
                            }
                            onElementChanged?.invoke(snapshot())
                        } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Switch(
                        checked = isCircle,
                        onCheckedChange = if (circleEnabled) { checked ->
                            isCircle = checked
                            if (checked) {
                                val w = currentWidth()
                                height = w.toString()
                                radius = (w / 2).toString()
                            }
                            onElementChanged?.invoke(snapshot())
                        } else null,
                        modifier = Modifier.wrapContentHeight().width(36.dp).scale(0.6f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    Text("圆形",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start)
                }

                // Lbl4: 文字大小（右对齐）
                GridLabel("文字大小", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Content: 滑块（占多格）
                Box(Modifier.weight(3f).padding(end = 4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${textSizePercent.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 8.sp)
                        Slider(value = textSizePercent,
                            onValueChange = { textSizePercent = it; onElementChanged?.invoke(snapshot()) },
                            valueRange = 10f..150f,
                            modifier = Modifier.height(16.dp).fillMaxWidth())
                    }
                }
                // Btn: 复制
                TextButton(onClick = onDuplicate, modifier = Modifier.wrapContentHeight(),
                    contentPadding = paddingValues) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("复制", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // ═══════════════════════════════════════════════════════
            // Row 3: 按键类型 | 粗细 | 圆角 | 图层+按钮 | 保存
            // ═══════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Lbl2: X坐标（右对齐）
                GridLabel("X坐标", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input2: X值
                StepperIntField(value = centralX, onValueChange = { centralX = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp))
                // Lbl3: Y坐标（右对齐）
                GridLabel("Y坐标", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input3: Y值
                StepperIntField(value = centralY, onValueChange = { centralY = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp))
                // Lbl1: 图层（右对齐）
                GridLabel("所在图层", Modifier.weight(0.8f), rightAlign = true)
                Spacer(Modifier.width(2.dp))
                // Input1: 图层值
                StepperIntField(value = layer, onValueChange = { layer = it; onElementChanged?.invoke(snapshot()) },
                    modifier = Modifier.weight(1.5f).padding(end = 2.dp))
                Spacer(Modifier.width(1.dp))
                Text(element.type.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1.5f).wrapContentHeight())
                Spacer(Modifier.width(1.dp))
                // Content: 圆形开关 + 类型值 + 颜色自定义 + 专属属性设置
                Row(Modifier.weight(3.8f), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(4.dp))
                    if (element.type.hasTypeSpecificProperties()) {
                        TextButton(onClick = { onOpenTypeSpecificEditor(snapshot()) },
                            modifier = Modifier.weight(1f).wrapContentHeight(),
                            contentPadding = ButtonDefaults.TextButtonContentPadding) {
                            Text("${element.type.displayName}属性设置", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                // Btn: 删除（红色）
                TextButton(onClick = onDelete, modifier = Modifier.wrapContentHeight(),
                    contentPadding = paddingValues) {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("删除", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
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
                onElementChanged?.invoke(snapshot())
            },
            onDismiss = { showKeyPicker = false },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 组件
// ═══════════════════════════════════════════════════════════════════════════════

/** 网格小标签 */
@Composable
private fun GridLabel(text: String, modifier: Modifier = Modifier, rightAlign: Boolean = false) {
    Text(text, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.sp,
        textAlign = if (rightAlign) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
        modifier = modifier
            .then(if (rightAlign) Modifier.fillMaxWidth() else Modifier)
            .padding(end = 1.dp))
}

/** 判断该元素类型是否有专属属性可设置 */
private fun ElementType.hasTypeSpecificProperties(): Boolean = when (this) {
    ElementType.DIGITAL_COMMON_BUTTON,
    ElementType.DIGITAL_SWITCH_BUTTON,
    ElementType.UNKNOWN -> false
    else -> true
}

/** 微型整数输入框 */
@Composable
private fun MiniIntField(
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
            fontSize = 10.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 3.dp),
    )
}

/** 带 +/- 步进按钮的整数输入框 */
@Composable
private fun StepperIntField(
    value: String,
    onValueChange: (String) -> Unit,
    step: Int = 1,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 减量按钮
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = contentAlpha), RoundedCornerShape(3.dp))
                .then(if (enabled) Modifier.clickable {
                    val intVal = value.toIntOrNull() ?: return@clickable
                    onValueChange((intVal - step).toString())
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text("-",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                fontSize = 10.sp)
        }

        Spacer(Modifier.width(2.dp))

        MiniIntField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).alpha(contentAlpha),
        )

        Spacer(Modifier.width(2.dp))

        // 增量按钮
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = contentAlpha), RoundedCornerShape(3.dp))
                .then(if (enabled) Modifier.clickable {
                    val intVal = value.toIntOrNull() ?: return@clickable
                    onValueChange((intVal + step).toString())
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text("+",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                fontSize = 10.sp)
        }
    }
}

/** 行内文字输入框 */
@Composable
private fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    BasicTextField(
        value = value,
        onValueChange = { if (enabled) onValueChange(it) },
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            fontSize = 11.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)),
        keyboardOptions = KeyboardOptions.Default,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = contentAlpha), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  颜色工具（被 ColorEditorDialog 复用）
// ═══════════════════════════════════════════════════════════════════════════════

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

internal fun parseHexColor(hex: String): Int? {
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
