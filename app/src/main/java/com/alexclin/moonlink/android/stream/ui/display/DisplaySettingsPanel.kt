package com.alexclin.moonlink.android.stream.ui.display

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
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
import androidx.compose.ui.draw.scale
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.DetailScaffold
import com.alexclin.moonlink.android.stream.ui.common.ChipSelector
import com.alexclin.moonlink.android.stream.ui.common.CompactChip
import com.alexclin.moonlink.android.stream.ui.common.RestartHintBanner
import com.limelight.preferences.CustomResolutionsConsts
import com.limelight.preferences.PreferenceConfiguration
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── 展开项联动 ID ──
private const val EXP_SECTION_ABR = 0
private const val EXP_SECTION_OUTPUT_BUFFER = 3
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

    val handleBack = {
        // isFinishing 保护：recreate 调用在 Activity 将销毁时不执行，回退到 onBack
        if (engine.displaySettingsRestartPending && !engine.activity.isFinishing) {
            engine.changeResolution()
        } else {
            onBack()
        }
    }

    DetailScaffold(title = "显示设置", onBack = handleBack) {
        var activeExpandableId by remember { mutableIntStateOf(-1) }
        val onSectionExpand = { id: Int -> activeExpandableId = id }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── 需要重启提示横幅 ──
            if (engine.displaySettingsRestartPending) {
                item {
                    RestartHintBanner(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
            }

            // ── 分组一：帧率与画面 ──
            item { SectionTitle("画面") }

            // DB-1: 码率选择行(含智能码率 & 流量估算)
            item {
                BitrateSelector(
                    context = context,
                    engine = engine,
                    abrSectionId = EXP_SECTION_ABR,
                    activeSectionId = activeExpandableId,
                    onSectionExpand = onSectionExpand,
                )
            }

            // DB-1: 帧率选择
            item { FpsSelector(engine = engine) }

            // DE-1: 视频编码格式
            item { VideoFormatSelector(engine = engine) }

            // DC-2: 输出缓冲区滑块
            item {
                OutputBufferSlider(
                    engine = engine,
                    sectionId = EXP_SECTION_OUTPUT_BUFFER,
                    activeSectionId = activeExpandableId,
                    onSectionExpand = onSectionExpand,
                )
            }

            // DE-2: 帧时序模式
            item { FramePacingSelector(engine = engine) }

            // DB-3: HDR
            item { HdrSection(engine) }

            // CR-3: 使用外接显示器
            item { ExternalDisplaySection(engine) }

            // DC-2: 画面设置开关组
            item { VideoSwitches(engine) }

            // ── 画中画（放在 MTK 上方，统一 SettingSwitch 样式） ──
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                item { PipSwitch(engine) }
            }

            // DC-2: MTK
            item { MtkSwitch(engine) }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            // ── 分组二：显示器（仅在检测到显示器时显示标题） ──
            if (engine.currentDisplayName != null || engine.currentDeviceId != null) {
                item { SectionTitle("显示器") }
            }

            // DD-1: 显示器信息 + 分辨率 + DPI缩放 + 切换
            item { DisplaySection(engine) }
        }
    }
}

// ══════════════════════════════════════════
// DB-1: FpsSelector — 紧凑芯片布局
// ══════════════════════════════════════════

@Composable
private fun FpsSelector(engine: StreamEngine) {
    val pref = engine.prefConfig
    var unlockFps by remember { mutableStateOf(pref.unlockFps) }

    Column {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("视频帧率", style = MaterialTheme.typography.bodyLarge,
                 modifier = Modifier.weight(1f))
            Text("解锁帧率", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = unlockFps,
                onCheckedChange = {
                    unlockFps = it
                    pref.unlockFps = it
                },
                modifier = Modifier.height(20.dp),
            )
        }

        val baseFps = listOf(30, 60, 90, 120)
        val extraFps = listOf(144, 165)
        val allFps = baseFps + if (unlockFps) extraFps else emptyList()
        ChipSelector(
            options = allFps.map { fps ->
                "${fps}FPS" to fps.toString()
            },
            selectedValue = pref.fps.toString(),
            onSelect = { value ->
                val newFps = value.toIntOrNull() ?: 60
                pref.fps = newFps
                engine.displaySettingsRestartPending = true
            },
        )
    }
}

