package com.limelight.binding.input.evdev

import android.view.KeyEvent

object EvdevTranslator {
    fun translateEvdevKeyCode(evdevKeyCode: Int): Short {
        return when (evdevKeyCode) {
            KEY_ESC -> KeyEvent.KEYCODE_ESCAPE
            KEY_1 -> KeyEvent.KEYCODE_1
            KEY_2 -> KeyEvent.KEYCODE_2
            KEY_3 -> KeyEvent.KEYCODE_3
            KEY_4 -> KeyEvent.KEYCODE_4
            KEY_5 -> KeyEvent.KEYCODE_5
            KEY_6 -> KeyEvent.KEYCODE_6
            KEY_7 -> KeyEvent.KEYCODE_7
            KEY_8 -> KeyEvent.KEYCODE_8
            KEY_9 -> KeyEvent.KEYCODE_9
            KEY_0 -> KeyEvent.KEYCODE_0
            KEY_MINUS -> KeyEvent.KEYCODE_MINUS
            KEY_EQUAL -> KeyEvent.KEYCODE_EQUALS
            KEY_BACKSPACE -> KeyEvent.KEYCODE_DEL
            KEY_TAB -> KeyEvent.KEYCODE_TAB
            KEY_Q -> KeyEvent.KEYCODE_Q
            KEY_W -> KeyEvent.KEYCODE_W
            KEY_E -> KeyEvent.KEYCODE_E
            KEY_R -> KeyEvent.KEYCODE_R
            KEY_T -> KeyEvent.KEYCODE_T
            KEY_Y -> KeyEvent.KEYCODE_Y
            KEY_U -> KeyEvent.KEYCODE_U
            KEY_I -> KeyEvent.KEYCODE_I
            KEY_O -> KeyEvent.KEYCODE_O
            KEY_P -> KeyEvent.KEYCODE_P
            KEY_LEFTBRACE -> KeyEvent.KEYCODE_LEFT_BRACKET
            KEY_RIGHTBRACE -> KeyEvent.KEYCODE_RIGHT_BRACKET
            KEY_ENTER -> KeyEvent.KEYCODE_ENTER
            KEY_LEFTCTRL -> KeyEvent.KEYCODE_CTRL_LEFT
            KEY_A -> KeyEvent.KEYCODE_A
            KEY_S -> KeyEvent.KEYCODE_S
            KEY_D -> KeyEvent.KEYCODE_D
            KEY_F -> KeyEvent.KEYCODE_F
            KEY_G -> KeyEvent.KEYCODE_G
            KEY_H -> KeyEvent.KEYCODE_H
            KEY_J -> KeyEvent.KEYCODE_J
            KEY_K -> KeyEvent.KEYCODE_K
            KEY_L -> KeyEvent.KEYCODE_L
            KEY_SEMICOLON -> KeyEvent.KEYCODE_SEMICOLON
            KEY_APOSTROPHE -> KeyEvent.KEYCODE_APOSTROPHE
            KEY_GRAVE -> KeyEvent.KEYCODE_GRAVE
            KEY_LEFTSHIFT -> KeyEvent.KEYCODE_SHIFT_LEFT
            KEY_BACKSLASH -> KeyEvent.KEYCODE_BACKSLASH
            KEY_Z -> KeyEvent.KEYCODE_Z
            KEY_X -> KeyEvent.KEYCODE_X
            KEY_C -> KeyEvent.KEYCODE_C
            KEY_V -> KeyEvent.KEYCODE_V
            KEY_B -> KeyEvent.KEYCODE_B
            KEY_N -> KeyEvent.KEYCODE_N
            KEY_M -> KeyEvent.KEYCODE_M
            KEY_COMMA -> KeyEvent.KEYCODE_COMMA
            KEY_DOT -> KeyEvent.KEYCODE_PERIOD
            KEY_SLASH -> KeyEvent.KEYCODE_SLASH
            KEY_RIGHTSHIFT -> KeyEvent.KEYCODE_SHIFT_RIGHT
            KEY_KPASTERISK -> KeyEvent.KEYCODE_NUMPAD_MULTIPLY
            KEY_LEFTALT -> KeyEvent.KEYCODE_ALT_LEFT
            KEY_SPACE -> KeyEvent.KEYCODE_SPACE
            KEY_CAPSLOCK -> KeyEvent.KEYCODE_CAPS_LOCK
            KEY_F1 -> KeyEvent.KEYCODE_F1
            KEY_F2 -> KeyEvent.KEYCODE_F2
            KEY_F3 -> KeyEvent.KEYCODE_F3
            KEY_F4 -> KeyEvent.KEYCODE_F4
            KEY_F5 -> KeyEvent.KEYCODE_F5
            KEY_F6 -> KeyEvent.KEYCODE_F6
            KEY_F7 -> KeyEvent.KEYCODE_F7
            KEY_F8 -> KeyEvent.KEYCODE_F8
            KEY_F9 -> KeyEvent.KEYCODE_F9
            KEY_F10 -> KeyEvent.KEYCODE_F10
            KEY_NUMLOCK -> KeyEvent.KEYCODE_NUM_LOCK
            KEY_SCROLLLOCK -> KeyEvent.KEYCODE_SCROLL_LOCK
            KEY_KP7 -> KeyEvent.KEYCODE_NUMPAD_7
            KEY_KP8 -> KeyEvent.KEYCODE_NUMPAD_8
            KEY_KP9 -> KeyEvent.KEYCODE_NUMPAD_9
            KEY_KPMINUS -> KeyEvent.KEYCODE_NUMPAD_SUBTRACT
            KEY_KP4 -> KeyEvent.KEYCODE_NUMPAD_4
            KEY_KP5 -> KeyEvent.KEYCODE_NUMPAD_5
            KEY_KP6 -> KeyEvent.KEYCODE_NUMPAD_6
            KEY_KPPLUS -> KeyEvent.KEYCODE_NUMPAD_ADD
            KEY_KP1 -> KeyEvent.KEYCODE_NUMPAD_1
            KEY_KP2 -> KeyEvent.KEYCODE_NUMPAD_2
            KEY_KP3 -> KeyEvent.KEYCODE_NUMPAD_3
            KEY_KP0 -> KeyEvent.KEYCODE_NUMPAD_0
            KEY_KPDOT -> KeyEvent.KEYCODE_NUMPAD_DOT
            KEY_F11 -> KeyEvent.KEYCODE_F11
            KEY_F12 -> KeyEvent.KEYCODE_F12
            KEY_KPENTER -> KeyEvent.KEYCODE_NUMPAD_ENTER
            KEY_RIGHTCTRL -> KeyEvent.KEYCODE_CTRL_RIGHT
            KEY_KPSLASH -> KeyEvent.KEYCODE_NUMPAD_DIVIDE
            KEY_SYSRQ -> KeyEvent.KEYCODE_SYSRQ
            KEY_RIGHTALT -> KeyEvent.KEYCODE_ALT_RIGHT
            KEY_HOME -> KeyEvent.KEYCODE_MOVE_HOME
            KEY_UP -> KeyEvent.KEYCODE_DPAD_UP
            KEY_PAGEUP -> KeyEvent.KEYCODE_PAGE_UP
            KEY_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            KEY_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            KEY_END -> KeyEvent.KEYCODE_MOVE_END
            KEY_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            KEY_PAGEDOWN -> KeyEvent.KEYCODE_PAGE_DOWN
            KEY_INSERT -> KeyEvent.KEYCODE_INSERT
            KEY_DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
            KEY_KPEQUAL -> KeyEvent.KEYCODE_NUMPAD_EQUALS
            KEY_PAUSE -> KeyEvent.KEYCODE_BREAK
            KEY_KPCOMMA -> KeyEvent.KEYCODE_NUMPAD_COMMA
            KEY_LEFTMETA -> KeyEvent.KEYCODE_META_LEFT
            KEY_RIGHTMETA -> KeyEvent.KEYCODE_META_RIGHT
            else -> 0
        }.toShort()
    }

