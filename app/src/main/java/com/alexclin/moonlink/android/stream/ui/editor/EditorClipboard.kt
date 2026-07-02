package com.alexclin.moonlink.android.stream.ui.editor

/**
 * 跨方案剪贴板 — 存储复制的元素，用于跨方案粘贴。
 */
data class ClipboardData(
    /** 复制的元素 */
    val rootElement: EditorElement,
)

/**
 * 粘贴操作的结果，包含需要插入数据库的元素。
 */
data class PasteResult(
    /** 粘贴后的元素（已分配新 ID） */
    val rootElement: EditorElement,
)

/**
 * 编辑器剪贴板单例。
 *
 * 使用 object 确保所有方案共享同一个剪贴板实例，
 * 用户在方案 A 复制后切换到方案 B 粘贴。
 */
object EditorClipboard {

    private var _data: ClipboardData? = null

    /** 当前剪贴板内容，null 表示剪贴板为空 */
    val data: ClipboardData? get() = _data

    /** 剪贴板是否有内容 */
    val hasData: Boolean get() = _data != null

    /** 将元素复制到剪贴板 */
    fun copy(element: EditorElement) {
        _data = ClipboardData(rootElement = element)
    }

    /** 清空剪贴板 */
    fun clear() {
        _data = null
    }

    /**
     * 将剪贴板内容粘贴到指定方案。
     *
     * 自动为所有元素生成新的 [elementId] 和 [configId]。
     *
     * @param configId 目标方案的 configId
     * @param existingIds 该方案已存在的 elementId 集合，用于分配不重复的 ID
     * @param offsetX  粘贴时相对原位置的 X 偏移
     * @param offsetY  粘贴时相对原位置的 Y 偏移
     * @param layerOffset 图层偏移（默认 +1 避免重叠）
     * @return [PasteResult] 包含所有需要插入数据库的元素，或 null 如果剪贴板为空
     */
    fun paste(
        configId: Long,
        existingIds: Set<Long> = emptySet(),
        offsetX: Int = 30,
        offsetY: Int = 30,
        layerOffset: Int = 1,
    ): PasteResult? {
        val clipboard = _data ?: return null

        // ── 分配不重复的新 ID ──
        var newId = 1L
        val usedIds = existingIds.toMutableSet()
        while (newId in usedIds) newId++

        val finalRoot = clipboard.rootElement.copy(
            elementId = newId,
            configId = configId,
            centralX = clipboard.rootElement.centralX + offsetX,
            centralY = clipboard.rootElement.centralY + offsetY,
            layer = clipboard.rootElement.layer + layerOffset,
        )

        return PasteResult(rootElement = finalRoot)
    }
}
