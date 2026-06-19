package com.alexclin.moonlink.stream

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.limelight.R
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.StreamOverlay
import com.alexclin.moonlink.stream.ui.overlay.KeyMappingOverlay
import com.alexclin.moonlink.stream.ui.editor.EditorElement
import com.alexclin.moonlink.stream.ui.editor.ElementType
import com.alexclin.moonlink.theme.MoonLinkTheme
import com.limelight.LimeLog
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
                Box(modifier = Modifier.fillMaxSize()) {
                    // 串流画面 SurfaceView
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).also { sv ->
                                sv.id = R.id.surfaceView
                                engine.surfaceView = sv
                                engine.attachSurfaceView(sv)
                                sv.holder.addCallback(engine.surfaceCallback)
                                // 触控事件监听
                                sv.setOnTouchListener { view, event ->
                                    engine.touchHandler?.handleMotionEvent(view, event) ?: false
                                }
                                // 鼠标/触控笔事件监听
                                sv.setOnGenericMotionListener { view, event ->
                                    engine.touchHandler?.handleMotionEvent(view, event) ?: false
                                }
                            }
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

                    // D-Pad 单独追踪（每个方向独立）
                    val dpadUp = remember { mutableStateOf(false) }
                    val dpadDown = remember { mutableStateOf(false) }
                    val dpadLeft = remember { mutableStateOf(false) }
                    val dpadRight = remember { mutableStateOf(false) }

                    fun sendFullState() {
                        var flags = btnState.value
                        if (dpadUp.value) flags = flags or com.limelight.nvstream.input.ControllerPacket.UP_FLAG
                        if (dpadDown.value) flags = flags or com.limelight.nvstream.input.ControllerPacket.DOWN_FLAG
                        if (dpadLeft.value) flags = flags or com.limelight.nvstream.input.ControllerPacket.LEFT_FLAG
                        if (dpadRight.value) flags = flags or com.limelight.nvstream.input.ControllerPacket.RIGHT_FLAG
                        engine.controllerHandler?.reportOscState(
                            flags, lsX.value, lsY.value, rsX.value, rsY.value, ltV.value, rtV.value
                        )
                    }

                    // ── 解析元素 value 为控制器标志位 ──
                    fun parseValueToFlag(value: String): Int {
                        return when {
                            value.startsWith("k0x") -> value.substring(3).toIntOrNull(16) ?: 0
                            value.startsWith("g") -> value.substring(1).toIntOrNull(16) ?: 0
                            else -> 0
                        }
                    }

                    // ── 摇杆辅助：根据 relX/relY 和元素半径计算轴值 ──
                    fun computeStickAxis(relX: Float, relY: Float, radius: Float): Pair<Short, Short> {
                        val deadZone = 0.2f
                        val maxRadius = radius.coerceAtLeast(1f)
                        var nx = (relX / maxRadius).coerceIn(-1f, 1f)
                        var ny = (relY / maxRadius).coerceIn(-1f, 1f)
                        if (kotlin.math.abs(nx) < deadZone) nx = 0f
                        if (kotlin.math.abs(ny) < deadZone) ny = 0f
                        return Pair((nx * 32767).toInt().toShort(), (ny * 32767).toInt().toShort())
                    }

                    // ── D-Pad 方向检测 ──
                    fun computeDpadDirection(relX: Float, relY: Float, w: Int, h: Int): Int {
                        val hw = w / 2f
                        val hh = h / 2f
                        // D-Pad 分为 4 个方向区域（对角线分割）
                        return when {
                            relX < -hw * 0.33f && kotlin.math.abs(relY) < hh * 0.5f -> 1  // left
                            relX > hw * 0.33f && kotlin.math.abs(relY) < hh * 0.5f -> 2  // right
                            relY < -hh * 0.33f && kotlin.math.abs(relX) < hw * 0.5f -> 3  // up
                            relY > hh * 0.33f && kotlin.math.abs(relX) < hw * 0.5f -> 4  // down
                            // 角落区域：按角度判断
                            else -> {
                                val angle = kotlin.math.atan2(relY.toDouble(), relX.toDouble())
                                when {
                                    angle < -Math.PI * 0.75 || angle > Math.PI * 0.75 -> 1 // left
                                    angle < -Math.PI * 0.25 -> 3 // up
                                    angle > Math.PI * 0.25 -> 4 // down
                                    else -> 2 // right
                                }
                            }
                        }
                    }

                    // ── 元素触控回调（处理所有类型） ──
                    val onElementAction: (EditorElement, Boolean, Float, Float) -> Unit = remember(engine) {
                        { el, isPressed, relX, relY ->
                            val value = el.value
                            when (el.type) {
                                ElementType.DIGITAL_COMMON_BUTTON,
                                ElementType.DIGITAL_SWITCH_BUTTON,
                                ElementType.DIGITAL_MOVABLE_BUTTON,
                                ElementType.DIGITAL_COMBINE_BUTTON -> {
                                    when {
                                        value == "lt" -> ltV.value = if (isPressed) 0xFF.toByte() else 0
                                        value == "rt" -> rtV.value = if (isPressed) 0xFF.toByte() else 0
                                        value.startsWith("k0x") || value.startsWith("g") -> {
                                            val flag = parseValueToFlag(value)
                                            if (flag != 0) {
                                                btnState.value = if (isPressed) btnState.value or flag
                                                    else btnState.value and flag.inv()
                                            }
                                        }
                                    }
                                    sendFullState()
                                }
                                ElementType.DIGITAL_PAD -> {
                                    if (isPressed) {
                                        val dir = computeDpadDirection(relX, relY, el.width, el.height)
                                        dpadUp.value = dir == 3
                                        dpadDown.value = dir == 4
                                        dpadLeft.value = dir == 1
                                        dpadRight.value = dir == 2
                                    } else {
                                        dpadUp.value = false
                                        dpadDown.value = false
                                        dpadLeft.value = false
                                        dpadRight.value = false
                                    }
                                    sendFullState()
                                }
                                ElementType.ANALOG_STICK,
                                ElementType.DIGITAL_STICK,
                                ElementType.INVISIBLE_ANALOG_STICK,
                                ElementType.INVISIBLE_DIGITAL_STICK -> {
                                    val rad = el.radius.coerceAtLeast(el.width.coerceAtMost(el.height) / 2)
                                    val (sx, sy) = computeStickAxis(relX, relY, rad.toFloat())
                                    // 根据 upValue/downValue/leftValue/rightValue 判断左/右摇杆
                                    val isRightStick = el.leftValue == "a2" || el.rightValue == "a2"
                                    if (isRightStick) {
                                        rsX.value = sx; rsY.value = sy
                                    } else {
                                        lsX.value = sx; lsY.value = sy
                                    }
                                    // 双击摇杆 = 摇杆点击 (L3/R3)，这里只在按下且偏移很小时触发
                                    if (!isPressed) {
                                        if (isRightStick) { rsX.value = 0; rsY.value = 0 }
                                        else { lsX.value = 0; lsY.value = 0 }
                                    }
                                    sendFullState()
                                }
                                else -> { /* GROUP_BUTTON, SIMPLIFY_PERFORMANCE, WHEEL_PAD 暂不处理 */ }
                            }
                        }
                    }

                    // 确保按键映射元素在 UI 就绪后加载（避免初始化时序问题）
                    LaunchedEffect(Unit) {
                        if (engine.isCrownFeatureEnabled && overlayElements.isEmpty()) {
                            engine.reloadOverlay()
                        }
                    }

                    if (overlayElements.isNotEmpty()) {
                        KeyMappingOverlay(
                            elements = overlayElements,
                            modifier = Modifier.fillMaxSize(),
                            onElementAction = onElementAction,
                        )
                    }

                    // 面板 overlay（阶段 0 为空，后续阶段实现）
                    StreamOverlay(engine = engine, connectionStage = connectionStage)
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
        // 实时评估 PiP 条件，而非依赖缓存状态
        if (engine.connected && engine.prefConfig.enablePip) {
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

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // 防抖
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressTime < backPressDebounceMs) return
        lastBackPressTime = now
        // 当所有面板都已隐藏时，退出串流
        engine.disconnect()
    }
}
