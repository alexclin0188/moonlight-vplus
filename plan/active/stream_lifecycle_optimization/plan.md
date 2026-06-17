# 新版串流页生命周期优化 + 缺失核心功能补齐

> 创建日期：2026-06-17 | 状态：prepare | 版本：1.0
> 基于对比分析的 7 个差距项，分 7 个 Batch 实施。
> 依赖前置：`stream_ui_completion`（UI 面板功能基本完成）

---

## 一、当前现状与问题清单

### 1.1 核心差距摘要

| # | 差距项 | 严重度 | 对应旧版代码 |
|---|--------|--------|-------------|
| G1 | `onResume` 用 `recreate()` 粗暴重建 | 🔴 严重 | Game.kt `onStart` → `prepareConnection()` |
| G2 | 未重写 `onStop`，无流时长统计/保活 | 🔴 严重 | Game.kt `onStop` (L1205-1265) |
| G3 | 无 `ControllerHandler`，实体手柄/键盘映射不可用 | 🔴 严重 | Game.kt `createConnectionAndHandler()` (L639-663) |
| G4 | 无 `InputCaptureProvider`，鼠标无法捕获/隐藏 | 🟡 中等 | Game.kt L354-360 |
| G5 | PiP 声明但未实现任何逻辑 | 🟡 中等 | Game.kt L978-1060 |
| G6 | 无 `ClipboardSyncManager` | 🟡 中等 | Game.kt L1459-1480 |
| G7 | `rumble`/`onResolutionChanged` 等回调空实现 | 🟢 低 | Game.kt 各处 |

### 1.2 当前代码基线

| 文件 | 行数 | 说明 |
|------|------|------|
| `StreamActivity.kt` | 173 | Compose Activity，生命周期管理薄弱 |
| `StreamEngine.kt` | 1103 | 核心引擎，缺少 ControllerHandler/InputCaptureProvider |
| `StreamTouchHandler.kt` | 320 | 触控处理，缺少光标同步 |
| `StreamOverlay.kt` | 634 | UI 覆盖层 |

---

## 二、Batch A：onResume recreate 替换 + 生命周期基础修复

### 问题

```kotlin
// 当前 StreamActivity.onResume():
override fun onResume() {
    super.onResume()
    if (wasPaused) {
        wasPaused = false
        recreate()  // ★ 粗暴重建，黑屏闪烁
        return
    }
    engine.onResume()  // ★ 空实现
}
```

旧版做法：在 `onStart()` 中判断 `shouldResumeSession` → `prepareConnection()` 重连。

### LA-1：StreamEngine 添加重连能力

**文件**：`StreamEngine.kt`

```kotlin
class StreamEngine {
    var shouldResumeSession = false
    var isExtremeResumeEnabled = false

    fun prepareConnection() {
        // 1. 销毁旧解码器 + 音频渲染器
        try { decoderRenderer?.prepareForStop() } catch (_: Exception) {}
        decoderRenderer = null
        audioRenderer = null

        // 2. 重新创建 NvConnection（复用 createConnection）
        createConnection()

        // 3. 触控重新初始化
        cachedSurfaceView?.let { initTouchHandler(it) }

        // 4. 重置连接状态
        attemptedConnection = false
        connected = false
        isChangingResolution = false
        StreamLogger.log(activity, "RECONNECT", "重连准备完成")
    }
}
```

**可复用**：`createConnection()`（L280-396）、`initTouchHandler()`（L553-559）。

### LA-2：StreamActivity.onResume 替换 recreate

**文件**：`StreamActivity.kt`

```kotlin
class StreamActivity : ComponentActivity() {
    private var wasPaused = false
    private var wasBackgrounded = false

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
        // engine.onResume() // 保持空实现或关联 KeyboardService
    }

    override fun onPause() {
        super.onPause()
        wasPaused = true
        wasBackgrounded = isFinishing
        if (!isFinishing) engine.shouldResumeSession = true
        engine.onPause()
    }
}
```

