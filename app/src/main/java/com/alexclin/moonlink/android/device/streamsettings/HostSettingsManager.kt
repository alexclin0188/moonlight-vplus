package com.alexclin.moonlink.android.device.streamsettings

import android.content.Context
import android.view.KeyEvent
import androidx.preference.PreferenceManager

/**
 * 主机级串流配置持久化管理器。
 *
 * 每个主机使用独立的 SharedPreferences 文件（`host_settings_<uuid>`），
 * 实现配置与主机的绑定。删除主机时应同步调用 [deleteSettings]。
 */
class HostSettingsManager(private val context: Context) {

    companion object {
        private const val SP_PREFIX = "host_settings_"

        // ── SP 键名常量（复用 PreferenceConfiguration 的命名风格） ──

        // 触控模式
        private const val KEY_ENABLE_ENHANCED_TOUCH = "checkbox_enable_enhanced_touch"
        private const val KEY_ENHANCED_TOUCH_ON_RIGHT = "checkbox_enhanced_touch_on_which_side"
        private const val KEY_ENHANCE_TOUCH_ZONE_DIVIDER = "enhanced_touch_zone_divider"
        private const val KEY_POINTER_VELOCITY_FACTOR = "pointer_velocity_factor"
        private const val KEY_TOUCHSCREEN_TRACKPAD = "checkbox_touchscreen_trackpad"
        private const val KEY_TOUCHPAD_SENSITIVITY = "seekbar_touchpad_sensitivity"
        private const val KEY_ENABLE_NATIVE_MOUSE_POINTER = "checkbox_enable_native_mouse_pointer"
        private const val KEY_ENABLE_DOUBLE_CLICK_DRAG = "pref_enable_double_click_drag"
        private const val KEY_DOUBLE_TAP_TIME_THRESHOLD = "seekbar_double_tap_time_threshold"
        private const val KEY_ENABLE_LOCAL_CURSOR_RENDERING = "pref_enable_local_cursor_rendering"
        private const val KEY_NATIVE_TOUCH_FINGERS_TOGGLE_KEYBOARD = "seekbar_keyboard_toggle_fingers_native_touch"
        private const val KEY_LONG_PRESS_FLAT_REGION_PIXELS = "seekbar_flat_region_pixels"
        private const val KEY_SYNC_TOUCH_EVENT_WITH_DISPLAY = "checkbox_sync_touch_event_with_display"

        // 显示设置
        private const val KEY_RESOLUTION = "list_resolution"
        private const val KEY_FPS = "list_fps"
        private const val KEY_BITRATE = "seekbar_bitrate_kbps"
        private const val KEY_ADAPTIVE_BITRATE = "checkbox_adaptive_bitrate"
        private const val KEY_ABR_MODE = "list_abr_mode"
        private const val KEY_VIDEO_FORMAT = "video_format"
        private const val KEY_ENABLE_HDR = "checkbox_enable_hdr"
        private const val KEY_ENABLE_HDR_HIGH_BRIGHTNESS = "checkbox_enable_hdr_high_brightness"
        private const val KEY_HDR_MODE = "list_hdr_mode"
        private const val KEY_RESOLUTION_SCALE = "seekbar_resolutions_scale"
        private const val KEY_FRAME_PACING = "frame_pacing"
        private const val KEY_STRETCH_VIDEO = "checkbox_stretch_video"
        private const val KEY_REVERSE_RESOLUTION = "checkbox_reverse_resolution"
        private const val KEY_ROTABLE_SCREEN = "checkbox_rotable_screen"
        private const val KEY_OUTPUT_BUFFER_QUEUE_LIMIT = "seekbar_output_buffer_queue_limit"
        private const val KEY_FORCE_MTK_MAX_OPERATING_RATE = "checkbox_force_mtk_max_operating_rate"
        private const val KEY_UNLOCK_FPS = "checkbox_unlock_fps"
        private const val KEY_ENABLE_PIP = "checkbox_enable_pip"
        private const val KEY_USE_EXTERNAL_DISPLAY = "use_external_display"
        private const val KEY_SCREEN_POSITION = "list_screen_position"
        private const val KEY_SCREEN_OFFSET_X = "seekbar_screen_offset_x"
        private const val KEY_SCREEN_OFFSET_Y = "seekbar_screen_offset_y"
        private const val KEY_REDUCE_REFRESH_RATE = "checkbox_reduce_refresh_rate"
        private const val KEY_FULL_RANGE = "checkbox_full_range"
        private const val KEY_SCREEN_COMBINATION_MODE = "list_screen_combination_mode"

        // 主机设置
        private const val KEY_ENABLE_SOPS = "checkbox_enable_sops"
        private const val KEY_LOCK_SCREEN_AFTER_DISCONNECT = "checkbox_lock_screen_after_disconnect"
        private const val KEY_PLAY_HOST_AUDIO = "checkbox_host_audio"
        private const val KEY_MUTE_CLIENT_AUDIO = "checkbox_mute_client_audio"
        private const val KEY_CONTROL_ONLY = "checkbox_control_only"
        private const val KEY_CLIPBOARD_SYNC_TEXT = "checkbox_clipboard_sync_text"
        private const val KEY_CLIPBOARD_SYNC_IMAGE = "checkbox_clipboard_sync_image"
        private const val KEY_ENABLE_ESC_MENU = "checkbox_enable_esc_menu"
        private const val KEY_ESC_MENU_KEY = "list_esc_menu_key"
        private const val KEY_ENABLE_START_KEY_MENU = "checkbox_enable_start_key_menu"

        // 声音设置
        private const val KEY_AUDIO_CONFIG = "list_audio_config"
        private const val KEY_AUDIO_CODEC = "list_audio_codec"
        private const val KEY_AUDIO_CODEC_BITRATE = "list_audio_codec_bitrate"
        private const val KEY_ENABLE_AUDIO_FX = "checkbox_enable_audiofx"
        private const val KEY_ENABLE_SPATIALIZER = "checkbox_enable_spatializer"
        private const val KEY_ENABLE_AUDIO_PASSTHROUGH = "checkbox_enable_audio_passthrough"
        private const val KEY_AUDIO_PASSTHROUGH_BUFFER = "list_audio_passthrough_buffer"
        private const val KEY_ENABLE_AUDIO_VIBRATION = "checkbox_audio_vibration"
        private const val KEY_AUDIO_VIBRATION_STRENGTH = "seekbar_audio_vibration_strength"
        private const val KEY_AUDIO_VIBRATION_MODE = "list_audio_vibration_mode"
        private const val KEY_AUDIO_VIBRATION_SCENE = "list_audio_vibration_scene"
        private const val KEY_ENABLE_MIC = "checkbox_enable_mic"
        private const val KEY_MIC_BITRATE = "seekbar_mic_bitrate_kbps"
        private const val KEY_MIC_ICON_COLOR = "list_mic_icon_color"

        // 体感
        private const val KEY_GYRO_SENSITIVITY_MULTIPLIER = "gyro_sensitivity_multiplier"
        private const val KEY_GYRO_INVERT_X_AXIS = "gyro_invert_x_axis"
        private const val KEY_GYRO_INVERT_Y_AXIS = "gyro_invert_y_axis"
        private const val KEY_GYRO_ACTIVATION_KEY_CODE = "gyro_activation_key_code"
        private const val KEY_SHOW_GYRO_CARD = "checkbox_show_gyro_card"

        // 其它
        private const val KEY_ENABLE_PERF_OVERLAY = "checkbox_enable_perf_overlay"
        private const val KEY_PERF_OVERLAY_LOCKED = "perf_overlay_locked"
        private const val KEY_PERF_OVERLAY_BG_OPACITY = "seekbar_perf_overlay_bg_opacity"
        private const val KEY_PERF_OVERLAY_ORIENTATION = "list_perf_overlay_orientation"
        private const val KEY_PERF_OVERLAY_POSITION = "list_perf_overlay_position"
        private const val KEY_ENABLE_SIMPLIFY_PERF_OVERLAY = "checkbox_enable_simplify_perf_overlay"
        private const val KEY_ENABLE_LATENCY_TOAST = "checkbox_enable_post_stream_toast"
        private const val KEY_FAB_OPACITY = "seekbar_fab_opacity"
        private const val KEY_TOOL_PANEL_AUTO_HIDE_MODE = "tool_panel_auto_hide_mode"
        private const val KEY_ENABLE_FLOAT_BALL = "checkbox_enable_float_ball"
        private const val KEY_FLOAT_BALL_AUTO_HIDE_DELAY = "seekbar_float_ball_auto_hide_delay"
        private const val KEY_FLOAT_BALL_SINGLE_CLICK = "list_float_ball_single_click_action"
        private const val KEY_FLOAT_BALL_DOUBLE_CLICK = "list_float_ball_double_click_action"
        private const val KEY_FLOAT_BALL_LONG_CLICK = "list_float_ball_long_click_action"
        private const val KEY_FLOAT_BALL_SWIPE_UP = "list_float_ball_swipe_up_action"
        private const val KEY_FLOAT_BALL_SWIPE_DOWN = "list_float_ball_swipe_down_action"
        private const val KEY_FLOAT_BALL_SWIPE_LEFT = "list_float_ball_swipe_left_action"
        private const val KEY_FLOAT_BALL_SWIPE_RIGHT = "list_float_ball_swipe_right_action"
        private const val KEY_SHOW_BITRATE_CARD = "checkbox_show_bitrate_card"
        private const val KEY_SHOW_QUICK_KEY_CARD = "checkbox_show_QuickKeyCard"
        private const val KEY_KEY_MAPPING_ENABLED = "checkbox_enable_key_mapping"
        private const val KEY_DISABLE_WARNINGS = "checkbox_disable_warnings"
    }

