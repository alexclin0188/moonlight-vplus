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
import com.alexclin.moonlink.android.stream.ui.editor.drawGroupButtonChildrenPreview
import com.alexclin.moonlink.android.stream.ui.editor.drawUnknownElement
import com.alexclin.moonlink.android.stream.ui.editor.drawWheelPad
import org.json.JSONObject

/**
 * 按键映射覆盖层 — Compose Canvas 渲染 + 触摸处理 + 输入发送。
 *
 * 接收 [EditorElement] 列表，用 [ElementRenderers] 的 draw* 函数渲染所有元素。
 * 触摸事件仅在被元素命中时消费，未命中区域透传到下层（串流画面）。
 * 命中时通过 [onElementAction] 回调通知上层发送实际按键输入给主机。
 * relX/relY 是触摸点相对于元素中心的位置（用于十字键方向检测和摇杆轴值计算）。
 * 虚拟手柄和游戏按键映射共用此覆盖层，仅数据来源不同。
 *
 * @param globalOpacity         全局不透明度（0-100），叠加到所有元素的渲染不透明度上
 * @param enabled               true=元素交互正常+非元素区域透传下层；false=元素交互正常+非元素区域消费(阻止透传)
 * @param touchSense            触控灵敏度（1-200），影响元素触摸命中区域的弹性边距
 * @param enhancedTouch         增强触控：启用时扩大触摸命中区域边距，提升触摸响应
 * @param onElementPositionChanged GroupButton 在 Normal 模式下长按拖动位置变更回调
 *   (elementId, newCx, newCy, isFinal): isFinal=true 表示拖动结束（UP/CANCEL），可持久化
 * @param repeatHandler          外部 Handler，用于 GroupButton 长按计时器（可选，默认内部创建）
 */
