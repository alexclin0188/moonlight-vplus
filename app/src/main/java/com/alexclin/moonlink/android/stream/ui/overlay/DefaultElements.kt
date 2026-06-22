package com.alexclin.moonlink.android.stream.ui.overlay

import android.content.ContentValues
import com.limelight.binding.input.advance_setting.element.Element

/**
 * 内置方案默认元素生成。
 *
 * 布局设计参考标准 Xbox/PS 手柄按键排布：
 * - 左半区：左摇杆（上）、D-Pad（下）
 * - 右半区：功能键钻石排布 Y/A/X/B（下），右摇杆（上）
 * - 顶部：LT/LB（左）、RB/RT（右）
 * - 底部中央：BACK、START
 *
 * 坐标系统：128×72 网格（16:9），实际像素 = 网格值 × screenHeight / 72。
 *
 * 不依赖 [PreferenceConfiguration]，所有选项通过参数传入。
 */
object DefaultElements {

    // ── 全新布局常量 ──

    // 左摇杆 (左上)
    private const val ANALOG_L_CX = 20
    private const val ANALOG_L_CY = 24
    private const val ANALOG_SIZE = 22

    // D-Pad (左下，八方向)
    private const val DPAD_CX = 20
    private const val DPAD_CY = 54
    private const val DPAD_SIZE = 28

    // 右摇杆 (右下) — 与 D-Pad 水平对齐
    private const val ANALOG_R_CX = 108
    private const val ANALOG_R_CY = 58

    // 功能键钻石排布，中心 (108, 28)
    private const val FACE_CX = 108
    private const val FACE_CY = 28
    private const val BUTTON_SIZE = 10
    // Y(108,18)  A(108,38)  X(98,28)  B(118,28)

    // 扳机 / 肩键 (顶部) — 沿 D-Pad / XAYB 中心竖直线对称
    private const val TRIGGER_WIDTH = 14
    private const val TRIGGER_HEIGHT = 8
    private const val TRIGGER_Y = 5
    private const val LT_CX = 10
    private const val LB_CX = 30
    private const val RB_CX = 98
    private const val RT_CX = 118

    // 中心按键
    private const val BACK_CX = 50
    private const val START_CX = 78
    private const val START_BACK_WIDTH = 14
    private const val START_BACK_HEIGHT = 8
    private const val START_BACK_Y = 68

    // L3/R3 only 模式
    private const val L3_CX = 14
    private const val R3_CX = 108
    private const val L3_R3_Y = 56

    /** 网格单元：高度按 72 格、宽高比 16:9 设计 */
    private const val GRID_HEIGHT = 72

    /** 比例换算函数 */
    private fun Int.screenScale(height: Int): Int =
        Math.round(height / GRID_HEIGHT.toFloat() * this.toFloat())