    /**
     * 获取指定主机的配置。如果该主机尚无配置，从全局默认设置中回退。
     */
    fun getSettings(uuid: String): HostSettings {
        val sp = context.getSharedPreferences("$SP_PREFIX$uuid", Context.MODE_PRIVATE)
        val globalSp = PreferenceManager.getDefaultSharedPreferences(context)

        // 辅助函数：如果主机 SP 中没有该 key，则从全局 SP 读取
        fun bool(key: String, globalKey: String = key, globalDefault: Boolean = false): Boolean =
            if (sp.contains(key)) sp.getBoolean(key, globalDefault)
            else globalSp.getBoolean(globalKey, globalDefault)

        fun int(key: String, globalKey: String = key, globalDefault: Int = 0): Int =
            try {
                if (sp.contains(key)) (sp.all[key] as? Number)?.toInt() ?: globalDefault
                else (globalSp.all[globalKey] as? Number)?.toInt() ?: globalDefault
            } catch (_: Exception) {
                globalDefault
            }

        fun string(key: String, globalKey: String = key, globalDefault: String = ""): String =
            try {
                if (sp.contains(key)) sp.all[key]?.toString() ?: globalDefault
                else globalSp.all[globalKey]?.toString() ?: globalDefault
            } catch (_: Exception) {
                globalDefault
            }

        return HostSettings(
            // 触控模式
            enableEnhancedTouch = bool(KEY_ENABLE_ENHANCED_TOUCH),
            enhancedTouchOnWhichSide = bool(KEY_ENHANCED_TOUCH_ON_RIGHT, globalDefault = true),
            enhanceTouchZoneDivider = int(KEY_ENHANCE_TOUCH_ZONE_DIVIDER, globalDefault = 50),
            pointerVelocityFactor = int(KEY_POINTER_VELOCITY_FACTOR, globalDefault = 100),
            touchscreenTrackpad = bool(KEY_TOUCHSCREEN_TRACKPAD, globalDefault = true),
            touchpadSensitivity = int(KEY_TOUCHPAD_SENSITIVITY, globalDefault = 100),
            enableNativeMousePointer = bool(KEY_ENABLE_NATIVE_MOUSE_POINTER),
            enableDoubleClickDrag = bool(KEY_ENABLE_DOUBLE_CLICK_DRAG),
            doubleTapTimeThreshold = int(KEY_DOUBLE_TAP_TIME_THRESHOLD, globalDefault = 125),
            enableLocalCursorRendering = bool(KEY_ENABLE_LOCAL_CURSOR_RENDERING, globalDefault = true),
            nativeTouchFingersToToggleKeyboard = int(KEY_NATIVE_TOUCH_FINGERS_TOGGLE_KEYBOARD, globalDefault = 3),
            longPressFlatRegionPixels = int(KEY_LONG_PRESS_FLAT_REGION_PIXELS),
            syncTouchEventWithDisplay = bool(KEY_SYNC_TOUCH_EVENT_WITH_DISPLAY),

            // 显示设置
            width = parseWidth(string(KEY_RESOLUTION, globalDefault = "0x0")),
            height = parseHeight(string(KEY_RESOLUTION, globalDefault = "0x0")),
            fps = parseFps(string(KEY_FPS, globalDefault = "0")),
            bitrate = int(KEY_BITRATE),
            enableAdaptiveBitrate = bool(KEY_ADAPTIVE_BITRATE),
            abrMode = string(KEY_ABR_MODE, globalDefault = "balanced"),
            videoFormat = string(KEY_VIDEO_FORMAT, globalDefault = "auto"),
            enableHdr = bool(KEY_ENABLE_HDR),
            enableHdrHighBrightness = bool(KEY_ENABLE_HDR_HIGH_BRIGHTNESS),
            hdrMode = parseHdrMode(string(KEY_HDR_MODE, globalDefault = "1")),
            resolutionScale = int(KEY_RESOLUTION_SCALE, globalDefault = 100),
            framePacing = parseFramePacing(string(KEY_FRAME_PACING, globalDefault = "latency")),
            stretchVideo = bool(KEY_STRETCH_VIDEO),
            reverseResolution = bool(KEY_REVERSE_RESOLUTION),
            rotableScreen = bool(KEY_ROTABLE_SCREEN),
            outputBufferQueueLimit = int(KEY_OUTPUT_BUFFER_QUEUE_LIMIT, globalDefault = 2).coerceIn(1, 5),
            forceMtkMaxOperatingRate = bool(KEY_FORCE_MTK_MAX_OPERATING_RATE),
            unlockFps = bool(KEY_UNLOCK_FPS),
            enablePip = bool(KEY_ENABLE_PIP),
            useExternalDisplay = bool(KEY_USE_EXTERNAL_DISPLAY),
            screenPosition = string(KEY_SCREEN_POSITION, globalDefault = "center"),
            screenOffsetX = int(KEY_SCREEN_OFFSET_X),
            screenOffsetY = int(KEY_SCREEN_OFFSET_Y),
            reduceRefreshRate = bool(KEY_REDUCE_REFRESH_RATE),
            fullRange = bool(KEY_FULL_RANGE),
            screenCombinationMode = parseScreenCombinationMode(string(KEY_SCREEN_COMBINATION_MODE, globalDefault = "-1")),

            // 主机设置
            enableSops = bool(KEY_ENABLE_SOPS, globalDefault = true),
            lockScreenAfterDisconnect = bool(KEY_LOCK_SCREEN_AFTER_DISCONNECT),
            playHostAudio = bool(KEY_PLAY_HOST_AUDIO),
            muteClientAudio = bool(KEY_MUTE_CLIENT_AUDIO),
            controlOnly = bool(KEY_CONTROL_ONLY),
            enableClipboardSyncText = bool(KEY_CLIPBOARD_SYNC_TEXT),
            enableClipboardSyncImage = bool(KEY_CLIPBOARD_SYNC_IMAGE),
            enableEscMenu = bool(KEY_ENABLE_ESC_MENU, globalDefault = true),
            escMenuKey = parseKeyCode(string(KEY_ESC_MENU_KEY), KeyEvent.KEYCODE_ESCAPE),
            enableStartKeyMenu = bool(KEY_ENABLE_START_KEY_MENU, globalDefault = true),

            // 声音设置
            audioConfiguration = string(KEY_AUDIO_CONFIG, globalDefault = "2"),
            audioCodec = string(KEY_AUDIO_CODEC, globalDefault = "auto"),
            audioCodecBitrate = int(KEY_AUDIO_CODEC_BITRATE),
            enableAudioFx = bool(KEY_ENABLE_AUDIO_FX),
            enableSpatializer = bool(KEY_ENABLE_SPATIALIZER),
            enableAudioPassthrough = bool(KEY_ENABLE_AUDIO_PASSTHROUGH),
            audioPassthroughBuffer = string(KEY_AUDIO_PASSTHROUGH_BUFFER, globalDefault = "normal"),
            enableAudioVibration = bool(KEY_ENABLE_AUDIO_VIBRATION),
            audioVibrationStrength = int(KEY_AUDIO_VIBRATION_STRENGTH, globalDefault = 80).coerceIn(0, 200),
            audioVibrationMode = string(KEY_AUDIO_VIBRATION_MODE, globalDefault = "auto"),
            audioVibrationScene = parseAudioScene(string(KEY_AUDIO_VIBRATION_SCENE, globalDefault = "0")),
            enableMic = bool(KEY_ENABLE_MIC),
            micBitrate = int(KEY_MIC_BITRATE, globalDefault = 96).coerceIn(32, 256),
            micIconColor = string(KEY_MIC_ICON_COLOR, globalDefault = "solid_white"),

            // 体感
            gyroSensitivityMultiplier = if (sp.contains("gyro_sensitivity_multiplier"))
                sp.getFloat("gyro_sensitivity_multiplier", 1.0f)
            else
                globalSp.getFloat("gyro_sensitivity_multiplier", 1.0f),
            gyroInvertXAxis = bool(KEY_GYRO_INVERT_X_AXIS),
            gyroInvertYAxis = bool(KEY_GYRO_INVERT_Y_AXIS),
            gyroActivationKeyCode = parseKeyCode(string(KEY_GYRO_ACTIVATION_KEY_CODE), KeyEvent.KEYCODE_BUTTON_L2),
            showGyroCard = bool(KEY_SHOW_GYRO_CARD, globalDefault = true),

            // 其它
            enablePerfOverlay = bool(KEY_ENABLE_PERF_OVERLAY),
            perfOverlayLocked = bool(KEY_PERF_OVERLAY_LOCKED),
            perfOverlayBgOpacity = int(KEY_PERF_OVERLAY_BG_OPACITY, globalDefault = 53).coerceIn(0, 100),
            perfOverlayOrientation = string(KEY_PERF_OVERLAY_ORIENTATION, globalDefault = "horizontal"),
            perfOverlayPosition = string(KEY_PERF_OVERLAY_POSITION, globalDefault = "top"),
            enableSimplifyPerfOverlay = bool(KEY_ENABLE_SIMPLIFY_PERF_OVERLAY),
            enableLatencyToast = bool(KEY_ENABLE_LATENCY_TOAST),
            fabOpacity = int(KEY_FAB_OPACITY, globalDefault = 50).coerceIn(10, 100),
            toolPanelAutoHideMode = int(KEY_TOOL_PANEL_AUTO_HIDE_MODE, globalDefault = 2).coerceIn(0, 2),
            enableFloatBall = bool(KEY_ENABLE_FLOAT_BALL, globalDefault = true),
            floatBallAutoHideDelay = int(KEY_FLOAT_BALL_AUTO_HIDE_DELAY, globalDefault = 2000),
            floatBallSingleClickAction = string(KEY_FLOAT_BALL_SINGLE_CLICK, globalDefault = "open_keyboard"),
            floatBallDoubleClickAction = string(KEY_FLOAT_BALL_DOUBLE_CLICK, globalDefault = "open_menu"),
            floatBallLongClickAction = string(KEY_FLOAT_BALL_LONG_CLICK, globalDefault = "toggle_visibility"),
            floatBallSwipeUpAction = string(KEY_FLOAT_BALL_SWIPE_UP, globalDefault = "none"),
            floatBallSwipeDownAction = string(KEY_FLOAT_BALL_SWIPE_DOWN, globalDefault = "none"),
            floatBallSwipeLeftAction = string(KEY_FLOAT_BALL_SWIPE_LEFT, globalDefault = "none"),
            floatBallSwipeRightAction = string(KEY_FLOAT_BALL_SWIPE_RIGHT, globalDefault = "none"),
            showBitrateCard = bool(KEY_SHOW_BITRATE_CARD, globalDefault = true),
            showQuickKeyCard = bool(KEY_SHOW_QUICK_KEY_CARD, globalDefault = true),
            keyMappingEnabled = bool(KEY_KEY_MAPPING_ENABLED),
            disableWarnings = bool(KEY_DISABLE_WARNINGS),
        )
    }

