package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R

/**
 * 获取元素类型的本地化显示名称（Composable 版本）。
 * 在 Composable 作用域内使用 [stringResource] 从 Android 资源系统中读取。
 */
@Composable
fun ElementType.toDisplayName(): String = when (this) {
    ElementType.DIGITAL_COMMON_BUTTON -> stringResource(R.string.element_type_common_button)
    ElementType.DIGITAL_SWITCH_BUTTON -> stringResource(R.string.element_type_switch_button)
    ElementType.DIGITAL_MOVABLE_BUTTON -> stringResource(R.string.element_type_movable_button)
    ElementType.DIGITAL_COMBINE_BUTTON -> stringResource(R.string.element_type_combo_key)
    ElementType.DIGITAL_PAD -> stringResource(R.string.element_type_dpad)
    ElementType.ANALOG_STICK -> stringResource(R.string.element_type_analog_stick)
    ElementType.DIGITAL_STICK -> stringResource(R.string.element_type_digital_stick)
    ElementType.INVISIBLE_ANALOG_STICK -> stringResource(R.string.element_type_invisible_analog_stick)
    ElementType.INVISIBLE_DIGITAL_STICK -> stringResource(R.string.element_type_invisible_digital_stick)
    ElementType.UNKNOWN -> stringResource(R.string.element_type_unknown)
}

/**
 * 获取元素类型的本地化显示名称（非 Composable 版本）。
 * 在非 Composable 作用域（如 Toast、Canvas 绘制等）中使用 [Context.getString]。
 */
fun ElementType.getDisplayName(context: android.content.Context): String = when (this) {
    ElementType.DIGITAL_COMMON_BUTTON -> context.getString(R.string.element_type_common_button)
    ElementType.DIGITAL_SWITCH_BUTTON -> context.getString(R.string.element_type_switch_button)
    ElementType.DIGITAL_MOVABLE_BUTTON -> context.getString(R.string.element_type_movable_button)
    ElementType.DIGITAL_COMBINE_BUTTON -> context.getString(R.string.element_type_combo_key)
    ElementType.DIGITAL_PAD -> context.getString(R.string.element_type_dpad)
    ElementType.ANALOG_STICK -> context.getString(R.string.element_type_analog_stick)
    ElementType.DIGITAL_STICK -> context.getString(R.string.element_type_digital_stick)
    ElementType.INVISIBLE_ANALOG_STICK -> context.getString(R.string.element_type_invisible_analog_stick)
    ElementType.INVISIBLE_DIGITAL_STICK -> context.getString(R.string.element_type_invisible_digital_stick)
    ElementType.UNKNOWN -> context.getString(R.string.element_type_unknown)
}
