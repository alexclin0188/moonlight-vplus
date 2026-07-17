package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 螺旋放置工具 — 当屏幕中心已被占用时，按阿基米德螺旋线向外寻找空位。
 *
 * 算法：
 * 1. 先检查中心位置 (canvasWidth/2, canvasHeight/2) 是否空闲
 * 2. 若空闲 → 直接返回中心
 * 3. 若被占用 → 按螺旋线向外旋转，逐步增大角度和半径
 * 4. 每次迭代检查候选位置是否与任何现有元素重叠
 * 5. 找到空位则返回，否则继续旋转
 * 6. 超过最大迭代次数后兜底返回中心
 *
 * @param existingElements 现有元素列表
 * @param newElementWidth  新元素宽度（px）
 * @param newElementHeight 新元素高度（px）
 * @param canvasWidth      画布宽度（px）
 * @param canvasHeight     画布高度（px）
 * @param maxScreenPx      坐标上限（用于 clamping）
 * @return Pair(x, y) 放置位置的中心坐标
 */
fun findSpiralPlacement(
    existingElements: List<EditorElement>,
    newElementWidth: Int,
    newElementHeight: Int,
    canvasWidth: Int,
    canvasHeight: Int,
    maxScreenPx: Int = 5000,
): Pair<Int, Int> {
    val centerX = (canvasWidth / 2).coerceIn(50, maxScreenPx - 50)
    val centerY = (canvasHeight / 2).coerceIn(50, maxScreenPx - 50)

    // 先检查中心是否空闲
    if (!isOverlappingAny(centerX, centerY, newElementWidth, newElementHeight, existingElements)) {
        return centerX to centerY
    }

    // 阿基米德螺旋线参数
    val angleStep = PI / 4          // 45°，每次旋转角度步长
    val radiusStep = 30.0           // 每转一圈向外扩展 30px（"不要太大"）
    val b = radiusStep / (2 * PI)   // 螺旋线系数: r = b * θ

    val maxIterations = 200

    for (i in 1..maxIterations) {
        val theta = i * angleStep
        val r = b * theta

        val x = (centerX + r * cos(theta)).roundToInt()
        val y = (centerY + r * sin(theta)).roundToInt()

        // 超出画布范围则跳过
        if (x < 0 || x > maxScreenPx || y < 0 || y > maxScreenPx) continue

        if (!isOverlappingAny(x, y, newElementWidth, newElementHeight, existingElements)) {
            return x.coerceIn(0, maxScreenPx) to y.coerceIn(0, maxScreenPx)
        }
    }

    // 兜底：返回中心
    return centerX to centerY
}

/**
 * 判断以 (cx, cy) 为中心、给定宽高的元素，是否与现有元素列表中的任何一个重叠。
 */
private fun isOverlappingAny(
    cx: Int,
    cy: Int,
    width: Int,
    height: Int,
    elements: List<EditorElement>,
): Boolean {
    val newRect = Rect(
        left = (cx - width / 2f).coerceAtLeast(0f),
        top = (cy - height / 2f).coerceAtLeast(0f),
        right = (cx + width / 2f).toFloat(),
        bottom = (cy + height / 2f).toFloat(),
    )
    return elements.any { existing ->
        val existingRect = elementRect(existing)
        // 给一点间距 (2px margin)，避免紧贴时也算重叠
        val expandedRect = Rect(
            left = existingRect.left - 2f,
            top = existingRect.top - 2f,
            right = existingRect.right + 2f,
            bottom = existingRect.bottom + 2f,
        )
        newRect.overlaps(expandedRect)
    }
}