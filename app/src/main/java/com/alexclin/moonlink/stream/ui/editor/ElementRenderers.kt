package com.alexclin.moonlink.stream.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ════════════════════════════════════════════════════════════════════════════
//  常量和颜色工具
// ════════════════════════════════════════════════════════════════════════════

/** DigitalPad 背景八边形的缩进比例（左右/上下） */
private const val PAD_OCTAGON_INSET = 0.33f
/** DigitalPad 中心空白区尺寸比例 */
private const val PAD_CENTER_SIZE = 0.28f
/** DigitalPad 方向臂宽度比例 */
private const val PAD_ARM_SIZE = 0.33f
/** 摇杆头半径占外圈比例 */
private const val STICK_HEAD_RATIO = 0.2f
/** WheelPad 内圈半径默认比例 */
private const val WHEEL_INNER_RATIO = 30f / 100f

/** 将 ARGB int 转为 Compose Color */
private fun Int.toColor(): Color = Color(this)

/** 将元素颜色 Int 与元素透明度复合，支持额外 alpha 系数 */
private fun EditorElement.withOpacity(colorInt: Int, alphaMul: Float = 1f): Color {
    val base = Color(colorInt)
    return base.copy(alpha = (base.alpha * alphaMul * opacity / 100f).coerceIn(0f, 1f))
}

/** 将 ARGB int 与元素透明度复合（用于 native Paint） */
private fun EditorElement.argbWithOpacity(colorInt: Int): Int {
    val a = ((colorInt shr 24) and 0xFF) * opacity / 100
    return (a.coerceIn(0, 255) shl 24) or (colorInt and 0x00FFFFFF)
}


// ════════════════════════════════════════════════════════════════════════════
//  DigitalCommonButton / DigitalSwitchButton / DigitalCombineButton / DigitalMovableButton
//  通用按键绘制
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制普通按键（旧 DigitalCommonButton 的 onElementDraw 等效）。
 *
 * 绘制顺序：填充背景 → 边框 → 居中文字。
 * 视觉效果与旧系统一致：圆角矩形容器 + 边框描边 + 文字居中。
 */
