package com.alexclin.moonlink.stream.ui.display

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.limelight.preferences.CustomResolutionsConsts
import com.limelight.preferences.PreferenceConfiguration
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FPS_AUTO_PREF = "fps_auto"
private val STANDARD_RESOLUTIONS = listOf(
    "640x360", "854x480", "1280x720",
    "1920x1080", "2560x1440", "3840x2160",
)

/**
 * 显示设置子面板（两分组布局）
 *
 * 分组一：帧率与画面  — 包含帧率、码率、HDR、输出缓冲区、画面开关等
 * 分组二：显示器       — 包含显示器列表、分辨率、DPI缩放
 */
@Composable
fun DisplaySettingsPanel(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    DetailScaffold(title = "显示设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── 分组一：帧率与画面 ──
            item { SectionTitle("帧率与画面") }

            // DB-1: 帧率选择
            item { FpsSelector(engine) }

            // DB-2: 码率选择行(含智能码率 & 流量估算)
            item { BitrateSelector(context, engine) }

            // DE-1: 视频编码格式
            item { VideoFormatSelector(engine) }

            // DB-3: HDR
            item { HdrSection(engine) }

            // DD-3: 分辨率缩放
            item { ResolutionScaleSelector(engine) }

            // DC-2: 输出缓冲区滑块
            item { OutputBufferSlider(engine) }

            // DE-2: 帧时序模式
            item { FramePacingSelector(engine) }

            // DC-2: MTK
            item { MtkSwitch(engine) }

            // DC-2: 画面设置开关组
            item { VideoSwitches(engine) }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── 分组二：显示器 ──
            item { SectionTitle("显示器") }

            // DD-1: 显示器信息 + 分辨率 + DPI缩放 + 切换
            item { DisplaySection(engine) }
        }
    }
}

// ══════════════════════════════════════════
// DB-1: FpsSelector  (B3 fix: auto mode persisted via SP key)
// ══════════════════════════════════════════

