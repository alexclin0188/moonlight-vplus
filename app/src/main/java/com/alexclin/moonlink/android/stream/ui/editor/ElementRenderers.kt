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

    // ── 文字 ──
    val fontSizePx = (element.height * element.textSizePercent / 100f).coerceIn(8f, 120f)
    val textArgb = element.argbWithOpacity(if (isPressed) element.pressedTextColor else element.normalTextColor)
    val valueLabel = getKeyLabelByValue(element.value) ?: element.value

    if (element.text.isNotBlank() && !element.text.equals(valueLabel, ignoreCase = true)) {
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
                drawText(element.text, element.centralX.toFloat(), baseline1, paint1)

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
//  WheelPad — 滚轮面板（完整版，参照旧 Crown）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 绘制滚轮面板（总调度）。
 *
 * 根据弹窗模式状态决定渲染策略：
 * - 弹窗未激活 → 仅绘制中心触发器
 * - 弹窗激活 + 屏幕居中 → 画布平移到屏幕中心绘制完整轮盘
 * - 弹窗激活 + 原地弹出 → 在元素原位绘制完整轮盘
 * - 非弹窗模式（直接模式）→ 始终在元素原位绘制完整轮盘
 *
 * @param element          轮盘元素数据
 * @param activeIndex      当前选中的扇区索引（-1 无选中）
 * @param isPopupMode      是否弹窗模式（element.text 非空时为 true）
 * @param isPopupActive    弹窗模式是否已激活展开
 * @param popupAtCenter    弹窗是否屏幕居中（对应 flag1）
 */
fun DrawScope.drawWheelPad(
    element: EditorElement,
    activeIndex: Int = -1,
    isPopupMode: Boolean = false,
    isPopupActive: Boolean = false,
    popupAtCenter: Boolean = true,
) {
    if (isPopupMode && !isPopupActive) {
        // ── 弹窗未激活：仅绘制中心触发器 ──
        drawWheelPadTrigger(element)
    } else if (isPopupMode && isPopupActive && popupAtCenter) {
        // ── 弹窗激活 + 屏幕居中：平移到屏幕中心绘制 ──
        val rect = elementRect(element)
        val translateX = size.width / 2f - rect.center.x
        val translateY = size.height / 2f - rect.center.y
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(translateX, translateY)
        }
        drawFullWheel(element, activeIndex)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.restore()
        }
    } else {
        // ── 原地绘制（弹窗激活原地 / 直接模式） ──
        drawFullWheel(element, activeIndex)
    }
}

/**
 * 绘制未激活弹窗的触发器：中心圆 + 触发器文字。
 * 等效旧 Crown [drawInactivePopupCenter]。
 */
fun DrawScope.drawWheelPadTrigger(element: EditorElement) {
    val rect = elementRect(element)
    val cx = rect.center.x
    val cy = rect.center.y
    val outerRadius = min(rect.width, rect.height) / 2f - element.thick
    val innerRatio = (element.sense.coerceIn(10, 90) / 100f)
    val innerRadius = outerRadius * innerRatio
    val borderInnerRadius = innerRadius + element.thick / 2f

    val normalColor = element.withOpacity(element.normalColor)

    // 半透明深色中心圆
    drawCircle(Color(0x80000000), innerRadius, Offset(cx, cy))

    // 内圈高亮轮廓（柔和阴影）
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            color = element.argbWithOpacity(element.normalColor)
            strokeWidth = element.thick.toFloat()
            isAntiAlias = true
            setShadowLayer(
                element.thick * 0.6f, 0f, 0f,
                (element.normalColor and 0x00FFFFFF) or 0x33000000
            )
        }
        canvas.nativeCanvas.drawCircle(cx, cy, borderInnerRadius, paint)
        // 清除阴影避免影响后续
        paint.clearShadowLayer()
    }

    // ── 中心触发器文字 ──
    val triggerText = element.text
    if (triggerText.isNotBlank()) {
        // 从 extraAttributesJson 读取触发器文字大小和颜色
        val extraAttrs = try { JSONObject(element.extraAttributesJson) } catch (_: Exception) { JSONObject() }
        val triggerTextSizePercent = extraAttrs.optInt("triggerTextSizePercent", 40)
        val centerTxtColor = extraAttrs.optInt("centerTextColor", -1)
        fun effectiveCenterTextColor(): Int = if (centerTxtColor != -1) centerTxtColor else 0xFFFFFFFF.toInt()

        val triggerTextSize = (innerRadius * 2) * (triggerTextSizePercent / 100f)

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = element.argbWithOpacity(effectiveCenterTextColor())
                textSize = triggerTextSize
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
                setShadowLayer(
                    (element.thick * 0.3f).coerceAtLeast(2f), 0f,
                    (element.thick * 0.2f).coerceAtLeast(1f), 0x88000000.toInt()
                )
            }
            val metrics = paint.fontMetrics
            val baseline = cy - (metrics.top + metrics.bottom) / 2f
            canvas.nativeCanvas.drawText(triggerText, cx, baseline, paint)
            paint.clearShadowLayer()
        }
    }
}

