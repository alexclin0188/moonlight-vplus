package com.alexclin.moonlink.android.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                CheckBoxPreference(key = "checkbox_resume_stream", title = "自动恢复串流")
            }
            item {
                CheckBoxPreference(key = "checkbox_extreme_resume", title = "不断开连接", dependency = "checkbox_resume_stream")
            }
            item {
                CheckBoxPreference(key = "checkbox_background_audio", title = "后台播放音频", dependency = "checkbox_extreme_resume")
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_stun", title = "获取公网 IP（STUN）")
            }
        }
}
