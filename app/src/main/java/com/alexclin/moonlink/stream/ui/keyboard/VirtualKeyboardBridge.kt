package com.alexclin.moonlink.stream.ui.keyboard

import com.alexclin.moonlink.stream.engine.StreamEngine
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.advance_setting.KeyboardUIController

/**
 * 将旧 [KeyboardUIController] 的按键事件桥接到 [StreamEngine]。
 *
 * ## 键码转换
 *
 * [KeyboardUIController] 通过 View tag 传递 **Android KeyEvent 键码**（如 k45 = KEYCODE_Q），
 * 本适配器使用 [KeyboardTranslator] 将 Android 键码转换为 **Windows VK 码**，
 * 再传给 [StreamEngine.sendKeyboardInputWithModifier]，与旧版 Game → KeyboardInputHandler 路径一致。
 *
 * ## 修饰键跟踪
 *
 * [KeyboardUIController] 的修饰键状态机（NEUTRAL → SINGLE → LOCKED）独立运行，
 * 本适配器通过 [modifierBitFor] 跟踪当前活跃的修饰键 bitmask，
 * 在每次 [sendKeyEvent] 调用时传入正确的 modifier 参数。
 */
class VirtualKeyboardBridge(
    private val engine: StreamEngine,
) : KeyboardUIController.OnKeyboardEventListener {

    /** Android 键码 → Windows VK 码翻译器 */
    private val translator = KeyboardTranslator()

    /** 当前活跃的修饰键 bitmask（SHIFT=0x01, CTRL=0x02, ALT=0x04, META=0x08） */
    private var modifierState: Byte = 0

    companion object {
        // KeyboardPacket modifier 常量（与 NvConnection 一致）
        private const val MOD_SHIFT = 0x01.toByte()
        private const val MOD_CTRL  = 0x02.toByte()
        private const val MOD_ALT   = 0x04.toByte()
        private const val MOD_META  = 0x08.toByte()

        // Android KeyEvent 键码常量
        private const val KEYCODE_CTRL_LEFT  = 113
        private const val KEYCODE_CTRL_RIGHT = 114
        private const val KEYCODE_SHIFT_LEFT = 59
        private const val KEYCODE_SHIFT_RIGHT = 60
        private const val KEYCODE_ALT_LEFT = 57
        private const val KEYCODE_ALT_RIGHT = 58
        private const val KEYCODE_META_LEFT = 117
    }

    override fun sendKeyEvent(down: Boolean, keyCode: Short) {
        val modBit = modifierBitFor(keyCode.toInt())

        if (modBit != 0.toByte()) {
            // 修饰键：更新本地 bitmask
            if (down) {
                modifierState = (modifierState.toInt() or modBit.toInt()).toByte()
            } else {
                modifierState = (modifierState.toInt() and modBit.toInt().inv()).toByte()
            }
        }

        // 将 Android KeyEvent 键码翻译为 Windows VK 码（与旧 KeyboardInputHandler 一致）
        val translatedKeyCode = translator.translate(keyCode.toInt(), -1)
        if (translatedKeyCode.toInt() == 0) {
            // 未知键码，跳过
            return
        }

        // 发送翻译后的键码，modifier 参数使用当前累积的修饰键状态
        engine.sendKeyboardInputWithModifier(translatedKeyCode, down, modifierState)
    }

    override fun rumbleSingleVibrator(lowFreq: Short, highFreq: Short, duration: Int) {
        engine.rumbleSingleVibrator(lowFreq, highFreq, duration)
    }

    private fun modifierBitFor(keyCode: Int): Byte = when (keyCode) {
        KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT   -> MOD_CTRL
        KEYCODE_SHIFT_LEFT, KEYCODE_SHIFT_RIGHT -> MOD_SHIFT
        KEYCODE_ALT_LEFT, KEYCODE_ALT_RIGHT     -> MOD_ALT
        KEYCODE_META_LEFT                       -> MOD_META
        else -> 0
    }
}
