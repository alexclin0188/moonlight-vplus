package com.alexclin.moonlink.stream.ui.display

import android.content.Context
import android.graphics.Point
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FPS_AUTO_PREF = "fps_auto"

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

            // DB-3: HDR
            item { HdrSection(engine) }

            // DC-2: 输出缓冲区滑块
            item { OutputBufferSlider(engine) }

            // DC-2: MTK
            item { MtkSwitch(engine) }

            // DC-3: VDD + 外接
            item { VddSection(engine) }

            // DC-2: 画面设置开关组
            item { VideoSwitches(engine) }

            // ── 分组二：显示器 ──
            item { SectionTitle("显示器") }

            // DD-1: 显示器列表
            item { MonitorList(engine) }

            // DD-2: 分辨率 + DPI缩放
            item { MonitorSettings(engine) }
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

    var expanded by remember { mutableStateOf(false) }
    var unlockFps by remember { mutableStateOf(pref.unlockFps) }
    val fpsAuto = fpsAutoPref.getBoolean(FPS_AUTO_PREF, false)
    var selectedFps by remember { mutableIntStateOf(if (fpsAuto) 0 else pref.fps) }

    val baseFpsOptions = listOf(0, 30, 60, 90, 120)  // 0 = 自动
    val extraFpsOptions = listOf(144, 165)
    val currentFpsText = if (selectedFps == 0) "自动" else "${selectedFps}"

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
            Text("自动/$currentFpsText", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 8.dp)) {
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
                    }
                }
                if (unlockFps) {
                    extraFpsOptions.forEach { fps ->
                        FpsRadioItem(fps, "${fps} FPS", selectedFps == fps) {
                            selectedFps = it
                            fpsAutoPref.edit().putBoolean(FPS_AUTO_PREF, false).apply()
                            pref.fps = it
                            pref.writePreferences(context)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("解锁帧率", Modifier.weight(1f),
                         style = MaterialTheme.typography.bodySmall)
                    Switch(checked = unlockFps, onCheckedChange = {
                        unlockFps = it
                        pref.unlockFps = it
                        pref.writePreferences(context)
                    })
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
    var sliderProgress by remember { mutableFloatStateOf(0f) }
    var customKbps by remember {
        mutableIntStateOf(pref.bitrate.coerceIn(
            BitrateUtils.BITRATE_CUSTOM_MIN, BitrateUtils.BITRATE_CUSTOM_MAX,
        ))
    }
    // U4: 当前有效码率（跟随所有 preset/slider 变化）
    var effectiveBitrate by remember { mutableIntStateOf(pref.bitrate) }

    LaunchedEffect(Unit) {
        if (selectedPreset == BitrateUtils.BitratePreset.CUSTOM) {
            sliderProgress = BitrateUtils.customKbpsToProgress(pref.bitrate).toFloat()
        }
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = {
                        selectedPreset = preset
                        onBitratePresetSelected(engine, preset, context, customKbps)
                        // 更新流量估算用码率
                        effectiveBitrate = when (preset) {
                            BitrateUtils.BitratePreset.AUTO -> 0
                            BitrateUtils.BitratePreset.M2 -> BitrateUtils.BITRATE_2M
                            BitrateUtils.BitratePreset.M8 -> BitrateUtils.BITRATE_8M
                            BitrateUtils.BitratePreset.M20 -> BitrateUtils.BITRATE_20M
                            BitrateUtils.BitratePreset.CUSTOM -> customKbps
                        }
                    },
                    label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // U4: 流量估算（始终跟随 effectiveBitrate 变化）
        TrafficEstimateLine(effectiveBitrate)

        // 自动 → 智能码率模式
        AnimatedVisibility(visible = selectedPreset == BitrateUtils.BitratePreset.AUTO) {
            Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text("智能码率模式", style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.primary)
                listOf(
                    "quality" to "画质优先",
                    "balanced" to "均衡",
                    "lowLatency" to "低延迟",
                ).forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onAbrModeSelected(engine, value, context) }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(selected = abrMode == value,
                             onClick = { onAbrModeSelected(engine, value, context) })
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // 自定义 → SeekBar
        AnimatedVisibility(visible = selectedPreset == BitrateUtils.BitratePreset.CUSTOM) {
            Column(Modifier.padding(top = 8.dp)) {
                val displayKbps = BitrateUtils.customProgressToKbps(sliderProgress.toInt())
                Text("码率: ${displayKbps / 1000} Mbps ($displayKbps kbps)",
                     style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = sliderProgress,
                    onValueChange = { sliderProgress = it
                        // U4: 拖动过程中实时更新估算
                        effectiveBitrate = BitrateUtils.customProgressToKbps(it.toInt())
                    },
                    onValueChangeFinished = {
                        val kbps = BitrateUtils.customProgressToKbps(sliderProgress.toInt())
                        customKbps = kbps
                        effectiveBitrate = kbps
                        pref.bitrate = kbps
                        pref.enableAdaptiveBitrate = false
                        pref.writePreferences(context)
                        engine.conn?.setBitrate(kbps, null)
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
    }
}

@Composable
private fun TrafficEstimateLine(bitrateKbps: Int) {
    if (bitrateKbps > 0) {
        Text(
            text = BitrateUtils.formatTrafficEstimate(bitrateKbps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
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
    pref.writePreferences(context)
    engine.conn?.setBitrate(kbps, null)
}

private fun onAbrModeSelected(
    engine: StreamEngine, mode: String, context: android.content.Context,
) {
    engine.prefConfig.abrMode = mode
    engine.prefConfig.writePreferences(context)
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

    Column {
        SettingSwitch("HDR", hdrEnabled) {
            hdrEnabled = it; pref.enableHdr = it; pref.writePreferences(context)
        }
        AnimatedVisibility(visible = hdrEnabled) {
            SettingSwitch("  HDR高亮度", highBrightness,
                modifier = Modifier.padding(start = 24.dp)) {
                highBrightness = it; pref.enableHdrHighBrightness = it; pref.writePreferences(context)
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
    var sliderValue by remember { mutableFloatStateOf(initVal.toFloat()) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("输出缓冲区大小", style = MaterialTheme.typography.bodyLarge)
        }
        Text("当前: ${sliderValue.toInt()} 帧",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                pref.outputBufferQueueLimit = sliderValue.toInt().coerceIn(1, 5)
                pref.writePreferences(context)
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
        SettingSwitch("拉伸视频", stretch) { stretch = it; pref.stretchVideo = it; pref.writePreferences(context) }
        SettingSwitch("反转分辨率", reverse) { reverse = it; pref.reverseResolution = it; pref.writePreferences(context) }
        SettingSwitch("可旋转画面", rotable) { rotable = it; pref.rotableScreen = it; pref.writePreferences(context) }
    }
}

// ══════════════════════════════════════════
// DC-3: VddSection (B2 fix: read actual engine state)
// ══════════════════════════════════════════

@Composable
private fun VddSection(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var useVdd by remember { mutableStateOf(engine.pcUseVdd) }
    var useExternal by remember { mutableStateOf(pref.useExternalDisplay) }

    Column {
        SettingSwitch("VDD虚拟显示器", useVdd) {
            useVdd = it
            Toast.makeText(context,
                if (it) "VDD开关已更改，重启串流后生效"
                else "VDD开关已关闭，重启串流后生效",
                Toast.LENGTH_SHORT).show()
        }
        SettingSwitch("使用外接显示器", useExternal) {
            useExternal = it
            pref.useExternalDisplay = it
            pref.writePreferences(context)
        }
    }
}

// ══════════════════════════════════════════
// DD-1: MonitorList  (B1 fix: update selectedIndex on click)
// ══════════════════════════════════════════

@Composable
private fun MonitorList(engine: StreamEngine) {
    val context = LocalContext.current
    var displays by remember { mutableStateOf<List<com.limelight.nvstream.http.NvHTTP.DisplayInfo>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val conn = engine.conn ?: return@withContext
                val list = conn.createNvHttp()?.getDisplays() ?: emptyList()
                displays = list
                if (list.isNotEmpty() && selectedIndex < 0) {
                    selectedIndex = list.indexOfFirst { it.isPrimary }.coerceAtLeast(0)
                }
            } catch (_: Exception) {}
            loading = false
        }
    }

    Column {
        if (loading) {
            Text("正在获取显示器列表...", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (displays.isEmpty()) {
            Text("无法获取显示器列表", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            displays.forEachIndexed { index, display ->
                val isActive = index == selectedIndex
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedIndex = index       // B1: update visual selection
                            Toast.makeText(context, "显示器切换需重启串流生效", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isActive,
                        onClick = {
                            selectedIndex = index       // B1: update visual selection
                            Toast.makeText(context, "显示器切换需重启串流生效", Toast.LENGTH_SHORT).show()
                        })
                    Spacer(Modifier.width(8.dp))
                    Text(display.name, modifier = Modifier.weight(1f))
                    if (isActive) {
                        Text("[active]", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.primary)
                    }
                    if (display.isPrimary) {
                        Text("[主]", style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════
// DD-2: MonitorSettings (分辨率 + DPI缩放)
//         U2: rememberCoroutineScope + Toast on DPI failure
//         U3: mutable customResSet, refresh after save
// ══════════════════════════════════════════

@Composable
private fun MonitorSettings(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val scope = rememberCoroutineScope()   // U2

    val standardResolutions = listOf(
        "640x360", "854x480", "1280x720",
        "1920x1080", "2560x1440", "3840x2160",
    )

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
            "${size.x}x${size.y}"
        })
    }

    // U3: mutable state — refreshable after adding custom resolution
    var customResSet by remember { mutableStateOf(loadCustomResolutions(context)) }

    val allResolutions = remember {
        standardResolutions +
            listOf("Native ($nativeRes)") +
            customResSet.map { "$it (自定义)" }
    }

    var selectedRes by remember { mutableStateOf("${pref.width}x${pref.height}") }
    var showCustomDialog by remember { mutableStateOf(false) }

    Column {
        Text("当前分辨率", style = MaterialTheme.typography.labelLarge,
             color = MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Text("${pref.width} × ${pref.height}",
             style = MaterialTheme.typography.bodyMedium,
             modifier = Modifier.padding(bottom = 8.dp))

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
                    customResSet = loadCustomResolutions(context)   // U3: refresh list
                    showCustomDialog = false
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ── DPI 缩放 (U2: rememberCoroutineScope + Toast) ──
        var scalePercent by remember { mutableIntStateOf(100) }
        var scaleSupported by remember { mutableStateOf(false) }
        var supportedScales by remember { mutableStateOf(listOf(100)) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    val conn = engine.conn ?: return@withContext
                    val displays = conn.createNvHttp()?.getDisplays() ?: emptyList()
                    val primaryDisplay = displays.find { it.isPrimary } ?: displays.firstOrNull()
                    if (primaryDisplay != null && primaryDisplay.scaleSetSupported) {
                        scaleSupported = true
                        scalePercent = primaryDisplay.currentScalePercent
                        supportedScales = primaryDisplay.supportedScalePercents
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
                                    // U2: lifecycle-aware scope + failure Toast
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            try {
                                                val conn = engine.conn ?: return@withContext false
                                                conn.createNvHttp()?.setDisplayScale(scale) == true
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
    val actualRes = if (res == nativeRes) "Native" else res
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
