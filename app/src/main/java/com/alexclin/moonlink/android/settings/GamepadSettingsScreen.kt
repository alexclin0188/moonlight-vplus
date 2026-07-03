package com.alexclin.moonlink.android.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R

@Composable
fun GamepadSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SeekBarPreference(
                key = "seekbar_deadzone",
                title = stringResource(R.string.title_seekbar_deadzone),
                min = 0, max = 20, step = 1, defaultValue = 7,
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_multi_controller",
                title = stringResource(R.string.title_checkbox_multi_controller),
                defaultValue = true,
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_mouse_emulation",
                title = stringResource(R.string.title_checkbox_mouse_emulation),
                defaultValue = true,
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_enable_start_key_menu",
                title = stringResource(R.string.title_checkbox_enable_start_key_menu),
                defaultValue = true,
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_usb_driver",
                title = stringResource(R.string.title_checkbox_xb1_driver),
                defaultValue = true,
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_usb_bind_all",
                title = stringResource(R.string.title_checkbox_usb_bind_all),
                defaultValue = false,
                dependency = "checkbox_usb_driver",
            )
        }
        item {
            ListPreference(
                key = "analog_scrolling",
                title = stringResource(R.string.title_analog_scrolling),
                entries = listOf(
                    stringResource(R.string.analogscroll_right) to "right",
                    stringResource(R.string.analogscroll_left) to "left",
                    stringResource(R.string.analogscroll_none) to "disabled",
                ),
                defaultValue = "right",
                dependency = "checkbox_mouse_emulation",
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_vibrate_fallback",
                title = stringResource(R.string.title_checkbox_vibrate_fallback),
            )
        }
        item {
            SeekBarPreference(
                key = "seekbar_vibrate_fallback_strength",
                title = stringResource(R.string.title_seekbar_vibrate_fallback_strength),
                min = 0, max = 200, step = 1, defaultValue = 100,
                dependency = "checkbox_vibrate_fallback",
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_flip_face_buttons",
                title = stringResource(R.string.title_checkbox_flip_face_buttons),
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_gamepad_touchpad_as_mouse",
                title = stringResource(R.string.title_checkbox_gamepad_touchpad_as_mouse),
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_gamepad_motion_sensors",
                title = stringResource(R.string.title_checkbox_gamepad_motion_sensors),
                defaultValue = true,
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_gamepad_motion_fallback",
                title = stringResource(R.string.title_checkbox_gamepad_motion_fallback),
            )
        }
    }
}
