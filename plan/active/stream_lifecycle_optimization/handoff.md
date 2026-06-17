# Handoff: stream_lifecycle_optimization（可独立执行版）

> 本文件包含另一个编程 agent 执行此计划所需的所有信息。
> **不依赖当前会话上下文** — 所有文件路径、行号、API、代码模式均已包含。

---

## 项目环境

- **项目**: Android Kotlin + Jetpack Compose
- **新代码包**: `com.alexclin.moonlink.stream.*`
- **旧代码包**: `com.limelight.*`（只读引用，除非明确批准，不做修改）
- **工作目录**: `app/src/main/java/com/alexclin/moonlink/stream/`
- **关键文件**:
  - `StreamActivity.kt` — Compose Activity，173行
  - `StreamEngine.kt` — 核心引擎，1103行
  - `StreamTouchHandler.kt` — 触控处理，320行

## 关键约束

1. **优先修改现有文件**，不创建新文件
2. **不修改 `com.limelight.*` 包** — 只引用其公开 API
3. 当必须与旧版类型交互（如 `ControllerHandler` 要求 `Game` 类型）时，使用轻量代理对象而非修改旧代码

---

## Batch A：onResume recreate 替换 + 生命周期基础修复

### 目标
用优雅重连替换 `recreate()`，添加 `shouldResumeSession` 逻辑。

### LA-1：StreamEngine 添加 `prepareConnection()`

**文件**: `StreamEngine.kt`

**新增字段**（在类开头附近）:
```kotlin
var shouldResumeSession = false
var isExtremeResumeEnabled = false
```

**新增方法**（在 `disconnect()` 方法附近）:
```kotlin
fun prepareConnection() {
    try { decoderRenderer?.prepareForStop() } catch (_: Exception) {}
    decoderRenderer = null
    audioRenderer = null
    createConnection()
    cachedSurfaceView?.let { initTouchHandler(it) }
    attemptedConnection = false
    connected = false
    isChangingResolution = false
    StreamLogger.log(activity, "RECONNECT", "重连准备完成")
    LimeLog.info("StreamEngine: 重连准备完成")
}
```

### LA-2：StreamActivity 替换 recreate

**文件**: `StreamActivity.kt`

**替换 `wasPaused` 单字段为多状态**:
```kotlin
private var wasPaused = false
private var wasBackgrounded = false
```

**替换 onResume**:
```kotlin
override fun onResume() {
    super.onResume()
    if (wasPaused) {
        wasPaused = false
        if (wasBackgrounded) {
            wasBackgrounded = false
            if (!engine.connected && engine.shouldResumeSession) {
                engine.shouldResumeSession = false
                engine.prepareConnection()
            }
        }
        return
    }
}
```

**替换 onPause**:
```kotlin
override fun onPause() {
    super.onPause()
    wasPaused = true
    wasBackgrounded = !isFinishing
    if (!isFinishing) engine.shouldResumeSession = true
    engine.onPause()
}
```

**删除** `recreate()` 调用（当前 L132）。

### LA-3：onWindowFocusChanged 增强

**文件**: `StreamActivity.kt`

```kotlin
override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        engine.onWindowFocusChanged(hasFocus)  // 新增
    }
}
```

**文件**: `StreamEngine.kt` — 新增方法:
```kotlin
fun onWindowFocusChanged(hasFocus: Boolean) {
    if (hasFocus) {
        clipboardSyncManager?.onFocusGained()
    }
    inputCaptureProvider?.onWindowFocusChanged(hasFocus)
}
```
（`clipboardSyncManager` 和 `inputCaptureProvider` 将在后续 Batch 中添加，先保留空安全调用）

---

## Batch B：onStop 保活 + 流时长统计

### LB-1：onStop 实现

**文件**: `StreamActivity.kt` — 新增:
```kotlin
override fun onStop() {
    super.onStop()
    if ((engine.isExtremeResumeEnabled || engine.isChangingResolution) && !isFinishing) {
        LimeLog.info("Stream: onStop 极速恢复拦截")
        if (!engine.isChangingResolution) {
            showKeepAliveNotification()
        }
        return
    }
    engine.onStopStreaming()
    if (!engine.shouldResumeSession && !isFinishing) {
        engine.shouldResumeSession = true
    }
}
```

**文件**: `StreamEngine.kt` — 新增字段:
```kotlin
private var accumulatedStreamTime: Long = 0
private var streamStartTime: Long = 0
private var lastActiveTime: Long = 0
var isStreamingActive = false
```

