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

/**
 * MoonLink 新版串流 Activity。
 *
 * 使用 Compose 构建 UI，通过 [StreamEngine] 封装底层串流连接。
 * 与旧版 [com.limelight.Game] 并行存在，不修改任何旧代码。
 */
class StreamActivity : ComponentActivity() {

    private lateinit var engine: StreamEngine
    private var wasPaused = false
    private var lastBackPressTime = 0L
    private val backPressDebounceMs = 300L

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
            recreate()
            return
        }
        engine.onResume()
    }

    override fun onPause() {
        super.onPause()
        wasPaused = true
        engine.onPause()
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
