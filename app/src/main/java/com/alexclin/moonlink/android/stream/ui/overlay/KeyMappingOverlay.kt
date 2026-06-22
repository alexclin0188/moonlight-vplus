package com.alexclin.moonlink.android.stream.ui.overlay

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.alexclin.moonlink.android.stream.ui.editor.EditorElement
import com.alexclin.moonlink.android.stream.ui.editor.ElementType
import com.alexclin.moonlink.android.stream.ui.editor.drawAnalogStick
import com.alexclin.moonlink.android.stream.ui.editor.drawDigitalButton
import com.alexclin.moonlink.android.stream.ui.editor.drawDigitalPad
import com.alexclin.moonlink.android.stream.ui.editor.drawGroupButton
import com.alexclin.moonlink.android.stream.ui.editor.drawUnknownElement
import com.alexclin.moonlink.android.stream.ui.editor.drawWheelPad

/**
 * 按键映射覆盖层 — Compose Canvas 渲染 + 触摸处理 + 输入发送。
 *
 * 接收 [EditorElement] 列表，用 [ElementRenderers] 的 draw* 函数渲染所有元素。
 * 触摸事件仅在被元素命中时消费，未命中区域透传到下层（串流画面）。
 * 命中时通过 [onElementAction] 回调通知上层发送实际按键输入给主机。
 * relX/relY 是触摸点相对于元素中心的位置（用于十字键方向检测和摇杆轴值计算）。
 * 虚拟手柄和游戏按键映射共用此覆盖层，仅数据来源不同。
 *
 * @param globalOpacity  全局透明度（0-100），叠加到所有元素的渲染透明度上
 * @param enabled        false 时跳过所有触摸处理，元素仍渲染但不可交互
 * @param touchSense     触控灵敏度（1-200），影响元素触摸命中区域的弹性边距
 * @param enhancedTouch  增强触控：启用时扩大触摸命中区域边距，提升触摸响应
 */
