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
fun InputSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SeekBarPreference(key = "seekbar_double_tap_time_threshold", title = "双击时间阈值", min = 25, max = 1000, step = 25, defaultValue = 125, unit = "ms")
            }
            item {
                CheckBoxPreference(key = "checkbox_special_key_map", title = "特殊按键映射")
            }
            item {
                CheckBoxPreference(key = "checkbox_mouse_middle", title = "修复鼠标中键", defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_mouse_wheel", title = "修复本地鼠标滚轮", defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_sync_touch_event_with_display", title = "触控事件与显示刷新同步")
            }
            item {
                Text(
                    "以下键盘切换设置为全局默认值，可在\"设备串流设置 → 触控模式\"中为每台主机单独配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_keyboard_toggle_in_native_touch", title = "多点触控模式下键盘切换", defaultValue = true)
            }
            item {
                SeekBarPreference(key = "seekbar_keyboard_toggle_fingers_native_touch", title = "轻敲手指数量", min = 3, max = 10, step = 1, defaultValue = 3, dependency = "checkbox_enable_keyboard_toggle_in_native_touch")
            }
            item {
                CheckBoxPreference(key = "checkbox_mouse_nav_buttons", title = "启用前进后退鼠标键")
            }
            item {
                CheckBoxPreference(key = "checkbox_absolute_mouse_mode", title = "适合远程桌面的鼠标模式")
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_esc_menu", title = "允许自定义键打开返回菜单", defaultValue = true)
            }
            item {
                ListPreference(
                    key = "list_esc_menu_key",
                    title = "返回菜单激活按键",
                    entries = listOf("返回键" to "111", "其他按键" to "other"),
                    defaultValue = "111",
                    dependency = "checkbox_enable_esc_menu",
                )
            }
        }
}