**关键**：避免 `recreate()` 后的 Activity 重建闪烁。`surfaceChanged` 会在 `prepareConnection` 后重新触发，`startStreaming()` 中 `attemptedConnection=false` 允许重新 `conn.start()`。

### LA-3：onWindowFocusChanged 增强

**文件**：`StreamActivity.kt` + `StreamEngine.kt`

```kotlin
// StreamActivity
override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
        // 保持全屏（已有）
        window.decorView.systemUiVisibility = ...
        // 新增：通知 engine
        engine.onWindowFocusChanged(hasFocus)
    }
}

// StreamEngine 新增方法：
fun onWindowFocusChanged(hasFocus: Boolean) {
    if (hasFocus) {
        clipboardSyncManager?.onFocusGained()
    }
    inputCaptureProvider?.onWindowFocusChanged(hasFocus)
}
```

---

## 三、Batch B：onStop 保活 + 流时长统计 + WiFi Lock

### 问题

旧版 `onStop` 有 ~50 行关键逻辑：极速恢复保活、流时长统计、标记应恢复会话、后台通知。

### LB-1：onStop 实现

**文件**：`StreamActivity.kt` + `StreamEngine.kt`

```kotlin
// StreamActivity.onStop
override fun onStop() {
    super.onStop()

    if ((engine.isExtremeResumeEnabled || engine.isChangingResolution) && !isFinishing) {
        LimeLog.info("onStop: 极速恢复拦截")
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

```kotlin
// StreamEngine 新增
private var accumulatedStreamTime: Long = 0
private var streamStartTime: Long = 0
private var lastActiveTime: Long = 0
var isStreamingActive = false

// 在 connectionStarted 中激活
override fun connectionStarted() {
    connected = true
    streamStartTime = System.currentTimeMillis()
    lastActiveTime = System.currentTimeMillis()
    isStreamingActive = true
    // ... 原有逻辑
}

fun onStopStreaming() {
    if (isStreamingActive && lastActiveTime > 0) {
        accumulatedStreamTime += System.currentTimeMillis() - lastActiveTime
        isStreamingActive = false
    }
}

fun onStartStreaming() {
    if (!isStreamingActive && streamStartTime > 0) {
        lastActiveTime = System.currentTimeMillis()
        isStreamingActive = true
    }
}
```

**后台通知**：直接调用 `StreamNotificationService.start(this, pcName, appName)`。

### LB-2：WiFi Lock + 计费网络提示

**文件**：`StreamEngine.kt`

```kotlin
class StreamEngine {
    private var highPerfWifiLock: WifiManager.WifiLock? = null
    private var lowLatencyWifiLock: WifiManager.WifiLock? = null

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

    fun checkMeteredNetwork() {
        val connMgr = activity.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        if (connMgr.isActiveNetworkMetered) {
            displayTransientMessage("当前为计费网络，请注意流量消耗")
        }
    }
}
```

**调用时机**：`initialize()` 中调 `acquireWifiLocks()` + `checkMeteredNetwork()`；`release()` 中调 `releaseWifiLocks()`。

---

## 四、Batch C：ControllerHandler 集成

### 问题

新版完全缺少 `ControllerHandler`：实体手柄不可用、键盘映射入口占位、rumble 等回调空实现。

### LC-1：添加 ControllerHandler

**文件**：`StreamEngine.kt`

**核心问题**：`ControllerHandler` 构造签名要求 `Game` 类型（旧版 Activity）。

**三选一路径（已确认选择路径 B）**：

| 路径 | 方案 | 优点 | 缺点 |
|------|------|------|------|
| A | EvdevListener 代理，不修改旧代码 | 无侵入 | ControllerHandler 构造依赖 Game 更多接口，代理脆弱 |
| **B ✅** | **提取 StreamInputCallbacks 接口，修改 ControllerHandler + Game.kt** | **解耦干净，长期维护好** | **需修改旧代码但改量可控** |
| C | 推迟，Toast 占位 | 无风险 | 功能继续缺失 |

**路径 B（已选）实现要点**：

**第一步**：创建 `StreamInputCallbacks` 接口（新文件 `StreamInputCallbacks.kt`，放在 `com.alexclin.moonlink.stream.engine` 包下）：

```kotlin
package com.alexclin.moonlink.stream.engine

