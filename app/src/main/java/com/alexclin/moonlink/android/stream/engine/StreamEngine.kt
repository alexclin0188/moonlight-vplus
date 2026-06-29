package com.alexclin.moonlink.android.stream.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.limelight.Game
import com.limelight.LimeLog
import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.SmartAudioRenderer
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.GameInputDevice
import com.limelight.binding.input.capture.InputCaptureManager
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.binding.input.evdev.EvdevListener
import com.limelight.binding.input.touch.NativeTouchContext
import com.limelight.nvstream.input.ClipboardSyncManager
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.ui.GameGestures
import com.limelight.ui.StreamView
import android.net.ConnectivityManager
import android.hardware.input.InputManager
import android.view.InputDevice
import android.net.wifi.WifiManager
import com.limelight.binding.video.CrashListener
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.binding.video.PerformanceInfo
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.AdaptiveBitrateService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.GlPreferences
import com.limelight.utils.AppSettingsManager
import com.limelight.utils.NetHelper
import com.alexclin.moonlink.android.device.streamsettings.HostSettings
import com.alexclin.moonlink.android.device.streamsettings.HostSettingsManager
import android.net.TrafficStats
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 封装 Moonlight 串流核心引擎：NvConnection 创建、解码器、音频渲染。
 *
 * 引用 [com.limelight] 包中的底层类，不修改任何旧代码。
 */
class StreamEngine(val activity: Activity) : NvConnectionListener, GameGestures, EvdevListener {

    /** 从 SharedPreferences 读取的全部串流配置 */
    lateinit var prefConfig: PreferenceConfiguration

    /** 当前串流应用对象 */
    lateinit var app: NvApp

    /** 串流连接 */
    var conn: NvConnection? = null
        private set

    /** 视频解码渲染器 */
    private var decoderRenderer: MediaCodecDecoderRenderer? = null

    /** 触控处理器 */
    var touchHandler: StreamTouchHandler? = null
        private set

    /** 手柄控制器处理器 */
    var controllerHandler: ControllerHandler? = null
        private set

    /** 输入捕获提供者（光标隐藏/鼠标捕获） */
    var inputCaptureProvider: InputCaptureProvider? = null
        private set

    /** 剪贴板同步管理器 */
    private var clipboardSyncManager: ClipboardSyncManager? = null

    /** 光标服务管理器（网络光标 + 本地渲染器），Compose 模式下暂为空 */
    @Suppress("unused")
    private var cursorServiceManager: Any? = null

    /** PiP 模式标志 */
    var isInPipMode = false
        private set

    /** 缓存 SurfaceView（用于触控处理器初始化） */
    private var cachedSurfaceView: SurfaceView? = null

    // ── 设备在线状态管理 ──

    /** 设备在线状态管理器：维护所有设备的 Compose 可观察列表，后台扫描结果自动更新到此 */
    val deviceManager = DeviceStateManager()

    // ── 流时长统计 ──
    private var accumulatedStreamTime: Long = 0
    private var streamStartTime: Long = 0
    private var lastActiveTime: Long = 0
    var isStreamingActive = false

    /** 音频渲染器 */
    private var audioRenderer: SmartAudioRenderer? = null

    /** 是否已连接 */
    var connected = false
        private set

    /** 是否应恢复串流会话（从后台返回时重连） */
    var shouldResumeSession = false

    /** 是否启用极速恢复（surface 重建时不中断串流） */
    var isExtremeResumeEnabled = false

    /** 是否已尝试过连接（防止 surfaceChanged 重复启动） */
    private var attemptedConnection = false

    /** 缓存的 SurfaceHolder（用于 setRenderTarget） */
    private var cachedSurfaceHolder: SurfaceHolder? = null

    /** SurfaceView（由外部设置） */
    var surfaceView: SurfaceView? = null

    /** 串流结束回调 */
    var onStreamEnded: (() -> Unit)? = null

    // ── WiFi Lock ──
    private var highPerfWifiLock: WifiManager.WifiLock? = null
    private var lowLatencyWifiLock: WifiManager.WifiLock? = null

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
    var pcUseVdd: Boolean = false
    /** VDD 虚拟显示器宽度（0=使用客户端原生分辨率） */
    private var vddWidth: Int = 0
    /** VDD 虚拟显示器高度 */
    private var vddHeight: Int = 0
    /** VDD 虚拟显示器帧率 */
    private var vddFps: Int = 90

    /** 智能码率服务引用（由外部 Game/StreamActivity 注入，用于手动调码率时同步状态） */
    var adaptiveBitrateService: com.limelight.nvstream.http.AdaptiveBitrateService? = null

    /** 正在切换分辨率，阻止 onPause 断连（远端流需保持，供新 Activity 复用） */
    @Volatile
    var isChangingResolution = false

    /** 当前串流使用的显示器 display_name（直接影响 launch?display_name= 参数） */
    @Volatile
    var currentDisplayName: String? = null
    /** 当前串流使用的显示器 device_id */
    @Volatile
    var currentDeviceId: String? = null

    /** 显示设置面板中有需要重启串流才能生效的选项被修改 */
    @Volatile
    var displaySettingsRestartPending: Boolean = false

    /** 用户是否在面板中主动设置过分辨率/缩放，为 false 时不发送 mode/resolutionScale 到 Sunshine */
    @Volatile
    var applyDisplaySettings: Boolean = false

    /** Evdev 修饰键追踪状态（CR-2）：当前按住的所有修饰键位掩码 */
    private var currentModifiers: Byte = 0

    /** 当前显示的对话框引用，release 时需关闭防止 WindowLeaked */
    private var activeDialog: android.app.AlertDialog? = null
    private var pcUuid: String? = null
    private var pcName: String? = null
    private var appId: Int = NvApp.DESKTOP_APP_ID

    /** 解码器连续崩溃计数（从 tombstone 缓存读取） */
    private var consecutiveDecoderCrashCount: Int = 0

    /** OpenGL 渲染器类型 */
    private var glRenderer: String = "opengl"

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        /** 按键发送：按下与释放之间的延迟 (ms) */
        private const val KEY_UP_DELAY = 25L

        /** 睡眠快捷键两段式延迟 (ms) */
        private const val SLEEP_DELAY = 200L

        /** 当前方案的 SharedPreference key，与旧 PageConfigController 互通 */
        const val PREF_CURRENT_CONFIG_ID = "current_config_id"

        // ── PiP 跨 Activity 状态跟踪 ──

