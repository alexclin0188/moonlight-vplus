package com.alexclin.moonlink.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AudioSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListPreference(
                    key = "list_audio_config",
                    title = "环绕声",
                    entries = listOf("立体声" to "0", "5.1 环绕声" to "2", "7.1 环绕声" to "4"),
                    defaultValue = "2",
                )
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_audiofx", title = "均衡器")
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_spatializer", title = "空间音频")
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_audio_passthrough", title = "音频直通", summary = "通过数字接口传输音频")
            }
            item {
                ListPreference(
                    key = "list_audio_codec",
                    title = "直通编解码器",
                    entries = listOf("自动" to "auto", "DTS" to "dts", "Dolby" to "dolby"),
                    defaultValue = "auto",
                    dependency = "checkbox_enable_audio_passthrough",
                )
            }
            item {
                ListPreference(
                    key = "list_audio_passthrough_buffer",
                    title = "直通缓冲区",
                    entries = listOf("低" to "low", "正常" to "normal", "高" to "high"),
                    defaultValue = "normal",
                    dependency = "checkbox_enable_audio_passthrough",
                )
            }
            item {
                CheckBoxPreference(key = "checkbox_audio_vibration", title = "音频驱动振动", summary = "低频能量驱动触觉反馈")
            }
            item {
                SeekBarPreference(
                    key = "seekbar_audio_vibration_strength",
                    title = "振动强度",
                    min = 0, max = 200, step = 1, defaultValue = 80,
                    dependency = "checkbox_audio_vibration",
                )
            }
            item {
                ListPreference(
                    key = "list_audio_vibration_mode",
                    title = "振动路由",
                    entries = listOf("自动" to "auto", "仅扬声器" to "speaker", "仅耳机" to "headset"),
                    defaultValue = "auto",
                    dependency = "checkbox_audio_vibration",
                )
            }
            item {
                ListPreference(
                    key = "list_audio_vibration_scene",
                    title = "场景模式",
                    entries = listOf("通用" to "0", "游戏" to "1", "电影" to "2", "音乐" to "3"),
                    defaultValue = "0",
                    dependency = "checkbox_audio_vibration",
                )
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_mic", title = "麦克风重定向", summary = "远程语音通话")
            }
            item {
                SeekBarPreference(
                    key = "seekbar_mic_bitrate_kbps",
                    title = "麦克风传输音质",
                    min = 32, max = 256, step = 8, defaultValue = 64,
                    unit = "kbps",
                    dependency = "checkbox_enable_mic",
                )
            }
            item {
                ListPreference(
                    key = "list_mic_icon_color",
                    title = "麦克风图标颜色",
                    entries = listOf("纯白" to "solid_white", "强调色" to "accent"),
                    defaultValue = "solid_white",
                    dependency = "checkbox_enable_mic",
                )
            }
        }
}