    private const val KEY_ESC = 1
    private const val KEY_1 = 2
    private const val KEY_2 = 3
    private const val KEY_3 = 4
    private const val KEY_4 = 5
    private const val KEY_5 = 6
    private const val KEY_6 = 7
    private const val KEY_7 = 8
    private const val KEY_8 = 9
    private const val KEY_9 = 10
    private const val KEY_0 = 11
    private const val KEY_MINUS = 12
    private const val KEY_EQUAL = 13
    private const val KEY_BACKSPACE = 14
    private const val KEY_TAB = 15
    private const val KEY_Q = 16
    private const val KEY_W = 17
    private const val KEY_E = 18
    private const val KEY_R = 19
    private const val KEY_T = 20
    private const val KEY_Y = 21
    private const val KEY_U = 22
    private const val KEY_I = 23
    private const val KEY_O = 24
    private const val KEY_P = 25
    private const val KEY_LEFTBRACE = 26
    private const val KEY_RIGHTBRACE = 27
    private const val KEY_ENTER = 28
    private const val KEY_LEFTCTRL = 29
    private const val KEY_A = 30
    private const val KEY_S = 31
    private const val KEY_D = 32
    private const val KEY_F = 33
    private const val KEY_G = 34
    private const val KEY_H = 35
    private const val KEY_J = 36
    private const val KEY_K = 37
    private const val KEY_L = 38
    private const val KEY_SEMICOLON = 39
    private const val KEY_APOSTROPHE = 40
    private const val KEY_GRAVE = 41
    private const val KEY_LEFTSHIFT = 42
    private const val KEY_BACKSLASH = 43
    private const val KEY_Z = 44
    private const val KEY_X = 45
    private const val KEY_C = 46
    private const val KEY_V = 47
    private const val KEY_B = 48
    private const val KEY_N = 49
    private const val KEY_M = 50
    private const val KEY_COMMA = 51
    private const val KEY_DOT = 52
    private const val KEY_SLASH = 53
    private const val KEY_RIGHTSHIFT = 54
    private const val KEY_KPASTERISK = 55
    private const val KEY_LEFTALT = 56
    private const val KEY_SPACE = 57
    private const val KEY_CAPSLOCK = 58
    private const val KEY_F1 = 59
    private const val KEY_F2 = 60
    private const val KEY_F3 = 61
    private const val KEY_F4 = 62
    private const val KEY_F5 = 63
    private const val KEY_F6 = 64
    private const val KEY_F7 = 65
    private const val KEY_F8 = 66
    private const val KEY_F9 = 67
    private const val KEY_F10 = 68
    private const val KEY_NUMLOCK = 69
    private const val KEY_SCROLLLOCK = 70
    private const val KEY_KP7 = 71
    private const val KEY_KP8 = 72
    private const val KEY_KP9 = 73
    private const val KEY_KPMINUS = 74
    private const val KEY_KP4 = 75
    private const val KEY_KP5 = 76
    private const val KEY_KP6 = 77
    private const val KEY_KPPLUS = 78
    private const val KEY_KP1 = 79
    private const val KEY_KP2 = 80
    private const val KEY_KP3 = 81
    private const val KEY_KP0 = 82
    private const val KEY_KPDOT = 83
    private const val KEY_F11 = 87
    private const val KEY_F12 = 88
    private const val KEY_KPENTER = 96
    private const val KEY_RIGHTCTRL = 97
    private const val KEY_KPSLASH = 98
    private const val KEY_SYSRQ = 99
    private const val KEY_RIGHTALT = 100
    private const val KEY_HOME = 102
    private const val KEY_UP = 103
    private const val KEY_PAGEUP = 104
    private const val KEY_LEFT = 105
    private const val KEY_RIGHT = 106
    private const val KEY_END = 107
    private const val KEY_DOWN = 108
    private const val KEY_PAGEDOWN = 109
    private const val KEY_INSERT = 110
    private const val KEY_DELETE = 111
    private const val KEY_KPEQUAL = 117
    private const val KEY_PAUSE = 119
    private const val KEY_KPCOMMA = 121
    private const val KEY_LEFTMETA = 125
    private const val KEY_RIGHTMETA = 126
}