        /** 当前 PiP 串流对应的主机 UUID，用于检测是否同一设备 */
        @Volatile
        var currentPipUuid: String? = null
        /** 当前 PiP 串流对应的连接 Activity，用于从首页关闭 PiP */
        @Volatile
        var currentPipActivity: android.app.Activity? = null
    }

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

            // 恢复客户端声音开关状态
            isAudioMuted = prefConfig.muteClientAudio

            // 同步运行时 Compose 状态
            perfOverlayEnabled = prefConfig.enablePerfOverlay
            fabOpacity = prefConfig.fabOpacity

            // 默认触摸模式：增强式多点触控
            prefConfig.enableEnhancedTouch = true
            prefConfig.touchscreenTrackpad = false
            prefConfig.enableNativeMousePointer = false

            // 默认关闭按键映射（不持久化上次状态）
            prefConfig.keyMappingEnabled = false

            // 同步拉伸视频状态
            stretchVideo = prefConfig.stretchVideo

            // 初始化 NativeTouchContext 静态参数
            syncNativeTouchContext()

            // 2a. 应用主机级串流配置（覆盖全局默认值）
            // 注意：Intent 中的"最近配置"在第 2b 步执行，但第 2c 步会重新应用主机级配置以保持优先级。
            val pcUuidFromIntent = intent.getStringExtra(Game.EXTRA_PC_UUID)
            var hostSettingsForReapply: HostSettings? = null
            if (pcUuidFromIntent != null) {
                try {
                    val hostSettingsManager = HostSettingsManager(activity)
                    hostSettingsManager.getSettings(pcUuidFromIntent)?.let { hostSettings ->
                        hostSettingsForReapply = hostSettings
                        applyHostSettingsToPrefConfig(prefConfig, hostSettings)
                    }
                    // 重新同步运行时状态（第一次同步在步骤 1 基于全局 SP 执行）
                    isAudioMuted = prefConfig.muteClientAudio
                    perfOverlayEnabled = prefConfig.enablePerfOverlay
                    fabOpacity = prefConfig.fabOpacity
                    stretchVideo = prefConfig.stretchVideo
                } catch (_: Exception) {
                    // 忽略 - 降级到全局配置
                }
            }

            // 2b. 应用"以最近一次配置启动"（若 Intent 中包含）
            AppSettingsManager(activity).applyLastSettingsFromIntent(intent, prefConfig)

            // 2c. 如果存在主机级配置，重新应用使其优先级高于"最近配置"
            //     这样用户在主机的"串流设置"中明确设置的值不会被 per-app 历史记录覆盖。
            if (hostSettingsForReapply != null) {
                applyHostSettingsToPrefConfig(prefConfig, hostSettingsForReapply)
                // 重新同步运行时状态
                isAudioMuted = prefConfig.muteClientAudio
                perfOverlayEnabled = prefConfig.enablePerfOverlay
                fabOpacity = prefConfig.fabOpacity
                stretchVideo = prefConfig.stretchVideo
            }

            // 2d. 用最终值重新同步 NativeTouchContext 静态参数
            syncNativeTouchContext()

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

            // 如果没有指定显示器、且未使用虚拟显示器：不指定目标显示器，
            // 让 Sunshine 自行决定（即串流到当前活跃桌面），与旧版 PcView 行为一致。
            if (currentDisplayName == null && displayName == null && !pcUseVdd) {
                applyDisplaySettings = false
            } else {
                // 有显示器配置或使用了虚拟显示器：应用用户的分辨率/缩放等显示设置到 Sunshine
                applyDisplaySettings = true
            }

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

            StreamLogger.log(activity, "INIT", "初始化完成 host=$host app=${app.appName}")

            // 7. 创建 NvConnection（延迟到 surface 可用时 start）
            createConnection()

            // 8. 网络能力初始化
            acquireWifiLocks()
            checkMeteredNetwork()

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
                override fun onPerfUpdateV(performanceInfo: PerformanceInfo) {
                    // 带宽计算
                    val currentRxBytes = TrafficStats.getTotalRxBytes()
                    val timeMillis = System.currentTimeMillis()
                    val interval = timeMillis - previousTimeMillis
                    if (interval > 5000) {
                        performanceInfo.bandWidth = lastValidBandwidth
                    } else {
                        val calc = NetHelper.calculateBandwidth(currentRxBytes, previousRxBytes, interval)
                        if (calc != "0 K/s") {
                            performanceInfo.bandWidth = calc
                            lastValidBandwidth = calc
                        } else {
                            performanceInfo.bandWidth = lastValidBandwidth
                        }
                    }
                    bandwidthInfo = performanceInfo.bandWidth ?: "N/A"
                    previousRxBytes = currentRxBytes
                    previousTimeMillis = timeMillis

                    latestPerfInfo = performanceInfo
                    onPerfInfoUpdate?.invoke(performanceInfo)
                }
                override fun onPerfUpdateWG(performanceInfo: PerformanceInfo) {
                    latestPerfInfo = performanceInfo
                }
                override fun isPerfOverlayVisible(): Boolean = prefConfig.enablePerfOverlay
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
                @Suppress("DEPRECATION")
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
            displayName = currentDisplayName ?: displayName,
            forceResumeCurrentSession
        )

        // 7) 初始化手柄控制器
        initControllerHandler()

        LimeLog.info("StreamEngine: NvConnection 已创建")
        StreamLogger.logParams(activity, "CONFIG", mapOf(
            "width" to config.width, "height" to config.height,
            "launchRefreshRate" to config.launchRefreshRate,
            "refreshRate" to config.refreshRate,
            "bitrate" to config.bitrate,
            "resolutionScale" to config.resolutionScale,
            "sops" to config.sops,
            "appId" to app.appId,
            "attachedGamepadMask" to config.attachedGamepadMask,
            "remoteConfig" to config.remote,
            "audioCodec" to config.audioCodec,
            "hdrMode" to config.hdrMode,
            "useVdd" to pcUseVdd,
            "screenCombinationMode" to prefConfig.screenCombinationMode,
            "vddScreenCombinationMode" to prefConfig.vddScreenCombinationMode,
            "displayName" to (currentDisplayName ?: displayName),
            "forceResume" to forceResumeCurrentSession,
        ))
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
        @Suppress("DEPRECATION")
        val displayRefreshRate = activity.windowManager.defaultDisplay.refreshRate
        val roundedRefreshRate = displayRefreshRate.roundToInt()

        // 实际使用的帧率 — 始终使用 prefConfig.fps
        var chosenFrameRate = prefConfig.fps
        val effectiveLaunchFps = prefConfig.fps

        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            if (chosenFrameRate >= roundedRefreshRate) {
                if (chosenFrameRate > roundedRefreshRate + 3) {
                    // 使用 drop 模式 → 不改帧率
                } else if (roundedRefreshRate <= 49) {
                    // 刷新率太低
                } else {
                    chosenFrameRate = roundedRefreshRate - 1
                }
            }
        }
        val clientRefreshRateX100 = (displayRefreshRate * 100).roundToInt()

        // VDD 模式下分辨率/帧率覆盖
        val effectiveWidth: Int
        val effectiveHeight: Int
        val effectiveFps: Int
        val effectiveLaunchFpsVal: Int
        if (pcUseVdd) {
            effectiveFps = vddFps
            effectiveLaunchFpsVal = vddFps
            if (vddWidth > 0 && vddHeight > 0) {
                effectiveWidth = vddWidth
                effectiveHeight = vddHeight
            } else {
                // 使用客户端设备原生分辨率
                val size = Point()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = activity.windowManager.currentWindowMetrics.bounds
                    size.set(bounds.width(), bounds.height())
                } else {
                    @Suppress("DEPRECATION")
                    val display = activity.windowManager.defaultDisplay
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        display.getRealSize(size)
                    } else {
                        display.getSize(size)
                    }
                }
                effectiveWidth = maxOf(size.x, size.y)
                effectiveHeight = minOf(size.x, size.y)
            }
        } else {
            effectiveWidth = prefConfig.width
            effectiveHeight = prefConfig.height
            effectiveFps = chosenFrameRate
            effectiveLaunchFpsVal = effectiveLaunchFps
        }

        // 保存实际串流分辨率，供 UI 层（StreamView 宽高比）使用
        actualStreamWidth = effectiveWidth
        actualStreamHeight = effectiveHeight

        return StreamConfiguration.Builder()
            .setResolution(effectiveWidth, effectiveHeight)
            .setLaunchRefreshRate(effectiveLaunchFpsVal)
            .setRefreshRate(effectiveFps)
            .setApp(app)
            .setBitrate(prefConfig.bitrate)
            .setResolutionScale(if (applyDisplaySettings) prefConfig.resolutionScale else 100)
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
            .setAttachedGamepadMask(computeGamepadMask())
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

        if (isExtremeResumeEnabled && connected) {
            if (isInPipMode) {
                // PiP 模式下跳过解码器恢复 — 解码器保持存活无需恢复，surface 正在重建，
                // 此时 resumeProcessing() 可能因 surface 未就绪而触发 NPE。
                // 直接清除标志位，退出 PiP 后恢复音频即可。
                LimeLog.info("StreamEngine: PiP 中跳过极速恢复")
                isExtremeResumeEnabled = false
                return
            }
            LimeLog.info("StreamEngine: 极速恢复，重新设置渲染目标并恢复")
            decoder.setRenderTarget(holder)
            audioRenderer?.resumeProcessing()
            decoder.resumeProcessing()
            isExtremeResumeEnabled = false
            return
        }

        if (attemptedConnection) {
            LimeLog.info("StreamEngine: 已尝试过连接，跳过")
            return
        }

        attemptedConnection = true

        StreamLogger.log(activity, "STREAM", "开始启动串流 width=${prefConfig.width} height=${prefConfig.height} fps=${prefConfig.fps}")

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

        // 按持久化的声音开关状态设置 mute
        audioRenderer.setMuted(isAudioMuted)

        // 启动连接
        conn.start(audioRenderer, decoder, this)

        LimeLog.info("StreamEngine: conn.start() 已调用")

        // 初始化外设监听
        initPeripheralMonitoring()

        // 初始化触控处理器（需 conn 已就绪 + SurfaceView 已设置）
        val sv = cachedSurfaceView
        if (sv != null) {
            initTouchHandler(sv)
        }
    }

    /** 设置 SurfaceView 并延迟初始化触控处理器（若无连接则等 startStreaming 时初始化） */
    fun attachSurfaceView(sv: SurfaceView) {
        cachedSurfaceView = sv
        val c = conn
        if (c != null) {
            initTouchHandler(sv)
            // InputCaptureManager 内部通过 activity.findViewById(R.id.surfaceView) 查找视图，
            // 此时 SV 虽已创建但尚未附加到窗口，defer 到下一帧以确保 findViewById 能找到
            handler.post { initInputCapture() }
        }
    }

    private fun initTouchHandler(view: View) {
        val c = conn ?: return
        val handler = StreamTouchHandler(
            conn = c,
            prefConfig = prefConfig,
            targetView = view,
            onToggleKeyboard = { this.toggleKeyboard() },
            onCursorVisibilityChanged = { visible ->
                if (connected) setInputGrabState(!visible)
            },
        )
        handler.onLocalCursorMoved = { x, y ->
            // 将像素坐标转换为比例坐标（0.0~1.0），供 Compose 光标覆盖层使用
            val w = view.width.coerceAtLeast(1)
            val h = view.height.coerceAtLeast(1)
            localCursorAbsX = (x / w).coerceIn(0f, 1f)
            localCursorAbsY = (y / h).coerceIn(0f, 1f)
        }
        // 根据 prefConfig 更新光标可见性
        showLocalCursor = prefConfig.enableLocalCursorRendering && prefConfig.touchscreenTrackpad
        handler.initTouchContexts()
        touchHandler = handler
        LimeLog.info("StreamEngine: 触控处理器已初始化")
    }

    /** 运行时切换触控模式（写入 prefConfig + 通知触控处理器） */
    fun applyTouchMode(mode: Int) {
        // mode: 0=ENHANCED, 1=CLASSIC, 2=TRACKPAD, 3=NATIVE_MOUSE
        prefConfig.enableEnhancedTouch = (mode == 0)
        prefConfig.enableNativeMousePointer = (mode == 3)
        prefConfig.touchscreenTrackpad = (mode == 2)

        // 同步 NativeTouchContext 确保与最新 prefConfig 一致
        syncNativeTouchContext()

        // 同步本地光标可见性（触控板模式下显示本地光标）
        showLocalCursor = prefConfig.enableLocalCursorRendering && prefConfig.touchscreenTrackpad

        touchHandler?.let { handler ->
            handler.setTouchMode(mode == 2)       // 相对坐标（触控板）
            handler.setEnhancedTouch(mode == 0)   // 增强触控
            handler.cursorVisible = (mode == 3)   // 本地鼠标指针
        }
    }

    /** 初始化手柄控制器处理器 — 在 createConnection() 后调用 */
    private fun initControllerHandler() {
        val c = conn ?: return
        controllerHandler = ControllerHandler(activity, c, this, prefConfig)
        LimeLog.info("StreamEngine: ControllerHandler 已初始化")
    }

    /** 初始化光标服务管理器 — 在连接后调用 */
    private fun initCursorService(hostAddress: String?) {
        // CursorServiceManager 需要 StreamView/CursorView（旧 View 架构），
        // 新版 Compose 架构使用原生 PointerIcon API 替代。此处保留结构占位。
        LimeLog.info("StreamEngine: CursorServiceManager 暂不初始化（Compose 下无 StreamView）")
    }

    /** 初始化剪贴板同步管理器 — 在连接建立后（onStageStarting）调用 */
    private fun initClipboardSync() {
        val wantText = prefConfig.enableClipboardSyncText
        val wantImage = prefConfig.enableClipboardSyncImage
        if (!wantText && !wantImage) return
        val mgr = ClipboardSyncManager(
            context = activity.applicationContext,
            syncText = wantText,
            syncImage = wantImage,
            fileProviderAuthority = "${activity.packageName}.clipboard_fileprovider",
            nvHttpProvider = { conn?.createNvHttp() },
        )
        runCatching { mgr.start() }
            .onFailure { LimeLog.warning("StreamEngine: 剪贴板同步启动失败 ${it.message}") }
            .onSuccess { clipboardSyncManager = mgr }
    }

    /** 初始化输入捕获 — SurfaceView 就绪后（已设 R.id.surfaceView）调用 */
    private fun initInputCapture() {
        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(
            activity, this
        )
        LimeLog.info("StreamEngine: InputCaptureProvider 已初始化")
    }

    /** 设置输入捕获光标抓取状态 */
    fun setInputGrabState(isGrabbing: Boolean) {
        val cap = inputCaptureProvider ?: return
        if (isGrabbing) cap.enableCapture() else cap.disableCapture()
    }

    // ── GameGestures 实现 ──

    override fun toggleKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.toggleSoftInput(0, 0)
    }

    override fun showGameMenu(device: GameInputDevice?) {
        LimeLog.info("StreamEngine: showGameMenu 暂空实现")
    }

    fun disconnect() {
        try {
            conn?.stop()
        } catch (e: Exception) {
            LimeLog.warning("StreamEngine: disconnect stop 失败 ${e.message}")
        }
        stopPeripheralMonitoring()
        activity.finish()
    }

    /**
     * 从后台恢复时的重连准备。对标旧版 Game.prepareConnection()。
     * 销毁旧解码器音频渲染器 → 重新创建 NvConnection → 重置连接状态。
     * Surface 就绪后 surfaceChanged 会重新触发 startStreaming()。
     */
    fun prepareConnection() {
        LimeLog.info("StreamEngine: prepareConnection 开始")
        StreamLogger.log(activity, "RECONNECT", "重连准备开始")

        // 0. 先终止旧连接（防止 display_name 变化时新旧连接冲突）
        if (connected) {
            try { conn?.stop() } catch (_: Exception) {}
            connected = false
        }

        // 1. 销毁旧解码器和音频渲染器
        try { decoderRenderer?.prepareForStop() } catch (_: Exception) {}
        decoderRenderer = null
        audioRenderer = null

        // 2. 重新创建 NvConnection
        createConnection()

        // 3. 触控重新初始化
        cachedSurfaceView?.let { initTouchHandler(it) }

        // 4. 重置连接状态，允许 surfaceChanged 重新触发 startStreaming()
        attemptedConnection = false
        connected = false
        isChangingResolution = false

        StreamLogger.log(activity, "RECONNECT", "重连准备完成")
        LimeLog.info("StreamEngine: 重连准备完成")
    }

    fun disconnectAndQuit() {
        try {
            conn?.doStopAndQuit()
        } catch (e: Exception) {
            LimeLog.warning("StreamEngine: disconnectAndQuit 失败 ${e.message}")
        }
        activity.finish()
    }

    // ── WiFi Lock 管理 ──

    /** 获取高功耗 WiFi Lock 和低延迟 WiFi Lock，防止串流中断 */
    fun acquireWifiLocks() {
        val wifiMgr = activity.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            highPerfWifiLock = wifiMgr.createWifiLock(
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MoonLink High Perf")
            highPerfWifiLock?.setReferenceCounted(false)
            highPerfWifiLock?.acquire()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyWifiLock = wifiMgr.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MoonLink Low Latency")
                lowLatencyWifiLock?.setReferenceCounted(false)
                lowLatencyWifiLock?.acquire()
            }
        } catch (_: SecurityException) {
            LimeLog.warning("StreamEngine: 获取 WiFi Lock 失败（无权限）")
        }
    }

    /** 释放 WiFi Lock */
    fun releaseWifiLocks() {
        try {
            lowLatencyWifiLock?.release()
            lowLatencyWifiLock = null
            highPerfWifiLock?.release()
            highPerfWifiLock = null
        } catch (_: Exception) {}
    }

    /** 检查是否为计费网络，提示用户 */
    fun checkMeteredNetwork() {
        try {
            val connMgr = activity.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            if (connMgr.isActiveNetworkMetered) {
                displayTransientMessage("当前为计费网络，请注意流量消耗")
            }
        } catch (_: Exception) {}
    }

    /** 计算已连接的控制器掩码（与旧 Game.kt 逻辑一致） */
    private fun computeGamepadMask(): Int {
        var mask = ControllerHandler.getAttachedControllerMask(activity).toInt()
        if (!prefConfig.multiController) {
            mask = 1
        }
        if (prefConfig.onscreenController) {
            mask = mask or 1
        }
        return mask
    }

    fun changeResolution() {
        applyDisplaySettings = true
        isChangingResolution = true
        // 先终止旧串流连接，再重建 Activity，避免 display_name 变化时新旧连接冲突
        try { conn?.stop() } catch (_: Exception) {}
        connected = false
        handler.post { activity.recreate() }
    }

    /**
     * 自动从远端主机查询显示器列表，选择合适的显示器设为当前显示器。
     *
     * 规则：
     * - 如果主机只有一台显示器 → 自动选中该显示器
     * - 如果主机有多台显示器 → 自动选中 is_primary=true 的主显示器
     * - 查询失败或无显示器 → currentDisplayName 保持 null
     *
     * 注意：此方法在 createConnection() 之前调用，因此不能依赖 conn，
     * 而是使用 Intent 参数直接创建 NvHTTP 实例。
     */
    private fun autoSelectDisplayFromHost() {
        try {
            val http = NvHTTP(
                ComputerDetails.AddressTuple(host, port),
                httpsPort,
                uniqueId,
                "",
                serverCert,
                PlatformBinding.getCryptoProvider(activity),
            )
            val displays = http.getDisplays()
            if (displays.isEmpty()) return

            val target = when {
                displays.size == 1 -> displays[0]
                else -> displays.find { it.isPrimary } ?: displays[0]
            }
            currentDisplayName = target.name
            currentDeviceId = target.guid
            LimeLog.info("StreamEngine: 自动选择显示器: ${target.name} (isPrimary=${target.isPrimary})")
        } catch (e: Exception) {
            LimeLog.warning("StreamEngine: 自动选择显示器失败: ${e.message}")
        }
    }

    /** 切换串流目标显示器，保存选择并重启串流。*/
    fun changeDisplay(displayName: String, deviceId: String) {
        currentDisplayName = displayName
        currentDeviceId = deviceId
        // 切换显示器需要重启串流
        changeResolution()
    }

    // ========================================================================
    // 快捷操作 — 状态与切换方法
    // ========================================================================

    var isAudioMuted: Boolean by mutableStateOf(false)
    var isHdrEnabled: Boolean by mutableStateOf(false)

    fun toggleAudioMute() {
        isAudioMuted = !isAudioMuted
        audioRenderer?.setMuted(isAudioMuted)
        // 持久化客户端声音开关状态
        prefConfig.muteClientAudio = isAudioMuted
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(muteClientAudio = isAudioMuted) }
        displayTransientMessage(if (isAudioMuted) "声音已关闭" else "声音已开启")
    }

    fun toggleMicrophoneButton() {
        prefConfig.enableMic = !prefConfig.enableMic
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(enableMic = prefConfig.enableMic) }
        displayTransientMessage(if (prefConfig.enableMic) "麦克风已开启" else "麦克风已关闭")
    }

    /** 设置暂停串流支持开关，同步到全局偏好和主机设置 */
    fun setShowPauseStream(enabled: Boolean) {
        prefConfig.showPauseStream = enabled
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(showPauseStream = enabled) }
    }

    fun toggleVirtualController() {
        displayTransientMessage("虚拟手柄切换（待实现）")
    }

    // ── 新 Compose 按键映射覆盖层 ──

    /** 当前按键映射覆盖层的元素列表。由 [reloadOverlay] 更新。 */
    val currentOverlayElements: androidx.compose.runtime.MutableState<List<com.alexclin.moonlink.android.stream.ui.editor.EditorElement>> =
        androidx.compose.runtime.mutableStateOf(emptyList())

    // ── 按键映射配置面板的设置值（运行时状态） ──

    /** 全局不透明度（0-100），由 [KeyMappingConfigPanel] 更新，[KeyMappingOverlay] 消费。 */
    var configGlobalOpacity: Int by mutableStateOf(100)

    /** 触控开关，由 [KeyMappingConfigPanel] 更新，[KeyMappingOverlay] 消费。 */
    var configTouchEnabled: Boolean by mutableStateOf(true)

    /** 增强触控开关 */
    var configEnhancedTouch: Boolean by mutableStateOf(false)

    /** 游戏震动 */
    var configGameVibrator: Boolean by mutableStateOf(false)

    /** 按键震动 */
    var configButtonVibrator: Boolean by mutableStateOf(false)

    /** 鼠标滚轮速度 */
    var configWheelSpeed: Int by mutableStateOf(20)

    /** 触控灵敏度 */
    var configTouchSense: Int by mutableStateOf(100)

    /** 本地光标显示状态（触控板模式下由 enableLocalCursorRendering 控制） */
    var showLocalCursor: Boolean by mutableStateOf(false)

    /** 本地光标 X 坐标（相对于 StreamView 宽度的比例，0.0~1.0） */
    var localCursorAbsX: Float by mutableFloatStateOf(0.5f)

    /** 本地光标 Y 坐标（相对于 StreamView 高度的比例，0.0~1.0） */
    var localCursorAbsY: Float by mutableFloatStateOf(0.5f)

    /** 拉伸视频：纯客户端渲染，即时生效无需重启串流 */
    var stretchVideo: Boolean by mutableStateOf(false)

    /** 实际串流宽度（可能 ≠ prefConfig.width，如 VDD 设备原生分辨率时） */
    var actualStreamWidth: Int by mutableStateOf(0)

    /** 实际串流高度 */
    var actualStreamHeight: Int by mutableStateOf(0)

    /** 是否在全屏页面（编辑器/方案选择器）中，用于 StreamActivity 隐藏干扰 UI */
    var isFullScreenPageActive: Boolean by mutableStateOf(false)

    // ── 外设管理 ──

    data class PeripheralDevice(
        val deviceId: Int,
        val name: String,
        val type: PeripheralType,
        var isEnabled: Boolean = true,
    )

    enum class PeripheralType { MOUSE, KEYBOARD, GAMEPAD }

    /** 当前已连接的外设列表 */
    var peripheralDevices: MutableList<PeripheralDevice> by mutableStateOf(mutableListOf())

    /** 当前生效的鼠标设备 ID（多个鼠标时用户可切换） */
    var activeMouseId: Int? by mutableStateOf(null)

    /** 当前生效的键盘设备 ID（多个键盘时用户可切换） */
    var activeKeyboardId: Int? by mutableStateOf(null)

    /** 扫描并更新外设列表 */
    fun scanPeripherals() {
        val newList = mutableListOf<PeripheralDevice>()
        var newMouseId = activeMouseId
        var newKeyboardId = activeKeyboardId
        val seenDescriptors = mutableSetOf<String>()

        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            // 仅识别外部设备（USB/蓝牙等外接，排除内置）
            if (!device.isExternal) continue
            val name = device.name ?: "未知设备"
            val descriptor = try { device.descriptor } catch (_: Exception) { null }
            // 使用 descriptor 去重：同一物理设备可能被识别为多个逻辑设备
            if (descriptor != null && !seenDescriptors.add(descriptor)) continue
            when {
                // 鼠标：外部 + 鼠标输入源
                device.supportsSource(InputDevice.SOURCE_MOUSE) ||
                device.supportsSource(InputDevice.SOURCE_MOUSE_RELATIVE) -> {
                    newList.add(PeripheralDevice(id, name, PeripheralType.MOUSE))
                    if (activeMouseId == null) newMouseId = id
                }
                // 键盘：外部 + 全字母键盘（优先级高于手柄，避免键盘被误认为手柄）
                device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> {
                    newList.add(PeripheralDevice(id, name, PeripheralType.KEYBOARD))
                    if (activeKeyboardId == null) newKeyboardId = id
                }
                // 手柄：外部 + 摇杆/手柄输入源（排除名称含 Keyboard 的设备）
                device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
                device.supportsSource(InputDevice.SOURCE_GAMEPAD) -> {
                    if (!name.contains("Keyboard", ignoreCase = true)) {
                        val existing = peripheralDevices.find { it.deviceId == id }
                        newList.add(PeripheralDevice(id, name, PeripheralType.GAMEPAD, existing?.isEnabled ?: true))
                    }
                }
            }
        }
        peripheralDevices = newList
        activeMouseId = newMouseId
        activeKeyboardId = newKeyboardId
    }

    /** 注册外设插拔监听 */
    private var peripheralInputManager: InputManager? = null
    private val peripheralDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) {
            scanPeripherals()
            // 仅在被过滤条件接受进入列表时才提示
            val accepted = peripheralDevices.any { it.deviceId == id }
            if (accepted) {
                val device = InputDevice.getDevice(id)
                val deviceName = device?.name ?: "外设"
                activity.runOnUiThread {
                    Toast.makeText(activity, "$deviceName 已连接", Toast.LENGTH_SHORT).show()
                }
            }
        }
        override fun onInputDeviceRemoved(id: Int) {
            // 先记录是否在列表内，再扫描
            val wasInList = peripheralDevices.find { it.deviceId == id }
            val deviceName = wasInList?.name ?: "外设"
            scanPeripherals()
            if (wasInList != null) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "$deviceName 已断开", Toast.LENGTH_SHORT).show()
                }
            }
        }
        override fun onInputDeviceChanged(id: Int) {
            scanPeripherals()
        }
    }

    /** 初始化外设监听（在串流启动时调用） */
    fun initPeripheralMonitoring() {
        peripheralInputManager = activity.getSystemService(android.content.Context.INPUT_SERVICE) as InputManager
        peripheralInputManager?.registerInputDeviceListener(peripheralDeviceListener, null)
        scanPeripherals()
    }

    /** 停止外设监听 */
    fun stopPeripheralMonitoring() {
        peripheralInputManager?.unregisterInputDeviceListener(peripheralDeviceListener)
        peripheralInputManager = null
    }

    // ── 运行时 Compose 状态（由 UI 设置，引擎消费） ──

    /** 性能监控图层开关 — 由 MoreDetail 设置，PerformanceOverlay 消费 */
    var perfOverlayEnabled: Boolean by mutableStateOf(false)

    /** 悬浮按钮不透明度 (10-100) — 由 MoreDetail 设置，FloatingActionButton 消费 */
    var fabOpacity: Int by mutableStateOf(50)

    /**
     * 从 DB 读取当前方案的配置面板设置并同步到运行时状态。
     * 在打开配置面板时或切换方案后调用。
     */
    fun loadConfigFromDb() {
        try {
            val db = com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper(activity)
            val configId = currentSchemeConfigId
            configTouchEnabled = java.lang.Boolean.parseBoolean(
                (db.queryConfigAttribute(configId, com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_BOOLEAN_TOUCH_ENABLE, "true") as? String) ?: "true"
            )
            configGameVibrator = java.lang.Boolean.parseBoolean(
                (db.queryConfigAttribute(configId, com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_BOOLEAN_GAME_VIBRATOR, "false") as? String) ?: "false"
            )
            configButtonVibrator = java.lang.Boolean.parseBoolean(
                (db.queryConfigAttribute(configId, com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_BOOLEAN_BUTTON_VIBRATOR, "false") as? String) ?: "false"
            )
            configWheelSpeed = ((db.queryConfigAttribute(configId, "mouse_wheel_speed", 20L) as? Long) ?: 20L).toInt()
            configEnhancedTouch = java.lang.Boolean.parseBoolean(
                (db.queryConfigAttribute(configId, com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, "false") as? String) ?: "false"
            )
            configGlobalOpacity = ((db.queryConfigAttribute(configId, com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_INT_GLOBAL_OPACITY, 100L) as? Long) ?: 100L).toInt()
        } catch (_: Exception) {
            // 保持默认值
        }
    }

    /** 从 DB 重新加载当前方案的覆盖层元素（仅读取，不做换算）。 */
    fun reloadOverlay() {
        if (!keyMappingState) {
            currentOverlayElements.value = emptyList()
            return
        }
        try {
            val db = com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper(activity)
            currentOverlayElements.value = com.alexclin.moonlink.android.stream.ui.overlay.DbElementLoader.loadElements(db, currentSchemeConfigId, activity)
            // 应用全局颜色（覆盖元素的边框色和文字色）
            val borderColor = db.queryConfigAttribute(currentSchemeConfigId,
                com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_INT_GLOBAL_BORDER_COLOR, null) as? Long
            val textColor = db.queryConfigAttribute(currentSchemeConfigId,
                com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_INT_GLOBAL_TEXT_COLOR, null) as? Long
            if (borderColor != null || textColor != null) {
                val bc = borderColor?.toInt()
                val tc = textColor?.toInt()
                currentOverlayElements.value = currentOverlayElements.value.map { el ->
                    el.copy(
                        normalColor = bc ?: el.normalColor,
                        pressedColor = bc ?: el.pressedColor,
                        normalTextColor = tc ?: el.normalTextColor,
                        pressedTextColor = tc ?: el.pressedTextColor,
                    )
                }
            }
            // 同时加载配置状态
            loadConfigFromDb()
        } catch (_: Exception) {
            currentOverlayElements.value = emptyList()
        }
    }

    // ── 按键映射开关（Crown → MoonLink） ──

    /** 按键映射是否启用（Compose 可观察状态） */
    var keyMappingState: Boolean by mutableStateOf(false)

    /** 按键映射是否启用 */
    val isKeyMappingFucEnabled: Boolean
        get() = keyMappingState

    /** 当前选中的方案 configId，从 SharedPreferences 读取。 */
    val currentSchemeConfigId: Long
        get() {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
            return prefs.getLong(PREF_CURRENT_CONFIG_ID, 0L)
        }

    /** 当前方案的名称，从数据库查询。内置方案固定返回"内置虚拟手柄方案"。 */
    val currentSchemeName: String
        get() {
            if (currentSchemeConfigId == 0L) return "内置虚拟手柄方案"
            try {
                val db = com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper(activity)
                val name = db.queryConfigAttribute(
                    currentSchemeConfigId,
                    com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_STRING_CONFIG_NAME,
                    "未命名"
                )
                return name as? String ?: "未命名"
            } catch (_: Exception) {
                return "未命名"
            }
        }

    /** 设置按键映射开关（不持久化，重启后默认为关闭）。同时加载当前方案的覆盖层元素和配置状态。由 Compose UI（KeyMappingSection）调用。 */
    fun setKeyMappingEnabled(enabled: Boolean) {
        keyMappingState = enabled
        if (enabled) {
            reloadOverlay()
        } else {
            currentOverlayElements.value = emptyList()
            // 关闭时重置触控状态，让下层触控处理器接管
            configTouchEnabled = true
            configGlobalOpacity = 100
        }
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
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(enablePerfOverlay = prefConfig.enablePerfOverlay, perfOverlayLocked = prefConfig.perfOverlayLocked) }
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
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(enablePip = prefConfig.enablePip) }
        displayTransientMessage(if (prefConfig.enablePip) "画中画已开启" else "画中画已关闭")
    }

    fun toggleGyro() {
        val enable = !prefConfig.gyroToRightStick
        if (enable) {
            enableGyroRightStick()
        } else {
            disableGyro()
        }
        displayTransientMessage(if (enable) "体感已开启" else "体感已关闭")
    }

    /** 开启体感右摇杆模式，注册陀螺仪传感器 */
    fun enableGyroRightStick() {
        prefConfig.gyroToRightStick = true
        prefConfig.gyroToMouse = false
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(gyroToRightStick = true, gyroToMouse = false) }
        controllerHandler?.gyroManager?.setGyroToRightStickEnabled(true)
        LimeLog.info("StreamEngine: 体感右摇杆模式已启用")
    }

    /** 开启体感鼠标模式，注册陀螺仪传感器到默认上下文 */
    fun enableGyroMouse() {
        prefConfig.gyroToMouse = true
        prefConfig.gyroToRightStick = false
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(gyroToMouse = true, gyroToRightStick = false) }
        controllerHandler?.gyroManager?.setGyroToMouseEnabled(true)
        LimeLog.info("StreamEngine: 体感鼠标模式已启用")
    }

    /** 关闭所有体感模式，注销陀螺仪传感器 */
    fun disableGyro() {
        val hadRightStick = prefConfig.gyroToRightStick
        val hadMouse = prefConfig.gyroToMouse
        prefConfig.gyroToRightStick = false
        prefConfig.gyroToMouse = false
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(gyroToRightStick = false, gyroToMouse = false) }
        if (hadRightStick) {
            controllerHandler?.gyroManager?.setGyroToRightStickEnabled(false)
        }
        if (hadMouse) {
            controllerHandler?.gyroManager?.setGyroToMouseEnabled(false)
        }
        LimeLog.info("StreamEngine: 体感已关闭")
    }

    /** 激活按键发生变化时重新计算所有控制器的 gyroHold 状态 */
    fun recomputeGyroHold() {
        controllerHandler?.gyroManager?.recomputeGyroHoldForAllContexts()
        LimeLog.info("StreamEngine: gyroHold 已重新计算")
    }

    fun toggleAdaptiveBitrate() {
        prefConfig.enableAdaptiveBitrate = !prefConfig.enableAdaptiveBitrate
        prefConfig.writePreferences(activity)
        syncToHostSettings { it.copy(enableAdaptiveBitrate = prefConfig.enableAdaptiveBitrate) }
        displayTransientMessage(if (prefConfig.enableAdaptiveBitrate) "自适应码率已开启" else "自适应码率已关闭")
    }

    /** 启动智能码率（如设置已开启）。在连接建立后调用，也供外部切换 AUTO 时调用。*/
    fun startAdaptiveBitrateIfEnabled() {
        if (!prefConfig.enableAdaptiveBitrate) return
        if (adaptiveBitrateService != null) return
        val c = conn ?: return
        val service = AdaptiveBitrateService(
            nvHttpFactory = { c.createNvHttp() },
            statsProvider = {
                latestPerfInfo?.let { p ->
                    AdaptiveBitrateService.AbrStats(
                        packetLoss = p.lostFrameRate,
                        rttMs = (p.rttInfo shr 32).toInt(),
                        decodeFps = p.totalFps,
                        droppedFrames = 0
                    )
                }
            },
            onBitrateChanged = { kbps, _ ->
                c.applyBitrateLocally(kbps)
            }
        )
        service.start(prefConfig.bitrate, prefConfig.abrMode)
        adaptiveBitrateService = service
        LimeLog.info("StreamEngine: AdaptiveBitrateService 已启动，初始码率=${prefConfig.bitrate}kbps")
    }

    /** 停止智能码率服务。幂等，可在任意状态下安全调用。 */
    fun stopAdaptiveBitrate() {
        adaptiveBitrateService?.stop()
        adaptiveBitrateService = null
    }

    /**
     * 将当前 prefConfig 的变更同步到 per-device HostSettings，
     * 使设备串流设置页面（设备概要 → 串流设置）中对应的开关状态跟随变化。
     */
    private fun syncToHostSettings(updater: (HostSettings) -> HostSettings) {
        val uuid = pcUuid ?: return
        try {
            val mgr = HostSettingsManager(activity)
            val current = mgr.getSettings(uuid)
            mgr.saveSettings(uuid, updater(current))
        } catch (_: Exception) {
            // 忽略 — host settings 的同步是辅助性的，不应影响核心功能
        }
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
            val c2 = conn ?: return@postDelayed
            var mod = finalModifier
            for (pos in keys.indices.reversed()) {
                val key = keys[pos]
                mod = (mod.toInt() and getKeyModifier(key).toInt().inv()).toByte()
                c2.sendKeyboardInput(key, com.limelight.nvstream.input.KeyboardPacket.KEY_UP, mod, 0.toByte())
            }
        }, KEY_UP_DELAY)
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

    /**
     * 根据 evdev scancode 映射修饰键位掩码（CR-2）。
     * 用于 EvdevCaptureProvider 路径的 keyboardEvent() 调用链，
     * 与 [getKeyModifier]（使用 KeyboardTranslator.VK_* 常量）不同。
     */
    private fun getKeyModifierByEvdevCode(keyCode: Short): Byte = when (keyCode.toInt()) {
        0x2A, 0x36 -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_SHIFT
        0x1D        -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_CTRL
        0x38        -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_ALT
        0x5B        -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_META
        else        -> 0
    }

    // ========================================================================
    // 虚拟键盘实时按键（逐个按下/释放，支持修饰键状态）
    // ========================================================================

    /**
     * 发送单个按键事件（按下或释放），带修饰键状态。
     *
     * 与 [sendKeys] 不同：sendKeys 一次性按下所有键再释放（适合快捷键组合），
     * 本方法逐个发送按下/释放事件（适合虚拟键盘实时输入）。
     *
     * @param keyCode Android KeyEvent 键码
     * @param down true=按下，false=释放
     * @param modifier 当前修饰键 bitmask（SHIFT=0x01, CTRL=0x02, ALT=0x04, META=0x08）
     */
    fun sendKeyboardInputWithModifier(keyCode: Short, down: Boolean, modifier: Byte) {
        val c = conn ?: return
        val action = if (down) com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN
                     else com.limelight.nvstream.input.KeyboardPacket.KEY_UP
        c.sendKeyboardInput(keyCode, action, modifier, 0.toByte())
    }

    /**
     * 将 Unicode 文本直接发送到远程主机（适用于系统 IME 输入）。
     * 绕过键码映射，直接发送 UTF-8 编码的文本。
     */
    fun sendUtf8Text(text: String) {
        conn?.sendUtf8Text(text)
    }

    /**
     * 振动反馈（供虚拟键盘按键使用）。
     */
    fun rumbleSingleVibrator(lowFreq: Short, highFreq: Short, duration: Int) {
        try {
            @Suppress("DEPRECATION")
            val vibrator = activity.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        duration.toLong(),
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                vibrator.vibrate(duration.toLong())
            }
        } catch (_: Exception) {
            // 部分设备可能没有振动器
        }
    }

    // ========================================================================
    // 按键便捷方法
    // ========================================================================

    /** 显示桌面: Win+D */
    fun sendWinD() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LWIN.toShort(),
            0x44.toShort()
        ))
    }

    /** 展示窗口: Win+Tab */
    fun sendWinTab() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LWIN.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_TAB.toShort()
        ))
    }

    /** 锁定: Win+L */
    fun sendWinL() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LWIN.toShort(),
            0x4C.toShort()
        ))
    }

    /** 唤起远端主机屏幕键盘 (Win+Ctrl+O) */
    fun sendToggleHostKeyboard() {
        sendWinCtrlO()
    }

    /** 主机键盘开关: Win+Ctrl+O */
    fun sendWinCtrlO() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LWIN.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_LCONTROL.toShort(),
            0x4F.toShort()
        ))
    }

    /** 任务管理器: Ctrl+Shift+Esc */
    fun sendCtrlShiftEsc() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LCONTROL.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_LSHIFT.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_ESCAPE.toShort()
        ))
    }

    /** Win 键 */
    fun sendWin() {
        sendKeys(shortArrayOf(com.limelight.binding.input.KeyboardTranslator.VK_LWIN.toShort()))
    }

    /** Alt+Tab */
    fun sendAltTab() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_MENU.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_TAB.toShort()
        ))
    }

    /** Alt+F4 */
    fun sendAltF4() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_MENU.toShort(),
            (com.limelight.binding.input.KeyboardTranslator.VK_F1 + 3).toShort()
        ))
    }

    /** 睡眠: Win+X → U+S（两段式，延迟 200ms） */
    fun sendSleep() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LWIN.toShort(),
            0x58.toShort()  // 'X'
        ))
        handler.postDelayed({
            sendKeys(shortArrayOf(
                0x55.toShort(),  // 'U'
                0x53.toShort()   // 'S'
            ))
        }, SLEEP_DELAY)
    }

    /** HDR 切换: Win+Alt+B */
    fun sendHdrToggle() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LWIN.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_MENU.toShort(),
            0x42.toShort()
        ))
    }

    // 性能面板
    @Volatile
    var latestPerfInfo: PerformanceInfo? = null
    var onPerfInfoUpdate: ((PerformanceInfo) -> Unit)? = null

    // 带宽计算
    private var previousRxBytes = 0L
    private var previousTimeMillis = 0L
    private var lastValidBandwidth = "N/A"
    @Volatile
    var bandwidthInfo: String = "N/A"

    /** 远程鼠标切换: Ctrl+Alt+Shift+N（与旧 GameMenu 一致） */
    fun sendRemoteMouseToggle() {
        sendKeys(shortArrayOf(
            com.limelight.binding.input.KeyboardTranslator.VK_LCONTROL.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_MENU.toShort(),
            com.limelight.binding.input.KeyboardTranslator.VK_LSHIFT.toShort(),
            78.toShort()  // VK_N
        ))
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
            if (attemptedConnection && connected && !activity.isFinishing && !isInPipMode) {
                // 极速恢复：暂停渲染但不中断串流，surface 重建后 resume
                isExtremeResumeEnabled = true
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
        StreamLogger.log(activity, "STAGE", "开始: $stage")
        handler.post { onStageUpdate?.invoke(stage, false, false) }
    }

    override fun stageComplete(stage: String) {
        LimeLog.info("StreamEngine: stageComplete $stage")
        StreamLogger.log(activity, "STAGE", "完成: $stage")
        // 不传递 complete=true，避免 stage 级别完成就隐藏进度 overlay
        // 进度 overlay 由 firstFrameCallback 触发隐藏，确保首帧到达后才消失
        handler.post { onStageUpdate?.invoke(stage, false, false) }
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        LimeLog.severe("StreamEngine: stageFailed $stage port=$portFlags err=$errorCode")
        StreamLogger.log(activity, "STAGE", "失败: $stage errorCode=$errorCode portFlags=$portFlags")
        handler.post {
            if (activity.isFinishing) return@post
            onStageUpdate?.invoke(stage, false, true)
            var msg = "连接失败: $stage (错误码 $errorCode)"
            if (errorCode == 503) {
                msg = "连接失败: Sunshine 拒绝显示模式设置\n请检查在 Sunshine 面板中虚拟显示器 (VDD) 配置。"
            }
            if (portFlags != 0) {
                msg += "\n\n端口检测失败，请检查路由器端口转发设置:\n${MoonBridge.stringifyPortFlags(portFlags, "\n")}"
            }
            activeDialog = android.app.AlertDialog.Builder(activity)
                .setTitle("连接失败")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("确定") { _, _ ->
                    activeDialog = null
                    activity.finish()
                }
                .show()
            activeDialog?.setOnDismissListener { activeDialog = null }
        }
    }

    override fun connectionStarted() {
        LimeLog.info("StreamEngine: connectionStarted")
        StreamLogger.log(activity, "CONN", "连接成功")
        connected = true
        isChangingResolution = false  // 新流已建立，清除分辨率切换标记

        // 流时长统计初始化
        streamStartTime = System.currentTimeMillis()
        lastActiveTime = System.currentTimeMillis()
        isStreamingActive = true

        handler.post {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // 连接建立后启动智能码率（如设置已开启）
        startAdaptiveBitrateIfEnabled()
        // 剪贴板同步
        initClipboardSync()

        // 仅控制模式：无视频解码器，首帧回调不会触发，
        // 在此直接通知 UI 连接完成，关闭进度 overlay
        if (prefConfig.controlOnly) {
            handler.post { onStageUpdate?.invoke("connected", true, false) }
        }
    }

    override fun connectionTerminated(errorCode: Int) {
        LimeLog.info("StreamEngine: connectionTerminated code=$errorCode")
        StreamLogger.log(activity, "CONN", "断开 errorCode=$errorCode")
        connected = false
        // 停止智能码率
        stopAdaptiveBitrate()
        handler.post {
            // 如果正在切换分辨率/显示器，跳过 UI 提示和 finish，新 Activity 会接管
            if (isChangingResolution) return@post

            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!activity.isFinishing) {
                // 延迟统计信息 Toast（在 finish 之前显示）
                if (prefConfig.enableLatencyToast) {
                    try {
                        val avgLat = decoderRenderer?.getAverageEndToEndLatency() ?: 0
                        val avgDecLat = decoderRenderer?.getAverageDecoderLatency() ?: 0
                        val latencyText = buildString {
                            if (avgLat > 0) append("端到端延迟: ${avgLat}ms")
                            if (avgDecLat > 0) {
                                if (isNotEmpty()) append(" | ")
                                append("解码延迟: ${avgDecLat}ms")
                            }
                        }
                        if (latencyText.isNotEmpty()) {
                            Toast.makeText(activity, latencyText, Toast.LENGTH_LONG).show()
                        }
                    } catch (_: Exception) {}
                }
                if (errorCode != 0 && errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                    val portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode)
                    var msg = when (errorCode) {
                        MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC -> "未收到视频数据，请检查网络连接"
                        MoonBridge.ML_ERROR_NO_VIDEO_FRAME -> "未收到视频帧，请检查网络连接"
                        MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION,
                        MoonBridge.ML_ERROR_PROTECTED_CONTENT -> "串流被意外中断"
                        MoonBridge.ML_ERROR_FRAME_CONVERSION -> "视频格式转换失败"
                        else -> "串流已断开 (${if (kotlin.math.abs(errorCode) > 1000)
                            "0x${Integer.toHexString(errorCode)}" else errorCode.toString()})"
                    }
                    if (portFlags != 0) {
                        msg += "\n\n端口检测失败，请检查路由器端口转发设置:\n${MoonBridge.stringifyPortFlags(portFlags, "\n")}"
                    }
                    if (!activity.isFinishing) {
                        activeDialog = android.app.AlertDialog.Builder(activity)
                        .setTitle("串流已断开")
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton("确定") { _, _ ->
                            activeDialog = null
                            activity.finish()
                        }
                        .show()
                        activeDialog?.setOnDismissListener { activeDialog = null }
                    }
                } else {
                    // 正常断开，调用 onStreamEnded（可能触发 finish）
                    onStreamEnded?.invoke()
                    if (!activity.isFinishing) {
                        activity.finish()
                    }
                }
            }
        }
    }

    override fun connectionStatusUpdate(connectionStatus: Int) {}

    override fun displayMessage(message: String) {
        LimeLog.info("StreamEngine: displayMessage $message")
        StreamLogger.log(activity, "SRV_MSG", message)
        handler.post { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
    }

    override fun displayTransientMessage(message: String) {
        handler.post { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        if (!configGameVibrator) return
        controllerHandler?.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor)
    }
    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        controllerHandler?.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger)
    }
    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        isHdrEnabled = enabled
    }
    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
        controllerHandler?.handleSetMotionEventState(controllerNumber, motionType, reportRateHz)
    }
    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        controllerHandler?.handleSetControllerLED(controllerNumber, r, g, b)
    }
    override fun onResolutionChanged(width: Int, height: Int) {
        LimeLog.info("StreamEngine: 分辨率变化 ${width}x${height}")
        // 不要覆写 prefConfig.width/height —— 那是用户的偏好设置，
        // 在 prepareConnection()/createConnection() 中需要保持原始值发送给 Sunshine。
        decoderRenderer?.onResolutionChanged(width, height)
        handler.post {
            val orientation = if (width > height) "横屏" else "竖屏"
            LimeLog.info("StreamEngine: 方向 $orientation")
        }
    }

    // ── EvdevListener 实现 ──

    override fun mouseMove(deltaX: Int, deltaY: Int) {
        conn?.sendMouseMove(deltaX.toShort(), deltaY.toShort())
    }

    override fun mouseButtonEvent(buttonId: Int, down: Boolean) {
        val buttonIndex: Byte = when (buttonId) {
            EvdevListener.BUTTON_LEFT -> MouseButtonPacket.BUTTON_LEFT
            EvdevListener.BUTTON_MIDDLE -> MouseButtonPacket.BUTTON_MIDDLE
            EvdevListener.BUTTON_RIGHT -> MouseButtonPacket.BUTTON_RIGHT
            EvdevListener.BUTTON_X1 -> MouseButtonPacket.BUTTON_X1
            EvdevListener.BUTTON_X2 -> MouseButtonPacket.BUTTON_X2
            else -> {
                LimeLog.warning("StreamEngine: 未处理的鼠标按钮 $buttonId")
                return
            }
        }
        if (down) conn?.sendMouseButtonDown(buttonIndex)
        else conn?.sendMouseButtonUp(buttonIndex)
    }

    override fun mouseVScroll(amount: Byte) {
        conn?.sendMouseScroll(amount)
    }

    override fun mouseHScroll(amount: Byte) {
        conn?.sendMouseHScroll(amount)
    }

    override fun keyboardEvent(buttonDown: Boolean, keyCode: Short) {
        val modifierBit = getKeyModifierByEvdevCode(keyCode)
        if (modifierBit != 0.toByte()) {
            // 修饰键（Shift/Ctrl/Alt/Win）按下/释放 → 更新追踪状态
            currentModifiers = if (buttonDown) {
                (currentModifiers.toInt() or modifierBit.toInt()).toByte()
            } else {
                (currentModifiers.toInt() and modifierBit.toInt().inv()).toByte()
            }
        }
        // 发送按键事件，使用协议正确的 KEY_DOWN(0x03)/KEY_UP(0x04) 和当前修饰键状态
        conn?.sendKeyboardInput(keyCode,
            if (buttonDown) com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN
            else com.limelight.nvstream.input.KeyboardPacket.KEY_UP,
            currentModifiers, 0)
    }

    // ========================================================================
    // 生命周期
    // ========================================================================

    /** 进入 PiP 时暂停非必要资源 */
    fun pauseForPip() {
        if (connected) {
            LimeLog.info("StreamEngine: PiP 保持连接中")
        }
    }

    /** 退出 PiP 时恢复全量资源 */
    fun restoreFullStreaming() {
        LimeLog.info("StreamEngine: 恢复全量串流资源")
        audioRenderer?.resumeProcessing()
        decoderRenderer?.resumeProcessing()
    }

    fun onResume() {
        if (isInPipMode) {
            isInPipMode = false
            restoreFullStreaming()
        }
    }

    /** PiP 模式变化 — 由 Activity.onPictureInPictureModeChanged 调用 */
    fun onPiPModeChanged(isInPictureInPictureMode: Boolean) {
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            LimeLog.info("StreamEngine: 进入 PiP 模式，暂停音频/渲染优化")
            pauseForPip()
        } else {
            LimeLog.info("StreamEngine: 退出 PiP 模式，恢复全量串流")
            restoreFullStreaming()
        }
    }

    /** 窗口焦点变化 — 恢复剪贴板同步和输入捕获 */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            clipboardSyncManager?.onFocusGained()
        }
        inputCaptureProvider?.onWindowFocusChanged(hasFocus)
    }

    /** 停止流时长计时（Activity onStop 时调用） */
    fun onStopStreaming() {
        if (isStreamingActive && lastActiveTime > 0) {
            accumulatedStreamTime += System.currentTimeMillis() - lastActiveTime
            isStreamingActive = false
            LimeLog.info("串流时长暂停，已累计: ${accumulatedStreamTime / 1000}秒")
        }
    }

    /** 恢复流时长计时（Activity onStart 时调用） */
    fun onStartStreaming() {
        if (!isStreamingActive && streamStartTime > 0) {
            lastActiveTime = System.currentTimeMillis()
            isStreamingActive = true
            LimeLog.info("串流时长恢复")
        }
    }

    fun onPause() {
        // 切换分辨率时不能断连远端，新 Activity 会接管串流
        if (isChangingResolution) return
        // PiP 模式下保持串流连接和解码器存活，让画中画窗口继续显示视频画面
        if (isInPipMode) {
            LimeLog.info("StreamEngine: PiP 模式，保持连接和解码器存活")
            // 仅暂停音频以节省资源，解码器保持运行以便持续渲染画面供 PiP 使用
            audioRenderer?.pauseProcessing()
            return
        }
        // 关闭对话框（Activity 即将不可见，防止 WindowLeaked）
        activeDialog?.dismiss()
        activeDialog = null
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
        // 关闭所有显示的对话框，防止 WindowLeaked
        activeDialog?.dismiss()
        activeDialog = null
        onPerfInfoUpdate = null
        stopAdaptiveBitrate()
        // 切换分辨率时由新 Activity 接管串流，不在这里 stop
        if (!isChangingResolution && connected) conn?.stop()
        releaseWifiLocks()
        clipboardSyncManager?.stop()
        clipboardSyncManager = null
        controllerHandler = null
        inputCaptureProvider?.destroy()
        inputCaptureProvider = null
        audioRenderer = null
        decoderRenderer = null
        conn = null
    }

    // ═══════════════════════════════════════════════════════════
    // 主机级串流配置映射
    // ═══════════════════════════════════════════════════════════

    /**
     * 将 [HostSettings] 的各字段覆盖到 [prefConfig]。
     *
     * 处理类型差异（String → Enum、String → Int 等），
     * 确保主机级配置能正确传递到 [StreamConfiguration]。
     * 注意：主机级配置在第 2c 步重新应用，拥有最高优先级，覆盖 Intent 中的"最近配置"。
     */
    private fun applyHostSettingsToPrefConfig(
        prefConfig: PreferenceConfiguration,
        settings: HostSettings,
    ) {
        // ── 触控模式 ──
        prefConfig.enableEnhancedTouch = settings.enableEnhancedTouch
        prefConfig.enhancedTouchOnWhichSide = settings.enhancedTouchOnWhichSide
        prefConfig.enhanceTouchZoneDivider = settings.enhanceTouchZoneDivider
        prefConfig.pointerVelocityFactor = settings.pointerVelocityFactor.toFloat()
        prefConfig.touchscreenTrackpad = settings.touchscreenTrackpad
        prefConfig.touchpadSensitivity = settings.touchpadSensitivity
        prefConfig.enableNativeMousePointer = settings.enableNativeMousePointer
        prefConfig.enableDoubleClickDrag = settings.enableDoubleClickDrag
        prefConfig.doubleTapTimeThreshold = settings.doubleTapTimeThreshold
        prefConfig.enableLocalCursorRendering = settings.enableLocalCursorRendering
        prefConfig.nativeTouchFingersToToggleKeyboard = settings.nativeTouchFingersToToggleKeyboard
        prefConfig.longPressflatRegionPixels = settings.longPressFlatRegionPixels
        prefConfig.syncTouchEventWithDisplay = settings.syncTouchEventWithDisplay

        // ── 显示设置 ──
        prefConfig.width = settings.width
        prefConfig.height = settings.height
        prefConfig.fps = settings.fps
        prefConfig.bitrate = if (settings.bitrate > 0) settings.bitrate
            else PreferenceConfiguration.getDefaultBitrate("${settings.width}x${settings.height}", settings.fps.toString())
        prefConfig.enableAdaptiveBitrate = settings.enableAdaptiveBitrate
        prefConfig.abrMode = settings.abrMode
        prefConfig.videoFormat = parseFormatOption(settings.videoFormat)
        prefConfig.enableHdr = settings.enableHdr
        prefConfig.enableHdrHighBrightness = settings.enableHdrHighBrightness
        prefConfig.hdrMode = settings.hdrMode
        prefConfig.resolutionScale = settings.resolutionScale
        prefConfig.framePacing = settings.framePacing
        prefConfig.stretchVideo = settings.stretchVideo
        prefConfig.reverseResolution = settings.reverseResolution
        prefConfig.rotableScreen = settings.rotableScreen
        prefConfig.outputBufferQueueLimit = settings.outputBufferQueueLimit
        prefConfig.forceMtkMaxOperatingRate = settings.forceMtkMaxOperatingRate
        prefConfig.unlockFps = settings.unlockFps
        prefConfig.enablePip = settings.enablePip
        prefConfig.useExternalDisplay = settings.useExternalDisplay
        prefConfig.screenPosition = parseScreenPosition(settings.screenPosition)
        prefConfig.screenOffsetX = settings.screenOffsetX
        prefConfig.screenOffsetY = settings.screenOffsetY
        prefConfig.reduceRefreshRate = settings.reduceRefreshRate
        prefConfig.fullRange = settings.fullRange

        // ── 虚拟显示器 ──
        vddWidth = settings.vddWidth
        vddHeight = settings.vddHeight
        vddFps = settings.vddFps

        // ── 主机设置 ──
        prefConfig.enableSops = settings.enableSops
        prefConfig.lockScreenAfterDisconnect = settings.lockScreenAfterDisconnect
        prefConfig.playHostAudio = settings.playHostAudio
        prefConfig.muteClientAudio = settings.muteClientAudio
        prefConfig.controlOnly = settings.controlOnly
        prefConfig.enableClipboardSyncText = settings.enableClipboardSyncText
        prefConfig.enableClipboardSyncImage = settings.enableClipboardSyncImage
        prefConfig.enableEscMenu = settings.enableEscMenu
        prefConfig.escMenuKey = settings.escMenuKey
        prefConfig.enableStartKeyMenu = settings.enableStartKeyMenu

        // ── 声音设置 ──
        prefConfig.audioConfiguration = parseAudioConfiguration(settings.audioConfiguration)
        prefConfig.audioCodec = parseAudioCodec(settings.audioCodec)
        prefConfig.audioCodecBitrate = settings.audioCodecBitrate
        prefConfig.enableAudioFx = settings.enableAudioFx
        prefConfig.enableSpatializer = settings.enableSpatializer
        prefConfig.enableAudioPassthrough = settings.enableAudioPassthrough
        prefConfig.audioPassthroughBufferBytes = parseAudioPassthroughBuffer(settings.audioPassthroughBuffer)
        prefConfig.enableAudioVibration = settings.enableAudioVibration
        prefConfig.audioVibrationStrength = settings.audioVibrationStrength
        prefConfig.audioVibrationMode = settings.audioVibrationMode
        prefConfig.audioVibrationScene = settings.audioVibrationScene
        prefConfig.enableMic = settings.enableMic
        prefConfig.micBitrate = settings.micBitrate
        prefConfig.micIconColor = settings.micIconColor

        // ── 体感 ──
        prefConfig.gyroToRightStick = settings.gyroToRightStick
        prefConfig.gyroToMouse = settings.gyroToMouse
        prefConfig.gyroFullDeflectionDps = settings.gyroFullDeflectionDps
        prefConfig.gyroSensitivityMultiplier = settings.gyroSensitivityMultiplier
        prefConfig.gyroInvertXAxis = settings.gyroInvertXAxis
        prefConfig.gyroInvertYAxis = settings.gyroInvertYAxis
        prefConfig.gyroActivationKeyCode = settings.gyroActivationKeyCode
        prefConfig.showGyroCard = settings.showGyroCard

        // ── 其它 ──
        prefConfig.enablePerfOverlay = settings.enablePerfOverlay
        prefConfig.perfOverlayLocked = settings.perfOverlayLocked
        prefConfig.perfOverlayBgOpacity = settings.perfOverlayBgOpacity
        prefConfig.perfOverlayOrientation = parsePerfOverlayOrientation(settings.perfOverlayOrientation)
        prefConfig.perfOverlayPosition = parsePerfOverlayPosition(settings.perfOverlayPosition)
        prefConfig.enableSimplifyPerfOverlay = settings.enableSimplifyPerfOverlay
        prefConfig.enableLatencyToast = settings.enableLatencyToast
        prefConfig.fabOpacity = settings.fabOpacity
        prefConfig.toolPanelAutoHideMode = settings.toolPanelAutoHideMode
        prefConfig.enableFloatBall = settings.enableFloatBall
        prefConfig.floatBallAutoHideDelay = settings.floatBallAutoHideDelay
        prefConfig.floatBallSingleClickAction = settings.floatBallSingleClickAction
        prefConfig.floatBallDoubleClickAction = settings.floatBallDoubleClickAction
        prefConfig.floatBallLongClickAction = settings.floatBallLongClickAction
        prefConfig.floatBallSwipeUpAction = settings.floatBallSwipeUpAction
        prefConfig.floatBallSwipeDownAction = settings.floatBallSwipeDownAction
        prefConfig.floatBallSwipeLeftAction = settings.floatBallSwipeLeftAction
        prefConfig.floatBallSwipeRightAction = settings.floatBallSwipeRightAction
        prefConfig.showBitrateCard = settings.showBitrateCard
        prefConfig.showQuickKeyCard = settings.showQuickKeyCard
        prefConfig.keyMappingEnabled = settings.keyMappingEnabled
        prefConfig.disableWarnings = settings.disableWarnings
        prefConfig.showPauseStream = settings.showPauseStream
    }

    /**
     * 将 [prefConfig] 中触控模式的各项值同步到 [NativeTouchContext] 静态参数。
     * 每次应用主机配置后必须调用，确保运行时使用的参数与持久化配置一致。
     */
    private fun syncNativeTouchContext() {
        NativeTouchContext.ENABLE_ENHANCED_TOUCH = prefConfig.enableEnhancedTouch
        NativeTouchContext.ENHANCED_TOUCH_ON_RIGHT = if (prefConfig.enhancedTouchOnWhichSide) -1 else 1
        NativeTouchContext.ENHANCED_TOUCH_ZONE_DIVIDER = prefConfig.enhanceTouchZoneDivider * 0.01f
        NativeTouchContext.POINTER_VELOCITY_FACTOR = prefConfig.pointerVelocityFactor * 0.01f
        NativeTouchContext.INTIAL_ZONE_PIXELS = prefConfig.longPressflatRegionPixels.toFloat()
    }

    // ── 类型转换辅助函数 ─────────────────────────────────

    private fun parseFormatOption(value: String): PreferenceConfiguration.FormatOption {
        return when (value.lowercase()) {
            "forceav1", "force_av1", "av1" -> PreferenceConfiguration.FormatOption.FORCE_AV1
            "forcehevc", "force_hevc", "hevc" -> PreferenceConfiguration.FormatOption.FORCE_HEVC
            "neverh265", "force_h264", "h264" -> PreferenceConfiguration.FormatOption.FORCE_H264
            else -> PreferenceConfiguration.FormatOption.AUTO
        }
    }

    private fun parseAudioConfiguration(value: String): MoonBridge.AudioConfiguration {
        return when (value) {
            "714" -> MoonBridge.AUDIO_CONFIGURATION_714_SURROUND
            "71" -> MoonBridge.AUDIO_CONFIGURATION_71_SURROUND
            "51" -> MoonBridge.AUDIO_CONFIGURATION_51_SURROUND
            else -> MoonBridge.AUDIO_CONFIGURATION_STEREO
        }
    }

    private fun parseAudioCodec(value: String): Int {
        return when (value.lowercase()) {
            "ac3" -> MoonBridge.AUDIO_CODEC_AC3
            "eac3" -> MoonBridge.AUDIO_CODEC_EAC3
            "pcm" -> MoonBridge.AUDIO_CODEC_PCM_S16
            "opus" -> MoonBridge.AUDIO_CODEC_OPUS
            else -> MoonBridge.AUDIO_CODEC_OPUS
        }
    }

    private fun parseAudioPassthroughBuffer(value: String): Int {
        return when (value.lowercase()) {
            "low" -> 8 * 1024
            "high" -> 32 * 1024
            else -> 16 * 1024
        }
    }

    private fun parsePerfOverlayOrientation(value: String): PreferenceConfiguration.PerfOverlayOrientation {
        return if (value.lowercase() == "vertical") {
            PreferenceConfiguration.PerfOverlayOrientation.VERTICAL
        } else {
            PreferenceConfiguration.PerfOverlayOrientation.HORIZONTAL
        }
    }

    private fun parsePerfOverlayPosition(value: String): PreferenceConfiguration.PerfOverlayPosition {
        return when (value.lowercase()) {
            "bottom" -> PreferenceConfiguration.PerfOverlayPosition.BOTTOM
            "top_left" -> PreferenceConfiguration.PerfOverlayPosition.TOP_LEFT
            "top_right" -> PreferenceConfiguration.PerfOverlayPosition.TOP_RIGHT
            "bottom_left" -> PreferenceConfiguration.PerfOverlayPosition.BOTTOM_LEFT
            "bottom_right" -> PreferenceConfiguration.PerfOverlayPosition.BOTTOM_RIGHT
            else -> PreferenceConfiguration.PerfOverlayPosition.TOP
        }
    }

    private fun parseScreenPosition(value: String): PreferenceConfiguration.ScreenPosition {
        return when (value.lowercase()) {
            "top_left" -> PreferenceConfiguration.ScreenPosition.TOP_LEFT
            "top_center" -> PreferenceConfiguration.ScreenPosition.TOP_CENTER
            "top_right" -> PreferenceConfiguration.ScreenPosition.TOP_RIGHT
            "center_left" -> PreferenceConfiguration.ScreenPosition.CENTER_LEFT
            "center_right" -> PreferenceConfiguration.ScreenPosition.CENTER_RIGHT
            "bottom_left" -> PreferenceConfiguration.ScreenPosition.BOTTOM_LEFT
            "bottom_center" -> PreferenceConfiguration.ScreenPosition.BOTTOM_CENTER
            "bottom_right" -> PreferenceConfiguration.ScreenPosition.BOTTOM_RIGHT
            else -> PreferenceConfiguration.ScreenPosition.CENTER
        }
    }
}
