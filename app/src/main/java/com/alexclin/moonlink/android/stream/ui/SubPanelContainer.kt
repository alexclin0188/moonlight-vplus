package com.alexclin.moonlink.android.stream.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.common.MoonLinkQuickActions
import com.alexclin.moonlink.android.stream.ui.common.RestartHintBanner
import com.alexclin.moonlink.android.stream.ui.common.getActionIcon
import com.alexclin.moonlink.android.stream.ui.common.getActionLabel
import com.alexclin.moonlink.android.stream.ui.panels.QuickActionRow
import com.limelight.QuickActionRegistry
import android.view.KeyEvent
import android.widget.Toast
import com.limelight.binding.input.ControllerGyroManager
import com.alexclin.moonlink.android.stream.ui.display.DisplaySettingsPanel
import com.alexclin.moonlink.android.stream.ui.panels.KeyMappingConfigPanel

enum class DetailPage {
    MAIN_LIST,
    DISPLAY,
    HOST_SETTINGS,
    PERIPHERALS,
    QUICK_ACTION_EDITOR,
    GYRO,
    MORE,
    KEY_MAPPING_CONFIG,
}

@Composable
fun SubPanelContainer(
    engine: StreamEngine,
    detailPage: DetailPage,
    onDetailPageChange: (DetailPage) -> Unit,
    onOpenKeyboardShortcuts: () -> Unit = {},
    onOpenFullScreenPage: (FullScreenPage) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var configIds: List<String> by remember { mutableStateOf(QuickActionRegistry.loadConfig(context)) }
    val panelWidth = 316.dp

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(panelWidth),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        AnimatedContent(
            targetState = detailPage,
            transitionSpec = {
                if (targetState == DetailPage.MAIN_LIST) {
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
        ) { page ->
            when (page) {
                DetailPage.MAIN_LIST -> {
                    MainListView(
                        engine = engine,
                        configIds = configIds,
                        onOpenKeyboardShortcuts = onOpenKeyboardShortcuts,
                        onEditActionClick = { onDetailPageChange(DetailPage.QUICK_ACTION_EDITOR) },
                        onNavigate = { onDetailPageChange(it) },
                        onOpenFullScreenPage = onOpenFullScreenPage,
                    )
                }
                DetailPage.DISPLAY -> {
                    DisplaySettingsPanel(
                        engine = engine,
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
                DetailPage.HOST_SETTINGS -> {
                    HostSettingsSection(
                        engine = engine,
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
                DetailPage.PERIPHERALS -> {
                    PeripheralsDetail(
                        engine = engine,
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
                DetailPage.GYRO -> {
                    GyroDetail(
                        engine = engine,
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
                DetailPage.MORE -> {
                    MoreDetail(
                        engine = engine,
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
                DetailPage.QUICK_ACTION_EDITOR -> {
                    QuickActionEditorPage(
                        configIds = configIds,
                        onSave = { newIds ->
                            QuickActionRegistry.saveConfig(context, newIds)
                            configIds = newIds
                            onDetailPageChange(DetailPage.MAIN_LIST)
                        },
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
                DetailPage.KEY_MAPPING_CONFIG -> {
                    KeyMappingConfigPanel(
                        engine = engine,
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
            }
        }
    }
}

// ── Main List ──

@Composable
private fun MainListView(
    engine: StreamEngine,
    configIds: List<String>,
    onOpenKeyboardShortcuts: () -> Unit = {},
    onEditActionClick: () -> Unit,
    onNavigate: (DetailPage) -> Unit,
    onOpenFullScreenPage: (FullScreenPage) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        item { QuickActionRow(engine = engine, configIds = configIds, onEditClick = onEditActionClick) }

        if (engine.prefConfig.enablePip) {
            item { PanZoomSection(engine = engine) }
        }

        // ── 分隔线：快捷操作区与按键映射的分隔 ──
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item { KeyMappingSection(
            engine = engine,
            onOpenFullScreenPage = onOpenFullScreenPage,
            onNavigateToConfig = onNavigate,
        ) }

        item { TouchModeSection(engine = engine) }

        // ── 分隔线：快捷操作区与设置区域的分隔 ──
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item {
            SectionEntryRow(
                icon = Icons.Default.Tv,
                label = "显示设置",
                onClick = { onNavigate(DetailPage.DISPLAY) },
            )
        }

        item {
            SectionEntryRow(
                icon = Icons.Default.Computer,
                label = "主机设置",
                onClick = { onNavigate(DetailPage.HOST_SETTINGS) },
            )
        }

        item {
            SectionEntryRow(
                icon = Icons.Default.Sensors,
                label = "体感助手",
                onClick = { onNavigate(DetailPage.GYRO) },
            )
        }

        item {
            PeripheralsSection(
                onNavigate = { onNavigate(DetailPage.PERIPHERALS) },
            )
        }

        // ── 分隔线：外设与其它设置的分隔 ──
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item {
            SectionEntryRow(
                icon = Icons.Default.Extension,
                label = "其它设置",
                onClick = { onNavigate(DetailPage.MORE) },
            )
        }

        item {
            SectionEntryRow(
                icon = Icons.Default.Bolt,
                label = "快捷键",
                onClick = onOpenKeyboardShortcuts,
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ── Components ──

@Composable
private fun SectionEntryRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Inline Section Composables ──

@Composable
private fun PanZoomSection(engine: StreamEngine) {
    // 仅画中画模式可见 — 外层已通过 engine.prefConfig.enablePip 控制可见性
    var enabled by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PanTool, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text("平移缩放", style = MaterialTheme.typography.bodyLarge,
             color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
}

@Composable
private fun KeyMappingSection(
    engine: StreamEngine,
    onOpenFullScreenPage: (FullScreenPage) -> Unit = {},
    onNavigateToConfig: (DetailPage) -> Unit = {},
) {
    val context = LocalContext.current
    val enabled by remember { derivedStateOf { engine.isKeyMappingFucEnabled } }
    val expanded = enabled

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.VideogameAsset, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("启用按键映射", style = MaterialTheme.typography.bodyLarge,
                 color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { newValue ->
                engine.setKeyMappingEnabled(newValue)
                if (newValue) {
                    // 自动切换触控板模式并立即生效
                    engine.applyTouchMode(2)  // 2 = 触控板模式
                    engine.prefConfig.touchscreenTrackpad = true
                    Toast.makeText(context, "已自动切换为触控板模式，可在触控模式中更改", Toast.LENGTH_SHORT).show()
                }
            })
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 15.dp, end = 12.dp)) {
                // 1. 切换按键映射方案 → 全屏方案选择页
                TextButton(
                    onClick = {
                        onOpenFullScreenPage(FullScreenPage.KEY_MAPPING_SCHEME_SELECTOR)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Column {
                            Text("切换按键映射方案 >", style = MaterialTheme.typography.bodyMedium)
                            Text("当前方案：${engine.currentSchemeName}",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 2. 编辑当前方案 → 全屏编辑器
                TextButton(
                    onClick = {
                        onOpenFullScreenPage(FullScreenPage.KEY_MAPPING_EDITOR)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("编辑当前方案 >")
                    }
                }

                // 3. 按键映射配置（所有方案统一使用此入口）
                TextButton(
                    onClick = { onNavigateToConfig(DetailPage.KEY_MAPPING_CONFIG) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("按键映射配置 >")
                    }
                }
            }
        }
    }
}

private enum class TouchMode(val label: String) {
    ENHANCED("增强式\n多点触控"),
    TRACKPAD("触控板\n模式"),
    MOUSE("鼠标模式"),
}

@Composable
private fun TouchModeSection(engine: StreamEngine) {
    val context = LocalContext.current

    // 从 prefConfig 读取当前模式
    val currentMode = remember(engine.prefConfig) {
        when {
            engine.prefConfig.touchscreenTrackpad -> TouchMode.TRACKPAD
            engine.prefConfig.enableEnhancedTouch -> TouchMode.ENHANCED
            else -> TouchMode.MOUSE  // CLASSIC 或 NATIVE_MOUSE 统一归入鼠标模式
        }
    }
    var selectedMode by remember { mutableStateOf(currentMode) }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TouchApp, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("触控模式", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))

        // 3 个 Chip 等宽等高三等分
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TouchMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = {
                        selectedMode = mode
                        applyTouchMode(engine, mode, context)
                    },
                    label = {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            AutoScaleLabel(text = mode.label)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 关联子项
        AnimatedVisibility(visible = selectedMode == TouchMode.TRACKPAD) {
            Column {
                var doubleClickDrag by remember { mutableStateOf(engine.prefConfig.enableDoubleClickDrag) }
                var localCursor by remember { mutableStateOf(engine.prefConfig.enableLocalCursorRendering) }
                // 触控灵敏度：单行展示 灵敏度 + Slider
                var sensitivity by remember { mutableIntStateOf(engine.prefConfig.touchpadSensitivity) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("灵敏度", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = sensitivity.toFloat(),
                        onValueChange = { sensitivity = it.toInt() },
                        onValueChangeFinished = {
                            engine.prefConfig.touchpadSensitivity = sensitivity
                            engine.configTouchSense = sensitivity
                            engine.prefConfig.writePreferences(context)
                        },
                        valueRange = 1f..200f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("$sensitivity", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.padding(start = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("双击按住", Modifier.weight(1f))
                    Switch(checked = doubleClickDrag, onCheckedChange = {
                        doubleClickDrag = it
                        engine.prefConfig.enableDoubleClickDrag = it
                        engine.prefConfig.writePreferences(context)
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本地光标渲染", Modifier.weight(1f))
                    Switch(checked = localCursor, onCheckedChange = {
                        localCursor = it
                        engine.prefConfig.enableLocalCursorRendering = it
                        engine.prefConfig.writePreferences(context)
                    })
                }
            }
        }
        AnimatedVisibility(visible = selectedMode == TouchMode.MOUSE) {
            Column {
                var nativeMouse by remember { mutableStateOf(engine.prefConfig.enableNativeMousePointer) }
                var remoteMouseVisible by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本地鼠标指针", Modifier.weight(1f))
                    Switch(checked = nativeMouse, onCheckedChange = {
                        nativeMouse = it
                        // 同步切换底层模式
                        engine.applyTouchMode(if (it) 3 else 1)
                        engine.prefConfig.writePreferences(context)
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("切换远程鼠标显示/隐藏", Modifier.weight(1f))
                    Switch(checked = remoteMouseVisible, onCheckedChange = {
                        remoteMouseVisible = it
                        engine.sendRemoteMouseToggle()
                    })
                }
            }
        }
    }
}

private fun applyTouchMode(engine: StreamEngine, mode: TouchMode, context: android.content.Context) {
    val modeInt = when (mode) {
        TouchMode.ENHANCED -> 0
        TouchMode.TRACKPAD -> 2
        TouchMode.MOUSE -> if (engine.prefConfig.enableNativeMousePointer) 3 else 1
    }
    engine.applyTouchMode(modeInt)
    engine.prefConfig.writePreferences(context)
    val msg = when (mode) {
        TouchMode.ENHANCED -> "已切换为增强式多点触控"
        TouchMode.TRACKPAD -> "已切换为触控板模式"
        TouchMode.MOUSE -> "已切换为鼠标模式"
    }
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

/** 自动换行居中标签 */
@Composable
private fun AutoScaleLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PeripheralsSection(
    onNavigate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigate)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Mouse,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "外设",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GyroDetail(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    DetailScaffold(title = "体感助手", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 体感总开关（当前激活的模式）
            item {
                val gyroActive = pref.gyroToRightStick || pref.gyroToMouse
                var gyroEnabled by remember { mutableStateOf(gyroActive) }
                SettingSwitch("体感开关", gyroEnabled) {
                    gyroEnabled = it
                    if (!it) {
                        engine.disableGyro()
                    } else {
                        // 启用时恢复上次模式（默认右摇杆）
                        engine.enableGyroRightStick()
                    }
                    pref.writePreferences(context)
                }
            }

            // 模式
            item {
                var gyroMode by remember { mutableIntStateOf(
                    if (pref.gyroToMouse) 1 else 0
                ) }
                Text("模式", style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.padding(bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = gyroMode == 0, onClick = {
                        gyroMode = 0
                        engine.enableGyroRightStick()
                        pref.writePreferences(context)
                    })
                    Text("右摇杆", Modifier.padding(end = 16.dp))
                    RadioButton(selected = gyroMode == 1, onClick = {
                        gyroMode = 1
                        engine.enableGyroMouse()
                        pref.writePreferences(context)
                    })
                    Text("鼠标")
                }
            }

            // 灵敏度 (0.5x~3.0x, 25 级)
            item {
                val initSens = if (pref.gyroSensitivityMultiplier > 0f)
                    pref.gyroSensitivityMultiplier else 1.0f
                var sensitivity by remember { mutableFloatStateOf(initSens) }
                Text("灵敏度: ${"%.1f".format(sensitivity)}x")
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    onValueChangeFinished = {
                        pref.gyroSensitivityMultiplier = sensitivity
                        pref.writePreferences(context)
                    },
                    valueRange = 0.5f..3.0f,
                )
            }

            // X/Y 轴反转
            item {
                var invertX by remember { mutableStateOf(pref.gyroInvertXAxis) }
                var invertY by remember { mutableStateOf(pref.gyroInvertYAxis) }
                SettingSwitch("X轴反转", invertX) {
                    invertX = it; pref.gyroInvertXAxis = it; pref.writePreferences(context)
                }
                SettingSwitch("Y轴反转", invertY) {
                    invertY = it; pref.gyroInvertYAxis = it; pref.writePreferences(context)
                }
            }

            // 激活按键
            item {
                val keyToIdx = mapOf(
                    ControllerGyroManager.GYRO_ACTIVATION_ALWAYS to 0,
                    KeyEvent.KEYCODE_BUTTON_L2 to 1,
                    KeyEvent.KEYCODE_BUTTON_R2 to 2,
                )
                val idxToKey = mapOf(
                    0 to ControllerGyroManager.GYRO_ACTIVATION_ALWAYS,
                    1 to KeyEvent.KEYCODE_BUTTON_L2,
                    2 to KeyEvent.KEYCODE_BUTTON_R2,
                )
                val initIdx = keyToIdx[pref.gyroActivationKeyCode] ?: 1
                var activateKey by remember { mutableIntStateOf(initIdx) }

                Text("激活按键:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = activateKey == 0,
                        onClick = {
                            activateKey = 0
                            pref.gyroActivationKeyCode = ControllerGyroManager.GYRO_ACTIVATION_ALWAYS
                            pref.writePreferences(context)
                            engine.recomputeGyroHold()
                        },
                        label = { Text("始终") },
                    )
                    FilterChip(
                        selected = activateKey == 1,
                        onClick = {
                            activateKey = 1
                            pref.gyroActivationKeyCode = KeyEvent.KEYCODE_BUTTON_L2
                            pref.writePreferences(context)
                            engine.recomputeGyroHold()
                        },
                        label = { Text("LT") },
                    )
                    FilterChip(
                        selected = activateKey == 2,
                        onClick = {
                            activateKey = 2
                            pref.gyroActivationKeyCode = KeyEvent.KEYCODE_BUTTON_R2
                            pref.writePreferences(context)
                            engine.recomputeGyroHold()
                        },
                        label = { Text("RT") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreDetail(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    DetailScaffold(title = "更多", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                // 性能监控图层
                var perfEnabled by remember { mutableStateOf(engine.prefConfig.enablePerfOverlay) }
                SettingSwitch("启用性能图层", perfEnabled) {
                    perfEnabled = it
                    engine.prefConfig.enablePerfOverlay = it
                    engine.perfOverlayEnabled = it  // 立即生效
                    engine.prefConfig.writePreferences(context)
                }
                AnimatedVisibility(visible = perfEnabled) {
                    Column {
                        var bgAlpha by remember {
                            mutableFloatStateOf(engine.prefConfig.perfOverlayBgOpacity / 100f)
                        }
                        Text("背景不透明度: ${(bgAlpha * 100).toInt()}%")
                        Slider(value = bgAlpha, onValueChange = {
                            bgAlpha = it
                            engine.prefConfig.perfOverlayBgOpacity = (it * 100).toInt()
                            engine.prefConfig.writePreferences(context)
                        }, valueRange = 0f..1f)
                        Text(
                            "性能图层详细设置请到主页-设置-性能和统计分析中设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            item {
                // 悬浮按钮不透明度
                val initOpacity = engine.prefConfig.fabOpacity
                var fabAlpha by remember { mutableFloatStateOf(initOpacity / 100f) }
                Text("悬浮按钮不透明度: ${(fabAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = fabAlpha,
                    onValueChange = { fabAlpha = it },
                    onValueChangeFinished = {
                        val intVal = (fabAlpha * 100).toInt().coerceIn(10, 100)
                        engine.prefConfig.fabOpacity = intVal
                        engine.fabOpacity = intVal  // 立即生效
                        engine.prefConfig.writePreferences(context)
                    },
                    valueRange = 0.1f..1.0f,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            item {
                // 操作面板自动隐藏
                var hideMode by remember { mutableIntStateOf(engine.prefConfig.toolPanelAutoHideMode) }
                Text("操作面板自动隐藏:", style = MaterialTheme.typography.bodyMedium)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        hideMode = 0
                        engine.prefConfig.toolPanelAutoHideMode = 0
                        engine.prefConfig.writePreferences(context)
                    }) {
                        RadioButton(selected = hideMode == 0, onClick = null)
                        Text("开启按键映射时隐藏", Modifier.padding(end = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        hideMode = 1
                        engine.prefConfig.toolPanelAutoHideMode = 1
                        engine.prefConfig.writePreferences(context)
                    }) {
                        RadioButton(selected = hideMode == 1, onClick = null)
                        Text("2秒后自动隐藏")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        hideMode = 2
                        engine.prefConfig.toolPanelAutoHideMode = 2
                        engine.prefConfig.writePreferences(context)
                    }) {
                        RadioButton(selected = hideMode == 2, onClick = null)
                        Text("不自动隐藏")
                    }
                }
            }
        }
    }
}

@Composable
private fun HostSettingsSection(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    val handleBack = {
        if (engine.displaySettingsRestartPending && !engine.activity.isFinishing) {
            engine.changeResolution()
        } else {
            onBack()
        }
    }

    DetailScaffold(title = "主机设置", onBack = handleBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── 需要重启提示横幅 ──
            if (engine.displaySettingsRestartPending) {
                item { RestartHintBanner(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
            }

            // 实时生效
            item {
                var v by remember { mutableStateOf(pref.lockScreenAfterDisconnect) }
                SettingSwitch("断开串流时锁定屏幕", v) { v = it; pref.lockScreenAfterDisconnect = it; pref.writePreferences(context) }
            }

            // ── 以下选项需重启串流才能生效 ──
            item {
                var v by remember { mutableStateOf(pref.enableSops) }
                SettingSwitch("自动优化主机设置", v) { v = it; pref.enableSops = it; pref.writePreferences(context); engine.displaySettingsRestartPending = true }
            }
            item {
                var v by remember { mutableStateOf(pref.playHostAudio) }
                SettingSwitch("在电脑上播放声音", v) { v = it; pref.playHostAudio = it; pref.writePreferences(context); engine.displaySettingsRestartPending = true }
            }
            item {
                var controlOnly by remember { mutableStateOf(pref.controlOnly) }
                SettingSwitch("仅控制模式", controlOnly) {
                    controlOnly = it; pref.controlOnly = it; pref.writePreferences(context); engine.displaySettingsRestartPending = true
                }
            }
            item {
                var v by remember { mutableStateOf(pref.enableClipboardSyncText) }
                SettingSwitch("同步剪贴板文本", v) { v = it; pref.enableClipboardSyncText = it; pref.writePreferences(context); engine.displaySettingsRestartPending = true }
            }
            item {
                var v by remember { mutableStateOf(pref.enableClipboardSyncImage) }
                SettingSwitch("同步剪贴板图片", v) { v = it; pref.enableClipboardSyncImage = it; pref.writePreferences(context); engine.displaySettingsRestartPending = true }
            }
        }
    }
}
// 注意：ShortcutActionsSection 已删除 — "快捷操作"入口已改向到键盘面板快捷键标签

@Composable
private fun PeripheralsDetail(
    engine: StreamEngine,
    onBack: () -> Unit,
) {
    var subPage by remember { mutableStateOf<String?>(null) }
    val devices = engine.peripheralDevices

    if (subPage == null) {
        DetailScaffold(title = "外设", onBack = onBack) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                val gamepadCount = devices.count { it.type == com.alexclin.moonlink.android.stream.engine.StreamEngine.PeripheralType.GAMEPAD }
                val keyboardCount = devices.count { it.type == com.alexclin.moonlink.android.stream.engine.StreamEngine.PeripheralType.KEYBOARD }
                val mouseCount = devices.count { it.type == com.alexclin.moonlink.android.stream.engine.StreamEngine.PeripheralType.MOUSE }
                fun countText(n: Int) = if (n == 0) "无" else n.toString()
                item { PeripheralEntry("手柄", Icons.Default.VideogameAsset, countText(gamepadCount)) { subPage = "gamepad" } }
                item { PeripheralEntry("键盘", Icons.Default.Keyboard, countText(keyboardCount)) { subPage = "keyboard" } }
                item { PeripheralEntry("鼠标", Icons.Default.Mouse, countText(mouseCount)) { subPage = "mouse" } }
                // 麦克风：直接开关，不进入子页面
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("麦克风", Modifier.weight(1f))
                        Switch(
                            checked = engine.prefConfig.enableMic,
                            onCheckedChange = { enabled ->
                                engine.prefConfig.enableMic = enabled
                                engine.prefConfig.writePreferences(engine.activity)
                                engine.toggleMicrophoneButton()
                            },
                        )
                    }
                }
            }
        }
    } else {
        DetailScaffold(title = when(subPage) {
            "gamepad" -> "手柄设置"
            "keyboard" -> "键盘设置"
            "mouse" -> "鼠标设置"
            else -> ""
        }, onBack = { subPage = null }) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                when (subPage) {
                    "keyboard" -> {
                        val keyboards = devices.filter { it.type == com.alexclin.moonlink.android.stream.engine.StreamEngine.PeripheralType.KEYBOARD }
                        val activeId = engine.activeKeyboardId
                        if (keyboards.isEmpty()) {
                            item { Text("未检测到键盘", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            keyboards.forEach { dev ->
                                item {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (keyboards.size > 1) {
                                            RadioButton(
                                                selected = dev.deviceId == activeId,
                                                onClick = { engine.activeKeyboardId = dev.deviceId },
                                            )
                                        }
                                        Text(if (keyboards.size > 1) "${dev.name}" else "已连接: ${dev.name}",
                                            Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    "mouse" -> {
                        val mice = devices.filter { it.type == com.alexclin.moonlink.android.stream.engine.StreamEngine.PeripheralType.MOUSE }
                        val activeId = engine.activeMouseId
                        if (mice.isEmpty()) {
                            item { Text("未检测到鼠标", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            mice.forEach { dev ->
                                item {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (mice.size > 1) {
                                            RadioButton(
                                                selected = dev.deviceId == activeId,
                                                onClick = { engine.activeMouseId = dev.deviceId },
                                            )
                                        }
                                        Text(if (mice.size > 1) "${dev.name}" else "已连接: ${dev.name}",
                                            Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    "gamepad" -> {
                        val gamepads = devices.filter { it.type == com.alexclin.moonlink.android.stream.engine.StreamEngine.PeripheralType.GAMEPAD }
                        if (gamepads.isEmpty()) {
                            item { Text("未检测到手柄", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            gamepads.forEach { dev ->
                                item {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(dev.name, Modifier.weight(1f))
                                        Switch(
                                            checked = dev.isEnabled,
                                            onCheckedChange = { enabled ->
                                                val idx = engine.peripheralDevices.indexOfFirst { it.deviceId == dev.deviceId }
                                                if (idx >= 0) {
                                                    engine.peripheralDevices[idx] = dev.copy(isEnabled = enabled)
                                                    engine.peripheralDevices = engine.peripheralDevices.toMutableList()
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
        }
    }
}

@Composable
private fun PeripheralEntry(
    label: String,
    icon: ImageVector,
    countText: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        Text(countText, style = MaterialTheme.typography.bodySmall,
            color = if (countText == "无") MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Quick Action Editor ──

private fun getAllEditorActionIds(): List<String> {
    val builtinIds = QuickActionRegistry.DEFAULT_IDS.toList()
    val moonlinkIds = listOf(
        MoonLinkQuickActions.TOGGLE_PIP,
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE,
        MoonLinkQuickActions.TOGGLE_CONTROL_ONLY,
        MoonLinkQuickActions.TOGGLE_GYRO,
    )
    return builtinIds + moonlinkIds
}

@Composable
private fun QuickActionEditorPage(
    configIds: List<String>,
    onSave: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val allAvailableIds = remember { getAllEditorActionIds() }
    val items = remember(configIds) {
        val result = configIds.toMutableList()
        for (id in allAvailableIds) {
            if (id !in result) result.add(id)
        }
        result.toList()
    }
    val reorderableItems = remember { mutableStateListOf<String>().also { it.addAll(items) } }
    val activeCount = 3.coerceAtMost(reorderableItems.size)
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 72.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "快捷操作调整",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSave(reorderableItems.toList().take(activeCount)) }) {
                Text("保存")
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(reorderableItems, key = { it }) { id ->
                val index = reorderableItems.indexOf(id)
                val isActive = index < activeCount
                val isDragging = draggedItemKey == id

                Surface(
                    modifier = Modifier
                        .animateItem()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            scaleX = if (isDragging) 1.03f else 1f
                            scaleY = if (isDragging) 1.03f else 1f
                            shadowElevation = if (isDragging) 8f else 0f
                        }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedItemKey = id
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIdx = reorderableItems.indexOf(id)
                                    val threshold = itemHeightPx * 0.5f
                                    if (dragOffset > threshold && currentIdx < reorderableItems.size - 1) {
                                        val tmp = reorderableItems[currentIdx]
                                        reorderableItems[currentIdx] = reorderableItems[currentIdx + 1]
                                        reorderableItems[currentIdx + 1] = tmp
                                        dragOffset -= itemHeightPx
                                    } else if (dragOffset < -threshold && currentIdx > 0) {
                                        val tmp = reorderableItems[currentIdx]
                                        reorderableItems[currentIdx] = reorderableItems[currentIdx - 1]
                                        reorderableItems[currentIdx - 1] = tmp
                                        dragOffset += itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggedItemKey = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedItemKey = null
                                    dragOffset = 0f
                                },
                            )
                        },
                    shape = RoundedCornerShape(0.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 18.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(12.dp))
                        val icon = getActionIcon(id)
                        if (icon != null) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            getActionLabel(id),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "拖动排序",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

// ── Helper Composables ──

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall,
         color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}



