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

    val bgColor = element.backgroundColor.toColor()
    val borderColor = (if (isPressed) element.pressedColor else element.normalColor).toColor()
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

    // ── 文字 ──
    if (element.text.isNotBlank()) {
        val fontSizePx = (element.height * element.textSizePercent / 100f).coerceIn(8f, 120f)
        val textArgb = if (isPressed) element.pressedTextColor else element.normalTextColor
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
                drawText(element.text, element.centralX.toFloat(), baseline, mPaint)
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

    val normalColor = element.normalColor.toColor()
    val pressedColor = element.pressedColor.toColor()
    val bgColor = element.backgroundColor.toColor()

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
    val centerColor = if (activeDirections == 0) normalColor else normalColor.copy(alpha = 0.3f)
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

    val bgColor = element.backgroundColor.toColor()
    val normalColor = element.normalColor.toColor()
    val pressedColor = element.pressedColor.toColor()

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
    drawPath(markPath, color = Color(0xAAFFFFFF))
}

// ════════════════════════════════════════════════════════════════════════════
//  SimplifyPerformance — 性能信息面板
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制性能信息面板。半透明背景 + 简洁文字。
 */
fun DrawScope.drawSimplifyPerformance(
    element: EditorElement,
) {
    val rect = elementRect(element)

    // 半透明背景
    drawRect(
        color = Color(0x88000000),
        topLeft = rect.topLeft,
        size = rect.size,
    )

    // 简约边框
    drawRect(
        color = Color(0xFF888888),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 1f),
    )

    // 文字（FPS / 延迟占位，实际由运行时代入）
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 12f
                isAntiAlias = true
            }
            drawText("FPS: --", rect.left + 4, rect.top + 14, paint)
            drawText("延迟: --", rect.left + 4, rect.top + 28, paint)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  WheelPad — 滚轮面板（基础版）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制滚轮面板（简化版，仅做视觉占位）。
 *
 * 完整版需要解析 [element.extraAttributesJson] 获取分段数量和名称。
 */
fun DrawScope.drawWheelPad(
    element: EditorElement,
) {
    val rect = elementRect(element)
    val cx = rect.center.x
    val cy = rect.center.y
    val outerRadius = min(rect.width, rect.height) / 2f - element.thick
    val innerRadius = outerRadius * WHEEL_INNER_RATIO

    val bgColor = element.backgroundColor.toColor()
    val normalColor = element.normalColor.toColor()
    val pressedColor = element.pressedColor.toColor()

    // 背景圆
    drawCircle(bgColor, outerRadius, Offset(cx, cy))

    // 外圈
    drawCircle(normalColor, outerRadius, Offset(cx, cy),
        style = Stroke(width = element.thick.toFloat()))

    // 内圈
    drawCircle(Color(0xFF000000), innerRadius, Offset(cx, cy))

    // 内圈边框
    drawCircle(normalColor.copy(alpha = 0.5f), innerRadius, Offset(cx, cy),
        style = Stroke(width = element.thick.toFloat() * 0.5f))

    // 分段线（8段）
    val segmentCount = element.mode.coerceIn(2, 24)
    val sweepAngle = 360f / segmentCount
    for (i in 0 until segmentCount) {
        val angle = Math.toRadians((i * sweepAngle - sweepAngle / 2 - 90).toDouble())
        val startX = cx + (innerRadius * cos(angle)).toFloat()
        val startY = cy + (innerRadius * sin(angle)).toFloat()
        val endX = cx + (outerRadius * cos(angle)).toFloat()
        val endY = cy + (outerRadius * sin(angle)).toFloat()
        drawLine(normalColor.copy(alpha = 0.4f),
            Offset(startX, startY), Offset(endX, endY),
            element.thick.toFloat() * 0.7f)
    }

    // 中心占位文字
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 14f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            drawText("轮盘", cx, cy + 5f, paint)
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

    // 半透明蓝色覆盖
    drawRect(Color(0x220066FF), rect.topLeft, rect.size)

    // 十字线
    drawLine(Color(0x440066FF), Offset(cx, rect.top), Offset(cx, rect.bottom), 1f)
    drawLine(Color(0x440066FF), Offset(rect.left, cy), Offset(rect.right, cy), 1f)
}
