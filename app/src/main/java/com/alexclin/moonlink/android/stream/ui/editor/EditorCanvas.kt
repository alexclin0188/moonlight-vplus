package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════════════════════════════
//  编辑器画布
// ════════════════════════════════════════════════════════════════════════════

/** 画布操作回调集合 */
data class CanvasCallbacks(
    val elementTap: (elementId: Long) -> Unit = {},
    val elementDragStart: (elementId: Long) -> Unit = {},
    val elementDrag: (elementId: Long, delta: Offset) -> Unit = { _, _ -> },
    val elementDragEnd: (elementId: Long) -> Unit = {},
    val canvasTap: () -> Unit = {},
)

/**
 * 按键映射编辑画布（Compose Canvas 实现，路线 A）。
 *
 * 绘制层级（从底到顶）：
 * 1. 半透明黑色背景
 * 2. 编辑网格（可选）
 * 3. 元素（按 layer 排序）
 * 4. 选中态高亮
 *
 * 隐形元素（INVISIBLE_ANALOG_STICK / INVISIBLE_DIGITAL_STICK）在编辑模式下用
 * 半透明虚线框绘制，并可被选中/拖拽，方便用户定位。
 *
 * @param elements 当前方案的所有元素
 * @param selectedIds 选中元素 ID
 * @param pressedIds 按下元素 ID
 * @param gridColumnCount 网格列数（0=不显示）
 * @param callbacks 画布交互回调集合
 * @param modifier Modifier
 */
@Composable
fun EditorCanvas(
    elements: List<EditorElement>,
    selectedIds: Set<Long>,
    pressedIds: Set<Long>,
    gridColumnCount: Int,
    callbacks: CanvasCallbacks = CanvasCallbacks(),
    modifier: Modifier = Modifier,
) {
    var draggingId by remember { mutableStateOf(0L) }

    // 使用 rememberUpdatedState 确保手势处理器能读到最新的 elements/selectedIds/callbacks，
    // 但 pointerInput 使用稳定 key（Unit）避免协程因状态变化而重启，
    // 从而修复已选中的按钮无法拖拽的问题。
    val currentElements by rememberUpdatedState(elements)
    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentCallbacks by rememberUpdatedState(callbacks)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val els = currentElements
                        val cb = currentCallbacks
                        val hitId = hitTestElement(els, offset)
                        if (hitId != null) {
                            cb.elementTap(hitId)
                        } else {
                            cb.canvasTap()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val els = currentElements
                        val cb = currentCallbacks
                        val hitId = hitTestElement(els, offset)
                        if (hitId != null) {
                            draggingId = hitId
                            cb.elementDragStart(hitId)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val cb = currentCallbacks
                        if (draggingId != 0L) {
                            cb.elementDrag(draggingId, dragAmount)
                        }
                    },
                    onDragEnd = {
                        val cb = currentCallbacks
                        if (draggingId != 0L) {
                            cb.elementDragEnd(draggingId)
                            draggingId = 0L
                        }
                    },
                    onDragCancel = {
                        draggingId = 0L
                    },
                )
            },
    ) {
        val canvasSize = size

        // ── 1. 网格 ──
        if (gridColumnCount > 1) {
            drawGrid(gridColumnCount)
        }

        // ── 3+4. 元素（按 layer 排序）+ 选中高亮 ──
        val sortedElements = elements.sortedBy { it.layer }
        for (element in sortedElements) {
            val isSelected = element.elementId in selectedIds
            val isPressed = element.elementId in pressedIds
            drawElement(element, isSelected, isPressed)
        }

        // ── 5. 底部画布尺寸提示 ──
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(60, 255, 255, 255)
                    textSize = 10f
                    isAntiAlias = true
                }
                drawText("${canvasSize.width.toInt()}×${canvasSize.height.toInt()}px",
                    canvasSize.width - 8f, canvasSize.height - 6f, paint)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  网格绘制
// ════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawGrid(gridColumnCount: Int) {
    val gridColor = Color(0x22FFFFFF.toInt())
    val cellWidth = size.width / gridColumnCount
    val rowCount = (size.height / cellWidth).roundToInt()
    if (cellWidth < 10f) return

    for (col in 0..gridColumnCount) {
        val x = col * cellWidth
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
    }
    for (row in 0..rowCount) {
        val y = row * cellWidth
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
    }

    val centerColor = Color(0x44FFFFFF.toInt())
    drawLine(centerColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1f)
    drawLine(centerColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
}

