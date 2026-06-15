# Handoff: stream_ui_completion（可独立执行版）

> 本文件包含另一个编程 agent 执行此计划所需的所有信息。
> **不依赖当前会话上下文** — 所有文件路径、行号、API、代码模式均已包含。

---

## 项目环境

- **项目**: Android Kotlin + Jetpack Compose
- **新代码包**: `com.alexclin.moonlink.stream.*`
- **旧代码包**: `com.limelight.*`（只读引用，不做修改）
- **工作目录**: `app/src/main/java/com/alexclin/moonlink/stream/`

## 关键约束

1. **不创建新文件** — 所有改动在现有文件中
2. **不修改 `com.limelight.*` 包** — 只引用其公开 API
3. **`SubPanelContainer.kt` 会显著膨胀**（当前712行→预计1500+行），后续可按需拆分

---

## Batch A：快捷操作入口改向（3 files，~30行改动）

### 目标
点击子面板"快捷操作 >"条目 → 关闭子面板 → 弹出键盘面板 → 自动切到"快捷键"标签

### TA-1：`KeyboardSubPanel.kt` 加 `initialTab` 参数

**文件**: `app/src/main/java/com/alexclin/moonlink/stream/ui/keyboard/KeyboardSubPanel.kt`

**现有签名** (L77-83):
```kotlin
@Composable
fun KeyboardSubPanel(
    engine: StreamEngine,
    onClose: () -> Unit = {},
    onCloseToHidden: () -> Unit = onClose,
    onShowFloatingKeyboard: () -> Unit = {},
) {
```

**改为**:
```kotlin
@Composable
fun KeyboardSubPanel(
    engine: StreamEngine,
    initialTab: Int = 0,  // 0=输入法 1=快捷键 2=虚拟键盘 3=主机键盘
    onClose: () -> Unit = {},
    onCloseToHidden: () -> Unit = onClose,
    onShowFloatingKeyboard: () -> Unit = {},
) {
```

**L89 改动** — `selectedTab` 初始化:
```kotlin
// 改前: var selectedTab by remember { mutableIntStateOf(0) }
// 改后:
var selectedTab by remember { mutableIntStateOf(initialTab) }
```

### TA-2：`SubPanelContainer.kt` 添加回调

**文件**: `app/src/main/java/com/alexclin/moonlink/stream/ui/SubPanelContainer.kt`

**现有签名** (L87-88):
```kotlin
fun SubPanelContainer(engine: StreamEngine, modifier: Modifier = Modifier) {
```

**改为**:
```kotlin
fun SubPanelContainer(
    engine: StreamEngine,
    onOpenKeyboardShortcuts: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
```

**`MainListView` 签名** (L180-186) — 也需要加参数:
```kotlin
// 改前:
private fun MainListView(
    engine: StreamEngine,
    configIds: List<String>,
    onEditActionClick: () -> Unit,
    onNavigate: (DetailPage) -> Unit,
) {
// 改后:
private fun MainListView(
    engine: StreamEngine,
    configIds: List<String>,
    onEditActionClick: () -> Unit,
    onNavigate: (DetailPage) -> Unit,
    onOpenKeyboardShortcuts: () -> Unit = {},
) {
```

**"快捷操作 >" 条目** (L221-226):
```kotlin
// 改前:
        item {
            SectionEntryRow(
                icon = Icons.Default.Bolt,
                label = "快捷操作",
                onClick = { onNavigate(DetailPage.SHORTCUT_ACTIONS) },
            )
        }
// 改后:
        item {
            SectionEntryRow(
                icon = Icons.Default.Bolt,
                label = "快捷操作",
                onClick = onOpenKeyboardShortcuts,
            )
        }
```

**`MainListView` 调用处** (L134-139) — 传入新参数:
```kotlin
// 改前:
                    MainListView(
                        engine = engine,
                        configIds = configIds,
                        onEditActionClick = { detailPage = DetailPage.QUICK_ACTION_EDITOR },
                        onNavigate = { detailPage = it },
                    )
// 改后:
                    MainListView(
                        engine = engine,
                        configIds = configIds,
                        onEditActionClick = { detailPage = DetailPage.QUICK_ACTION_EDITOR },
                        onNavigate = { detailPage = it },
                        onOpenKeyboardShortcuts = onOpenKeyboardShortcuts,
                    )
```

