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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
//  颜色项描述（公开给调用方）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 颜色项描述。
 * @param label 显示名称（如"边框颜色"）
 * @param key   唯一标识（如 "border" / "text"）
 * @param currentColor 当前颜色 ARGB 值
 */
data class ColorPickerItem(
    val label: String,
    val key: String,
    val currentColor: Int,
)

// ════════════════════════════════════════════════════════════════════════════
//  工具函数（colorToHex / parseHexColor 定义在 EditorPropertiesPanel.kt 中）
// ════════════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  颜色项行（可选择 + 色块 + 十六进制输入）
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ColorItemRow(
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

// ════════════════════════════════════════════════════════════════════════════
//  HSV 颜色选择器组件
// ════════════════════════════════════════════════════════════════════════════

/**
 * 饱和度-亮度二维选择器（矩形 Canvas）。
 * 横轴 = 饱和度 (0→1)，纵轴 = 亮度 (1→0)。
 */
@Composable
internal fun SatValPicker(
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
internal fun HueBar(
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

// ════════════════════════════════════════════════════════════════════════════
//  核心对话框
// ════════════════════════════════════════════════════════════════════════════

/**
 * 通用颜色选择器对话框。
 *
 * 接收颜色项列表，动态按 [items] 数量展示对应的颜色项行，
 * 每个都配有 HSV 颜色选择器（色相/饱和度/亮度 + RGB 滑条 + 十六进制输入）。
 * 用户确认后通过 [onSave] 返回 [(key, 所选颜色), ...] 列表。
 *
 * @param title  对话框标题
 * @param items  颜色项列表（动态决定展示几个颜色）
 * @param onSave 保存回调（返回 key→颜色的映射列表）
 * @param onDismiss 关闭回调
 */
@Composable
fun ColorPickerDialog(
    title: String,
    items: List<ColorPickerItem>,
    onSave: (List<Pair<String, Int>>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return

    // ── 每个颜色的十六进制状态，按 key 索引 ──
    val hexMap = remember(items) {
        mutableMapOf<String, String>().apply {
            for (item in items) {
                put(item.key, colorToHex(item.currentColor))
            }
        }
    }

    // ── 当前编辑的颜色索引 ──
    var selectedIndex by remember { mutableIntStateOf(0) }

    // ── 颜色选择器的 HSV 状态（针对选中颜色） ──
    val selectedItem = items.getOrElse(selectedIndex) { items.first() }
    val selectedKey = selectedItem.key
    val selectedHex = hexMap[selectedKey] ?: colorToHex(selectedItem.currentColor)
    val selectedArgb = parseHexColor(selectedHex) ?: selectedItem.currentColor

    val initHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(selectedArgb, initHsv)
    var hue by remember(selectedIndex, selectedArgb) { mutableFloatStateOf(initHsv[0]) }
    var sat by remember(selectedIndex, selectedArgb) { mutableFloatStateOf(initHsv[1]) }
    var value by remember(selectedIndex, selectedArgb) { mutableFloatStateOf(initHsv[2]) }
    var alpha by remember(selectedIndex, selectedArgb) { mutableIntStateOf(android.graphics.Color.alpha(selectedArgb)) }

    /** 解析选中颜色的当前 ARGB */
    fun currentColor(): Int = android.graphics.Color.HSVToColor(alpha, floatArrayOf(hue, sat, value))

    /** 同步当前 picker 状态到 hexMap */
    fun syncSelectedColorToHexMap() {
        val argb = currentColor()
        val hex = colorToHex(argb)
        hexMap[selectedKey] = hex
    }

    /** 构建返回结果列表 */
    fun buildResult(): List<Pair<String, Int>> {
        return items.map { item ->
            val hex = hexMap[item.key] ?: colorToHex(item.currentColor)
            item.key to (parseHexColor(hex) ?: item.currentColor)
        }
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
                // ── 标题行（末尾放置取消/保存按钮） ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        onSave(buildResult())
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider()

                // ── 三列主体：SatVal 拾色区 | Hue 竖向条 | 颜色项+预览+Hex ──
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // ═══ 第一列：Sat/Val 拾色区（占大部分宽度） ═══
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

                    // ═══ 第二列：竖向 Hue 选色条 ═══
                    HueBar(
                        hue = hue,
                        onHueChanged = { h ->
                            hue = h
                            syncSelectedColorToHexMap()
                        },
                        modifier = Modifier.width(24.dp).fillMaxHeight(),
                    )

                    Spacer(Modifier.width(10.dp))

                    // ═══ 第三列：颜色项列表 + 色块预览 + Hex 值调整 ═══
                    Column(
                        modifier = Modifier.width(180.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // ── 颜色项列表（可滚动） ──
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            items.forEachIndexed { index, item ->
                                val isSelected = index == selectedIndex
                                val hex = hexMap[item.key] ?: colorToHex(item.currentColor)
                                val parsed = parseHexColor(hex)
                                ColorItemRow(
                                    label = item.label,
                                    hex = hex,
                                    parsedColor = parsed,
                                    isSelected = isSelected,
                                    onSelect = {
                                        selectedIndex = index
                                        val argb = parseHexColor(hexMap[items[index].key] ?: colorToHex(items[index].currentColor))
                                            ?: items[index].currentColor
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
                                        }
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // ── 色块预览小方块 ──
                        val currentArgb = currentColor()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(currentArgb))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .align(Alignment.CenterHorizontally),
                        )

                        Spacer(Modifier.height(6.dp))

                        // ── Hex 值调整区域：[-] #AABBCC [+] ──
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            // 减量按钮
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                    .clickable {
                                        // 减小亮度（V 值减 0.05）
                                        value = (value - 0.05f).coerceIn(0f, 1f)
                                        syncSelectedColorToHexMap()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("-",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }

                            Spacer(Modifier.width(4.dp))

                            // Hex 值显示
                            Text("#", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            BasicTextField(
                                value = hexMap[selectedKey]?.removePrefix("#") ?: "",
                                onValueChange = { h ->
                                    val clean = h.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                                    val newHex = if (clean.isEmpty()) "" else "#$clean"
                                    hexMap[selectedKey] = newHex
                                    val parsed = parseHexColor(newHex)
                                    if (parsed != null) {
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(parsed, hsv)
                                        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                        alpha = android.graphics.Color.alpha(parsed)
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                modifier = Modifier
                                    .width(90.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                            )

                            Spacer(Modifier.width(4.dp))

                            // 增量按钮
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                                    .clickable {
                                        // 增加亮度（V 值加 0.05）
                                        value = (value + 0.05f).coerceIn(0f, 1f)
                                        syncSelectedColorToHexMap()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }


            }
        }
    }
}
