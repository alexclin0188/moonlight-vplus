package com.alexclin.moonlink.android.stream.engine

import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.NativeTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.alexclin.moonlink.android.util.LimeLog
import kotlin.math.roundToInt
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

    /** 输入捕获提供者（用于获取指针捕获下的相对鼠标坐标） */
    var inputCaptureProvider: InputCaptureProvider? = null

    /** 本地光标位置变化回调（Compose 光标覆盖层驱动） */
    var onLocalCursorMoved: ((x: Float, y: Float) -> Unit)? = null

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

    // 按钮事件时上次发送的光标位置（避免重复 sendMousePosition）
    private var lastSentBtnX = -1
    private var lastSentBtnY = -1

    /** 容器尺寸（Box/屏幕像素尺寸），由 StreamActivity.onSizeChanged 更新，
     * 用于与 Box.pointerInput 路径使用统一公式计算视频画面区域。
     * 仅主线程读写，无需 @Volatile。 */
    var containerWidth: Int = 0
    var containerHeight: Int = 0

    fun initTouchContexts() {
        for (i in 0 until TOUCH_CONTEXT_LENGTH) {
            absoluteTouchContextMap[i] = AbsoluteTouchContext(conn, i, targetView)
            RelativeTouchContext(conn, i, targetView, prefConfig).also {
                it.sense = prefConfig.touchpadSensitivity.coerceIn(1, 200) / 100.0
                it.onCursorPositionChanged = { x, y -> onLocalCursorMoved?.invoke(x, y) }
                relativeTouchContextMap[i] = it
            }
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

        // 优先检测 buttonState：外部鼠标点击可能以 SOURCE_TOUCHSCREEN 到达，
        // 但 buttonState 非零意味着事件携带鼠标按钮信息，应走鼠标处理路径
        if (prefConfig.enableNativeMousePointer && (source and InputDevice.SOURCE_CLASS_POINTER) != 0) {
            return handleNativeMousePointer(event)
        }
        // 精确匹配 SOURCE_TOUCHSCREEN（位掩码检查会误伤 SOURCE_MOUSE，
        // 因为两者共享 SOURCE_CLASS_POINTER 位导致 & SOURCE_TOUCHSCREEN 非零）
        if (source == InputDevice.SOURCE_TOUCHSCREEN) {
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
        val isRealPointer = event.source != InputDevice.SOURCE_TOUCHSCREEN

        // 使用与 Box.pointerInput 完全相同的公式计算视频画面区域：
        // videoSize = targetView 实测尺寸, offset = (containerSize - videoSize) / 2
        val cw = containerWidth.coerceAtLeast(1)
        val ch = containerHeight.coerceAtLeast(1)
        val svW = targetView.width.coerceAtLeast(1)
        val svH = targetView.height.coerceAtLeast(1)
        val ox = (cw - svW) / 2
        val oy = (ch - svH) / 2

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {}
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if (isRealPointer) {
                    val rawX = event.rawX.roundToInt()
                    val rawY = event.rawY.roundToInt()
                    if (rawX >= ox && rawX < ox + svW && rawY >= oy && rawY < oy + svH) {
                        val videoX = rawX - ox
                        val videoY = rawY - oy
                        conn.sendMousePosition(
                            videoX.toShort(), videoY.toShort(),
                            svW.toShort(), svH.toShort()
                        )
                    }
                }
            }
            MotionEvent.ACTION_SCROLL -> {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                // 只发送 sendMouseScroll（内部已调用 sendMouseHighResScroll），
                // 不再重复调用 sendMouseHighResScroll，避免双包抵消
                if (vScroll != 0f) {
                    conn.sendMouseScroll((-vScroll).roundToInt().toByte())
                }
                if (hScroll != 0f) {
                    conn.sendMouseHScroll((-hScroll).roundToInt().toByte())
                }
            }
            else -> {
                if (isRealPointer) {
                    val rawX = event.rawX.roundToInt()
                    val rawY = event.rawY.roundToInt()
                    if (rawX >= ox && rawX < ox + svW && rawY >= oy && rawY < oy + svH) {
                        val videoX = rawX - ox
                        val videoY = rawY - oy
                        if (videoX != lastSentBtnX || videoY != lastSentBtnY) {
                            conn.sendMousePosition(
                                videoX.toShort(), videoY.toShort(),
                                svW.toShort(), svH.toShort()
                            )
                            lastSentBtnX = videoX
                            lastSentBtnY = videoY
                        }
                    }
                }
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
                if (changed and MotionEvent.BUTTON_BACK != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_BACK != 0)
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1)
                    else
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1)
                }
                if (changed and MotionEvent.BUTTON_FORWARD != 0) {
                    if (event.buttonState and MotionEvent.BUTTON_FORWARD != 0)
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2)
                    else
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2)
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
        // 鼠标移动（相对坐标，指针捕获模式下有效）
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_MOVE) {
            val cap = inputCaptureProvider
            if (cap != null && cap.eventHasRelativeMouseAxes(event)) {
                val dx = cap.getRelativeAxisX(event).toInt()
                val dy = cap.getRelativeAxisY(event).toInt()
                if (dx != 0 || dy != 0) {
                    conn.sendMouseMove(dx.toShort(), dy.toShort())
                }
            }
        }
        val changed = event.buttonState xor lastButtonState
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            // 只发送 sendMouseScroll（内部已调用 sendMouseHighResScroll）
            if (vScroll != 0f) {
                conn.sendMouseScroll((-vScroll).roundToInt().toByte())
            }
            if (hScroll != 0f) {
                conn.sendMouseHScroll((-hScroll).roundToInt().toByte())
            }
        }
        if (changed and MotionEvent.BUTTON_PRIMARY != 0) {
            if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
            else conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
        }
        if (changed and MotionEvent.BUTTON_SECONDARY != 0) {
            if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
            else conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
        }
        if (changed and MotionEvent.BUTTON_TERTIARY != 0) {
            if (event.buttonState and MotionEvent.BUTTON_TERTIARY != 0) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
            else conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
        }
        if (changed and MotionEvent.BUTTON_BACK != 0) {
            if (event.buttonState and MotionEvent.BUTTON_BACK != 0) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1)
            else conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1)
        }
        if (changed and MotionEvent.BUTTON_FORWARD != 0) {
            if (event.buttonState and MotionEvent.BUTTON_FORWARD != 0) conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2)
            else conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2)
        }
        lastButtonState = event.buttonState
        return true
    }

    // ═════════════════════════════════════════════════════
    // 触摸屏事件
    // ═════════════════════════════════════════════════════

    private fun handleTouchScreen(view: View?, event: MotionEvent): Boolean {
        // 触摸事件同步设置：false=无缓冲调度（低延迟），true=跟随显示刷新
        if (!prefConfig.syncTouchEventWithDisplay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.requestUnbufferedDispatch(event)
        }
        // 增强触控
        if (!prefConfig.touchscreenTrackpad && prefConfig.enableEnhancedTouch) {
            if (trySendTouchEvent(view, event)) return true
        }

        val actionIdx = event.actionIndex
        val action = event.actionMasked
        val pc = event.pointerCount

        // 多指 → 键盘（手指数由 prefConfig 控制；0 或负数=禁用）
        val toggleFingers = prefConfig.nativeTouchFingersToToggleKeyboard
        if (toggleFingers > 0 && action == MotionEvent.ACTION_POINTER_DOWN && pc == toggleFingers) {
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
        // event.getX()/getY() 已经相对于 view 自身，无需减去 view.x 或除以 scale
        return floatArrayOf(rawX, rawY)
    }

    /** 归一化到 0..1（供 sendTouchEvent 使用） */
    private fun norm01(view: View?, rawX: Float, rawY: Float): FloatArray {
        val v = view ?: return floatArrayOf(rawX / targetView.width, rawY / targetView.height)
        val w = v.width; val h = v.height
        if (w == 0 || h == 0) return floatArrayOf(0f, 0f)
        // event.getX()/getY() 已相对于 view 自身，直接归一化即可
        return floatArrayOf(
            (rawX / w).coerceIn(0f, 1f),
            (rawY / h).coerceIn(0f, 1f)
        )
    }

    private fun ctxOf(idx: Int): TouchContext? = if (idx < touchContextMap.size) touchContextMap[idx] else null
}
