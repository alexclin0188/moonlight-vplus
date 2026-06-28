package com.alexclin.moonlink.android.device.streamsettings

import android.view.KeyEvent

/**
 * 单台主机的串流配置数据类。
 *
 * 每个字段对应 [com.limelight.preferences.PreferenceConfiguration] 中的一个配置项，
 * 默认值与全局 PreferenceConfiguration 保持一致。
 *
 * 以 [Category] 枚举区分配置分类，方便 UI 按类展示。
 */
data class HostSettings(
    // ═══════════════════════════════════════════
    // 触控模式 (TOUCH_MODE)
    // ═══════════════════════════════════════════
    /** 增强式多点触控 */
    val enableEnhancedTouch: Boolean = false,
    /** 增强触控区在右侧 */
    val enhancedTouchOnWhichSide: Boolean = true,
    /** 增强触控区分界线（百分比） */
    val enhanceTouchZoneDivider: Int = 50,
    /** 指针速度因子 */
    val pointerVelocityFactor: Int = 100,
    /** 触控板模式 */
    val touchscreenTrackpad: Boolean = true,
    /** 触控板灵敏度 1-200 */
    val touchpadSensitivity: Int = 100,
    /** 本地鼠标指针模式 */
    val enableNativeMousePointer: Boolean = false,
    /** 双击按住拖拽 */
    val enableDoubleClickDrag: Boolean = false,
    /** 双击时间阈值 ms */
    val doubleTapTimeThreshold: Int = 125,
    /** 本地光标渲染 */
    val enableLocalCursorRendering: Boolean = true,
    /** 触控模式下多点触控键盘切换手指数（-1=禁用） */
    val nativeTouchFingersToToggleKeyboard: Int = 3,
    /** 长按时触摸坐标平坦区像素 */
    val longPressFlatRegionPixels: Int = 0,
    /** 同步触摸事件到显示刷新 */
    val syncTouchEventWithDisplay: Boolean = false,

    // ═══════════════════════════════════════════
    // 显示设置 (DISPLAY)
    // ═══════════════════════════════════════════
    /** 分辨率宽度 */
    val width: Int = 1920,
    /** 分辨率高度 */
    val height: Int = 1080,
    /** 帧率 */
    val fps: Int = 60,
    /** 码率 kbps */
    val bitrate: Int = 0, // 0 means auto/default
    /** 自适应码率 */
    val enableAdaptiveBitrate: Boolean = true,
    /** ABR 模式: quality / balanced / lowLatency */
    val abrMode: String = "balanced",
    /** 视频编码格式 */
    val videoFormat: String = "auto", // auto / forceav1 / forceh265 / neverh265
    /** HDR */
    val enableHdr: Boolean = false,
    /** HDR 高亮度 */
    val enableHdrHighBrightness: Boolean = false,
    /** HDR 模式 0=SDR 1=HDR10 2=HLG */
    val hdrMode: Int = 1,
    /** 分辨率缩放 50-400% */
    val resolutionScale: Int = 100,
    /** 帧时序模式 */
    val framePacing: Int = 0, // 0=latency 1=balanced 2=cap-fps 3=smoothness 4=exp-low-latency 5=precise-sync
    /** 拉伸视频 */
    val stretchVideo: Boolean = false,
    /** 反转分辨率 */
    val reverseResolution: Boolean = false,
    /** 可旋转画面 */
    val rotableScreen: Boolean = false,
    /** 输出缓冲区队列大小 1-5 */
    val outputBufferQueueLimit: Int = 2,
    /** MTK 专属选项 */
    val forceMtkMaxOperatingRate: Boolean = false,
    /** 解锁帧率 */
    val unlockFps: Boolean = false,
    /** 画中画 (PiP) */
    val enablePip: Boolean = false,
    /** 使用外接显示器 */
    val useExternalDisplay: Boolean = false,
    /** 画面位置 */
    val screenPosition: String = "center",
    /** 画面偏移 X */
    val screenOffsetX: Int = 0,
    /** 画面偏移 Y */
    val screenOffsetY: Int = 0,
    /** 减少刷新率 */
    val reduceRefreshRate: Boolean = false,
    /** 全色域 */
    val fullRange: Boolean = false,

    // ═══════════════════════════════════════════
    // 虚拟显示器 (VDD)
    // ═══════════════════════════════════════════
    /** VDD 分辨率宽度（0=使用客户端设备原生分辨率） */
    val vddWidth: Int = 0,
    /** VDD 分辨率高度 */
    val vddHeight: Int = 0,
    /** VDD 帧率 */
    val vddFps: Int = 90,

    // ═══════════════════════════════════════════
    // 主机设置 (HOST)
    // ═══════════════════════════════════════════
    /** 自动优化主机设置 (SOPS) */
    val enableSops: Boolean = true,
    /** 断开串流时锁定屏幕 */
    val lockScreenAfterDisconnect: Boolean = false,
    /** 在电脑上播放声音 */
    val playHostAudio: Boolean = false,
    /** 静音客户端音频 */
    val muteClientAudio: Boolean = false,
    /** 仅控制模式 */
    val controlOnly: Boolean = false,
    /** 同步剪贴板文本 */
    val enableClipboardSyncText: Boolean = false,
    /** 同步剪贴板图片 */
    val enableClipboardSyncImage: Boolean = false,
    /** 启用 ESC 菜单 */
    val enableEscMenu: Boolean = true,
    /** ESC 菜单键值 */
    val escMenuKey: Int = KeyEvent.KEYCODE_ESCAPE,
    /** 启用 Start 键菜单 */
    val enableStartKeyMenu: Boolean = true,

    // ═══════════════════════════════════════════
    // 声音设置 (AUDIO)
    // ═══════════════════════════════════════════
    /** 环绕声配置: stereo/51/71/714 */
    val audioConfiguration: String = "2", // 0=Stereo 2=5.1 4=7.1
    /** 音频编解码器: auto / opus / ac3 / eac3 */
    val audioCodec: String = "auto",
    /** 音频编解码器码率 */
    val audioCodecBitrate: Int = 0,
    /** 均衡器 */
    val enableAudioFx: Boolean = false,
    /** 空间音频 */
    val enableSpatializer: Boolean = false,
    /** 音频直通 */
    val enableAudioPassthrough: Boolean = false,
    /** 直通缓冲区: low / normal / high */
    val audioPassthroughBuffer: String = "normal",
    /** 音频驱动振动 */
    val enableAudioVibration: Boolean = false,
    /** 音频振动强度 0-200 */
    val audioVibrationStrength: Int = 80,
    /** 音频振动路由: auto / speaker / headset */
    val audioVibrationMode: String = "auto",
    /** 音频振动场景 0=通用 1=游戏 2=电影 3=音乐 */
    val audioVibrationScene: Int = 0,
    /** 麦克风重定向 */
    val enableMic: Boolean = false,
    /** 麦克风码率 kbps 32-256 */
    val micBitrate: Int = 96,
    /** 麦克风图标颜色: solid_white / accent */
    val micIconColor: String = "solid_white",

    // ═══════════════════════════════════════════
    // 体感 (GYRO)
    // ═══════════════════════════════════════════
    /** 体感映射到右摇杆（运行时） */
    val gyroToRightStick: Boolean = false,
    /** 体感映射到鼠标（运行时） */
    val gyroToMouse: Boolean = false,
    /** 体感满偏转 DPS（运行时） */
    val gyroFullDeflectionDps: Float = 180.0f,
    /** 灵敏度倍率 */
    val gyroSensitivityMultiplier: Float = 1.0f,
    /** X 轴反转 */
    val gyroInvertXAxis: Boolean = false,
    /** Y 轴反转 */
    val gyroInvertYAxis: Boolean = false,
    /** 激活按键码 */
    val gyroActivationKeyCode: Int = KeyEvent.KEYCODE_BUTTON_L2,
    /** 显示体感卡片 */
    val showGyroCard: Boolean = true,

    // ═══════════════════════════════════════════
    // 其它 (OTHER)
    // ═══════════════════════════════════════════
    /** 性能监控图层 */
    val enablePerfOverlay: Boolean = false,
    /** 性能图层锁定 */
    val perfOverlayLocked: Boolean = false,
    /** 性能图层背景不透明度 0-100 */
    val perfOverlayBgOpacity: Int = 53,
    /** 性能图层方向: horizontal / vertical */
    val perfOverlayOrientation: String = "horizontal",
    /** 性能图层位置 */
    val perfOverlayPosition: String = "top",
    /** 启用简化性能覆盖层 */
    val enableSimplifyPerfOverlay: Boolean = false,
    /** 延迟 toast */
    val enableLatencyToast: Boolean = false,
    /** 悬浮按钮不透明度 10-100 */
    val fabOpacity: Int = 50,
    /** 操作面板自动隐藏模式 0=按键映射 1=2s 2=不隐藏 */
    val toolPanelAutoHideMode: Int = 2,
    /** 启用悬浮球 */
    val enableFloatBall: Boolean = true,
    /** 悬浮球自动隐藏延时 ms */
    val floatBallAutoHideDelay: Int = 2000,
    /** 悬浮球单击动作 */
    val floatBallSingleClickAction: String = "open_keyboard",
    /** 悬浮球双击动作 */
    val floatBallDoubleClickAction: String = "open_menu",
    /** 悬浮球长按动作 */
    val floatBallLongClickAction: String = "toggle_visibility",
    /** 悬浮球上滑动作 */
    val floatBallSwipeUpAction: String = "none",
    /** 悬浮球下滑动作 */
    val floatBallSwipeDownAction: String = "none",
    /** 悬浮球左滑动作 */
    val floatBallSwipeLeftAction: String = "none",
    /** 悬浮球右滑动作 */
    val floatBallSwipeRightAction: String = "none",
    /** 显示码率卡片 */
    val showBitrateCard: Boolean = true,
    /** 显示快捷卡片（横屏时默认关闭） */
    val showQuickKeyCard: Boolean = true,
    /** 按键映射开关 */
    val keyMappingEnabled: Boolean = false,
    /** 禁用警告 */
    val disableWarnings: Boolean = false,
    /** 暂停串流支持开关 */
    val showPauseStream: Boolean = false,
) {
    companion object {
        /** 配置分类，用于 UI 分类展示 */
        enum class Category {
            TOUCH_MODE,
            DISPLAY,
            HOST,
            AUDIO,
            GYRO,
            OTHER,
        }

        // 注意：字段到分类的映射已内联到 DeviceStreamSettingsScreen 的各 @Composable 函数中。
    }
}
