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
            // 3. 性能监控图层
            item {
                CheckBoxPreference(
                    key = "checkbox_enable_perf_overlay",
                    title = stringResource(R.string.perf_title_enable_overlay),
                    summary = stringResource(R.string.perf_summary_enable_overlay),
                )
            }
            // 4. 性能图层方向
            item {
                ListPreference(
                    key = "list_perf_overlay_orientation",
                    title = stringResource(R.string.perf_title_overlay_orientation),
                    entries = listOf(
                        stringResource(R.string.perf_option_horizontal) to "horizontal",
                        stringResource(R.string.perf_option_vertical) to "vertical",
                    ),
                    defaultValue = "horizontal",
                    dependency = "checkbox_enable_perf_overlay",
                )
            }
            // 5. 性能图层位置
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
                    dependency = "checkbox_enable_perf_overlay",
                )
            }
            // 6. 性能图层显示项目
            item {
                MultiSelectPreference(
                    key = "perf_overlay_display_items",
                    title = stringResource(R.string.perf_title_display_items),
                    summary = stringResource(R.string.perf_summary_display_items),
                    items = listOf(
                        "🎬 Resolution & Target FPS" to "resolution",
                        "Codec Decoder Info" to "decoder",
                        "Rx/Rd Received & Rendered FPS" to "render_fps",
                        "📶 Packet Loss" to "packet_loss",
                        "🌐 Bitrate & Network Latency" to "network_latency",
                        "⏱️/🥵 Decode Latency" to "decode_latency",
                        "🖥 Host Latency" to "host_latency",
                        "🔋 Battery Level" to "battery",
                        "📉 1% Low FPS (Smoothness)" to "one_percent_low",
                    ),
                    defaultValues = PerfOverlayDisplayItemsPreference.getDefaultDisplayItems(),
                    dependency = "checkbox_enable_perf_overlay",
                )
            }
            // 7. 性能图层背景不透明度
            item {
                SeekBarPreference(
                    key = "seekbar_perf_overlay_bg_opacity",
                    title = stringResource(R.string.perf_title_bg_opacity),
                    min = 0, max = 100, step = 5, defaultValue = 53,
                    unit = "%",
                    dependency = "checkbox_enable_perf_overlay",
                )
            }
            // 8. 串流完毕显示延迟信息
            item {
                CheckBoxPreference(
                    key = "checkbox_enable_post_stream_toast",
                    title = stringResource(R.string.perf_title_post_stream_toast),
                    summary = stringResource(R.string.perf_summary_post_stream_toast),
                )
            }
        }
}