### TA-3：`StreamOverlay.kt` 连接回调

**文件**: `app/src/main/java/com/alexclin/moonlink/stream/ui/StreamOverlay.kt`

**新增状态变量** (在 L80-83 附近 `var panelState` 等后面):
```kotlin
var keyboardInitialTab by remember { mutableIntStateOf(0) }
```

**`SubPanelContainer` 调用处** (L264):
```kotlin
// 改前:
            SubPanelContainer(engine = engine)
// 改后:
            SubPanelContainer(
                engine = engine,
                onOpenKeyboardShortcuts = {
                    panelState = PanelState.KEYBOARD_PANEL
                    activeEntry = "keyboard"
                    keyboardInitialTab = 1  // 跳转到"快捷键"标签
                },
            )
```

**`KeyboardSubPanel` 调用处** (L292-305):
```kotlin
// 改前:
                KeyboardSubPanel(
                    engine = engine,
                    onClose = { ... },
                    ...
                )
// 改后:
                KeyboardSubPanel(
                    engine = engine,
                    initialTab = keyboardInitialTab,
                    onClose = { ... },
                    ...
                )
```

### Batch A 验证
1. 打开 app → 点击悬浮按钮 → 窄条出现
2. 点击窄条"操作" → 子面板出现
3. 点击子面板中"快捷操作 >" 条目
4. 确认：子面板消失 → 键盘面板从底部弹出 → **默认选中"快捷键"标签**

---

## Batch B：触控模式 + 平移缩放（1 file，SubPanelContainer.kt）

### TB-1：`TouchModeSection` 完整实现

**位置**: 当前占位符 L335-357。替换整个函数。

```kotlin
private enum class TouchMode(val label: String) {
    ENHANCED("增强式多点触控"),
    CLASSIC("经典鼠标模式"),
    TRACKPAD("触控板模式"),
    NATIVE_MOUSE("本地鼠标指针"),
}

@Composable
private fun TouchModeSection(engine: StreamEngine) {
    val context = LocalContext.current

    // 从 prefConfig 读取当前模式
    val currentMode = remember(engine.prefConfig) {
        when {
            engine.prefConfig.enableNativeMousePointer -> TouchMode.NATIVE_MOUSE
            engine.prefConfig.touchscreenTrackpad -> TouchMode.TRACKPAD
            engine.prefConfig.enableEnhancedTouch -> TouchMode.ENHANCED
            else -> TouchMode.CLASSIC
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

        // 4 个 Chip 横向排列
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TouchMode.entries.toList()) { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = {
                        selectedMode = mode
                        applyTouchMode(engine, mode, context)
                    },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        // 关联子项
        AnimatedVisibility(visible = selectedMode == TouchMode.CLASSIC) {
            TextButton(onClick = { engine.sendRemoteMouseToggle() }) {
                Text("显示/隐藏远程鼠标")
            }
        }
        AnimatedVisibility(visible = selectedMode == TouchMode.TRACKPAD) {
            Column {
                var doubleClickDrag by remember { mutableStateOf(engine.prefConfig.enableDoubleClickDrag) }
                var localCursor by remember { mutableStateOf(engine.prefConfig.enableLocalCursorRendering) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("双击按住", Modifier.weight(1f))
                    Switch(checked = doubleClickDrag, onCheckedChange = {
                        doubleClickDrag = it
                        engine.prefConfig.enableDoubleClickDrag = it
                        engine.prefConfig.writePreferences(engine.activity)
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本地光标渲染", Modifier.weight(1f))
                    Switch(checked = localCursor, onCheckedChange = {
                        localCursor = it
                        engine.prefConfig.enableLocalCursorRendering = it
                        engine.prefConfig.writePreferences(engine.activity)
                    })
                }
            }
        }
    }
}

private fun applyTouchMode(engine: StreamEngine, mode: TouchMode, context: android.content.Context) {
    val pref = engine.prefConfig
    when (mode) {
        TouchMode.ENHANCED -> {
            pref.enableEnhancedTouch = true; pref.enableNativeMousePointer = false; pref.touchscreenTrackpad = false
            android.widget.Toast.makeText(context, "已切换为增强式多点触控", android.widget.Toast.LENGTH_SHORT).show()
        }
        TouchMode.CLASSIC -> {
            pref.enableEnhancedTouch = false; pref.enableNativeMousePointer = false; pref.touchscreenTrackpad = false
            android.widget.Toast.makeText(context, "已切换为经典鼠标模式", android.widget.Toast.LENGTH_SHORT).show()
        }
        TouchMode.TRACKPAD -> {
            pref.enableEnhancedTouch = false; pref.enableNativeMousePointer = false; pref.touchscreenTrackpad = true
            android.widget.Toast.makeText(context, "已切换为触控板模式", android.widget.Toast.LENGTH_SHORT).show()
        }
        TouchMode.NATIVE_MOUSE -> {
            pref.enableEnhancedTouch = false; pref.enableNativeMousePointer = true; pref.touchscreenTrackpad = false
            android.widget.Toast.makeText(context, "已切换为本地鼠标指针", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    pref.writePreferences(context)
}
```

