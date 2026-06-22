package com.alexclin.moonlink.android.stream.ui

import android.content.Context
import android.content.ContentValues
import android.util.DisplayMetrics
import android.view.WindowManager
import com.limelight.binding.input.advance_setting.element.Element
import kotlin.math.roundToLong

/**
 * 屏幕参数换算工具：导出时将当前设备屏幕尺寸写入文件，
 * 导入时根据原屏幕与目标屏幕的比例自动缩放元素坐标和大小。
 *
 * 元素存储的属性：
 * - [Element.COLUMN_INT_ELEMENT_CENTRAL_X] / [Element.COLUMN_INT_ELEMENT_CENTRAL_Y] — 中心坐标 (px)
 * - [Element.COLUMN_INT_ELEMENT_WIDTH] / [Element.COLUMN_INT_ELEMENT_HEIGHT] — 尺寸 (px)
 *
 * 缩放策略：
 * - 位置用独立 X/Y 缩放（保持相对位置百分比）
 * - 尺寸用宽/高缩放的比例平均值（保持视觉比例，不因奇偶差异失真）
 */
object ScreenScaleHelper {

    /** JSON key：源设备屏幕宽度 (px) */
    const val KEY_SOURCE_WIDTH = "sourceWidth"

    /** JSON key：源设备屏幕高度 (px) */
    const val KEY_SOURCE_HEIGHT = "sourceHeight"

    /**
     * 获取当前设备的真实屏幕像素尺寸。
     */
    fun getDeviceScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * 使用 [ContentValues] 对单个元素进行坐标缩放。
     * 如果元素缺少必要的坐标字段则跳过。
     */
    fun scaleElementContentValues(
        elementCv: ContentValues,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) return

        val scaleX = targetWidth.toFloat() / sourceWidth.toFloat()
        val scaleY = targetHeight.toFloat() / sourceHeight.toFloat()

        // 位置：独立 X/Y 缩放（用 roundToLong 与 Java Math.round() 保持一致）
        val cx = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_CENTRAL_X)
        if (cx != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_CENTRAL_X, (cx * scaleX).roundToLong())
        }
        val cy = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_CENTRAL_Y)
        if (cy != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_CENTRAL_Y, (cy * scaleY).roundToLong())
        }

        // 尺寸：用各自方向的缩放，但保证至少 1px
        val w = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_WIDTH)
        if (w != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_WIDTH, kotlin.math.max(1L, (w * scaleX).roundToLong()))
        }
        val h = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_HEIGHT)
        if (h != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_HEIGHT, kotlin.math.max(1L, (h * scaleY).roundToLong()))
        }

        // 半径（DigitalPad 等使用）：用 min 缩放比例防止变形
        val radius = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_RADIUS)
        if (radius != null) {
            val uniformScale = kotlin.math.min(scaleX, scaleY)
            elementCv.put(Element.COLUMN_INT_ELEMENT_RADIUS, kotlin.math.max(1L, (radius * uniformScale).roundToLong()))
        }
    }

    /**
     * 对 [ContentValues] 列表（批量元素）进行坐标缩放。
     */
    fun scaleAllElements(
        elements: List<ContentValues>,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        for (el in elements) {
            scaleElementContentValues(el, sourceWidth, sourceHeight, targetWidth, targetHeight)
        }
    }

    /**
     * 对 [ContentValues] 数组（批量元素）进行坐标缩放。
     */
    fun scaleAllElements(
        elements: Array<ContentValues>,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        for (el in elements) {
            scaleElementContentValues(el, sourceWidth, sourceHeight, targetWidth, targetHeight)
        }
    }
}