    /**
     * 保存指定主机的配置。
     */
    fun saveSettings(uuid: String, settings: HostSettings) {
        val sp = context.getSharedPreferences("$SP_PREFIX$uuid", Context.MODE_PRIVATE)
        sp.edit()
            // 触控模式
            .putBoolean(KEY_ENABLE_ENHANCED_TOUCH, settings.enableEnhancedTouch)
            .putBoolean(KEY_ENHANCED_TOUCH_ON_RIGHT, settings.enhancedTouchOnWhichSide)
            .putInt(KEY_ENHANCE_TOUCH_ZONE_DIVIDER, settings.enhanceTouchZoneDivider)
            .putInt(KEY_POINTER_VELOCITY_FACTOR, settings.pointerVelocityFactor)
            .putBoolean(KEY_TOUCHSCREEN_TRACKPAD, settings.touchscreenTrackpad)
            .putInt(KEY_TOUCHPAD_SENSITIVITY, settings.touchpadSensitivity)
            .putBoolean(KEY_ENABLE_NATIVE_MOUSE_POINTER, settings.enableNativeMousePointer)
            .putBoolean(KEY_ENABLE_DOUBLE_CLICK_DRAG, settings.enableDoubleClickDrag)
            .putInt(KEY_DOUBLE_TAP_TIME_THRESHOLD, settings.doubleTapTimeThreshold)
            .putBoolean(KEY_ENABLE_LOCAL_CURSOR_RENDERING, settings.enableLocalCursorRendering)
            .putInt(KEY_NATIVE_TOUCH_FINGERS_TOGGLE_KEYBOARD, settings.nativeTouchFingersToToggleKeyboard)
            .putInt(KEY_LONG_PRESS_FLAT_REGION_PIXELS, settings.longPressFlatRegionPixels)
            .putBoolean(KEY_SYNC_TOUCH_EVENT_WITH_DISPLAY, settings.syncTouchEventWithDisplay)
            // 显示设置
            .putString(KEY_RESOLUTION, "${settings.width}x${settings.height}")
            .putString(KEY_FPS, settings.fps.toString())
            .putInt(KEY_BITRATE, settings.bitrate)
            .putBoolean(KEY_ADAPTIVE_BITRATE, settings.enableAdaptiveBitrate)
            .putString(KEY_ABR_MODE, settings.abrMode)
            .putString(KEY_VIDEO_FORMAT, settings.videoFormat)
            .putBoolean(KEY_ENABLE_HDR, settings.enableHdr)
            .putBoolean(KEY_ENABLE_HDR_HIGH_BRIGHTNESS, settings.enableHdrHighBrightness)
            .putString(KEY_HDR_MODE, settings.hdrMode.toString())
            .putInt(KEY_RESOLUTION_SCALE, settings.resolutionScale)
            .putString(KEY_FRAME_PACING, framePacingToString(settings.framePacing))
            .putBoolean(KEY_STRETCH_VIDEO, settings.stretchVideo)
            .putBoolean(KEY_REVERSE_RESOLUTION, settings.reverseResolution)
            .putBoolean(KEY_ROTABLE_SCREEN, settings.rotableScreen)
            .putInt(KEY_OUTPUT_BUFFER_QUEUE_LIMIT, settings.outputBufferQueueLimit)
            .putBoolean(KEY_FORCE_MTK_MAX_OPERATING_RATE, settings.forceMtkMaxOperatingRate)
            .putBoolean(KEY_UNLOCK_FPS, settings.unlockFps)
            .putBoolean(KEY_ENABLE_PIP, settings.enablePip)
            .putBoolean(KEY_USE_EXTERNAL_DISPLAY, settings.useExternalDisplay)
            .putString(KEY_SCREEN_POSITION, settings.screenPosition)
            .putInt(KEY_SCREEN_OFFSET_X, settings.screenOffsetX)
            .putInt(KEY_SCREEN_OFFSET_Y, settings.screenOffsetY)
            .putBoolean(KEY_REDUCE_REFRESH_RATE, settings.reduceRefreshRate)
            .putBoolean(KEY_FULL_RANGE, settings.fullRange)
            .putString(KEY_SCREEN_COMBINATION_MODE, settings.screenCombinationMode.toString())
            // 主机设置
            .putBoolean(KEY_ENABLE_SOPS, settings.enableSops)
            .putBoolean(KEY_LOCK_SCREEN_AFTER_DISCONNECT, settings.lockScreenAfterDisconnect)
            .putBoolean(KEY_PLAY_HOST_AUDIO, settings.playHostAudio)
            .putBoolean(KEY_MUTE_CLIENT_AUDIO, settings.muteClientAudio)
            .putBoolean(KEY_CONTROL_ONLY, settings.controlOnly)
            .putBoolean(KEY_CLIPBOARD_SYNC_TEXT, settings.enableClipboardSyncText)
            .putBoolean(KEY_CLIPBOARD_SYNC_IMAGE, settings.enableClipboardSyncImage)
            .putBoolean(KEY_ENABLE_ESC_MENU, settings.enableEscMenu)
            .putString(KEY_ESC_MENU_KEY, settings.escMenuKey.toString())
            .putBoolean(KEY_ENABLE_START_KEY_MENU, settings.enableStartKeyMenu)
            // 声音设置
            .putString(KEY_AUDIO_CONFIG, settings.audioConfiguration)
            .putString(KEY_AUDIO_CODEC, settings.audioCodec)
            .putInt(KEY_AUDIO_CODEC_BITRATE, settings.audioCodecBitrate)
            .putBoolean(KEY_ENABLE_AUDIO_FX, settings.enableAudioFx)
            .putBoolean(KEY_ENABLE_SPATIALIZER, settings.enableSpatializer)
            .putBoolean(KEY_ENABLE_AUDIO_PASSTHROUGH, settings.enableAudioPassthrough)
            .putString(KEY_AUDIO_PASSTHROUGH_BUFFER, settings.audioPassthroughBuffer)
            .putBoolean(KEY_ENABLE_AUDIO_VIBRATION, settings.enableAudioVibration)
            .putInt(KEY_AUDIO_VIBRATION_STRENGTH, settings.audioVibrationStrength)
            .putString(KEY_AUDIO_VIBRATION_MODE, settings.audioVibrationMode)
            .putString(KEY_AUDIO_VIBRATION_SCENE, settings.audioVibrationScene.toString())
            .putBoolean(KEY_ENABLE_MIC, settings.enableMic)
            .putInt(KEY_MIC_BITRATE, settings.micBitrate)
            .putString(KEY_MIC_ICON_COLOR, settings.micIconColor)
            // 体感
            .putFloat(KEY_GYRO_SENSITIVITY_MULTIPLIER, settings.gyroSensitivityMultiplier)
            .putBoolean(KEY_GYRO_INVERT_X_AXIS, settings.gyroInvertXAxis)
            .putBoolean(KEY_GYRO_INVERT_Y_AXIS, settings.gyroInvertYAxis)
            .putInt(KEY_GYRO_ACTIVATION_KEY_CODE, settings.gyroActivationKeyCode)
            .putBoolean(KEY_SHOW_GYRO_CARD, settings.showGyroCard)
            // 其它
            .putBoolean(KEY_ENABLE_PERF_OVERLAY, settings.enablePerfOverlay)
            .putBoolean(KEY_PERF_OVERLAY_LOCKED, settings.perfOverlayLocked)
            .putInt(KEY_PERF_OVERLAY_BG_OPACITY, settings.perfOverlayBgOpacity)
            .putString(KEY_PERF_OVERLAY_ORIENTATION, settings.perfOverlayOrientation)
            .putString(KEY_PERF_OVERLAY_POSITION, settings.perfOverlayPosition)
            .putBoolean(KEY_ENABLE_SIMPLIFY_PERF_OVERLAY, settings.enableSimplifyPerfOverlay)
            .putBoolean(KEY_ENABLE_LATENCY_TOAST, settings.enableLatencyToast)
            .putInt(KEY_FAB_OPACITY, settings.fabOpacity)
            .putInt(KEY_TOOL_PANEL_AUTO_HIDE_MODE, settings.toolPanelAutoHideMode)
            .putBoolean(KEY_ENABLE_FLOAT_BALL, settings.enableFloatBall)
            .putInt(KEY_FLOAT_BALL_AUTO_HIDE_DELAY, settings.floatBallAutoHideDelay)
            .putString(KEY_FLOAT_BALL_SINGLE_CLICK, settings.floatBallSingleClickAction)
            .putString(KEY_FLOAT_BALL_DOUBLE_CLICK, settings.floatBallDoubleClickAction)
            .putString(KEY_FLOAT_BALL_LONG_CLICK, settings.floatBallLongClickAction)
            .putString(KEY_FLOAT_BALL_SWIPE_UP, settings.floatBallSwipeUpAction)
            .putString(KEY_FLOAT_BALL_SWIPE_DOWN, settings.floatBallSwipeDownAction)
            .putString(KEY_FLOAT_BALL_SWIPE_LEFT, settings.floatBallSwipeLeftAction)
            .putString(KEY_FLOAT_BALL_SWIPE_RIGHT, settings.floatBallSwipeRightAction)
            .putBoolean(KEY_SHOW_BITRATE_CARD, settings.showBitrateCard)
            .putBoolean(KEY_SHOW_QUICK_KEY_CARD, settings.showQuickKeyCard)
            .putBoolean(KEY_KEY_MAPPING_ENABLED, settings.keyMappingEnabled)
            .putBoolean(KEY_DISABLE_WARNINGS, settings.disableWarnings)
            .apply()
    }

