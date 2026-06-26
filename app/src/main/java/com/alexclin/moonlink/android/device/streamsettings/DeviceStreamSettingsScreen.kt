package com.alexclin.moonlink.android.device.streamsettings

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.alexclin.moonlink.android.stream.ui.common.ChipSelector
import com.alexclin.moonlink.android.stream.ui.common.CompactChip
import com.alexclin.moonlink.android.stream.ui.panels.SchemeInfo
import com.alexclin.moonlink.android.stream.ui.panels.loadUserSchemes
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import androidx.preference.PreferenceManager
import com.limelight.preferences.CustomResolutionsConsts

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
// 主页面（分类列表）
// ═══════════════════════════════════════════

/**
 * 主机串流配置分类列表页。
 *
 * 从设备概要页点击"串流设置"进入，展示六个配置分类入口。
 * 各分类子页为独立 NavHost 路由，由 [MoonLinkApp] 统一管理导航和标题栏。
 */
@Composable
fun DeviceStreamSettingsScreen(
    hostname: String,
    onNavigateToCategory: (String) -> Unit,
    onBack: () -> Unit,
) {
    MainCategoryList(
        hostname = hostname,
        categories = CATEGORIES,
        onSelectCategory = onNavigateToCategory,
    )
}

// ═══════════════════════════════════════════
// 主页：分类列表
// ═══════════════════════════════════════════