@Composable
fun KeyMappingOverlay(
    elements: List<EditorElement>,
    modifier: Modifier = Modifier,
    onElementAction: ((element: EditorElement, isPressed: Boolean, relX: Float, relY: Float) -> Unit)? = null,
    onElementPositionChanged: ((elementId: Long, newCentralX: Int, newCentralY: Int, isFinal: Boolean) -> Unit)? = null,
    globalOpacity: Int = 100,
    enabled: Boolean = true,
    touchSense: Int = 100,
    enhancedTouch: Boolean = false,
    repeatHandler: android.os.Handler = remember {
        android.os.Handler(android.os.Looper.getMainLooper())
    },
    activeDpadDirections: Map<Long, Int> = emptyMap(),
    wheelActiveIndex: Map<Long, Int> = emptyMap(),
    wheelPopupActive: Map<Long, Boolean> = emptyMap(),
    wheelHoveredGroupId: Map<Long, Long> = emptyMap(),
    allElements: List<EditorElement> = emptyList(),
) {
    // ── 通用触摸状态 ──
    val pressedIds = remember { mutableStateMapOf<Long, Boolean>() }
    val touchOffsets = remember { mutableStateMapOf<Long, Offset>() }
    val pointerToElement = remember { mutableStateMapOf<Int, Long>() }
    val switchToggleStates = remember { mutableStateMapOf<Long, Boolean>() }

    // ── GroupButton Normal 模式长按拖动状态 ──
    val groupDragActive = remember { mutableStateMapOf<Long, Boolean>() }
    val groupDragAnchor = remember { mutableStateMapOf<Long, Offset>() }
    val groupDragTimers = remember { mutableStateMapOf<Long, java.lang.Runnable>() }

    // ── 解析 GroupButton 是否可拖动 ──
    fun isDraggableGroup(el: EditorElement): Boolean {
        if (el.type != ElementType.GROUP_BUTTON) return false
        return try {
            org.json.JSONObject(el.extraAttributesJson)
                .optBoolean("movableInNormalMode", false)
        } catch (_: Exception) { false }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(globalOpacity.coerceIn(0, 100) / 100f)
            .pointerInteropFilter { motionEvent ->
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

                                // ── GroupButton: 启动 250ms 长按拖动计时器 ──
                                if (isDraggableGroup(hitIdx)) {
                                    val dragRunnable = java.lang.Runnable {
                                        val el = elements.find { it.elementId == hitIdx.elementId }
                                        if (el != null && pressedIds[hitIdx.elementId] == true) {
                                            // 长按触发 → 取消按键效果，进入拖动模式
                                            groupDragActive[hitIdx.elementId] = true
                                            groupDragAnchor[hitIdx.elementId] = Offset(
                                                position.x - el.centralX,
                                                position.y - el.centralY
                                            )
                                            onElementAction?.invoke(el, false, 0f, 0f)
                                        }
                                    }
                                    groupDragTimers[hitIdx.elementId] = dragRunnable
                                    repeatHandler.postDelayed(dragRunnable, 250)
                                    groupDragActive[hitIdx.elementId] = false
                                }

                                val newPressed = if (hitIdx.type == ElementType.DIGITAL_SWITCH_BUTTON) {
                                    val current = switchToggleStates[hitIdx.elementId] ?: false
                                    val next = !current
                                    switchToggleStates[hitIdx.elementId] = next
                                    next
                                } else {
                                    true
                                }
                                onElementAction?.invoke(hitIdx, newPressed, rel.x, rel.y)
                                true
                            }
                        } else {
                            !enabled || actionMasked == MotionEvent.ACTION_POINTER_DOWN
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // ── 清理 GroupButton 拖动状态，发送 isFinal=true 持久化 ──
                        for ((elId, _) in groupDragActive.toMap()) {
                            if (groupDragActive[elId] == true) {
                                val el = elements.find { it.elementId == elId }
                                if (el != null) {
                                    onElementPositionChanged?.invoke(elId, el.centralX, el.centralY, true)
                                }
                            }
                            groupDragTimers.remove(elId)?.let(repeatHandler::removeCallbacks)
                            groupDragActive.remove(elId)
                            groupDragAnchor.remove(elId)
                        }
                        // 释放所有（开关按键保持 toggle 状态，不回调）
                        for ((id, _) in pressedIds.toMap()) {
                            pressedIds[id] = false
                            val el = elements.find { it.elementId == id }
                            if (el != null && el.type != ElementType.DIGITAL_SWITCH_BUTTON) {
                                onElementAction?.invoke(el, false, 0f, 0f)
                            }
                        }
                        touchOffsets.clear()
                        pointerToElement.clear()
                        !enabled || hitIdx != null
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        // ── 清理该指针对应的 GroupButton 拖动状态，发送 isFinal=true ──
                        val releasedElId = pointerToElement[pointerId]
                        if (releasedElId != null) {
                            if (groupDragActive[releasedElId] == true) {
                                val el = elements.find { it.elementId == releasedElId }
                                if (el != null) {
                                    onElementPositionChanged?.invoke(releasedElId, el.centralX, el.centralY, true)
                                }
                            }
                            groupDragTimers.remove(releasedElId)?.let(repeatHandler::removeCallbacks)
                            groupDragActive.remove(releasedElId)
                            groupDragAnchor.remove(releasedElId)
                        }
                        // 某指抬起：释放对应元素（开关按键保持 toggle 状态，不回调）
                        pointerToElement.remove(pointerId)?.let { eId ->
                            pressedIds[eId] = false
                            touchOffsets.remove(eId)
                            val el = elements.find { it.elementId == eId }
                            if (el != null && el.type != ElementType.DIGITAL_SWITCH_BUTTON) {
                                onElementAction?.invoke(el, false, 0f, 0f)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // ── 清理所有 GroupButton 拖动状态，发送 isFinal=true ──
                        for ((elId, _) in groupDragActive.toMap()) {
                            if (groupDragActive[elId] == true) {
                                val el = elements.find { it.elementId == elId }
                                if (el != null) {
                                    onElementPositionChanged?.invoke(elId, el.centralX, el.centralY, true)
                                }
                            }
                            groupDragTimers.remove(elId)?.let(repeatHandler::removeCallbacks)
                            groupDragActive.remove(elId)
                            groupDragAnchor.remove(elId)
                        }
                        // 释放所有（开关按键保持 toggle 状态，不回调）
                        for ((id, _) in pressedIds.toMap()) {
                            pressedIds[id] = false
                            val el = elements.find { it.elementId == id }
                            if (el != null && el.type != ElementType.DIGITAL_SWITCH_BUTTON) {
                                onElementAction?.invoke(el, false, 0f, 0f)
                            }
                        }
                        touchOffsets.clear()
                        pointerToElement.clear()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        var consumed = false
                        // 遍历所有活跃指针
                        for (i in 0 until motionEvent.pointerCount) {
                            val pid = motionEvent.getPointerId(i)
                            val pos = Offset(motionEvent.getX(i), motionEvent.getY(i))
                            val elId = pointerToElement[pid]

                            // ── GroupButton 拖动中 ──
                            if (elId != null && groupDragActive[elId] == true) {
                                val anchor = groupDragAnchor[elId] ?: continue
                                val newCx = (pos.x - anchor.x).toInt()
                                val newCy = (pos.y - anchor.y).toInt()
                                onElementPositionChanged?.invoke(elId, newCx, newCy, false)
                                consumed = true
                                continue
                            }

                            if (elId == null) {
                                // 指针未关联任何元素 — 检查是否滑入新元素
                                val hit = findHitElement(elements, pos, touchMargin)
                                if (hit != null && !pointerToElement.containsValue(hit.elementId)
                                    && pressedIds[hit.elementId] != true
                                ) {
                                    // 手指滑入新元素
                                    pressedIds[hit.elementId] = true
                                    pointerToElement[pid] = hit.elementId
                                    val rel = Offset(pos.x - hit.centralX, pos.y - hit.centralY)
                                    touchOffsets[hit.elementId] = rel

                                    // 滑入可拖动 GroupButton 时启动长按计时器
                                    if (isDraggableGroup(hit)) {
                                        val dragRunnable = java.lang.Runnable {
                                            val el = elements.find { it.elementId == hit.elementId }
                                            if (el != null && pressedIds[hit.elementId] == true) {
                                                groupDragActive[hit.elementId] = true
                                                groupDragAnchor[hit.elementId] = Offset(
                                                    pos.x - el.centralX,
                                                    pos.y - el.centralY
                                                )
                                                onElementAction?.invoke(el, false, 0f, 0f)
                                            }
                                        }
                                        groupDragTimers[hit.elementId] = dragRunnable
                                        repeatHandler.postDelayed(dragRunnable, 250)
                                        groupDragActive[hit.elementId] = false
                                    }

                                    val newPressed = if (hit.type == ElementType.DIGITAL_SWITCH_BUTTON) {
                                        val cur = switchToggleStates[hit.elementId] ?: false
                                        val next = !cur
                                        switchToggleStates[hit.elementId] = next
                                        next
                                    } else {
                                        true
                                    }
                                    onElementAction?.invoke(hit, newPressed, rel.x, rel.y)
                                    consumed = true
                                }
                                continue
                            }

                            // ── 跨按钮手指滑动联动（旧 Crown 行为） ──
                            val currentEl = elements.find { it.elementId == elId }
                            if (currentEl != null) {
                                val stillInside = hitTest(currentEl, pos, touchMargin)

                                if (!stillInside) {
                                    // 手指离开当前元素 → 释放它（取消长按计时器）
                                    // 如果正在拖动，先发送 isFinal=true 持久化最终位置
                                    if (groupDragActive[elId] == true) {
                                        onElementPositionChanged?.invoke(elId, currentEl.centralX, currentEl.centralY, true)
                                    }
                                    groupDragTimers.remove(elId)?.let(repeatHandler::removeCallbacks)
                                    groupDragActive.remove(elId)
                                    groupDragAnchor.remove(elId)

                                    pressedIds[elId] = false
                                    touchOffsets.remove(elId)
                                    pointerToElement.remove(pid)
                                    val shouldNotify = currentEl.type != ElementType.DIGITAL_SWITCH_BUTTON
                                    if (shouldNotify) {
                                        onElementAction?.invoke(currentEl, false, 0f, 0f)
                                    }

                                    // 再检测是否滑入其他元素
                                    val hit = findHitElement(elements, pos, touchMargin)
                                    if (hit != null && !pointerToElement.containsValue(hit.elementId)
                                        && pressedIds[hit.elementId] != true
                                    ) {
                                        pressedIds[hit.elementId] = true
                                        pointerToElement[pid] = hit.elementId
                                        val rel = Offset(pos.x - hit.centralX, pos.y - hit.centralY)
                                        touchOffsets[hit.elementId] = rel

                                        // 滑入可拖动 GroupButton 时启动长按计时器
                                        if (isDraggableGroup(hit)) {
                                            val dragRunnable = java.lang.Runnable {
                                                val el = elements.find { it.elementId == hit.elementId }
                                                if (el != null && pressedIds[hit.elementId] == true) {
                                                    groupDragActive[hit.elementId] = true
                                                    groupDragAnchor[hit.elementId] = Offset(
                                                        pos.x - el.centralX,
                                                        pos.y - el.centralY
                                                    )
                                                    onElementAction?.invoke(el, false, 0f, 0f)
                                                }
                                            }
                                            groupDragTimers[hit.elementId] = dragRunnable
                                            repeatHandler.postDelayed(dragRunnable, 250)
                                            groupDragActive[hit.elementId] = false
                                        }

                                        val newPressed = if (hit.type == ElementType.DIGITAL_SWITCH_BUTTON) {
                                            val cur = switchToggleStates[hit.elementId] ?: false
                                            val next = !cur
                                            switchToggleStates[hit.elementId] = next
                                            next
                                        } else true
                                        onElementAction?.invoke(hit, newPressed, rel.x, rel.y)
                                    }
                                    consumed = true
                                    continue
                                }

                                // 仍在元素内 → 更新偏移（开关按键跳过 MOVE 回调，旧 Crown 行为）
                                val rel = Offset(pos.x - currentEl.centralX, pos.y - currentEl.centralY)
                                touchOffsets[elId] = rel
                                // 跳过 onElementAction 的条件：开关按键 / 可拖动 GroupButton 等待长按计时器触发中
                                val skipAction = currentEl.type == ElementType.DIGITAL_SWITCH_BUTTON ||
                                    (isDraggableGroup(currentEl) && groupDragTimers.containsKey(elId) && groupDragActive[elId] != true)
                                if (!skipAction) {
                                    onElementAction?.invoke(currentEl, true, rel.x, rel.y)
                                }
                                consumed = true
                            }
                        }
                        // enabled=false 时始终消费 MOVE 事件，阻止透传
                        consumed || !enabled
                    }
                    else -> false
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize(), contentDescription = "keymap") {
            val sorted = elements.sortedBy { it.layer }
            for (el in sorted) {
                val isPressed = if (el.type == ElementType.DIGITAL_SWITCH_BUTTON) {
                    switchToggleStates[el.elementId] == true
                } else {
                    pressedIds[el.elementId] == true
                }
                drawElement(
                    el, isPressed,
                    wheelActiveIndex = wheelActiveIndex[el.elementId] ?: -1,
                    wheelPopupActive = wheelPopupActive[el.elementId] ?: false,
                    activeDpadDirections = activeDpadDirections,
                )

                // ── 组按键子元素预览（轮盘悬停在 gb 分段时，需开启 previewGroupChildren） ──
                if (el.type == ElementType.WHEEL_PAD) {
                    val previewEnabled = try {
                        JSONObject(el.extraAttributesJson).optBoolean("previewGroupChildren", true)
                    } catch (_: Exception) { true }
                    val hoveredGbId = wheelHoveredGroupId[el.elementId]
                    if (previewEnabled && hoveredGbId != null && hoveredGbId > 0) {
                        val hoveredGroup = allElements.find { it.elementId == hoveredGbId }
                        if (hoveredGroup != null && hoveredGroup.type == ElementType.GROUP_BUTTON) {
                    val rect = el.elementRect()
                    val isPopupMode = el.text.isNotBlank()
                            val popupAtCenter = el.flag1 == 1
                            val isPopupActive = wheelPopupActive[el.elementId] == true
                            val translateX = if (isPopupMode && isPopupActive && popupAtCenter) {
                                size.width / 2f - rect.center.x
                            } else 0f
                            val translateY = if (isPopupMode && isPopupActive && popupAtCenter) {
                                size.height / 2f - rect.center.y
                            } else 0f
                            drawGroupButtonChildrenPreview(
                                hoveredGroup = hoveredGroup,
                                allElements = allElements,
                                wheelTranslateX = translateX,
                                wheelTranslateY = translateY,
                                wheelGlobalLeft = rect.left,
                                wheelGlobalTop = rect.top,
                            )
                        }
                    }
                }
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
internal fun DrawScope.drawElement(
    el: EditorElement,
    isPressed: Boolean,
    wheelActiveIndex: Int = -1,
    wheelPopupActive: Boolean = false,
    activeDpadDirections: Map<Long, Int> = emptyMap(),
) {
    when (el.type) {
        ElementType.DIGITAL_COMMON_BUTTON,
        ElementType.DIGITAL_SWITCH_BUTTON,
        ElementType.DIGITAL_MOVABLE_BUTTON,
        ElementType.DIGITAL_COMBINE_BUTTON -> drawDigitalButton(el, isPressed)

        ElementType.GROUP_BUTTON -> drawGroupButton(el, isPressed)
        ElementType.DIGITAL_PAD -> drawDigitalPad(el, activeDpadDirections[el.elementId] ?: 0)
        ElementType.ANALOG_STICK,
        ElementType.DIGITAL_STICK,
        ElementType.INVISIBLE_ANALOG_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK -> drawAnalogStick(el)

        ElementType.WHEEL_PAD -> drawWheelPad(
            el, wheelActiveIndex,
            isPopupMode = el.text.isNotBlank(),
            isPopupActive = wheelPopupActive,
            popupAtCenter = el.flag1 == 1,
        )
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