@Composable
private fun FpsSelector(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val fpsAutoPref = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var unlockFps by remember { mutableStateOf(pref.unlockFps) }
    val fpsAuto = fpsAutoPref.getBoolean(FPS_AUTO_PREF, false)
    var selectedFps by remember { mutableIntStateOf(if (fpsAuto) 0 else pref.fps) }

    val baseFpsOptions = listOf(0, 30, 60, 90, 120)  // 0 = 自动
    val extraFpsOptions = listOf(144, 165)
    val currentFpsText = if (selectedFps == 0) "自动" else "${selectedFps}FPS"

    // 5秒无操作自动收起
    LaunchedEffect(expanded) {
        if (expanded) {
            kotlinx.coroutines.delay(5000)
            expanded = false
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("视频帧率", style = MaterialTheme.typography.bodyLarge,
                 modifier = Modifier.weight(1f))
            Text(currentFpsText, style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 8.dp)) {
                // 解锁帧率 — 放在RadioButton上面
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("解锁帧率", Modifier.weight(1f),
                         style = MaterialTheme.typography.bodySmall)
                    Switch(checked = unlockFps, onCheckedChange = {
                        unlockFps = it
                        pref.unlockFps = it
                        pref.writePreferences(context)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putBoolean(PreferenceConfiguration.UNLOCK_FPS_STRING, it).apply()
                    }, modifier = Modifier.height(24.dp))  // 调小Switch
                }
                baseFpsOptions.forEach { fps ->
                    val label = if (fps == 0) "自动" else "${fps} FPS"
                    FpsRadioItem(fps, label, selectedFps == fps) {
                        selectedFps = it
                        if (it != 0) {
                            fpsAutoPref.edit().putBoolean(FPS_AUTO_PREF, false).apply()
                            pref.fps = it
                            pref.writePreferences(context)
                        } else {
                            fpsAutoPref.edit().putBoolean(FPS_AUTO_PREF, true).apply()
                            // 自动模式下保留 pref.fps 原值，不覆盖
                        }
                        expanded = false
                    }
                }
                if (unlockFps) {
                    extraFpsOptions.forEach { fps ->
                        FpsRadioItem(fps, "${fps} FPS", selectedFps == fps) {
                            selectedFps = it
                            fpsAutoPref.edit().putBoolean(FPS_AUTO_PREF, false).apply()
                            pref.fps = it
                            pref.writePreferences(context)
                            expanded = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FpsRadioItem(value: Int, label: String, selected: Boolean, onClick: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onClick(value) }.padding(vertical = 3.dp),
    ) {
        RadioButton(selected = selected, onClick = { onClick(value) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ══════════════════════════════════════════
// DB-2: BitrateSelector (U4 fix: traffic estimate moved inside)
// ══════════════════════════════════════════

@Composable
private fun BitrateSelector(context: android.content.Context, engine: StreamEngine) {
    val pref = engine.prefConfig
    val presets = BitrateUtils.BitratePreset.entries.toList()

    var selectedPreset by remember { mutableStateOf(BitrateUtils.getPresetByKbps(pref.bitrate)) }
    var abrMode by remember { mutableStateOf(pref.abrMode) }
    // 从持久化中恢复自定义码率，跨预设切换时仍保持（Bug 4 fix）
    val savedCustomKbps = loadCustomKbps(context)
    val initCustomKbps = if (pref.bitrate in BitrateUtils.BITRATE_CUSTOM_MIN..BitrateUtils.BITRATE_CUSTOM_MAX)
        pref.bitrate else savedCustomKbps.coerceIn(BitrateUtils.BITRATE_CUSTOM_MIN, BitrateUtils.BITRATE_CUSTOM_MAX)
    var customKbps by remember { mutableIntStateOf(initCustomKbps) }
    var sliderProgress by remember {
        val initProgress = if (selectedPreset == BitrateUtils.BitratePreset.CUSTOM)
            BitrateUtils.customKbpsToProgress(initCustomKbps).toFloat() else 0f
        mutableFloatStateOf(initProgress)
    }
    // U6: 配置的 fallback 码率（预设/滑块值，ABR 不可用时的回退）
    var configuredBitrate by remember { mutableIntStateOf(pref.bitrate) }
    // U6: 实际用于流量估算的码率 — 优先 ABR 实时值，否则用 configuredBitrate
    var displayBitrate by remember { mutableIntStateOf(
        engine.adaptiveBitrateService?.currentBitrate?.takeIf { it > 0 } ?: pref.bitrate
    ) }

    // U6: 始终跟随 ABR 实时码率（当 ABR 服务存在时，不限 AUTO/固定模式）
    DisposableEffect(engine.adaptiveBitrateService) {
        val service = engine.adaptiveBitrateService
        if (service != null) {
            displayBitrate = service.currentBitrate.takeIf { it > 0 } ?: configuredBitrate
            service.bitrateListener = { kbps, _ -> displayBitrate = kbps }
            onDispose {
                service.bitrateListener = null
            }
        } else {
            onDispose { }
        }
    }

    Column {
        // 码率选择小标题
        Text("码率选择", style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant,
             modifier = Modifier.padding(bottom = 4.dp))
        // 自定义标签映射
        val customLabels = mapOf(
            BitrateUtils.BitratePreset.AUTO to "自动",
            BitrateUtils.BitratePreset.M2 to "清晰\n2M",
            BitrateUtils.BitratePreset.M8 to "高清\n8M",
            BitrateUtils.BitratePreset.M20 to "原画\n20M",
            BitrateUtils.BitratePreset.CUSTOM to "自定义",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp),
             modifier = Modifier.fillMaxWidth()) {
            presets.forEach { preset ->
                BitrateChip(
                    label = customLabels[preset] ?: preset.label,
                    selected = selectedPreset == preset,
                    onClick = {
                        selectedPreset = preset
                        onBitratePresetSelected(engine, preset, context, customKbps)
                        // 切换到 AUTO 时同步 ABR 服务的当前码率
                        if (preset == BitrateUtils.BitratePreset.AUTO) {
                            configuredBitrate = engine.adaptiveBitrateService?.currentBitrate?.takeIf { it > 0 } ?: 0
                        }
                        // 切换到 CUSTOM 时同步 SeekBar 位置
                        if (preset == BitrateUtils.BitratePreset.CUSTOM) {
                            val targetKbps = customKbps.coerceIn(
                                BitrateUtils.BITRATE_CUSTOM_MIN, BitrateUtils.BITRATE_CUSTOM_MAX)
                            sliderProgress = BitrateUtils.customKbpsToProgress(targetKbps).toFloat()
                        }
                        // U6: 更新流量估算用码率 fallback + 优先尝试 ABR 实时值
                        configuredBitrate = when (preset) {
                            BitrateUtils.BitratePreset.AUTO -> 0
                            BitrateUtils.BitratePreset.M2 -> BitrateUtils.BITRATE_2M
                            BitrateUtils.BitratePreset.M8 -> BitrateUtils.BITRATE_8M
                            BitrateUtils.BitratePreset.M20 -> BitrateUtils.BITRATE_20M
                            BitrateUtils.BitratePreset.CUSTOM -> customKbps
                        }
                        displayBitrate = engine.adaptiveBitrateService?.currentBitrate?.takeIf { it > 0 }
                            ?: configuredBitrate
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 自动 → 智能码率模式（可展开收起）
        AnimatedVisibility(visible = selectedPreset == BitrateUtils.BitratePreset.AUTO) {
            val abrLabels = mapOf(
                "quality" to "画质优先",
                "balanced" to "均衡",
                "lowLatency" to "低延迟",
            )
            var abrExpanded by remember { mutableStateOf(false) }
            LaunchedEffect(abrExpanded) {
                if (abrExpanded) {
                    kotlinx.coroutines.delay(5000)
                    abrExpanded = false
                }
            }
            Column(Modifier.padding(top = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { abrExpanded = !abrExpanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("智能码率模式", style = MaterialTheme.typography.bodyLarge,
                         modifier = Modifier.weight(1f))
                    Text(abrLabels[abrMode] ?: "均衡",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        if (abrExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = abrExpanded) {
                    Column(Modifier.padding(start = 8.dp)) {
                        abrLabels.forEach { (value, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        onAbrModeSelected(engine, value, context)
                                        abrExpanded = false
                                    }
                                    .padding(vertical = 2.dp),
                            ) {
                                RadioButton(selected = abrMode == value,
                                     onClick = {
                                         onAbrModeSelected(engine, value, context)
                                         abrExpanded = false
                                     })
                                Spacer(Modifier.width(4.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // 自定义 → SeekBar
        AnimatedVisibility(visible = selectedPreset == BitrateUtils.BitratePreset.CUSTOM) {
            Column(Modifier.padding(top = 8.dp)) {
                val displayKbps = BitrateUtils.customProgressToKbps(sliderProgress.roundToInt())
                Text("码率: ${displayKbps / 1000} Mbps ($displayKbps kbps)",
                     style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = sliderProgress,
                    onValueChange = { sliderProgress = it
                        // U6: 拖动过程中实时更新估算（ABR 存在时优先 ABR 值）
                        configuredBitrate = BitrateUtils.customProgressToKbps(it.roundToInt())
                        displayBitrate = engine.adaptiveBitrateService?.currentBitrate?.takeIf { v -> v > 0 }
                            ?: configuredBitrate
                    },
                    onValueChangeFinished = {
                        val kbps = BitrateUtils.customProgressToKbps(sliderProgress.roundToInt())
                        customKbps = kbps
                        configuredBitrate = kbps
                        displayBitrate = engine.adaptiveBitrateService?.currentBitrate?.takeIf { v -> v > 0 }
                            ?: configuredBitrate
                        saveCustomKbps(context, kbps)
                        // 使用 setFixedBitrate 统一处理持久化 + 异步回调 + ABR 同步
                        setFixedBitrate(engine, pref, kbps, context)
                    },
                    valueRange = 0f..100f,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("1M", style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("800M", style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // U7: 流量估算 — 放在自定义拖动控件下方，不受码率选项影响始终展示
        TrafficEstimateLine(displayBitrate)
    }
}

// ══════════════════════════════════════════
// BitrateChip: 自定义轻量芯片（替代 FilterChip，零内边距可控）
// ══════════════════════════════════════════

@Composable
private fun BitrateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                 else null,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)
        ) {
            Text(
                text = label,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun TrafficEstimateLine(bitrateKbps: Int) {
    val text = if (bitrateKbps > 0) {
        BitrateUtils.formatTrafficEstimate(bitrateKbps)
    } else {
        "码率自适应中…"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * 直接写入 [PreferenceConfiguration.writePreferences] 未覆盖的 SP 键。
 *
 * [writePreferences] 遗漏了多个字段（如 enableAdaptiveBitrate、abrMode、stretchVideo、
 * unlockFps、resolutionScale 等），因为它们原本由旧 PreferenceScreen 自动保存。
 * 新的 Compose UI 必须手动写入这些键。
 */
private fun writeDirectPrefs(pref: PreferenceConfiguration, context: android.content.Context) {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    sp.edit()
        .putBoolean("checkbox_adaptive_bitrate", pref.enableAdaptiveBitrate)
        .putString("list_abr_mode", pref.abrMode)
        .putBoolean("checkbox_stretch_video", pref.stretchVideo)
        .putInt("seekbar_output_buffer_queue_limit", pref.outputBufferQueueLimit)
        .putBoolean("checkbox_unlock_fps", pref.unlockFps)
        .putInt("seekbar_resolutions_scale", pref.resolutionScale)
        .apply()
}

private const val CUSTOM_KBPS_PREF = "custom_kbps"

private fun loadCustomKbps(context: android.content.Context): Int =
    context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        .getInt(CUSTOM_KBPS_PREF, 50000)

private fun saveCustomKbps(context: android.content.Context, kbps: Int) {
    context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        .edit().putInt(CUSTOM_KBPS_PREF, kbps).apply()
}

private fun onBitratePresetSelected(
    engine: StreamEngine, preset: BitrateUtils.BitratePreset,
    context: android.content.Context, customKbps: Int,
) {
    val pref = engine.prefConfig
    when (preset) {
        BitrateUtils.BitratePreset.AUTO -> {
            pref.enableAdaptiveBitrate = true
            pref.writePreferences(context)
            writeDirectPrefs(pref, context)
            // 运行时切换 AUTO：如果连接已建立但服务未创建，立即启动
            engine.startAdaptiveBitrateIfEnabled()
        }
        BitrateUtils.BitratePreset.M2 -> setFixedBitrate(engine, pref, BitrateUtils.BITRATE_2M, context)
        BitrateUtils.BitratePreset.M8 -> setFixedBitrate(engine, pref, BitrateUtils.BITRATE_8M, context)
        BitrateUtils.BitratePreset.M20 -> setFixedBitrate(engine, pref, BitrateUtils.BITRATE_20M, context)
        BitrateUtils.BitratePreset.CUSTOM -> setFixedBitrate(engine, pref, customKbps, context)
    }
}

private fun setFixedBitrate(
    engine: StreamEngine, pref: com.limelight.preferences.PreferenceConfiguration,
    kbps: Int, context: android.content.Context,
) {
    pref.enableAdaptiveBitrate = false
    pref.bitrate = kbps
    // writePreferences 不保存 enableAdaptiveBitrate，需直接写 SP
    writeDirectPrefs(pref, context)

    // 切换到固定预设时停止 ABR 服务，防止后台 tick 覆盖手动设定值
    engine.adaptiveBitrateService?.stop()
    engine.adaptiveBitrateService = null

    val conn = engine.conn
    if (conn != null) {
        conn.setBitrate(kbps, object : com.limelight.nvstream.NvConnection.BitrateAdjustmentCallback {
            override fun onSuccess(newBitrate: Int) {
                pref.bitrate = newBitrate
                pref.writePreferences(context)
            }
            override fun onFailure(errorMessage: String) {
                // 服务端拒绝，恢复之前的状态
                Toast.makeText(context, "码率设置失败: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

private fun onAbrModeSelected(
    engine: StreamEngine, mode: String, context: android.content.Context,
) {
    val pref = engine.prefConfig
    pref.abrMode = mode
    pref.writePreferences(context)
    writeDirectPrefs(pref, context)
}

// ══════════════════════════════════════════
// DE-1: VideoFormatSelector
// ══════════════════════════════════════════

@Composable
private fun VideoFormatSelector(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val formats = listOf(
        Triple(PreferenceConfiguration.FormatOption.AUTO, "自动", "auto"),
        Triple(PreferenceConfiguration.FormatOption.FORCE_AV1, "AV1", "forceav1"),
        Triple(PreferenceConfiguration.FormatOption.FORCE_HEVC, "HEVC", "forceh265"),
        Triple(PreferenceConfiguration.FormatOption.FORCE_H264, "H264", "neverh265"),
    )

    Column {
        Text("视频编码格式", style = MaterialTheme.typography.bodyLarge,
             modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            formats.forEach { (option, label, spValue) ->
                FilterChip(
                    selected = pref.videoFormat == option,
                    onClick = {
                        pref.videoFormat = option
                        pref.writePreferences(context)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putString("video_format", spValue).apply()
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

// ══════════════════════════════════════════
// DB-3: HdrSection
// ══════════════════════════════════════════

@Composable
private fun HdrSection(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var hdrEnabled by remember { mutableStateOf(pref.enableHdr) }
    var highBrightness by remember { mutableStateOf(pref.enableHdrHighBrightness) }
    var hdrMode by remember { mutableIntStateOf(pref.hdrMode) }

    Column {
        SettingSwitch("HDR", hdrEnabled) {
            hdrEnabled = it; pref.enableHdr = it; pref.writePreferences(context)
        }
        AnimatedVisibility(visible = hdrEnabled) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                SettingSwitch("HDR高亮度", highBrightness) {
                    highBrightness = it; pref.enableHdrHighBrightness = it; pref.writePreferences(context)
                }
                Spacer(Modifier.height(4.dp))
                Text("HDR模式", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.primary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        hdrMode = 1; pref.hdrMode = 1; pref.writePreferences(context)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putString("list_hdr_mode", "1").apply()
                    }.padding(vertical = 2.dp),
                ) {
                    RadioButton(selected = hdrMode == 1, onClick = {
                        hdrMode = 1; pref.hdrMode = 1; pref.writePreferences(context)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putString("list_hdr_mode", "1").apply()
                    })
                    Spacer(Modifier.width(4.dp))
                    Text("HDR10/PQ", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        hdrMode = 2; pref.hdrMode = 2; pref.writePreferences(context)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putString("list_hdr_mode", "2").apply()
                    }.padding(vertical = 2.dp),
                ) {
                    RadioButton(selected = hdrMode == 2, onClick = {
                        hdrMode = 2; pref.hdrMode = 2; pref.writePreferences(context)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putString("list_hdr_mode", "2").apply()
                    })
                    Spacer(Modifier.width(4.dp))
                    Text("HLG", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ══════════════════════════════════════════
// DD-3: ResolutionScaleSelector (移入分组一)
// ══════════════════════════════════════════

@Composable
private fun ResolutionScaleSelector(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val defaultScale = 100
    val initScale = if (pref.resolutionScale in 50..400) pref.resolutionScale else defaultScale
    var expanded by remember { mutableStateOf(false) }
    var resScaleSlider by remember { mutableFloatStateOf(initScale.toFloat()) }
    val scope = rememberCoroutineScope()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("分辨率缩放", style = MaterialTheme.typography.bodyLarge,
                 modifier = Modifier.weight(1f))
            Text("${resScaleSlider.toInt()}%", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Slider(
                    value = resScaleSlider,
                    onValueChange = { resScaleSlider = it },
                    onValueChangeFinished = {
                        engine.applyDisplaySettings = true
                        pref.resolutionScale = resScaleSlider.toInt().coerceIn(50, 400)
                        pref.writePreferences(context)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putInt("seekbar_resolutions_scale", pref.resolutionScale).apply()
                        // 调整完毕自动收起
                        expanded = false
                    },
                    valueRange = 50f..400f, steps = 35,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("50%", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("400%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ══════════════════════════════════════════
// DC-2: OutputBufferSlider + MtkSwitch + VideoSwitches
// ══════════════════════════════════════════

@Composable
private fun OutputBufferSlider(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val defaultBuf = 2  // PreferenceConfiguration.DEFAULT_OUTPUT_BUFFER_QUEUE_LIMIT
    val initVal = if (pref.outputBufferQueueLimit in 1..5) pref.outputBufferQueueLimit else defaultBuf
    var expanded by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(initVal.toFloat()) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("输出缓冲区大小", style = MaterialTheme.typography.bodyLarge,
                 modifier = Modifier.weight(1f))
            Text("${sliderValue.toInt()} 帧", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        pref.outputBufferQueueLimit = sliderValue.toInt().coerceIn(1, 5)
                        pref.writePreferences(context)
                        writeDirectPrefs(pref, context)
                        expanded = false
                    },
                    valueRange = 1f..5f, steps = 3,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("1帧", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("5帧", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MtkSwitch(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var checked by remember { mutableStateOf(pref.forceMtkMaxOperatingRate) }
    SettingSwitch("MTK专属选项", checked) {
        checked = it; pref.forceMtkMaxOperatingRate = it; pref.writePreferences(context)
    }
}

@Composable
private fun VideoSwitches(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var stretch by remember { mutableStateOf(pref.stretchVideo) }
    var reverse by remember { mutableStateOf(pref.reverseResolution) }
    var rotable by remember { mutableStateOf(pref.rotableScreen) }

    Column {
        SettingSwitch("拉伸视频", stretch) { stretch = it; pref.stretchVideo = it; pref.writePreferences(context); writeDirectPrefs(pref, context) }
        SettingSwitch("反转分辨率", reverse) { reverse = it; pref.reverseResolution = it; pref.writePreferences(context) }
        SettingSwitch("可旋转画面", rotable) { rotable = it; pref.rotableScreen = it; pref.writePreferences(context) }
    }
}

// ══════════════════════════════════════════
// DE-2: FramePacingSelector
// ══════════════════════════════════════════

@Composable
private fun FramePacingSelector(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var expanded by remember { mutableStateOf(false) }

    data class PacingOption(val value: Int, val spKey: String, val label: String)
    val options = listOf(
        PacingOption(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, "latency", "最低延迟"),
        PacingOption(PreferenceConfiguration.FRAME_PACING_BALANCED, "balanced", "均衡"),
        PacingOption(PreferenceConfiguration.FRAME_PACING_CAP_FPS, "cap-fps", "均衡+FPS限制"),
        PacingOption(PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS, "smoothness", "最高流畅度"),
        PacingOption(PreferenceConfiguration.FRAME_PACING_EXPERIMENTAL_LOW_LATENCY, "experimental-low-latency", "超低延迟(实验)"),
        PacingOption(PreferenceConfiguration.FRAME_PACING_PRECISE_SYNC, "precise-sync", "精确同步"),
    )
    val currentLabel = options.find { it.value == pref.framePacing }?.label ?: "最低延迟"

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("帧时序模式", style = MaterialTheme.typography.bodyLarge,
                 modifier = Modifier.weight(1f))
            Text(currentLabel, style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 8.dp)) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                expanded = false
                                pref.framePacing = option.value
                                pref.writePreferences(context)
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .edit().putString("frame_pacing", option.spKey).apply()
                            }
                            .padding(vertical = 3.dp),
                    ) {
                        RadioButton(
                            selected = pref.framePacing == option.value,
                            onClick = {
                                pref.framePacing = option.value
                                pref.writePreferences(context)
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .edit().putString("frame_pacing", option.spKey).apply()
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════
// DD-1: DisplaySection — 显示器信息 + 分辨率 + DPI缩放 + 切换
// ══════════════════════════════════════════

@Composable
private fun DisplaySection(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val scope = rememberCoroutineScope()
    val displayPrefs = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    var displays by remember { mutableStateOf<List<com.limelight.nvstream.http.NvHTTP.DisplayInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // 当前显示器信息
    val currentName = engine.currentDisplayName
    val currentDeviceId = engine.currentDeviceId

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val conn = engine.conn ?: return@withContext
                val list = conn.createNvHttp()?.getDisplays() ?: emptyList()
                displays = list
            } catch (_: Exception) {}
            loading = false
        }
    }

    Column {
        // ── 当前显示器信息 ──
        Text("当前显示器", style = MaterialTheme.typography.labelLarge,
             color = MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))

        if (currentName != null) {
            Text("display_name: $currentName",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (currentDeviceId != null) {
            Text("device_id: $currentDeviceId",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.padding(bottom = 6.dp))
        }

        // ── 分辨率 ──
        val nativeRes by remember {
            mutableStateOf(run {
                val display = (context.getSystemService(Context.WINDOW_SERVICE)
                        as android.view.WindowManager).defaultDisplay
                val size = Point()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    display.getRealSize(size)
                } else {
                    display.getSize(size)
                }
                val w = maxOf(size.x, size.y)
                val h = minOf(size.x, size.y)
                "${w}x${h}"
            })
        }

        var customResSet by remember { mutableStateOf(loadCustomResolutions(context)) }

        val allResolutions = remember {
            val nativeLabel = if (nativeRes !in STANDARD_RESOLUTIONS) {
                listOf("Native ($nativeRes)")
            } else emptyList()
            STANDARD_RESOLUTIONS + nativeLabel + customResSet.map { "$it (自定义)" }
        }

        var selectedRes by remember { mutableStateOf("${pref.width}x${pref.height}") }
        var showCustomDialog by remember { mutableStateOf(false) }

        Text("切换分辨率", style = MaterialTheme.typography.labelLarge,
             color = MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(bottom = 4.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(allResolutions) { res ->
                val cleanRes = res.replace(" (自定义)", "").replace("Native (", "").replace(")", "")
                FilterChip(
                    selected = selectedRes == cleanRes,
                    onClick = {
                        selectedRes = cleanRes
                        onResolutionSelected(engine, context, cleanRes, nativeRes)
                    },
                    label = { Text(res, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        TextButton(onClick = { showCustomDialog = true }) {
            Text("+ 添加自定义分辨率")
        }

        if (showCustomDialog) {
            CustomResolutionDialog(
                onDismiss = { showCustomDialog = false },
                onConfirm = { width, height ->
                    saveCustomResolution(context, width, height)
                    customResSet = loadCustomResolutions(context)
                    showCustomDialog = false
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ── DPI 缩放 (针对当前显示器) ──
        var scalePercent by remember { mutableIntStateOf(100) }
        var scaleSupported by remember { mutableStateOf(false) }
        var supportedScales by remember { mutableStateOf(listOf(100)) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    val conn = engine.conn ?: return@withContext
                    val list = conn.createNvHttp()?.getDisplays() ?: emptyList()
                    val target = if (currentName != null) {
                        list.find { it.name == currentName || it.guid == currentDeviceId }
                    } else null
                    val display = target ?: list.find { it.isPrimary } ?: list.firstOrNull()
                    if (display != null && display.scaleSetSupported) {
                        scaleSupported = true
                        scalePercent = display.currentScalePercent
                        supportedScales = display.supportedScalePercents
                    }
                } catch (_: Exception) {}
            }
        }

        if (scaleSupported) {
            Row(verticalAlignment = Alignment.CenterVertically,
                 modifier = Modifier.padding(vertical = 4.dp)) {
                Text("DPI缩放", Modifier.weight(1f))
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text("${scalePercent}%")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                             modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        supportedScales.forEach { scale ->
                            DropdownMenuItem(
                                text = { Text("${scale}%") },
                                onClick = {
                                    expanded = false
                                    scalePercent = scale
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            try {
                                                val c = engine.conn ?: return@withContext false
                                                // 对当前显示器设置 DPI 缩放
                                                c.createNvHttp()?.setDisplayScale(scale, displayName = currentName) == true
                                            } catch (_: Exception) { false }
                                        }
                                        if (!ok) {
                                            Toast.makeText(context, "DPI缩放设置失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── 其它显示器选择 ──
        if (!loading && displays.isNotEmpty()) {
            val otherDisplays = if (currentName != null || currentDeviceId != null) {
                displays.filter { d ->
                    (currentName != null && d.name == currentName) ||
                    (currentDeviceId != null && d.guid == currentDeviceId)
                }.let { current ->
                    displays.filterNot { it in current }
                }
            } else displays

            if (otherDisplays.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("其它显示器", style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(bottom = 4.dp))

                otherDisplays.forEach { display ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                engine.changeDisplay(display.name, display.guid)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = false,
                            onClick = {
                                engine.changeDisplay(display.name, display.guid)
                            })
                        Spacer(Modifier.width(8.dp))
                        Text(display.name, modifier = Modifier.weight(1f))
                        if (display.isPrimary) {
                            Text("[主]", style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            engine.changeDisplay(display.name, display.guid)
                        }) {
                            Text("切换")
                        }
                    }
                }
            }
        }

        // ── PiP（画中画） ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { pref.enablePip = !pref.enablePip }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = pref.enablePip,
                    onCheckedChange = { pref.enablePip = it },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "画中画 (PiP)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun loadCustomResolutions(context: Context): List<String> {
    return context.getSharedPreferences(
        CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE
    ).getStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, emptySet())
        ?.sortedBy { it } ?: emptyList()
}

private fun onResolutionSelected(
    engine: StreamEngine, context: android.content.Context,
    res: String, nativeRes: String,
) {
    // 始终存实际分辨率字符串（如 "2560x1600"），避免旧解析代码对 "Native" 字符串崩溃
    val actualRes = res
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().putString(PreferenceConfiguration.RESOLUTION_PREF_STRING, actualRes).apply()
    engine.changeResolution()  // → activity.recreate()
}

private fun saveCustomResolution(context: android.content.Context, width: Int, height: Int) {
    val prefs = context.getSharedPreferences(
        CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE
    )
    val set = prefs.getStringSet(
        CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, mutableSetOf()
    )?.toMutableSet() ?: mutableSetOf()
    set.add("${width}x${height}")
    prefs.edit().putStringSet(
        CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, set
    ).apply()
    Toast.makeText(context, "自定义分辨率已保存，请重新选择", Toast.LENGTH_SHORT).show()
}

@Composable
private fun CustomResolutionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义分辨率") },
        text = {
            Column {
                OutlinedTextField(value = width, onValueChange = { width = it },
                    label = { Text("宽度 (320~7680)") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = height, onValueChange = { height = it },
                    label = { Text("高度 (240~4320)") })
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = width.toIntOrNull() ?: 0
                val h = height.toIntOrNull() ?: 0
                if (w < 320 || w > 7680 || h < 240 || h > 4320) {
                    error = "分辨率超出范围(宽320~7680, 高240~4320)"
                } else if (w % 2 != 0 || h % 2 != 0) {
                    error = "宽高必须为偶数"
                } else {
                    onConfirm(w, h)
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ══════════════════════════════════════════
// 通用组件
// ══════════════════════════════════════════

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall,
         color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(title, style = MaterialTheme.typography.titleMedium,
                 color = MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider()
        content()
    }
}

@Composable
private fun SettingSwitch(
    label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp),
         verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
