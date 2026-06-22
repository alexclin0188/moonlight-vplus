package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ════════════════════════════════════════════════════════════════════════════
//  颜色项描述
// ════════════════════════════════════════════════════════════════════════════

private data class ColorItem(val label: String, val key: String)

private val COLOR_ITEMS = listOf(
    ColorItem("正常色", "normal"),
    ColorItem("按下色", "pressed"),
    ColorItem("背景色", "bg"),
    ColorItem("文字色", "normalText"),
    ColorItem("按下文字色", "pressedText"),
)

/**
 * 全屏颜色自定义对话框。
 *
 * 展示 5 种颜色项（正常色、按下色、背景色、文字色、按下文字色），
 * 集成 HSV 颜色选择器（色相/饱和度/亮度 + RGB 滑条 + 十六进制输入）。
 * 点击颜色项可切换当前编辑的目标颜色。
 *
 * 宽高自适应：最大宽度 90%，最大高度 95%。
 */
@Composable
fun ColorEditorDialog(
    element: EditorElement,
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
    onElementChanged: ((EditorElement) -> Unit)? = null,
) {
    // ── 5 个颜色的十六进制状态 ──
    val hexMap = remember(element.elementId) {
        mutableMapOf(
            "normal" to colorToHex(element.normalColor),
            "pressed" to colorToHex(element.pressedColor),
            "bg" to colorToHex(element.backgroundColor),
            "normalText" to colorToHex(element.normalTextColor),
            "pressedText" to colorToHex(element.pressedTextColor),
        )
    }

    // ── 当前编辑的颜色索引（0-4） ──
    var selectedIndex by remember { mutableIntStateOf(0) }

    // ── 颜色选择器的 HSV 状态（针对选中颜色） ──
    val selectedKey = COLOR_ITEMS[selectedIndex].key
    val selectedHex = hexMap[selectedKey] ?: "#FFFFFFFF"
    val selectedArgb = parseHexColor(selectedHex) ?: 0xFFFFFFFF.toInt()

    // HSV from selected color
    val initHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(selectedArgb, initHsv)
    var hue by remember(selectedIndex, selectedArgb) { mutableFloatStateOf(initHsv[0]) }
    var sat by remember(selectedIndex, selectedArgb) { mutableFloatStateOf(initHsv[1]) }
    var value by remember(selectedIndex, selectedArgb) { mutableFloatStateOf(initHsv[2]) }
    var alpha by remember(selectedIndex, selectedArgb) { mutableIntStateOf(android.graphics.Color.alpha(selectedArgb)) }

    // ── RGB sliders 状态 ──
    var red by remember(selectedIndex, selectedArgb) { mutableIntStateOf(android.graphics.Color.red(selectedArgb)) }
    var green by remember(selectedIndex, selectedArgb) { mutableIntStateOf(android.graphics.Color.green(selectedArgb)) }
    var blue by remember(selectedIndex, selectedArgb) { mutableIntStateOf(android.graphics.Color.blue(selectedArgb)) }
    var rText by remember(selectedIndex) { mutableStateOf(red.toString()) }
    var gText by remember(selectedIndex) { mutableStateOf(green.toString()) }
    var bText by remember(selectedIndex) { mutableStateOf(blue.toString()) }
    var aText by remember(selectedIndex) { mutableStateOf(alpha.toString()) }

    // ── 从 hexMap 构建更新后的 EditorElement ──
    fun buildUpdated(): EditorElement = element.copy(
        normalColor = parseHexColor(hexMap["normal"] ?: "") ?: element.normalColor,
        pressedColor = parseHexColor(hexMap["pressed"] ?: "") ?: element.pressedColor,
        backgroundColor = parseHexColor(hexMap["bg"] ?: "") ?: element.backgroundColor,
        normalTextColor = parseHexColor(hexMap["normalText"] ?: "") ?: element.normalTextColor,
        pressedTextColor = parseHexColor(hexMap["pressedText"] ?: "") ?: element.pressedTextColor,
    )

    /** 解析选中颜色的当前 ARGB */
    fun currentColor(): Int = android.graphics.Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))

    /** 同步当前 picker/滑条状态到 hexMap 并触发预览 */
    fun syncSelectedColorToHexMap() {
        val argb = currentColor()
        red = android.graphics.Color.red(argb)
        green = android.graphics.Color.green(argb)
        blue = android.graphics.Color.blue(argb)
        rText = red.toString()
        gText = green.toString()
        bText = blue.toString()
        aText = alpha.toString()
        val hex = colorToHex(argb)
        hexMap[selectedKey] = hex
        onElementChanged?.invoke(buildUpdated())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val maxHeightDp = (screenHeightDp * 0.95f).coerceAtMost(800f)

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .heightIn(max = maxHeightDp.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                // ── 标题行 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("颜色自定义", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider()

                // ── 可滚动主体 ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // ═══ 5 个颜色项 ═══
                    COLOR_ITEMS.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        val hex = hexMap[item.key] ?: "#FFFFFFFF"
                        val parsed = parseHexColor(hex)
                        ColorItemRow(
                            label = item.label,
                            hex = hex,
                            parsedColor = parsed,
                            isSelected = isSelected,
                            onSelect = {
                                selectedIndex = index
                                // 刷新 RGB 文字
                                val argb = parseHexColor(hexMap[COLOR_ITEMS[index].key] ?: "#FFFFFFFF")
                                    ?: 0xFFFFFFFF.toInt()
                                red = android.graphics.Color.red(argb)
                                green = android.graphics.Color.green(argb)
                                blue = android.graphics.Color.blue(argb)
                                alpha = android.graphics.Color.alpha(argb)
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            },
                            onHexChange = { newHex ->
                                hexMap[item.key] = newHex
                                if (isSelected) {
                                    val argb = parseHexColor(newHex) ?: 0xFFFFFFFF.toInt()
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb, hsv)
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                    alpha = android.graphics.Color.alpha(argb)
                                    red = android.graphics.Color.red(argb)
                                    green = android.graphics.Color.green(argb)
                                    blue = android.graphics.Color.blue(argb)
                                    rText = red.toString()
                                    gText = green.toString()
                                    bText = blue.toString()
                                    aText = alpha.toString()
                                }
                                onElementChanged?.invoke(buildUpdated())
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ═══ 颜色选择器 ═══
                    Text("颜色选择器",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)

                    Spacer(Modifier.height(8.dp))

                    // HSV 面板：Sat/Val 矩形 + Hue 条
                    Row(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Sat/Val 二维选择器
                        SatValPicker(
                            hue = hue,
                            sat = sat,
                            value = value,
                            onSatValChanged = { s, v ->
                                sat = s; value = v
                                syncSelectedColorToHexMap()
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp),
                        )

                        // Hue 竖直条
                        HueBar(
                            hue = hue,
                            onHueChanged = { h ->
                                hue = h
                                syncSelectedColorToHexMap()
                            },
                            modifier = Modifier.width(28.dp).fillMaxHeight(),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // ═══ RGB + Alpha 滑条 ═══
                    RgbSliderRow("R", red, 255, { red = it.toInt().coerceIn(0, 255); rText = red.toString(); syncSelectedColorToHexMap() },
                        rText, { rText = it })
                    RgbSliderRow("G", green, 255, { green = it.toInt().coerceIn(0, 255); gText = green.toString(); syncSelectedColorToHexMap() },
                        gText, { gText = it })
                    RgbSliderRow("B", blue, 255, { blue = it.toInt().coerceIn(0, 255); bText = blue.toString(); syncSelectedColorToHexMap() },
                        bText, { bText = it })
                    RgbSliderRow("A", alpha, 255, { alpha = it.toInt().coerceIn(0, 255); aText = alpha.toString(); syncSelectedColorToHexMap() },
                        aText, { aText = it })

                    Spacer(Modifier.height(8.dp))

                    // ═══ 色块预览 + Hex 输入 ═══
                    val currentArgb = currentColor()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 色块
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(currentArgb))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        BasicTextField(
                            value = hexMap[selectedKey]?.removePrefix("#") ?: "",
                            onValueChange = { h ->
                                val clean = h.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                                val newHex = if (clean.isEmpty()) "" else "#$clean"
                                hexMap[selectedKey] = newHex
                                // 同步 picker 状态
                                val parsed = parseHexColor(newHex)
                                if (parsed != null) {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(parsed, hsv)
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                    alpha = android.graphics.Color.alpha(parsed)
                                    red = android.graphics.Color.red(parsed)
                                    green = android.graphics.Color.green(parsed)
                                    blue = android.graphics.Color.blue(parsed)
                                    rText = red.toString()
                                    gText = green.toString()
                                    bText = blue.toString()
                                    aText = alpha.toString()
                                }
                                onElementChanged?.invoke(buildUpdated())
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }

                HorizontalDivider()

                // ── 底部按钮 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onSave(buildUpdated())
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  颜色项行（可选择 + 色块 + 十六进制输入）
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ColorItemRow(
    label: String,
    hex: String,
    parsedColor: Int?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onHexChange: (String) -> Unit,
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { onSelect() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选中指示圈
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    if (isSelected) 0.dp else 1.5.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    RoundedCornerShape(50),
                ),
        )
        Spacer(Modifier.width(6.dp))

        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp))

        // 色块预览
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (parsedColor != null) Color(parsedColor) else Color.Transparent)
                .border(
                    0.5.dp,
                    if (parsedColor != null) Color.Transparent else Color.Red,
                    RoundedCornerShape(4.dp),
                ),
        )

        Spacer(Modifier.width(8.dp))

        // Hex 输入
        Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        BasicTextField(
            value = hex.removePrefix("#"),
            onValueChange = { h ->
                val clean = h.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                onHexChange(if (clean.isEmpty()) "" else "#$clean")
            },
            singleLine = true,
            textStyle = TextStyle(
                color = if (parsedColor != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier
                .width(100.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  HSV 颜色选择器组件
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 饱和度-亮度二维选择器（矩形 Canvas）。
 * 横轴 = 饱和度 (0→1)，纵轴 = 亮度 (1→0)。
 */
@Composable
private fun SatValPicker(
    hue: Float,
    sat: Float,
    value: Float,
    onSatValChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    onSatValChanged(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        isDragging = true
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onSatValChanged(s, v)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        // 纯色（当前色相，全饱和全亮）
        val pureColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

        // 从左到右：白 → 纯色
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, pureColor),
                startX = 0f, endX = w,
            ),
            size = size,
        )
        // 从下到上：透明 → 黑色（叠加在渐变上）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f, endY = h,
            ),
            size = size,
        )

        // 选择器圆
        val selX = sat * w
        val selY = (1f - value) * h
        val selColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
        drawCircle(selColor, 8f, Offset(selX, selY))
        drawCircle(Color.White, 8f, Offset(selX, selY), style = Stroke(width = 2f))
    }
}

