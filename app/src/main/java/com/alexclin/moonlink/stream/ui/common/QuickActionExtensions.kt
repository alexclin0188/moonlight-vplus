package com.alexclin.moonlink.stream.ui.common

import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HdrOff
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.limelight.QuickActionRegistry

object MoonLinkQuickActions {
    const val TOGGLE_PIP = "toggle_pip"
    const val TOGGLE_ADAPTIVE_BITRATE = "toggle_adaptive_bitrate"
    const val TOGGLE_CONTROL_ONLY = "toggle_control_only"
    const val TOGGLE_GYRO = "toggle_gyro"
}

fun getActionIcon(id: String, isActive: Boolean? = null): ImageVector? {
    return when (id) {
        "send_win" -> Icons.Default.Layers
        "send_esc" -> Icons.Default.Adjust
        "toggle_hdr" -> if (isActive == true) Icons.Default.HdrOn else Icons.Default.HdrOff
        "toggle_audio" -> if (isActive == true) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff
        "toggle_mic" -> Icons.Default.Mic
        "send_sleep" -> Icons.Default.NightsStay
        "send_tab" -> Icons.Default.Tab
        "send_alt_tab" -> Icons.Default.Tab
        "send_alt_f4" -> Icons.Default.Mouse
        "toggle_keyboard" -> Icons.Default.VideogameAsset
        "toggle_controller" -> Icons.Default.SportsEsports
        "toggle_perf" -> Icons.Default.Tune
        MoonLinkQuickActions.TOGGLE_PIP -> Icons.Default.Layers
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE -> Icons.Default.GraphicEq
        MoonLinkQuickActions.TOGGLE_CONTROL_ONLY -> Icons.Default.SettingsRemote
        MoonLinkQuickActions.TOGGLE_GYRO -> Icons.Default.Sensors
        else -> null
    }
}

fun getActionLabel(id: String): String {
    return when (id) {
        "send_win" -> "Win"
        "send_esc" -> "ESC"
        "toggle_hdr" -> "HDR"
        "toggle_audio" -> "声音"
        "toggle_mic" -> "麦克风"
        "send_sleep" -> "睡眠"
        "send_tab" -> "Tab"
        "send_alt_tab" -> "Alt+Tab"
        "send_alt_f4" -> "Alt+F4"
        "toggle_keyboard" -> "键盘"
        "toggle_controller" -> "手柄"
        "toggle_perf" -> "性能"
        MoonLinkQuickActions.TOGGLE_PIP -> "画中画"
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE -> "自适应码率"
        MoonLinkQuickActions.TOGGLE_CONTROL_ONLY -> "纯控制"
        MoonLinkQuickActions.TOGGLE_GYRO -> "体感"
        else -> id
    }
}

fun getAllActionIds(): List<String> {
    val builtinIds = QuickActionRegistry.DEFAULT_IDS.toList()
    val moonlinkIds = listOf(
        MoonLinkQuickActions.TOGGLE_PIP,
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE,
        MoonLinkQuickActions.TOGGLE_CONTROL_ONLY,
        MoonLinkQuickActions.TOGGLE_GYRO,
    )
    return builtinIds + moonlinkIds
}
