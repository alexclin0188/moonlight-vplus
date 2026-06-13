package com.alexclin.moonlink.navigation

/**
 * Type-safe navigation destinations for the MoonLink Compose navigation graph.
 */
sealed class MoonLinkRoute(val route: String) {

    /** 主页 — 3 Tab 设备列表 */
    data object DeviceList : MoonLinkRoute("device_list")

    /** 设备概要页 — 需要 uuid 参数 */
    data object DeviceOverview : MoonLinkRoute("device_overview/{uuid}") {
        const val ARG_UUID = "uuid"
        fun createRoute(uuid: String) = "device_overview/$uuid"
    }

    /** 设备详情页 — 需要 uuid 参数 */
    data object DeviceDetail : MoonLinkRoute("device_detail/{uuid}") {
        const val ARG_UUID = "uuid"
        fun createRoute(uuid: String) = "device_detail/$uuid"
    }

    // ── Settings sub-pages ─────────────────────────────────────────

    data object SettingsUi : MoonLinkRoute("settings_ui")
    data object SettingsAudio : MoonLinkRoute("settings_audio")
    data object SettingsGamepad : MoonLinkRoute("settings_gamepad")
    data object SettingsInput : MoonLinkRoute("settings_input")
    data object SettingsMultitouch : MoonLinkRoute("settings_multitouch")
    data object SettingsConnection : MoonLinkRoute("settings_connection")
    data object SettingsScene : MoonLinkRoute("settings_scene")
    data object SettingsKeyMapping : MoonLinkRoute("settings_keymapping")
    data object SettingsHelp : MoonLinkRoute("settings_help")

    /** 桌面小组件管理 */
    data object SettingsWidget : MoonLinkRoute("settings_widget")

    /** 性能与统计分析 */
    data object SettingsPerformance : MoonLinkRoute("settings_performance")
}