/**
 * 绘制完整轮盘（扇形、分割线、文字、中心预览）。
 * 等效旧 Crown [drawFullWheel]。
 */
private fun DrawScope.drawFullWheel(
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

    // 解析段值列表（逗号分隔，支持 "k51|名称" 和 "k51+k29" 多键组合格式）
    val segments = element.value.split(",").filter { it.isNotBlank() }

    // 解析 extraAttributesJson（一次解析，复用）
    val extraAttrs = try { JSONObject(element.extraAttributesJson) } catch (_: Exception) { JSONObject() }

    val textSizePct = extraAttrs.optInt("textSizePercent", 35)
    val centerTextSizePct = extraAttrs.optInt("centerTextSizePercent", 60)
    val normalTxtColor = extraAttrs.optInt("normalTextColor", -1)
    val pressedTxtColor = extraAttrs.optInt("pressedTextColor", -1)
    val centerTxtColor = extraAttrs.optInt("centerTextColor", -1)

    fun effectiveNormalTextColor(): Int = if (normalTxtColor != -1) normalTxtColor else 0xFFFFFFFF.toInt()
    fun effectivePressedTextColor(): Int = if (pressedTxtColor != -1) pressedTxtColor else 0xFFFFFFFF.toInt()
    fun effectiveCenterTextColor(): Int = if (centerTxtColor != -1) centerTxtColor else 0xFFFFFFFF.toInt()

    // ── 绘制各段扇形 ──
    val isPopupMode = element.text.isNotBlank()
    val popupAtCenter = element.flag1 == 1

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

        // ── 激活分区发光效果（参照旧 Crown paintGlow） ──
        if (isActive) {
            val glowAlpha = 0x3C // ~60/255, 柔和发光
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                    val pc = element.pressedColor
                    color = android.graphics.Color.argb(
                        glowAlpha,
                        android.graphics.Color.red(pc),
                        android.graphics.Color.green(pc),
                        android.graphics.Color.blue(pc),
                    )
                    setShadowLayer(
                        outerRadius * 0.15f, 0f, 0f,
                        element.pressedColor
                    )
                }
                val arcRect = android.graphics.RectF(
                    cx - outerRadius, cy - outerRadius,
                    cx + outerRadius, cy + outerRadius
                )
                canvas.nativeCanvas.drawArc(arcRect, startAngle, sweepAngle, true, paint)
                paint.clearShadowLayer()
            }
        }
    }

    // ── 中心圆 ──
    drawCircle(Color(0xFF000000), innerRadius, Offset(cx, cy))

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

    // ── 外圈/内圈边框 ──
    // 屏幕居中弹窗时外圈添加柔和阴影
    if (isPopupMode && popupAtCenter) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                color = element.argbWithOpacity(element.normalColor)
                strokeWidth = element.thick.toFloat()
                isAntiAlias = true
                setShadowLayer(
                    (element.thick * 0.6f).coerceAtLeast(2f), 0f, 0f, 0x55000000
                )
            }
            canvas.nativeCanvas.drawCircle(cx, cy, outerRadius - element.thick / 2f, paint)
            paint.clearShadowLayer()
        }
    } else {
        drawCircle(normalColor, outerRadius, Offset(cx, cy), style = Stroke(width = element.thick.toFloat()))
    }
    drawCircle(element.withOpacity(element.normalColor, 0.5f), innerRadius, Offset(cx, cy),
        style = Stroke(width = element.thick.toFloat() * 0.5f))

    // ── 各段文字 ──
    val ringThickness = outerRadius - innerRadius
    val textSizePx = (ringThickness * (textSizePct / 100f)).coerceIn(8f, 48f)
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
            // 解析显示名称：优先用 | 后缀命名，否则查键值表
            val displayName = if (segValue.contains("|")) {
                segValue.substringAfterLast("|")
            } else {
                // 多键组合时显示第一个键的名称
                val firstKey = segValue.substringBefore("+")
                getKeyLabelByValue(firstKey) ?: segValue
            }
            paint.color = if (i == activeIndex) {
                element.argbWithOpacity(effectivePressedTextColor())
            } else {
                element.argbWithOpacity(effectiveNormalTextColor())
            }
            paint.isFakeBoldText = i == activeIndex
            val metrics = paint.fontMetrics
            val baseline = ty - (metrics.top + metrics.bottom) / 2f
            nativeCanvas.drawText(if (displayName.isNotEmpty()) displayName else "-", tx, baseline, paint)
        }
    }

    // ── 选中时中心显示键值名（带阴影） ──
    if (activeIndex in 0 until segmentCount) {
        val segValue = segments.getOrElse(activeIndex) { "" }
        val centerDisplay = if (segValue.contains("|")) {
            segValue.substringAfterLast("|")
        } else {
            getKeyLabelByValue(segValue) ?: segValue
        }
        val ctSizePx = innerRadius * 2 * (centerTextSizePct / 100f)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = element.argbWithOpacity(effectiveCenterTextColor())
                    textSize = ctSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                    setShadowLayer(
                        (ctSizePx * 0.06f).coerceAtLeast(2f), 0f,
                        (ctSizePx * 0.04f).coerceAtLeast(1f), 0x88000000.toInt()
                    )
                }
                val metrics = paint.fontMetrics
                val baseline = cy - (metrics.top + metrics.bottom) / 2f
                drawText(centerDisplay, cx, baseline, paint)
                paint.clearShadowLayer()
            }
        }
    }
}