@Composable
private fun MainCategoryList(
    hostname: String,
    categories: List<CategoryEntry>,
    onSelectCategory: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
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
        Switch(checked = checked, modifier = Modifier.scale(0.8f), onCheckedChange = onToggle)
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

/** 分隔线 */
@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

// ═══════════════════════════════════════════
// 触控模式
// ═══════════════════════════════════════════

@Composable
fun TouchModeCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
) {
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
                        var flatRegion by remember { mutableFloatStateOf(settings.longPressFlatRegionPixels.toFloat()) }
                        SettingSlider(
                            value = flatRegion,
                            valueRange = 0f..250f,
                            valueLabel = "长按抖动消除: ${flatRegion.toInt()}px",
                            onValueChange = { flatRegion = it },
                            onValueChangeFinished = {
                                onSettingsChange(settings.copy(longPressFlatRegionPixels = flatRegion.toInt()))
                            },
                        )
                    }
                }
            }

            // ── 按键映射 ──
            item {
                Divider()
                SectionTitle("按键映射")
                SettingSwitchRow("启用按键映射", settings.keyMappingEnabled) {
                    onSettingsChange(settings.copy(keyMappingEnabled = it))
                }
                AnimatedVisibility(visible = settings.keyMappingEnabled) {
                    Column(Modifier.padding(start = 16.dp)) {
                        val context = LocalContext.current
                        var schemes by remember { mutableStateOf<List<SchemeInfo>>(emptyList()) }
                        var showSchemeDialog by remember { mutableStateOf(false) }
                        val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                        var currentConfigId by remember {
                            mutableLongStateOf(prefs.getLong(StreamEngine.PREF_CURRENT_CONFIG_ID, 0L))
                        }
                        var currentSchemeName by remember { mutableStateOf("加载中...") }

                        LaunchedEffect(Unit) {
                            val loaded = loadUserSchemes(context)
                            schemes = loaded
                            currentSchemeName = if (currentConfigId == 0L) "内置虚拟手柄方案"
                                else loaded.find { it.configId == currentConfigId }?.name ?: "未知"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSchemeDialog = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("当前方案", style = MaterialTheme.typography.bodyMedium,
                                 modifier = Modifier.weight(1f))
                            Text(currentSchemeName, style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null,
                                 modifier = Modifier.size(18.dp),
                                 tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        TextButton(onClick = { showSchemeDialog = true }) {
                            Text("切换方案")
                        }

                        if (showSchemeDialog) {
                            val allSchemes = listOf(
                                SchemeInfo(configId = 0L, name = "内置虚拟手柄方案")
                            ) + schemes
                            AlertDialog(
                                onDismissRequest = { showSchemeDialog = false },
                                title = { Text("选择按键映射方案") },
                                text = {
                                    Column {
                                        allSchemes.forEach { scheme ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        currentConfigId = scheme.configId
                                                        currentSchemeName = scheme.name
                                                        prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, scheme.configId).apply()
                                                        showSchemeDialog = false
                                                    }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = scheme.configId == currentConfigId,
                                                    onClick = {
                                                        currentConfigId = scheme.configId
                                                        currentSchemeName = scheme.name
                                                        prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, scheme.configId).apply()
                                                        showSchemeDialog = false
                                                    },
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(scheme.name, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showSchemeDialog = false }) { Text("关闭") }
                                },
                            )
                        }

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
                var toggleFingers by remember { mutableFloatStateOf(settings.nativeTouchFingersToToggleKeyboard.toFloat()) }
                SettingSlider(
                    value = toggleFingers,
                    valueRange = 0f..10f,
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

@Composable
fun DisplayCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
) {
    val presets = listOf(
        "自动" to "auto",
        "2M" to "2M",
        "8M" to "8M",
        "20M" to "20M",
        "自定义" to "custom",
    )
    var selectedPreset by remember(settings) {
        mutableStateOf(
            when {
                settings.enableAdaptiveBitrate -> "auto"
                settings.bitrate <= 0 -> "auto"
                settings.bitrate <= 2000 -> "2M"
                settings.bitrate <= 8000 -> "8M"
                settings.bitrate <= 20000 -> "20M"
                else -> "custom"
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // ── 画面 ──
        item { SectionTitle("画面") }

        // 码率预设
        item {
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
            AnimatedVisibility(visible =
                selectedPreset == "custom" || settings.bitrate > 20000) {
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
                        ChipSelector(
                            options = listOf("画质优先" to "quality", "均衡" to "balanced", "低延迟" to "lowLatency"),
                            selectedValue = settings.abrMode,
                            onSelect = { onSettingsChange(settings.copy(abrMode = it)) },
                        )
                    }
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

            // 帧率
            item {
                Divider()
                val unlocked = settings.unlockFps
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
                        checked = unlocked,
                        onCheckedChange = { onSettingsChange(settings.copy(unlockFps = it)) },
                        modifier = Modifier.height(20.dp).scale(0.8f),
                    )
                }
                val baseFps = listOf(30, 60, 90, 120)
                val extraFps = listOf(144, 165)
                val allFps = baseFps + if (unlocked) extraFps else emptyList()
                ChipSelector(
                    options = allFps.map { fps ->
                        "${fps}FPS" to fps.toString()
                    },
                    selectedValue = settings.fps.toString(),
                    onSelect = { value ->
                        val newFps = value.toIntOrNull() ?: 60
                        onSettingsChange(settings.copy(fps = newFps))
                    },
                    columns = 5,
                )
            }

            // 帧时序模式
            item {
                Divider()
                Text("帧时序模式", style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier.padding(vertical = 6.dp))
                ChipSelector(
                    options = listOf(
                        "最低延迟" to "0", "均衡" to "1", "均衡+FPS限制" to "2",
                        "最高流畅度" to "3", "超低延迟(实验)" to "4", "精确同步" to "5",
                    ),
                    selectedValue = settings.framePacing.toString(),
                    onSelect = { value ->
                        onSettingsChange(settings.copy(framePacing = value.toIntOrNull() ?: 0))
                    },
                    columns = 3,
                    spacingDp = 6,
                )
            }

            // ── 分辨率选择（预设 + 自定义） ──
            item {
                Divider()
                var showCustomDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "分辨率",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).weight(1f),
                    )
                    TextButton(onClick = { showCustomDialog = true }) {
                        Text("+自定义分辨率", style = MaterialTheme.typography.labelSmall)
                    }
                }
                val context = LocalContext.current

                val standardResolutions = listOf(
                    "640x360", "854x480", "1280x720",
                    "1920x1080", "2560x1440", "3840x2160",
                )

                // 获取设备原生分辨率
                val nativeRes = remember {
                    val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
                    val size = Point()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        display.getRealSize(size)
                    } else {
                        display.getSize(size)
                    }
                    val w = maxOf(size.x, size.y)
                    val h = minOf(size.x, size.y)
                    "${w}x${h}"
                }

                // 加载自定义分辨率（与主页设置-自定义分辨率共享数据源）
                val customResSet = remember {
                    context.getSharedPreferences(
                        CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE
                    ).getStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, emptySet())
                        ?.sortedBy { it } ?: emptyList()
                }

                val allResolutions = remember {
                    val nativeLabel = if (nativeRes !in standardResolutions) {
                        listOf("Native ($nativeRes)")
                    } else emptyList()
                    standardResolutions + nativeLabel + customResSet.map { "$it (自定义)" }
                }

                val currentRes = if (settings.width > 0 && settings.height > 0)
                    "${settings.width}x${settings.height}"
                else "1920x1080"
                var selectedRes by remember(settings) { mutableStateOf(currentRes) }

                // 3列网格显示分辨率芯片
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    allResolutions.chunked(3).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowItems.forEach { res ->
                                val cleanRes = res
                                    .replace(" (自定义)", "")
                                    .replace("Native (", "").replace(")", "")
                                val isSelected = selectedRes == cleanRes
                                CompactChip(
                                    label = res,
                                    selected = isSelected,
                                    onClick = {
                                        selectedRes = cleanRes
                                        val parts = cleanRes.split("x")
                                        if (parts.size == 2) {
                                            val w = parts[0].toIntOrNull() ?: settings.width
                                            val h = parts[1].toIntOrNull() ?: settings.height
                                            onSettingsChange(settings.copy(width = w, height = h))
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                )
                            }
                            // 补齐不足3列的空位
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                if (showCustomDialog) {
                    CustomResolutionInputDialog(
                        onDismiss = { showCustomDialog = false },
                        onConfirm = { w, h ->
                            val prefs = context.getSharedPreferences(
                                CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE
                            )
                            val existing = prefs.getStringSet(
                                CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, mutableSetOf()
                            )?.toMutableSet() ?: mutableSetOf()
                            existing.add("${w}x${h}")
                            prefs.edit().putStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, existing).apply()
                            onSettingsChange(settings.copy(width = w, height = h))
                            showCustomDialog = false
                        },
                    )
                }
            }

            // 屏幕组合模式
            item {
                Divider()
                ScreenCombinationModeSelector(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
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

            // ── 开关组 ──
            item {
                Divider()
                SectionTitle("画面开关")
                SettingSwitchRow("HDR", settings.enableHdr) {
                    onSettingsChange(settings.copy(enableHdr = it))
                }
                AnimatedVisibility(visible = settings.enableHdr) {
                    Column(Modifier.padding(start = 16.dp)) {
                        SettingSwitchRow("HDR 高亮度", settings.enableHdrHighBrightness) {
                            onSettingsChange(settings.copy(enableHdrHighBrightness = it))
                        }
                        Text("HDR模式", style = MaterialTheme.typography.bodyMedium)
                        ChipSelector(
                            options = listOf("HDR10/PQ" to "1", "HLG" to "2"),
                            selectedValue = settings.hdrMode.toString(),
                            onSelect = { onSettingsChange(settings.copy(hdrMode = it.toIntOrNull() ?: 1)) },
                            columns = 2,
                        )
                    }
                }
                SettingSwitchRow("全屏拉伸画面", settings.stretchVideo) {
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

            // PiP
            item {
                Divider()
                SettingSwitchRow("画中画 (PiP)", settings.enablePip) {
                    onSettingsChange(settings.copy(enablePip = it))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
}

@Composable
fun HostCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
) {
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 实时生效
            item {
                SettingSwitchRow("断开串流时锁定屏幕", settings.lockScreenAfterDisconnect) {
                    onSettingsChange(settings.copy(lockScreenAfterDisconnect = it))
                }
                SettingSwitchRow("自动优化主机设置 (SOPS)", settings.enableSops) {
                    onSettingsChange(settings.copy(enableSops = it))
                }
                SettingSwitchRow("在电脑上播放声音", settings.playHostAudio) {
                    onSettingsChange(settings.copy(playHostAudio = it))
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

            item { Spacer(Modifier.height(24.dp)) }
        }
}

@Composable
fun AudioCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
) {
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
                SettingSwitchRow("播放串流音频", !settings.muteClientAudio) {
                    onSettingsChange(settings.copy(muteClientAudio = !it))
                }
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
                        ChipSelector(
                            options = listOf("自动" to "auto", "Opus" to "opus", "AC3" to "ac3", "E-AC3" to "eac3"),
                            selectedValue = settings.audioCodec,
                            onSelect = { onSettingsChange(settings.copy(audioCodec = it)) },
                            columns = 2,
                        )
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
                        ChipSelector(
                            options = listOf("通用" to "0", "游戏" to "1", "电影" to "2", "音乐" to "3"),
                            selectedValue = settings.audioVibrationScene.toString(),
                            onSelect = { onSettingsChange(settings.copy(audioVibrationScene = it.toIntOrNull() ?: 0)) },
                            columns = 2,
                        )
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

@Composable
fun GyroCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
) {
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

            item {
                Divider()
                SectionTitle("模式")
                val currentMode = if (settings.gyroToMouse) "mouse" else "right_stick"
                ChipSelector(
                    options = listOf("右摇杆" to "right_stick", "鼠标" to "mouse"),
                    selectedValue = currentMode,
                    onSelect = { value ->
                        onSettingsChange(settings.copy(
                            gyroToRightStick = value == "right_stick",
                            gyroToMouse = value == "mouse",
                        ))
                    },
                    columns = 2,
                )
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

            item { Spacer(Modifier.height(24.dp)) }
        }
}

@Composable
fun OtherCategory(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 操作面板自动隐藏
            item {
                SectionTitle("操作面板自动隐藏")
                ChipSelector(
                    options = listOf(
                        "按键映射时隐藏" to "0",
                        "2秒后自动隐藏" to "1",
                        "不自动隐藏" to "2",
                    ),
                    selectedValue = settings.toolPanelAutoHideMode.toString(),
                    onSelect = { value ->
                        onSettingsChange(settings.copy(toolPanelAutoHideMode = value.toIntOrNull() ?: 0))
                    },
                )
            }

            // 性能监控图层
            item {
                Divider()
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

            // 禁用警告
            item {
                Divider()
                SettingSwitchRow("禁用警告提示", settings.disableWarnings) {
                    onSettingsChange(settings.copy(disableWarnings = it))
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

            item { Spacer(Modifier.height(24.dp)) }
        }
}

// ═══════════════════════════════════════════
// 自定义分辨率输入对话框
// ═══════════════════════════════════════════

@Composable
private fun ScreenCombinationModeSelector(
    settings: HostSettings,
    onSettingsChange: (HostSettings) -> Unit,
) {
    val context = LocalContext.current
    val modeNames = remember {
        context.resources.getStringArray(com.alexclin.moonlink.android.R.array.screen_combination_mode_names)
    }
    val modeValues = remember {
        context.resources.getStringArray(com.alexclin.moonlink.android.R.array.screen_combination_mode_values)
    }

    // 找到当前值对应的名称
    val targetValue = settings.screenCombinationMode.toString()
    val currentName = remember(modeValues, modeNames, targetValue) {
        val idx = modeValues.indexOf(targetValue)
        if (idx >= 0) modeNames[idx] else modeNames.firstOrNull() ?: "使用主机端配置"
    }

    var showDialog by remember { mutableStateOf(false) }

    Column {
        SectionTitle("屏幕组合模式")
        Text(
            "控制主机在启动串流时如何管理屏幕组合，此处配置将覆盖主机端配置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // 当前选中模式的可点击行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showDialog = true
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "组合模式",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                currentName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("屏幕组合模式") },
            text = {
                Column {
                    modeNames.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val value = try {
                                        modeValues[index].toInt()
                                    } catch (_: Exception) { -1 }
                                    onSettingsChange(settings.copy(screenCombinationMode = value))
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = modeValues[index] == settings.screenCombinationMode.toString(),
                                onClick = {
                                    val value = try {
                                        modeValues[index].toInt()
                                    } catch (_: Exception) { -1 }
                                    onSettingsChange(settings.copy(screenCombinationMode = value))
                                    showDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun CustomResolutionInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义分辨率") },
        text = {
            Column {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = { Text("宽度 (320-7680)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("高度 (240-4320)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = widthText.toIntOrNull() ?: 0
                val h = heightText.toIntOrNull() ?: 0
                when {
                    w < 320 || w > 7680 -> error = "宽度超出范围 (320-7680)"
                    h < 240 || h > 4320 -> error = "高度超出范围 (240-4320)"
                    w % 2 != 0 -> error = "宽度必须为偶数"
                    h % 2 != 0 -> error = "高度必须为偶数"
                    else -> onConfirm(w, h)
                }
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