**需要的 imports**（添加到 SubPanelContainer.kt 顶部）:
```kotlin
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.animation.AnimatedVisibility
```

**注意**: `TouchModeSection` 当前在 `MainListView` 中以 `TouchModeSection()` 无参调用（L202），需要改为 `TouchModeSection(engine = engine)`。

### TB-3：`PanZoomSection` 实现

**位置**: 当前占位符 L286-307。替换整个函数。

```kotlin
@Composable
private fun PanZoomSection(engine: StreamEngine) {
    // 仅画中画模式可见
    if (!engine.prefConfig.enablePip) return

    var enabled by remember { mutableStateOf(false) }  // 默认关闭

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
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            // 注意：StreamEngine 没有 setTouchOverrideEnabled，直接操作 prefConfig
            // 具体实现取决于 Game.kt 中 isTouchOverrideEnabled 的逻辑
        })
    }
}
```

> **注意**: 原设计中 `engine.setTouchOverrideEnabled()` 在 StreamEngine 中不存在。如果旧 Game.kt 中有对应逻辑，需从那里移植。否则此功能可先保留 Switch 切换但标注为 TODO。

**调用处更新** (L194附近): `PanZoomSection()` → `PanZoomSection(engine = engine)`

### Batch B 验证
1. 切换4种触控模式 Chip → 选中态高亮，Toast 提示
2. 经典模式 → 显示"显示/隐藏远程鼠标"按钮
3. 触控板模式 → 显示"双击按住"和"本地光标渲染"开关
4. 平移缩放仅在画中画模式可见

---

## Batch C：显示详情（1 file，SubPanelContainer.kt，复杂度最高）

### TC-1：`DisplaySection` 完整实现

**位置**: 当前占位符 L480-497。

**6个子区域**，全部在一个 `LazyColumn` 中：

