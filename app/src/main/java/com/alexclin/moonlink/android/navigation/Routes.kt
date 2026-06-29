package com.alexclin.moonlink.android.navigation

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

    /** 串流设置页 — 需要 uuid 参数 */
    data object DeviceStreamSettings : MoonLinkRoute("device_stream_settings/{uuid}") {
        const val ARG_UUID = "uuid"
        fun createRoute(uuid: String) = "device_stream_settings/$uuid"
        /** 子页路由模板：/touch /display /host /audio /gyro /other */
        fun subRoute(uuid: String, category: String) = "device_stream_settings/$uuid/$category"
    }

    /** 串流设置 → 触控模式子页 */
    data object DeviceStreamSettingsTouch : MoonLinkRoute("device_stream_settings/{uuid}/touch") {
        const val ARG_UUID = "uuid"
    }
    /** 串流设置 → 显示设置子页 */
    data object DeviceStreamSettingsDisplay : MoonLinkRoute("device_stream_settings/{uuid}/display") {
        const val ARG_UUID = "uuid"
    }
    /** 串流设置 → 主机设置子页 */
    data object DeviceStreamSettingsHost : MoonLinkRoute("device_stream_settings/{uuid}/host") {
        const val ARG_UUID = "uuid"
    }
    /** 串流设置 → 声音设置子页 */
    data object DeviceStreamSettingsAudio : MoonLinkRoute("device_stream_settings/{uuid}/audio") {
        const val ARG_UUID = "uuid"
    }
    /** 串流设置 → 体感子页 */
    data object DeviceStreamSettingsGyro : MoonLinkRoute("device_stream_settings/{uuid}/gyro") {
        const val ARG_UUID = "uuid"
    }
    /** 串流设置 → 画面开关子页 */
    data object DeviceStreamSettingsSwitches : MoonLinkRoute("device_stream_settings/{uuid}/switches") {
        const val ARG_UUID = "uuid"
    }
    /** 串流设置 → 其它设置子页 */
    data object DeviceStreamSettingsOther : MoonLinkRoute("device_stream_settings/{uuid}/other") {
        const val ARG_UUID = "uuid"
    }

    // ── Settings sub-pages ─────────────────────────────────────────

    data object SettingsUi : MoonLinkRoute("settings_ui")
    data object SettingsGamepad : MoonLinkRoute("settings_gamepad")
    data object SettingsInput : MoonLinkRoute("settings_input")
    data object SettingsKeyMapping : MoonLinkRoute("settings_keymapping")
    data object SettingsHelp : MoonLinkRoute("settings_help")

    /** 桌面小组件管理 */
    data object SettingsWidget : MoonLinkRoute("settings_widget")

    /** 性能与统计分析 */
    data object SettingsPerformance : MoonLinkRoute("settings_performance")

    /** 连接设置 */
    data object SettingsConnection : MoonLinkRoute("settings_connection")
}
