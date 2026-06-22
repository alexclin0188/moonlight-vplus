package com.alexclin.moonlink.android.stream.ui.editor

/**
 * 跨方案剪贴板 — 存储复制的元素及其子元素，用于跨方案粘贴。
 *
 * 支持两种场景：
 * 1. **GroupButton + 子元素**：复制组按键时，同时捕获其所有子元素
 * 2. **普通元素**：复制单个元素（不含子元素关联）
 */
data class ClipboardData(
    /** 复制的根元素（被选中的那个元素） */
    val rootElement: EditorElement,
    /** 所有关联的子元素（GroupButton 的子元素，或空） */
    val childElements: List<EditorElement>,
) {
    /** 是否为 GroupButton 复制（包含子元素） */
    val isGroupWithChildren: Boolean get() =
        rootElement.type == ElementType.GROUP_BUTTON && childElements.isNotEmpty()
}

/**
 * 粘贴操作的结果，包含所有需要插入数据库的元素。
 */
data class PasteResult(
    /** 粘贴后的根元素（已分配新 ID、已更新子元素引用） */
    val rootElement: EditorElement,
    /** 粘贴后的子元素列表（已分配新 ID 和 configId） */
    val childElements: List<EditorElement>,
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

    /** 将元素及其子元素复制到剪贴板 */
    fun copy(element: EditorElement, children: List<EditorElement> = emptyList()) {
        _data = ClipboardData(
            rootElement = element,
            childElements = children,
        )
    }

    /** 清空剪贴板 */
    fun clear() {
        _data = null
    }

    /**
     * 将剪贴板内容粘贴到指定方案。
     *
     * 自动为所有元素生成新的 [elementId] 和 [configId]，
     * 并更新 GroupButton 的 [EditorElement.value] 字段以正确引用新的子元素 ID。
     *
     * @param configId 目标方案的 configId
     * @param offsetX  粘贴时相对原位置的 X 偏移
     * @param offsetY  粘贴时相对原位置的 Y 偏移
     * @param layerOffset 图层偏移（默认 +1 避免重叠）
     * @return [PasteResult] 包含所有需要插入数据库的元素，或 null 如果剪贴板为空
     */
    fun paste(
        configId: Long,
        offsetX: Int = 30,
        offsetY: Int = 30,
        layerOffset: Int = 1,
    ): PasteResult? {
        val clipboard = _data ?: return null
        val baseTime = System.currentTimeMillis()

        // ── 为所有元素分配新 ID ──
        val oldToNewId = mutableMapOf<Long, Long>()

        // 根元素
        val newRootId = baseTime
        oldToNewId[clipboard.rootElement.elementId] = newRootId

        // 子元素
        var idCounter = baseTime + 1
        val newChildElements = clipboard.childElements.map { child ->
            val newId = idCounter++
            oldToNewId[child.elementId] = newId
            child.copy(
                elementId = newId,
                configId = configId,
                centralX = child.centralX + offsetX,
                centralY = child.centralY + offsetY,
            )
        }

        // ── 构建新的根元素 ──
        val maxLayer = (clipboard.childElements.maxOfOrNull { it.layer } ?: clipboard.rootElement.layer) + layerOffset

        val newRoot = clipboard.rootElement.copy(
            elementId = newRootId,
            configId = configId,
            centralX = clipboard.rootElement.centralX + offsetX,
            centralY = clipboard.rootElement.centralY + offsetY,
            layer = maxLayer,
        )

        // ── 如果是 GroupButton，更新 value 字段中的子元素 ID 引用 ──
        val finalRoot = if (newRoot.type == ElementType.GROUP_BUTTON) {
            val oldChildIds = newRoot.value
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { it != -1L }

            if (oldChildIds.isNotEmpty()) {
                val newValue = "-1," + oldChildIds.mapNotNull { oldId ->
                    oldToNewId[oldId]?.toString()
                }.joinToString(",")
                newRoot.copy(value = newValue)
            } else {
                newRoot
            }
        } else {
            newRoot
        }

        return PasteResult(
            rootElement = finalRoot,
            childElements = newChildElements,
        )
    }
}
