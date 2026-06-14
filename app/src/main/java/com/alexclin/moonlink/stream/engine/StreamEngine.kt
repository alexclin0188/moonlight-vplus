package com.alexclin.moonlink.stream.engine

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import com.limelight.Game
import com.limelight.LimeLog
import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.SmartAudioRenderer
import com.limelight.binding.video.CrashListener
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.binding.video.PerformanceInfo
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.GlPreferences
import com.limelight.utils.AppSettingsManager
import kotlin.math.roundToInt
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 封装 Moonlight 串流核心引擎：NvConnection 创建、解码器、音频渲染。
 *
 * 引用 [com.limelight] 包中的底层类，不修改任何旧代码。
 */
class StreamEngine(private val activity: Activity) : NvConnectionListener {

    /** 从 SharedPreferences 读取的全部串流配置 */
    lateinit var prefConfig: PreferenceConfiguration

    /** 当前串流应用对象 */
    lateinit var app: NvApp

    /** 串流连接 */
    var conn: NvConnection? = null
        private set

    /** 视频解码渲染器 */
    private var decoderRenderer: MediaCodecDecoderRenderer? = null

    /** 音频渲染器 */
    private var audioRenderer: SmartAudioRenderer? = null

    /** 是否已连接 */
    var connected = false
        private set

    /** 是否已尝试过连接（防止 surfaceChanged 重复启动） */
    private var attemptedConnection = false

    /** 是否使用极速恢复（surface 重建时不中断串流，仅暂停渲染） */
    private var extremeResumeEnabled = false

    /** 缓存的 SurfaceHolder（用于 setRenderTarget） */
    private var cachedSurfaceHolder: SurfaceHolder? = null

    /** SurfaceView（由外部设置） */
    var surfaceView: SurfaceView? = null

    /** 串流结束回调 */
    var onStreamEnded: (() -> Unit)? = null

    /** 连接阶段状态回调 */
    var onStageUpdate: ((stage: String, complete: Boolean, failed: Boolean) -> Unit)? = null

    // ── NvConnection 构造所需参数（从 Intent 提取） ──
    private lateinit var host: String
    private var port: Int = 0
    private var httpsPort: Int = 0
    private var uniqueId: String = ""
    private var pairName: String = ""
    private var serverCert: X509Certificate? = null
    private var displayName: String? = null
    private var forceResumeCurrentSession: Boolean = false
    private var pcUseVdd: Boolean = false
    private var pcUuid: String? = null
    private var pcName: String? = null
    private var appId: Int = NvApp.DESKTOP_APP_ID

    /** 解码器连续崩溃计数（从 tombstone 缓存读取） */
    private var consecutiveDecoderCrashCount: Int = 0

    /** OpenGL 渲染器类型 */
    private var glRenderer: String = "opengl"

    private val handler = Handler(Looper.getMainLooper())

    // ========================================================================
    // 初始化
    // ========================================================================

