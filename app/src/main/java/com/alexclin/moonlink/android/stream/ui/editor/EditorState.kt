package com.alexclin.moonlink.android.stream.ui.editor

import android.content.ContentValues
import com.alexclin.moonlink.android.stream.data.ConfigColumns
import com.alexclin.moonlink.android.stream.data.ElementColumns
import com.alexclin.moonlink.android.stream.data.KeymappingDatabaseHelper

// ════════════════════════════════════════════════════════════════════════════
//  元素类型枚举（与旧 Element.java 常量完全对应）
// ════════════════════════════════════════════════════════════════════════════

enum class ElementType(val value: Int, val displayName: String) {
    DIGITAL_COMMON_BUTTON(0, "Common Button"),
    DIGITAL_SWITCH_BUTTON(1, "Switch Button"),
    DIGITAL_MOVABLE_BUTTON(2, "Movable Button"),
    DIGITAL_COMBINE_BUTTON(3, "Combo Key"),
    DIGITAL_PAD(20, "D-Pad"),
    ANALOG_STICK(30, "Analog Stick"),
    DIGITAL_STICK(31, "Digital Stick"),
    INVISIBLE_ANALOG_STICK(32, "Invisible Analog Stick"),
    INVISIBLE_DIGITAL_STICK(33, "Invisible Digital Stick"),
    UNKNOWN(-1, "Unknown");

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Int): ElementType = map[value] ?: UNKNOWN
    }
}

/** 判断该元素类型是否有专属属性可设置（例如组合键、方向键、摇杆等有额外配置面板） */
fun ElementType.hasTypeSpecificProperties(): Boolean = when (this) {
    ElementType.UNKNOWN -> false
    else -> true
}

// ════════════════════════════════════════════════════════════════════════════
//  纯 Compose 数据类（脱离旧 View 系统）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 按键映射元素的纯 Compose 数据模型。
 *
 * 涵盖所有 [ElementType] 的共同属性和类型专属属性。
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

    // ── 圆形模式 ──
    val isCircle: Boolean = false,    // 是否圆形按钮（UI 编辑辅助，持久化在 extraAttributesJson 中）

    // ── 开关按键：长按效果 ──
    val longPressEffect: Boolean = false, // 开关按键长按效果（持久化在 extraAttributesJson 中）

    // ── 通用标志 ──
    val flag1: Int = 0,              // element_flag1: 通用标志

    // ── 扩展 JSON（MovableButton 触控板模式等） ──
    val extraAttributesJson: String = "{}",
)

// ════════════════════════════════════════════════════════════════════════════
//  编辑器状态 — 数据库读写层
// ════════════════════════════════════════════════════════════════════════════

/**
 * 纯 Compose 编辑器的状态管理 + 数据库 CRUD 层。
 *
 * 通过 [KeymappingDatabaseHelper] 读写旧 Crown 系统的 element 表和 config 表，
 * 与旧代码数据完全互通。
 *
 * @param context Android 上下文
 * @param configId 当前编辑的按键方案 ID
 */
