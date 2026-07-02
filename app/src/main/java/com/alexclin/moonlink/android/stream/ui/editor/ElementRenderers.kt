package com.alexclin.moonlink.android.stream.ui.editor

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
import androidx.compose.ui.unit.dp
import org.json.JSONObject
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

    // ── 文字 ──
    val fontSizePx = (element.height * element.textSizePercent / 100f).coerceIn(8f, 120f)
    val textArgb = element.argbWithOpacity(if (isPressed) element.pressedTextColor else element.normalTextColor)
    val valueLabel = getKeyLabelByValue(element.value) ?: element.value

    // 组合键：text 为空时默认显示 "组合键{键值名}"
    val displayText = if (element.type == ElementType.DIGITAL_COMBINE_BUTTON && element.text.isBlank()) {
        "组合键"
    } else {
        element.text
    }

    if (displayText.isNotBlank() && !displayText.equals(valueLabel, ignoreCase = true)) {
        // 自定义按键名不为空且与键值名不同 → 两行显示：第一行按键名，第二行键值名（较小）
        val line1Size = fontSizePx * 0.7f
        val line2Size = fontSizePx * 0.5f
        val spacing = minOf(line2Size * 0.3f, with(drawContext.density) { 1.dp.toPx() })

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                val paint1 = android.graphics.Paint().apply {
                    color = textArgb
                    textSize = line1Size
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                val paint2 = android.graphics.Paint().apply {
                    color = textArgb
                    textSize = line2Size
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                val fm1 = paint1.fontMetrics
                val fm2 = paint2.fontMetrics
                val h1 = fm1.bottom - fm1.top
                val h2 = fm2.bottom - fm2.top
                val totalHeight = h1 + spacing + h2
                val blockTop = element.centralY.toFloat() - totalHeight / 2f

                // Line 1: 自定义按键名
                val line1CenterY = blockTop + h1 / 2f
                val baseline1 = line1CenterY - (fm1.top + fm1.bottom) / 2f
                drawText(displayText, element.centralX.toFloat(), baseline1, paint1)

                // Line 2: 键值名（较小）
                val line2CenterY = blockTop + h1 + spacing + h2 / 2f
                val baseline2 = line2CenterY - (fm2.top + fm2.bottom) / 2f
                drawText(valueLabel, element.centralX.toFloat(), baseline2, paint2)
            }
        }
    } else {
        // 没有自定义按键名（或与键值名相同）→ 单行显示键值名
        if (valueLabel.isNotBlank()) {
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
                    drawText(valueLabel, element.centralX.toFloat(), baseline, mPaint)
                }
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
//  未知类型 — 占位
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制未知类型的占位元素。
 */

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
