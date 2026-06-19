package com.alexclin.moonlink.stream.ui.editor

import android.content.ContentValues
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.element.Element
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper

// ════════════════════════════════════════════════════════════════════════════
//  元素类型枚举（与旧 Element.java 常量完全对应）
// ════════════════════════════════════════════════════════════════════════════

enum class ElementType(val value: Int, val displayName: String) {
    DIGITAL_COMMON_BUTTON(0, "普通按键"),
    DIGITAL_SWITCH_BUTTON(1, "开关按键"),
    DIGITAL_MOVABLE_BUTTON(2, "可移动按键"),
    DIGITAL_COMBINE_BUTTON(3, "组合键"),
    GROUP_BUTTON(4, "组按键"),
    DIGITAL_PAD(20, "方向键"),
    ANALOG_STICK(30, "模拟摇杆"),
    DIGITAL_STICK(31, "数字摇杆"),
    INVISIBLE_ANALOG_STICK(32, "隐形模拟摇杆"),
    INVISIBLE_DIGITAL_STICK(33, "隐形数字摇杆"),
    SIMPLIFY_PERFORMANCE(50, "性能信息"),
    WHEEL_PAD(54, "滚轮面板"),
    UNKNOWN(-1, "未知");

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Int): ElementType = map[value] ?: UNKNOWN
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  纯 Compose 数据类（脱离旧 View 系统）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 按键映射元素的纯 Compose 数据模型。
 *
 * 涵盖所有 12 种 [ElementType] 的共同属性和类型专属属性。
 * 类型专属属性通过 [extraAttributesJson]（JSON 兜底）和可选字段（mode/sense/方向值）表达。
 */
data class EditorElement(
    // ── 身份标识 ──
    val elementId: Long,
    val configId: Long,
    val type: ElementType,

    // ── 显示文字 & 键值 ──
    val text: String = "",
    val value: String = "",

    // ── 位置 & 尺寸（像素坐标） ──
    val centralX: Int = 100,
    val centralY: Int = 100,
    val width: Int = 100,
    val height: Int = 100,
    val layer: Int = 50,

    // ── 外观 ──
    val radius: Int = 0,
    val opacity: Int = 100,         // 0-100
    val thick: Int = 5,             // 边框粗细
    val normalColor: Int = 0xF0888888.toInt(),       // 正常颜色 (ARGB)
    val pressedColor: Int = 0xF00000FF.toInt(),      // 按下颜色
    val backgroundColor: Int = 0x00FFFFFF,    // 背景色
    val normalTextColor: Int = 0xFFFFFFFF.toInt(),    // 正常文字颜色
    val pressedTextColor: Int = 0xFFCCCCCC.toInt(),   // 按下文字颜色
    val textSizePercent: Int = 25,            // 文字占高度百分比 (10-150)

    // ── 类型专属属性（按钮/摇杆模式） ──
    val mode: Int = 0,               // element_mode: 0=按钮, 1=摇杆（MovableButton 用）
    val sense: Int = 100,            // 灵敏度（摇杆/触控板用）

    // ── 方向值（组合键 / 十字键 / 摇杆用） ──
    val middleValue: String = "",
    val upValue: String = "",
    val downValue: String = "",
    val leftValue: String = "",
    val rightValue: String = "",

    // ── 组按键标志 ──
    val flag1: Int = 0,              // element_flag1: 组按键隐藏标志

    // ── 扩展 JSON（WheelPad 分段、MovableButton 触控板模式、GroupButton 额外状态） ──
    val extraAttributesJson: String = "{}",
)

// ════════════════════════════════════════════════════════════════════════════
//  编辑器状态 — 数据库读写层
// ════════════════════════════════════════════════════════════════════════════

/**
 * 纯 Compose 编辑器的状态管理 + 数据库 CRUD 层。
 *
 * 通过 [SuperConfigDatabaseHelper] 读写旧 Crown 系统的 element 表和 config 表，
 * 与旧代码数据完全互通。
 *
 * @param context Android 上下文
 * @param configId 当前编辑的按键方案 ID
 */