import com.limelight.binding.input.evdev.EvdevListener
import com.limelight.binding.input.GameGestures

/**
 * 串流输入回调接口 — 提取自 Game.kt 中对 ControllerHandler 暴露的方法。
 * 新串流 StreamEngine 实现此接口，替代对 Game 类型的强依赖。
 */
interface StreamInputCallbacks : EvdevListener, GameGestures, StreamView.InputCallbacks {
    override fun mouseMove(deltaX: Int, deltaY: Int)
    override fun mouseButtonEvent(buttonId: Int, down: Boolean)
    override fun mouseVScroll(amount: Byte)
    override fun mouseHScroll(amount: Byte)
    override fun keyboardEvent(down: Boolean, keyCode: Short)
    override fun showGameMenu(device: Any?)
    override fun toggleKeyboard()
}
```

**第二步**：修改 `ControllerHandler` 构造签名（旧代码 `com.limelight.binding.input.ControllerHandler`）：

```kotlin
// 改前：
class ControllerHandler(context: Context, conn: NvConnection, game: Game, pref: PreferenceConfiguration)

// 改后：
class ControllerHandler(context: Context, conn: NvConnection, callbacks: StreamInputCallbacks, pref: PreferenceConfiguration)
```

同时修改 `ControllerHandler` 内部所有 `game.*` 调用改为 `callbacks.*`。

**第三步**：`Game.kt` 实现 `StreamInputCallbacks` 接口（已有方法签名匹配，仅添加 `: StreamInputCallbacks` 声明）：

```kotlin
class Game : Activity(), ..., StreamInputCallbacks {
    // 所有方法已存在，无需修改实现
}
```

**第四步**：`StreamEngine` 实现 `StreamInputCallbacks` 接口：

```kotlin
class StreamEngine(
    private val activity: Activity
) : NvConnectionListener, StreamInputCallbacks {

    var controllerHandler: ControllerHandler? = null

    private fun initControllerHandler() {
        val c = conn ?: return
        controllerHandler = ControllerHandler(activity, c, this, prefConfig)
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
    override fun keyboardEvent(down: Boolean, keyCode: Short) { /* 暂空 */ }
    override fun showGameMenu(device: Any?) { /* 暂空 */ }
    override fun toggleKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(0, 0)
    }
}
```

### LC-2：NvConnectionListener 回调补齐

```kotlin
override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
    controllerHandler?.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor)
}

override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
    controllerHandler?.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger)
}

override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
    controllerHandler?.handleSetMotionEventState(controllerNumber, motionType, reportRateHz)
}

override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
    controllerHandler?.handleSetControllerLED(controllerNumber, r, g, b)
}

override fun onResolutionChanged(width: Int, height: Int) {
    LimeLog.info("StreamEngine: onResolutionChanged ${width}x${height}")
    if (prefConfig.width == width && prefConfig.height == height) return
    prefConfig.width = width
    prefConfig.height = height
    decoderRenderer?.onResolutionChanged(width, height)
}
```

---

## 五、Batch D：InputCaptureProvider + 鼠标/光标管理

### LD-1：InputCaptureProvider 集成

**文件**：`StreamEngine.kt`

```kotlin
class StreamEngine : InputCaptureProvider.Callback {
    var inputCaptureProvider: InputCaptureProvider? = null
    var grabbedInput = true
    var isCursorVisible = false

