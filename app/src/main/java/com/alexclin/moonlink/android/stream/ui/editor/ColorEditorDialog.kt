package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.runtime.Composable

/**
 * 颜色项描述（内部使用，供 [ColorPickerDialog] 引用）。
 */
internal data class ColorItem(val label: String, val key: String)

/**
 * 固定 5 颜色项列表（供旧调用方 [ColorEditorDialog] 使用）。
 */
internal val COLOR_ITEMS = listOf(
    ColorItem("正常色", "normal"),
    ColorItem("按下色", "pressed"),
    ColorItem("背景色", "bg"),
    ColorItem("文字色", "normalText"),
    ColorItem("按下文字色", "pressedText"),
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
        title = "颜色自定义",
        items = listOf(
            ColorPickerItem("正常色", "normal", element.normalColor),
            ColorPickerItem("按下色", "pressed", element.pressedColor),
            ColorPickerItem("背景色", "bg", element.backgroundColor),
            ColorPickerItem("文字色", "normalText", element.normalTextColor),
            ColorPickerItem("按下文字色", "pressedText", element.pressedTextColor),
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
