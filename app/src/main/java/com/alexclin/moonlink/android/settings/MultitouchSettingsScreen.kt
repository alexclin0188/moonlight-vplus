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
fun MultitouchSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SeekBarPreference(key = "seekbar_flat_region_pixels", title = "多点触控长按抖动消除区域", min = 0, max = 250, step = 1, defaultValue = 0, unit = "px")
            }
            item {
                CheckBoxPreference(key = "checkbox_enhanced_touch_on_which_side", title = "反转增强型触控区")
            }
            item {
                SeekBarPreference(key = "enhanced_touch_zone_divider", title = "触点灵敏度分区调控", min = 0, max = 100, step = 1, defaultValue = 50)
            }
            item {
                SeekBarPreference(key = "pointer_velocity_factor", title = "触点灵敏度", min = 0, max = 500, step = 1, defaultValue = 100)
            }
        }
}