```kotlin
@Composable
private fun DisplaySection(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    // 显示器列表状态
    var displays by remember { mutableStateOf<List<com.limelight.nvstream.http.NvHTTP.DisplayInfo>>(emptyList()) }
    var selectedDisplayIndex by remember { mutableIntStateOf(-1) }
    var useVdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = engine.conn ?: return@withContext
                // 需要通过 NvHTTP 获取显示器列表
                // conn 是 NvConnection 类型，需要通过其内部引用访问 NvHTTP
                // 如果无法直接访问，暂用空列表
            } catch (_: Exception) {}
        }
    }

    DetailScaffold(title = "显示", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── 1. 帧率切换 ──
            item { SectionTitle("帧率") }
            item {
                val fpsOptions = listOf(30, 60, 90, 120)
                var selectedFps by remember { mutableIntStateOf(pref.fps) }
                LazyRow {
                    items(fpsOptions) { fps ->
                        FilterChip(
                            selected = selectedFps == fps,
                            onClick = {
                                selectedFps = fps
                                pref.fps = fps
                                pref.writePreferences(context)
                                Toast.makeText(context, "帧率已设为${fps}fps，重启串流后生效", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("${fps}fps") },
                        )
                    }
                }
            }

            // ── 2. 码率分段滑块 ──
            item { SectionTitle("码率 (kbps)") }
            item {
                val currentKbps = pref.bitrate
                val initialProgress = bitrateToProgress(currentKbps)
                var sliderProgress by remember { mutableFloatStateOf(initialProgress.toFloat()) }
                val currentKbpsDisplay = progressToBitrateKbps(sliderProgress.toInt())

                Column {
                    Text("${currentKbpsDisplay} kbps", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = sliderProgress,
                        onValueChange = { sliderProgress = it },
                        onValueChangeFinished = {
                            val kbps = progressToBitrateKbps(sliderProgress.toInt())
                            pref.bitrate = kbps
                            pref.writePreferences(context)
                            engine.conn?.setBitrate(kbps) { _ -> }  // 简化回调
                        },
                        valueRange = 0f..59f,
                        steps = 58,
                    )
                }
            }

            // ── 3. 多显示器管理 ──
            item { SectionTitle("显示器") }
            if (displays.isNotEmpty()) {
                items(displays.size) { i ->
                    val d = displays[i]
                    Row(Modifier.fillMaxWidth().clickable { selectedDisplayIndex = i }.padding(vertical = 4.dp)) {
                        RadioButton(selected = selectedDisplayIndex == i, onClick = { selectedDisplayIndex = i })
                        Text(d.name, Modifier.padding(start = 8.dp))
                    }
                }
            } else {
                item { Text("无法获取显示器列表", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("VDD 虚拟显示器", Modifier.weight(1f))
                    Switch(checked = useVdd, onCheckedChange = { useVdd = it })
                }
            }
            item {
                var external by remember { mutableStateOf(pref.useExternalDisplay) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("使用外接显示器", Modifier.weight(1f))
                    Switch(checked = external, onCheckedChange = {
                        external = it
                        pref.useExternalDisplay = it
                        pref.writePreferences(context)
                    })
                }
            }
            item {
                Text("⚠ 显示器选择需重启串流生效", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── 4. 自适应流畅度 ──
            item { SectionTitle("自适应流畅度") }
            item {
                var adaptive by remember { mutableStateOf(false) }  // 从 pref 读取 checkbox_adaptive_bitrate
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自适应码率", Modifier.weight(1f))
                    Switch(checked = adaptive, onCheckedChange = { adaptive = it })
                }
            }

            // ── 5. HDR ──
            item { SectionTitle("HDR") }
            item {
                var hdr by remember { mutableStateOf(pref.enableHdr) }
                var highBrightness by remember { mutableStateOf(pref.enableHdrHighBrightness) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用 HDR", Modifier.weight(1f))
                    Switch(checked = hdr, onCheckedChange = {
                        hdr = it
                        pref.enableHdr = it
                        pref.writePreferences(context)
                    })
                }
                AnimatedVisibility(visible = hdr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("  HDR 高亮度", Modifier.weight(1f))
                        Switch(checked = highBrightness, onCheckedChange = {
                            highBrightness = it
                            pref.enableHdrHighBrightness = it
                            pref.writePreferences(context)
                        })
                    }
                }
            }

            // ── 6. 画面设置 ──
            item { SectionTitle("画面设置") }
            item {
                var stretch by remember { mutableStateOf(pref.stretchVideo) }
                var reverse by remember { mutableStateOf(pref.reverseResolution) }
                var rotable by remember { mutableStateOf(pref.rotableScreen) }
                SettingSwitch("拉伸视频", stretch) { stretch = it; pref.stretchVideo = it; pref.writePreferences(context) }
                SettingSwitch("反转分辨率", reverse) { reverse = it; pref.reverseResolution = it; pref.writePreferences(context) }
                SettingSwitch("可旋转画面", rotable) { rotable = it; pref.rotableScreen = it; pref.writePreferences(context) }
            }
        }
    }
}

// 辅助组件（添加到文件底部）
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
```