class EditorState(
    private val db: KeymappingDatabaseHelper,
    val configId: Long,
) {

    // ═══════════════════════════════════════════════════════════════════════
    // 读取
    // ═══════════════════════════════════════════════════════════════════════

    /** 加载当前方案的所有元素（自动过滤已被删除的元素类型，如组按键、轮盘按键） */
    fun loadElements(): List<EditorElement> {
        val ids = db.queryAllElementIds(configId) ?: return emptyList()
        return ids.mapNotNull { id ->
            val attrs = db.queryAllElementAttributes(configId, id)
            if (attrs.isEmpty()) null else attrs.toEditorElement()
        }.filter { it.type != ElementType.UNKNOWN }
    }

    /** 加载单个元素（若为已删除的类型则返回 null） */
    fun loadElement(elementId: Long): EditorElement? {
        val attrs = db.queryAllElementAttributes(configId, elementId)
        if (attrs.isEmpty()) return null
        val el = attrs.toEditorElement()
        return if (el.type == ElementType.UNKNOWN) null else el
    }

    /** 读取方案的 config 属性 */
    fun getConfigName(): String {
        return db.queryConfigAttribute(configId, ConfigColumns.COLUMN_STRING_CONFIG_NAME, "Key Scheme") as? String
            ?: "Key Scheme"
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 写入
    // ═══════════════════════════════════════════════════════════════════════

    /** 更新元素属性 */
    fun saveElement(element: EditorElement) {
        db.updateElement(configId, element.elementId, element.toContentValues())
    }

    /** 插入新元素（自动分配不重复的 elementId，从 1 开始） */
    fun addElement(element: EditorElement) {
        val existingIds = db.queryAllElementIds(configId).toSet()
        var newId = 1L
        while (newId in existingIds) newId++
        db.insertElement(element.copy(elementId = newId).toContentValues())
    }

    /** 插入新元素（通过 ContentValues 快捷创建） */
    fun addElement(cv: ContentValues) {
        val existingIds = db.queryAllElementIds(configId).toSet()
        var newId = 1L
        while (newId in existingIds) newId++
        cv.put(ElementColumns.COLUMN_LONG_ELEMENT_ID, newId)
        db.insertElement(cv)
    }

    /** 删除元素 */
    fun deleteElement(elementId: Long) {
        db.deleteElement(configId, elementId)
    }

    /** 批量插入（导入用，自动分配不重复 elementId，从 1 开始） */
    fun addElements(elements: List<EditorElement>) {
        val existingIds = db.queryAllElementIds(configId).toSet()
        var counter = 1L
        while (counter in existingIds) counter++
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
        val isPad = type == ElementType.DIGITAL_PAD
        val isStick = type in listOf(
            ElementType.ANALOG_STICK, ElementType.DIGITAL_STICK,
            ElementType.INVISIBLE_ANALOG_STICK, ElementType.INVISIBLE_DIGITAL_STICK,
        )
        val isInvisible = type in listOf(
            ElementType.INVISIBLE_ANALOG_STICK, ElementType.INVISIBLE_DIGITAL_STICK,
        )
        val isAnalog = type in listOf(
            ElementType.ANALOG_STICK, ElementType.INVISIBLE_ANALOG_STICK,
        )

        return EditorElement(
            elementId = 0L,
            configId = configId,
            type = type,
            text = "",
            value = when (type) {
                ElementType.DIGITAL_COMMON_BUTTON -> "k29"
                ElementType.DIGITAL_SWITCH_BUTTON -> "k29"
                ElementType.DIGITAL_MOVABLE_BUTTON -> "k29"
                ElementType.DIGITAL_COMBINE_BUTTON -> "k29"
                ElementType.ANALOG_STICK, ElementType.INVISIBLE_ANALOG_STICK -> "LS"
                else -> ""
            },
            // 方向值（默认：DIGITAL_PAD=方向键↑↓←→, DIGITAL_STICK=WASD+Shift）
            upValue = when (type) {
                ElementType.DIGITAL_PAD -> "k19"
                ElementType.DIGITAL_STICK, ElementType.INVISIBLE_DIGITAL_STICK -> "k51"
                else -> ""
            },
            downValue = when (type) {
                ElementType.DIGITAL_PAD -> "k20"
                ElementType.DIGITAL_STICK, ElementType.INVISIBLE_DIGITAL_STICK -> "k47"
                else -> ""
            },
            leftValue = when (type) {
                ElementType.DIGITAL_PAD -> "k21"
                ElementType.DIGITAL_STICK, ElementType.INVISIBLE_DIGITAL_STICK -> "k29"
                else -> ""
            },
            rightValue = when (type) {
                ElementType.DIGITAL_PAD -> "k22"
                ElementType.DIGITAL_STICK, ElementType.INVISIBLE_DIGITAL_STICK -> "k32"
                else -> ""
            },
            middleValue = when (type) {
                ElementType.ANALOG_STICK, ElementType.INVISIBLE_ANALOG_STICK -> "g64"
                ElementType.DIGITAL_STICK, ElementType.INVISIBLE_DIGITAL_STICK -> "k59"
                else -> ""
            },
            width = when {
                isPad -> 300
                isInvisible -> 400
                isStick -> 200
                else -> 100
            },
            height = when {
                isPad -> 300
                isInvisible -> 400
                isStick -> 200
                else -> 100
            },
            centralX = when {
                isStick -> 250
                else -> 100
            },
            centralY = when {
                isStick -> 250
                else -> 100
            },
            layer = when {
                isInvisible -> 45
                else -> 50
            },
            normalColor = 0xF0888888.toInt(),
            pressedColor = 0xF00000FF.toInt(),
            backgroundColor = 0x00FFFFFF,
            normalTextColor = 0xFFFFFFFF.toInt(),
            pressedTextColor = 0xFFCCCCCC.toInt(),
            textSizePercent = when (type) {
                ElementType.DIGITAL_COMMON_BUTTON,
                ElementType.DIGITAL_SWITCH_BUTTON,
                ElementType.DIGITAL_MOVABLE_BUTTON -> 50
                else -> 25
            },
            thick = 5,
            sense = when {
                isStick -> 30
                else -> 100
            },
            radius = when {
                isStick -> 100
                else -> 0
            },
            flag1 = 0,
            extraAttributesJson = when (type) {
                ElementType.DIGITAL_MOVABLE_BUTTON -> """{"isTrackpadMode":false}"""
                else -> "{}"
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  类型安全转换工具
// ════════════════════════════════════════════════════════════════════════════

/** 将 DB 返回的 Map<String, Object> 安全转换为 Int */
fun Map<String, Any?>.optInt(key: String, default: Int = 0): Int {
    return when (val v = this[key]) {
        is Long -> v.toInt()
        is Int -> v
        is String -> v.toIntOrNull() ?: default
        else -> default
    }
}

/** 将 DB 返回的 Map<String, Object> 安全转换为 Long */
fun Map<String, Any?>.optLong(key: String, default: Long = 0L): Long {
    return when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is String -> v.toLongOrNull() ?: default
        else -> default
    }
}

/** 将 DB 返回的 Map<String, Object> 安全转换为 String */
fun Map<String, Any?>.optString(key: String, default: String = ""): String {
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
 * DB 返回约定（来自 [KeymappingDatabaseHelper.queryAllElementAttributes]）：
 * - INTEGER 列 → `Long`
 * - TEXT 列 → `String`
 * - FLOAT 列 → `Double`
 * - NULL 列 → 不在 map 中
 */
fun Map<String, Any?>.toEditorElement(): EditorElement {
    return EditorElement(
        elementId = this.optLong(ElementColumns.COLUMN_LONG_ELEMENT_ID),
        configId = this.optLong(ElementColumns.COLUMN_LONG_CONFIG_ID),
        type = ElementType.fromValue(this.optInt(ElementColumns.COLUMN_INT_ELEMENT_TYPE)),

        text = this.optString(ElementColumns.COLUMN_STRING_ELEMENT_TEXT),
        value = this.optString(ElementColumns.COLUMN_STRING_ELEMENT_VALUE),

        centralX = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_CENTRAL_X, 100),
        centralY = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_CENTRAL_Y, 100),
        width = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_WIDTH, 100),
        height = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_HEIGHT, 100),
        layer = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_LAYER, 50),

        radius = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_RADIUS),
        opacity = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_OPACITY, 100),
        thick = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_THICK, 5),
        normalColor = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_NORMAL_COLOR, 0xF0888888.toInt()),
        pressedColor = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_PRESSED_COLOR, 0xF00000FF.toInt()),
        backgroundColor = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_BACKGROUND_COLOR, 0x00FFFFFF),
        normalTextColor = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, 0xFFFFFFFF.toInt()),
        pressedTextColor = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, 0xFFCCCCCC.toInt()),
        textSizePercent = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, 25),

        mode = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_MODE),
        sense = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_SENSE, 100),

        middleValue = this.optString(ElementColumns.COLUMN_STRING_ELEMENT_MIDDLE_VALUE),
        upValue = this.optString(ElementColumns.COLUMN_STRING_ELEMENT_UP_VALUE),
        downValue = this.optString(ElementColumns.COLUMN_STRING_ELEMENT_DOWN_VALUE),
        leftValue = this.optString(ElementColumns.COLUMN_STRING_ELEMENT_LEFT_VALUE),
        rightValue = this.optString(ElementColumns.COLUMN_STRING_ELEMENT_RIGHT_VALUE),

        flag1 = this.optInt(ElementColumns.COLUMN_INT_ELEMENT_FLAG1),
        extraAttributesJson = this.optString(ElementColumns.COLUMN_STRING_EXTRA_ATTRIBUTES, "{}"),
    ).let { el ->
        // 从 extraAttributesJson 反序列化 isCircle 和 longPressEffect
        val extras = try {
            org.json.JSONObject(el.extraAttributesJson)
        } catch (_: Exception) {
            null
        }
        val isCircle = extras?.optBoolean("isCircle", false) ?: false
        val longPressEffect = extras?.optBoolean("longPressEffect", false) ?: false
        el.copy(isCircle = isCircle, longPressEffect = longPressEffect)
    }
}