// ════════════════════════════════════════════════════════════════════════════
//  元素绘制分发
// ════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawElement(
    element: EditorElement,
    isSelected: Boolean,
    isPressed: Boolean,
) {
    when (element.type) {
        ElementType.DIGITAL_COMMON_BUTTON,
        ElementType.DIGITAL_SWITCH_BUTTON,
        ElementType.DIGITAL_MOVABLE_BUTTON,
        ElementType.DIGITAL_COMBINE_BUTTON -> drawDigitalButton(element, isPressed)
        ElementType.DIGITAL_PAD -> drawDigitalPad(element)
        ElementType.ANALOG_STICK,
        ElementType.DIGITAL_STICK -> drawAnalogStick(element)
        ElementType.INVISIBLE_ANALOG_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK -> drawInvisibleStickPreview(element)
        ElementType.UNKNOWN -> drawUnknownElement(element)
    }

    if (isSelected) drawSelectionHighlight(element)
}

// ════════════════════════════════════════════════════════════════════════════
//  选中高亮
// ════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawSelectionHighlight(element: EditorElement) {
    val rect = elementRect(element)
    drawRect(
        color = Color(0xFFFE9900.toInt()),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 3f),
    )
    // 边角指示器
    val cornerLen = 12f
    val corners = listOf(
        rect.topLeft to Offset(rect.left + cornerLen, rect.top),
        rect.topLeft to Offset(rect.left, rect.top + cornerLen),
        rect.topRight to Offset(rect.right - cornerLen, rect.top),
        rect.topRight to Offset(rect.right, rect.top + cornerLen),
        rect.bottomLeft to Offset(rect.left + cornerLen, rect.bottom),
        rect.bottomLeft to Offset(rect.left, rect.bottom - cornerLen),
        rect.bottomRight to Offset(rect.right - cornerLen, rect.bottom),
        rect.bottomRight to Offset(rect.right, rect.bottom - cornerLen),
    )
    for ((start, end) in corners) {
        drawLine(Color(0xFFFE9900.toInt()), start, end, 4f)
    }
}

/**
 * 隐形摇杆预览绘制（仅在编辑模式下显示）。
 * 使用半透明虚线边框 + 中心十字线 + 类型名提示，方便用户定位。
 */
private fun DrawScope.drawInvisibleStickPreview(element: EditorElement) {
    val rect = elementRect(element)
    val alpha = element.opacity / 100f * 0.4f

    // 半透明虚线边框
    drawRect(
        color = Color(0x88AAAAAA.toInt()).copy(alpha = alpha),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(
            width = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
        ),
    )

    // 中心十字线
    val cx = rect.center.x
    val cy = rect.center.y
    val crossColor = Color(0x66AAAAAA.toInt()).copy(alpha = alpha)
    drawLine(crossColor, Offset(cx, rect.top), Offset(cx, rect.bottom), 1f)
    drawLine(crossColor, Offset(rect.left, cy), Offset(rect.right, cy), 1f)

    // 类型名提示
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb((alpha * 180).toInt(), 170, 170, 170)
                textSize = 11f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val typeName = element.type.displayName
            drawText(typeName, cx, cy + 4f, paint)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  元素矩形 & 命中检测
// ════════════════════════════════════════════════════════════════════════════

fun elementRect(element: EditorElement): Rect {
    val left = element.centralX.toFloat() - element.width / 2f
    val top = element.centralY.toFloat() - element.height / 2f
    return Rect(left, top, left + element.width, top + element.height)
}

private fun hitTestElement(elements: List<EditorElement>, offset: Offset): Long? {
    val sorted = elements.sortedByDescending { it.layer }
    for (el in sorted) {
        if (elementRect(el).contains(offset)) {
            return el.elementId
        }
    }
    return null
}

// ════════════════════════════════════════════════════════════════════════════
//  辅助函数
// ════════════════════════════════════════════════════════════════════════════

fun elementBounds(element: EditorElement): Rect = elementRect(element)

fun Offset.toElementLocal(element: EditorElement): Offset {
    val bounds = elementRect(element)
    return Offset(x - bounds.left, y - bounds.top)
}

/** 网格吸附：将像素坐标吸附到最近的网格交叉点 */
fun snapToGrid(value: Int, gridCellSize: Int): Int {
    if (gridCellSize <= 1) return value
    return (value / gridCellSize) * gridCellSize
}
