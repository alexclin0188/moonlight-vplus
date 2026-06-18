package com.alexclin.moonlink.stream.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

// ════════════════════════════════════════════════════════════════════════════
//  尺寸调整手柄枚举
// ════════════════════════════════════════════════════════════════════════════

/** 手柄的像素半径（检测用） */
internal const val HANDLE_RADIUS = 12f
/** 手柄的绘制半径（视觉） */
internal const val HANDLE_DRAW_RADIUS = 6f

/** 元素的尺寸调整手柄位置 */
enum class ResizeHandle {
    TOP_LEFT, TOP, TOP_RIGHT,
    RIGHT, BOTTOM_RIGHT, BOTTOM,
    BOTTOM_LEFT, LEFT;
}

// ════════════════════════════════════════════════════════════════════════════
//  编辑器画布
// ════════════════════════════════════════════════════════════════════════════

/** 画布操作回调集合 */
data class CanvasCallbacks(
    val elementTap: (elementId: Long) -> Unit = {},
    val elementDragStart: (elementId: Long) -> Unit = {},
    val elementDrag: (elementId: Long, delta: Offset) -> Unit = { _, _ -> },
    val elementDragEnd: (elementId: Long) -> Unit = {},
    val elementResizeStart: (elementId: Long, handle: ResizeHandle) -> Unit = { _, _ -> },
    val elementResize: (elementId: Long, handle: ResizeHandle, delta: Offset) -> Unit = { _, _, _ -> },
    val elementResizeEnd: (elementId: Long) -> Unit = {},
    val canvasTap: () -> Unit = {},
)

/**
 * 按键映射编辑画布（Compose Canvas 实现，路线 A）。
 *
 * 绘制层级（从底到顶）：
 * 1. 半透明黑色背景
 * 2. 编辑网格（可选）
 * 3. 元素（按 layer 排序）
 * 4. 选中态高亮 + 尺寸调整手柄
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
    var resizingId by remember { mutableStateOf(0L) }
    var activeResizeHandle by remember { mutableStateOf<ResizeHandle?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(elements, selectedIds) {
                detectTapGestures(
                    onTap = { offset ->
                        // 先检查是否点击了 resize handle
                        val handleHit = if (resizingId == 0L) hitTestResizeHandle(elements, selectedIds, offset) else null
                        if (handleHit != null) {
                            // 点击手柄上不切换选中
                        } else {
                            val hitId = hitTestElement(elements, offset)
                            if (hitId != null) {
                                callbacks.elementTap(hitId)
                            } else {
                                callbacks.canvasTap()
                            }
                        }
                    },
                )
            }
            .pointerInput(elements, selectedIds) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // 1. 先检测 resize handle
                        val handleHit = hitTestResizeHandle(elements, selectedIds, offset)
                        if (handleHit != null) {
                            val (elId, handle) = handleHit
                            resizingId = elId
                            activeResizeHandle = handle
                            callbacks.elementResizeStart(elId, handle)
                            return@detectDragGestures
                        }
                        // 2. 再检测元素本体
                        val hitId = hitTestElement(elements, offset)
                        if (hitId != null) {
                            draggingId = hitId
                            callbacks.elementDragStart(hitId)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (resizingId != 0L && activeResizeHandle != null) {
                            callbacks.elementResize(resizingId, activeResizeHandle!!, dragAmount)
                        } else if (draggingId != 0L) {
                            callbacks.elementDrag(draggingId, dragAmount)
                        }
                    },
                    onDragEnd = {
                        if (resizingId != 0L) {
                            callbacks.elementResizeEnd(resizingId)
                            resizingId = 0L
                            activeResizeHandle = null
                        }
                        if (draggingId != 0L) {
                            callbacks.elementDragEnd(draggingId)
                            draggingId = 0L
                        }
                    },
                    onDragCancel = {
                        draggingId = 0L
                        resizingId = 0L
                        activeResizeHandle = null
                    },
                )
            },
    ) {
        val canvasSize = size

        // ── 1. 背景 ──
        drawRect(color = Color(0xCC000000.toInt()))

        // ── 2. 网格 ──
        if (gridColumnCount > 1) {
            drawGrid(gridColumnCount)
        }

        // ── 3+4. 元素（按 layer 排序）+ 选中元素的手柄 ──
        val sortedElements = elements.sortedBy { it.layer }
        for (element in sortedElements) {
            val isSelected = element.elementId in selectedIds
            val isPressed = element.elementId in pressedIds
            drawElement(element, isSelected, isPressed)
            if (isSelected) {
                drawResizeHandles(element)
            }
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
        ElementType.GROUP_BUTTON -> drawGroupButton(element, isPressed)
        ElementType.SIMPLIFY_PERFORMANCE -> drawSimplifyPerformance(element)
        ElementType.WHEEL_PAD -> drawWheelPad(element)
        ElementType.INVISIBLE_ANALOG_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK -> { /* 隐形不绘制 */ }
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