在 `connectionStarted()` 末尾添加:
```kotlin
streamStartTime = System.currentTimeMillis()
lastActiveTime = System.currentTimeMillis()
isStreamingActive = true
```

新增方法:
```kotlin
fun onStopStreaming() {
    if (isStreamingActive && lastActiveTime > 0) {
        accumulatedStreamTime += System.currentTimeMillis() - lastActiveTime
        isStreamingActive = false
        LimeLog.info("串流时长暂停，已累计: ${accumulatedStreamTime / 1000}秒")
    }
}

fun onStartStreaming() {
    if (!isStreamingActive && streamStartTime > 0) {
        lastActiveTime = System.currentTimeMillis()
        isStreamingActive = true
    }
}
```

### LB-2：WiFi Lock

**文件**: `StreamEngine.kt` — 新增字段:
```kotlin
import android.net.wifi.WifiManager
private var highPerfWifiLock: WifiManager.WifiLock? = null
private var lowLatencyWifiLock: WifiManager.WifiLock? = null
```

新增方法:
```kotlin
fun acquireWifiLocks() {
    val wifiMgr = activity.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    try {
        highPerfWifiLock = wifiMgr.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MoonLink High Perf")
        highPerfWifiLock?.setReferenceCounted(false)
        highPerfWifiLock?.acquire()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            lowLatencyWifiLock = wifiMgr.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MoonLink Low Latency")
            lowLatencyWifiLock?.setReferenceCounted(false)
            lowLatencyWifiLock?.acquire()
        }
    } catch (_: SecurityException) {}
}

fun releaseWifiLocks() {
    lowLatencyWifiLock?.release(); lowLatencyWifiLock = null
    highPerfWifiLock?.release(); highPerfWifiLock = null
}
```

**在 `initialize()` 末尾调用 `acquireWifiLocks()`；在 `release()` 末尾调用 `releaseWifiLocks()`**。

---

## Batch C：ControllerHandler 集成

### LC-1：添加 ControllerHandler（已选路径 B：接口提取方案）

**方案**：创建 `StreamInputCallbacks` 接口，修改 `ControllerHandler` 构造签名从 `Game` 改为 `StreamInputCallbacks`。`Game.kt` 和 `StreamEngine` 都实现此接口。

**新文件**: `engine/StreamInputCallbacks.kt`

```kotlin
package com.alexclin.moonlink.stream.engine

/** 串流输入回调 — 替换 ControllerHandler 对 Game 类型的依赖 */
interface StreamInputCallbacks : com.limelight.binding.input.evdev.EvdevListener,
    com.limelight.binding.input.GameGestures,
    com.limelight.ui.StreamView.InputCallbacks {

    override fun mouseMove(deltaX: Int, deltaY: Int)
    override fun mouseButtonEvent(buttonId: Int, down: Boolean)
    override fun mouseVScroll(amount: Byte)
    override fun mouseHScroll(amount: Byte)
    override fun keyboardEvent(down: Boolean, keyCode: Short)
    override fun showGameMenu(device: Any?)
    override fun toggleKeyboard()
}
```

**修改旧文件**: `com.limelight.binding.input.ControllerHandler`

构造签名改前:
```kotlin
class ControllerHandler(context: Context, conn: NvConnection, game: Game, pref: PreferenceConfiguration)
```
构造签名改后:
```kotlin
class ControllerHandler(context: Context, conn: NvConnection, callbacks: StreamInputCallbacks, pref: PreferenceConfiguration)
```
内部所有 `game.*` 调用改为 `callbacks.*`。

**修改旧文件**: `com.limelight.Game`

```kotlin
class Game : Activity(), ..., StreamInputCallbacks {
    // 已有方法签名匹配，只需添加 StreamInputCallbacks 到 implements 列表
}
```

**修改文件**: `StreamEngine.kt`

让 `StreamEngine` 实现 `StreamInputCallbacks`:

```kotlin
class StreamEngine(private val activity: Activity) : NvConnectionListener, StreamInputCallbacks {

    var controllerHandler: ControllerHandler? = null

    private fun initControllerHandler() {
        val c = conn ?: return
        controllerHandler = ControllerHandler(activity, c, this, prefConfig)
        LimeLog.info("StreamEngine: ControllerHandler 已初始化")
    }

    // --- StreamInputCallbacks 实现 ---
    override fun mouseMove(deltaX: Int, deltaY: Int) {
        conn?.sendMouseMove(deltaX.toShort(), deltaY.toShort())
    }
    override fun mouseButtonEvent(buttonId: Int, down: Boolean) {
        val btn = when (buttonId) {
            EvdevListener.BUTTON_LEFT -> MouseButtonPacket.BUTTON_LEFT
            EvdevListener.BUTTON_RIGHT -> MouseButtonPacket.BUTTON_RIGHT
            else -> return
        }
        if (down) conn?.sendMouseButtonDown(btn) else conn?.sendMouseButtonUp(btn)
    }
    override fun mouseVScroll(amount: Byte) { conn?.sendMouseScroll(amount) }
    override fun mouseHScroll(amount: Byte) { conn?.sendMouseHScroll(amount) }
    override fun keyboardEvent(down: Boolean, keyCode: Short) { /* 暂空，后续完善 */ }
    override fun showGameMenu(device: Any?) { /* 暂空 */ }
    override fun toggleKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.toggleSoftInput(0, 0)
    }
}
```

**调用位置**：在 `createConnection()` 末尾调用 `initControllerHandler()`。

### LC-2：回调补齐

**文件**: `StreamEngine.kt` — 替换以下空实现:

```kotlin
override fun rumble(cn: Short, lf: Short, hf: Short) {
    controllerHandler?.handleRumble(cn, lf, hf)
}
override fun rumbleTriggers(cn: Short, lt: Short, rt: Short) {
    controllerHandler?.handleRumbleTriggers(cn, lt, rt)
}
override fun setMotionEventState(cn: Short, mt: Byte, rr: Short) {
    controllerHandler?.handleSetMotionEventState(cn, mt, rr)
}
override fun setControllerLED(cn: Short, r: Byte, g: Byte, b: Byte) {
    controllerHandler?.handleSetControllerLED(cn, r, g, b)
}
override fun onResolutionChanged(w: Int, h: Int) {
    LimeLog.info("StreamEngine: 分辨率变化 ${w}x${h}")
    if (prefConfig.width == w && prefConfig.height == h) return
    prefConfig.width = w; prefConfig.height = h
    decoderRenderer?.onResolutionChanged(w, h)
}
```

---

## Batch D：InputCaptureProvider + 鼠标/光标管理

### LD-1：InputCaptureProvider

**文件**: `StreamEngine.kt`

```kotlin
var inputCaptureProvider: InputCaptureProvider? = null
var grabbedInput = true
var isCursorVisible = false

private fun initInputCapture() {
    // 注意：getInputCaptureProvider 第二个参数需要 Callback
    // 让 StreamEngine 实现 InputCaptureProvider.Callback 或在 Activity 中创建
    inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(
        activity, activity as InputCaptureProvider.Callback
    )
}

fun setInputGrabState(grab: Boolean) {
    if (grab) {
        inputCaptureProvider?.enableCapture()
        if (isCursorVisible) inputCaptureProvider?.showCursor()
    } else {
        inputCaptureProvider?.disableCapture()
    }
    grabbedInput = grab
}
```

**方案**：如果 `activity as InputCaptureProvider.Callback` 失败（类型不匹配），则在 `StreamActivity` 中实现该接口再传入。

### LD-2：CursorServiceManager

**文件**: `StreamEngine.kt` + `StreamActivity.kt`

StreamEngine 新增:
```kotlin
var cursorServiceManager: CursorServiceManager? = null

fun initCursorService(surfaceView: SurfaceView, cursorOverlay: CursorView) {
    cursorServiceManager = CursorServiceManager(
        surfaceView, cursorOverlay, prefConfig,
        touchHandler?.relativeTouchContextMap ?: arrayOfNulls(2),
        object : CursorServiceManager.UiCallback {
            override fun runOnUi(r: Runnable) = handler.post(r)
            override fun isActivityAlive() = !activity.isFinishing
        }
    )
    LimeLog.info("StreamEngine: CursorServiceManager 已初始化")
}
```

StreamActivity 中创建 CursorView:
```kotlin
// 在 setContent 的 Box 中，SurfaceView 之后添加：
AndroidView(
    factory = { ctx -> CursorView(ctx).also { engine.initCursorService(surfaceView, it) } },
    modifier = Modifier.fillMaxSize()
)
```
（需要先获得 SurfaceView 引用）

### LD-3：StreamTouchHandler 增强

**文件**: `StreamTouchHandler.kt`