**码率映射函数**（添加到 SubPanelContainer.kt 文件底部， private function）:
```kotlin
private fun progressToBitrateKbps(progress: Int): Int = when {
    progress <= 9  -> 500 + progress * 500
    progress <= 24 -> 5000 + (progress - 9) * 1000
    progress <= 39 -> 20000 + (progress - 24) * 2000
    progress <= 49 -> 50000 + (progress - 39) * 5000
    else           -> 100000 + (progress - 49) * 10000
}

private fun bitrateToProgress(kbps: Int): Int = when {
    kbps <= 5000   -> ((kbps - 500) / 500).coerceIn(0, 9)
    kbps <= 20000  -> (9 + (kbps - 5000 + 500) / 1000).coerceIn(10, 24)
    kbps <= 50000  -> (24 + (kbps - 20000 + 1000) / 2000).coerceIn(25, 39)
    kbps <= 100000 -> (39 + (kbps - 50000 + 2500) / 5000).coerceIn(40, 49)
    else           -> (49 + (kbps - 100000 + 5000) / 10000).coerceIn(50, 59)
}
```

**调用处更新** (L141-144):
```kotlin
// 改前:
                DetailPage.DISPLAY -> {
                    DisplaySection(onBack = { detailPage = DetailPage.MAIN_LIST })
                }
// 改后:
                DetailPage.DISPLAY -> {
                    DisplaySection(engine = engine, onBack = { detailPage = DetailPage.MAIN_LIST })
                }
```

### Batch C 验证
1. 点击子面板"显示 >" → 进入显示详情页
2. 帧率 Chip 可切换，Toast 提示"重启串流后生效"
3. 码率 Slider 拖动 → 实时更新显示值，松开后调用 setBitrate
4. 显示器列表加载（如果主机在线）
5. HDR 开关展开/收起子项正常
6. 画面设置开关写入 prefConfig

---

## Batch D：主机设置 + 外设详情（1 file，SubPanelContainer.kt）

### TD-1：`HostSettingsSection` 实现

**位置**: 当前占位符 L499-516。

```kotlin
@Composable
private fun HostSettingsSection(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    DetailScaffold(title = "主机设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 7个Switch配置项，对应 PreferenceConfiguration 的 SP keys
            // 注意：部分字段需要在 PreferenceConfiguration 中确认是否存在
            item { SettingSwitch("断开串流时锁定屏幕", false) { /* checkbox_lock_screen_after_disconnect */ } }
            item { SettingSwitch("自动优化主机设置", false) { /* checkbox_enable_sops */ } }
            item { SettingSwitch("在电脑上播放声音", false) { /* checkbox_host_audio */ } }
            item { SettingSwitch("交换退出/断开按钮功能", false) { /* checkbox_swap_quit_and_disconnect */ } }
            item {
                var controlOnly by remember { mutableStateOf(pref.controlOnly) }
                SettingSwitch("仅控制模式", controlOnly) {
                    controlOnly = it; pref.controlOnly = it; pref.writePreferences(context)
                }
            }
            item { SettingSwitch("同步剪贴板文本", false) { /* checkbox_clipboard_sync_text */ } }
            item { SettingSwitch("同步剪贴板图片", false) { /* checkbox_clipboard_sync_image */ } }
        }
    }
}
```

> **注意**: 部分字段（锁定屏幕、自动优化、播放声音、交换退出、剪贴板同步）在 `PreferenceConfiguration` 中可能没有直接的 Kotlin 属性，它们是通过 SharedPreferences key 直接读写的。如果找不到对应属性，使用 `PreferenceManager.getDefaultSharedPreferences(context)` 直接读写。

**调用处更新** (L146-149):
```kotlin
// 改前:
                DetailPage.HOST_SETTINGS -> {
                    HostSettingsSection(onBack = { detailPage = DetailPage.MAIN_LIST })
                }
// 改后:
                DetailPage.HOST_SETTINGS -> {
                    HostSettingsSection(engine = engine, onBack = { detailPage = DetailPage.MAIN_LIST })
                }
```

### TD-2：`PeripheralsDetail` 实现

**位置**: 当前占位符 L537-554。

```kotlin
@Composable
private fun PeripheralsDetail(onBack: () -> Unit) {
    var subPage by remember { mutableStateOf<String?>(null) }  // null=主列表, "gamepad"/"keyboard"/"mouse"/"mic"

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
        // 子详情页
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
```

**需要的 imports**:
```kotlin
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Keyboard
```

### Batch D 验证
1. 主机设置7个开关可切换并持久化
2. 外设详情显示4个子入口，点击进入子详情 → 返回回到外设主列表

---

## Batch E：体感助手 + 更多（1 file，SubPanelContainer.kt）

