package com.alexclin.moonlink.android.device.streamsettings

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.stream.ui.DetailScaffold

// ═══════════════════════════════════════════
// 分类定义
// ═══════════════════════════════════════════

private data class CategoryEntry(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

private val CATEGORIES = listOf(
    CategoryEntry("touch", "触控模式", Icons.Default.TouchApp),
    CategoryEntry("display", "显示设置", Icons.Default.Tv),
    CategoryEntry("host", "主机设置", Icons.Default.Computer),
    CategoryEntry("audio", "声音设置", Icons.Default.VolumeUp),
    CategoryEntry("gyro", "体感", Icons.Default.Sensors),
    CategoryEntry("other", "其它", Icons.Default.Extension),
)

// ═══════════════════════════════════════════
// 主页面
// ═══════════════════════════════════════════

/**
 * 主机串流配置页面。
 *
 * 从设备概要页点击"串流设置"进入，将子面板中快捷键以外的设置以六分类展示，
 * 配置持久化到主机级 SharedPreferences（由 [HostSettingsManager] 管理）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStreamSettingsScreen(
    hostname: String,
    uuid: String,
    settingsManager: HostSettingsManager,
    onBack: () -> Unit,
) {
    // 加载当前主机的配置
    var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }

    // 当前选中的分类子页（null = 主页）
    var currentCategory by remember { mutableStateOf<String?>(null) }

    // 页面切换动画
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentCategory,
            transitionSpec = {
                if (targetState == null) {
                    // 返回主页
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200)),
                        initialContentExit = slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200)),
                    )
                } else {
                    // 进入子页
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200)),
                        initialContentExit = slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200)),
                    )
                }
            }
        ) { category ->
            when (category) {
                "touch" -> TouchModeCategory(settings, { settings = it; settingsManager.saveSettings(uuid, it) }, { currentCategory = null })
                "display" -> DisplayCategory(settings, { settings = it; settingsManager.saveSettings(uuid, it) }, { currentCategory = null })
                "host" -> HostCategory(settings, { settings = it; settingsManager.saveSettings(uuid, it) }, { currentCategory = null })
                "audio" -> AudioCategory(settings, { settings = it; settingsManager.saveSettings(uuid, it) }, { currentCategory = null })
                "gyro" -> GyroCategory(settings, { settings = it; settingsManager.saveSettings(uuid, it) }, { currentCategory = null })
                "other" -> OtherCategory(settings, { settings = it; settingsManager.saveSettings(uuid, it) }, { currentCategory = null })
                // 主页
                null -> MainCategoryList(
                    hostname = hostname,
                    categories = CATEGORIES,
                    onSelectCategory = { currentCategory = it },
                    onBack = onBack,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
// 主页：分类列表
// ═══════════════════════════════════════════

@Composable
private fun MainCategoryList(
    hostname: String,
    categories: List<CategoryEntry>,
    onSelectCategory: (String) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "${hostname}串流设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    "选择要调整的配置分类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }

            items(categories) { category ->
                CategoryEntryRow(
                    icon = category.icon,
                    label = category.label,
                    onClick = { onSelectCategory(category.key) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun CategoryEntryRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ═══════════════════════════════════════════
// 通用 UI 组件
// ═══════════════════════════════════════════

/** 开关设置行 */
@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/** 分类标题 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** 单选芯片组 */
@Composable
private fun ChipSelector(
    options: List<Pair<String, String>>, // (label, value)
    selectedValue: String,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = value == selectedValue,
                onClick = { onSelect(value) },
                label = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 展开/收起选择行（带下拉箭头） */
@Composable
private fun ExpandableSelectorRow(
    label: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Slider 行 */
@Composable
private fun SettingSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column {
        Text(
            "$valueLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}

/** Radio 选项 */
@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 分隔线 */
@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

// ═══════════════════════════════════════════
// 触控模式
// ═══════════════════════════════════════════

@Composable
private fun TouchModeCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "触控模式", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 触控模式选择（三选一）
            item {
                SectionTitle("触控模式选择")
                var selectedMode by remember(settings) { mutableStateOf(
                    when {
                        settings.touchscreenTrackpad -> "trackpad"
                        settings.enableEnhancedTouch -> "enhanced"
                        else -> "mouse"
                    }
                ) }
                ChipSelector(
                    options = listOf(
                        "增强式多点触控" to "enhanced",
                        "触控板模式" to "trackpad",
                        "鼠标模式" to "mouse",
                    ),
                    selectedValue = selectedMode,
                    onSelect = { value ->
                        selectedMode = value
                        onSettingsChange(settings.copy(
                            enableEnhancedTouch = value == "enhanced",
                            touchscreenTrackpad = value == "trackpad",
                            enableNativeMousePointer = value == "mouse" && settings.enableNativeMousePointer,
                        ))
                    },
                )
            }

            // ── 触控板模式子选项 ──
            item {
                AnimatedVisibility(visible = settings.touchscreenTrackpad) {
                    Column {
                        Divider()
                        SectionTitle("触控板设置")

                        var sensitivity by remember { mutableFloatStateOf(settings.touchpadSensitivity.toFloat()) }
                        SettingSlider(
                            value = sensitivity,
                            valueRange = 1f..200f,
                            valueLabel = "灵敏度: ${sensitivity.toInt()}",
                            onValueChange = { sensitivity = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(touchpadSensitivity = sensitivity.toInt()))
                            },
                        )

                        SettingSwitchRow(
                            label = "双击按住",
                            checked = settings.enableDoubleClickDrag,
                            onToggle = { onSettingsChange(settings.copy(enableDoubleClickDrag = it)) },
                        )
                        SettingSwitchRow(
                            label = "本地光标渲染",
                            checked = settings.enableLocalCursorRendering,
                            onToggle = { onSettingsChange(settings.copy(enableLocalCursorRendering = it)) },
                        )
                    }
                }
            }

            // ── 鼠标模式子选项 ──
            item {
                AnimatedVisibility(visible = !settings.touchscreenTrackpad && !settings.enableEnhancedTouch) {
                    Column {
                        Divider()
                        SectionTitle("鼠标设置")
                        SettingSwitchRow(
                            label = "本地鼠标指针",
                            checked = settings.enableNativeMousePointer,
                            onToggle = { onSettingsChange(settings.copy(enableNativeMousePointer = it)) },
                        )
                    }
                }
            }

            // ── 增强式多点触控子选项 ──
            item {
                AnimatedVisibility(visible = settings.enableEnhancedTouch) {
                    Column {
                        Divider()
                        SectionTitle("增强触控设置")
                        SettingSwitchRow(
                            label = "增强触控区在右侧",
                            checked = settings.enhancedTouchOnWhichSide,
                            onToggle = { onSettingsChange(settings.copy(enhancedTouchOnWhichSide = it)) },
                        )
                        var zoneDivider by remember { mutableFloatStateOf(settings.enhanceTouchZoneDivider.toFloat()) }
                        SettingSlider(
                            value = zoneDivider,
                            valueRange = 10f..90f,
                            valueLabel = "分区界线: ${zoneDivider.toInt()}%",
                            onValueChange = { zoneDivider = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(enhanceTouchZoneDivider = zoneDivider.toInt()))
                            },
                        )
                        var velocity by remember { mutableFloatStateOf(settings.pointerVelocityFactor.toFloat()) }
                        SettingSlider(
                            value = velocity,
                            valueRange = 10f..500f,
                            valueLabel = "指针速度: ${velocity.toInt()}%",
                            onValueChange = { velocity = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(pointerVelocityFactor = velocity.toInt()))
                            },
                        )
                    }
                }
            }

            // 通用触控设置
            item {
                Divider()
                SectionTitle("通用触控设置")
                SettingSwitchRow(
                    label = "同步触摸事件到显示刷新",
                    checked = settings.syncTouchEventWithDisplay,
                    onToggle = { onSettingsChange(settings.copy(syncTouchEventWithDisplay = it)) },
                )
                var flatRegion by remember { mutableFloatStateOf(settings.longPressFlatRegionPixels.toFloat()) }
                SettingSlider(
                    value = flatRegion,
                    valueRange = 0f..100f,
                    valueLabel = "长按平坦区: ${flatRegion.toInt()}px",
                    onValueChange = { flatRegion = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(longPressFlatRegionPixels = flatRegion.toInt()))
                    },
                )
                var toggleFingers by remember { mutableFloatStateOf(settings.nativeTouchFingersToToggleKeyboard.toFloat()) }
                SettingSlider(
                    value = toggleFingers,
                    valueRange = 0f..5f,
                    valueLabel = "切换键盘手指数: ${toggleFingers.toInt()} (0=禁用)",
                    onValueChange = { toggleFingers = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(nativeTouchFingersToToggleKeyboard = toggleFingers.toInt()))
                    },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DisplayCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "显示设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── 画面 ──
            item { SectionTitle("画面") }

            // 码率预设
            item {
                val presets = listOf(
                    "自动" to "auto",
                    "2M" to "2M",
                    "8M" to "8M",
                    "20M" to "20M",
                    "自定义" to "custom",
                )
                var selectedPreset by remember { mutableStateOf(
                    when {
                        settings.enableAdaptiveBitrate -> "auto"
                        settings.bitrate <= 0 -> "auto"
                        settings.bitrate <= 2000 -> "2M"
                        settings.bitrate <= 8000 -> "8M"
                        settings.bitrate <= 20000 -> "20M"
                        else -> "custom"
                    }
                ) }
                Text("码率设置", style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier.padding(vertical = 6.dp))
                ChipSelector(
                    options = presets,
                    selectedValue = selectedPreset,
                    onSelect = { value ->
                        selectedPreset = value
                        val newBitrate = when (value) {
                            "auto" -> { onSettingsChange(settings.copy(enableAdaptiveBitrate = true, bitrate = 0)); return@ChipSelector }
                            "2M" -> 2000; "8M" -> 8000; "20M" -> 20000; else -> settings.bitrate
                        }
                        onSettingsChange(settings.copy(enableAdaptiveBitrate = false, bitrate = newBitrate))
                    },
                )
            }

            // 自定义码率滑块
            item {
                AnimatedVisibility(visible = selectedPreset == "custom" || settings.bitrate > 20000) {
                    var customKbps by remember { mutableFloatStateOf(
                        if (settings.bitrate > 20000) settings.bitrate.toFloat() else 50000f
                    ) }
                    SettingSlider(
                        value = customKbps,
                        valueRange = 1000f..800000f,
                        valueLabel = "码率: ${(customKbps / 1000).toInt()} Mbps (${customKbps.toInt()} kbps)",
                        onValueChange = { customKbps = it },
                        onValueChangeFinished = {
                            onSettingsChange(settings.copy(enableAdaptiveBitrate = false, bitrate = customKbps.toInt()))
                        },
                    )
                }
            }

            // ABR 模式
            item {
                AnimatedVisibility(visible = settings.enableAdaptiveBitrate) {
                    Column {
                        Text("智能码率模式", style = MaterialTheme.typography.bodyMedium,
                             modifier = Modifier.padding(vertical = 4.dp))
                        val abrModes = listOf("画质优先" to "quality", "均衡" to "balanced", "低延迟" to "lowLatency")
                        abrModes.forEach { (label, value) ->
                            RadioOption(
                                label = label,
                                selected = settings.abrMode == value,
                                onClick = { onSettingsChange(settings.copy(abrMode = value)) },
                            )
                        }
                    }
                }
            }

            // 帧率
            item {
                Divider()
                var unlocked by remember { mutableStateOf(settings.unlockFps) }
                SettingSwitchRow("解锁帧率", unlocked) { unlocked = it; onSettingsChange(settings.copy(unlockFps = it)) }
                val fpsOptions = if (unlocked) listOf(30, 60, 90, 120, 144, 165) else listOf(30, 60, 90, 120)
                Text("视频帧率", style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.padding(vertical = 4.dp))
                fpsOptions.forEach { fps ->
                    RadioOption(
                        label = "${fps} FPS",
                        selected = settings.fps == fps,
                        onClick = { onSettingsChange(settings.copy(fps = fps)) },
                    )
                }
            }

            // 视频编码格式
            item {
                Divider()
                Text("视频编码格式", style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier.padding(vertical = 6.dp))
                ChipSelector(
                    options = listOf(
                        "自动" to "auto",
                        "AV1" to "forceav1",
                        "HEVC" to "forceh265",
                        "H264" to "neverh265",
                    ),
                    selectedValue = settings.videoFormat,
                    onSelect = { onSettingsChange(settings.copy(videoFormat = it)) },
                )
            }

            // 分辨率缩放
            item {
                Divider()
                var scale by remember { mutableFloatStateOf(settings.resolutionScale.toFloat()) }
                SettingSlider(
                    value = scale,
                    valueRange = 50f..400f,
                    valueLabel = "分辨率缩放: ${scale.toInt()}%",
                    onValueChange = { scale = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(resolutionScale = scale.toInt()))
                    },
                )
            }

            // 帧时序模式
            item {
                Divider()
                val pacingModes = listOf(
                    "最低延迟" to 0, "均衡" to 1, "均衡+FPS限制" to 2,
                    "最高流畅度" to 3, "超低延迟(实验)" to 4, "精确同步" to 5,
                )
                Text("帧时序模式", style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier.padding(vertical = 6.dp))
                pacingModes.forEach { (label, value) ->
                    RadioOption(
                        label = label,
                        selected = settings.framePacing == value,
                        onClick = { onSettingsChange(settings.copy(framePacing = value)) },
                    )
                }
            }

            // 输出缓冲区
            item {
                Divider()
                var buf by remember { mutableFloatStateOf(settings.outputBufferQueueLimit.toFloat()) }
                SettingSlider(
                    value = buf,
                    valueRange = 1f..5f,
                    valueLabel = "输出缓冲区: ${buf.toInt()} 帧",
                    onValueChange = { buf = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(outputBufferQueueLimit = buf.toInt()))
                    },
                )
            }

            // HDR
            item {
                Divider()
                SettingSwitchRow("HDR", settings.enableHdr) {
                    onSettingsChange(settings.copy(enableHdr = it))
                }
                AnimatedVisibility(visible = settings.enableHdr) {
                    Column(Modifier.padding(start = 16.dp)) {
                        SettingSwitchRow("HDR 高亮度", settings.enableHdrHighBrightness) {
                            onSettingsChange(settings.copy(enableHdrHighBrightness = it))
                        }
                        Text("HDR模式", style = MaterialTheme.typography.bodyMedium)
                        RadioOption("HDR10/PQ", settings.hdrMode == 1) {
                            onSettingsChange(settings.copy(hdrMode = 1))
                        }
                        RadioOption("HLG", settings.hdrMode == 2) {
                            onSettingsChange(settings.copy(hdrMode = 2))
                        }
                    }
                }
            }

            // PiP
            item {
                Divider()
                SettingSwitchRow("画中画 (PiP)", settings.enablePip) {
                    onSettingsChange(settings.copy(enablePip = it))
                }
            }

            // ── 开关组 ──
            item {
                Divider()
                SectionTitle("画面开关")
                SettingSwitchRow("拉伸视频", settings.stretchVideo) {
                    onSettingsChange(settings.copy(stretchVideo = it))
                }
                SettingSwitchRow("反转分辨率", settings.reverseResolution) {
                    onSettingsChange(settings.copy(reverseResolution = it))
                }
                SettingSwitchRow("可旋转画面", settings.rotableScreen) {
                    onSettingsChange(settings.copy(rotableScreen = it))
                }
                SettingSwitchRow("减少刷新率", settings.reduceRefreshRate) {
                    onSettingsChange(settings.copy(reduceRefreshRate = it))
                }
                SettingSwitchRow("全色域", settings.fullRange) {
                    onSettingsChange(settings.copy(fullRange = it))
                }
                SettingSwitchRow("MTK 专属选项", settings.forceMtkMaxOperatingRate) {
                    onSettingsChange(settings.copy(forceMtkMaxOperatingRate = it))
                }
                SettingSwitchRow("使用外接显示器", settings.useExternalDisplay) {
                    onSettingsChange(settings.copy(useExternalDisplay = it))
                }
            }

            // 画面位置
            item {
                Divider()
                SectionTitle("画面位置")
                val positions = listOf(
                    "左上" to "top_left", "中上" to "top_center", "右上" to "top_right",
                    "左中" to "center_left", "居中" to "center", "右中" to "center_right",
                    "左下" to "bottom_left", "中下" to "bottom_center", "右下" to "bottom_right",
                )
                ChipSelector(
                    options = positions,
                    selectedValue = settings.screenPosition,
                    onSelect = { onSettingsChange(settings.copy(screenPosition = it)) },
                )
                var offsetX by remember { mutableFloatStateOf(settings.screenOffsetX.toFloat()) }
                SettingSlider(
                    value = offsetX,
                    valueRange = -100f..100f,
                    valueLabel = "X 偏移: ${offsetX.toInt()}",
                    onValueChange = { offsetX = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(screenOffsetX = offsetX.toInt()))
                    },
                )
                var offsetY by remember { mutableFloatStateOf(settings.screenOffsetY.toFloat()) }
                SettingSlider(
                    value = offsetY,
                    valueRange = -100f..100f,
                    valueLabel = "Y 偏移: ${offsetY.toInt()}",
                    onValueChange = { offsetY = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(screenOffsetY = offsetY.toInt()))
                    },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HostCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "主机设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 实时生效
            item {
                SettingSwitchRow("断开串流时锁定屏幕", settings.lockScreenAfterDisconnect) {
                    onSettingsChange(settings.copy(lockScreenAfterDisconnect = it))
                }
            }

            // 需重启串流生效的选项
            item {
                Divider()
                SectionTitle("需重启串流生效")
                SettingSwitchRow("自动优化主机设置 (SOPS)", settings.enableSops) {
                    onSettingsChange(settings.copy(enableSops = it))
                }
                SettingSwitchRow("在电脑上播放声音", settings.playHostAudio) {
                    onSettingsChange(settings.copy(playHostAudio = it))
                }
                SettingSwitchRow("静音客户端音频", settings.muteClientAudio) {
                    onSettingsChange(settings.copy(muteClientAudio = it))
                }
                SettingSwitchRow("仅控制模式", settings.controlOnly) {
                    onSettingsChange(settings.copy(controlOnly = it))
                }
                SettingSwitchRow("同步剪贴板文本", settings.enableClipboardSyncText) {
                    onSettingsChange(settings.copy(enableClipboardSyncText = it))
                }
                SettingSwitchRow("同步剪贴板图片", settings.enableClipboardSyncImage) {
                    onSettingsChange(settings.copy(enableClipboardSyncImage = it))
                }
            }

            // 菜单设置
            item {
                Divider()
                SectionTitle("菜单设置")
                SettingSwitchRow("启用 ESC 菜单", settings.enableEscMenu) {
                    onSettingsChange(settings.copy(enableEscMenu = it))
                }
                SettingSwitchRow("启用 Start 键菜单", settings.enableStartKeyMenu) {
                    onSettingsChange(settings.copy(enableStartKeyMenu = it))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AudioCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "声音设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 环绕声
            item {
                SectionTitle("输出设置")
                Text("环绕声", style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.padding(bottom = 4.dp))
                val surroundOptions = listOf(
                    "立体声" to "0",
                    "5.1 环绕声" to "2",
                    "7.1 环绕声" to "4",
                )
                ChipSelector(
                    options = surroundOptions,
                    selectedValue = settings.audioConfiguration,
                    onSelect = { onSettingsChange(settings.copy(audioConfiguration = it)) },
                )
            }

            // 均衡器 / 空间音频
            item {
                Divider()
                SettingSwitchRow("均衡器", settings.enableAudioFx) {
                    onSettingsChange(settings.copy(enableAudioFx = it))
                }
                SettingSwitchRow("空间音频", settings.enableSpatializer) {
                    onSettingsChange(settings.copy(enableSpatializer = it))
                }
            }

            // 音频直通
            item {
                Divider()
                SectionTitle("音频直通")
                SettingSwitchRow("启用音频直通", settings.enableAudioPassthrough) {
                    onSettingsChange(settings.copy(enableAudioPassthrough = it))
                }
                AnimatedVisibility(visible = settings.enableAudioPassthrough) {
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("直通编解码器", style = MaterialTheme.typography.bodyMedium)
                        val codecOptions = listOf("自动" to "auto", "Opus" to "opus", "AC3" to "ac3", "E-AC3" to "eac3")
                        codecOptions.forEach { (label, value) ->
                            RadioOption(label, settings.audioCodec == value) {
                                onSettingsChange(settings.copy(audioCodec = value))
                            }
                        }
                        Text("直通缓冲区", style = MaterialTheme.typography.bodyMedium)
                        val bufOptions = listOf("低延迟" to "low", "正常" to "normal", "高兼容" to "high")
                        ChipSelector(
                            options = bufOptions,
                            selectedValue = settings.audioPassthroughBuffer,
                            onSelect = { onSettingsChange(settings.copy(audioPassthroughBuffer = it)) },
                        )
                    }
                }
            }

            // 音频驱动振动
            item {
                Divider()
                SectionTitle("音频驱动振动")
                SettingSwitchRow("启用音频振动", settings.enableAudioVibration) {
                    onSettingsChange(settings.copy(enableAudioVibration = it))
                }
                AnimatedVisibility(visible = settings.enableAudioVibration) {
                    Column(Modifier.padding(start = 16.dp)) {
                        var strength by remember { mutableFloatStateOf(settings.audioVibrationStrength.toFloat()) }
                        SettingSlider(
                            value = strength,
                            valueRange = 0f..200f,
                            valueLabel = "振动强度: ${strength.toInt()}",
                            onValueChange = { strength = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(audioVibrationStrength = strength.toInt()))
                            },
                        )
                        Text("振动路由", style = MaterialTheme.typography.bodyMedium)
                        val modeOptions = listOf("自动" to "auto", "仅扬声器" to "speaker", "仅耳机" to "headset")
                        ChipSelector(
                            options = modeOptions,
                            selectedValue = settings.audioVibrationMode,
                            onSelect = { onSettingsChange(settings.copy(audioVibrationMode = it)) },
                        )
                        Text("场景模式", style = MaterialTheme.typography.bodyMedium)
                        val sceneOptions = listOf("通用" to 0, "游戏" to 1, "电影" to 2, "音乐" to 3)
                        sceneOptions.forEach { (label, value) ->
                            RadioOption(label, settings.audioVibrationScene == value) {
                                onSettingsChange(settings.copy(audioVibrationScene = value))
                            }
                        }
                    }
                }
            }

            // 麦克风
            item {
                Divider()
                SectionTitle("麦克风")
                SettingSwitchRow("麦克风重定向", settings.enableMic) {
                    onSettingsChange(settings.copy(enableMic = it))
                }
                AnimatedVisibility(visible = settings.enableMic) {
                    Column(Modifier.padding(start = 16.dp)) {
                        var micRate by remember { mutableFloatStateOf(settings.micBitrate.toFloat()) }
                        SettingSlider(
                            value = micRate,
                            valueRange = 32f..256f,
                            valueLabel = "传输音质: ${micRate.toInt()} kbps",
                            onValueChange = { micRate = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(micBitrate = micRate.toInt()))
                            },
                        )
                        Text("图标颜色", style = MaterialTheme.typography.bodyMedium)
                        val colorOptions = listOf("纯白" to "solid_white", "强调色" to "accent")
                        ChipSelector(
                            options = colorOptions,
                            selectedValue = settings.micIconColor,
                            onSelect = { onSettingsChange(settings.copy(micIconColor = it)) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GyroCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "体感", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 体感总开关（当前激活的模式）
            item {
                val gyroActive = settings.gyroToRightStick || settings.gyroToMouse
                SettingSwitchRow("体感开关", gyroActive) { enabled ->
                    if (!enabled) {
                        onSettingsChange(settings.copy(gyroToRightStick = false, gyroToMouse = false))
                    } else {
                        // 默认启用右摇杆模式
                        onSettingsChange(settings.copy(gyroToRightStick = true, gyroToMouse = false))
                    }
                }
            }

            item {
                Divider()
                SectionTitle("模式")
                RadioOption("右摇杆", settings.gyroToRightStick && !settings.gyroToMouse) {
                    onSettingsChange(settings.copy(gyroToRightStick = true, gyroToMouse = false))
                }
                RadioOption("鼠标", settings.gyroToMouse) {
                    onSettingsChange(settings.copy(gyroToRightStick = false, gyroToMouse = true))
                }
            }

            // 灵敏度
            item {
                Divider()
                var sensitivity by remember { mutableFloatStateOf(settings.gyroSensitivityMultiplier) }
                SettingSlider(
                    value = sensitivity,
                    valueRange = 0.5f..3.0f,
                    valueLabel = "灵敏度: ${"%.1f".format(sensitivity)}x",
                    onValueChange = { sensitivity = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(gyroSensitivityMultiplier = sensitivity))
                    },
                )
            }

            // X/Y 轴反转
            item {
                Divider()
                SectionTitle("轴反转")
                SettingSwitchRow("X 轴反转", settings.gyroInvertXAxis) {
                    onSettingsChange(settings.copy(gyroInvertXAxis = it))
                }
                SettingSwitchRow("Y 轴反转", settings.gyroInvertYAxis) {
                    onSettingsChange(settings.copy(gyroInvertYAxis = it))
                }
            }

            // 激活按键
            item {
                Divider()
                SectionTitle("激活按键")
                val keyOptions = listOf("始终" to 0, "LT" to KeyEvent.KEYCODE_BUTTON_L2, "RT" to KeyEvent.KEYCODE_BUTTON_R2)
                ChipSelector(
                    options = keyOptions.map { (label, value) -> label to value.toString() },
                    selectedValue = settings.gyroActivationKeyCode.toString(),
                    onSelect = { value ->
                        onSettingsChange(settings.copy(gyroActivationKeyCode = value.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_L2))
                    },
                )
            }

            // 显示体感卡片
            item {
                Divider()
                SettingSwitchRow("在串流中显示体感卡片", settings.showGyroCard) {
                    onSettingsChange(settings.copy(showGyroCard = it))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun OtherCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "其它设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 性能监控图层
            item {
                SectionTitle("性能与显示")
                var perfEnabled by remember { mutableStateOf(settings.enablePerfOverlay) }
                SettingSwitchRow("启用性能图层", perfEnabled) {
                    perfEnabled = it; onSettingsChange(settings.copy(enablePerfOverlay = it))
                }
                AnimatedVisibility(visible = perfEnabled) {
                    Column(Modifier.padding(start = 16.dp)) {
                        var bgAlpha by remember { mutableFloatStateOf(settings.perfOverlayBgOpacity / 100f) }
                        SettingSlider(
                            value = bgAlpha,
                            valueRange = 0f..1f,
                            valueLabel = "背景不透明度: ${(bgAlpha * 100).toInt()}%",
                            onValueChange = { bgAlpha = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(perfOverlayBgOpacity = (bgAlpha * 100).toInt()))
                            },
                        )
                        SettingSwitchRow("锁定性能图层", settings.perfOverlayLocked) {
                            onSettingsChange(settings.copy(perfOverlayLocked = it))
                        }
                        Text("性能图层详细设置请到主页-设置-性能和统计分析中设置",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                             modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // 延迟 Toast
            item {
                Divider()
                SettingSwitchRow("串流结束后显示延迟信息", settings.enableLatencyToast) {
                    onSettingsChange(settings.copy(enableLatencyToast = it))
                }
            }

            // 悬浮按钮不透明度
            item {
                Divider()
                SectionTitle("悬浮元素")
                var fabAlpha by remember { mutableFloatStateOf(settings.fabOpacity / 100f) }
                SettingSlider(
                    value = fabAlpha,
                    valueRange = 0.1f..1.0f,
                    valueLabel = "悬浮按钮不透明度: ${(fabAlpha * 100).toInt()}%",
                    onValueChange = { fabAlpha = it },
                    onValueChangeFinished = {
                        onSettingsChange(settings.copy(fabOpacity = (fabAlpha * 100).toInt().coerceIn(10, 100)))
                    },
                )
            }

            // 操作面板自动隐藏
            item {
                Divider()
                Text("操作面板自动隐藏", style = MaterialTheme.typography.bodyMedium)
                val hideOptions = listOf(
                    "开启按键映射时隐藏" to 0,
                    "2秒后自动隐藏" to 1,
                    "不自动隐藏" to 2,
                )
                hideOptions.forEach { (label, value) ->
                    RadioOption(label, settings.toolPanelAutoHideMode == value) {
                        onSettingsChange(settings.copy(toolPanelAutoHideMode = value))
                    }
                }
            }

            // 悬浮球设置
            item {
                Divider()
                SectionTitle("悬浮球")
                SettingSwitchRow("启用悬浮球", settings.enableFloatBall) {
                    onSettingsChange(settings.copy(enableFloatBall = it))
                }
                AnimatedVisibility(visible = settings.enableFloatBall) {
                    Column(Modifier.padding(start = 16.dp)) {
                        var hideDelay by remember { mutableFloatStateOf(settings.floatBallAutoHideDelay.toFloat()) }
                        SettingSlider(
                            value = hideDelay,
                            valueRange = 500f..10000f,
                            valueLabel = "自动隐藏延迟: ${hideDelay.toInt()} ms",
                            onValueChange = { hideDelay = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(floatBallAutoHideDelay = hideDelay.toInt()))
                            },
                        )

                        Text("单击动作", style = MaterialTheme.typography.bodySmall)
                        val actionOptions = listOf(
                            "打开键盘" to "open_keyboard",
                            "打开菜单" to "open_menu",
                            "切换可见性" to "toggle_visibility",
                            "无操作" to "none",
                        )
                        ChipSelector(
                            options = actionOptions,
                            selectedValue = settings.floatBallSingleClickAction,
                            onSelect = { onSettingsChange(settings.copy(floatBallSingleClickAction = it)) },
                        )

                        // 双击/长按/滑动保持简洁，使用 Radio 列表
                        Text("双击动作", style = MaterialTheme.typography.bodySmall,
                             modifier = Modifier.padding(top = 8.dp))
                        actionOptions.forEach { (label, value) ->
                            RadioOption(label, settings.floatBallDoubleClickAction == value) {
                                onSettingsChange(settings.copy(floatBallDoubleClickAction = value))
                            }
                        }
                        Text("长按动作", style = MaterialTheme.typography.bodySmall,
                             modifier = Modifier.padding(top = 4.dp))
                        actionOptions.forEach { (label, value) ->
                            RadioOption(label, settings.floatBallLongClickAction == value) {
                                onSettingsChange(settings.copy(floatBallLongClickAction = value))
                            }
                        }
                    }
                }
            }

            // 卡片可见性
            item {
                Divider()
                SectionTitle("快速信息卡片")
                SettingSwitchRow("显示码率卡片", settings.showBitrateCard) {
                    onSettingsChange(settings.copy(showBitrateCard = it))
                }
                SettingSwitchRow("显示快捷卡片", settings.showQuickKeyCard) {
                    onSettingsChange(settings.copy(showQuickKeyCard = it))
                }
            }

            // 按键映射
            item {
                Divider()
                SectionTitle("按键映射")
                SettingSwitchRow("启用按键映射", settings.keyMappingEnabled) {
                    onSettingsChange(settings.copy(keyMappingEnabled = it))
                }
            }

            // 禁用警告
            item {
                Divider()
                SettingSwitchRow("禁用警告提示", settings.disableWarnings) {
                    onSettingsChange(settings.copy(disableWarnings = it))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