    /**
     * 从 [Intent] 中解析串流参数并初始化配置。
     *
     * @return 是否初始化成功
     */
    fun initialize(intent: Intent): Boolean {
        return try {
            // 1. 读取全局串流偏好
            prefConfig = PreferenceConfiguration.readPreferences(activity)

            // 2. 应用"以最近一次配置启动"（若 Intent 中包含）
            AppSettingsManager(activity).applyLastSettingsFromIntent(intent, prefConfig)

            // 3. 从 Intent 提取参数
            host = intent.getStringExtra(Game.EXTRA_HOST) ?: return fail("缺少 host")
            port = intent.getIntExtra(Game.EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT)
            httpsPort = intent.getIntExtra(Game.EXTRA_HTTPS_PORT, 0)
            uniqueId = intent.getStringExtra(Game.EXTRA_UNIQUEID) ?: ""
            pairName = intent.getStringExtra(Game.EXTRA_PAIR_NAME) ?: ""
            displayName = intent.getStringExtra(Game.EXTRA_DISPLAY_NAME)
            forceResumeCurrentSession = intent.getBooleanExtra(Game.EXTRA_FORCE_RESUME_CURRENT_SESSION, false)
            pcUseVdd = intent.getBooleanExtra(Game.EXTRA_PC_USEVDD, false)
            pcUuid = intent.getStringExtra(Game.EXTRA_PC_UUID)
            pcName = intent.getStringExtra(Game.EXTRA_PC_NAME)

            // 解析服务器证书
            val certBytes = intent.getByteArrayExtra(Game.EXTRA_SERVER_CERT)
            if (certBytes != null) {
                val cf = CertificateFactory.getInstance("X.509")
                serverCert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            }

            // 处理 VDD 屏幕组合模式（若 Intent 中携带则覆盖 Preferences 默认值）
            prefConfig.vddScreenCombinationMode = intent.getIntExtra(
                Game.EXTRA_VDD_SCREEN_COMBINATION_MODE, prefConfig.vddScreenCombinationMode
            )
            prefConfig.screenCombinationMode = intent.getIntExtra(
                Game.EXTRA_SCREEN_COMBINATION_MODE, prefConfig.screenCombinationMode
            )

            // 4. 构建 NvApp 对象
            val appName = intent.getStringExtra(Game.EXTRA_APP_NAME) ?: "Desktop"
            appId = intent.getIntExtra(Game.EXTRA_APP_ID, NvApp.DESKTOP_APP_ID)
            val appHdr = intent.getBooleanExtra(Game.EXTRA_APP_HDR, false)
            app = NvApp(appName, appId, appHdr)

            // 处理 cmdList
            val cmdListJson = intent.getStringExtra(Game.EXTRA_APP_CMD)
            if (!cmdListJson.isNullOrEmpty()) {
                app.setCmdList(cmdListJson)
            }

            // 5. 恢复解码器 tombstone 中的崩溃计数
            val tombstonePrefs = activity.getSharedPreferences("DecoderTombstone", 0)
            consecutiveDecoderCrashCount = if (appId == NvApp.DESKTOP_APP_ID) {
                tombstonePrefs.getInt("consecutive_crash_count_desktop", 0)
            } else {
                tombstonePrefs.getInt("consecutive_crash_count", 0)
            }

            // 6. 读取 OpenGL 渲染器偏好并初始化 MediaCodecHelper（必须在创建解码器之前）
            glRenderer = GlPreferences.readPreferences(activity).glRenderer
            MediaCodecHelper.initialize(activity, glRenderer)

            // 7. 创建 NvConnection（延迟到 surface 可用时 start）
            createConnection()

            LimeLog.info("StreamEngine: 初始化完成 host=$host port=$port app=${app.appName}")
            true
        } catch (e: Exception) {
            LimeLog.severe("StreamEngine: 初始化失败 ${e.message}")
            Toast.makeText(activity, "串流初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun fail(msg: String): Boolean {
        LimeLog.severe("StreamEngine: $msg")
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
        return false
    }

    // ========================================================================
    // 创建解码器 + NvConnection
    // ========================================================================

    private fun createConnection() {
        var willStreamHdr = prefConfig.enableHdr && app.isHdrSupported()

        // 1) 先创建 MediaCodecDecoderRenderer（旧代码在 buildStreamConfiguration 之前创建）
        decoderRenderer = MediaCodecDecoderRenderer(
            activity,
            prefConfig,
            CrashListener { e ->
                LimeLog.severe("StreamEngine: 解码器崩溃 ${e.message}")
                val tp = activity.getSharedPreferences("DecoderTombstone", 0)
                val key = if (appId == NvApp.DESKTOP_APP_ID)
                    "consecutive_crash_count_desktop" else "consecutive_crash_count"
                tp.edit().putInt(key, tp.getInt(key, 0) + 1).apply()
            },
            consecutiveDecoderCrashCount,
            false, // meteredData
            willStreamHdr,
            glRenderer,
            object : PerfOverlayListener {
                override fun onPerfUpdateV(performanceInfo: PerformanceInfo) {}
                override fun onPerfUpdateWG(performanceInfo: PerformanceInfo) {}
                override fun isPerfOverlayVisible(): Boolean = false
            }
        )

        // 2) 检查 AVC 解码器是否可用（旧代码 line 479）
        if (decoderRenderer?.isAvcSupported() != true) {
            Toast.makeText(activity, "设备不支持 H.264 硬件解码", Toast.LENGTH_LONG).show()
            throw IllegalStateException("No AVC decoder available")
        }

        // 3) 检查 HDR 支持
        if (willStreamHdr) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val hdrCaps = activity.windowManager.defaultDisplay.hdrCapabilities
                if (hdrCaps == null || !hdrCaps.supportedHdrTypes.contains(
                        android.view.Display.HdrCapabilities.HDR_TYPE_HDR10
                    )
                ) {
                    willStreamHdr = false
                }
            } else {
                willStreamHdr = false
            }
            if (willStreamHdr && decoderRenderer?.isHevcMain10Supported() != true &&
                decoderRenderer?.isAv1Main10Supported() != true
            ) {
                willStreamHdr = false
            }
        }

        // 4) 构建 StreamConfiguration
        val config = buildStreamConfiguration(willStreamHdr)

        // 5) 首帧回调：通知 UI 连接完成，关闭进度 overlay
        decoderRenderer?.firstFrameCallback = {
            handler.post { onStageUpdate?.invoke("connected", true, false) }
        }

        // 6) 创建 NvConnection
        conn = NvConnection(
            activity.applicationContext,
            ComputerDetails.AddressTuple(host, port),
            httpsPort,
            uniqueId,
            pairName,
            config,
            PlatformBinding.getCryptoProvider(activity),
            serverCert,
            displayName,
            forceResumeCurrentSession
        )

        LimeLog.info("StreamEngine: NvConnection 已创建")
    }

