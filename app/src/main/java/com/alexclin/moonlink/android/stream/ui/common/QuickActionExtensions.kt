package com.alexclin.moonlink.android.stream.ui.common

import com.alexclin.moonlink.android.R
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
import com.alexclin.moonlink.android.util.QuickActionRegistry

object MoonLinkQuickActions {
    const val TOGGLE_PIP = "toggle_pip"
    const val TOGGLE_ADAPTIVE_BITRATE = "toggle_adaptive_bitrate"
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
        MoonLinkQuickActions.TOGGLE_GYRO -> Icons.Default.Sensors
        else -> null
    }
}

fun getActionLabelResId(id: String): Int {
    return when (id) {
        "send_win" -> R.string.qa_label_win
        "send_esc" -> R.string.qa_label_esc
        "toggle_hdr" -> R.string.display_hdr
        "toggle_audio" -> R.string.qa_label_audio
        "toggle_mic" -> R.string.qa_label_mic
        "send_sleep" -> R.string.action_sleep
        "send_tab" -> R.string.qa_label_tab
        "send_alt_tab" -> R.string.qa_label_alt_tab
        "send_alt_f4" -> R.string.qa_label_alt_f4
        "toggle_keyboard" -> R.string.bar_keyboard
        "toggle_controller" -> R.string.qa_label_controller
        "toggle_perf" -> R.string.qa_label_performance
        MoonLinkQuickActions.TOGGLE_PIP -> R.string.qa_label_pip
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE -> R.string.qa_label_adaptive_bitrate
        MoonLinkQuickActions.TOGGLE_GYRO -> R.string.subpanel_title_gyro
        else -> 0
    }
}

/**
 * 获取快捷操作在快捷行中显示的简写 label 资源 ID。
 * 与 [getActionLabelResId] 不同，此函数返回适合 56dp 窄按钮的短文本。
 */
fun getActionShortLabelResId(id: String): Int {
    return when (id) {
        "toggle_keyboard" -> R.string.quick_btn_keyboard
        "toggle_controller" -> R.string.quick_btn_controller
        "toggle_perf" -> R.string.quick_btn_perf
        "toggle_adaptive_bitrate" -> R.string.qa_label_short_adaptive_bitrate
        "toggle_gyro" -> R.string.qa_label_short_gyro
        else -> getActionLabelResId(id) // 其余项简写=全称，复用
    }
}

fun getAllActionIds(): List<String> {
    val builtinIds = QuickActionRegistry.DEFAULT_IDS.toList()
    val moonlinkIds = listOf(
        MoonLinkQuickActions.TOGGLE_PIP,
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE,
        MoonLinkQuickActions.TOGGLE_GYRO,
    )
    return builtinIds + moonlinkIds
}