@Composable
fun KeyMappingOverlay(
    elements: List<EditorElement>,
    modifier: Modifier = Modifier,
    onElementAction: ((element: EditorElement, isPressed: Boolean, relX: Float, relY: Float) -> Unit)? = null,
    globalOpacity: Int = 100,
    enabled: Boolean = true,
    touchSense: Int = 100,
    enhancedTouch: Boolean = false,
) {
    val pressedIds = remember { mutableStateMapOf<Long, Boolean>() }
    // 记录每个元素的触摸偏移，用于 MOVE 时更新摇杆
    val touchOffsets = remember { mutableStateMapOf<Long, Offset>() }
    // 多指跟踪：pointerId → elementId
    val pointerToElement = remember { mutableStateMapOf<Int, Long>() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(globalOpacity.coerceIn(0, 100) / 100f)
            .then(
                if (enabled) Modifier.pointerInteropFilter { motionEvent ->
                val touchMargin = computeTouchMargin(touchSense, enhancedTouch)
                val actionMasked = motionEvent.actionMasked
                val pointerIndex = motionEvent.actionIndex
                val pointerId = motionEvent.getPointerId(pointerIndex)
                val position = Offset(
                    motionEvent.getX(pointerIndex),
                    motionEvent.getY(pointerIndex),
                )
                val hitIdx = findHitElement(elements, position, touchMargin)

                when (actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (hitIdx != null) {
                            // 如果该元素已被其他手指按住（同一元素），不再重复触发
                            if (pointerToElement.containsValue(hitIdx.elementId)) {
                                true
                            } else {
                                pressedIds[hitIdx.elementId] = true
                                pointerToElement[pointerId] = hitIdx.elementId
                                val rel = Offset(
                                    position.x - hitIdx.centralX,
                                    position.y - hitIdx.centralY
                                )
                                touchOffsets[hitIdx.elementId] = rel
                                onElementAction?.invoke(hitIdx, true, rel.x, rel.y)
                                true
                            }
                        } else {
                            // ACTION_DOWN 未命中时不消费（透传），POINTER_DOWN 未命中直接消费
                            actionMasked == MotionEvent.ACTION_POINTER_DOWN
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // 最后一指抬起：释放所有
                        for ((id, _) in pressedIds.toMap()) {
                            pressedIds[id] = false
                            val el = elements.find { it.elementId == id }
                            if (el != null) {
                                onElementAction?.invoke(el, false, 0f, 0f)
                            }
                        }
                        touchOffsets.clear()
                        pointerToElement.clear()
                        // 如果抬起位置命中某元素则消费，否则透传
                        hitIdx != null
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        // 某一指抬起：释放对应的元素
                        val releasedElId = pointerToElement.remove(pointerId)
                        if (releasedElId != null) {
                            pressedIds[releasedElId] = false
                            touchOffsets.remove(releasedElId)
                            val el = elements.find { it.elementId == releasedElId }
                            if (el != null) {
                                onElementAction?.invoke(el, false, 0f, 0f)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        for ((id, _) in pressedIds.toMap()) {
                            pressedIds[id] = false
                            val el = elements.find { it.elementId == id }
                            if (el != null) {
                                onElementAction?.invoke(el, false, 0f, 0f)
                            }
                        }
                        touchOffsets.clear()
                        pointerToElement.clear()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        var consumed = false
                        // 遍历所有活跃指针，处理每个指针的状态变化
                        for (i in 0 until motionEvent.pointerCount) {
                            val pid = motionEvent.getPointerId(i)
                            val elId = pointerToElement[pid]
                            if (elId == null) {
                                // 该指针未关联任何元素 — 检查是否滑入新元素
                                val pos = Offset(motionEvent.getX(i), motionEvent.getY(i))
                                val hit = findHitElement(elements, pos, touchMargin)
                                if (hit != null && !pointerToElement.containsValue(hit.elementId) && !pressedIds.containsKey(hit.elementId)) {
                                    // 手指滑入新元素
                                    pressedIds[hit.elementId] = true
                                    pointerToElement[pid] = hit.elementId
                                    val rel = Offset(
                                        pos.x - hit.centralX,
                                        pos.y - hit.centralY
                                    )
                                    touchOffsets[hit.elementId] = rel
                                    onElementAction?.invoke(hit, true, rel.x, rel.y)
                                    consumed = true
                                }
                                continue
                            }
                            // 更新已有元素的偏移（摇杆用）
                            val el = elements.find { it.elementId == elId }
                            if (el != null) {
                                val pos = Offset(motionEvent.getX(i), motionEvent.getY(i))
                                val rel = Offset(
                                    pos.x - el.centralX,
                                    pos.y - el.centralY
                                )
                                touchOffsets[elId] = rel
                                onElementAction?.invoke(el, true, rel.x, rel.y)
                                consumed = true
                            }
                        }
                        consumed
                    }
                    else -> false
                }
            }
                else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize(), contentDescription = "keymap") {
            val sorted = elements.sortedBy { it.layer }
            for (el in sorted) {
                val isPressed = pressedIds[el.elementId] == true
                drawElement(el, isPressed)
            }
        }
    }
}

/** 按 layer 降序 → 同 layer 按列表倒序检测（后绘制的优先），返回命中的元素，无命中返回 null */
private fun findHitElement(
    elements: List<EditorElement>,
    position: Offset,
    touchMargin: Int = 0,
): EditorElement? {
    val idx = elements.indices.sortedWith(
        compareByDescending<Int> { elements[it].layer }
            .thenByDescending { it }
    ).firstOrNull { hitTest(elements[it], position, touchMargin) }
    return if (idx != null) elements[idx] else null
}

/**
 * 检测 [offset] 是否落在元素 [el] 的矩形区域内。
 *
 * @param el            被检测的元素
 * @param offset        触摸点坐标
 * @param touchMargin   额外触控边距（像素），增强触控时增加此值以扩大命中区域
 * @return true 表示命中
 */
internal fun hitTest(el: EditorElement, offset: Offset, touchMargin: Int = 0): Boolean {
    val halfW = el.width / 2f + touchMargin
    val halfH = el.height / 2f + touchMargin
    val rect = Rect(
        left = el.centralX - halfW,
        top = el.centralY - halfH,
        right = el.centralX + halfW,
        bottom = el.centralY + halfH,
    )
    return rect.contains(offset)
}

/**
 * 根据触控灵敏度和增强触控计算触摸命中边距。
 * - 基础边距由 touchSense 决定（1~4px），越高越灵敏
 * - enhancedTouch 启用时额外 +8px
 * - 最终值归一化到 [0..16] 范围
 */
internal fun computeTouchMargin(touchSense: Int, enhancedTouch: Boolean): Int {
    // touchSense: 1~200 → baseMargin: 1~4px
    val senseNorm = touchSense.coerceIn(1, 200) / 100f
    val baseMargin = (senseNorm * 2).toInt().coerceIn(1, 4)
    val enhancedBonus = if (enhancedTouch) 8 else 0
    return (baseMargin + enhancedBonus).coerceIn(0, 16)
}

/** 根据元素类型分发绘制到对应的 renderer */
internal fun DrawScope.drawElement(el: EditorElement, isPressed: Boolean, wheelActiveIndex: Int = -1) {
    when (el.type) {
        ElementType.DIGITAL_COMMON_BUTTON,
        ElementType.DIGITAL_SWITCH_BUTTON,
        ElementType.DIGITAL_MOVABLE_BUTTON,
        ElementType.DIGITAL_COMBINE_BUTTON -> drawDigitalButton(el, isPressed)

        ElementType.GROUP_BUTTON -> drawGroupButton(el, isPressed)
        ElementType.DIGITAL_PAD -> drawDigitalPad(el, if (isPressed) 0x0F else 0)
        ElementType.ANALOG_STICK,
        ElementType.DIGITAL_STICK,
        ElementType.INVISIBLE_ANALOG_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK -> drawAnalogStick(el)

        ElementType.WHEEL_PAD -> drawWheelPad(el, wheelActiveIndex)
        ElementType.UNKNOWN -> drawUnknownElement(el)
    }
}

/**
 * 计算 [EditorElement] 在屏幕上的矩形边界。
 */
fun EditorElement.elementRect(): Rect {
    val halfW = width / 2f
    val halfH = height / 2f
    return Rect(
        offset = Offset(centralX - halfW, centralY - halfH),
        size = Size(width.toFloat(), height.toFloat()),
    )
}