### TE-1：`GyroSection` 实现

**位置**: 当前占位符 L392-414。

```kotlin
@Composable
private fun GyroSection(engine: StreamEngine) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Sensors, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("体感助手", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 44.dp, end = 12.dp, bottom = 8.dp)) {
                // 总开关
                var gyroEnabled by remember { mutableStateOf(false) }
                SettingSwitch("体感开关", gyroEnabled) { gyroEnabled = it }

                // 模式 (简化：两个 RadioButton)
                var gyroMode by remember { mutableIntStateOf(0) } // 0=右摇杆, 1=鼠标
                Row { RadioButton(gyroMode == 0, { gyroMode = 0 }); Text("右摇杆"); RadioButton(gyroMode == 1, { gyroMode = 1 }); Text("鼠标") }

                // 灵敏度 (0.5x~3.0x, 25 级)
                var sensitivity by remember { mutableFloatStateOf(1.0f) }
                Text("灵敏度: ${"%.1f".format(sensitivity)}x")
                Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0.5f..3.0f)

                // X/Y 轴反转
                var invertX by remember { mutableStateOf(false) }
                var invertY by remember { mutableStateOf(false) }
                SettingSwitch("X轴反转", invertX) { invertX = it }
                SettingSwitch("Y轴反转", invertY) { invertY = it }

                // 激活按键
                var activateKey by remember { mutableIntStateOf(0) } // 0=始终, 1=LT, 2=RT
                Text("激活按键:")
                Row {
                    FilterChip(selected = activateKey == 0, onClick = { activateKey = 0 }, label = { Text("始终") })
                    FilterChip(selected = activateKey == 1, onClick = { activateKey = 1 }, label = { Text("LT") })
                    FilterChip(selected = activateKey == 2, onClick = { activateKey = 2 }, label = { Text("RT") })
                }
            }
        }
    }
}
```

**需要的 imports**:
```kotlin
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
```

**调用处更新** (L237附近): `GyroSection()` → `GyroSection(engine = engine)`

### TE-2：`MoreSection` 实现

**位置**: 当前占位符 L416-444。

```kotlin
@Composable
private fun MoreSection() {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Extension, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("更多", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 44.dp, end = 12.dp, bottom = 8.dp)) {
                // 性能监控图层
                var perfEnabled by remember { mutableStateOf(false) }
                SettingSwitch("启用性能图层", perfEnabled) { perfEnabled = it }
                AnimatedVisibility(visible = perfEnabled) {
                    Column {
                        var bgAlpha by remember { mutableFloatStateOf(0.5f) }
                        Text("背景透明度: ${(bgAlpha * 100).toInt()}%")
                        Slider(value = bgAlpha, onValueChange = { bgAlpha = it }, valueRange = 0f..1f)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                // 自动隐藏工具栏
                var hideMode by remember { mutableIntStateOf(0) } // 0=开启按键映射时, 1=自动延迟, 2=不隐藏
                Text("自动隐藏工具栏:", style = MaterialTheme.typography.bodyMedium)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(hideMode == 0, { hideMode = 0 }); Text("开启按键映射时隐藏")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(hideMode == 1, { hideMode = 1 }); Text("自动隐藏")
                    }
                    AnimatedVisibility(visible = hideMode == 1) {
                        var delay by remember { mutableFloatStateOf(1000f) }
                        Text("延时: ${delay.toInt()}ms")
                        Slider(value = delay, onValueChange = { delay = it }, valueRange = 0f..5000f)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(hideMode == 2, { hideMode = 2 }); Text("不隐藏")
                    }
                }
            }
        }
    }
}
```

### Batch E 验证
1. 体感助手点击展开 → 开关/模式/灵敏度/X-Y反转/激活键 可操作
2. 更多点击展开 → 性能图层开关+透明度/自动隐藏3选项联动

---

## Batch F：按键映射（1 file，SubPanelContainer.kt，⚠️ 最后执行）

### TB-2：`KeyMappingSection` 实现 + 自动切换触控板

**位置**: 当前占位符 L309-333。

**关键特性**：开启按键映射 Switch 时自动将触控模式设为触控板(TRACKPAD)。