    private fun initInputCapture(v: View) {
        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(activity, this)
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

    // InputCaptureProvider.Callback 实现
    override fun getActiveStreamView(): View? = cachedSurfaceView
    override fun getInputCaptureOverlay(): View? = null
    override fun isCapturing(): Boolean = grabbedInput
    override fun onCaptureStateChanged(capturing: Boolean) {
        if (capturing) isCursorVisible = false
    }
}
```

**注意**：需要完全理解 `InputCaptureProvider.Callback` 接口的全部方法签名。若太多，先只实现必要的最小集。

### LD-2：CursorServiceManager 集成

**文件**：`StreamEngine.kt` + `StreamActivity.kt`（添加 CursorView 到 Compose 树）

```kotlin
// StreamEngine
var cursorServiceManager: CursorServiceManager? = null

private fun initCursorService(surfaceView: SurfaceView) {
    // CursorView 在 Compose 中需通过 AndroidView 嵌入
    // Activity 需要提供一个 CursorView 实例
    val cursorOverlay = activity.findViewById<CursorView>(...)
    cursorServiceManager = CursorServiceManager(
        surfaceView, cursorOverlay, prefConfig,
        touchHandler?.relativeTouchContextMap ?: arrayOfNulls(2),
        object : CursorServiceManager.UiCallback {
            override fun runOnUi(r: Runnable) = handler.post(r)
            override fun isActivityAlive() = !activity.isFinishing
        }
    )
}
```

**Compose 集成**：在 `StreamActivity` 的 `setContent` 中，将 `CursorView` 作为另一个 `AndroidView` 叠加在 `SurfaceView` 之上。

### LD-3：StreamTouchHandler 原生鼠标增强

**文件**：`StreamTouchHandler.kt`

增加与 InputCaptureProvider 的联动标记（无需直接引用，通过回调联动）：

```kotlin
// 新增字段
var onCursorStateChanged: ((Boolean) -> Unit)? = null

// handleNativeMousePointer 增强：处理右键、中键
private fun handleNativeMousePointer(event: MotionEvent): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_MOVE -> {
            conn.sendMouseMove(event.rawX.toInt().toShort(), event.rawY.toInt().toShort())
        }
        MotionEvent.ACTION_BUTTON_PRESS -> {
            when (event.actionButton) {
                MotionEvent.BUTTON_PRIMARY ->
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                MotionEvent.BUTTON_SECONDARY ->
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                MotionEvent.BUTTON_TERTIARY ->
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE)
            }
        }
        MotionEvent.ACTION_BUTTON_RELEASE -> {
            when (event.actionButton) {
                MotionEvent.BUTTON_PRIMARY ->
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                MotionEvent.BUTTON_SECONDARY ->
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                MotionEvent.BUTTON_TERTIARY ->
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE)
            }
        }
    }
    return true
}
```

---

## 六、Batch E：PictureInPicture 实际实现

### LE-1：StreamActivity PiP 入口

**文件**：`StreamActivity.kt`

```kotlin
class StreamActivity : ComponentActivity() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appName?.let { builder.setTitle(it) }
            pcName?.let { builder.setSubtitle(it) }
        }
        return builder.build()
    }

    fun updatePipAutoEnter() {
        if (!prefConfig.enablePip) return
        val autoEnter = engine.connected && suppressPipRefCount == 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams())
        } else {
            autoEnterPip = autoEnter
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (autoEnterPip) {
                enterPictureInPictureMode(getPictureInPictureParams())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onPictureInPictureRequested(): Boolean {
        return enterPictureInPictureMode(getPictureInPictureParams())
    }
}
```

**关键**：`appName` 和 `pcName` 需要从 StreamEngine 暴露。

### LE-2：PiP 生命周期管理

**文件**：`StreamEngine.kt`

```kotlin
fun onConfigurationChanged(newConfig: Configuration) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = activity as? ComponentActivity ?: return
        if (activity.isInPictureInPictureMode) {
            // PiP 进入：暂停传感器/音频/部分渲染
            audioRenderer?.let {
                if (!prefConfig.enableBackgroundAudio) it.pauseProcessing()
            }
        } else {
            // PiP 退出：恢复
            audioRenderer?.resumeProcessing()
            decoderRenderer?.resumeProcessing()
        }
    }
}
```

### LE-3：DisplaySection PiP 开关联动

**文件**：`SubPanelContainer.kt`（已在 `MoreSection` 中有对应设置）

确认 `engine.prefConfig.enablePip` 读取正确，并在 PiP 开关切换时调用 `engine.togglePip()`（已存在，L676-679）。

---

## 七、Batch F：连接错误处理增强 + 剪贴板同步

### LF-1：ClipboardSyncManager 集成

**文件**：`StreamEngine.kt`

```kotlin
class StreamEngine {
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
            .onFailure { LimeLog.warning("Clipboard sync start failed: ${it.message}") }
    }

    // connectionStarted 末尾调用
    override fun connectionStarted() {
        // ... 原有逻辑
        startClipboardSyncIfEnabled()
    }
}
```

### LF-2：connectionTerminated 增强

**文件**：`StreamEngine.kt` L1001-1043

在断连对话框中增加更多错误信息（参考旧版 `showLatencyToast`）：

```kotlin
override fun connectionTerminated(errorCode: Int) {
    // ... 原有逻辑不变
    // 在显示 Dialog 之前，如果有解码器数据，附加延迟信息
}
```

### LF-3：onResolutionChanged 实现

参见 Batch C 的 LC-2 中已包含。

---

## 八、Batch G：全量回归验证

### LG-1：验证清单

| # | 验证项 | 方法 |
|---|--------|------|
| V1 | onResume 优雅重连 | 串流中按 Home → 从最近任务返回 → 应无缝恢复，无黑屏闪烁 |
| V2 | 快速切换 App | 串流中切到其他 App → 返回 → 应重连或恢复 |
| V3 | 流时长统计 | 多次进出后台 → onDestroy 时累计时间正确 |
| V4 | 后台通知 | 串流进后台后显示保活通知 |
| V5 | WiFi Lock | 串流中查看 WiFi 状态 → 应保持高功耗模式 |
| V6 | 实体手柄 | 连接蓝牙手柄 → 操作映射正常 |
| V7 | 鼠标捕获 | 切换鼠标模式 → 光标隐藏/捕获正确 |
| V8 | PictureInPicture | 按 Home → 自动进 PiP → 从 PiP 返回 → 恢复渲染 |
| V9 | 剪贴板同步 | 远程桌面中复制文字 → 本地可粘贴 |
| V10 | 旧 Game.kt | 旧入口串流完全不受影响 |

---

## 九、文件变更总清单

| 操作 | 路径 | 预计行数 | 说明 |
|------|------|---------|------|
| 修改 | `StreamActivity.kt` | ~+150 | 生命周期重写 + PiP |
| 修改 | `StreamEngine.kt` | ~+300 | prepareConnection/onStop/ControllerHandler/InputCapture/ClipboardSync/回调补齐 |
| 修改 | `StreamTouchHandler.kt` | ~+50 | 原生鼠标增强 + 光标回调 |
| 修改 | `StreamOverlay.kt` | ~+20 | CursorView 嵌入（如需要） |
| 修改 | `AndroidManifest.xml` | ~0 | 无需改动 |

> **不新建文件**：所有改动在现有文件中完成。

---

## 十、风险与缓解

| 风险 | 缓解 |
|------|------|
| `ControllerHandler` 构造需要 `Game` 类型，无法直接创建 | 使用 EvdevListener 代理 + 检查构造签名。如果必须修改旧代码，先在旧代码中提接口再引用 |
| `InputCaptureProvider.Callback` 接口方法过多，不易全部实现 | 先只实现最核心的 `getActiveStreamView()` 和 `onCaptureStateChanged()` |
| `prepareConnection()` 中销毁 decoderRenderer 后，surface 可能不会重新触发 | 在 `prepareConnection()` 末尾手动触发 `cachedSurfaceView?.holder?.surface?.let { /* 触发 surfaceChanged */ }` |
| PiP 在 Compose Activity 中行为与旧版不同 | Compose Activity (`ComponentActivity`) 是 `AppCompatActivity` 的子类，PiP API 行为一致。但 SurfaceView 在 PiP 模式下可能缩小，需测试 |
| 新串流页面功能增多导致 StreamEngine 膨胀 | 如果 StreamEngine 超过 1500 行，可按职责拆分为独立的 Manager 类（ControllerManager、DisplayManager 等） |