    /**
     * 生成内置方案的按键元素 ContentValues 列表。
     *
     * @param screenWidthPx      屏幕宽度 (px)
     * @param screenHeightPx     屏幕高度 (px)
     * @param onlyL3R3           仅显示 L3/R3（精简模式）
     * @param halfHeightPortrait 竖屏半高模式
     * @param isPortrait         是否竖屏
     */
    fun createDefaultElements(
        screenWidthPx: Int,
        screenHeightPx: Int,
        onlyL3R3: Boolean = false,
        halfHeightPortrait: Boolean = false,
        isPortrait: Boolean = false,
    ): List<ContentValues> {
        var height = screenHeightPx
        var baseYUnit = 0

        // 竖屏半高模式
        if (halfHeightPortrait && isPortrait) {
            height /= 2
            baseYUnit = 72
        }

        val rightDisplacement = screenWidthPx - height * 16 / 9
        val result = mutableListOf<ContentValues>()

        val normalColor = 0xF0888888L
        val pressedColor = 0xF00000FFL
        val bgColor = 0x00FFFFFFL
        val textColor = 0xFFFFFFFFL
        val pressedTextColor = 0xFFCCCCCCL

        fun cv(
            elementId: Long, type: Int, text: String, value: String,
            cx: Int, cy: Int, w: Int, h: Int,
            layer: Int = 50,
            middleValue: String = "", upValue: String = "",
            downValue: String = "", leftValue: String = "", rightValue: String = "",
            radius: Int = 0,
        ) = ContentValues().apply {
            put(Element.COLUMN_LONG_ELEMENT_ID, elementId)
            put(Element.COLUMN_INT_ELEMENT_TYPE, type.toLong())
            put(Element.COLUMN_STRING_ELEMENT_TEXT, text)
            put(Element.COLUMN_STRING_ELEMENT_VALUE, value)
            put(Element.COLUMN_INT_ELEMENT_CENTRAL_X, cx.toLong())
            put(Element.COLUMN_INT_ELEMENT_CENTRAL_Y, cy.toLong())
            put(Element.COLUMN_INT_ELEMENT_WIDTH, w.toLong())
            put(Element.COLUMN_INT_ELEMENT_HEIGHT, h.toLong())
            put(Element.COLUMN_INT_ELEMENT_LAYER, layer.toLong())
            put(Element.COLUMN_INT_ELEMENT_RADIUS, radius.toLong())
            put(Element.COLUMN_INT_ELEMENT_OPACITY, 100L)
            put(Element.COLUMN_INT_ELEMENT_THICK, 5L)
            put(Element.COLUMN_INT_ELEMENT_NORMAL_COLOR, normalColor)
            put(Element.COLUMN_INT_ELEMENT_PRESSED_COLOR, pressedColor)
            put(Element.COLUMN_INT_ELEMENT_BACKGROUND_COLOR, bgColor)
            put(Element.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, textColor)
            put(Element.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, pressedTextColor)
            put(Element.COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, 25L)
            put(Element.COLUMN_STRING_ELEMENT_MIDDLE_VALUE, middleValue)
            put(Element.COLUMN_STRING_ELEMENT_UP_VALUE, upValue)
            put(Element.COLUMN_STRING_ELEMENT_DOWN_VALUE, downValue)
            put(Element.COLUMN_STRING_ELEMENT_LEFT_VALUE, leftValue)
            put(Element.COLUMN_STRING_ELEMENT_RIGHT_VALUE, rightValue)
        }

        if (!onlyL3R3) {
            var eid = 1L

            // ── D-Pad (左下) ──
            result.add(cv(
                elementId = eid++, type = 20, text = "", value = "",
                cx = DPAD_CX.screenScale(height),
                cy = (DPAD_CY + baseYUnit).screenScale(height),
                w = DPAD_SIZE.screenScale(height),
                h = DPAD_SIZE.screenScale(height),
                radius = 6,
                upValue = "k19", downValue = "k20",
                leftValue = "k21", rightValue = "k22",
                layer = 60,
            ))

            // ── 功能键钻石排布 (右下) ──
            // Y — 上
            result.add(cv(elementId = eid++, type = 0, text = "Y", value = "k0x8000",
                cx = FACE_CX.screenScale(height) + rightDisplacement,
                cy = (FACE_CY - BUTTON_SIZE + baseYUnit).screenScale(height),
                w = BUTTON_SIZE.screenScale(height), h = BUTTON_SIZE.screenScale(height)))
            // A — 下
            result.add(cv(elementId = eid++, type = 0, text = "A", value = "k0x1000",
                cx = FACE_CX.screenScale(height) + rightDisplacement,
                cy = (FACE_CY + BUTTON_SIZE + baseYUnit).screenScale(height),
                w = BUTTON_SIZE.screenScale(height), h = BUTTON_SIZE.screenScale(height)))
            // X — 左
            result.add(cv(elementId = eid++, type = 0, text = "X", value = "k0x4000",
                cx = (FACE_CX - BUTTON_SIZE).screenScale(height) + rightDisplacement,
                cy = (FACE_CY + baseYUnit).screenScale(height),
                w = BUTTON_SIZE.screenScale(height), h = BUTTON_SIZE.screenScale(height)))
            // B — 右
            result.add(cv(elementId = eid++, type = 0, text = "B", value = "k0x2000",
                cx = (FACE_CX + BUTTON_SIZE).screenScale(height) + rightDisplacement,
                cy = (FACE_CY + baseYUnit).screenScale(height),
                w = BUTTON_SIZE.screenScale(height), h = BUTTON_SIZE.screenScale(height)))

            // ── 扳机 / 肩键 (顶部) ──
            // LT
            result.add(cv(elementId = eid++, type = 0, text = "LT", value = "lt",
                cx = LT_CX.screenScale(height),
                cy = (TRIGGER_Y + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = TRIGGER_HEIGHT.screenScale(height)))
            // RT
            result.add(cv(elementId = eid++, type = 0, text = "RT", value = "rt",
                cx = RT_CX.screenScale(height) + rightDisplacement,
                cy = (TRIGGER_Y + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = TRIGGER_HEIGHT.screenScale(height)))
            // LB
            result.add(cv(elementId = eid++, type = 0, text = "LB", value = "k0x0100",
                cx = LB_CX.screenScale(height),
                cy = (TRIGGER_Y + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = TRIGGER_HEIGHT.screenScale(height)))
            // RB
            result.add(cv(elementId = eid++, type = 0, text = "RB", value = "k0x0200",
                cx = RB_CX.screenScale(height) + rightDisplacement,
                cy = (TRIGGER_Y + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = TRIGGER_HEIGHT.screenScale(height)))

            // ── 左摇杆 (左上，双击 = L3) ──
            result.add(cv(elementId = eid++, type = 30, text = "", value = "",
                cx = ANALOG_L_CX.screenScale(height),
                cy = (ANALOG_L_CY + baseYUnit).screenScale(height),
                w = ANALOG_SIZE.screenScale(height), h = ANALOG_SIZE.screenScale(height),
                radius = ANALOG_SIZE.screenScale(height) / 2,
                leftValue = "a0", rightValue = "a0",
                upValue = "a1", downValue = "a1",
                middleValue = "g64",
                layer = 55))
            // 右摇杆（双击 = R3）
            result.add(cv(elementId = eid++, type = 30, text = "", value = "",
                cx = ANALOG_R_CX.screenScale(height) + rightDisplacement,
                cy = (ANALOG_R_CY + baseYUnit).screenScale(height),
                w = ANALOG_SIZE.screenScale(height), h = ANALOG_SIZE.screenScale(height),
                radius = ANALOG_SIZE.screenScale(height) / 2,
                leftValue = "a2", rightValue = "a2",
                upValue = "a3", downValue = "a3",
                middleValue = "g128",
                layer = 55))

            // ── 中央功能键 ──
            // BACK (SELECT)
            result.add(cv(elementId = eid++, type = 0, text = "BACK", value = "k0x0010",
                cx = BACK_CX.screenScale(height),
                cy = (START_BACK_Y + baseYUnit).screenScale(height),
                w = START_BACK_WIDTH.screenScale(height), h = START_BACK_HEIGHT.screenScale(height)))
            // START
            result.add(cv(elementId = eid++, type = 0, text = "START", value = "k0x0008",
                cx = START_CX.screenScale(height) + rightDisplacement,
                cy = (START_BACK_Y + baseYUnit).screenScale(height),
                w = START_BACK_WIDTH.screenScale(height), h = START_BACK_HEIGHT.screenScale(height)))

            // ── L3 / R3 / GUIDE (居中横排，在 D-Pad 与 BACK 之间) ──
            // L3 — 左 (+11 右移)
            result.add(cv(elementId = eid++, type = 0, text = "L3", value = "k0x0004",
                cx = ((DPAD_CX + BACK_CX) / 2 + 11).screenScale(height),
                cy = ((DPAD_CY + START_BACK_Y) / 2 - 2 + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = 7.screenScale(height)))
            // GUIDE — 中
            result.add(cv(elementId = eid++, type = 0, text = "GUIDE", value = "k0x0400",
                cx = ((BACK_CX + START_CX) / 2).screenScale(height),
                cy = ((DPAD_CY + START_BACK_Y) / 2 - 2 + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = 7.screenScale(height)))
            // R3 — 右 (与 L3 以 GUIDE 中心对称)
            result.add(cv(elementId = eid++, type = 0, text = "R3", value = "k0x0002",
                cx = ((BACK_CX + START_CX) / 2 * 2 - (DPAD_CX + BACK_CX) / 2 - 11).screenScale(height) + rightDisplacement,
                cy = ((DPAD_CY + START_BACK_Y) / 2 - 2 + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = 7.screenScale(height)))
        } else {
            var eid = 1L
            // L3 only mode
            result.add(cv(elementId = eid++, type = 0, text = "L3", value = "k0x0004",
                cx = L3_CX.screenScale(height),
                cy = (L3_R3_Y + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = TRIGGER_HEIGHT.screenScale(height)))
            result.add(cv(elementId = eid++, type = 0, text = "R3", value = "k0x0002",
                cx = R3_CX.screenScale(height) + rightDisplacement,
                cy = (L3_R3_Y + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = TRIGGER_HEIGHT.screenScale(height)))
            result.add(cv(elementId = eid++, type = 0, text = "GUIDE", value = "k0x0400",
                cx = ((L3_CX + R3_CX) / 2).screenScale(height) + rightDisplacement / 2,
                cy = (L3_R3_Y + baseYUnit).screenScale(height),
                w = TRIGGER_WIDTH.screenScale(height), h = TRIGGER_HEIGHT.screenScale(height)))
        }

        return result
    }
}
