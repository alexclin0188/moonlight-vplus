package com.alexclin.moonlink.android.stream.data

/**
 * Element 表的列名常量。
 *
 * 从旧 [Element] 类中提取，与 DB schema（[KeymappingDatabaseHelper]）
 * 中的 element 表列名完全一致。
 */
object ElementColumns {
    // ── 标识列 ──
    const val COLUMN_LONG_ELEMENT_ID = "element_id"
    const val COLUMN_LONG_CONFIG_ID = "config_id"

    // ── 类型 ──
    const val COLUMN_INT_ELEMENT_TYPE = "element_type"

    // ── 文字 ──
    const val COLUMN_STRING_ELEMENT_TEXT = "element_text"
    const val COLUMN_STRING_ELEMENT_VALUE = "element_value"
    const val COLUMN_STRING_ELEMENT_MIDDLE_VALUE = "element_middle_value"
    const val COLUMN_STRING_ELEMENT_UP_VALUE = "element_up_value"
    const val COLUMN_STRING_ELEMENT_DOWN_VALUE = "element_down_value"
    const val COLUMN_STRING_ELEMENT_LEFT_VALUE = "element_left_value"
    const val COLUMN_STRING_ELEMENT_RIGHT_VALUE = "element_right_value"

    // ── 位置 & 尺寸 ──
    const val COLUMN_INT_ELEMENT_CENTRAL_X = "element_central_x"
    const val COLUMN_INT_ELEMENT_CENTRAL_Y = "element_central_y"
    const val COLUMN_INT_ELEMENT_WIDTH = "element_width"
    const val COLUMN_INT_ELEMENT_HEIGHT = "element_height"
    const val COLUMN_INT_ELEMENT_LAYER = "element_layer"

    // ── 外观 ──
    const val COLUMN_INT_ELEMENT_RADIUS = "element_radius"
    const val COLUMN_INT_ELEMENT_OPACITY = "element_opacity"
    const val COLUMN_INT_ELEMENT_THICK = "element_thick"
    const val COLUMN_INT_ELEMENT_NORMAL_COLOR = "element_color"
    const val COLUMN_INT_ELEMENT_PRESSED_COLOR = "element_pressed_color"
    const val COLUMN_INT_ELEMENT_BACKGROUND_COLOR = "element_background_color"
    const val COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR = "normalTextColor"
    const val COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR = "pressedTextColor"
    const val COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT = "textSizePercent"

    // ── 行为 ──
    const val COLUMN_INT_ELEMENT_MODE = "element_mode"
    const val COLUMN_INT_ELEMENT_SENSE = "element_sense"
    const val COLUMN_INT_ELEMENT_FLAG1 = "element_flag1"

    // ── 扩展 ──
    const val COLUMN_STRING_EXTRA_ATTRIBUTES = "extra_attributes"

    // ── 元素类型常量 ──
    const val ELEMENT_TYPE_GROUP_BUTTON = 4L
    const val ELEMENT_TYPE_WHEEL_PAD = 40L
}
