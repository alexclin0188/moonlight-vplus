package com.alexclin.moonlink.android.stream.ui.editor

/**
 * 收集元素列表中所有已使用的显示名称，用于按键名查重。
 * 包括：
 * - 所有元素非空的 [EditorElement.text]
 * - 组合键（DIGITAL_COMBINE_BUTTON）方向值（up/down/left/right）对应的键值标签
 * - 摇杆/十字键类型方向值（up/down/left/right/middle）对应的键值标签
 *
 * @param elements 元素列表
 * @param excludeElementId 需要排除的元素 ID（编辑模式下排除自身）
 */
internal fun collectElementDisplayNames(
    elements: List<EditorElement>,
    excludeElementId: Long? = null,
): Set<String> {
    val names = mutableSetOf<String>()
    for (el in elements) {
        if (el.elementId == excludeElementId) continue
        // 元素的 text 字段
        if (el.text.isNotBlank()) names.add(el.text.trim())
        // 方向值对应的键值标签
        val dirValues = when (el.type) {
            ElementType.DIGITAL_COMBINE_BUTTON -> listOf(
                el.upValue, el.downValue, el.leftValue, el.rightValue
            )
            ElementType.DIGITAL_PAD,
            ElementType.ANALOG_STICK,
            ElementType.DIGITAL_STICK,
            ElementType.INVISIBLE_ANALOG_STICK,
            ElementType.INVISIBLE_DIGITAL_STICK -> listOf(
                el.upValue, el.downValue, el.leftValue, el.rightValue, el.middleValue
            )
            else -> emptyList()
        }
        for (v in dirValues) {
            if (v.isNotBlank()) {
                val label = getKeyLabelByValue(v) ?: v
                names.add(label)
            }
        }
    }
    return names
}

/**
 * 校验键值映射中是否存在内部互斥重复。
 * 检查所有非空值是否有重复，并生成详细的重复描述信息。
 * 适用于组合键方向值互斥校验、摇杆方向值互斥校验等场景。
 *
 * @param labeledValues 标签→键值的映射，如 mapOf("单击" to "k29", "上滑" to "k29")
 * @return 存在重复时返回错误描述（含标签），无重复时返回 null
 */
internal fun findDuplicateKeyValues(labeledValues: Map<String, String>): String? {
    val nonBlank = labeledValues.filterValues { it.isNotBlank() }
    val valueToLabels = mutableMapOf<String, MutableList<String>>()
    for ((label, value) in nonBlank) {
        valueToLabels.getOrPut(value) { mutableListOf() }.add(label)
    }
    val duplicates = valueToLabels.filter { it.value.size > 1 }
    if (duplicates.isEmpty()) return null
    return duplicates.entries.joinToString("；") { (value, labels) ->
        val keyLabel = getKeyLabelByValue(value) ?: value
        "「$keyLabel」重复用于：${labels.joinToString("、")}"
    }
}

/**
 * 校验按键名是否与已有元素重名。
 *
 * @param text 待校验的按键名
 * @param existingNames 已有元素的非空按键名集合（可包含自身，需通过 [currentText] 排除）
 * @param currentText 当前元素自身的按键名，传此值可避免自检（编辑模式下使用）
 * @return true 表示存在重名（校验失败），false 表示通过
 */
internal fun isDuplicateElementName(
    text: String,
    existingNames: Set<String>,
    currentText: String? = null,
): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if (currentText != null && trimmed == currentText.trim()) return false
    return trimmed in existingNames
}
