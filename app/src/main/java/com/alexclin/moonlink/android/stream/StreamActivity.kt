package com.alexclin.moonlink.android.stream

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import com.limelight.ui.StreamView
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.android.R
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.StreamOverlay
import com.alexclin.moonlink.android.stream.ui.overlay.KeyMappingOverlay
import com.alexclin.moonlink.android.stream.ui.editor.EditorElement
import com.alexclin.moonlink.android.stream.ui.editor.ElementType
import com.alexclin.moonlink.android.theme.MoonLinkTheme
import com.limelight.LimeLog
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.services.StreamNotificationService

/**
 * MoonLink 新版串流 Activity。
 *
 * 使用 Compose 构建 UI，通过 [StreamEngine] 封装底层串流连接。
 * 与旧版 [com.limelight.Game] 并行存在，不修改任何旧代码。
 */
class StreamActivity : ComponentActivity() {

    private lateinit var engine: StreamEngine
    private var wasPaused = false
    private var wasBackgrounded = false
    private var lastBackPressTime = 0L
    private val backPressDebounceMs = 300L

    // ── Picture-in-Picture ──

    /** PiP 抑制引用计数（对话框打开时 +1，关闭时 -1，>0 时不进入 PiP） */
    private var suppressPipRefCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏（隐藏状态栏 + 导航栏）
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                window.setDecorFitsSystemWindows(false)
            } catch (_: NoSuchMethodError) {
                // 部分定制 ROM（如华为/小米早期版本）声称 >= R 但实际无此方法
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // 设置窗口背景为黑色，非视频区域（letterbox/pillarbox）显示纯黑
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // API 26+ 用 adjustResize（系统自动 resize），API 25- 用 adjustNothing
        //（避免 adjustResize + 沉浸式全屏在低版本上的兼容问题）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }

        // 默认横屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        // 初始化串流引擎
        engine = StreamEngine(this)
        if (!engine.initialize(intent)) {
            finish()
            return
        }

        engine.onStreamEnded = {
            if (!isFinishing) {
                finish()
            }
        }

        var connectionStage by mutableStateOf<String?>("")

        engine.onStageUpdate = { stage, complete, failed ->
            connectionStage = when {
                failed -> null
                complete -> null
                else -> stage
            }
        }

        setContent {
            val context = LocalContext.current
            val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
            val darkTheme = when (prefs.getString("list_theme_mode", "dark") ?: "dark") {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            MoonLinkTheme(darkTheme = darkTheme) {
                // 视频画面居中容器：StreamView 的 onMeasure() 根据 desiredAspectRatio
                // 约束自身大小，实现原始宽高比；非视频区域显示窗口黑色背景。
                Box(modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center) {
                    // 串流画面 StreamView（自定义 SurfaceView，支持 aspect ratio onMeasure）
                    // 触摸事件监听器引用（供 update 复用）
                    val touchListener = remember { View.OnTouchListener { view, event ->
                        if (engine.isFullScreenPageActive) false
                        else engine.touchHandler?.handleMotionEvent(view, event) ?: false
                    }}
                    val genericMotionListener = remember { View.OnGenericMotionListener { view, event ->
                        if (engine.isFullScreenPageActive) false
                        else engine.touchHandler?.handleMotionEvent(view, event) ?: false
                    }}

                    AndroidView(
                        factory = { ctx ->
                            StreamView(ctx).also { sv ->
                                sv.id = R.id.surfaceView
                                engine.surfaceView = sv
                                engine.attachSurfaceView(sv)
                                sv.holder.addCallback(engine.surfaceCallback)
                                sv.setOnTouchListener(touchListener)
                                sv.setOnGenericMotionListener(genericMotionListener)
                            }
                        },
                        update = { sv ->
                            // 根据 stretchVideo 即时更新画面比例（纯客户端，无需重启串流）
                            if (engine.stretchVideo) {
                                sv.setDesiredAspectRatio(0.0)
                                sv.holder.setFixedSize(
                                    engine.prefConfig.width.coerceAtLeast(1),
                                    engine.prefConfig.height.coerceAtLeast(1)
                                )
                            } else {
                                val aspect = engine.prefConfig.width.toFloat() /
                                    engine.prefConfig.height.toFloat()
                                sv.setDesiredAspectRatio(aspect.toDouble())
                                sv.holder.setSizeFromLayout()
                            }
                            sv.requestLayout()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 按键映射覆盖层（Compose 原生，响应式展示）
                    val overlayElements by remember { engine.currentOverlayElements }

                    // ── 按键输入状态追踪（所有元素类型共用） ──
                    val btnState = remember { mutableStateOf(0) }       // buttonFlags
                    val lsX = remember { mutableStateOf(0.toShort()) }  // leftStickX
                    val lsY = remember { mutableStateOf(0.toShort()) }  // leftStickY
                    val rsX = remember { mutableStateOf(0.toShort()) }  // rightStickX
                    val rsY = remember { mutableStateOf(0.toShort()) }  // rightStickY
                    val ltV = remember { mutableStateOf(0.toByte()) }   // leftTrigger
                    val rtV = remember { mutableStateOf(0.toByte()) }   // rightTrigger

                    fun sendFullState() {
                        engine.controllerHandler?.reportOscState(
                            btnState.value, lsX.value, lsY.value, rsX.value, rsY.value, ltV.value, rtV.value
                        )
                    }

                    // ── 解析元素 value 为控制器标志位（十进制解析，匹配旧 Crown） ──
                    fun parseValueToFlag(value: String): Int {
                        return when {
                            value.startsWith("g") -> value.substring(1).toIntOrNull() ?: 0
                            else -> 0
                        }
                    }

                    // ── 摇杆辅助：根据 relX/relY 和元素半径计算轴值 ──
                    // 死区由 el.sense（元素自身灵敏度，0-100）决定：sense 越大→死区越小→越灵敏
                    // formula: sense=100→0.05(5%), sense=50→0.275(27.5%), sense=1→0.5(50%)
                    fun computeStickAxis(relX: Float, relY: Float, radius: Float, element: EditorElement): Pair<Short, Short> {
                        val normalizedSense = element.sense.coerceIn(1, 100) / 100f
                        val deadZoneRatio = 0.05f + (1f - normalizedSense) * 0.45f
                        val maxRadius = radius.coerceAtLeast(1f)
                        var nx = (relX / maxRadius).coerceIn(-1f, 1f)
                        var ny = (relY / maxRadius).coerceIn(-1f, 1f)
                        if (kotlin.math.abs(nx) < deadZoneRatio) nx = 0f
                        if (kotlin.math.abs(ny) < deadZoneRatio) ny = 0f
                        return Pair((nx * 32767).toInt().toShort(), (ny * 32767).toInt().toShort())
                    }

                    // ── D-Pad 方向位掩码（与旧 Crown DigitalPad 一致） ──
                    val DPAD_LEFT = 1
                    val DPAD_RIGHT = 2
                    val DPAD_UP = 4
                    val DPAD_DOWN = 8

                    // ── D-Pad 方向检测（旧 Crown 使用 33%/66% 网格分割，支持对角线） ──
                    fun computeDpadBitmask(relX: Float, relY: Float, w: Int, h: Int): Int {
                        // 将 relX/relY（相对于元素中心）转换为元素左上角为原点的坐标
                        val ex = relX + w / 2f
                        val ey = relY + h / 2f
                        var mask = 0
                        if (ex < w * 0.33f) mask = mask or DPAD_LEFT
                        if (ex > w * 0.66f) mask = mask or DPAD_RIGHT
                        if (ey > h * 0.66f) mask = mask or DPAD_DOWN
                        if (ey < h * 0.33f) mask = mask or DPAD_UP
                        return mask
                    }

                    // ── GroupButton 子元素显隐状态 ──
                    val groupButtonHiddenIds = remember { mutableStateOf<Set<Long>>(emptySet()) }

                    // 十字方向键当前激活的方向（elementId → 方向），用于 MOVE 时的方向变更检测
                    val activeDpadDirections = remember { HashMap<Long, Int>() }
                    // 摇杆当前激活的方向集合（elementId → 方向集合），支持对角线
                    val activeStickDirections = remember { HashMap<Long, MutableSet<String>>() }
                    // WheelPad 状态：元素ID → {是否已激活(弹窗模式用), 当前选中的段索引}
                    val wheelActiveMap = remember { HashMap<Long, Boolean>() }
                    val wheelActiveIndex = remember { HashMap<Long, Int>() }
                    // 摇杆上次点击时间（元素ID → 时间戳），用于双击 middleValue 检测
                    val stickLastClickTime = remember { HashMap<Long, Long>() }
                    // MovableButton 触控板模式：追踪上次触摸位置（元素ID → Pair(relX, relY)）
                    val trackpadLastPos = remember { HashMap<Long, Pair<Float, Float>>() }
                    // MovableButton 摇杆模式（mode=1）：记录首次触摸偏移（元素ID → Pair(relX, relY)）
                    val joystickFirstTouch = remember { HashMap<Long, Pair<Float, Float>>() }

                    // ── 键盘按键翻译器（用于 kXX 格式按键值） ──
                    val keyboardTranslator = remember { KeyboardTranslator() }
                    // 按键重复 Handler（参考原 Crown 50ms+75ms 双调度）
                    val repeatHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
                    val keyboardRepeatMap = remember { HashMap<Short, java.lang.Runnable>() }
                    // 鼠标按键重复（btnId → Runnable），手柄状态重复（单一共享）
                    val mouseRepeatMap = remember { HashMap<Int, java.lang.Runnable>() }
                    val gamepadRepeatRunnable = remember { mutableStateOf<java.lang.Runnable?>(null) }
                    val scrollRepeatRunnable = remember { mutableStateOf<java.lang.Runnable?>(null) }

                    // ── 振动反馈（参考原 Crown buttonVibrator） ──
                    fun triggerVibration() {
                        try {
                            val vibrator = engine.activity.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator?
                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(20)
                                }
                            }
                        } catch (_: Exception) { }
                    }

                    /** 发送键盘按键事件（含 50ms 重复逻辑，参考原 Crown） */
                    fun sendKeyboardKey(value: String, isPressed: Boolean) {
                        val doSend: (Short, Byte) -> Unit = { code, action ->
                            engine.conn?.sendKeyboardInput(code, action, 0, 0)
                        }
                        if (value.startsWith("k") && !value.startsWith("k0x")) {
                            val crownIdx = value.substring(1).toIntOrNull()
                            if (crownIdx != null) {
                                val gfeKeyCode = keyboardTranslator.translate(crownIdx, -1)
                                if (gfeKeyCode.toInt() != 0) {
                                    // 取消旧重复
                                    keyboardRepeatMap.remove(gfeKeyCode)?.let(repeatHandler::removeCallbacks)
                                    val action = if (isPressed) KeyboardPacket.KEY_DOWN else KeyboardPacket.KEY_UP
                                    doSend(gfeKeyCode, action)
                                    if (isPressed) {
                                    val r = object : java.lang.Runnable {
                                        override fun run() {
                                            doSend(gfeKeyCode, KeyboardPacket.KEY_DOWN)
                                            repeatHandler.postDelayed(this, 50)
                                        }
                                    }
                                    keyboardRepeatMap[gfeKeyCode] = r
                                    repeatHandler.postDelayed(r, 50)
                                    repeatHandler.postDelayed(r, 75)
                                    }
                                }
                            }
                        } else if (value.startsWith("k0x")) {
                            val hexCode = value.substring(3).toIntOrNull(16)
                            if (hexCode != null && hexCode != 0) {
                                val shortCode = hexCode.toShort()
                                keyboardRepeatMap.remove(shortCode)?.let(repeatHandler::removeCallbacks)
                                val action = if (isPressed) KeyboardPacket.KEY_DOWN else KeyboardPacket.KEY_UP
                                doSend(shortCode, action)
                                if (isPressed) {
                                    val r = object : java.lang.Runnable {
                                        override fun run() {
                                            doSend(shortCode, KeyboardPacket.KEY_DOWN)
                                            repeatHandler.postDelayed(this, 50)
                                        }
                                    }
                                    keyboardRepeatMap[shortCode] = r
                                    repeatHandler.postDelayed(r, 50)
                                    repeatHandler.postDelayed(r, 75)
                                }
                            }
                        } else if (value == "SU") {
                            // 鼠标滚轮上 — 按下时持续滚动（参考原 Crown：初始延迟 150ms，重复间隔 100ms）
                            scrollRepeatRunnable.value?.let(repeatHandler::removeCallbacks)
                            scrollRepeatRunnable.value = null
                            if (isPressed) {
                                engine.mouseVScroll(1.toByte())
                                val r = object : java.lang.Runnable {
                                    override fun run() {
                                        engine.mouseVScroll(1.toByte())
                                        repeatHandler.postDelayed(this, 100)
                                    }
                                }
                                scrollRepeatRunnable.value = r
                                repeatHandler.postDelayed(r, 150)
                            }
                        } else if (value == "SD") {
                            scrollRepeatRunnable.value?.let(repeatHandler::removeCallbacks)
                            scrollRepeatRunnable.value = null
                            if (isPressed) {
                                engine.mouseVScroll((-1).toByte())
                                val r = object : java.lang.Runnable {
                                    override fun run() {
                                        engine.mouseVScroll((-1).toByte())
                                        repeatHandler.postDelayed(this, 100)
                                    }
                                }
                                scrollRepeatRunnable.value = r
                                repeatHandler.postDelayed(r, 150)
                            }
                        } else {
                            // 特殊功能键 & gb 组引用（非键盘/鼠标/手柄值的兜底处理）
                            if (!isPressed) return@sendKeyboardKey
                            when {
                                value.startsWith("gb") -> {
                                    // gb{id} 格式：切换对应 GroupButton 的子元素显隐
                                    val groupId = value.substring(2).toLongOrNull()
                                    if (groupId != null && groupId > 0) {
                                        val childIds = overlayElements
                                            .find { it.elementId == groupId }
                                            ?.value
                                            ?.split(",")
                                            ?.mapNotNull { it.trim().toLongOrNull() }
                                            ?.filter { it != -1L }
                                            ?.toSet() ?: emptySet()
                                        if (childIds.isNotEmpty()) {
                                            val currentHidden = groupButtonHiddenIds.value
                                            val anyHidden = childIds.any { it in currentHidden }
                                            groupButtonHiddenIds.value = if (anyHidden) {
                                                currentHidden - childIds
                                            } else {
                                                currentHidden + childIds
                                            }
                                        }
                                    }
                                }
                                value == "MMS" -> engine.applyTouchMode(if (engine.prefConfig.enableEnhancedTouch) 1 else 0)
                                value == "CMS" -> engine.applyTouchMode(1)
                                value == "TPM" -> engine.applyTouchMode(2)
                                value == "MTM" -> engine.applyTouchMode(0)
                                value == "PKS" -> engine.toggleKeyboard()
                                value == "PCK" -> engine.sendKeyboardShortcut(0, 0) // 主机键盘开关
                                value == "ACK" -> { val imm = engine.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager; imm?.toggleSoftInput(0, 0) }
                                value == "OGM" -> engine.toggleVirtualController()
                            }
                        }
                    }

                    // ── 发送单个方向键值（支持 k/g/m/lt/rt 前缀，匹配旧 Crown） ──
                    fun sendDirectionValue(value: String, isPressed: Boolean) {
                        when {
                            value == "lt" -> ltV.value = if (isPressed) 0xFF.toByte() else 0
                            value == "rt" -> rtV.value = if (isPressed) 0xFF.toByte() else 0
                            value.startsWith("k") -> sendKeyboardKey(value, isPressed)
                            value.startsWith("g") -> {
                                val flag = parseValueToFlag(value)
                                if (flag != 0) {
                                    btnState.value = if (isPressed) btnState.value or flag
                                        else btnState.value and flag.inv()
                                    sendFullState()
                                }
                            }
                            value.startsWith("m") -> {
                                val btnId = value.substring(1).toIntOrNull()
                                if (btnId != null) {
                                    if (isPressed) engine.conn?.sendMouseButtonDown(btnId.toByte())
                                    else engine.conn?.sendMouseButtonUp(btnId.toByte())
                                }
                            }
                        }
                    }

                    // ── 处理 D-Pad 位掩码变化（参考旧 Crown: XOR 检测每个方向的变化） ──
                    fun processDpadBitmaskChange(oldMask: Int, newMask: Int, el: EditorElement) {
                        val changed = oldMask xor newMask
                        if ((changed and DPAD_LEFT) != 0) {
                            val v = el.leftValue; if (v.isNotEmpty()) sendDirectionValue(v, (newMask and DPAD_LEFT) != 0)
                        }
                        if ((changed and DPAD_RIGHT) != 0) {
                            val v = el.rightValue; if (v.isNotEmpty()) sendDirectionValue(v, (newMask and DPAD_RIGHT) != 0)
                        }
                        if ((changed and DPAD_UP) != 0) {
                            val v = el.upValue; if (v.isNotEmpty()) sendDirectionValue(v, (newMask and DPAD_UP) != 0)
                        }
                        if ((changed and DPAD_DOWN) != 0) {
                            val v = el.downValue; if (v.isNotEmpty()) sendDirectionValue(v, (newMask and DPAD_DOWN) != 0)
                        }
                    }

                    // ── 按方向名发送摇杆方向键值（参考旧 Crown DigitalStick 独立方向检测） ──
                    fun sendStickDirection(el: EditorElement, dir: String, isPressed: Boolean) {
                        val dirValue = when (dir) {
                            "up" -> el.upValue
                            "down" -> el.downValue
                            "left" -> el.leftValue
                            "right" -> el.rightValue
                            else -> ""
                        }
                        if (dirValue.isNotEmpty() && !dirValue.startsWith("a")) {
                            sendKeyboardKey(dirValue, isPressed)
                        }
                    }

                    // ── 元素触控回调（处理所有类型） ──
                    val onElementAction: (EditorElement, Boolean, Float, Float) -> Unit = remember(engine) {
                        { el, isPressed, relX, relY ->
                            val value = el.value
                            when (el.type) {
                                ElementType.DIGITAL_COMMON_BUTTON,
                                ElementType.DIGITAL_SWITCH_BUTTON -> {
                                    if (isPressed) triggerVibration()
                                    when {
                                        value == "lt" -> ltV.value = if (isPressed) 0xFF.toByte() else 0
                                        value == "rt" -> rtV.value = if (isPressed) 0xFF.toByte() else 0
                                        value.startsWith("k") -> sendKeyboardKey(value, isPressed)
                                        value.startsWith("g") -> {
                                            val flag = parseValueToFlag(value)
                                            if (flag != 0) {
                                                btnState.value = if (isPressed) btnState.value or flag
                                                    else btnState.value and flag.inv()
                                                gamepadRepeatRunnable.value?.let(repeatHandler::removeCallbacks)
                                                if (isPressed) {
                                                    val r = java.lang.Runnable { sendFullState() }
                                                    gamepadRepeatRunnable.value = r
                                                    repeatHandler.postDelayed(r, 50)
                                                    repeatHandler.postDelayed(r, 75)
                                                }
                                            }
                                        }
                                        value.startsWith("m") -> {
                                            val btnId = value.substring(1).toIntOrNull()
                                            if (btnId != null) {
                                                if (isPressed) engine.conn?.sendMouseButtonDown(btnId.toByte())
                                                else engine.conn?.sendMouseButtonUp(btnId.toByte())
                                                mouseRepeatMap.remove(btnId)?.let(repeatHandler::removeCallbacks)
                                                if (isPressed) {
                                                    val r = java.lang.Runnable {
                                                        engine.conn?.sendMouseButtonDown(btnId.toByte())
                                                    }
                                                    mouseRepeatMap[btnId] = r
                                                    repeatHandler.postDelayed(r, 50)
                                                    repeatHandler.postDelayed(r, 75)
                                                }
                                            }
                                        }
                                    }
                                    sendFullState()
                                }
                                ElementType.DIGITAL_MOVABLE_BUTTON -> {
                                    // 从 extraAttributesJson 解析 isTrackpadMode
                                    val isTrackpad = try {
                                        org.json.JSONObject(el.extraAttributesJson).optBoolean("isTrackpadMode", false)
                                    } catch (_: Exception) { false }
                                    val joyMode = el.mode == 1

                                    if (isTrackpad) {
                                        // 触控板模式：触摸移动驱动鼠标（参考旧 Crown）
                                        if (isPressed) {
                                            val prev = trackpadLastPos[el.elementId]
                                            if (prev != null) {
                                                val dx = relX - prev.first
                                                val dy = relY - prev.second
                                                val senseMul = el.sense.coerceIn(1, 500) * 0.01f
                                                engine.conn?.sendMouseMove((dx * senseMul).toInt().toShort(), (dy * senseMul).toInt().toShort())
                                            }
                                            trackpadLastPos[el.elementId] = Pair(relX, relY)
                                        } else {
                                            trackpadLastPos.remove(el.elementId)
                                        }
                                    } else if (joyMode) {
                                        // 摇杆模式（mode=1）：参照旧 Crown 合成 MotionEvent
                                        run joy@ {
                                            if (engine.surfaceView == null) return@joy
                                            val sv = engine.surfaceView!!
                                            val downTime = android.os.SystemClock.uptimeMillis()
                                            val senseMul = el.sense.coerceIn(1, 500) * 0.01f
                                            val touchX = (sv.width / 2f + relX * senseMul).toInt().coerceIn(0, sv.width)
                                            val touchY = (sv.height / 2f + relY * senseMul).toInt().coerceIn(0, sv.height)
                                            if (isPressed) {
                                                val prev = joystickFirstTouch[el.elementId]
                                                if (prev == null) {
                                                    joystickFirstTouch[el.elementId] = Pair(relX, relY)
                                                    val e = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, touchX.toFloat(), touchY.toFloat(), 0)
                                                    engine.touchHandler?.handleMotionEvent(sv, e)
                                                    e.recycle()
                                                } else {
                                                    val e = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_MOVE, touchX.toFloat(), touchY.toFloat(), 0)
                                                    engine.touchHandler?.handleMotionEvent(sv, e)
                                                    e.recycle()
                                                }
                                            } else {
                                                joystickFirstTouch.remove(el.elementId)
                                                val e = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_UP, touchX.toFloat(), touchY.toFloat(), 0)
                                                engine.touchHandler?.handleMotionEvent(sv, e)
                                                e.recycle()
                                            }
                                        }
                                    } else {
                                        // 按钮模式（mode=0）：同普通按键
                                        if (isPressed) triggerVibration()
                                        when {
                                            value == "lt" -> ltV.value = if (isPressed) 0xFF.toByte() else 0
                                            value == "rt" -> rtV.value = if (isPressed) 0xFF.toByte() else 0
                                            value.startsWith("k") -> sendKeyboardKey(value, isPressed)
                                            value.startsWith("g") -> {
                                                val flag = parseValueToFlag(value)
                                                if (flag != 0) {
                                                    btnState.value = if (isPressed) btnState.value or flag
                                                        else btnState.value and flag.inv()
                                                    gamepadRepeatRunnable.value?.let(repeatHandler::removeCallbacks)
                                                    if (isPressed) {
                                                        val r = java.lang.Runnable { sendFullState() }
                                                        gamepadRepeatRunnable.value = r
                                                        repeatHandler.postDelayed(r, 50)
                                                        repeatHandler.postDelayed(r, 75)
                                                    }
                                                }
                                            }
                                            value.startsWith("m") -> {
                                                val btnId = value.substring(1).toIntOrNull()
                                                if (btnId != null) {
                                                    if (isPressed) engine.conn?.sendMouseButtonDown(btnId.toByte())
                                                    else engine.conn?.sendMouseButtonUp(btnId.toByte())
                                                    mouseRepeatMap.remove(btnId)?.let(repeatHandler::removeCallbacks)
                                                    if (isPressed) {
                                                        val r = java.lang.Runnable {
                                                            engine.conn?.sendMouseButtonDown(btnId.toByte())
                                                        }
                                                        mouseRepeatMap[btnId] = r
                                                        repeatHandler.postDelayed(r, 50)
                                                        repeatHandler.postDelayed(r, 75)
                                                    }
                                                }
                                            }
                                        }
                                        sendFullState()
                                    }
                                }
                                ElementType.DIGITAL_COMBINE_BUTTON -> {
                                    // 组合键：同时触发全部5个方向键值（参照旧 Crown）
                                    if (isPressed) triggerVibration()
                                    val values = listOfNotNull(
                                        value.takeIf { it.isNotEmpty() },
                                        el.upValue.takeIf { it.isNotEmpty() },
                                        el.downValue.takeIf { it.isNotEmpty() },
                                        el.leftValue.takeIf { it.isNotEmpty() },
                                        el.rightValue.takeIf { it.isNotEmpty() },
                                    )
                                    for (v in values) sendKeyboardKey(v, isPressed)
                                    sendFullState()
                                }
                                ElementType.DIGITAL_PAD -> {
                                    if (isPressed) triggerVibration()
                                    // 旧 Crown 方式：位掩码检测，支持对角线（左上 = LEFT|UP）
                                    val newMask = if (isPressed) computeDpadBitmask(relX, relY, el.width, el.height) else 0
                                    val oldMask = activeDpadDirections[el.elementId] ?: 0
                                    if (newMask != oldMask) {
                                        processDpadBitmaskChange(oldMask, newMask, el)
                                        if (newMask != 0) activeDpadDirections[el.elementId] = newMask
                                        else activeDpadDirections.remove(el.elementId)
                                    }
                                }
                                ElementType.ANALOG_STICK,
                                ElementType.DIGITAL_STICK,
                                ElementType.INVISIBLE_ANALOG_STICK,
                                ElementType.INVISIBLE_DIGITAL_STICK -> {
                                    val rad = el.radius.coerceAtLeast(el.width.coerceAtMost(el.height) / 2).toFloat()
                                    val (sx, sy) = computeStickAxis(relX, relY, rad, el)
                                    // 根据 upValue/downValue/leftValue/rightValue 判断左/右摇杆
                                    val isRightStick = el.leftValue == "a2" || el.rightValue == "a2"
                                    if (isRightStick) {
                                        rsX.value = sx; rsY.value = sy
                                    } else {
                                        lsX.value = sx; lsY.value = sy
                                    }
                                    // 摇杆方向键值处理（参考旧 Crown DigitalStick）
                                    // 每个方向独立检测，支持对角线同时触发
                                    // sense 越大→阈值越低→越灵敏：sense=100→0.1, sense=1→0.5
                                    val normalizedSense = el.sense.coerceIn(1, 100) / 100f
                                    val directionThreshold = 0.1f + (1f - normalizedSense) * 0.4f
                                    val nx = relX / rad.coerceAtLeast(1f)
                                    val ny = relY / rad.coerceAtLeast(1f)
                                    val leftActive = nx < -directionThreshold
                                    val rightActive = nx > directionThreshold
                                    val upActive = ny < -directionThreshold
                                    val downActive = ny > directionThreshold
                                    // 获取当前元素各方向状态
                                    val dirState = activeStickDirections.getOrPut(el.elementId) {
                                        mutableSetOf<String>()
                                    }
                                    // 左方向
                                    if (leftActive && "left" !in dirState) {
                                        dirState.add("left"); sendStickDirection(el, "left", true)
                                    } else if (!leftActive && "left" in dirState) {
                                        dirState.remove("left"); sendStickDirection(el, "left", false)
                                    }
                                    // 右方向
                                    if (rightActive && "right" !in dirState) {
                                        dirState.add("right"); sendStickDirection(el, "right", true)
                                    } else if (!rightActive && "right" in dirState) {
                                        dirState.remove("right"); sendStickDirection(el, "right", false)
                                    }
                                    // 上方向
                                    if (upActive && "up" !in dirState) {
                                        dirState.add("up"); sendStickDirection(el, "up", true)
                                    } else if (!upActive && "up" in dirState) {
                                        dirState.remove("up"); sendStickDirection(el, "up", false)
                                    }
                                    // 下方向
                                    if (downActive && "down" !in dirState) {
                                        dirState.add("down"); sendStickDirection(el, "down", true)
                                    } else if (!downActive && "down" in dirState) {
                                        dirState.remove("down"); sendStickDirection(el, "down", false)
                                    }
                                    // 双击摇杆 = 摇杆点击 (L3/R3)，只在释放且偏移很小时触发
                                    if (!isPressed) {
                                        // 检测双击（两次按下间隔 < 300ms）
                                        val now = System.currentTimeMillis()
                                        val lastClick = stickLastClickTime[el.elementId] ?: 0L
                                        if (now - lastClick < 300 && lastClick > 0) {
                                            // 双击 → 触发 middleValue（支持 k/g/m/lt/rt 前缀，匹配旧 Crown）
                                            if (el.middleValue.isNotEmpty()) {
                                                val mv = el.middleValue
                                                when {
                                                    mv == "lt" -> { ltV.value = 0xFF.toByte(); sendFullState(); ltV.value = 0; sendFullState() }
                                                    mv == "rt" -> { rtV.value = 0xFF.toByte(); sendFullState(); rtV.value = 0; sendFullState() }
                                                    mv.startsWith("k") -> {
                                                        sendKeyboardKey(mv, true)
                                                        sendKeyboardKey(mv, false)
                                                    }
                                                    mv.startsWith("g") -> {
                                                        val flag = parseValueToFlag(mv)
                                                        if (flag != 0) {
                                                            btnState.value = btnState.value or flag
                                                            sendFullState()
                                                            btnState.value = btnState.value and flag.inv()
                                                            sendFullState()
                                                        }
                                                    }
                                                    mv.startsWith("m") -> {
                                                        val btnId = mv.substring(1).toIntOrNull()
                                                        if (btnId != null) {
                                                            engine.conn?.sendMouseButtonDown(btnId.toByte())
                                                            engine.conn?.sendMouseButtonUp(btnId.toByte())
                                                        }
                                                    }
                                                }
                                            }
                                            stickLastClickTime[el.elementId] = 0L
                                        } else {
                                            stickLastClickTime[el.elementId] = now
                                        }
                                        if (isRightStick) { rsX.value = 0; rsY.value = 0 }
                                        else { lsX.value = 0; lsY.value = 0 }
                                        // 释放所有方向
                                        for (dir in dirState.toList()) {
                                            sendStickDirection(el, dir, false)
                                        }
                                        dirState.clear()
                                    }
                                    sendFullState()
                                }
                                ElementType.GROUP_BUTTON -> {
                                    if (isPressed) {
                                        // 点击 GroupButton：切换子元素显隐
                                        val childIds = el.value
                                            .split(",")
                                            .mapNotNull { it.trim().toLongOrNull() }
                                            .filter { it != -1L }
                                            .toSet()
                                        if (childIds.isNotEmpty()) {
                                            val currentHidden = groupButtonHiddenIds.value
                                            // 如果当前任意子元素已隐藏 → 全部显示；否则全部隐藏
                                            val anyHidden = childIds.any { it in currentHidden }
                                            groupButtonHiddenIds.value = if (anyHidden) {
                                                currentHidden - childIds
                                            } else {
                                                currentHidden + childIds
                                            }
                                        }
                                    }
                                }
                                ElementType.WHEEL_PAD -> {
                                    // 直接模式：触摸环带直接选择分段（参照旧 Crown）
                                    if (isPressed) {
                                        val w = el.width
                                        val h = el.height
                                        val cx = w / 2f
                                        val cy = h / 2f
                                        val outerR = min(w, h) / 2f - el.thick
                                        val innerR = outerR * (el.sense.coerceIn(10, 90) / 100f)
                                        val dx = relX
                                        val dy = relY
                                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                        if (dist > innerR && dist < outerR) {
                                            var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90
                                            if (angle < 0) angle += 360
                                            val segCount = el.mode.coerceIn(2, 24)
                                            val sweep = 360f / segCount
                                            val idx = ((angle + sweep / 2) % 360 / sweep).toInt()
                                            val prevIdx = wheelActiveIndex[el.elementId] ?: -1
                                            if (idx != prevIdx) {
                                                // 释放旧段，按下新段
                                                val segments = el.value.split(",").filter { it.isNotBlank() }
                                                if (prevIdx in segments.indices) sendKeyboardKey(segments[prevIdx], false)
                                                if (idx in segments.indices) sendKeyboardKey(segments[idx], true)
                                                wheelActiveIndex[el.elementId] = idx
                                            }
                                        } else {
                                            val prevIdx = wheelActiveIndex.remove(el.elementId)
                                            if (prevIdx != null) {
                                                val segments = el.value.split(",").filter { it.isNotBlank() }
                                                if (prevIdx in segments.indices) sendKeyboardKey(segments[prevIdx], false)
                                            }
                                        }
                                    } else {
                                        // 释放
                                        val prevIdx = wheelActiveIndex.remove(el.elementId)
                                        if (prevIdx != null) {
                                            val segments = el.value.split(",").filter { it.isNotBlank() }
                                            if (prevIdx in segments.indices) sendKeyboardKey(segments[prevIdx], false)
                                        }
                                    }
                                }
                                else -> { /* SIMPLIFY_PERFORMANCE 无交互操作 */ }
                            }
                        }
                    }

                    // 确保按键映射元素在 UI 就绪后加载（避免初始化时序问题）
                    LaunchedEffect(Unit) {
                        if (engine.isKeyMappingFucEnabled && overlayElements.isEmpty()) {
                            engine.reloadOverlay()
                        }
                    }


                    val globalOpacity = engine.configGlobalOpacity
                    val touchEnabled = engine.configTouchEnabled
                    val touchSense = engine.configTouchSense
                    val enhancedTouch = engine.configEnhancedTouch

                    if (overlayElements.isNotEmpty() && !engine.isFullScreenPageActive) {
                        // 过滤被 GroupButton 隐藏的子元素
                        val hiddenIds = groupButtonHiddenIds.value
                        val visibleElements = if (hiddenIds.isEmpty()) overlayElements
                            else overlayElements.filter { it.elementId !in hiddenIds }

                        KeyMappingOverlay(
                            elements = visibleElements,
                            modifier = Modifier.fillMaxSize(),
                            onElementAction = onElementAction,
                            globalOpacity = globalOpacity,
                            enabled = touchEnabled,
                            touchSense = touchSense,
                            enhancedTouch = enhancedTouch,
                        )
                    }

                    // 面板 overlay（PiP 模式下隐藏所有面板）
                    if (!engine.isInPipMode) {
                        StreamOverlay(engine = engine, connectionStage = connectionStage)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasPaused) {
            wasPaused = false
            if (wasBackgrounded) {
                wasBackgrounded = false
                // 恢复流时长计时
                engine.onStartStreaming()
                // 从后台返回 → 如已断连则优雅重连
                if (!engine.connected && engine.shouldResumeSession) {
                    engine.shouldResumeSession = false
                    engine.prepareConnection()
                }
            }
            return
        }
        engine.onResume()
    }

    /** 进入 PiP 模式 */
    private fun enterPip() {
        if (suppressPipRefCount > 0 || isFinishing || isDestroyed) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (!engine.prefConfig.enablePip) return
        try {
            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(
                        engine.prefConfig.width.coerceAtLeast(1),
                        engine.prefConfig.height.coerceAtLeast(1)
                    ))
                    .build()
            } else null
            @Suppress("DEPRECATION")
            if (params != null) enterPictureInPictureMode(params)
            else enterPictureInPictureMode()
        } catch (e: Exception) {
            LimeLog.warning("StreamActivity: enterPip 失败 ${e.message}")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 仅当串流画面本身显示时进入 PiP（全屏页面如编辑器/方案选择器打开时不触发）
        if (engine.connected && engine.prefConfig.enablePip && !engine.isFullScreenPageActive) {
            enterPip()
        }
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        engine.onPiPModeChanged(isInPictureInPictureMode)

        // 跟踪 PiP 状态供跨 Activity 使用
        if (isInPictureInPictureMode) {
            // 进入 PiP：记录当前 Activity 和设备 UUID
            StreamEngine.currentPipActivity = this
            StreamEngine.currentPipUuid = intent.getStringExtra(com.limelight.Game.EXTRA_PC_UUID)
        } else {
            // 退出 PiP（用户点击 PiP 窗口恢复或关闭 PiP）
            // 如果 Activity 未被 finish（即用户点击 PiP 窗口恢复），不清除引用
            // 如果 Activity 正在 finish（用户点击 X 关闭 PiP），在 onDestroy 中清理
            if (isFinishing || isDestroyed) {
                clearPipReference()
            }
        }
    }

    private fun clearPipReference() {
        if (StreamEngine.currentPipActivity === this) {
            StreamEngine.currentPipActivity = null
            StreamEngine.currentPipUuid = null
        }
    }

    override fun onDestroy() {
        clearPipReference()
        super.onDestroy()
        engine.release()
    }

    override fun onPause() {
        super.onPause()
        wasPaused = true
        wasBackgrounded = !isFinishing
        if (!isFinishing) {
            engine.shouldResumeSession = true
        }
        engine.onPause()
    }

    override fun onStop() {
        super.onStop()

        // 极速恢复：不 finish 时保活
        if ((engine.isExtremeResumeEnabled || engine.isChangingResolution) && !isFinishing) {
            LimeLog.info("StreamActivity: onStop 极速恢复拦截")
            if (!engine.isChangingResolution) {
                showKeepAliveNotification()
            }
            return
        }

        // 流时长统计暂停
        engine.onStopStreaming()

        // 标记为"应恢复会话"
        if (!engine.shouldResumeSession && !isFinishing) {
            engine.shouldResumeSession = true
        }
    }

    private fun showKeepAliveNotification() {
        val pcName = intent.getStringExtra(com.limelight.Game.EXTRA_PC_NAME) ?: return
        val appName = intent.getStringExtra(com.limelight.Game.EXTRA_APP_NAME) ?: return
        StreamNotificationService.start(this, pcName, appName)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        @Suppress("DEPRECATION")
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        engine.onWindowFocusChanged(hasFocus)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 防抖
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressTime < backPressDebounceMs) return
        lastBackPressTime = now
        // 如果 PiP 已开启且串流已连接 → 进入画中画而非断开串流
        if (engine.connected && engine.prefConfig.enablePip) {
            enterPip()
        } else {
            engine.disconnect()
        }
    }
}
