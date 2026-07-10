package com.alexclin.moonlink.android.settings

import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.settings.PerfOverlayDisplayItemsPreference

@Composable
fun PerformanceSettingsScreen() {
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 1. 启用统计分析
            item {
                CheckBoxPreference(
                    key = "checkbox_enable_analytics",
                    title = stringResource(R.string.perf_title_enable_analytics),
                    summary = stringResource(R.string.perf_summary_enable_analytics),
                    defaultValue = true,
                )
            }
            // 2. 禁用错误提示
            item {
                CheckBoxPreference(
                    key = "checkbox_disable_warnings",
                    title = stringResource(R.string.perf_title_disable_warnings),
                    summary = stringResource(R.string.perf_summary_disable_warnings),
                )
            }
            // 3. 性能图层位置
            item {
                ListPreference(
                    key = "list_perf_overlay_position",
                    title = stringResource(R.string.perf_title_overlay_position),
                    entries = listOf(
                        stringResource(R.string.perf_option_top) to "top",
                        stringResource(R.string.perf_option_bottom) to "bottom",
                        stringResource(R.string.perf_option_top_left) to "top_left",
                        stringResource(R.string.perf_option_top_right) to "top_right",
                        stringResource(R.string.perf_option_bottom_left) to "bottom_left",
                        stringResource(R.string.perf_option_bottom_right) to "bottom_right",
                    ),
                    defaultValue = "top",
                )
            }
            // 4. 性能图层显示项目
            item {
                MultiSelectPreference(
                    key = "perf_overlay_display_items",
                    title = stringResource(R.string.perf_title_display_items),
                    summary = stringResource(R.string.perf_summary_display_items),
                    items = listOf(
                        stringResource(R.string.perf_item_resolution) to "resolution",
                        stringResource(R.string.perf_item_decoder) to "decoder",
                        stringResource(R.string.perf_item_render_fps) to "render_fps",
                        stringResource(R.string.perf_item_packet_loss) to "packet_loss",
                        stringResource(R.string.perf_item_network_latency) to "network_latency",
                        stringResource(R.string.perf_item_decode_latency) to "decode_latency",
                        stringResource(R.string.perf_item_host_latency) to "host_latency",
                        stringResource(R.string.perf_item_battery) to "battery",
                        stringResource(R.string.perf_item_one_percent_low) to "one_percent_low",
                    ),
                    defaultValues = PerfOverlayDisplayItemsPreference.getDefaultDisplayItems(),
                )
            }
            // 5. 性能图层显示不透明度
            item {
                SeekBarPreference(
                    key = "seekbar_perf_overlay_opacity",
                    title = stringResource(R.string.perf_title_overlay_opacity),
                    summary = stringResource(R.string.perf_summary_overlay_opacity),
                    min = 30,
                    max = 100,
                    step = 5,
                    defaultValue = 80,
                    unit = "%",
                )
            }
        }
}
