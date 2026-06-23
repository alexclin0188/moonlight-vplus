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
 * - 尺寸（width/height/radius）用统一缩放因子 min(scaleX,scaleY)，保持按钮宽高比不变，防止变形
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

        // X 坐标：使用 Max(宽,高) 的比例（横屏时映射到宽，竖屏时映射到高）
        val scaleX = kotlin.math.max(targetWidth, targetHeight).toFloat() /
                kotlin.math.max(sourceWidth, sourceHeight).toFloat()
        // Y 坐标：使用 Min(宽,高) 的比例（横屏时映射到高，竖屏时映射到宽）
        val scaleY = kotlin.math.min(targetWidth, targetHeight).toFloat() /
                kotlin.math.min(sourceWidth, sourceHeight).toFloat()

        // 位置：独立 X/Y 缩放（用 roundToLong 与 Java Math.round() 保持一致）
        val cx = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_CENTRAL_X)
        if (cx != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_CENTRAL_X, (cx * scaleX).roundToLong())
        }
        val cy = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_CENTRAL_Y)
        if (cy != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_CENTRAL_Y, (cy * scaleY).roundToLong())
        }

        // 尺寸：用统一缩放因子 min(scaleX,scaleY)，保持按钮宽高比不变
        val uniformScale = kotlin.math.min(scaleX, scaleY)
        val w = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_WIDTH)
        if (w != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_WIDTH, kotlin.math.max(1L, (w * uniformScale).roundToLong()))
        }
        val h = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_HEIGHT)
        if (h != null) {
            elementCv.put(Element.COLUMN_INT_ELEMENT_HEIGHT, kotlin.math.max(1L, (h * uniformScale).roundToLong()))
        }

        // 半径（DigitalPad 等使用）：用统一缩放因子
        val radius = elementCv.getAsLong(Element.COLUMN_INT_ELEMENT_RADIUS)
        if (radius != null) {
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
