package com.alexclin.moonlink.android.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 连接设置页面 — 串流连接相关配置项。
 *
 * 包含 4 个配置项：
 * - 自动恢复串流（checkbox_resume_stream）
 * - 不断开连接（checkbox_extreme_resume，依赖自动恢复串流）
 * - 后台播放音频（checkbox_background_audio，依赖不断开连接）
 * - 获取公网 IP（checkbox_enable_stun，独立）
 */
@Composable
fun ConnectionSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ── 分类标题 ──
        item {
            CategoryHeader(title = "串流连接")
        }

        // 1. 自动恢复串流
        item {
            CheckBoxPreference(
                key = "checkbox_resume_stream",
                title = "自动恢复串流",
                summary = "当从后台切回MoonLink时，自动恢复串流会话。",
                defaultValue = false,
            )
        }

        // 2. 不断开连接（依赖自动恢复串流）
        item {
            CheckBoxPreference(
                key = "checkbox_extreme_resume",
                title = "不断开连接",
                summary = "切换应用或锁屏时保持连接不断开，回到moonlight时可瞬间恢复。注意：后台挂起时仍会消耗流量和电量。",
                defaultValue = false,
                dependency = "checkbox_resume_stream",
            )
        }

        // 3. 后台播放音频（依赖不断开连接）
        item {
            CheckBoxPreference(
                key = "checkbox_background_audio",
                title = "后台播放音频",
                summary = "启用不断开连接后，切换到后台时是否需要播放音频。",
                defaultValue = false,
                dependency = "checkbox_extreme_resume",
            )
        }

        // 4. 获取公网 IP（独立）
        item {
            CheckBoxPreference(
                key = "checkbox_enable_stun",
                title = "获取公网 IP",
                summary = "这会尝试利用 STUN 服务器获取被控端的公网访问方式，以便在离开局域网环境后继续连接。但此功能可能会导致无公网 IP 用户扫描变慢。",
                defaultValue = false,
            )
        }

        // 底部留白
        item {
            HorizontalDivider(modifier = Modifier.fillMaxSize())
        }
    }
}
