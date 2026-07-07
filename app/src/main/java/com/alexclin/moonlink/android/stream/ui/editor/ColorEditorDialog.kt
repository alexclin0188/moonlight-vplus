package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R

/**
 * 颜色项描述（内部使用，供 [ColorPickerDialog] 引用）。
 */
internal data class ColorItem(val label: String, val key: String)

/**
 * 固定 5 颜色项列表（供旧调用方 [ColorEditorDialog] 使用）。
 */
internal val COLOR_ITEMS = listOf(
    ColorItem("Normal", "normal"),
    ColorItem("Pressed", "pressed"),
    ColorItem("Background", "bg"),
    ColorItem("Text", "normalText"),
    ColorItem("Pressed Text", "pressedText"),
)

/**
 * 全屏颜色自定义对话框（5 项固定版本）。
 *
 * 内部委托给 [ColorPickerDialog]，传入固定的 5 个颜色项，
 * 并负责将返回的 key→颜色列表映射回 [EditorElement]。
 *
 * 新调用方应直接使用 [ColorPickerDialog]。
 */
@Composable
fun ColorEditorDialog(
    element: EditorElement,
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
    onElementChanged: ((EditorElement) -> Unit)? = null,
) {
    ColorPickerDialog(
        title = stringResource(R.string.editor_color_customize),
        items = listOf(
            ColorPickerItem("Normal", "normal", element.normalColor),
            ColorPickerItem("Pressed", "pressed", element.pressedColor),
            ColorPickerItem("Background", "bg", element.backgroundColor),
            ColorPickerItem("Text", "normalText", element.normalTextColor),
            ColorPickerItem("Pressed Text", "pressedText", element.pressedTextColor),
        ),
        onSave = { result ->
            val map = result.toMap()
            val updated = element.copy(
                normalColor = map["normal"] ?: element.normalColor,
                pressedColor = map["pressed"] ?: element.pressedColor,
                backgroundColor = map["bg"] ?: element.backgroundColor,
                normalTextColor = map["normalText"] ?: element.normalTextColor,
                pressedTextColor = map["pressedText"] ?: element.pressedTextColor,
            )
            onSave(updated)
        },
        onDismiss = onDismiss,
    )
}
