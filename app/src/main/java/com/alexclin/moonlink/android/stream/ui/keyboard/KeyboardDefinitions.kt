package com.alexclin.moonlink.android.stream.ui.keyboard

/**
 * 键盘按键类型
 */
enum class KeyType {
    /** 普通按键（字母/数字/符号） */
    NORMAL,
    /** 修饰键（Ctrl/Shift/Alt/Win） */
    MODIFIER,
    /** 功能操作按钮（切换面板/隐藏/全屏等） */
    ACTION,
}

/**
 * 按键样式权重（对应原 XML layout_weight）
 */
enum class KeyWeight(val factor: Float) {
    W10(1.0f),
    W15(1.5f),
    W18(1.8f),
    W20(2.0f),
    W22(2.2f),
    W40(4.0f),
    W107(1.07f),
    W11(1.1f),
    W085(0.86f),
}

/**
 * 键盘按键定义
 *
 * @param label 按键显示文字
 * @param keyCode Android KeyEvent 键码
 * @param type 按键类型
 * @param weight 宽度权重
 * @param actionId 操作标识（仅 ACTION 类型使用）
 */
data class KeyboardKeyDef(
    val label: String,
    val keyCode: Int = 0,
    val type: KeyType = KeyType.NORMAL,
    val weight: KeyWeight = KeyWeight.W10,
    val actionId: String? = null,
)

/**
 * 键盘行定义
 *
 * @param keys 该行按键列表
 * @param heightWeight 行高度权重（相对于其他行）
 * @param horizontalPaddingDp 水平内边距（dp），用于 Mini Alpha 第二行的缩进效果
 */
data class KeyboardRowDef(
    val keys: List<KeyboardKeyDef>,
    val heightWeight: Float = 1f,
    val horizontalPaddingDp: Float = 0f,
)

/**
 * 键盘页面定义
 *
 * @param rows 各行定义
 */
data class KeyboardPageDef(
    val rows: List<KeyboardRowDef>,
)

/**
 * 完整键盘布局定义
 */
object KeyboardLayouts {
    // ── Android KeyEvent 键码常量 ──
    const val KEY_GRAVE = 68          // `
    const val KEY_1 = 8
    const val KEY_2 = 9
    const val KEY_3 = 10
    const val KEY_4 = 11
    const val KEY_5 = 12
    const val KEY_6 = 13
    const val KEY_7 = 14
    const val KEY_8 = 15
    const val KEY_9 = 16
    const val KEY_0 = 7
    const val KEY_MINUS = 69
    const val KEY_EQUALS = 70
    const val KEY_DEL = 67           // Backspace
    const val KEY_TAB = 61
    const val KEY_Q = 45
    const val KEY_W = 51
    const val KEY_E = 33
    const val KEY_R = 46
    const val KEY_T = 48
    const val KEY_Y = 53
    const val KEY_U = 49
    const val KEY_I = 37
    const val KEY_O = 43
    const val KEY_P = 44
    const val KEY_LBRACKET = 71      // [
    const val KEY_RBRACKET = 72      // ]
    const val KEY_BACKSLASH = 73     // \
    const val KEY_CAPS = 115
    const val KEY_A = 29
    const val KEY_S = 47
    const val KEY_D = 32
    const val KEY_F = 34
    const val KEY_G = 35
    const val KEY_H = 36
    const val KEY_J = 38
    const val KEY_K = 39
    const val KEY_L = 40
    const val KEY_SEMICOLON = 74     // ;
    const val KEY_APOSTROPHE = 75    // '
    const val KEY_ENTER = 66
    const val KEY_LSHIFT = 59
    const val KEY_Z = 54
    const val KEY_X = 52
    const val KEY_C = 31
    const val KEY_V = 50
    const val KEY_B = 30
    const val KEY_N = 42
    const val KEY_M = 41
    const val KEY_COMMA = 55         // ,
    const val KEY_PERIOD = 56        // .
    const val KEY_SLASH = 76         // /
    const val KEY_RSHIFT = 60
    const val KEY_DPAD_UP = 19
    const val KEY_LCTRL = 113
    const val KEY_LWIN = 117
    const val KEY_LALT = 57
    const val KEY_SPACE = 62
    const val KEY_RALT = 58
    const val KEY_RCTRL = 114
    const val KEY_DPAD_LEFT = 21
    const val KEY_DPAD_DOWN = 20
    const val KEY_DPAD_RIGHT = 22
    const val KEY_ESC = 111
    const val KEY_F1 = 131
    const val KEY_F2 = 132
    const val KEY_F3 = 133
    const val KEY_F4 = 134
    const val KEY_F5 = 135
    const val KEY_F6 = 136
    const val KEY_F7 = 137
    const val KEY_F8 = 138
    const val KEY_F9 = 139
    const val KEY_F10 = 140
    const val KEY_F11 = 141
    const val KEY_F12 = 142
    const val KEY_FORWARD_DEL = 112   // Delete (forward)
    const val KEY_INSERT = 124
    const val KEY_HOME = 122
    const val KEY_PAGE_UP = 92
    const val KEY_END = 123
    const val KEY_PAGE_DOWN = 93
    const val KEY_PRTSC = 120
    const val KEY_SCROLL_LOCK = 116
    const val KEY_PAUSE = 121
    const val KEY_NUM_LOCK = 143
    const val KEY_NUMPAD_0 = 144
    const val KEY_NUMPAD_1 = 145
    const val KEY_NUMPAD_2 = 146
    const val KEY_NUMPAD_3 = 147
    const val KEY_NUMPAD_4 = 148
    const val KEY_NUMPAD_5 = 149
    const val KEY_NUMPAD_6 = 150
    const val KEY_NUMPAD_7 = 151
    const val KEY_NUMPAD_8 = 152
    const val KEY_NUMPAD_9 = 153
    const val KEY_NUMPAD_DIVIDE = 154
    const val KEY_NUMPAD_MULTIPLY = 155
    const val KEY_NUMPAD_SUBTRACT = 156
    const val KEY_NUMPAD_ADD = 157
    const val KEY_NUMPAD_DOT = 158