    /**
     * 删除指定主机的配置（删除主机时调用）。
     */
    fun deleteSettings(uuid: String) {
        val sp = context.getSharedPreferences("$SP_PREFIX$uuid", Context.MODE_PRIVATE)
        sp.edit().clear().apply()
    }

    // ── 解析辅助 ──

    private fun parseWidth(res: String): Int = try {
        res.split("x")[0].toInt()
    } catch (_: Exception) { 1920 }

    private fun parseHeight(res: String): Int = try {
        res.split("x")[1].toInt()
    } catch (_: Exception) { 1080 }

    private fun parseFps(fps: String): Int = try {
        fps.toInt()
    } catch (_: Exception) { 60 }

    private fun parseHdrMode(mode: String): Int = try {
        mode.toInt()
    } catch (_: Exception) { 1 }

    private fun parseAudioScene(scene: String): Int = try {
        scene.toInt()
    } catch (_: Exception) { 0 }

    private fun parseKeyCode(value: String?, default: Int): Int = try {
        value?.toInt() ?: default
    } catch (_: Exception) { default }

    private fun parseScreenCombinationMode(value: String): Int = try {
        value.toInt()
    } catch (_: Exception) { -1 }

    private fun parseFramePacing(value: String): Int = when (value) {
        "latency" -> 0
        "balanced" -> 1
        "cap-fps" -> 2
        "smoothness" -> 3
        "experimental-low-latency" -> 4
        "precise-sync" -> 5
        else -> 0
    }

    private fun framePacingToString(value: Int): String = when (value) {
        0 -> "latency"
        1 -> "balanced"
        2 -> "cap-fps"
        3 -> "smoothness"
        4 -> "experimental-low-latency"
        5 -> "precise-sync"
        else -> "latency"
    }
}