class EditorState(
    private val db: SuperConfigDatabaseHelper,
    val configId: Long,
) {

    // ═══════════════════════════════════════════════════════════════════════
    // 读取
    // ═══════════════════════════════════════════════════════════════════════

    /** 加载当前方案的所有元素 */
    fun loadElements(): List<EditorElement> {
        val ids = db.queryAllElementIds(configId) ?: return emptyList()
        return ids.mapNotNull { id ->
            val attrs = db.queryAllElementAttributes(configId, id)
            if (attrs.isEmpty()) null else attrs.toEditorElement()
        }
    }

    /** 加载单个元素 */
    fun loadElement(elementId: Long): EditorElement? {
        val attrs = db.queryAllElementAttributes(configId, elementId)
        return if (attrs.isEmpty()) null else attrs.toEditorElement()
    }

    /** 读取方案的 config 属性 */
    fun getConfigName(): String {
        return db.queryConfigAttribute(configId, PageConfigController.COLUMN_STRING_CONFIG_NAME, "按键方案") as? String
            ?: "按键方案"
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 写入
    // ═══════════════════════════════════════════════════════════════════════

    /** 更新元素属性 */
    fun saveElement(element: EditorElement) {
        db.updateElement(configId, element.elementId, element.toContentValues())
    }

    /** 插入新元素 */
    fun addElement(element: EditorElement) {
        db.insertElement(element.toContentValues())
    }

    /** 插入新元素（通过 ContentValues 快捷创建） */
    fun addElement(cv: ContentValues) {
        db.insertElement(cv)
    }

    /** 删除元素 */
    fun deleteElement(elementId: Long) {
        db.deleteElement(configId, elementId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 清理
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 通用孤儿引用清理 — 遍历 [elementTypes] 的元素，对其 [EditorElement.value]
     * 按 `,` 拆分为项，用 [itemFilter] 逐项判断是否保留，移除不满足条件的项。
     *
     * @param currentElements 当前在内存中的元素列表
     * @param elementTypes    需要处理的元素类型集合
     * @param shouldSkip      附加跳过条件（返回 true 则跳过该元素，不解析 value）
     * @param itemFilter      逐项过滤：(item字符串, 有效ID集合) → true 保留, false 移除
     * @return 清理后的元素列表（内存副本已更新）
     */
    fun cleanupOrphanedRefs(
        currentElements: List<EditorElement>,
        elementTypes: Set<ElementType>,
        shouldSkip: (EditorElement) -> Boolean = { false },
        itemFilter: (item: String, validIds: Set<Long>) -> Boolean,
    ): List<EditorElement> {
        val validIds = currentElements.map { it.elementId }.toSet()
        val result = currentElements.toMutableList()
        var hasChanges = false

        for ((index, el) in currentElements.withIndex()) {
            if (el.type !in elementTypes) continue
            if (el.value.isBlank() || shouldSkip(el)) continue

            val items = el.value.split(",")
            val cleaned = items.filter { item -> itemFilter(item, validIds) }

            if (cleaned.size < items.size) {
                val newValue = cleaned.joinToString(",")
                val updated = el.copy(value = newValue)
                result[index] = updated
                db.updateElement(configId, el.elementId, updated.toContentValues())
                hasChanges = true
            }
        }

        return if (hasChanges) result else currentElements
    }

    /**
     * 清理 GroupButton 的孤儿子元素 ID 引用。
     * 委托给 [cleanupOrphanedRefs]。
     */
    fun cleanupOrphanedGroupButtonRefs(currentElements: List<EditorElement>): List<EditorElement> =
        cleanupOrphanedRefs(
            currentElements,
            elementTypes = setOf(ElementType.GROUP_BUTTON),
            shouldSkip = { it.value == "-1" },
            itemFilter = { item, validIds ->
                val id = item.trim().toLongOrNull()
                id == null || id == -1L || id in validIds
            },
        )

    /**
     * 清理 WheelPad 的孤儿 GroupButton 引用（`gb{id}` 格式）。
     * 委托给 [cleanupOrphanedRefs]。
     */
    fun cleanupOrphanedWheelPadRefs(currentElements: List<EditorElement>): List<EditorElement> =
        cleanupOrphanedRefs(
            currentElements,
            elementTypes = setOf(ElementType.WHEEL_PAD),
            itemFilter = { item, validIds ->
                val valuePart = item.substringBefore("|").trim()
                if (valuePart.startsWith("gb")) {
                    val groupId = valuePart.substring(2).toLongOrNull()
                    groupId != null && groupId in validIds
                } else {
                    true
                }
            },
        )

    /** 批量插入（导入用） */
    fun addElements(elements: List<EditorElement>) {
        var counter = System.currentTimeMillis()
        for (el in elements) {
            val cv = el.copy(elementId = counter).toContentValues()
            db.insertElement(cv)
            counter++
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 默认值
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 创建一个指定类型的新元素，使用合理的默认值。
     * elementId 为占位符，insert 时由数据库或调用方分配。
     */
    fun createDefaultElement(type: ElementType): EditorElement {
        return EditorElement(
            elementId = 0L, // 占位，addElement 前需分配
            configId = configId,
            type = type,
            text = when (type) {
                ElementType.DIGITAL_COMMON_BUTTON -> "A"
                ElementType.DIGITAL_SWITCH_BUTTON -> "B"
                ElementType.DIGITAL_MOVABLE_BUTTON -> "M"
                ElementType.DIGITAL_COMBINE_BUTTON -> "组合键"
                ElementType.GROUP_BUTTON -> "GROUP"
                ElementType.DIGITAL_PAD -> "方向"
                ElementType.ANALOG_STICK -> ""
                else -> "按键"
            },
            value = when (type) {
                ElementType.DIGITAL_COMMON_BUTTON -> "k29"
                ElementType.DIGITAL_SWITCH_BUTTON -> "k29"
                ElementType.DIGITAL_MOVABLE_BUTTON -> "k29"
                ElementType.DIGITAL_COMBINE_BUTTON -> "k29"
                else -> ""
            },
            width = 100,
            height = 100,
            centralX = 100,
            centralY = 100,
            layer = 50,
            normalColor = 0xF0888888.toInt(),
            pressedColor = 0xF00000FF.toInt(),
            backgroundColor = 0x00FFFFFF,
            normalTextColor = 0xFFFFFFFF.toInt(),
            pressedTextColor = 0xFFCCCCCC.toInt(),
            textSizePercent = 25,
            thick = 5,
            sense = 100,
            extraAttributesJson = when (type) {
                ElementType.DIGITAL_MOVABLE_BUTTON -> """{"isTrackpadMode":false}"""
                ElementType.GROUP_BUTTON -> """{"movableInNormalMode":false,"userHasManuallySet":false,"isPermanentlyIndependent":false}"""
                else -> "{}"
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  类型安全转换工具
// ════════════════════════════════════════════════════════════════════════════

/** 将 DB 返回的 Map<String, Object> 安全转换为 Int */
private fun Map<String, Any?>.optInt(key: String, default: Int = 0): Int {
    return when (val v = this[key]) {
        is Long -> v.toInt()
        is Int -> v
        is String -> v.toIntOrNull() ?: default
        else -> default
    }
}

/** 将 DB 返回的 Map<String, Object> 安全转换为 Long */
private fun Map<String, Any?>.optLong(key: String, default: Long = 0L): Long {
    return when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is String -> v.toLongOrNull() ?: default
        else -> default
    }
}

/** 将 DB 返回的 Map<String, Object> 安全转换为 String */
private fun Map<String, Any?>.optString(key: String, default: String = ""): String {
    return when (val v = this[key]) {
        is String -> v
        else -> default
    }
}

/** 将 DB 返回的 Map<String, Object> 安全转换为 Boolean（DB 存为 String "true"/"false"） */
@Suppress("unused")
private fun Map<String, Any?>.optBoolean(key: String, default: Boolean = false): Boolean {
    return when (val v = this[key]) {
        is String -> v.toBooleanStrictOrNull() ?: default
        is Boolean -> v
        else -> default
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  转换函数
// ════════════════════════════════════════════════════════════════════════════

/**
 * 将 DB 查询结果 [Map<String, Object>] 转换为 [EditorElement]。
 *
 * DB 返回约定（来自 [SuperConfigDatabaseHelper.queryAllElementAttributes]）：
 * - INTEGER 列 → `Long`
 * - TEXT 列 → `String`
 * - FLOAT 列 → `Double`
 * - NULL 列 → 不在 map 中
 */
internal fun Map<String, Any?>.toEditorElement(): EditorElement {
    return EditorElement(
        elementId = this.optLong(Element.COLUMN_LONG_ELEMENT_ID),
        configId = this.optLong(Element.COLUMN_LONG_CONFIG_ID),
        type = ElementType.fromValue(this.optInt(Element.COLUMN_INT_ELEMENT_TYPE)),

        text = this.optString(Element.COLUMN_STRING_ELEMENT_TEXT),
        value = this.optString(Element.COLUMN_STRING_ELEMENT_VALUE),

        centralX = this.optInt(Element.COLUMN_INT_ELEMENT_CENTRAL_X, 100),
        centralY = this.optInt(Element.COLUMN_INT_ELEMENT_CENTRAL_Y, 100),
        width = this.optInt(Element.COLUMN_INT_ELEMENT_WIDTH, 100),
        height = this.optInt(Element.COLUMN_INT_ELEMENT_HEIGHT, 100),
        layer = this.optInt(Element.COLUMN_INT_ELEMENT_LAYER, 50),

        radius = this.optInt(Element.COLUMN_INT_ELEMENT_RADIUS),
        opacity = this.optInt(Element.COLUMN_INT_ELEMENT_OPACITY, 100),
        thick = this.optInt(Element.COLUMN_INT_ELEMENT_THICK, 5),
        normalColor = this.optInt(Element.COLUMN_INT_ELEMENT_NORMAL_COLOR, 0xF0888888.toInt()),
        pressedColor = this.optInt(Element.COLUMN_INT_ELEMENT_PRESSED_COLOR, 0xF00000FF.toInt()),
        backgroundColor = this.optInt(Element.COLUMN_INT_ELEMENT_BACKGROUND_COLOR, 0x00FFFFFF),
        normalTextColor = this.optInt(Element.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, 0xFFFFFFFF.toInt()),
        pressedTextColor = this.optInt(Element.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, 0xFFCCCCCC.toInt()),
        textSizePercent = this.optInt(Element.COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, 25),

        mode = this.optInt(Element.COLUMN_INT_ELEMENT_MODE),
        sense = this.optInt(Element.COLUMN_INT_ELEMENT_SENSE, 100),

        middleValue = this.optString(Element.COLUMN_STRING_ELEMENT_MIDDLE_VALUE),
        upValue = this.optString(Element.COLUMN_STRING_ELEMENT_UP_VALUE),
        downValue = this.optString(Element.COLUMN_STRING_ELEMENT_DOWN_VALUE),
        leftValue = this.optString(Element.COLUMN_STRING_ELEMENT_LEFT_VALUE),
        rightValue = this.optString(Element.COLUMN_STRING_ELEMENT_RIGHT_VALUE),

        flag1 = this.optInt(Element.COLUMN_INT_ELEMENT_FLAG1),
        extraAttributesJson = this.optString(Element.COLUMN_STRING_EXTRA_ATTRIBUTES, "{}"),
    )
}

/**
 * 将 [EditorElement] 转换为 [ContentValues] 以写入数据库。
 *
 * 注意：
 * - 不包含 `_id`（SQLite 自增主键）
 * - INTEGER 列通过 [ContentValues.put(key, Long)] 写入
 * - TEXT 列通过 [ContentValues.put(key, String)] 写入
 */
internal fun EditorElement.toContentValues(): ContentValues {
    return ContentValues().apply {
        put(Element.COLUMN_LONG_ELEMENT_ID, elementId)
        put(Element.COLUMN_LONG_CONFIG_ID, configId)
        put(Element.COLUMN_INT_ELEMENT_TYPE, type.value.toLong())
        put(Element.COLUMN_STRING_ELEMENT_TEXT, text)
        put(Element.COLUMN_STRING_ELEMENT_VALUE, value)

        put(Element.COLUMN_INT_ELEMENT_CENTRAL_X, centralX.toLong())
        put(Element.COLUMN_INT_ELEMENT_CENTRAL_Y, centralY.toLong())
        put(Element.COLUMN_INT_ELEMENT_WIDTH, width.toLong())
        put(Element.COLUMN_INT_ELEMENT_HEIGHT, height.toLong())
        put(Element.COLUMN_INT_ELEMENT_LAYER, layer.toLong())

        put(Element.COLUMN_INT_ELEMENT_RADIUS, radius.toLong())
        put(Element.COLUMN_INT_ELEMENT_OPACITY, opacity.toLong())
        put(Element.COLUMN_INT_ELEMENT_THICK, thick.toLong())
        put(Element.COLUMN_INT_ELEMENT_NORMAL_COLOR, normalColor.toLong())
        put(Element.COLUMN_INT_ELEMENT_PRESSED_COLOR, pressedColor.toLong())
        put(Element.COLUMN_INT_ELEMENT_BACKGROUND_COLOR, backgroundColor.toLong())
        put(Element.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, normalTextColor.toLong())
        put(Element.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, pressedTextColor.toLong())
        put(Element.COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, textSizePercent.toLong())

        put(Element.COLUMN_INT_ELEMENT_MODE, mode.toLong())
        put(Element.COLUMN_INT_ELEMENT_SENSE, sense.toLong())

        put(Element.COLUMN_STRING_ELEMENT_MIDDLE_VALUE, middleValue)
        put(Element.COLUMN_STRING_ELEMENT_UP_VALUE, upValue)
        put(Element.COLUMN_STRING_ELEMENT_DOWN_VALUE, downValue)
        put(Element.COLUMN_STRING_ELEMENT_LEFT_VALUE, leftValue)
        put(Element.COLUMN_STRING_ELEMENT_RIGHT_VALUE, rightValue)

        put(Element.COLUMN_INT_ELEMENT_FLAG1, flag1.toLong())
        put(Element.COLUMN_STRING_EXTRA_ATTRIBUTES, extraAttributesJson)
    }
}