    // ── Action 标识 ──
    const val ACTION_HIDE = "hide"
    const val ACTION_RESIZE = "resize"
    const val ACTION_TOGGLE_NUM = "toggle_num"
    const val ACTION_TOGGLE_PC = "toggle_pc"
    const val ACTION_BACK_ALPHA = "back_alpha"
    const val ACTION_TOGGLE_FULL = "toggle_full"
    const val ACTION_SHOW_NUMPAD = "show_numpad"
    const val ACTION_BACK_FULL = "back_full"
    const val ACTION_ENTER_MINI = "enter_mini"

    // ── 修饰键集合 ──
    val MODIFIER_KEYS = setOf(
        KEY_LCTRL, KEY_RCTRL, KEY_LSHIFT, KEY_RSHIFT, KEY_LALT, KEY_RALT, KEY_LWIN,
    )

    // ── 主键盘布局 (QWERTY) ──
    // 六行，每行末尾增加一个功能键：↑ ↓ ← → NUM MINI
    val MAIN = KeyboardPageDef(
        rows = listOf(
            // Row 0: Function key strip — ESC, F1-F12, ↑
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Esc", KEY_ESC, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("F1", KEY_F1),
                    KeyboardKeyDef("F2", KEY_F2),
                    KeyboardKeyDef("F3", KEY_F3),
                    KeyboardKeyDef("F4", KEY_F4),
                    KeyboardKeyDef("F5", KEY_F5),
                    KeyboardKeyDef("F6", KEY_F6),
                    KeyboardKeyDef("F7", KEY_F7),
                    KeyboardKeyDef("F8", KEY_F8),
                    KeyboardKeyDef("F9", KEY_F9),
                    KeyboardKeyDef("F10", KEY_F10),
                    KeyboardKeyDef("F11", KEY_F11),
                    KeyboardKeyDef("F12", KEY_F12),
                    KeyboardKeyDef("↑", KEY_DPAD_UP),
                ),
            ),
            // Row 1: Numbers + Backspace + ↓
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("` ~", KEY_GRAVE),
                    KeyboardKeyDef("1 !", KEY_1),
                    KeyboardKeyDef("2 @", KEY_2),
                    KeyboardKeyDef("3 #", KEY_3),
                    KeyboardKeyDef("4 $", KEY_4),
                    KeyboardKeyDef("5 %", KEY_5),
                    KeyboardKeyDef("6 ^", KEY_6),
                    KeyboardKeyDef("7 &", KEY_7),
                    KeyboardKeyDef("8 *", KEY_8),
                    KeyboardKeyDef("9 (", KEY_9),
                    KeyboardKeyDef("0 )", KEY_0),
                    KeyboardKeyDef("- _", KEY_MINUS),
                    KeyboardKeyDef("= +", KEY_EQUALS),
                    KeyboardKeyDef("⌫", KEY_DEL, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("↓", KEY_DPAD_DOWN, weight = KeyWeight.W107),
                ),
            ),
            // Row 2: QWERTY + ←
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Tab ⇥", KEY_TAB, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("Q", KEY_Q),
                    KeyboardKeyDef("W", KEY_W),
                    KeyboardKeyDef("E", KEY_E),
                    KeyboardKeyDef("R", KEY_R),
                    KeyboardKeyDef("T", KEY_T),
                    KeyboardKeyDef("Y", KEY_Y),
                    KeyboardKeyDef("U", KEY_U),
                    KeyboardKeyDef("I", KEY_I),
                    KeyboardKeyDef("O", KEY_O),
                    KeyboardKeyDef("P", KEY_P),
                    KeyboardKeyDef("[ {", KEY_LBRACKET),
                    KeyboardKeyDef("] }", KEY_RBRACKET),
                    KeyboardKeyDef("\\ |", KEY_BACKSLASH, weight = KeyWeight.W15),
                    KeyboardKeyDef("←", KEY_DPAD_LEFT,weight = KeyWeight.W11),
                ),
            ),
            // Row 3: ASDF + Enter + →
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Caps", KEY_CAPS, type = KeyType.MODIFIER, weight = KeyWeight.W18),
                    KeyboardKeyDef("A", KEY_A),
                    KeyboardKeyDef("S", KEY_S),
                    KeyboardKeyDef("D", KEY_D),
                    KeyboardKeyDef("F", KEY_F),
                    KeyboardKeyDef("G", KEY_G),
                    KeyboardKeyDef("H", KEY_H),
                    KeyboardKeyDef("J", KEY_J),
                    KeyboardKeyDef("K", KEY_K),
                    KeyboardKeyDef("L", KEY_L),
                    KeyboardKeyDef("; :", KEY_SEMICOLON),
                    KeyboardKeyDef("' \"", KEY_APOSTROPHE),
                    KeyboardKeyDef("Enter ↵", KEY_ENTER, type = KeyType.MODIFIER, weight = KeyWeight.W20),
                    KeyboardKeyDef("→", KEY_DPAD_RIGHT,weight = KeyWeight.W11),
                ),
            ),
            // Row 4: ZXCVB + Shift + NUM
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Shift", KEY_LSHIFT, type = KeyType.MODIFIER, weight = KeyWeight.W22),
                    KeyboardKeyDef("Z", KEY_Z),
                    KeyboardKeyDef("X", KEY_X),
                    KeyboardKeyDef("C", KEY_C),
                    KeyboardKeyDef("V", KEY_V),
                    KeyboardKeyDef("B", KEY_B),
                    KeyboardKeyDef("N", KEY_N),
                    KeyboardKeyDef("M", KEY_M),
                    KeyboardKeyDef(", <", KEY_COMMA),
                    KeyboardKeyDef(". >", KEY_PERIOD),
                    KeyboardKeyDef("/ ?", KEY_SLASH),
                    KeyboardKeyDef("Shift", KEY_RSHIFT, type = KeyType.MODIFIER, weight = KeyWeight.W22),
                    KeyboardKeyDef("NUM", actionId = ACTION_SHOW_NUMPAD, type = KeyType.ACTION,weight = KeyWeight.W107),
                ),
            ),
            // Row 5: Modifiers + Space + MINI
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Ctrl", KEY_LCTRL, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("Win", KEY_LWIN, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("Alt", KEY_LALT, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("Space", KEY_SPACE, weight = KeyWeight.W40),
                    KeyboardKeyDef("Alt", KEY_RALT, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("Ctrl", KEY_RCTRL, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("MINI", actionId = ACTION_ENTER_MINI, type = KeyType.ACTION,weight = KeyWeight.W085),
                ),
            ),
        ),
    )

    // ── 数字键盘右侧 3×3 特殊键 ──
    val NUMPAD_RIGHT_GRID = listOf(
        // Row 0: PrtSc / ScrLk / Pause
        listOf(
            KeyboardKeyDef("PrtSc", KEY_PRTSC, type = KeyType.MODIFIER),
            KeyboardKeyDef("ScrLk", KEY_SCROLL_LOCK, type = KeyType.MODIFIER),
            KeyboardKeyDef("Pause", KEY_PAUSE, type = KeyType.MODIFIER),
        ),
        // Row 1: Ins / Home / PgUp
        listOf(
            KeyboardKeyDef("Ins", KEY_INSERT),
            KeyboardKeyDef("Home", KEY_HOME),
            KeyboardKeyDef("PgUp", KEY_PAGE_UP),
        ),
        // Row 2: Del / End / PgDn
        listOf(
            KeyboardKeyDef("Del", KEY_FORWARD_DEL),
            KeyboardKeyDef("End", KEY_END),
            KeyboardKeyDef("PgDn", KEY_PAGE_DOWN),
        ),
    )

    // ── 数字键盘左侧布局（4列，四行均分高度） ──
    val NUMPAD_LEFT_GRID = listOf(
        listOf(
            KeyboardKeyDef("7", KEY_NUMPAD_7),
            KeyboardKeyDef("8", KEY_NUMPAD_8),
            KeyboardKeyDef("9", KEY_NUMPAD_9),
            KeyboardKeyDef("/", KEY_NUMPAD_DIVIDE),
        ),
        listOf(
            KeyboardKeyDef("4", KEY_NUMPAD_4),
            KeyboardKeyDef("5", KEY_NUMPAD_5),
            KeyboardKeyDef("6", KEY_NUMPAD_6),
            KeyboardKeyDef("*", KEY_NUMPAD_MULTIPLY),
        ),
        listOf(
            KeyboardKeyDef("1", KEY_NUMPAD_1),
            KeyboardKeyDef("2", KEY_NUMPAD_2),
            KeyboardKeyDef("3", KEY_NUMPAD_3),
            KeyboardKeyDef("-", KEY_NUMPAD_SUBTRACT),
        ),
        // 底部行：0（2列宽）、.、+，与其他行均分高度
        listOf(
            KeyboardKeyDef("0", KEY_NUMPAD_0, weight = KeyWeight.W20),
            KeyboardKeyDef(".", KEY_NUMPAD_DOT),
            KeyboardKeyDef("+", KEY_NUMPAD_ADD, type = KeyType.MODIFIER),
        ),
    )

    // ── Mini 键盘 - Alpha 面板 ──
    val MINI_ALPHA = KeyboardPageDef(
        rows = listOf(
            // Row 1: QWERTYUIOP
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Q", KEY_Q),
                    KeyboardKeyDef("W", KEY_W),
                    KeyboardKeyDef("E", KEY_E),
                    KeyboardKeyDef("R", KEY_R),
                    KeyboardKeyDef("T", KEY_T),
                    KeyboardKeyDef("Y", KEY_Y),
                    KeyboardKeyDef("U", KEY_U),
                    KeyboardKeyDef("I", KEY_I),
                    KeyboardKeyDef("O", KEY_O),
                    KeyboardKeyDef("P", KEY_P),
                ),
            ),
            // Row 2: ASDFGHJKL (indented)
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("A", KEY_A),
                    KeyboardKeyDef("S", KEY_S),
                    KeyboardKeyDef("D", KEY_D),
                    KeyboardKeyDef("F", KEY_F),
                    KeyboardKeyDef("G", KEY_G),
                    KeyboardKeyDef("H", KEY_H),
                    KeyboardKeyDef("J", KEY_J),
                    KeyboardKeyDef("K", KEY_K),
                    KeyboardKeyDef("L", KEY_L),
                ),
                horizontalPaddingDp = 10f,
            ),
            // Row 3: Caps + ZXCVBNM + Backspace
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Caps", KEY_CAPS, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                    KeyboardKeyDef("Z", KEY_Z),
                    KeyboardKeyDef("X", KEY_X),
                    KeyboardKeyDef("C", KEY_C),
                    KeyboardKeyDef("V", KEY_V),
                    KeyboardKeyDef("B", KEY_B),
                    KeyboardKeyDef("N", KEY_N),
                    KeyboardKeyDef("M", KEY_M),
                    KeyboardKeyDef("⌫", KEY_DEL, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                ),
            ),
            // Row 4: ?123 | PC | Space | Hide | Full | Enter
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("?123", actionId = ACTION_TOGGLE_NUM, type = KeyType.ACTION, weight = KeyWeight.W15),
                    KeyboardKeyDef("PC", actionId = ACTION_TOGGLE_PC, type = KeyType.ACTION),
                    KeyboardKeyDef("Space", KEY_SPACE, weight = KeyWeight.W40),
                    KeyboardKeyDef("Hide", actionId = ACTION_HIDE, type = KeyType.ACTION),
                    KeyboardKeyDef("Full", actionId = ACTION_TOGGLE_FULL, type = KeyType.ACTION),
                    KeyboardKeyDef("↵", KEY_ENTER, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                ),
            ),
        ),
    )

    // ── Mini 键盘 - Number 面板 ──
    val MINI_NUM = KeyboardPageDef(
        rows = listOf(
            // Row 1: 1-0 symbols
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("1!", KEY_1),
                    KeyboardKeyDef("2@", KEY_2),
                    KeyboardKeyDef("3#", KEY_3),
                    KeyboardKeyDef("4$", KEY_4),
                    KeyboardKeyDef("5%", KEY_5),
                    KeyboardKeyDef("6^", KEY_6),
                    KeyboardKeyDef("7&", KEY_7),
                    KeyboardKeyDef("8*", KEY_8),
                    KeyboardKeyDef("9(", KEY_9),
                    KeyboardKeyDef("0)", KEY_0),
                ),
            ),
            // Row 2: Special chars
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("-_", KEY_MINUS),
                    KeyboardKeyDef("=+", KEY_EQUALS),
                    KeyboardKeyDef("[{", KEY_LBRACKET),
                    KeyboardKeyDef("]}", KEY_RBRACKET),
                    KeyboardKeyDef("\\|", KEY_BACKSLASH),
                    KeyboardKeyDef(";:", KEY_SEMICOLON),
                    KeyboardKeyDef("'\"", KEY_APOSTROPHE),
                    KeyboardKeyDef(",<", KEY_COMMA),
                    KeyboardKeyDef(".>", KEY_PERIOD),
                    KeyboardKeyDef("/?", KEY_SLASH),
                ),
            ),
            // Row 3: F1-F8 + Backspace
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("F1", KEY_F1),
                    KeyboardKeyDef("F2", KEY_F2),
                    KeyboardKeyDef("F3", KEY_F3),
                    KeyboardKeyDef("F4", KEY_F4),
                    KeyboardKeyDef("F5", KEY_F5),
                    KeyboardKeyDef("F6", KEY_F6),
                    KeyboardKeyDef("F7", KEY_F7),
                    KeyboardKeyDef("F8", KEY_F8),
                    KeyboardKeyDef("⌫", KEY_DEL, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                ),
            ),
            // Row 4: ABC | PC | Space | Hide | Full | Enter
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("ABC", actionId = ACTION_BACK_ALPHA, type = KeyType.ACTION, weight = KeyWeight.W15),
                    KeyboardKeyDef("PC", actionId = ACTION_TOGGLE_PC, type = KeyType.ACTION),
                    KeyboardKeyDef("Space", KEY_SPACE, weight = KeyWeight.W40),
                    KeyboardKeyDef("Hide", actionId = ACTION_HIDE, type = KeyType.ACTION),
                    KeyboardKeyDef("Full", actionId = ACTION_TOGGLE_FULL, type = KeyType.ACTION),
                    KeyboardKeyDef("↵", KEY_ENTER, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                ),
            ),
        ),
    )

    // ── Mini 键盘 - PC 面板 ──
    val MINI_PC = KeyboardPageDef(
        rows = listOf(
            // Row 1: Esc Tab Home End PrtSc
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("Esc", KEY_ESC, type = KeyType.MODIFIER),
                    KeyboardKeyDef("Tab", KEY_TAB, type = KeyType.MODIFIER),
                    KeyboardKeyDef("Home", KEY_HOME, type = KeyType.MODIFIER),
                    KeyboardKeyDef("End", KEY_END, type = KeyType.MODIFIER),
                    KeyboardKeyDef("PrtSc", KEY_PRTSC, type = KeyType.MODIFIER),
                ),
                heightWeight = 0.8f,
            ),
            // Row 2: ABC + Delete (left) | Arrow keys (right)
            KeyboardRowDef(
                keys = listOf(
                    KeyboardKeyDef("ABC", actionId = ACTION_BACK_ALPHA, type = KeyType.ACTION, weight = KeyWeight.W15),
                    KeyboardKeyDef("⌫ Delete", KEY_FORWARD_DEL, type = KeyType.MODIFIER, weight = KeyWeight.W15),
                ),
            ),
            // Arrow grid rendered separately in composable
        ),
    )
}
