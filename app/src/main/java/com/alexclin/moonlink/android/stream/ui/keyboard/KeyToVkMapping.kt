package com.alexclin.moonlink.android.stream.ui.keyboard

import android.view.KeyEvent
import com.alexclin.moonlink.android.stream.ui.editor.getKeyLabelByValue
import com.limelight.binding.input.KeyboardTranslator

/**
 * 将键值选择器的键值（如 \"k29\"、\"m1\"、\"g16\"）转换为 Windows VK 十六进制字符串（如 \"0x41\"）。
 */
object KeyToVkMapping {

    /**
     * 将键值选择器中返回的键值转换为 Windows VK 码的十六进制格式。
     *
     * @param pickerValue 键值选择器返回的 value（如 \"k29\"）
     * @return 十六进制字符串（如 \"0x41\"），若无法转换返回 null
     */
    fun toHexVk(pickerValue: String): String? {
        when {
            pickerValue.startsWith("k") -> {
                val keyCode = pickerValue.substring(1).toIntOrNull() ?: return null
                return androidKeyCodeToHexVk(keyCode)
            }
            // 鼠标键：没有标准的 VK 映射，跳过
            pickerValue.startsWith("m") -> return null
            // 手柄键：没有标准的 VK 映射，跳过
            pickerValue.startsWith("g") -> return null
            // 特殊键（如 MMS, CMS 等）：没有 VK 映射，跳过
            else -> return null
        }
    }

    /**
     * 将键值选择器中返回的键值转换为对应的显示标签。
     */
    fun toLabel(pickerValue: String): String? {
        return getKeyLabelByValue(pickerValue)
    }

    /**
     * 将多个键值选择器的值转换为十六进制 VK 码列表。
     */
    fun toHexVkList(pickerValues: List<String>): List<String> {
        return pickerValues.mapNotNull { toHexVk(it) }
    }

    /**
     * 将多个键值选择器的值转换为显示标签列表。
     */
    fun toLabelList(pickerValues: List<String>): List<String> {
        return pickerValues.mapNotNull { toLabel(it) }
    }

    // ── 内部实现 ──

    /**
     * 将 Android 键码转换为 Windows VK 码的十六进制字符串。
     * 复用 KeyboardTranslator 的翻译逻辑。
     */
    private fun androidKeyCodeToHexVk(keyCode: Int): String? {
        // 数字键 0-9
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val vk = (keyCode - KeyEvent.KEYCODE_0) + KeyboardTranslator.VK_0
            return "0x${vk.toString(16).uppercase()}"
        }
        // 字母键 A-Z
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val vk = (keyCode - KeyEvent.KEYCODE_A) + KeyboardTranslator.VK_A
            return "0x${vk.toString(16).uppercase()}"
        }
        // 小键盘 0-9
        if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            val vk = (keyCode - KeyEvent.KEYCODE_NUMPAD_0) + KeyboardTranslator.VK_NUMPAD0
            return "0x${vk.toString(16).uppercase()}"
        }
        // F1-F12
        if (keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            val vk = (keyCode - KeyEvent.KEYCODE_F1) + KeyboardTranslator.VK_F1
            return "0x${vk.toString(16).uppercase()}"
        }
        // 其他特殊键
        val vk = when (keyCode) {
            KeyEvent.KEYCODE_ALT_LEFT -> 0xA4
            KeyEvent.KEYCODE_ALT_RIGHT -> 0xA5
            KeyEvent.KEYCODE_BACKSLASH -> 0xDC
            KeyEvent.KEYCODE_CAPS_LOCK -> KeyboardTranslator.VK_CAPS_LOCK
            KeyEvent.KEYCODE_CLEAR -> KeyboardTranslator.VK_CLEAR
            KeyEvent.KEYCODE_COMMA -> 0xBC
            KeyEvent.KEYCODE_CTRL_LEFT -> KeyboardTranslator.VK_LCONTROL
            KeyEvent.KEYCODE_CTRL_RIGHT -> 0xA3
            KeyEvent.KEYCODE_DEL -> KeyboardTranslator.VK_BACK_SPACE
            KeyEvent.KEYCODE_ENTER -> 0x0D
            KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> 0xBB
            KeyEvent.KEYCODE_ESCAPE -> KeyboardTranslator.VK_ESCAPE
            KeyEvent.KEYCODE_FORWARD_DEL -> 0x2E
            KeyEvent.KEYCODE_INSERT -> 0x2D
            KeyEvent.KEYCODE_LEFT_BRACKET -> 0xDB
            KeyEvent.KEYCODE_META_LEFT -> KeyboardTranslator.VK_LWIN
            KeyEvent.KEYCODE_META_RIGHT -> 0x5C
            KeyEvent.KEYCODE_MENU -> 0x5D
            KeyEvent.KEYCODE_MINUS -> 0xBD
            KeyEvent.KEYCODE_MOVE_END -> KeyboardTranslator.VK_END
            KeyEvent.KEYCODE_MOVE_HOME -> KeyboardTranslator.VK_HOME
            KeyEvent.KEYCODE_NUM_LOCK -> KeyboardTranslator.VK_NUM_LOCK
            KeyEvent.KEYCODE_PAGE_DOWN -> KeyboardTranslator.VK_PAGE_DOWN
            KeyEvent.KEYCODE_PAGE_UP -> KeyboardTranslator.VK_PAGE_UP
            KeyEvent.KEYCODE_PERIOD -> 0xBE
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 0xDD
            KeyEvent.KEYCODE_SCROLL_LOCK -> KeyboardTranslator.VK_SCROLL_LOCK
            KeyEvent.KEYCODE_SEMICOLON -> 0xBA
            KeyEvent.KEYCODE_SHIFT_LEFT -> KeyboardTranslator.VK_LSHIFT
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xA1
            KeyEvent.KEYCODE_SLASH -> 0xBF
            KeyEvent.KEYCODE_SPACE -> KeyboardTranslator.VK_SPACE
            KeyEvent.KEYCODE_SYSRQ -> KeyboardTranslator.VK_PRINTSCREEN
            KeyEvent.KEYCODE_TAB -> KeyboardTranslator.VK_TAB
            KeyEvent.KEYCODE_DPAD_LEFT -> KeyboardTranslator.VK_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> KeyboardTranslator.VK_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> KeyboardTranslator.VK_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> KeyboardTranslator.VK_DOWN
            KeyEvent.KEYCODE_GRAVE -> KeyboardTranslator.VK_BACK_QUOTE
            KeyEvent.KEYCODE_APOSTROPHE -> 0xDE
            KeyEvent.KEYCODE_BREAK -> KeyboardTranslator.VK_PAUSE
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> 0x6F
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> 0x6A
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 0x6D
            KeyEvent.KEYCODE_NUMPAD_ADD -> 0x6B
            KeyEvent.KEYCODE_NUMPAD_DOT -> 0x6E
            else -> return null
        }
        return "0x${vk.toString(16).uppercase()}"
    }
}