// ════════════════════════════════════════════════════════════════════════════
//  尺寸调整手柄
// ════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawResizeHandles(element: EditorElement) {
    val rect = elementRect(element)
    val handleColor = Color.White
    val handleBorder = Color(0xFF333333.toInt())
    val r = HANDLE_DRAW_RADIUS

    val positions = positionsForHandles(rect)
    for (pos in positions) {
        drawCircle(handleColor, r, pos)
        drawCircle(handleBorder, r, pos, style = Stroke(width = 1.5f))
    }
}

/** 计算 8 个手柄的中心位置 */
private fun positionsForHandles(rect: Rect): List<Offset> {
    val cx = rect.center.x
    val cy = rect.center.y
    return listOf(
        rect.topLeft,                 // TOP_LEFT
        Offset(cx, rect.top),         // TOP
        rect.topRight,                // TOP_RIGHT
        Offset(rect.right, cy),       // RIGHT
        rect.bottomRight,             // BOTTOM_RIGHT
        Offset(cx, rect.bottom),      // BOTTOM
        Offset(rect.left, cy),        // LEFT
        rect.bottomLeft,              // BOTTOM_LEFT
    )
}

/** 命中测试：检测 [offset] 是否在某个元素的手柄上 */
private fun hitTestResizeHandle(
    elements: List<EditorElement>,
    selectedIds: Set<Long>,
    offset: Offset,
): Pair<Long, ResizeHandle>? {
    val r = HANDLE_RADIUS
    for (element in elements) {
        if (element.elementId !in selectedIds) continue
        if (element.type == ElementType.INVISIBLE_ANALOG_STICK ||
            element.type == ElementType.INVISIBLE_DIGITAL_STICK) continue
        val rect = elementRect(element)
        val positions = positionsForHandles(rect)
        val handles = ResizeHandle.entries
        for ((i, pos) in positions.withIndex()) {
            if ((pos - offset).getDistance() <= r) {
                return element.elementId to handles[i]
            }
        }
    }
    return null
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
        if (el.type != ElementType.INVISIBLE_ANALOG_STICK && el.type != ElementType.INVISIBLE_DIGITAL_STICK) {
            if (elementRect(el).contains(offset)) {
                return el.elementId
            }
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

/** 根据手柄调整尺寸 */
fun applyResize(
    element: EditorElement,
    handle: ResizeHandle,
    delta: Offset,
    minSize: Int = 30,
): EditorElement {
    val dx = delta.x.roundToInt()
    val dy = delta.y.roundToInt()
    var x = element.centralX
    var y = element.centralY
    var w = element.width
    var h = element.height

    when (handle) {
        ResizeHandle.TOP_LEFT -> {
            val newW = (w - dx).coerceAtLeast(minSize)
            val newH = (h - dy).coerceAtLeast(minSize)
            x += (w - newW) / 2
            y += (h - newH) / 2
            w = newW; h = newH
        }
        ResizeHandle.TOP -> {
            val newH = (h - dy).coerceAtLeast(minSize)
            y += (h - newH) / 2
            h = newH
        }
        ResizeHandle.TOP_RIGHT -> {
            val newW = (w + dx).coerceAtLeast(minSize)
            val newH = (h - dy).coerceAtLeast(minSize)
            x += (w - newW) / 2
            y += (h - newH) / 2
            w = newW; h = newH
        }
        ResizeHandle.RIGHT -> {
            w = (w + dx).coerceAtLeast(minSize)
        }
        ResizeHandle.BOTTOM_RIGHT -> {
            w = (w + dx).coerceAtLeast(minSize)
            h = (h + dy).coerceAtLeast(minSize)
        }
        ResizeHandle.BOTTOM -> {
            h = (h + dy).coerceAtLeast(minSize)
        }
        ResizeHandle.BOTTOM_LEFT -> {
            val newW = (w - dx).coerceAtLeast(minSize)
            val newH = (h + dy).coerceAtLeast(minSize)
            x += (w - newW) / 2
            w = newW; h = newH
        }
        ResizeHandle.LEFT -> {
            val newW = (w - dx).coerceAtLeast(minSize)
            x += (w - newW) / 2
            w = newW
        }
    }
    return element.copy(centralX = x, centralY = y, width = w, height = h)
}