```kotlin
@Composable
private fun KeyMappingSection(engine: StreamEngine) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }  // 初始 false
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.VideogameAsset, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("启用按键映射", style = MaterialTheme.typography.bodyLarge,
                 color = MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { newValue ->
                enabled = newValue
                if (newValue) {
                    // ⚠️ 关键：开启按键映射时自动切换触控板模式
                    engine.prefConfig.enableEnhancedTouch = false
                    engine.prefConfig.enableNativeMousePointer = false
                    engine.prefConfig.touchscreenTrackpad = true
                    engine.prefConfig.writePreferences(context)
                    Toast.makeText(context, "已自动切换为触控板模式，可在触控模式中更改", Toast.LENGTH_SHORT).show()
                }
                // 注意：关闭时不自动恢复触控模式
                expanded = newValue
            })
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 44.dp, end = 12.dp, bottom = 8.dp)) {
                // 子入口 1：切换按键映射方案
                TextButton(onClick = {
                    // TODO: engine.controllerManager?.pageConfigController?.open()
                    // 注意：StreamEngine 当前没有 controllerManager 字段
                    // 可能需要从 Game.kt 移植 controllerManager 初始化逻辑
                    Toast.makeText(context, "方案选择（待对接旧编辑器）", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("切换按键映射方案 >")
                }

                // 子入口 2：编辑当前方案
                TextButton(onClick = {
                    // TODO: engine.controllerManager?.elementController?.changeMode(Edit)?.open()
                    Toast.makeText(context, "编辑方案（待对接旧编辑器）", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("编辑当前方案 >")
                }

                // 子入口 3：其它设置
                var showOther by remember { mutableStateOf(false) }
                TextButton(onClick = { showOther = !showOther }, modifier = Modifier.fillMaxWidth()) {
                    Text("其它设置 ${if (showOther) "▲" else "▼"}")
                }
                AnimatedVisibility(visible = showOther) {
                    Column {
                        // 触控开关/灵敏度/滚轮速度/震动/增强触控/不透明度/边框颜色/文字颜色
                        Text("其它设置项（待完善）", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
```

> **注意**: `controllerManager` 字段在 `StreamEngine` 中**不存在**（它在旧 `Game.kt` 中）。此批次的一个隐含任务是：如果确实需要对接旧编辑器，则需将 `ControllerManager` 的初始化和持有逻辑从 `Game.kt` 移植到 `StreamEngine`。这是一个较大的工程，可先保留 Toast 占位。

### Batch F 验证
1. 开启按键映射 Switch → Toast "已自动切换为触控板模式"
2. 确认触控模式已自动变为"触控板模式"选中态
3. 关闭按键映射 → 触控模式不变化
4. 3个子入口按钮可见（方案选择/编辑/其它设置）

---

## Batch G：返回键防抖 + 收尾（2 files）

### TF-1：返回键多级逻辑 + 300ms防抖

**Step 1**: `StreamOverlay.kt` — 暴露面板状态给 Activity

在 `StreamOverlay` 函数中添加回调参数 (L76-79):
```kotlin
// 改前:
fun StreamOverlay(
    engine: StreamEngine,
    connectionStage: String? = null,
) {
// 改后:
fun StreamOverlay(
    engine: StreamEngine,
    connectionStage: String? = null,
    onPanelStateChanged: (PanelState, DetailPage?) -> Unit = { _, _ -> },
) {
```

在 `StreamOverlay` 中暴露 `SubPanelContainer` 内部的 `detailPage` 状态。需要在 `SubPanelContainer` 也加回调，或者改为 `SubPanelContainer` 返回当前状态。简化方案：在 `onEntryClick` 和各种面板切换处调用 `onPanelStateChanged`。

**Step 2**: `StreamActivity.kt` — 实现多级返回

