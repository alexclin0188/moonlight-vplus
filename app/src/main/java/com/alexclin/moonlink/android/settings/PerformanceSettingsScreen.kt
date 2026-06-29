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
import androidx.compose.ui.unit.dp
import com.limelight.preferences.PerfOverlayDisplayItemsPreference

@Composable
fun PerformanceSettingsScreen() {
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 1. 启用统计分析
            item {
                CheckBoxPreference(
                    key = "checkbox_enable_analytics",
                    title = "启用统计分析",
                    summary = "帮助改进应用体验",
                    defaultValue = true,
                )
            }
            // 2. 禁用错误提示
            item {
                CheckBoxPreference(
                    key = "checkbox_disable_warnings",
                    title = "禁用错误提示",
                    summary = "不显示错误提示信息",
                )
            }
            // 3. 性能监控图层
            item {
                CheckBoxPreference(
                    key = "checkbox_enable_perf_overlay",
                    title = "性能监控图层",
                    summary = "在串流时显示性能监控信息",
                )
            }
            // 4. 性能图层方向
            item {
                ListPreference(
                    key = "list_perf_overlay_orientation",
                    title = "性能图层方向",
                    entries = listOf(
                        "水平" to "horizontal",
                        "垂直" to "vertical",
                    ),
                    defaultValue = "horizontal",
                    dependency = "checkbox_enable_perf_overlay",
                )
            }
            // 5. 性能图层位置
            item {
                ListPreference(
                    key = "list_perf_overlay_position",
                    title = "性能图层位置",
                    entries = listOf(
                        "顶部" to "top",
                        "底部" to "bottom",
                        "左上角" to "top_left",
                        "右上角" to "top_right",
                        "左下角" to "bottom_left",
                        "右下角" to "bottom_right",
                    ),
                    defaultValue = "top",
                    dependency = "checkbox_enable_perf_overlay",
                )
            }
            // 6. 性能图层显示项目
            item {
                MultiSelectPreference(
                    key = "perf_overlay_display_items",
                    title = "性能图层显示项目",
                    summary = "选择要显示的性能指标",
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
                    title = "性能图层背景不透明度",
                    min = 0, max = 100, step = 5, defaultValue = 53,
                    unit = "%",
                    dependency = "checkbox_enable_perf_overlay",
                )
            }
            // 8. 串流完毕显示延迟信息
            item {
                CheckBoxPreference(
                    key = "checkbox_enable_post_stream_toast",
                    title = "串流完毕显示延迟信息",
                    summary = "串流结束后显示延迟统计",
                )
            }
        }
}
