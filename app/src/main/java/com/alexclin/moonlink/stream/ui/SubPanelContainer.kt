package com.alexclin.moonlink.stream.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.common.MoonLinkQuickActions
import com.alexclin.moonlink.stream.ui.common.getActionIcon
import com.alexclin.moonlink.stream.ui.common.getActionLabel
import com.alexclin.moonlink.stream.ui.panels.QuickActionRow
import com.limelight.QuickActionRegistry
import android.widget.Toast
import com.alexclin.moonlink.stream.ui.display.DisplaySettingsPanel

enum class DetailPage {
    MAIN_LIST,
    DISPLAY,
    HOST_SETTINGS,
    PERIPHERALS,
    KEY_MAPPING,
    QUICK_ACTION_EDITOR,
    GYRO,
    MORE,
}

@Composable
fun SubPanelContainer(
    engine: StreamEngine,
    detailPage: DetailPage,
    onDetailPageChange: (DetailPage) -> Unit,
    onOpenKeyboardShortcuts: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var configIds: List<String> by remember { mutableStateOf(QuickActionRegistry.loadConfig(context)) }
    val configuration = LocalConfiguration.current
    val panelWidth = (configuration.screenWidthDp.dp * 0.45f).coerceIn(280.dp, 400.dp)

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(panelWidth),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
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
                        onBack = { onDetailPageChange(DetailPage.MAIN_LIST) },
                    )
                }
                DetailPage.KEY_MAPPING -> {}
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

        item { KeyMappingSection(engine = engine) }

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
private fun KeyMappingSection(engine: StreamEngine) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.VideogameAsset, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("启用按键映射", style = MaterialTheme.typography.bodyLarge,
                 color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { newValue ->
                enabled = newValue
                if (newValue) {
                    engine.applyTouchMode(2) // 切换为触控板模式
                    engine.prefConfig.writePreferences(context)
                    Toast.makeText(context, "已自动切换为触控板模式，可在触控模式中更改", Toast.LENGTH_SHORT).show()
                }
                expanded = newValue
            })
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 44.dp, end = 12.dp, bottom = 8.dp)) {
                TextButton(onClick = {
                    Toast.makeText(context, "方案选择（待对接旧编辑器）", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("切换按键映射方案 >")
                }

                TextButton(onClick = {
                    Toast.makeText(context, "编辑方案（待对接旧编辑器）", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("编辑当前方案 >")
                }

                var showOther by remember { mutableStateOf(false) }
                TextButton(onClick = { showOther = !showOther }, modifier = Modifier.fillMaxWidth()) {
                    Text("其它设置 ${if (showOther) "▲" else "▼"}")
                }
                AnimatedVisibility(visible = showOther) {
                    Column {
                        Text("其它设置项（待完善）", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    DetailScaffold(title = "体感助手", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                // 总开关
                var gyroEnabled by remember { mutableStateOf(false) }
                SettingSwitch("体感开关", gyroEnabled) { gyroEnabled = it }
            }

            item {
                // 模式 (简化：两个 RadioButton)
                var gyroMode by remember { mutableIntStateOf(0) } // 0=右摇杆, 1=鼠标
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = gyroMode == 0, onClick = { gyroMode = 0 })
                    Text("右摇杆", Modifier.padding(end = 8.dp))
                    RadioButton(selected = gyroMode == 1, onClick = { gyroMode = 1 })
                    Text("鼠标")
                }
            }

            item {
                // 灵敏度 (0.5x~3.0x, 25 级)
                var sensitivity by remember { mutableFloatStateOf(1.0f) }
                Text("灵敏度: ${"%.1f".format(sensitivity)}x")
                Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0.5f..3.0f)
            }

            item {
                // X/Y 轴反转
                var invertX by remember { mutableStateOf(false) }
                var invertY by remember { mutableStateOf(false) }
                SettingSwitch("X轴反转", invertX) { invertX = it }
                SettingSwitch("Y轴反转", invertY) { invertY = it }
            }

            item {
                // 激活按键
                var activateKey by remember { mutableIntStateOf(0) } // 0=始终, 1=LT, 2=RT
                Text("激活按键:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = activateKey == 0, onClick = { activateKey = 0 }, label = { Text("始终") })
                    FilterChip(selected = activateKey == 1, onClick = { activateKey = 1 }, label = { Text("LT") })
                    FilterChip(selected = activateKey == 2, onClick = { activateKey = 2 }, label = { Text("RT") })
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

// ── Detail Pages ──

@Composable
private fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider()
        content()
    }
}

@Composable
private fun HostSettingsSection(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    DetailScaffold(title = "主机设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 7个Switch配置项
            item {
                var v by remember { mutableStateOf(pref.lockScreenAfterDisconnect) }
                SettingSwitch("断开串流时锁定屏幕", v) { v = it; pref.lockScreenAfterDisconnect = it; pref.writePreferences(context) }
            }
            item {
                var v by remember { mutableStateOf(pref.enableSops) }
                SettingSwitch("自动优化主机设置", v) { v = it; pref.enableSops = it; pref.writePreferences(context) }
            }
            item {
                var v by remember { mutableStateOf(pref.playHostAudio) }
                SettingSwitch("在电脑上播放声音", v) { v = it; pref.playHostAudio = it; pref.writePreferences(context) }
            }
            item {
                var v by remember { mutableStateOf(pref.swapQuitAndDisconnect) }
                SettingSwitch("交换退出/断开按钮功能", v) { v = it; pref.swapQuitAndDisconnect = it; pref.writePreferences(context) }
            }
            item {
                var controlOnly by remember { mutableStateOf(pref.controlOnly) }
                SettingSwitch("仅控制模式", controlOnly) {
                    controlOnly = it; pref.controlOnly = it; pref.writePreferences(context)
                }
            }
            item {
                var v by remember { mutableStateOf(pref.enableClipboardSyncText) }
                SettingSwitch("同步剪贴板文本", v) { v = it; pref.enableClipboardSyncText = it; pref.writePreferences(context) }
            }
            item {
                var v by remember { mutableStateOf(pref.enableClipboardSyncImage) }
                SettingSwitch("同步剪贴板图片", v) { v = it; pref.enableClipboardSyncImage = it; pref.writePreferences(context) }
            }
        }
    }
}
// 注意：ShortcutActionsSection 已删除 — "快捷操作"入口已改向到键盘面板快捷键标签

@Composable
private fun PeripheralsDetail(onBack: () -> Unit) {
    var subPage by remember { mutableStateOf<String?>(null) }

    if (subPage == null) {
        DetailScaffold(title = "外设", onBack = onBack) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                item { PeripheralEntry("手柄", Icons.Default.VideogameAsset) { subPage = "gamepad" } }
                item { PeripheralEntry("键盘", Icons.Default.Keyboard) { subPage = "keyboard" } }
                item { PeripheralEntry("鼠标", Icons.Default.Mouse) { subPage = "mouse" } }
                item { PeripheralEntry("麦克风", Icons.Default.Mic) { subPage = "mic" } }
            }
        }
    } else {
        DetailScaffold(title = when(subPage) {
            "gamepad" -> "手柄设置"
            "keyboard" -> "键盘设置"
            "mouse" -> "鼠标设置"
            else -> "麦克风设置"
        }, onBack = { subPage = null }) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                item { Text("${subPage}设置项（待完善）", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun PeripheralEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
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