在 `StreamActivity` 中（精确位置取决于现有代码结构）:
```kotlin
// 防抖变量（类级别）
private var lastBackPressTime = 0L
private val backPressDebounceMs = 300L

// 面板状态追踪（通过 StreamOverlay 回调更新）
private var currentPanelState = PanelState.HIDDEN
private var currentDetailPage: DetailPage? = null

override fun onBackPressed() {
    val now = SystemClock.elapsedRealtime()
    if (now - lastBackPressTime < backPressDebounceMs) return
    lastBackPressTime = now

    when {
        // 1. 详情页 → 回主列表
        currentDetailPage != null && currentDetailPage != DetailPage.MAIN_LIST -> {
            // 通知 Compose 层回到主列表（需要状态提升机制）
        }
        // 2-3. 子面板/键盘面板 → 回竖向窄条
        currentPanelState == PanelState.SUB_PANEL || currentPanelState == PanelState.KEYBOARD_PANEL -> {
            // 通知 Compose 层回到 VERTICAL_BAR
        }
        // 4. 竖向窄条 → 隐藏
        currentPanelState == PanelState.VERTICAL_BAR -> {
            // 通知 Compose 层回到 HIDDEN
        }
        // 5. 全部隐藏 → 退出
        else -> {
            engine?.disconnect()
            super.onBackPressed()
        }
    }
}
```

> **技术难点**: Compose 状态与 Activity 同步。方案：在 `StreamActivity` 中持有 `panelState` 和 `detailPage`，通过 `StreamOverlay` 的 `onPanelStateChanged` 回调更新。同时 `StreamOverlay` 接受外部状态参数来控制面板展开/收起。这需要状态提升（state hoisting），将状态从 `StreamOverlay` 的 `remember` 提升到 `StreamActivity`。

**简化方案**: 如果状态提升太复杂，可退回到仅支持"面板可见时按返回收起面板"的单级逻辑，不做详情页→主列表的细粒度返回。

### TF-2：删除死代码

在 `SubPanelContainer.kt`:
1. 删除 `ShortcutActionsSection` composable (L518-535)
2. 删除 `DetailPage.SHORTCUT_ACTIONS` 枚举值 (L81) → 改为注释或直接移除
3. 删除 `when (page)` 中对应的分支 (L151-155)

### TF-3：收尾验证

- 检查所有 `Surface`/`Surface` 圆角一致性
- 空状态文案："暂无可用指令"、"暂无自定义按键"
- 横竖屏切换面板自适应

### Batch G 验证
1. 详情页可见时按返回 → 回到子面板主列表
2. 子面板可见时按返回 → 回到竖向窄条
3. 竖向窄条可见时按返回 → 回到悬浮按钮
4. 悬浮按钮时按返回 → 退出串流
5. 快速连按3次返回 → 只触发一次

---

## Batch H：全量回归验证

| # | 验证项 | 操作 |
|---|--------|------|
| 1 | 快捷操作入口改向 | 点击"快捷操作>"→键盘面板快捷键标签 |
| 2 | 触控模式4选1 | 切换+关联子项+持久化 |
| 3 | 按键映射+自动触控板 | 开启→Toast+触控模式联动 |
| 4 | 显示详情 | 帧率Chip/码率Slider/显示器列表/HDR/画面设置 |
| 5 | 主机设置 | 7开关可切换+持久化 |
| 6 | 外设详情 | 4子入口+次级导航 |
| 7 | 体感助手 | 展开/开关/模式/灵敏度/反转/激活键 |
| 8 | 更多 | 性能图层+自动隐藏 |
| 9 | 返回键多级 | 逐级返回到退出 |
| 10 | 返回键防抖 | 快速连按→仅触发1次 |
| 11 | 窄面板桌面/窗口 | Win+D/Win+Tab发送+面板关闭 |
| 12 | 键盘面板4标签 | 输入法/快捷键14预置/自定义CRUD/虚拟键盘/主机键盘 |
| 13 | 快捷操作行 | 固定按钮+可配按钮 不变 |
| 14 | 横竖屏 | 面板自适应 |
| 15 | 旧Game.kt | 旧入口串流不受影响 |

---

## 已知限制 & TODO

| 项目 | 说明 |
|------|------|
| 显示器运行时切换 | NvHTTP 无 API，仅启动时指定 `display_name` |
| `controllerManager` | StreamEngine 未持有，按键映射编辑入口暂用 Toast 占位 |
| `isCrownFeatureEnabled` | StreamEngine 无此字段，按键映射开关用本地 `remember` 管理 |
| `setTouchOverrideEnabled` | 不存在，平移缩放暂用 Switch 占位 |
| 主机设置部分字段 | 可能需要通过 `SharedPreferences` 直接读写而非 `prefConfig` 属性 |
