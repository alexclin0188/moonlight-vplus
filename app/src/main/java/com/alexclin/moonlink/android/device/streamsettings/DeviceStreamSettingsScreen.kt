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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.stream.ui.common.ChipSelector
import com.alexclin.moonlink.android.stream.ui.common.CompactChip
import com.alexclin.moonlink.android.stream.ui.panels.SchemeInfo
import com.alexclin.moonlink.android.stream.ui.panels.loadUserSchemes
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import androidx.preference.PreferenceManager
import com.limelight.preferences.CustomResolutionsConsts

private data class CategoryEntry(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun rememberCategories(context: Context) = remember {
    listOf(
        CategoryEntry("touch", context.getString(R.string.category_touch_mode), Icons.Default.TouchApp),
        CategoryEntry("display", context.getString(R.string.category_display_settings), Icons.Default.Tv),
        CategoryEntry("switches", context.getString(R.string.category_display_switches), Icons.Default.Tune),
        CategoryEntry("host", context.getString(R.string.category_host_settings), Icons.Default.Computer),
        CategoryEntry("audio", context.getString(R.string.title_sound_settings), Icons.Default.VolumeUp),
        CategoryEntry("gyro", context.getString(R.string.category_gyro), Icons.Default.Sensors),
        CategoryEntry("other", context.getString(R.string.category_other_settings), Icons.Default.Extension),
    )
}

@Composable
fun DeviceStreamSettingsScreen(
    hostname: String,
    onNavigateToCategory: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    MainCategoryList(
        hostname = hostname,
        categories = rememberCategories(context),
        onSelectCategory = onNavigateToCategory,
    )
}

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
            CategoryEntryRow(icon = category.icon, label = category.label, onClick = { onSelectCategory(category.key) })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun CategoryEntryRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, modifier = Modifier.scale(0.8f), onCheckedChange = onToggle)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun ExpandableSelectorRow(label: String, summary: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSlider(value: Float, valueRange: ClosedFloatingPointRange<Float>, valueLabel: String, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit) {
    Column {
        Text("$valueLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished, valueRange = valueRange)
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun TouchModeCategory(settings: HostSettings, onSettingsChange: (HostSettings) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            SectionTitle(stringResource(R.string.section_touch_mode_select))
            var selectedMode by remember(settings) { mutableStateOf(when {
                settings.touchscreenTrackpad -> "trackpad"
                settings.enableEnhancedTouch -> "enhanced"
                else -> "mouse"
            }) }
            ChipSelector(
                options = listOf(stringResource(R.string.option_enhanced_multi_touch) to "enhanced", stringResource(R.string.option_trackpad_mode) to "trackpad", stringResource(R.string.option_mouse_mode) to "mouse"),
                selectedValue = selectedMode,
                onSelect = { value ->
                    selectedMode = value
                    onSettingsChange(settings.copy(enableEnhancedTouch = value == "enhanced", touchscreenTrackpad = value == "trackpad", enableNativeMousePointer = value == "mouse" && settings.enableNativeMousePointer))
                },
            )
        }
        item {
            AnimatedVisibility(visible = settings.touchscreenTrackpad) {
                Column {
                    SectionTitle(stringResource(R.string.section_trackpad_settings))
                    var sensitivity by remember { mutableFloatStateOf(settings.touchpadSensitivity.toFloat()) }
                    SettingSlider(
                        value = sensitivity, valueRange = 1f..200f,
                        valueLabel = stringResource(R.string.label_sensitivity) + ": ${sensitivity.toInt()}",
                        onValueChange = { sensitivity = it },
                        onValueChangeFinished = { onSettingsChange(settings.copy(touchpadSensitivity = sensitivity.toInt())) },
                    )
                    SettingSwitchRow(stringResource(R.string.title_enable_double_click_drag), settings.enableDoubleClickDrag) { onSettingsChange(settings.copy(enableDoubleClickDrag = it)) }
                    SettingSwitchRow(stringResource(R.string.title_local_cursor_rendering), settings.enableLocalCursorRendering) { onSettingsChange(settings.copy(enableLocalCursorRendering = it)) }
                }
            }
        }
        item {
            AnimatedVisibility(visible = !settings.touchscreenTrackpad && !settings.enableEnhancedTouch) {
                Column {
                    SectionTitle(stringResource(R.string.section_mouse_settings))
                    SettingSwitchRow(stringResource(R.string.label_local_mouse_pointer), settings.enableNativeMousePointer) { onSettingsChange(settings.copy(enableNativeMousePointer = it)) }
                }
            }
        }
        item {
            AnimatedVisibility(visible = settings.enableEnhancedTouch) {
                Column {
                    SectionTitle(stringResource(R.string.section_enhanced_touch_settings))
                    SettingSwitchRow(stringResource(R.string.label_enhanced_touch_on_right), settings.enhancedTouchOnWhichSide) { onSettingsChange(settings.copy(enhancedTouchOnWhichSide = it)) }
                    var zoneDivider by remember { mutableFloatStateOf(settings.enhanceTouchZoneDivider.toFloat()) }
                    SettingSlider(value = zoneDivider, valueRange = 10f..90f, valueLabel = stringResource(R.string.label_zone_divider) + ": ${zoneDivider.toInt()}%", onValueChange = { zoneDivider = it }, onValueChangeFinished = { onSettingsChange(settings.copy(enhanceTouchZoneDivider = zoneDivider.toInt())) })
                    var velocity by remember { mutableFloatStateOf(settings.pointerVelocityFactor.toFloat()) }
                    SettingSlider(value = velocity, valueRange = 10f..500f, valueLabel = stringResource(R.string.label_pointer_speed) + ": ${velocity.toInt()}%", onValueChange = { velocity = it }, onValueChangeFinished = { onSettingsChange(settings.copy(pointerVelocityFactor = velocity.toInt())) })
                    var flatRegion by remember { mutableFloatStateOf(settings.longPressFlatRegionPixels.toFloat()) }
                    SettingSlider(value = flatRegion, valueRange = 0f..250f, valueLabel = stringResource(R.string.label_long_press_jitter) + ": ${flatRegion.toInt()}px", onValueChange = { flatRegion = it }, onValueChangeFinished = { onSettingsChange(settings.copy(longPressFlatRegionPixels = flatRegion.toInt())) })
                }
            }
        }
        item {
            Divider()
            SectionTitle(stringResource(R.string.title_key_mapping_features))
            SettingSwitchRow(stringResource(R.string.label_key_mapping_enabled), settings.keyMappingEnabled) { onSettingsChange(settings.copy(keyMappingEnabled = it)) }
            AnimatedVisibility(visible = settings.keyMappingEnabled) {
                Column(Modifier.padding(start = 16.dp)) {
                    val context = LocalContext.current
                    var schemes by remember { mutableStateOf<List<SchemeInfo>>(emptyList()) }
                    var showSchemeDialog by remember { mutableStateOf(false) }
                    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                    var currentConfigId by remember { mutableLongStateOf(prefs.getLong(StreamEngine.PREF_CURRENT_CONFIG_ID, 0L)) }
                    val loadingText = stringResource(R.string.label_loading)
                    val builtinGamepadText = stringResource(R.string.label_builtin_virtual_gamepad)
                    val unknownText = stringResource(R.string.label_unknown)
                    var currentSchemeName by remember { mutableStateOf(loadingText) }

                    LaunchedEffect(Unit) {
                        val loaded = loadUserSchemes(context)
                        schemes = loaded
                        currentSchemeName = if (currentConfigId == 0L) builtinGamepadText else loaded.find { it.configId == currentConfigId }?.name ?: unknownText
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable { showSchemeDialog = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.label_current_scheme), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(currentSchemeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { showSchemeDialog = true }) { Text(stringResource(R.string.label_switch_scheme)) }
                    if (showSchemeDialog) {
                        val allSchemes = listOf(SchemeInfo(configId = 0L, name = stringResource(R.string.label_builtin_virtual_gamepad))) + schemes
                        AlertDialog(
                            onDismissRequest = { showSchemeDialog = false },
                            title = { Text(stringResource(R.string.title_select_key_mapping_scheme)) },
                            text = {
                                Column {
                                    allSchemes.forEach { scheme ->
                                        Row(modifier = Modifier.fillMaxWidth().clickable { currentConfigId = scheme.configId; currentSchemeName = scheme.name; prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, scheme.configId).apply(); showSchemeDialog = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = scheme.configId == currentConfigId, onClick = { currentConfigId = scheme.configId; currentSchemeName = scheme.name; prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, scheme.configId).apply(); showSchemeDialog = false })
                                            Spacer(Modifier.width(8.dp))
                                            Text(scheme.name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showSchemeDialog = false }) { Text(stringResource(R.string.btn_close)) } },
                        )
                    }
                }
            }
        }
        item {
            Divider()
            SectionTitle(stringResource(R.string.section_general_touch_settings))
            SettingSwitchRow(stringResource(R.string.title_checkbox_sync_touch_event_with_display), settings.syncTouchEventWithDisplay) { onSettingsChange(settings.copy(syncTouchEventWithDisplay = it)) }
            var toggleFingers by remember { mutableFloatStateOf(settings.nativeTouchFingersToToggleKeyboard.toFloat()) }
            SettingSlider(value = toggleFingers, valueRange = 0f..10f, valueLabel = stringResource(R.string.label_keyboard_toggle_fingers) + ": ${toggleFingers.toInt()} " + stringResource(R.string.label_fingers_count, toggleFingers.toInt()), onValueChange = { toggleFingers = it }, onValueChangeFinished = { onSettingsChange(settings.copy(nativeTouchFingersToToggleKeyboard = toggleFingers.toInt())) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun DisplayCategory(settings: HostSettings, onSettingsChange: (HostSettings) -> Unit) {
    val presets = listOf(stringResource(R.string.option_auto) to "auto", "2M" to "2M", "8M" to "8M", "20M" to "20M", stringResource(R.string.option_custom) to "custom")
    var selectedPreset by remember(settings) { mutableStateOf(when {
        settings.enableAdaptiveBitrate -> "auto"; settings.bitrate <= 0 -> "auto"; settings.bitrate <= 2000 -> "2M"; settings.bitrate <= 8000 -> "8M"; settings.bitrate <= 20000 -> "20M"; else -> "custom"
    }) }

    val context = LocalContext.current
    var showCustomDialog by remember { mutableStateOf(false) }
    var customResSet by remember {
        mutableStateOf(context.getSharedPreferences(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE).getStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, emptySet())?.sortedBy { it } ?: emptyList())
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item { SectionTitle(stringResource(R.string.section_video)) }
        item {
            Text(stringResource(R.string.label_bitrate_settings), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
            ChipSelector(options = presets, selectedValue = selectedPreset, onSelect = { value ->
                selectedPreset = value
                val newBitrate = when (value) { "auto" -> { onSettingsChange(settings.copy(enableAdaptiveBitrate = true, bitrate = 0)); return@ChipSelector }; "2M" -> 2000; "8M" -> 8000; "20M" -> 20000; else -> settings.bitrate }
                onSettingsChange(settings.copy(enableAdaptiveBitrate = false, bitrate = newBitrate))
            })
        }
        item {
            AnimatedVisibility(visible = selectedPreset == "custom" || settings.bitrate > 20000) {
                var customKbps by remember { mutableFloatStateOf(if (settings.bitrate > 20000) settings.bitrate.toFloat() else 50000f) }
                SettingSlider(value = customKbps, valueRange = 1000f..800000f, valueLabel = stringResource(R.string.label_bitrate) + ": ${(customKbps / 1000).toInt()} Mbps (${customKbps.toInt()} kbps)", onValueChange = { customKbps = it }, onValueChangeFinished = { onSettingsChange(settings.copy(enableAdaptiveBitrate = false, bitrate = customKbps.toInt())) })
            }
        }
        item {
            AnimatedVisibility(visible = settings.enableAdaptiveBitrate) {
                Column {
                    Text(stringResource(R.string.label_abr_mode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
                    ChipSelector(options = listOf(stringResource(R.string.abr_mode_quality) to "quality", stringResource(R.string.abr_mode_balanced) to "balanced", stringResource(R.string.abr_mode_low_latency) to "lowLatency"), selectedValue = settings.abrMode, onSelect = { onSettingsChange(settings.copy(abrMode = it)) })
                }
            }
        }
        item {
            Divider()
            Text(stringResource(R.string.label_video_codec), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
            ChipSelector(options = listOf(stringResource(R.string.option_auto) to "auto", "AV1" to "forceav1", "HEVC" to "forceh265", "H264" to "neverh265"), selectedValue = settings.videoFormat, onSelect = { onSettingsChange(settings.copy(videoFormat = it)) })
        }
        item {
            Divider()
            val unlocked = settings.unlockFps
            Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_video_framerate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.label_unlock_framerate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Switch(checked = unlocked, onCheckedChange = { onSettingsChange(settings.copy(unlockFps = it)) }, modifier = Modifier.height(20.dp).scale(0.8f))
            }
            val baseFps = listOf(30, 60, 90, 120)
            val extraFps = listOf(144, 165)
            val allFps = baseFps + if (unlocked) extraFps else emptyList()
            ChipSelector(options = allFps.map { "${it}FPS" to it.toString() }, selectedValue = settings.fps.toString(), onSelect = { value -> val newFps = value.toIntOrNull() ?: 60; onSettingsChange(settings.copy(fps = newFps)) }, columns = 5)
        }
        item {
            Divider()
            Text(stringResource(R.string.label_frame_pacing), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
            ChipSelector(options = listOf(stringResource(R.string.pacing_latency) to "0", stringResource(R.string.pacing_balanced) to "1", stringResource(R.string.pacing_balanced_alt) to "2", stringResource(R.string.pacing_smoothness) to "3", stringResource(R.string.pacing_experimental_low_latency) to "4", stringResource(R.string.pacing_precise_sync) to "5"), selectedValue = settings.framePacing.toString(), onSelect = { value -> onSettingsChange(settings.copy(framePacing = value.toIntOrNull() ?: 0)) }, columns = 3, spacingDp = 6)
        }
        item {
            Divider()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_resolution), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).weight(1f))
                TextButton(onClick = { showCustomDialog = true }) { Text(stringResource(R.string.btn_add_custom_resolution), style = MaterialTheme.typography.labelSmall) }
            }
            val standardResolutions = listOf("640x360", "854x480", "1280x720", "1920x1080", "2560x1440", "3840x2160")
            val nativeRes = remember {
                val size = Point()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager; val bounds = wm.currentWindowMetrics.bounds; size.set(bounds.width(), bounds.height()) } else { @Suppress("DEPRECATION") val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) display.getRealSize(size) else display.getSize(size) }
                val w = maxOf(size.x, size.y); val h = minOf(size.x, size.y); "${w}x${h}"
            }
            val nativePrefix = stringResource(R.string.resolution_prefix_native)
            val customLabel = stringResource(R.string.option_custom)
            val customSuffix = " ($customLabel)"
            val nativePrefixParen = "$nativePrefix ("
            val allResolutions = remember(customResSet, nativePrefix, customLabel) {
                val nativeLabel = if (nativeRes !in standardResolutions) listOf("$nativePrefix ($nativeRes)") else emptyList()
                standardResolutions + nativeLabel + customResSet.map { "$it ($customLabel)" }
            }
            val currentRes = if (settings.width > 0 && settings.height > 0) "${settings.width}x${settings.height}" else "1920x1080"
            var selectedRes by remember(settings) { mutableStateOf(currentRes) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                allResolutions.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { res ->
                            val cleanRes = res.replace(customSuffix, "").replace(nativePrefixParen, "").replace(")", "")
                            val isSelected = selectedRes == cleanRes
                            CompactChip(label = res, selected = isSelected, onClick = {
                                selectedRes = cleanRes
                                val parts = cleanRes.split("x")
                                if (parts.size == 2) { val w = parts[0].toIntOrNull() ?: settings.width; val h = parts[1].toIntOrNull() ?: settings.height; onSettingsChange(settings.copy(width = w, height = h)) }
                            }, modifier = Modifier.weight(1f).heightIn(min = 40.dp))
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Divider()
            var buf by remember { mutableFloatStateOf(settings.outputBufferQueueLimit.toFloat()) }
            SettingSlider(value = buf, valueRange = 1f..5f, valueLabel = stringResource(R.string.label_output_buffer) + ": " + stringResource(R.string.label_output_buffer_frames, buf.toInt()), onValueChange = { buf = it }, onValueChangeFinished = { onSettingsChange(settings.copy(outputBufferQueueLimit = buf.toInt())) })
        }
        item {
            Divider()
            SectionTitle(stringResource(R.string.section_virtual_display))
            Text(stringResource(R.string.desc_virtual_display), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            val nativeRes2 = remember {
                val size = Point()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager; val bounds = wm.currentWindowMetrics.bounds; size.set(bounds.width(), bounds.height()) } else { @Suppress("DEPRECATION") val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) display.getRealSize(size) else display.getSize(size) }
                maxOf(size.x, size.y) to minOf(size.x, size.y)
            }
            val nativeResStr = "${nativeRes2.first}x${nativeRes2.second}"
            Text(stringResource(R.string.label_virtual_display_resolution), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            val vddStandardRes = listOf("640x360", "854x480", "1280x720", "1920x1080", "2560x1440", "3840x2160")
            val deviceNativeLabel = stringResource(R.string.label_device_native)
            val customLabel2 = stringResource(R.string.option_custom)
            val customSuffix2 = " ($customLabel2)"
            val nativeLabelParen = "$deviceNativeLabel ("
            val allVddResolutions = remember(customResSet, deviceNativeLabel, customLabel2) { listOf("$deviceNativeLabel ($nativeResStr)") + vddStandardRes + customResSet.map { "$it ($customLabel2)" } }
            val currentVddRes = if (settings.vddWidth > 0 && settings.vddHeight > 0) "${settings.vddWidth}x${settings.vddHeight}" else "$deviceNativeLabel ($nativeResStr)"
            var selectedVddRes by remember(settings) { mutableStateOf(currentVddRes) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                allVddResolutions.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { res ->
                            val cleanRes = res.replace(customSuffix2, "").replace(nativeLabelParen, "").replace(")", "")
                            val isSelected = selectedVddRes == res
                            CompactChip(label = res, selected = isSelected, onClick = {
                                selectedVddRes = res
                                if (res.startsWith(deviceNativeLabel)) { onSettingsChange(settings.copy(vddWidth = 0, vddHeight = 0)) }
                                else { val parts = cleanRes.split("x"); if (parts.size == 2) { val w = parts[0].toIntOrNull() ?: 0; val h = parts[1].toIntOrNull() ?: 0; onSettingsChange(settings.copy(vddWidth = w, vddHeight = h)) } }
                            }, modifier = Modifier.weight(1f).heightIn(min = 40.dp))
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(stringResource(R.string.label_virtual_display_framerate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            ChipSelector(options = listOf(30, 60, 90, 120, 144, 165).map { "${it}FPS" to it.toString() }, selectedValue = settings.vddFps.toString(), onSelect = { value -> val newFps = value.toIntOrNull() ?: 90; onSettingsChange(settings.copy(vddFps = newFps)) }, columns = 6)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCustomDialog) {
        CustomResolutionInputDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { w, h ->
                val resStr = "${w}x${h}"
                val prefs = context.getSharedPreferences(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE)
                val existing = prefs.getStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                existing.add(resStr)
                prefs.edit().putStringSet(CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, existing).apply()
                customResSet = existing.sorted()
                onSettingsChange(settings.copy(width = w, height = h))
                showCustomDialog = false
            },
        )
    }
}

@Composable
fun DisplaySwitchesCategory(settings: HostSettings, onSettingsChange: (HostSettings) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            SettingSwitchRow("HDR", settings.enableHdr) { onSettingsChange(settings.copy(enableHdr = it)) }
            AnimatedVisibility(visible = settings.enableHdr) {
                Column(Modifier.padding(start = 16.dp)) {
                    SettingSwitchRow(stringResource(R.string.label_hdr_high_brightness), settings.enableHdrHighBrightness) { onSettingsChange(settings.copy(enableHdrHighBrightness = it)) }
                    Text(stringResource(R.string.label_hdr_mode), style = MaterialTheme.typography.bodyMedium)
                    ChipSelector(options = listOf("HDR10/PQ" to "1", "HLG" to "2"), selectedValue = settings.hdrMode.toString(), onSelect = { onSettingsChange(settings.copy(hdrMode = it.toIntOrNull() ?: 1)) }, columns = 2)
                }
            }
            SettingSwitchRow(stringResource(R.string.label_stretch_video), settings.stretchVideo) { onSettingsChange(settings.copy(stretchVideo = it)) }
            SettingSwitchRow(stringResource(R.string.label_reverse_resolution), settings.reverseResolution) { onSettingsChange(settings.copy(reverseResolution = it)) }
            SettingSwitchRow(stringResource(R.string.label_rotable_screen), settings.rotableScreen) { onSettingsChange(settings.copy(rotableScreen = it)) }
            SettingSwitchRow(stringResource(R.string.label_reduce_refresh_rate), settings.reduceRefreshRate) { onSettingsChange(settings.copy(reduceRefreshRate = it)) }
            SettingSwitchRow(stringResource(R.string.label_full_range), settings.fullRange) { onSettingsChange(settings.copy(fullRange = it)) }
            SettingSwitchRow(stringResource(R.string.label_mtk_option), settings.forceMtkMaxOperatingRate) { onSettingsChange(settings.copy(forceMtkMaxOperatingRate = it)) }
            SettingSwitchRow(stringResource(R.string.label_use_external_display), settings.useExternalDisplay) { onSettingsChange(settings.copy(useExternalDisplay = it)) }
            SettingSwitchRow(stringResource(R.string.label_control_only), settings.controlOnly) { onSettingsChange(settings.copy(controlOnly = it)) }
        }
        item { Divider(); SettingSwitchRow(stringResource(R.string.label_pip), settings.enablePip) { onSettingsChange(settings.copy(enablePip = it)) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun HostCategory(settings: HostSettings, onSettingsChange: (HostSettings) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            SettingSwitchRow(stringResource(R.string.label_lock_screen_after_disconnect), settings.lockScreenAfterDisconnect) { onSettingsChange(settings.copy(lockScreenAfterDisconnect = it)) }
            SettingSwitchRow(stringResource(R.string.label_optimize_host_settings), settings.enableSops) { onSettingsChange(settings.copy(enableSops = it)) }
            SettingSwitchRow(stringResource(R.string.label_play_audio_on_pc), settings.playHostAudio) { onSettingsChange(settings.copy(playHostAudio = it)) }
            SettingSwitchRow(stringResource(R.string.label_sync_clipboard_text), settings.enableClipboardSyncText) { onSettingsChange(settings.copy(enableClipboardSyncText = it)) }
            SettingSwitchRow(stringResource(R.string.label_sync_clipboard_image), settings.enableClipboardSyncImage) { onSettingsChange(settings.copy(enableClipboardSyncImage = it)) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun AudioCategory(settings: HostSettings, onSettingsChange: (HostSettings) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            SectionTitle(stringResource(R.string.section_output_settings))
            Text(stringResource(R.string.label_surround_sound), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            ChipSelector(options = listOf(stringResource(R.string.audioconf_stereo) to "0", "5.1 " + stringResource(R.string.label_surround_sound) to "2", "7.1 " + stringResource(R.string.label_surround_sound) to "4"), selectedValue = settings.audioConfiguration, onSelect = { onSettingsChange(settings.copy(audioConfiguration = it)) })
        }
        item {
            Divider()
            SettingSwitchRow(stringResource(R.string.label_play_stream_audio), !settings.muteClientAudio) { onSettingsChange(settings.copy(muteClientAudio = !it)) }
            SettingSwitchRow(stringResource(R.string.label_equalizer), settings.enableAudioFx) { onSettingsChange(settings.copy(enableAudioFx = it)) }
            SettingSwitchRow(stringResource(R.string.label_spatial_audio), settings.enableSpatializer) { onSettingsChange(settings.copy(enableSpatializer = it)) }
        }
        item {
            Divider()
            SectionTitle(stringResource(R.string.section_audio_passthrough))
            SettingSwitchRow(stringResource(R.string.label_enable_audio_passthrough), settings.enableAudioPassthrough) { onSettingsChange(settings.copy(enableAudioPassthrough = it)) }
            AnimatedVisibility(visible = settings.enableAudioPassthrough) {
                Column(Modifier.padding(start = 16.dp)) {
                    Text(stringResource(R.string.label_passthrough_codec), style = MaterialTheme.typography.bodyMedium)
                    ChipSelector(options = listOf(stringResource(R.string.option_auto) to "auto", "Opus" to "opus", "AC3" to "ac3", "E-AC3" to "eac3"), selectedValue = settings.audioCodec, onSelect = { onSettingsChange(settings.copy(audioCodec = it)) }, columns = 2)
                    Text(stringResource(R.string.label_passthrough_buffer), style = MaterialTheme.typography.bodyMedium)
                    ChipSelector(options = listOf(stringResource(R.string.audio_passthrough_buffer_low) to "low", stringResource(R.string.audio_passthrough_buffer_normal) to "normal", stringResource(R.string.audio_passthrough_buffer_high) to "high"), selectedValue = settings.audioPassthroughBuffer, onSelect = { onSettingsChange(settings.copy(audioPassthroughBuffer = it)) })
                }
            }
        }
        item {
            Divider()
            SectionTitle(stringResource(R.string.section_audio_vibration))
            SettingSwitchRow(stringResource(R.string.label_enable_audio_vibration), settings.enableAudioVibration) { onSettingsChange(settings.copy(enableAudioVibration = it)) }
            AnimatedVisibility(visible = settings.enableAudioVibration) {
                Column(Modifier.padding(start = 16.dp)) {
                    var strength by remember { mutableFloatStateOf(settings.audioVibrationStrength.toFloat()) }
                    SettingSlider(value = strength, valueRange = 0f..200f, valueLabel = stringResource(R.string.label_vibration_strength) + ": ${strength.toInt()}", onValueChange = { strength = it }, onValueChangeFinished = { onSettingsChange(settings.copy(audioVibrationStrength = strength.toInt())) })
                    Text(stringResource(R.string.label_vibration_routing), style = MaterialTheme.typography.bodyMedium)
                    ChipSelector(options = listOf(stringResource(R.string.option_auto) to "auto", stringResource(R.string.option_speaker_only) to "speaker", stringResource(R.string.option_headset_only) to "headset"), selectedValue = settings.audioVibrationMode, onSelect = { onSettingsChange(settings.copy(audioVibrationMode = it)) })
                    Text(stringResource(R.string.label_scene_mode), style = MaterialTheme.typography.bodyMedium)
                    ChipSelector(options = listOf(stringResource(R.string.option_general) to "0", stringResource(R.string.option_game) to "1", stringResource(R.string.option_movie) to "2", stringResource(R.string.option_music) to "3"), selectedValue = settings.audioVibrationScene.toString(), onSelect = { onSettingsChange(settings.copy(audioVibrationScene = it.toIntOrNull() ?: 0)) }, columns = 2)
                }
            }
        }
        item {
            Divider()
            SectionTitle(stringResource(R.string.section_microphone))
            SettingSwitchRow(stringResource(R.string.label_mic_redirect), settings.enableMic) { onSettingsChange(settings.copy(enableMic = it)) }
            AnimatedVisibility(visible = settings.enableMic) {
                Column(Modifier.padding(start = 16.dp)) {
                    var micRate by remember { mutableFloatStateOf(settings.micBitrate.toFloat()) }
                    SettingSlider(value = micRate, valueRange = 32f..256f, valueLabel = stringResource(R.string.label_transmission_quality) + ": " + stringResource(R.string.label_transmission_quality_kbps, micRate.toInt()), onValueChange = { micRate = it }, onValueChangeFinished = { onSettingsChange(settings.copy(micBitrate = micRate.toInt())) })
                    Text(stringResource(R.string.label_icon_color), style = MaterialTheme.typography.bodyMedium)
                    ChipSelector(options = listOf(stringResource(R.string.option_solid_white) to "solid_white", stringResource(R.string.option_accent) to "accent"), selectedValue = settings.micIconColor, onSelect = { onSettingsChange(settings.copy(micIconColor = it)) })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun GyroCategory(settings: HostSettings, onSettingsChange: (HostSettings) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            val gyroActive = settings.gyroToRightStick || settings.gyroToMouse
            SettingSwitchRow(stringResource(R.string.label_gyro_switch), gyroActive) { enabled ->
                if (!enabled) onSettingsChange(settings.copy(gyroToRightStick = false, gyroToMouse = false))
                else onSettingsChange(settings.copy(gyroToRightStick = true, gyroToMouse = false))
            }
        }
        item {
            Divider(); SectionTitle(stringResource(R.string.label_activation_key))
            ChipSelector(options = listOf(stringResource(R.string.option_always) to 0, "LT" to KeyEvent.KEYCODE_BUTTON_L2, "RT" to KeyEvent.KEYCODE_BUTTON_R2).map { (label, value) -> label to value.toString() }, selectedValue = settings.gyroActivationKeyCode.toString(), onSelect = { value -> onSettingsChange(settings.copy(gyroActivationKeyCode = value.toIntOrNull() ?: KeyEvent.KEYCODE_BUTTON_L2)) })
        }
        item {
            Divider(); SectionTitle(stringResource(R.string.label_mode))
            val currentMode = if (settings.gyroToMouse) "mouse" else "right_stick"
            ChipSelector(options = listOf(stringResource(R.string.option_right_stick) to "right_stick", stringResource(R.string.option_mouse) to "mouse"), selectedValue = currentMode, onSelect = { value -> onSettingsChange(settings.copy(gyroToRightStick = value == "right_stick", gyroToMouse = value == "mouse")) }, columns = 2)
        }
        item {
            Divider()
            var sensitivity by remember { mutableFloatStateOf(settings.gyroSensitivityMultiplier) }
            SettingSlider(value = sensitivity, valueRange = 0.5f..3.0f, valueLabel = stringResource(R.string.label_sensitivity_format, sensitivity), onValueChange = { sensitivity = it }, onValueChangeFinished = { onSettingsChange(settings.copy(gyroSensitivityMultiplier = sensitivity)) })
        }
        item {
            Divider(); SectionTitle(stringResource(R.string.section_axis_invert))
            SettingSwitchRow(stringResource(R.string.label_x_axis_invert), settings.gyroInvertXAxis) { onSettingsChange(settings.copy(gyroInvertXAxis = it)) }
            SettingSwitchRow(stringResource(R.string.label_y_axis_invert), settings.gyroInvertYAxis) { onSettingsChange(settings.copy(gyroInvertYAxis = it)) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun OtherCategory(settings: HostSettings, onSettingsChange: (HostSettings) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            SectionTitle(stringResource(R.string.section_panel_auto_hide))
            ChipSelector(options = listOf(stringResource(R.string.option_hide_on_key_mapping) to "0", stringResource(R.string.option_auto_hide_2s) to "1", stringResource(R.string.option_never_hide) to "2"), selectedValue = settings.toolPanelAutoHideMode.toString(), onSelect = { value -> onSettingsChange(settings.copy(toolPanelAutoHideMode = value.toIntOrNull() ?: 0)) })
        }
        item {
            Divider(); SectionTitle(stringResource(R.string.section_performance_display))
            var perfEnabled by remember { mutableStateOf(settings.enablePerfOverlay) }
            SettingSwitchRow(stringResource(R.string.label_enable_perf_overlay), perfEnabled) { perfEnabled = it; onSettingsChange(settings.copy(enablePerfOverlay = it)) }
            AnimatedVisibility(visible = perfEnabled) {
                Column(Modifier.padding(start = 16.dp)) {
                    var bgAlpha by remember { mutableFloatStateOf(settings.perfOverlayBgOpacity / 100f) }
                    SettingSlider(value = bgAlpha, valueRange = 0f..1f, valueLabel = stringResource(R.string.label_bg_opacity) + ": " + stringResource(R.string.label_bg_opacity_percent, (bgAlpha * 100).toInt()), onValueChange = { bgAlpha = it }, onValueChangeFinished = { onSettingsChange(settings.copy(perfOverlayBgOpacity = (bgAlpha * 100).toInt())) })
                    SettingSwitchRow(stringResource(R.string.label_lock_perf_overlay), settings.perfOverlayLocked) { onSettingsChange(settings.copy(perfOverlayLocked = it)) }
                    Text(stringResource(R.string.desc_perf_overlay_settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { Divider(); SettingSwitchRow(stringResource(R.string.label_show_latency_after_stream), settings.enableLatencyToast) { onSettingsChange(settings.copy(enableLatencyToast = it)) } }
        item { Divider(); SettingSwitchRow(stringResource(R.string.label_disable_warnings), settings.disableWarnings) { onSettingsChange(settings.copy(disableWarnings = it)) } }
        item { Divider(); SettingSwitchRow(stringResource(R.string.label_pause_stream_support), settings.showPauseStream) { onSettingsChange(settings.copy(showPauseStream = it)) } }
        item {
            Divider(); SectionTitle(stringResource(R.string.section_floating_elements))
            var fabAlpha by remember { mutableFloatStateOf(settings.fabOpacity / 100f) }
            SettingSlider(value = fabAlpha, valueRange = 0.1f..1.0f, valueLabel = stringResource(R.string.label_fab_opacity) + ": " + stringResource(R.string.label_bg_opacity_percent, (fabAlpha * 100).toInt()), onValueChange = { fabAlpha = it }, onValueChangeFinished = { onSettingsChange(settings.copy(fabOpacity = (fabAlpha * 100).toInt().coerceIn(10, 100))) })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CustomResolutionInputDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val context = LocalContext.current
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val titleText = stringResource(R.string.title_custom_resolution)
    val widthHint = stringResource(R.string.hint_width_range)
    val heightHint = stringResource(R.string.hint_height_range)
    val errorWidthRange = stringResource(R.string.error_width_out_of_range)
    val errorHeightRange = stringResource(R.string.error_height_out_of_range)
    val errorWidthEven = stringResource(R.string.error_width_must_be_even)
    val errorHeightEven = stringResource(R.string.error_height_must_be_even)
    val btnAdd = stringResource(R.string.btn_add)
    val btnCancel = stringResource(R.string.btn_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                OutlinedTextField(value = widthText, onValueChange = { widthText = it }, label = { Text(widthHint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = heightText, onValueChange = { heightText = it }, label = { Text(heightHint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) { Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = widthText.toIntOrNull() ?: 0; val h = heightText.toIntOrNull() ?: 0
                when {
                    w < 320 || w > 7680 -> error = errorWidthRange
                    h < 240 || h > 4320 -> error = errorHeightRange
                    w % 2 != 0 -> error = errorWidthEven
                    h % 2 != 0 -> error = errorHeightEven
                    else -> onConfirm(w, h)
                }
            }) { Text(btnAdd) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(btnCancel) } },
    )
}