fun DrawScope.drawDigitalButton(
    element: EditorElement,
    isPressed: Boolean,
) {
    val rect = elementRect(element).let {
        // 为边框留出缩进
        val inset = element.thick / 2f
        Rect(it.left + inset, it.top + inset, it.right - inset, it.bottom - inset)
    }

    val bgColor = element.withOpacity(element.backgroundColor)
    val borderColor = element.withOpacity(if (isPressed) element.pressedColor else element.normalColor)
    val radius = element.radius.toFloat()

    // ── 背景填充 ──
    drawRoundRect(
        color = bgColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(radius, radius),
    )

    // ── 边框 ──
    drawRoundRect(
        color = borderColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = element.thick.toFloat()),
    )

    // ── 文字（text 为空时自动显示键值名） ──
    val displayText = element.text.ifBlank {
        getKeyLabelByValue(element.value) ?: ""
    }
    if (displayText.isNotBlank()) {
        val fontSizePx = (element.height * element.textSizePercent / 100f).coerceIn(8f, 120f)
        val textArgb = element.argbWithOpacity(if (isPressed) element.pressedTextColor else element.normalTextColor)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                val mPaint = android.graphics.Paint().apply {
                    color = textArgb
                    textSize = fontSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                val metrics = mPaint.fontMetrics
                val baseline = element.centralY - (metrics.top + metrics.bottom) / 2f
                drawText(displayText, element.centralX.toFloat(), baseline, mPaint)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DigitalPad — 方向键（十字键）
// ════════════════════════════════════════════════════════════════════════════

/** DigitalPad 方向位掩码，与旧代码一致 */
private const val DPAD_LEFT = 1
private const val DPAD_UP = 2
private const val DPAD_RIGHT = 4
private const val DPAD_DOWN = 8

/**
 * 绘制数字方向键（十字键）。
 *
 * 旧 [DigitalPad.onElementDraw] 的 Compose 等效。
 * 绘制八边形背景 + 四个方向矩形 + 对角线连接线。
 *
 * @param element 元素数据
 * @param activeDirections 当前按下的方向位掩码（0=无方向）
 */
fun DrawScope.drawDigitalPad(
    element: EditorElement,
    activeDirections: Int = 0,
) {
    val rect = elementRect(element)
    val w = rect.width
    val h = rect.height
    val thick = element.thick.toFloat()
    val margin = 5f
    val correctedBorder = thick + margin

    val bgColor = element.withOpacity(element.backgroundColor)
    val normalColor = element.withOpacity(element.normalColor)
    val pressedColor = element.withOpacity(element.pressedColor)

    // ── 八边形背景 ──
    val octInset = PAD_OCTAGON_INSET
    val bgPath = Path().apply {
        moveTo(w * octInset, correctedBorder)
        lineTo(w * (1 - octInset), correctedBorder)
        lineTo(w - correctedBorder, h * octInset)
        lineTo(w - correctedBorder, h * (1 - octInset))
        lineTo(w * (1 - octInset), h - correctedBorder)
        lineTo(w * octInset, h - correctedBorder)
        lineTo(correctedBorder, h * (1 - octInset))
        lineTo(correctedBorder, h * octInset)
        close()
    }
    drawPath(bgPath, color = bgColor)

    // ── 中心空白区矩形 ──
    val centerSize = PAD_CENTER_SIZE
    val centerOffset = (1 - centerSize) / 2
    val centerColor = if (activeDirections == 0) normalColor
        else element.withOpacity(element.normalColor, 0.3f)
    drawRect(
        color = centerColor,
        topLeft = Offset(rect.left + w * centerOffset, rect.top + h * centerOffset),
        size = Size(w * centerSize, h * centerSize),
        style = Stroke(width = thick),
    )

    val arm = PAD_ARM_SIZE
    val armOffset = 1 - arm
    // ── 左方向 ──
    val leftIsActive = (activeDirections and DPAD_LEFT) != 0
    drawRect(
        color = if (leftIsActive) pressedColor else normalColor,
        topLeft = Offset(rect.left + correctedBorder, rect.top + h * arm),
        size = Size(w * arm - correctedBorder, h * arm),
        style = Stroke(width = thick),
    )

    // ── 上方向 ──
    val upIsActive = (activeDirections and DPAD_UP) != 0
    drawRect(
        color = if (upIsActive) pressedColor else normalColor,
        topLeft = Offset(rect.left + w * arm, rect.top + correctedBorder),
        size = Size(w * arm, h * arm - correctedBorder),
        style = Stroke(width = thick),
    )

    // ── 右方向 ──
    val rightIsActive = (activeDirections and DPAD_RIGHT) != 0
    drawRect(
        color = if (rightIsActive) pressedColor else normalColor,
        topLeft = Offset(rect.left + w * armOffset, rect.top + h * arm),
        size = Size(w * (1 - armOffset) - correctedBorder, h * arm),
        style = Stroke(width = thick),
    )

    // ── 下方向 ──
    val downIsActive = (activeDirections and DPAD_DOWN) != 0
    drawRect(
        color = if (downIsActive) pressedColor else normalColor,
        topLeft = Offset(rect.left + w * arm, rect.top + h * armOffset),
        size = Size(w * arm, h * (1 - armOffset) - correctedBorder),
        style = Stroke(width = thick),
    )

    // ── 对角线连接线 ──
    // 左上
    val luColor = if (leftIsActive && upIsActive) pressedColor else normalColor
    drawLine(luColor,
        Offset(rect.left + correctedBorder, rect.top + h * arm),
        Offset(rect.left + w * arm, rect.top + correctedBorder), thick)

    // 右上
    val ruColor = if (upIsActive && rightIsActive) pressedColor else normalColor
    drawLine(ruColor,
        Offset(rect.left + w * armOffset, rect.top + correctedBorder),
        Offset(rect.left + w - correctedBorder, rect.top + h * arm), thick)

    // 右下
    val rdColor = if (rightIsActive && downIsActive) pressedColor else normalColor
    drawLine(rdColor,
        Offset(rect.left + w - correctedBorder, rect.top + h * armOffset),
        Offset(rect.left + w * armOffset, rect.top + h - correctedBorder), thick)

    // 左下
    val ldColor = if (downIsActive && leftIsActive) pressedColor else normalColor
    drawLine(ldColor,
        Offset(rect.left + w * arm, rect.top + h - correctedBorder),
        Offset(rect.left + correctedBorder, rect.top + h * armOffset), thick)
}

// ════════════════════════════════════════════════════════════════════════════
//  AnalogStick / DigitalStick — 摇杆
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制摇杆（AnalogStick / DigitalStick）。
 *
 * 绘制三层同心圆：外圈 → 内圈（死区）→ 摇杆头。
 * 摇杆头根据 [stickOffsetX/Y] 移动。
 */
fun DrawScope.drawAnalogStick(
    element: EditorElement,
    stickOffsetX: Float = 0f,
    stickOffsetY: Float = 0f,
) {
    val rect = elementRect(element)
    val cx = rect.center.x
    val cy = rect.center.y
    val outerRadius = min(rect.width, rect.height) / 2f - element.thick
    val innerRadiusPercent = element.sense.coerceIn(0, 100)
    val deadZoneRadius = outerRadius * innerRadiusPercent / 100f
    val stickRadius = outerRadius * STICK_HEAD_RATIO

    val bgColor = element.withOpacity(element.backgroundColor)
    val normalColor = element.withOpacity(element.normalColor)
    val pressedColor = element.withOpacity(element.pressedColor)

    // ── 外圈背景填充 ──
    drawCircle(bgColor, outerRadius, Offset(cx, cy))

    // ── 外圈边框 ──
    drawCircle(normalColor, outerRadius, Offset(cx, cy),
        style = Stroke(width = element.thick.toFloat()))

    // ── 死区圆 ──
    drawCircle(normalColor, deadZoneRadius, Offset(cx, cy),
        style = Stroke(width = element.thick.toFloat() * 0.5f))

    // ── 摇杆头 ──
    val stickCx = cx + stickOffsetX * (outerRadius - stickRadius)
    val stickCy = cy + stickOffsetY * (outerRadius - stickRadius)
    drawCircle(pressedColor, stickRadius, Offset(stickCx, stickCy))
}

// ════════════════════════════════════════════════════════════════════════════
//  GroupButton — 组按键
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制组按键。视觉效果类似 [drawDigitalButton] 但外观更清晰。
 *
 * 额外绘制一个小的组标记图标在右下角，以区别于普通按钮。
 */
fun DrawScope.drawGroupButton(
    element: EditorElement,
    isPressed: Boolean,
) {
    // 先画普通按钮外观
    drawDigitalButton(element, isPressed)

    val rect = elementRect(element)

    // ── 组标记：右下角小三角 ──
    val markSize = min(rect.width, rect.height) * 0.15f
    val markPath = Path().apply {
        moveTo(rect.right - markSize, rect.bottom)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.right, rect.bottom - markSize)
        close()
    }
    drawPath(markPath, color = element.withOpacity(0xAAFFFFFF.toInt()))
}

// ════════════════════════════════════════════════════════════════════════════
//  SimplifyPerformance — 性能信息面板
// ════════════════════════════════════════════════════════════════════════════

/** SimplifyPerformance 默认模板（与旧 CROWN 兼容） */
const val DEFAULT_PERF_TEMPLATE = "  带宽: ##带宽##    主机/网络/解码: ##主机延时## / ##网络延时## / ##解码时间##    帧率: ##帧率##    丢帧: ##丢帧率##    渲染:##渲染延迟##    时间:HH:MM:SS"

/** ##占位符## 正则 */
private val PERF_PLACEHOLDER_REGEX = Regex("##(.*?)##")

/**
 * 构建性能数据映射 —— 从 [PerformanceInfo] 生成占位符到实际值的映射。
 * 格式与旧 CROWN 的 performanceAttrs Map 兼容。
 */
internal fun buildPerformanceAttrs(perfInfo: com.limelight.binding.video.PerformanceInfo?): Map<String, String> {
    if (perfInfo == null) return emptyMap()
    val attrs = mutableMapOf<String, String>()
    attrs["带宽"] = perfInfo.bandWidth ?: "N/A"
    attrs["网络延时"] = "${perfInfo.rttInfo.toInt()}"
    attrs["主机延时"] = if (perfInfo.framesWithHostProcessingLatency > 0)
        "%.1f".format(perfInfo.aveHostProcessingLatency) else "N/A"
    attrs["解码时间"] = "%.2f".format(perfInfo.decodeTimeMs)
    attrs["帧率"] = "Rx %.0f / Rd %.0f".format(perfInfo.receivedFps, perfInfo.renderedFps)
    attrs["丢帧率"] = "%.2f%%".format(perfInfo.lostFrameRate)
    attrs["渲染延迟"] = "%.2f".format(perfInfo.renderingLatencyMs)
    attrs["总延迟"] = "%.0f".format(perfInfo.totalTimeMs)
    return attrs
}

/**
 * 解析模板字符串，替换 ##占位符## 为实际值，替换 HH/MM/SS 为当前时间。
 */
internal fun parsePerfTemplate(template: String, attrs: Map<String, String>): String {
    // 1. 替换 ##占位符##
    val afterPlaceholders = PERF_PLACEHOLDER_REGEX.replace(template) { match ->
        attrs[match.groupValues[1]] ?: match.value
    }
    // 2. 替换时钟占位符
    val now = java.util.Calendar.getInstance()
    return afterPlaceholders
        .replace("HH", java.text.SimpleDateFormat("HH", java.util.Locale.getDefault()).format(now.time))
        .replace("MM", java.text.SimpleDateFormat("mm", java.util.Locale.getDefault()).format(now.time))
        .replace("SS", java.text.SimpleDateFormat("ss", java.util.Locale.getDefault()).format(now.time))
}

/**
 * 绘制性能信息面板。支持 ##占位符## 模板替换。
 *
 * 当 [performanceAttrs] 非空时渲染实时数据，否则渲染原始模板（编辑器预览）。
 * 背景自适应文本尺寸，支持多行文本（\n 换行）。
 */
fun DrawScope.drawSimplifyPerformance(
    element: EditorElement,
    performanceAttrs: Map<String, String>? = null,
) {
    val rect = elementRect(element)

    // ── 1. 解析模板 ──
    val template = element.text.ifBlank { DEFAULT_PERF_TEMPLATE }
    val renderedText = if (performanceAttrs != null) {
        parsePerfTemplate(template, performanceAttrs)
    } else {
        // 无实时数据时：显示占位效果，但保留模板可见性
        template
    }

    val elementOpacity = element.opacity / 100f
    val fontSizePx = element.thick.toFloat().coerceIn(10f, 50f)

    // ── 2. 计算文本实际尺寸 ──
    val lines = renderedText.split("\n")
    val tmpPaint = android.graphics.Paint().apply {
        textSize = fontSizePx
        isAntiAlias = true
    }
    val fm = tmpPaint.fontMetrics
    val lineHeight = fm.bottom - fm.top
    val maxLineWidth = lines.maxOfOrNull { tmpPaint.measureText(it) } ?: 0f

    val hPadding = 10f
    val vPadding = 8f
    val contentWidth = maxLineWidth + hPadding * 2
    val contentHeight = lineHeight * lines.size + vPadding * 2

    // ── 3. 绘制半透明背景 ──
    val bgColor = Color(0x88000000).copy(alpha = 0x88 / 255f * elementOpacity)
    drawRoundRect(
        color = bgColor,
        topLeft = rect.topLeft,
        size = Size(contentWidth, contentHeight),
        cornerRadius = CornerRadius(element.radius.toFloat(), element.radius.toFloat()),
    )

    // ── 4. 简约边框 ──
    drawRoundRect(
        color = Color(0xFF888888).copy(alpha = 1f * elementOpacity),
        topLeft = rect.topLeft,
        size = Size(contentWidth, contentHeight),
        cornerRadius = CornerRadius(element.radius.toFloat(), element.radius.toFloat()),
        style = Stroke(width = 1f),
    )

    // ── 5. 逐行绘制文本 ──
    val textArgb = element.argbWithOpacity(element.normalColor)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = textArgb
                textSize = fontSizePx
                isAntiAlias = true
            }
            val baseline = rect.top + vPadding - fm.top
            for ((i, line) in lines.withIndex()) {
                drawText(line, rect.left + hPadding, baseline + i * lineHeight, paint)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  WheelPad — 滚轮面板（完整版，参照旧 Crown）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制滚轮面板。
 *
 * 将圆环按 [element.mode] 等分为 N 个扇区，每个扇区对应一个键值。
 * [activeIndex] 表示当前选中的扇区索引（-1 表示无选中）。
 *
 * 段值从 [element.value] 解析（逗号分隔，支持 `|` 后缀命名）。
 */
fun DrawScope.drawWheelPad(
    element: EditorElement,
    activeIndex: Int = -1,
) {
    val rect = elementRect(element)
    val cx = rect.center.x
    val cy = rect.center.y
    val outerRadius = min(rect.width, rect.height) / 2f - element.thick
    val innerRatio = (element.sense.coerceIn(10, 90) / 100f)
    val innerRadius = outerRadius * innerRatio
    val segmentCount = element.mode.coerceIn(2, 24)
    val sweepAngle = 360f / segmentCount

    val bgColor = element.withOpacity(element.backgroundColor)
    val normalColor = element.withOpacity(element.normalColor)
    val pressedColor = element.withOpacity(element.pressedColor)

    // 解析段值列表（逗号分隔，支持 "k51|名称" 格式）
    val segments = element.value.split(",").filter { it.isNotBlank() }

    // ── 绘制各段扇形 ──
    for (i in 0 until segmentCount) {
        val startAngle = (i * sweepAngle) - (sweepAngle / 2) - 90
        val isActive = i == activeIndex
        val fillColor = if (isActive) pressedColor else bgColor

        // 扇形填充
        drawArc(
            color = fillColor,
            topLeft = Offset(cx - outerRadius, cy - outerRadius),
            size = Size(outerRadius * 2, outerRadius * 2),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = true,
        )
    }

    // ── 绘制各段分割线（内圆到外圆） ──
    val dividerColor = element.withOpacity(element.normalColor, 0.4f)
    for (i in 0 until segmentCount) {
        val angle = Math.toRadians((i * sweepAngle - sweepAngle / 2 - 90).toDouble())
        val sx = cx + (innerRadius * cos(angle)).toFloat()
        val sy = cy + (innerRadius * sin(angle)).toFloat()
        val ex = cx + (outerRadius * cos(angle)).toFloat()
        val ey = cy + (outerRadius * sin(angle)).toFloat()
        drawLine(dividerColor, Offset(sx, sy), Offset(ex, ey), element.thick.toFloat() * 0.7f)
    }

    // ── 中心圆 ──
    drawCircle(Color(0xFF000000), innerRadius, Offset(cx, cy))

    // ── 外圈/内圈边框 ──
    drawCircle(normalColor, outerRadius, Offset(cx, cy), style = Stroke(width = element.thick.toFloat()))
    drawCircle(element.withOpacity(element.normalColor, 0.5f), innerRadius, Offset(cx, cy),
        style = Stroke(width = element.thick.toFloat() * 0.5f))

    // ── 各段文字 ──
    val ringThickness = outerRadius - innerRadius
    val textSizePx = (ringThickness * 0.35f).coerceIn(8f, 48f)
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = textSizePx
        }
        val textRadius = innerRadius + ringThickness / 2f
        for (i in 0 until segmentCount) {
            val angle = (i * sweepAngle) - 90
            val rad = Math.toRadians(angle.toDouble())
            val tx = cx + (textRadius * cos(rad)).toFloat()
            val ty = cy + (textRadius * sin(rad)).toFloat()

            val segValue = segments.getOrElse(i) { "" }
            val displayName = getKeyLabelByValue(segValue) ?: segValue
            paint.color = element.argbWithOpacity(if (i == activeIndex) element.pressedTextColor else element.normalTextColor)
            paint.isFakeBoldText = i == activeIndex
            val metrics = paint.fontMetrics
            val baseline = ty - (metrics.top + metrics.bottom) / 2f
            nativeCanvas.drawText(if (displayName.isNotEmpty()) displayName else "-", tx, baseline, paint)
        }
    }

    // ── 选中时中心显示键值名 ──
    if (activeIndex in 0 until segmentCount) {
        val segValue = segments.getOrElse(activeIndex) { "" }
        val centerText = getKeyLabelByValue(segValue) ?: segValue
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = element.argbWithOpacity(0xFFFFFFFF.toInt())
                    textSize = (innerRadius * 1.2f).coerceIn(12f, 60f)
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                val metrics = paint.fontMetrics
                val baseline = cy - (metrics.top + metrics.bottom) / 2f
                drawText(centerText, cx, baseline, paint)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  未知类型 — 占位
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制未知类型的占位元素。
 */
fun DrawScope.drawUnknownElement(
    element: EditorElement,
) {
    val rect = elementRect(element)
    // 红色虚线边框
    drawRect(
        color = Color(0xFFFF4444),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
        ),
    )
    // 问号标签
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(128, 255, 68, 68)
                textSize = 14f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawText("? ${element.type.value}",
                rect.center.x,
                rect.center.y + 5f,
                paint)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  触控板（MovableButton 的触控板模式辅助绘制）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制触控板模式指示器（画一条十字参考线 + 方向箭头）。
 */
fun DrawScope.drawTrackpadIndicator(
    element: EditorElement,
) {
    val rect = elementRect(element)
    val cx = rect.center.x
    val cy = rect.center.y

    val elementOpacity = element.opacity / 100f

    // 半透明蓝色覆盖
    drawRect(Color(0x220066FF).copy(alpha = (0x22 / 255f * elementOpacity).coerceIn(0f, 1f)), rect.topLeft, rect.size)

    // 十字线
    drawLine(Color(0x440066FF).copy(alpha = (0x44 / 255f * elementOpacity).coerceIn(0f, 1f)), Offset(cx, rect.top), Offset(cx, rect.bottom), 1f)
    drawLine(Color(0x440066FF).copy(alpha = (0x44 / 255f * elementOpacity).coerceIn(0f, 1f)), Offset(rect.left, cy), Offset(rect.right, cy), 1f)
}
