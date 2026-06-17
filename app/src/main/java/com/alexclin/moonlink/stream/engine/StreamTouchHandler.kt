package com.alexclin.moonlink.stream.engine

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.NativeTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import kotlin.math.sqrt

/**
 * 新版串流的触控处理器 — 完整版。
 *
 * 对标旧 TouchInputHandler，管理 AbsoluteTouchContext/RelativeTouchContext/NativeTouchContext，
 * 处理双击、双指右键、多指手势、坐标归一化等。
 */
class StreamTouchHandler(
    private val conn: NvConnection,
    private val prefConfig: PreferenceConfiguration,
    private val targetView: View,
    /** 三指/空格键弹出键盘的回调 */
    var onToggleKeyboard: (() -> Unit)? = null,
    /** 光标可见性变化回调（用于与 InputCaptureProvider 联动） */
    var onCursorVisibilityChanged: ((visible: Boolean) -> Unit)? = null,
) {

    companion object {
        const val TOUCH_CONTEXT_LENGTH = 2
        private const val TWO_FINGER_TAP_THRESHOLD = 100L
        private const val TWO_FINGER_MOVE_THRESHOLD = 20f
        private const val MULTI_FINGER_TAP_THRESHOLD = 300L
    }

    val absoluteTouchContextMap = arrayOfNulls<TouchContext>(TOUCH_CONTEXT_LENGTH)
    val relativeTouchContextMap = arrayOfNulls<TouchContext>(TOUCH_CONTEXT_LENGTH)
    var touchContextMap: Array<TouchContext?> = absoluteTouchContextMap

    var cursorVisible = false
        set(value) {
            if (field != value) {
                field = value
                onCursorVisibilityChanged?.invoke(value)
            }
        }

    // 双指右键检测
    private var twoFingerDownTime = 0L
    private var firstFingerUpTime = 0L
    private var twoFingerTapPending = false
    private var twoFingerMoved = false
    private var twoFingerStartX = 0f
    private var twoFingerStartY = 0f

    // 多指键盘
    private var multiFingerDownTime = 0L

    // 鼠标状态
    private var lastButtonState = 0

    // 增强触控 - Pointer 管理
    private val nativeTouchPointerMap = HashMap<Int, NativeTouchContext.Pointer>()

    fun initTouchContexts() {
        for (i in 0 until TOUCH_CONTEXT_LENGTH) {
            absoluteTouchContextMap[i] = AbsoluteTouchContext(conn, i, targetView)
            relativeTouchContextMap[i] = RelativeTouchContext(conn, i, targetView, prefConfig)
        }
        touchContextMap = if (!prefConfig.touchscreenTrackpad) absoluteTouchContextMap else relativeTouchContextMap
    }

    fun setTouchMode(enableRelative: Boolean) {
        touchContextMap = if (enableRelative) relativeTouchContextMap else absoluteTouchContextMap
    }

    fun setEnhancedTouch(enable: Boolean) {
        prefConfig.enableEnhancedTouch = enable
        if (enable) prefConfig.enableNativeMousePointer = false
    }

    // ═════════════════════════════════════════════════════
    // 主入口
    // ═════════════════════════════════════════════════════

    fun handleMotionEvent(view: View?, event: MotionEvent): Boolean {
        val source = event.source

        if (prefConfig.enableNativeMousePointer && (source and InputDevice.SOURCE_CLASS_POINTER) != 0) {
            return handleNativeMousePointer(event)
        }
        if ((source and InputDevice.SOURCE_TOUCHSCREEN) != 0) {
            return handleTouchScreen(view, event)
        }
        if ((source and InputDevice.SOURCE_CLASS_POINTER) != 0) {
            return handleMouseEvent(event)
        }
        return false
    }

    // ═════════════════════════════════════════════════════
    // 本地鼠标指针
    // ═════════════════════════════════════════════════════

    private fun handleNativeMousePointer(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> conn.sendMouseMove(event.rawX.toInt().toShort(), event.rawY.toInt().toShort())
            MotionEvent.ACTION_SCROLL -> {
                conn.sendMouseHighResScroll((event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120).toInt().toShort())
                conn.sendMouseHighResHScroll((event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120).toInt().toShort())
            }
            else -> {
                val changed = event.buttonState xor lastButtonState
                if (changed and MotionEvent.BUTTON_PRIMARY != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0)
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                    else
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                }
                if (changed and MotionEvent.BUTTON_SECONDARY != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0)
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                    else
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                }
                if (changed and MotionEvent.BUTTON_TERTIARY != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_TERTIARY != 0)
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
                    else
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
                }
                lastButtonState = event.buttonState
            }
        }
        return true
    }

    // ═════════════════════════════════════════════════════
    // 鼠标事件
    // ═════════════════════════════════════════════════════

    private fun handleMouseEvent(event: MotionEvent): Boolean {
        val changed = event.buttonState xor lastButtonState
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            conn.sendMouseHighResScroll((event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120).toInt().toShort())
            conn.sendMouseHighResHScroll((event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120).toInt().toShort())
        }
        if (changed and MotionEvent.BUTTON_PRIMARY != 0) {
            if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
            else conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
        }
        if (changed and MotionEvent.BUTTON_SECONDARY != 0) {
            if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
            else conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
        }
        lastButtonState = event.buttonState
        return true
    }

    // ═════════════════════════════════════════════════════
    // 触摸屏事件
    // ═════════════════════════════════════════════════════

    private fun handleTouchScreen(view: View?, event: MotionEvent): Boolean {
        // 增强触控
        if (!prefConfig.touchscreenTrackpad && prefConfig.enableEnhancedTouch) {
            if (trySendTouchEvent(view, event)) return true
        }

        val actionIdx = event.actionIndex
        val action = event.actionMasked
        val pc = event.pointerCount

        // 三指 → 键盘
        if (action == MotionEvent.ACTION_POINTER_DOWN && pc == 3) {
            multiFingerDownTime = event.eventTime
            for (ctx in touchContextMap) ctx?.cancelTouch()
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (action == MotionEvent.ACTION_DOWN) multiFingerDownTime = 0
                if (pc == 2 && prefConfig.touchscreenTrackpad) {
                    twoFingerDownTime = event.eventTime
                    twoFingerStartX = event.getX(0); twoFingerStartY = event.getY(0)
                    twoFingerMoved = false; twoFingerTapPending = false
                }
                for (tc in touchContextMap) tc?.setPointerCount(pc)
                val ctx = ctxOf(actionIdx) ?: return false
                val c = normXY(view, event.getX(actionIdx), event.getY(actionIdx))
                ctx.touchDownEvent(c[0].toInt(), c[1].toInt(), event.eventTime, true)
                if (action == MotionEvent.ACTION_POINTER_DOWN && pc >= 2) {
                    val ctx2 = ctxOf(1) ?: return false
                    val c2 = normXY(view, event.getX(1), event.getY(1))
                    ctx2.touchDownEvent(c2[0].toInt(), c2[1].toInt(), event.eventTime, false)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pc == 2 && !twoFingerMoved && prefConfig.touchscreenTrackpad) {
                    if (sqrt((event.getX(0) - twoFingerStartX).let { it * it } +
                             (event.getY(0) - twoFingerStartY).let { it * it }) > TWO_FINGER_MOVE_THRESHOLD)
                        twoFingerMoved = true
                }
                for (h in 0 until event.historySize) {
                    for (tc in touchContextMap) {
                        if (tc != null && tc.getActionIndex() < pc) {
                            val hc = normXY(view, event.getHistoricalX(tc.getActionIndex(), h), event.getHistoricalY(tc.getActionIndex(), h))
                            tc.touchMoveEvent(hc[0].toInt(), hc[1].toInt(), event.getHistoricalEventTime(h))
                        }
                    }
                }
                for (tc in touchContextMap) {
                    if (tc != null && tc.getActionIndex() < pc) {
                        val cc = normXY(view, event.getX(tc.getActionIndex()), event.getY(tc.getActionIndex()))
                        tc.touchMoveEvent(cc[0].toInt(), cc[1].toInt(), event.eventTime)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val c = normXY(view, event.getX(actionIdx), event.getY(actionIdx))

                // 触控板双指 → 右键
                if (multiFingerDownTime == 0L && pc == 2 && !twoFingerMoved && prefConfig.touchscreenTrackpad) {
                    if (event.eventTime - twoFingerDownTime < TWO_FINGER_TAP_THRESHOLD) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                        twoFingerTapPending = false; twoFingerMoved = true
                        ctxOf(actionIdx)?.cancelTouch()
                        for (tc in touchContextMap) tc?.setPointerCount(pc - 1)
                        return true
                    } else { firstFingerUpTime = event.eventTime; twoFingerTapPending = true }
                }
                if (pc == 1 && (Build.VERSION.SDK_INT < 33 || (event.flags and MotionEvent.FLAG_CANCELED) == 0)) {
                    if (twoFingerTapPending && !twoFingerMoved && prefConfig.touchscreenTrackpad) {
                        if (event.eventTime - firstFingerUpTime < TWO_FINGER_TAP_THRESHOLD) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                            twoFingerTapPending = false
                            for (tc in touchContextMap) { tc?.cancelTouch(); tc?.setPointerCount(0) }
                            return true
                        }
                    }
                    twoFingerTapPending = false
                    if (event.eventTime - multiFingerDownTime < MULTI_FINGER_TAP_THRESHOLD) {
                        // 多指（三指）点击 → 弹出键盘
                        onToggleKeyboard?.invoke()
                        return true
                    }
                }
                val ctx = ctxOf(actionIdx) ?: return false
                if (Build.VERSION.SDK_INT >= 33 && (event.flags and MotionEvent.FLAG_CANCELED) != 0) ctx.cancelTouch()
                else ctx.touchUpEvent(c[0].toInt(), c[1].toInt(), event.eventTime)
                for (tc in touchContextMap) tc?.setPointerCount(pc - 1)
                if (actionIdx == 0 && pc > 1 && !ctx.isCancelled()) {
                    val ctx1 = ctxOf(1) ?: return false
                    val c1 = normXY(view, event.getX(1), event.getY(1))
                    ctx1.touchDownEvent(c1[0].toInt(), c1[1].toInt(), event.eventTime, false)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                for (tc in touchContextMap) { tc?.cancelTouch(); tc?.setPointerCount(0) }
            }
            else -> return false
        }
        return true
    }

    // ═════════════════════════════════════════════════════
    // 增强触控 — 发送原生触摸事件
    // ═════════════════════════════════════════════════════

    private fun trySendTouchEvent(view: View?, event: MotionEvent): Boolean {
        val type = touchEventType(event)
        if (type < 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (prefConfig.enableEnhancedTouch)
                        nativeTouchPointerMap[event.getPointerId(i)]?.updatePointerCoords(event, i)
                    if (!sendTouchOne(view, event, type, i)) return false
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return conn.sendTouchEvent(
                MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, 0, 0f, 0f, 0f, 0f, 0f, MoonBridge.LI_ROT_UNKNOWN
            ) != MoonBridge.LI_ERR_UNSUPPORTED
            else -> {
                val idx = event.actionIndex
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                        if (prefConfig.enableEnhancedTouch) {
                            val ptr = NativeTouchContext.Pointer(event)
                            nativeTouchPointerMap[ptr.pointerId] = ptr
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (prefConfig.enableEnhancedTouch)
                            nativeTouchPointerMap.remove(event.getPointerId(idx))
                    }
                    else -> {}
                }
                return sendTouchOne(view, event, type, idx)
            }
        }
    }

    private fun sendTouchOne(view: View?, event: MotionEvent, type: Byte, idx: Int): Boolean {
        val n = norm01(view, event.getX(idx), event.getY(idx))
        val p = event.getPressure(idx).coerceIn(0f, 1f)
        val s = event.getSize(idx).coerceIn(0f, 1f)
        return conn.sendTouchEvent(type, event.getPointerId(idx), n[0], n[1], p, s, s, 0) != MoonBridge.LI_ERR_UNSUPPORTED
    }

    private fun touchEventType(event: MotionEvent): Byte = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> MoonBridge.LI_TOUCH_EVENT_DOWN
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
            if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) MoonBridge.LI_TOUCH_EVENT_CANCEL
            else MoonBridge.LI_TOUCH_EVENT_UP
        MotionEvent.ACTION_MOVE -> MoonBridge.LI_TOUCH_EVENT_MOVE
        MotionEvent.ACTION_CANCEL -> MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL
        else -> -1
    }

    // ═════════════════════════════════════════════════════
    // 坐标归一化
    // ═════════════════════════════════════════════════════

    /** 归一化到视图像素坐标（供 TouchContext 使用） */
    private fun normXY(view: View?, rawX: Float, rawY: Float): FloatArray {
        val v = view ?: return floatArrayOf(rawX, rawY)
        val sx = v.scaleX; val sy = v.scaleY
        if (sx == 0f || sy == 0f) return floatArrayOf(rawX, rawY)
        return floatArrayOf((rawX - v.x) / sx, (rawY - v.y) / sy)
    }

    /** 归一化到 0..1（供 sendTouchEvent 使用） */
    private fun norm01(view: View?, rawX: Float, rawY: Float): FloatArray {
        val v = view ?: return floatArrayOf(rawX / targetView.width, rawY / targetView.height)
        val w = v.width; val h = v.height
        if (w == 0 || h == 0) return floatArrayOf(0f, 0f)
        val sx = v.scaleX; val sy = v.scaleY
        if (sx == 0f || sy == 0f) return floatArrayOf(rawX / w, rawY / h)
        return floatArrayOf(
            ((rawX - v.x) / sx / w).coerceIn(0f, 1f),
            ((rawY - v.y) / sy / h).coerceIn(0f, 1f)
        )
    }

    private fun ctxOf(idx: Int): TouchContext? = if (idx < touchContextMap.size) touchContextMap[idx] else null
}