    /**
     * 根据 [PreferenceConfiguration] 构建 [StreamConfiguration]。
     * 调用前必须保证 [decoderRenderer] 已创建。
     */
    private fun buildStreamConfiguration(willStreamHdr: Boolean): StreamConfiguration {
        // 支持的视频格式（基于实际解码器能力，与旧代码一致）
        var supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264
        if (decoderRenderer?.isHevcSupported() == true) {
            supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_H265
            if (willStreamHdr && decoderRenderer?.isHevcMain10Supported() == true) {
                supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_H265_MAIN10
            }
        }
        if (decoderRenderer?.isAv1Supported() == true) {
            supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            if (willStreamHdr && decoderRenderer?.isAv1Main10Supported() == true) {
                supportedVideoFormats = supportedVideoFormats or MoonBridge.VIDEO_FORMAT_AV1_MAIN10
            }
        }

        // 帧率调整（旧代码 line 784-799）
        val displayRefreshRate = activity.windowManager.defaultDisplay.refreshRate
        val roundedRefreshRate = displayRefreshRate.roundToInt()
        var chosenFrameRate = prefConfig.fps
        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            if (prefConfig.fps >= roundedRefreshRate) {
                if (prefConfig.fps > roundedRefreshRate + 3) {
                    // 使用 drop 模式 → 不改帧率
                } else if (roundedRefreshRate <= 49) {
                    // 刷新率太低
                } else {
                    chosenFrameRate = roundedRefreshRate - 1
                }
            }
        }
        val clientRefreshRateX100 = (displayRefreshRate * 100).roundToInt()

