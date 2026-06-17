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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.StreamOverlay
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

    /** 自动 PiP 模式（由 preferences 控制） */
    private var autoEnterPip = false

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
        if (autoEnterPip && engine.connected) {
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
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            engine.onWindowFocusChanged(hasFocus)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 防抖
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressTime < backPressDebounceMs) return
        lastBackPressTime = now
        // 当所有面板都已隐藏时，退出串流
        engine.disconnect()
    }
}
