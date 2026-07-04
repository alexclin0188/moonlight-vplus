package com.alexclin.moonlink.android.stream.data

/**
 * config 表列名常量。
 *
 * 与旧 [PageConfigController] 中的常量完全等价，
 * 新代码（Compose）和旧代码（Crown 系统）统一引用此文件。
 */
object ConfigColumns {
    const val COLUMN_STRING_CONFIG_NAME = "config_name"
    const val COLUMN_BOOLEAN_TOUCH_ENABLE = "touch_enable"
    const val COLUMN_BOOLEAN_TOUCH_MODE = "touch_mode"
    const val COLUMN_INT_TOUCH_SENSE = "touch_sense"
    const val COLUMN_BOOLEAN_GAME_VIBRATOR = "game_vibrator"
    const val COLUMN_BOOLEAN_BUTTON_VIBRATOR = "button_vibrator"
    const val COLUMN_LONG_CONFIG_ID = "config_id"
    const val COLUMN_INT_MOUSE_WHEEL_SPEED = "mouse_wheel_speed"
    const val COLUMN_BOOLEAN_ENHANCED_TOUCH = "enhanced_touch"
    const val COLUMN_INT_GLOBAL_OPACITY = "global_opacity"
    const val COLUMN_INT_GLOBAL_BORDER_COLOR = "global_border_color"
    const val COLUMN_INT_GLOBAL_TEXT_COLOR = "global_text_color"
}