// ══════════════════════════════════════════
// DB-2: BitrateSelector (U4 fix: traffic estimate moved inside)
// ══════════════════════════════════════════

@Composable
private fun BitrateSelector(
    context: android.content.Context,
    engine: StreamEngine,
    abrSectionId: Int = -1,
    activeSectionId: Int = -1,
    onSectionExpand: (Int) -> Unit = {},
) {
    val pref = engine.prefConfig
    val presets = BitrateUtils.BitratePreset.entries.toList()

    var selectedPreset by remember { mutableStateOf(BitrateUtils.getPresetByKbps(pref.bitrate)) }
    var abrMode by remember { mutableStateOf(pref.abrMode) }
    var customKbps by remember { mutableIntStateOf(pref.bitrate.coerceIn(BitrateUtils.BITRATE_CUSTOM_MIN, BitrateUtils.BITRATE_CUSTOM_MAX)) }
    var sliderProgress by remember {
        val initProgress = if (selectedPreset == BitrateUtils.BitratePreset.CUSTOM)
            BitrateUtils.customKbpsToProgress(customKbps).toFloat() else 0f
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
        // 码率设置小标题
        Text("码率设置", style = MaterialTheme.typography.bodyLarge,
             modifier = Modifier.padding(vertical = 6.dp))
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
                CompactChip(
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
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
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
            // 其它 section 展开时收起 ABR 模式
            LaunchedEffect(activeSectionId) {
                if (activeSectionId != abrSectionId && abrExpanded) {
                    abrExpanded = false
                }
            }
            Column(Modifier.padding(top = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val willExpand = !abrExpanded
                            if (willExpand) onSectionExpand(abrSectionId)
                            abrExpanded = willExpand
                        }
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
                    ChipSelector(
                        options = abrLabels.entries.map { (value, label) -> label to value },
                        selectedValue = abrMode,
                        onSelect = { value ->
                            abrMode = value
                            onAbrModeSelected(engine, value, context)
                            abrExpanded = false
                        },
                        columns = 3,
                    )
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

private fun onBitratePresetSelected(
    engine: StreamEngine, preset: BitrateUtils.BitratePreset,
    context: android.content.Context, customKbps: Int,
) {
    val pref = engine.prefConfig
    when (preset) {
        BitrateUtils.BitratePreset.AUTO -> {
            pref.enableAdaptiveBitrate = true
            
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

    // 切换到固定预设时停止 ABR 服务，防止后台 tick 覆盖手动设定值
    engine.stopAdaptiveBitrate()

    val conn = engine.conn
    if (conn != null) {
        conn.setBitrate(kbps, object : com.limelight.nvstream.NvConnection.BitrateAdjustmentCallback {
            override fun onSuccess(newBitrate: Int) {
                pref.bitrate = newBitrate
                
                    }
            override fun onFailure(errorMessage: String) {
                // 服务端拒绝，恢复之前的状态
                ToastUtil.show(context, "码率设置失败: $errorMessage", Toast.LENGTH_SHORT)
            }
        })
    }
}

private fun onAbrModeSelected(
    engine: StreamEngine, mode: String, context: android.content.Context,
) {
    val pref = engine.prefConfig
    pref.abrMode = mode
    
                    }

// ══════════════════════════════════════════
// DE-1: VideoFormatSelector — 紧凑芯片布局
// ══════════════════════════════════════════

@Composable
private fun VideoFormatSelector(engine: StreamEngine) {
    val pref = engine.prefConfig

    Column {
        Text("视频编码格式", style = MaterialTheme.typography.bodyLarge,
             modifier = Modifier.padding(vertical = 6.dp))
        ChipSelector(
            options = listOf(
                "自动" to PreferenceConfiguration.FormatOption.AUTO.name,
                "AV1" to PreferenceConfiguration.FormatOption.FORCE_AV1.name,
                "HEVC" to PreferenceConfiguration.FormatOption.FORCE_HEVC.name,
                "H264" to PreferenceConfiguration.FormatOption.FORCE_H264.name,
            ),
            selectedValue = pref.videoFormat.name,
            onSelect = { value ->
                pref.videoFormat = PreferenceConfiguration.FormatOption.valueOf(value)
                engine.displaySettingsRestartPending = true
            },
        )
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
            hdrEnabled = it; pref.enableHdr = it; 
                    engine.displaySettingsRestartPending = true
        }
        AnimatedVisibility(visible = hdrEnabled) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                SettingSwitch("HDR高亮度", highBrightness) {
                    highBrightness = it; pref.enableHdrHighBrightness = it; 
                    engine.displaySettingsRestartPending = true
                }
                Spacer(Modifier.height(4.dp))
                Text("HDR模式", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.primary)
                ChipSelector(
                    options = listOf("HDR10/PQ" to "1", "HLG" to "2"),
                    selectedValue = hdrMode.toString(),
                    onSelect = { value ->
                        val mode = value.toIntOrNull() ?: 1
                        hdrMode = mode
                        pref.hdrMode = mode
                        pref.writePreferences(context)
                        engine.displaySettingsRestartPending = true
                    },
                    columns = 2,
                )
            }
        }
    }
}

// ══════════════════════════════════════════
// DC-2: OutputBufferSlider + MtkSwitch + VideoSwitches
// ══════════════════════════════════════════

@Composable
private fun OutputBufferSlider(
    engine: StreamEngine,
    sectionId: Int = -1,
    activeSectionId: Int = -1,
    onSectionExpand: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val defaultBuf = 2  // PreferenceConfiguration.DEFAULT_OUTPUT_BUFFER_QUEUE_LIMIT
    val initVal = if (pref.outputBufferQueueLimit in 1..5) pref.outputBufferQueueLimit else defaultBuf
    var expanded by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(initVal.toFloat()) }

    // 其它 section 展开时收起自己
    LaunchedEffect(activeSectionId) {
        if (activeSectionId != sectionId && expanded) {
            expanded = false
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val willExpand = !expanded
                    if (willExpand) onSectionExpand(sectionId)
                    expanded = willExpand
                }
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
    Column {
        var checked by remember { mutableStateOf(pref.forceMtkMaxOperatingRate) }
        SettingSwitch("MTK专属选项", checked) {
            checked = it; pref.forceMtkMaxOperatingRate = it; 
                    engine.displaySettingsRestartPending = true
        }
        Text(
            "强制MTK解码器高频运行以降低延迟，其它设备可能异常，默认关闭。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun VideoSwitches(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var stretch by remember { mutableStateOf(engine.stretchVideo) }
    var reverse by remember { mutableStateOf(pref.reverseResolution) }
    var rotable by remember { mutableStateOf(pref.rotableScreen) }

    Column {
        SettingSwitch("拉伸视频", stretch) { stretch = it; engine.stretchVideo = it }
        SettingSwitch("反转分辨率", reverse) { reverse = it; pref.reverseResolution = it; 
                    ; engine.displaySettingsRestartPending = true }
        SettingSwitch("可旋转画面", rotable) { rotable = it; pref.rotableScreen = it; 
                    ; engine.displaySettingsRestartPending = true }
    }
}

// ══════════════════════════════════════════
// DE-2: FramePacingSelector — 紧凑芯片布局
// ══════════════════════════════════════════

@Composable
private fun FramePacingSelector(engine: StreamEngine) {
    val pref = engine.prefConfig

    Column {
        Text("帧时序模式", style = MaterialTheme.typography.bodyLarge,
             modifier = Modifier.padding(vertical = 6.dp))
        ChipSelector(
            options = listOf(
                "最低延迟" to "0", "均衡" to "1", "均衡+FPS限制" to "2",
                "最高流畅度" to "3", "超低延迟(实验)" to "4", "精确同步" to "5",
            ),
            selectedValue = pref.framePacing.toString(),
            onSelect = { value ->
                pref.framePacing = value.toIntOrNull() ?: 0
            },
            columns = 2,
        )
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
        if (currentName != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("当前显示器", style = MaterialTheme.typography.bodyLarge)
                Text(currentName, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── 分辨率 ──
        val nativeRes by remember {
            mutableStateOf(run {
                val size = Point()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE)
                            as android.view.WindowManager
                    val bounds = wm.currentWindowMetrics.bounds
                    size.set(bounds.width(), bounds.height())
                } else {
                    @Suppress("DEPRECATION")
                    val display = (context.getSystemService(Context.WINDOW_SERVICE)
                            as android.view.WindowManager).defaultDisplay
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        display.getRealSize(size)
                    } else {
                        display.getSize(size)
                    }
                }
                val w = maxOf(size.x, size.y)
                val h = minOf(size.x, size.y)
                "${w}x${h}"
            })
        }

        val customResSet = remember { loadCustomResolutions(context) }

        val allResolutions = remember {
            val nativeLabel = if (nativeRes !in STANDARD_RESOLUTIONS) {
                listOf("Native ($nativeRes)")
            } else emptyList()
            STANDARD_RESOLUTIONS + nativeLabel + customResSet.map { "$it (自定义)" }
        }

        var selectedRes by remember { mutableStateOf("${pref.width}x${pref.height}") }

        Text("切换分辨率", style = MaterialTheme.typography.labelLarge,
             color = MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(bottom = 4.dp))

        // 使用 Row + chunked(3) 手动排成 3 列，避免 LazyVerticalGrid 在 LazyColumn 内崩溃
        // 因为分辨率为少量固定选项，无需懒加载
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allResolutions.chunked(3).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowItems.forEach { res ->
                        val cleanRes = res.replace(" (自定义)", "").replace("Native (", "").replace(")", "")
                        CompactChip(
                            label = res,
                            selected = selectedRes == cleanRes,
                            onClick = {
                                selectedRes = cleanRes
                                onResolutionSelected(engine, context, cleanRes)
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                        )
                    }
                    // 补齐不足 3 列的空位，保持对齐
                    repeat(3 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
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
                                            ToastUtil.show(context, "DPI缩放设置失败", Toast.LENGTH_SHORT)
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
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(display.name, modifier = Modifier.weight(1f))
                        if (display.isPrimary) {
                            Text("[主]", style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 modifier = Modifier.padding(end = 8.dp))
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

    }
}

private fun onResolutionSelected(
    engine: StreamEngine, context: android.content.Context,
    res: String,
) {
    val parts = res.split("x")
    if (parts.size == 2) {
        val w = parts[0].toIntOrNull() ?: engine.prefConfig.width
        val h = parts[1].toIntOrNull() ?: engine.prefConfig.height
        if (w > 0 && h > 0) {
            engine.prefConfig.width = w
            engine.prefConfig.height = h
            engine.applyDisplaySettings = true
        }
    }
    // 分辨率切换仅影响当前串流，不持久化
    engine.displaySettingsRestartPending = true
}

private fun loadCustomResolutions(context: android.content.Context): List<String> {
    return context.getSharedPreferences(
        CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, android.content.Context.MODE_PRIVATE
    ).getStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, emptySet())
        ?.sortedBy { it } ?: emptyList()
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
private fun ExternalDisplaySection(engine: StreamEngine) {
    val pref = engine.prefConfig
    var useExternal by remember { mutableStateOf(pref.useExternalDisplay) }

    SettingSwitch("使用外接显示器", useExternal) {
        useExternal = it
        pref.useExternalDisplay = it
    }
}

@Composable
private fun PipSwitch(engine: StreamEngine) {
    val pref = engine.prefConfig
    var checked by remember { mutableStateOf(pref.enablePip) }
    SettingSwitch("画中画 (PiP)", checked) {
        checked = it
        pref.enablePip = it
        if (it) {
            ToastUtil.show(engine.activity, "离开应用时将自动进入画中画模式", Toast.LENGTH_SHORT)
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp),
         verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, modifier = Modifier.scale(0.8f), onCheckedChange = onToggle)
    }
}