增强 `handleNativeMousePointer` 支持右键/中键:
```kotlin
private fun handleNativeMousePointer(event: MotionEvent): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_MOVE -> conn.sendMouseMove(
            event.rawX.toInt().toShort(), event.rawY.toInt().toShort())
        MotionEvent.ACTION_BUTTON_PRESS -> {
            when (event.actionButton) {
                MotionEvent.BUTTON_PRIMARY -> conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                MotionEvent.BUTTON_SECONDARY -> conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                MotionEvent.BUTTON_TERTIARY -> conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
            }
        }
        MotionEvent.ACTION_BUTTON_RELEASE -> {
            when (event.actionButton) {
                MotionEvent.BUTTON_PRIMARY -> conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                MotionEvent.BUTTON_SECONDARY -> conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                MotionEvent.BUTTON_TERTIARY -> conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
            }
        }
        MotionEvent.ACTION_SCROLL -> {
            conn.sendMouseHighResScroll((event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120).toInt().toShort())
            conn.sendMouseHighResHScroll((event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120).toInt().toShort())
        }
    }
    return true
}
```

---

## Batch E：PictureInPicture 实现

### LE-1：StreamActivity PiP

**文件**: `StreamActivity.kt`

```kotlin
// 新增导入
import android.util.Rational
import android.app.PictureInPictureParams
import androidx.annotation.RequiresApi

class StreamActivity {
    private var autoEnterPip = false
    private var suppressPipRefCount = 0

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getPictureInPictureParams(): PictureInPictureParams {
        val pref = engine.prefConfig
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(pref.width, pref.height))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterPip && suppressPipRefCount == 0)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun updatePipAutoEnter() {
        if (!prefConfig.enablePip) return
        autoEnterPip = engine.connected && suppressPipRefCount == 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.Q) {
            if (autoEnterPip) enterPictureInPictureMode(getPictureInPictureParams())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onPictureInPictureRequested(): Boolean {
        return enterPictureInPictureMode(getPictureInPictureParams())
    }
}
```

### LE-2：StreamEngine PiP

**文件**: `StreamEngine.kt`

```kotlin
fun onPiPChanged(inPip: Boolean) {
    if (inPip) {
        // 暂停传感器、音频
        audioRenderer?.pauseProcessing()
        decoderRenderer?.pauseProcessing()
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        audioRenderer?.resumeProcessing()
        decoderRenderer?.resumeProcessing()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

---

## Batch F：连接增强 + 剪贴板同步

### LF-1：ClipboardSyncManager

**文件**: `StreamEngine.kt`

```kotlin
var clipboardSyncManager: ClipboardSyncManager? = null

private fun startClipboardSyncIfEnabled() {
    if (clipboardSyncManager != null) return
    val wantText = prefConfig.enableClipboardSyncText
    val wantImage = prefConfig.enableClipboardSyncImage
    if (!wantText && !wantImage) return
    clipboardSyncManager = ClipboardSyncManager(
        context = activity.applicationContext,
        syncText = wantText,
        syncImage = wantImage,
        fileProviderAuthority = "${activity.packageName}.clipboard_fileprovider",
        nvHttpProvider = { conn?.createNvHttp() },
    )
    runCatching { clipboardSyncManager?.start() }
        .onFailure { LimeLog.warning("剪贴板同步启动失败: ${it.message}") }
}
```

在 `connectionStarted()` 末尾调用。

---

## Batch G：全量回归验证

### LG-1：验证清单

| # | 验证项 | 操作 | 预期 |
|---|--------|------|------|
| 1 | 优雅重连 | 串流中 Home → 返回 | 无缝恢复，无黑屏 |
| 2 | 快速切换 | 串流 → 切换 App → 返回 | 重连或恢复 |
| 3 | 流时长 | 多次进出后台 → finish | 累计时间正确 |
| 4 | 后台通知 | 串流进后台 | 显示保活通知 |
| 5 | WiFi Lock | 串流中查看 WiFi | 高功耗模式 |
| 6 | 实体手柄 | 蓝牙手柄连接 | 按键映射正常 |
| 7 | PiP | Home → 自动 PiP → 返回 | 正常进出 |
| 8 | 剪贴板 | 远程桌面复制文字 | 本地可粘贴 |
| 9 | 旧 Game.kt | 旧入口串流 | 不受影响 |

---

## 已知限制

| 项目 | 说明 |
|------|------|
| ControllerHandler 路径 B 需修改旧代码 | 创建 `StreamInputCallbacks` 接口 + 修改 `ControllerHandler` 构造 + `Game.kt` 实现接口。改量有限，约 3 个文件各 2-5 行 |
| InputCaptureProvider.Callback 接口 | 若 Activity 类型不匹配，改由 StreamEngine 实现该接口 |
| PiP SurfaceView 缩小 | Compose 中 SurfaceView 在 PiP 模式下可能表现不同，需实测 |