/**
 * 色相竖直选择条。
 * 从上到下：红→黄→绿→青→蓝→紫→红。
 */
@Composable
private fun HueBar(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val h = (offset.y / size.height * 360f).coerceIn(0f, 359.9f)
                    onHueChanged(h)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val h = (change.position.y / size.height * 360f).coerceIn(0f, 359.9f)
                        onHueChanged(h)
                    },
                )
            },
    ) {
        val h = size.height
        if (h <= 0) return@Canvas

        val hueColors = listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
        drawRect(
            brush = Brush.verticalGradient(hueColors, startY = 0f, endY = h),
            size = size,
        )

        // 选择器指示器
        val selY = (hue / 360f) * h
        val indicatorColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        drawCircle(indicatorColor, 8f, Offset(size.width / 2f, selY))
        drawCircle(Color.White, 8f, Offset(size.width / 2f, selY), style = Stroke(width = 2f))
    }
}

/**
 * RGB/Alpha 滑条行：标签 + Slider + 数值输入。
 */
@Composable
private fun RgbSliderRow(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Float) -> Unit,
    textValue: String,
    onTextChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        BasicTextField(
            value = textValue,
            onValueChange = { newVal ->
                if (newVal.isEmpty() || newVal.all { it.isDigit() }) {
                    onTextChange(newVal)
                    val intVal = newVal.toIntOrNull()
                    if (intVal != null && intVal in 0..max) {
                        onValueChange(intVal.toFloat())
                    }
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(36.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