        return StreamConfiguration.Builder()
            .setResolution(prefConfig.width, prefConfig.height)
            .setLaunchRefreshRate(prefConfig.fps)
            .setRefreshRate(chosenFrameRate)
            .setApp(app)
            .setBitrate(prefConfig.bitrate)
            .setResolutionScale(prefConfig.resolutionScale)
            .setEnableSops(prefConfig.enableSops)
            .enableLocalAudioPlayback(prefConfig.playHostAudio)
            .setMaxPacketSize(1392)
            .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
            .setSupportedVideoFormats(supportedVideoFormats)
            .setAudioConfiguration(prefConfig.audioConfiguration)
            .setAudioCodec(
                if (prefConfig.enableAudioPassthrough) prefConfig.audioCodec
                else MoonBridge.AUDIO_CODEC_OPUS
            )
            .setAudioBitrate(prefConfig.audioCodecBitrate)
            .setColorSpace(decoderRenderer?.getPreferredColorSpace() ?: 0)
            .setColorRange(
                if (willStreamHdr && prefConfig.hdrMode == MoonBridge.HDR_MODE_HLG)
                    MoonBridge.COLOR_RANGE_FULL
                else decoderRenderer?.getPreferredColorRange() ?: 0
            )
            .setHdrMode(if (willStreamHdr) prefConfig.hdrMode else MoonBridge.HDR_MODE_SDR)
            .setClientRefreshRateX100(clientRefreshRateX100)
            .setEnableMic(prefConfig.enableMic)
            .setControlOnly(prefConfig.controlOnly)
            .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
            .setUseVdd(pcUseVdd)
            .setCustomScreenMode(prefConfig.screenCombinationMode)
            .setCustomVddScreenMode(prefConfig.vddScreenCombinationMode)
            .build()
    }

    // ========================================================================
    // 串流启动
    // ========================================================================

    /**
     * 当 SurfaceView 的 surface 就绪后调用。
     *
     * - 首次调用：创建音频渲染器并启动 [NvConnection]。
     * - surface 重建后（极速恢复）：仅重新设置渲染目标并恢复渲染。
     */
    fun startStreaming() {
        val holder = cachedSurfaceHolder
        val decoder = decoderRenderer
        val conn = this.conn
        if (holder == null || decoder == null || conn == null) {
            LimeLog.severe("StreamEngine: holder/decoder/conn 为空")
            return
        }

        if (extremeResumeEnabled && connected) {
            LimeLog.info("StreamEngine: 极速恢复，重新设置渲染目标并恢复")
            decoder.setRenderTarget(holder)
            audioRenderer?.resumeProcessing()
            decoder.resumeProcessing()
            extremeResumeEnabled = false
            return
        }

        if (attemptedConnection) {
            LimeLog.info("StreamEngine: 已尝试过连接，跳过")
            return
        }

        attemptedConnection = true

        // 设置渲染目标（需要 SurfaceHolder）
        decoder.setRenderTarget(holder)

        // 创建音频渲染器
        val audioRenderer = SmartAudioRenderer(
            activity,
            prefConfig.enableAudioFx,
            prefConfig.enableSpatializer,
            prefConfig.audioPassthroughBufferBytes
        )
        this.audioRenderer = audioRenderer

        // 启动连接
        conn.start(audioRenderer, decoder, this)

        LimeLog.info("StreamEngine: conn.start() 已调用")
    }

    fun disconnect() {
        try {
            conn?.stop()
        } catch (e: Exception) {
            LimeLog.warning("StreamEngine: disconnect stop 失败 ${e.message}")
        }
        activity.finish()
    }

    fun disconnectAndQuit() {
        try {
            conn?.doStopAndQuit()
        } catch (e: Exception) {
            LimeLog.warning("StreamEngine: disconnectAndQuit 失败 ${e.message}")
        }
        activity.finish()
    }

    fun changeResolution() {
        handler.post { activity.recreate() }
    }

    // ========================================================================
    // 快捷操作 — 状态与切换方法
    // ========================================================================

    var isAudioMuted: Boolean = false
    var isHdrEnabled: Boolean = false

    fun toggleAudioMute() {
        isAudioMuted = !isAudioMuted
        audioRenderer?.setMuted(isAudioMuted)
        displayTransientMessage(if (isAudioMuted) "声音已关闭" else "声音已开启")
    }

    fun toggleMicrophoneButton() {
        prefConfig.enableMic = !prefConfig.enableMic
        displayTransientMessage(if (prefConfig.enableMic) "麦克风已开启" else "麦克风已关闭")
    }

    fun toggleKeyboard() {
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.toggleSoftInput(0, 0)
    }

    fun toggleVirtualController() {
        displayTransientMessage("虚拟手柄切换（待实现）")
    }

    fun togglePerformanceOverlay() {
        when {
            !prefConfig.enablePerfOverlay -> {
                prefConfig.enablePerfOverlay = true
                prefConfig.perfOverlayLocked = false
            }
            !prefConfig.perfOverlayLocked -> {
                prefConfig.perfOverlayLocked = true
            }
            else -> {
                prefConfig.enablePerfOverlay = false
                prefConfig.perfOverlayLocked = false
            }
        }
        displayTransientMessage(
            when {
                prefConfig.enablePerfOverlay && !prefConfig.perfOverlayLocked -> "性能面板（可拖动）"
                prefConfig.enablePerfOverlay -> "性能面板（已锁定）"
                else -> "性能面板已隐藏"
            }
        )
    }

    fun togglePip() {
        prefConfig.enablePip = !prefConfig.enablePip
        displayTransientMessage(if (prefConfig.enablePip) "画中画已开启" else "画中画已关闭")
    }

    fun toggleGyro() {
        prefConfig.gyroToRightStick = !prefConfig.gyroToRightStick
        displayTransientMessage(if (prefConfig.gyroToRightStick) "体感已开启" else "体感已关闭")
    }

    fun toggleAdaptiveBitrate() {
        prefConfig.enableAdaptiveBitrate = !prefConfig.enableAdaptiveBitrate
        displayTransientMessage(if (prefConfig.enableAdaptiveBitrate) "自适应码率已开启" else "自适应码率已关闭")
    }

    fun toggleControlOnly() {
        prefConfig.controlOnly = !prefConfig.controlOnly
        displayTransientMessage(if (prefConfig.controlOnly) "纯控制模式已开启" else "纯控制模式已关闭")
    }

    fun sendKeyboardShortcut(keyCode: Short, modifier: Byte = 0) {
        conn?.let { c ->
            c.sendKeyboardInput(keyCode, com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN, modifier, 0.toByte())
            c.sendKeyboardInput(keyCode, com.limelight.nvstream.input.KeyboardPacket.KEY_UP, modifier, 0.toByte())
        }
    }

    fun sendKeys(keys: ShortArray) {
        val c = conn ?: return
        if (keys.isEmpty()) return

        var modifier: Byte = 0
        for (key in keys) {
            c.sendKeyboardInput(key, com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN, modifier, 0.toByte())
            modifier = (modifier.toInt() or getKeyModifier(key).toInt()).toByte()
        }

        val finalModifier = modifier
        handler.postDelayed({
            var mod = finalModifier
            for (pos in keys.indices.reversed()) {
                val key = keys[pos]
                mod = (mod.toInt() and getKeyModifier(key).toInt().inv()).toByte()
                c.sendKeyboardInput(key, com.limelight.nvstream.input.KeyboardPacket.KEY_UP, mod, 0.toByte())
            }
        }, 50)
    }

    private fun getKeyModifier(key: Short): Byte {
        return when (key.toInt()) {
            com.limelight.binding.input.KeyboardTranslator.VK_LSHIFT -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_SHIFT
            com.limelight.binding.input.KeyboardTranslator.VK_LCONTROL -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_CTRL
            com.limelight.binding.input.KeyboardTranslator.VK_LWIN -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_META
            com.limelight.binding.input.KeyboardTranslator.VK_MENU -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_ALT
            else -> 0
        }
    }

    // ========================================================================
    // SurfaceHolder.Callback
    // ========================================================================

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            LimeLog.info("StreamEngine: surfaceCreated")
            cachedSurfaceHolder = holder
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            LimeLog.info("StreamEngine: surfaceChanged ${width}x${height}")
            cachedSurfaceHolder = holder
            startStreaming()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            LimeLog.info("StreamEngine: surfaceDestroyed")
            if (attemptedConnection && connected && !activity.isFinishing) {
                // 极速恢复：暂停渲染但不中断串流，surface 重建后 resume
                extremeResumeEnabled = true
                audioRenderer?.pauseProcessing()
                decoderRenderer?.pauseProcessing()
            }
            cachedSurfaceHolder = null
        }
    }

    // ========================================================================
    // NvConnectionListener
    // ========================================================================

    override fun stageStarting(stage: String) {
        LimeLog.info("StreamEngine: stageStarting $stage")
        handler.post { onStageUpdate?.invoke(stage, false, false) }
    }

    override fun stageComplete(stage: String) {
        LimeLog.info("StreamEngine: stageComplete $stage")
        handler.post { onStageUpdate?.invoke(stage, true, false) }
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        LimeLog.severe("StreamEngine: stageFailed $stage port=$portFlags err=$errorCode")
        handler.post {
            onStageUpdate?.invoke(stage, false, true)
            Toast.makeText(activity, "连接失败: $stage ($errorCode)", Toast.LENGTH_LONG).show()
        }
    }

    override fun connectionStarted() {
        LimeLog.info("StreamEngine: connectionStarted")
        connected = true
    }

    override fun connectionTerminated(errorCode: Int) {
        LimeLog.info("StreamEngine: connectionTerminated code=$errorCode")
        connected = false
        handler.post {
            if (!activity.isFinishing) {
                onStreamEnded?.invoke()
                if (errorCode != 0) {
                    Toast.makeText(activity, "串流已断开 (code=$errorCode)", Toast.LENGTH_SHORT).show()
                }
                activity.finish()
            }
        }
    }

    override fun connectionStatusUpdate(connectionStatus: Int) {}

    override fun displayMessage(message: String) {
        LimeLog.info("StreamEngine: displayMessage $message")
        handler.post { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
    }

    override fun displayTransientMessage(message: String) {
        handler.post { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {}
    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {}
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        isHdrEnabled = enabled
    }
    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {}
    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {}
    override fun onResolutionChanged(width: Int, height: Int) {}

    // ========================================================================
    // 生命周期
    // ========================================================================

    fun onResume() {}

    fun onPause() {
        if (connected) {
            try {
                conn?.stop()
            } catch (e: Exception) {
                LimeLog.warning("StreamEngine: onPause stop error ${e.message}")
            }
            connected = false
            attemptedConnection = false
        }
    }

    fun release() {
        LimeLog.info("StreamEngine: release")
        if (connected) conn?.stop()
        audioRenderer = null
        decoderRenderer = null
        conn = null
    }
}
