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
fun GamepadSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SeekBarPreference(key = "seekbar_deadzone", title = "摇杆死区", min = 0, max = 20, step = 1, defaultValue = 7)
            }
            item {
                CheckBoxPreference(key = "checkbox_multi_controller", title = "支持多个手柄", defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_mouse_emulation", title = "手柄鼠标模拟", defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_start_key_menu", title = "开始键打开菜单", defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_usb_driver", title = "USB 手柄驱动", defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_usb_bind_all", title = "USB 手柄绑定全部", defaultValue = false, dependency = "checkbox_usb_driver")
            }
            item {
                ListPreference(
                    key = "analog_scrolling",
                    title = "模拟摇杆滚动",
                    entries = listOf("右摇杆" to "right", "左摇杆" to "left", "禁用" to "disabled"),
                    defaultValue = "right",
                    dependency = "checkbox_mouse_emulation",
                )
            }
            item {
                CheckBoxPreference(key = "checkbox_vibrate_fallback", title = "震动回退")
            }
            item {
                SeekBarPreference(key = "seekbar_vibrate_fallback_strength", title = "震动回退强度", min = 0, max = 200, step = 1, defaultValue = 100, dependency = "checkbox_vibrate_fallback")
            }
            item {
                CheckBoxPreference(key = "checkbox_flip_face_buttons", title = "翻转 ABXY")
            }
            item {
                CheckBoxPreference(key = "checkbox_gamepad_touchpad_as_mouse", title = "手柄触摸板作为鼠标")
            }
            item {
                CheckBoxPreference(key = "checkbox_gamepad_motion_sensors", title = "手柄体感", defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_gamepad_motion_fallback", title = "手柄体感回退")
            }
        }
}