/**
 * 将 [EditorElement] 转换为 [ContentValues] 以写入数据库。
 *
 * 注意：
 * - 不包含 `_id`（SQLite 自增主键）
 * - INTEGER 列通过 [ContentValues.put(key, Long)] 写入
 * - TEXT 列通过 [ContentValues.put(key, String)] 写入
 */    internal fun EditorElement.toContentValues(): ContentValues {
        // 序列化 isCircle 和 longPressEffect 到 extraAttributesJson
        val mergedJson = try {
            val json = org.json.JSONObject(extraAttributesJson)
            json.put("isCircle", isCircle)
            json.put("longPressEffect", longPressEffect)
            json.toString()
        } catch (_: Exception) {
            extraAttributesJson
        }

    return ContentValues().apply {
        put(ElementColumns.COLUMN_LONG_ELEMENT_ID, elementId)
        put(ElementColumns.COLUMN_LONG_CONFIG_ID, configId)
        put(ElementColumns.COLUMN_INT_ELEMENT_TYPE, type.value.toLong())
        put(ElementColumns.COLUMN_STRING_ELEMENT_TEXT, text)
        put(ElementColumns.COLUMN_STRING_ELEMENT_VALUE, value)

        put(ElementColumns.COLUMN_INT_ELEMENT_CENTRAL_X, centralX.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_CENTRAL_Y, centralY.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_WIDTH, width.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_HEIGHT, height.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_LAYER, layer.toLong())

        put(ElementColumns.COLUMN_INT_ELEMENT_RADIUS, radius.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_OPACITY, opacity.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_THICK, thick.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_NORMAL_COLOR, normalColor.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_PRESSED_COLOR, pressedColor.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_BACKGROUND_COLOR, backgroundColor.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, normalTextColor.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, pressedTextColor.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, textSizePercent.toLong())

        put(ElementColumns.COLUMN_INT_ELEMENT_MODE, mode.toLong())
        put(ElementColumns.COLUMN_INT_ELEMENT_SENSE, sense.toLong())

        put(ElementColumns.COLUMN_STRING_ELEMENT_MIDDLE_VALUE, middleValue)
        put(ElementColumns.COLUMN_STRING_ELEMENT_UP_VALUE, upValue)
        put(ElementColumns.COLUMN_STRING_ELEMENT_DOWN_VALUE, downValue)
        put(ElementColumns.COLUMN_STRING_ELEMENT_LEFT_VALUE, leftValue)
        put(ElementColumns.COLUMN_STRING_ELEMENT_RIGHT_VALUE, rightValue)

        put(ElementColumns.COLUMN_INT_ELEMENT_FLAG1, flag1.toLong())
        put(ElementColumns.COLUMN_STRING_EXTRA_ATTRIBUTES, mergedJson)
    }
}