/**
 * 绘制组按键子元素预览（轮盘悬停在 gb 分段时）。
 * 等效旧 Crown [drawHoveredGroupButtonChildren]。
 *
 * @param hoveredGroup      悬停的 GroupButton 元素
 * @param allElements       所有元素列表（用于查找子元素）
 * @param wheelTranslateX   轮盘画布 X 轴平移量（屏幕居中模式有值，原地模式为 0）
 * @param wheelTranslateY   轮盘画布 Y 轴平移量
 * @param wheelGlobalLeft   轮盘在屏幕上的绝对 left 坐标
 * @param wheelGlobalTop    轮盘在屏幕上的绝对 top 坐标
 */
fun DrawScope.drawGroupButtonChildrenPreview(
    hoveredGroup: EditorElement,
    allElements: List<EditorElement>,
    wheelTranslateX: Float = 0f,
    wheelTranslateY: Float = 0f,
    wheelGlobalLeft: Float = 0f,
    wheelGlobalTop: Float = 0f,
) {
    val childIds = hoveredGroup.value
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .filter { it != -1L }
    if (childIds.isEmpty()) return

    val children = allElements.filter { it.elementId in childIds }
    if (children.isEmpty()) return

    drawIntoCanvas { canvas ->
        for (child in children) {
            canvas.nativeCanvas.save()
            // 计算子元素相对轮盘画布原点的位置
            // child.centralX/centralY 是屏幕绝对坐标
            // wheelGlobalLeft/Top + wheelTranslateX/Y 是当前画布原点在屏幕上的位置
            val childDrawX = child.centralX - (wheelGlobalLeft + wheelTranslateX) - child.width / 2f
            val childDrawY = child.centralY - (wheelGlobalTop + wheelTranslateY) - child.height / 2f
            canvas.nativeCanvas.translate(childDrawX, childDrawY)

            // 绘制子元素 — 需要手工调用各类型的绘制
            drawSingleChildElement(canvas.nativeCanvas, child, child.width, child.height)

            canvas.nativeCanvas.restore()
        }
    }
}

/**
 * 在 native Canvas 上绘制单个子元素。
 * 简化版：对于预览场景，绘制基本边框和文字即可。
 */
private fun drawSingleChildElement(
    nativeCanvas: android.graphics.Canvas,
    el: EditorElement,
    drawW: Int,
    drawH: Int,
) {
    val bgPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        color = (el.backgroundColor and 0x00FFFFFF) or ((el.backgroundColor ushr 24) * el.opacity / 100 shl 24)
        isAntiAlias = true
    }
    val borderPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        color = (el.normalColor and 0x00FFFFFF) or ((el.normalColor ushr 24) * el.opacity / 100 shl 24)
        strokeWidth = el.thick.toFloat()
        isAntiAlias = true
    }
    val textPaint = android.graphics.Paint().apply {
        color = (el.normalTextColor and 0x00FFFFFF) or ((el.normalTextColor ushr 24) * el.opacity / 100 shl 24)
        textSize = (drawH * el.textSizePercent / 100f).coerceIn(8f, 60f)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    val rectF = android.graphics.RectF(0f, 0f, drawW.toFloat(), drawH.toFloat())
    val radius = el.radius.toFloat()
    if (radius > 0) {
        nativeCanvas.drawRoundRect(rectF, radius, radius, bgPaint)
        nativeCanvas.drawRoundRect(rectF, radius, radius, borderPaint)
    } else {
        nativeCanvas.drawRect(rectF, bgPaint)
        nativeCanvas.drawRect(rectF, borderPaint)
    }

    val label = getKeyLabelByValue(el.value) ?: el.text.ifBlank { el.value }
    if (label.isNotBlank()) {
        val metrics = textPaint.fontMetrics
        val baseline = drawH / 2f - (metrics.top + metrics.bottom) / 2f
        nativeCanvas.drawText(label, drawW / 2f, baseline, textPaint)
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
