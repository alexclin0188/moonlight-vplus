package com.alexclin.moonlink.android.stream

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import com.limelight.ui.StreamView
import android.view.WindowManager
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
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

        // 清除上一次串流遗留的保活通知
        cancelKeepAliveNotification()

        // 全屏（隐藏状态栏 + 导航栏）
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                @Suppress("DEPRECATION")
                window.setDecorFitsSystemWindows(false)
            } catch (_: NoSuchMethodError) {
                // 部分定制 ROM（如华为/小米早期版本）声称 >= R 但实际无此方法
            }
        }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // 设置窗口背景为黑色，非视频区域（letterbox/pillarbox）显示纯黑
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        @Suppress("DEPRECATION")
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

        // 默认横屏，允许在 landscape / reverse landscape 之间跟随手机反转
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // 初始化串流引擎
        engine = StreamEngine(this)
        if (!engine.initialize(intent)) {
            finish()
            return
        }

        // 提示用户开启通知权限以保证后台保活
        if (engine.isResumeStreamEnabled()) {
            checkNotificationPermission()
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
                            val frameLayout = android.widget.FrameLayout(ctx).also { fl ->
                                fl.layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            }
                            StreamView(ctx).also { sv ->
                                sv.id = R.id.surfaceView
                                // FrameLayout 内居中：当 StreamView 按宽高比约束后小于父容器时，画面居中展示
                                sv.layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER
                                )
                                engine.surfaceView = sv
                                engine.attachSurfaceView(sv)
                                sv.holder.addCallback(engine.surfaceCallback)
                                sv.setOnTouchListener(touchListener)
                                sv.setOnGenericMotionListener(genericMotionListener)
                                frameLayout.addView(sv)
                            }
                            frameLayout
                        },
                        update = { fl ->
                            val sv = fl.getChildAt(0) as? StreamView ?: return@AndroidView
                            // 使用实际串流分辨率（VDD 模式下可能 ≠ prefConfig）
                            val displayW = engine.actualStreamWidth.coerceAtLeast(1).let {
                                if (it == 0) engine.prefConfig.width.coerceAtLeast(1) else it
                            }
                            val displayH = engine.actualStreamHeight.coerceAtLeast(1).let {
                                if (it == 0) engine.prefConfig.height.coerceAtLeast(1) else it
                            }
                            // 根据 stretchVideo 即时更新画面比例（纯客户端，无需重启串流）
                            if (engine.stretchVideo) {
                                sv.setDesiredAspectRatio(0.0)
                                sv.holder.setFixedSize(displayW, displayH)
                            } else {
                                val aspect = displayW.toFloat() / displayH.toFloat()
                                sv.setDesiredAspectRatio(aspect.toDouble())
                                sv.holder.setSizeFromLayout()
                            }
                            sv.requestLayout()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 仅控制模式提示覆盖层（连接完成后显示，位于本地光标之下，保证光标绘制在上层）
                    if (connectionStage == null && engine.prefConfig.controlOnly) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xCC000000.toInt()),
                                modifier = Modifier.padding(horizontal = 32.dp),
                            ) {
                                Text(
                                    text = "当前为仅控制模式\n如需退出，请在主机串流设置的画面开关中关闭",
                                    color = Color(0xFFCCCCCC.toInt()),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                )
                            }
                        }
                    }

                    // 本地光标覆盖层（触控板模式下绘制）
                    if (engine.showLocalCursor) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                        ) {
                            val cx = size.width * engine.localCursorAbsX
                            val cy = size.height * engine.localCursorAbsY
                            // 绘制一个简单的箭头光标
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(20f, 20f)
                                lineTo(12f, 22f)
                                lineTo(0f, 10f)
                                close()
                            }
                            drawPath(path, Color.White, style = Fill)
                            // 光标的暗色描边增强可视性
                            drawPath(path, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                        }
                    }

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
                    // 死区由 el.sense（与旧 Crown deadZoneRadius 同列，0-100）决定：直接用百分比
                    // 旧 Crown 使用径向距离（圆形死区），不是分轴检测
                    // sense=100→0%(无死区), sense=30→30%, sense=1→1%
                    // isActive: 上次是否已激活越过死区（用于滞后保持），退回死区时不归零
                    fun computeStickAxis(relX: Float, relY: Float, radius: Float, element: EditorElement, isActive: Boolean): Pair<Short, Short> {
                        val deadZoneRatio = element.sense.coerceIn(1, 100) / 100f
                        val maxRadius = radius.coerceAtLeast(1f)
                        val nx = (relX / maxRadius).coerceIn(-1f, 1f)
                        val ny = (relY / maxRadius).coerceIn(-1f, 1f)
                        // 旧 Crown 使用圆形死区（径向距离），非分轴
                        val dist = kotlin.math.sqrt(nx * nx + ny * ny)
                        val inDeadZone = dist < deadZoneRatio
                        // 死区滞后保持：已激活状态下退回死区不归零（参考旧 Crown STICK_STATE.MOVED_ACTIVE）
                        val finalNx = if (inDeadZone && !isActive) 0f else nx
                        val finalNy = if (inDeadZone && !isActive) 0f else ny
                        return Pair((finalNx * 32766).toInt().toShort(), (finalNy * 32766).toInt().toShort())
                    }

                    // ── D-Pad 方向位掩码（与旧 Crown DigitalPad 一致） ──
                    val DPAD_LEFT = 1
                    val DPAD_UP = 2
                    val DPAD_RIGHT = 4
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

                    // 十字方向键当前激活的方向（elementId → 方向），用于 MOVE 时的方向变更检测
                    val activeDpadDirections = remember { mutableStateMapOf<Long, Int>() }
                    // 摇杆当前激活的方向集合（elementId → 方向集合），支持对角线
                    val activeStickDirections = remember { HashMap<Long, MutableSet<String>>() }
                    // 摇杆上次点击时间（元素ID → 时间戳），用于双击 middleValue 检测
                    val stickLastClickTime = remember { HashMap<Long, Long>() }
                    // MovableButton 触控板模式：追踪上次触摸位置（元素ID → Pair(relX, relY)）
                    val trackpadLastPos = remember { HashMap<Long, Pair<Float, Float>>() }
                    // 触控板模式：首次触摸位置（用于判定是否移动超过阈值）
                    val trackpadFirstTouch = remember { HashMap<Long, Pair<Float, Float>>() }
                    // 触控板模式：是否已确认为拖动（长按300ms触发）
                    val trackpadDragConfirmed = remember { HashMap<Long, Boolean>() }
                    // 触控板模式：是否已确认为滑动（移动超过20px阈值）
                    val trackpadMoveConfirmed = remember { HashMap<Long, Boolean>() }
                    // 触控板模式：长按计时器（元素ID → Runnable），用于取消
                    val trackpadTimers = remember { HashMap<Long, java.lang.Runnable>() }
                    // MovableButton 摇杆模式（mode=1）：记录首次触摸偏移（元素ID → Pair(relX, relY)）
                    val joystickFirstTouch = remember { HashMap<Long, Pair<Float, Float>>() }
                    // 震动防重复：记录已触发震动的元素（元素ID → true），只在首次按下时震动，抬起清除
                    val elementVibrationFired = remember { HashMap<Long, Boolean>() }
                    // 摇杆死区滞后保持状态（elementId → 是否已激活越过死区），用于退回死区不归零
                    val stickActiveStates = remember { HashMap<Long, Boolean>() }
                    // 隐藏摇杆动态圆心（elementId → 触摸圆心在元素本地坐标中的偏移，用于 ACTION_DOWN 重定位）
                    val invisibleStickCenters = remember { HashMap<Long, Pair<Float, Float>>() }
                    // 隐藏摇杆按下时间戳（elementId → 按下时刻 ms），用于 timeoutDeadzone=150ms（旧 Crown）
                    val stickDownTime = remember { HashMap<Long, Long>() }
                    // 双击 middleValue 保持状态（elementId → true=双击已激活 middleValue 按下，UP 时释放）
                    val stickClickStates = remember { HashMap<Long, Boolean>() }

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
                        if (!engine.configButtonVibrator) return
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
                        // 调试日志：记录键值命令 ↓按下 / ↑释放
                        LimeLog.info("KEY: ${value}${if (isPressed) "↓" else "↑"}")
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
                            // 数值越小滚动越快：configWheelSpeed 1→最快, 119→最慢
                            scrollRepeatRunnable.value?.let(repeatHandler::removeCallbacks)
                            scrollRepeatRunnable.value = null
                            if (isPressed) {
                                val upAmount = ((120 - engine.configWheelSpeed) / 24 + 1).coerceIn(1, 5).toByte()
                                engine.mouseVScroll(upAmount)
                                val r = object : java.lang.Runnable {
                                    override fun run() {
                                        engine.mouseVScroll(upAmount)
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
                                val downAmount = (-((120 - engine.configWheelSpeed) / 24 + 1).coerceIn(1, 5)).toByte()
                                engine.mouseVScroll(downAmount)
                                val r = object : java.lang.Runnable {
                                    override fun run() {
                                        engine.mouseVScroll(downAmount)
                                        repeatHandler.postDelayed(this, 100)
                                    }
                                }
                                scrollRepeatRunnable.value = r
                                repeatHandler.postDelayed(r, 150)
                            }
                        } else {
                            // 特殊功能键（非键盘/鼠标/手柄值的兜底处理）
                            if (!isPressed) return@sendKeyboardKey
                            when {
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
                                    if (isPressed) {
                                        if (elementVibrationFired.put(el.elementId, true) == null) {
                                            triggerVibration()
                                        }
                                    } else {
                                        elementVibrationFired.remove(el.elementId)
                                    }
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
                                        // 触控板模式：点击/滑动/长按拖拽（参考旧 Crown handleTrackpadTouchEvent）
                                        if (isPressed) {
                                            val prev = trackpadLastPos[el.elementId]
                                            if (prev == null) {
                                                // ═══ ACTION_DOWN：首次按下 ═══
                                                trackpadFirstTouch[el.elementId] = Pair(relX, relY)
                                                trackpadDragConfirmed[el.elementId] = false
                                                trackpadMoveConfirmed[el.elementId] = false
                                                // 启动 300ms 长按检测计时器（触发拖拽模式）
                                                val dragRunnable = java.lang.Runnable {
                                                    val eId = el.elementId
                                                    if (trackpadLastPos.containsKey(eId) &&
                                                        trackpadDragConfirmed[eId] != true &&
                                                        trackpadMoveConfirmed[eId] != true) {
                                                        // 长按触发 → 进入拖拽模式：发送鼠标左键按下
                                                        trackpadDragConfirmed[eId] = true
                                                        triggerVibration()
                                                        engine.conn?.sendMouseButtonDown(1.toByte())
                                                    }
                                                }
                                                trackpadTimers[el.elementId] = dragRunnable
                                                repeatHandler.postDelayed(dragRunnable, 300)
                                            } else {
                                                // ═══ ACTION_MOVE：持续触摸移动 ═══
                                                val firstTouch = trackpadFirstTouch[el.elementId] ?: Pair(relX, relY)
                                                val dxTotal = relX - firstTouch.first
                                                val dyTotal = relY - firstTouch.second
                                                val moveDist = kotlin.math.sqrt(dxTotal * dxTotal + dyTotal * dyTotal)
                                                val isDrag = trackpadDragConfirmed[el.elementId] == true
                                                val isMove = trackpadMoveConfirmed[el.elementId] == true
                                                if (!isDrag && !isMove) {
                                                    // 尚未确认任何手势 — 检查是否超过移动阈值
                                                    if (moveDist > 20f /* TAP_MOVEMENT_THRESHOLD */) {
                                                        trackpadMoveConfirmed[el.elementId] = true
                                                        // 超过阈值则取消长按计时器（不是长按）
                                                        trackpadTimers.remove(el.elementId)?.let(repeatHandler::removeCallbacks)
                                                    }
                                                }
                                                if (isDrag || trackpadMoveConfirmed[el.elementId] == true) {
                                                    // 拖动或滑动中 → 驱动鼠标指针
                                                    val dx = relX - prev.first
                                                    val dy = relY - prev.second
                                                    val senseMul = el.sense.coerceIn(1, 500) * 0.01f
                                                    engine.conn?.sendMouseMove((dx * senseMul).toInt().toShort(), (dy * senseMul).toInt().toShort())
                                                }
                                            }
                                            trackpadLastPos[el.elementId] = Pair(relX, relY)
                                        } else {
                                            // ═══ ACTION_UP / CANCEL：手指抬起 ═══
                                            val isDrag = trackpadDragConfirmed[el.elementId] == true
                                            val isMove = trackpadMoveConfirmed[el.elementId] == true
                                            // 取消所有计时器
                                            trackpadTimers.remove(el.elementId)?.let(repeatHandler::removeCallbacks)
                                            if (isDrag) {
                                                // 拖拽结束 → 发送鼠标左键释放
                                                engine.conn?.sendMouseButtonUp(1.toByte())
                                            } else if (!isMove) {
                                                // 点击（未移动、未拖拽）→ 震动 + 发送鼠标左键单击
                                                triggerVibration()
                                                engine.conn?.sendMouseButtonDown(1.toByte())
                                                repeatHandler.postDelayed({
                                                    engine.conn?.sendMouseButtonUp(1.toByte())
                                                }, 50)
                                            }
                                            // 清理所有追踪状态
                                            trackpadLastPos.remove(el.elementId)
                                            trackpadFirstTouch.remove(el.elementId)
                                            trackpadDragConfirmed.remove(el.elementId)
                                            trackpadMoveConfirmed.remove(el.elementId)
                                        }
                                    } else if (joyMode) {
                                        // 摇杆模式（mode=1）：先发键值 + 再合成 MotionEvent（旧 Crown 行为）
                                        if (isPressed) {
                                            if (elementVibrationFired.put(el.elementId, true) == null) {
                                                triggerVibration()
                                            }
                                        } else {
                                            elementVibrationFired.remove(el.elementId)
                                        }
                                        when {
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
                                        // ── 合成 MotionEvent ──
                                        // 使用增量计算：MOVE 时基于首次触摸点的偏移量，而非绝对 relX
                                        // 这样手指在元素区域内滑动时鼠标位置平滑累加而非跳变
                                        run joy@ {
                                            if (engine.surfaceView == null) return@joy
                                            val sv = engine.surfaceView!!
                                            val downTime = android.os.SystemClock.uptimeMillis()
                                            val senseMul = el.sense.coerceIn(1, 500) * 0.01f
                                            // 首次触摸点（相对元素中心）
                                            val firstTouch = joystickFirstTouch[el.elementId]
                                            if (isPressed) {
                                                if (firstTouch == null) {
                                                    // ═══ ACTION_DOWN：记录首次触摸点，发送 DOWN 事件 ═══
                                                    joystickFirstTouch[el.elementId] = Pair(relX, relY)
                                                    val touchX = (sv.width / 2f).toInt().coerceIn(0, sv.width)
                                                    val touchY = (sv.height / 2f).toInt().coerceIn(0, sv.height)
                                                    val e = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, touchX.toFloat(), touchY.toFloat(), 0)
                                                    engine.touchHandler?.handleMotionEvent(sv, e)
                                                    e.recycle()
                                                } else {
                                                    // ═══ ACTION_MOVE：基于首次触摸点的增量 ═══
                                                    val deltaX = (relX - firstTouch.first) * senseMul
                                                    val deltaY = (relY - firstTouch.second) * senseMul
                                                    val touchX = (sv.width / 2f + deltaX).toInt().coerceIn(0, sv.width)
                                                    val touchY = (sv.height / 2f + deltaY).toInt().coerceIn(0, sv.height)
                                                    val e = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_MOVE, touchX.toFloat(), touchY.toFloat(), 0)
                                                    engine.touchHandler?.handleMotionEvent(sv, e)
                                                    e.recycle()
                                                }
                                            } else {
                                                // ═══ ACTION_UP：发送 UP 事件，清理状态 ═══
                                                joystickFirstTouch.remove(el.elementId)
                                                val e = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_UP, sv.width / 2f, sv.height / 2f, 0)
                                                engine.touchHandler?.handleMotionEvent(sv, e)
                                                e.recycle()
                                            }
                                        }
                                    } else {
                                        // 按钮模式（mode=0）：同普通按键 + 滑动时发送鼠标移动（旧 Crown 行为）
                                        if (isPressed) {
                                            if (elementVibrationFired.put(el.elementId, true) == null) {
                                                triggerVibration()
                                            }
                                            // 手指滑动 → 发送鼠标移动（参考旧 Crown handleButtonTouchEvent MOVE）
                                            val prev = trackpadLastPos[el.elementId]
                                            if (prev != null) {
                                                val dx = relX - prev.first
                                                val dy = relY - prev.second
                                                val senseMul = el.sense.coerceIn(1, 500) * 0.01f
                                                engine.conn?.sendMouseMove((dx * senseMul).toInt().toShort(), (dy * senseMul).toInt().toShort())
                                            }
                                            trackpadLastPos[el.elementId] = Pair(relX, relY)
                                        } else {
                                            elementVibrationFired.remove(el.elementId)
                                            trackpadLastPos.remove(el.elementId)
                                        }
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
                                    if (isPressed) {
                                        if (elementVibrationFired.put(el.elementId, true) == null) {
                                            triggerVibration()
                                        }
                                    } else {
                                        elementVibrationFired.remove(el.elementId)
                                    }
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
                                    if (isPressed) {
                                        if (elementVibrationFired.put(el.elementId, true) == null) {
                                            triggerVibration()
                                        }
                                    } else {
                                        elementVibrationFired.remove(el.elementId)
                                    }
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
                                    // ── 振动反馈：首次按下时触发 ──
                                    if (isPressed) {
                                        if (elementVibrationFired.put(el.elementId, true) == null) {
                                            triggerVibration()
                                        }
                                    } else {
                                        elementVibrationFired.remove(el.elementId)
                                    }

                                    // ── 双击检测（旧 Crown：第2次 DOWN 时触发 middleValue，保持直到 UP） ──
                                    if (isPressed) {
                                        val now = android.os.SystemClock.uptimeMillis()
                                        val lastClick = stickLastClickTime[el.elementId] ?: 0L
                                        if (lastClick > 0 && now - lastClick < 350) {
                                            // 双击 → 按下 middleValue，保持直到 UP（旧 Crown notifyOnDoubleClick + notifyOnRevoke）
                                            stickClickStates[el.elementId] = true
                                            if (el.middleValue.isNotEmpty()) {
                                                val mv = el.middleValue
                                                when {
                                                    mv == "lt" -> { ltV.value = 0xFF.toByte(); sendFullState() }
                                                    mv == "rt" -> { rtV.value = 0xFF.toByte(); sendFullState() }
                                                    mv.startsWith("k") -> sendKeyboardKey(mv, true)
                                                    mv.startsWith("g") -> {
                                                        val flag = parseValueToFlag(mv)
                                                        if (flag != 0) {
                                                            btnState.value = btnState.value or flag
                                                            sendFullState()
                                                        }
                                                    }
                                                    mv.startsWith("m") -> {
                                                        val btnId = mv.substring(1).toIntOrNull()
                                                        if (btnId != null) engine.conn?.sendMouseButtonDown(btnId.toByte())
                                                    }
                                                }
                                            }
                                            stickLastClickTime[el.elementId] = 0L
                                        } else {
                                            stickLastClickTime[el.elementId] = now
                                        }
                                    }

                                    val rad = el.radius.coerceAtLeast(el.width.coerceAtMost(el.height) / 2).toFloat()
                                    val isInvisible = el.type == ElementType.INVISIBLE_ANALOG_STICK ||
                                        el.type == ElementType.INVISIBLE_DIGITAL_STICK
                                    // AnalogStick moveMode=1 也表示相对移动模式，首次触摸点为动态圆心（旧 Crown 行为）
                                    val useDynamicCenter = isInvisible ||
                                        (el.type == ElementType.ANALOG_STICK && el.mode == 1)

                                    // ── 动态圆心：每次 ACTION_DOWN 时以手指按下位置为圆心 ──
                                    val effRelX: Float
                                    val effRelY: Float
                                    if (useDynamicCenter) {
                                        if (isPressed) {
                                            val center = invisibleStickCenters[el.elementId]
                                            if (center == null) {
                                                // 首次按下 → 记录触摸点为新圆心
                                                invisibleStickCenters[el.elementId] = Pair(relX, relY)
                                                effRelX = 0f
                                                effRelY = 0f
                                            } else {
                                                // 后续 MOVE → 相对于记录圆心计算偏移
                                                effRelX = relX - center.first
                                                effRelY = relY - center.second
                                            }
                                        } else {
                                            // 抬起 → 清除圆心
                                            invisibleStickCenters.remove(el.elementId)
                                            effRelX = relX
                                            effRelY = relY
                                        }
                                    } else {
                                        effRelX = relX
                                        effRelY = relY
                                    }

                                    // ── 隐藏摇杆 timeoutDeadzone 150ms 延迟解除（旧 Crown） ──
                                    // 使用 uptimeMillis 对齐 MotionEvent eventTime（单调时钟，不受系统时间跳变影响）
                                    val deadzoneTimeoutPassed: Boolean
                                    if (isInvisible && isPressed) {
                                        val downMs = stickDownTime.getOrPut(el.elementId) { android.os.SystemClock.uptimeMillis() }
                                        deadzoneTimeoutPassed = android.os.SystemClock.uptimeMillis() - downMs > 150L
                                    } else {
                                        if (!isPressed) stickDownTime.remove(el.elementId)
                                        deadzoneTimeoutPassed = false
                                    }

                                    // ── 死区滞后保持计算轴值 ──
                                    val wasActive = stickActiveStates[el.elementId] ?: false
                                    val (sx, sy) = computeStickAxis(effRelX, effRelY, rad, el, wasActive || deadzoneTimeoutPassed)
                                    val nowActive = if (isPressed) {
                                        // 滞后保持：一旦激活越过死区就保持激活直到抬起
                                        // 隐藏摇杆额外支持 150ms 延迟（旧 Crown timeoutDeadzone）
                                        wasActive || (sx != 0.toShort() || sy != 0.toShort()) || deadzoneTimeoutPassed
                                    } else {
                                        false
                                    }
                                    if (isPressed) {
                                        stickActiveStates[el.elementId] = nowActive
                                    } else {
                                        stickActiveStates.remove(el.elementId)
                                    }

                                    // ── 仅模拟摇杆类型发送手柄摇杆轴值（旧 Crown DigitalStick 不发送轴值） ──
                                    val isAnalogType = el.type == ElementType.ANALOG_STICK || el.type == ElementType.INVISIBLE_ANALOG_STICK
                                    val isRightStick = el.leftValue == "a2" || el.rightValue == "a2"
                                    if (isAnalogType) {
                                        if (isRightStick) {
                                            rsX.value = sx; rsY.value = sy
                                        } else {
                                            lsX.value = sx; lsY.value = sy
                                        }
                                    }
                                    // 摇杆方向键值处理（参考旧 Crown DigitalStick 独立方向检测）
                                    // 每个方向独立检测，支持对角线同时触发
                                    // 旧 Crown 方向阈值：deadZoneRadius * 0.01（sense 字段即 deadZoneRadius）
                                    val directionThreshold = el.sense.coerceIn(1, 100) / 100f
                                    // 方向检测统一使用动态圆心调整后的坐标（旧 Crown 隐藏摇杆使用动态圆心）
                                    val dirNx = effRelX / rad.coerceAtLeast(1f)
                                    val dirNy = effRelY / rad.coerceAtLeast(1f)
                                    val leftActive = dirNx < -directionThreshold
                                    val rightActive = dirNx > directionThreshold
                                    val upActive = dirNy < -directionThreshold
                                    val downActive = dirNy > directionThreshold
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
                                    // ── 释放处理：双击保持的 middleValue + 清零轴值 + 释放方向 ──
                                    if (!isPressed) {
                                        // 如果双击 middleValue 正在保持，释放之（旧 Crown notifyOnRevoke）
                                        val doubleHeld = stickClickStates.remove(el.elementId) ?: false
                                        if (doubleHeld && el.middleValue.isNotEmpty()) {
                                            val mv = el.middleValue
                                            when {
                                                mv == "lt" -> { ltV.value = 0; sendFullState() }
                                                mv == "rt" -> { rtV.value = 0; sendFullState() }
                                                mv.startsWith("k") -> sendKeyboardKey(mv, false)
                                                mv.startsWith("g") -> {
                                                    val flag = parseValueToFlag(mv)
                                                    if (flag != 0) {
                                                        btnState.value = btnState.value and flag.inv()
                                                        sendFullState()
                                                    }
                                                }
                                                mv.startsWith("m") -> {
                                                    val btnId = mv.substring(1).toIntOrNull()
                                                    if (btnId != null) engine.conn?.sendMouseButtonUp(btnId.toByte())
                                                }
                                            }
                                        }
                                        // 仅模拟摇杆类型清零轴值（数字摇杆不发送轴值）
                                        if (isAnalogType) {
                                            if (isRightStick) { rsX.value = 0; rsY.value = 0 }
                                            else { lsX.value = 0; lsY.value = 0 }
                                        }
                                        // 释放所有方向
                                        for (dir in dirState.toList()) {
                                            sendStickDirection(el, dir, false)
                                        }
                                        dirState.clear()
                                    }
                                    sendFullState()
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

                    // 仅控制模式提示覆盖层（连接完成后显示，位于按键映射层和面板层之下，保证触控事件穿透）
                    if (connectionStage == null && engine.prefConfig.controlOnly) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xCC000000.toInt()),
                                modifier = Modifier.padding(horizontal = 32.dp),
                            ) {
                                Text(
                                    text = "当前为仅控制模式\n如需退出，请在主机串流设置的画面开关中关闭",
                                    color = Color(0xFFCCCCCC.toInt()),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                )
                            }
                        }
                    }

                    if (overlayElements.isNotEmpty() && !engine.isFullScreenPageActive) {
                        KeyMappingOverlay(
                            elements = overlayElements,
                            modifier = Modifier.fillMaxSize(),
                            onElementAction = onElementAction,
                            globalOpacity = globalOpacity,
                            enabled = touchEnabled,
                            touchSense = touchSense,
                            enhancedTouch = enhancedTouch,
                            activeDpadDirections = activeDpadDirections,
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

    /**
     * 处理点击通知栏通知 / 其他 singleTask 唤起场景。
     * 不调用 setIntent()，保留 onCreate 时的原始 intent（含 EXTRA_PC_NAME 等关键参数）。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LimeLog.info("StreamActivity: onNewIntent 收到唤起")
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 用户在 landscape / reverse landscape 之间 180° 反转手机时，
        // Activity 不会重建（Manifest 已声明 configChanges），
        // Compose UI 自动适配 LocalConfiguration.current 的更新。
        // 仅需通知 engine 层面刷新显示参数（无实质操作，留作扩展）。
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
        cancelKeepAliveNotification()
        super.onDestroy()
        engine.release()
    }

    override fun onPause() {
        super.onPause()
        wasPaused = true
        wasBackgrounded = !isFinishing
        if (!isFinishing) {
            if (engine.isResumeStreamEnabled()) {
                engine.shouldResumeSession = true
            }
            // 切到后台时退出全屏编辑页面（按键编辑器/方案选择器）
            if (engine.isFullScreenPageActive) {
                engine.forceExitFullScreenPage++
            }
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
        if (!engine.shouldResumeSession && !isFinishing && engine.isResumeStreamEnabled()) {
            engine.shouldResumeSession = true
        }

        // 自动恢复串流未开启 → 直接终止并退出串流界面
        if (!engine.isResumeStreamEnabled() && !isFinishing) {
            finish()
        }
    }

    @SuppressLint("BatteryLife")
    private fun showKeepAliveNotification() {
        // Android 13+：必须先获得通知权限才能显示前台服务通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    KEEP_ALIVE_NOTIFICATION_ID
                )
                return
            }
        }

        // Android 12+：引导用户关闭电池优化，防止后台被系统杀死
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val isResumeEnabled = prefs.getBoolean("checkbox_resume_stream", false)
                val hasRequestedOptimization = prefs.getBoolean("pref_battery_optimization_requested", false)

                if (isResumeEnabled && !hasRequestedOptimization) {
                    if (ContextCompat.checkSelfPermission(this, "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val pm = getSystemService(POWER_SERVICE) as PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = "package:$packageName".toUri()
                            try {
                                startActivity(intent)
                                prefs.edit {
                                    putBoolean("pref_battery_optimization_requested", true)
                                }
                            } catch (e: Exception) {
                                LimeLog.warning("StreamActivity: 无法打开电池优化设置 ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val pcName = intent.getStringExtra(com.limelight.Game.EXTRA_PC_NAME) ?: return
        val appName = intent.getStringExtra(com.limelight.Game.EXTRA_APP_NAME) ?: return
        StreamNotificationService.start(this, pcName, appName)
    }

    private fun cancelKeepAliveNotification() {
        StreamNotificationService.stop(this)
    }

    /** 提示用户开启通知权限以确保后台保活生效 */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(this, getString(R.string.toast_enable_notification_for_bg), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == KEEP_ALIVE_NOTIFICATION_ID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，重新尝试启动保活通知
                val pcName = intent.getStringExtra(com.limelight.Game.EXTRA_PC_NAME) ?: return
                val appName = intent.getStringExtra(com.limelight.Game.EXTRA_APP_NAME) ?: return
                StreamNotificationService.start(this, pcName, appName)
            } else {
                Toast.makeText(this, getString(R.string.toast_no_notification_permission), Toast.LENGTH_LONG).show()
            }
        }
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
            engine.disconnectAndQuit()
        }
    }

    companion object {
        /** 保活通知权限请求码 */
        private const val KEEP_ALIVE_NOTIFICATION_ID = 1001
    }
}
