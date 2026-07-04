package com.alexclin.moonlink.android.stream

import android.view.View
import android.view.WindowManager
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.touch.TouchContext
import com.limelight.ui.StreamView

/**
 * Bridge interface that decouples element controllers from the concrete host Activity.
 *
 * Element controllers only need the stream-surface, controller handler, mouse/scroll input,
 * menu trigger, and touch-mode control — nothing else from the host class.
 */
interface StreamInteractionBridge {
    /** The [StreamView] that hosts video rendering and touch dispatch. */
    val streamView: StreamView

    /** The global controller handler (vibration, gamepad state, etc.). */
    val controllerHandler: ControllerHandler

    /** Relative touch context map used by touch controllers. */
    val relativeTouchContextMap: Array<TouchContext?>

    /** Bridge self as a [View.OnTouchListener] (Game is an Activity which implements it). */
    fun asOnTouchListener(): View.OnTouchListener

    // ── Mouse / Scroll ──────────────────────────────────────────────────────

    /** Send a vertical mouse scroll event to the host. */
    fun mouseVScroll(amount: Byte)

    /** Send a relative mouse move to the host. */
    fun mouseMove(deltaX: Int, deltaY: Int)

    // ── Touch mode ──────────────────────────────────────────────────────────

    /** Switch between absolute (false) and relative / trackpad (true) touch mode. */
    fun setTouchMode(enableRelativeTouch: Boolean)

    /** Toggle enhanced-touch (long-press → right-click) behaviour. */
    fun setEnhancedTouch(enableRelativeTouch: Boolean)

    /** Activity-level WindowManager (needed by element classes for display metrics). */
    fun getWindowManager(): WindowManager

    // ── Keyboard / Mouse button ─────────────────────────────────────────────

    /** Send a keyboard key event to the host. */
    fun keyboardEvent(buttonDown: Boolean, keyCode: Short)

    /** Send a mouse button event to the host. */
    fun mouseButtonEvent(buttonId: Int, down: Boolean)

    // ── Virtual keyboard toggles ────────────────────────────────────────────

    /** Toggle the PC-side virtual keyboard overlay (Moonlight custom keyboard). */
    fun toggleVirtualKeyboard()

    /** Toggle the Android soft keyboard (IME). */
    fun toggleKeyboard()

    // ── Touch override / pan-zoom ──────────────────────────────────────────

    /** Whether touch-override (pan/zoom) is currently enabled. */
    fun getisTouchOverrideEnabled(): Boolean

    /** Enable or disable touch-override (pan/zoom). */
    fun setisTouchOverrideEnabled(enabled: Boolean)

    // ── Touch routing ───────────────────────────────────────────────────────

    /** Route a motion event through the touch input handler. */
    fun getHandleMotionEvent(streamView: StreamView, event: android.view.MotionEvent): Boolean

    /** End the streaming session (equivalent to finish()). */
    fun disconnect()
}
