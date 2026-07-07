package com.alexclin.moonlink.android.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.R

@Composable
fun InputSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SeekBarPreference(key = "seekbar_double_tap_time_threshold", title = stringResource(R.string.input_title_double_tap), min = 25, max = 1000, step = 25, defaultValue = 125, unit = "ms")
            }
            item {
                CheckBoxPreference(key = "checkbox_special_key_map", title = stringResource(R.string.input_title_special_keys))
            }
            item {
                CheckBoxPreference(key = "checkbox_mouse_middle", title = stringResource(R.string.input_title_fix_middle), defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_mouse_wheel", title = stringResource(R.string.input_title_fix_wheel), defaultValue = true)
            }
            item {
                CheckBoxPreference(key = "checkbox_sync_touch_event_with_display", title = stringResource(R.string.input_title_sync_touch))
            }
            item {
                Text(
                    stringResource(R.string.input_hint_global_keyboard),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_keyboard_toggle_in_native_touch", title = stringResource(R.string.input_title_keyboard_toggle), defaultValue = true)
            }
            item {
                SeekBarPreference(key = "seekbar_keyboard_toggle_fingers_native_touch", title = stringResource(R.string.input_title_tap_fingers), min = 3, max = 10, step = 1, defaultValue = 3, dependency = "checkbox_enable_keyboard_toggle_in_native_touch")
            }
            item {
                CheckBoxPreference(key = "checkbox_mouse_nav_buttons", title = stringResource(R.string.input_title_mouse_nav))
            }
            item {
                CheckBoxPreference(key = "checkbox_absolute_mouse_mode", title = stringResource(R.string.input_title_absolute_mouse))
            }
            item {
                CheckBoxPreference(key = "checkbox_enable_esc_menu", title = stringResource(R.string.input_title_esc_menu), defaultValue = true)
            }
            item {
                ListPreference(
                    key = "list_esc_menu_key",
                    title = stringResource(R.string.input_title_esc_menu_key),
                    entries = listOf(stringResource(R.string.input_option_back_key) to "111", stringResource(R.string.input_option_other_keys) to "other"),
                    defaultValue = "111",
                    dependency = "checkbox_enable_esc_menu",
                )
            }
        }
}
