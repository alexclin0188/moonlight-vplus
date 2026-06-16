# Handoff: display_settings_redesign（可独立执行版）

> 本文件包含 build agent 执行此计划所需的所有信息。
> **不依赖当前会话上下文** — 所有文件路径、API、代码模式均已包含。
> 版本：1.2

---

## 项目环境

- **项目**: Android Kotlin + Jetpack Compose
- **新代码包**: `com.alexclin.moonlink.stream.*`
- **旧代码包**: `com.limelight.*`（只读引用，不做修改）
- **工作目录**: `app/src/main/java/com/alexclin/moonlink/stream/`
- **当前计划状态**: `approved`（用户已批准执行）

---

## 关键约束

1. **新建文件** — 允许新建 `DisplaySettingsPanel.kt` 和 `BitrateUtils.kt`
2. **不修改 `com.limelight.*` 包** — 只引用其公开 API，但 `NvHTTP.kt` 除外（可在其中添加新公开方法）
3. **`NvHTTP.kt` 可扩展** — 在 `com.limelight.nvstream.http.NvHTTP.kt` 中添加新方法
4. **`DisplayInfo` 类可扩展** — 当前只有 `index/name/guid` 3个字段，需添加 `isPrimary`, `currentScalePercent` 等 Sunshine JSON 完整字段

---

## 整体布局

```
┌─ 显示 ─────────────────────────────┐
│                                    │
│ ▼ 帧率与画面                        │
│  帧率选择(折叠态)  自动/60   ▶     │
│  码率 [自动][2M][8M][20M][自定义]   │
│  流量估算小字                       │
│  ─────────────                     │
│  HDR [ON] → 展开HDR高亮度          │
│  VDD虚拟显示器 [OFF]               │
│  使用外接显示器 [OFF]               │
│  MTK专属选项 [OFF]                  │
│  输出缓冲区: 2帧   ├─●────┤ 1~5   │
│  拉伸视频 [ON]                     │
│  反转分辨率 [OFF]                  │
│  可旋转画面 [OFF]                  │
│                                    │
│ ▼ 显示器                           │
│  ● DELL U2723QE [active]           │
│  ○ \\\\.\\DISPLAY2                 │
│  分辨率: 1920×1080                 │
│  [1920×1080] [2560×1440]  ←0x5506│
│  DPI缩放: 100% ▾                  │
└────────────────────────────────────┘
```

---

## 执行批次

### Batch A：基础设施（DA-1, DA-2, DA-3）

#### DA-1：新建 `BitrateUtils.kt`

**路径**: `app/src/main/java/com/alexclin/moonlink/stream/ui/display/BitrateUtils.kt`

**内容**:

```kotlin
package com.alexclin.moonlink.stream.ui.display

object BitrateUtils {
    const val BITRATE_AUTO = 0
    const val BITRATE_2M = 2000
    const val BITRATE_8M = 8000
    const val BITRATE_20M = 20000
    const val BITRATE_CUSTOM_MIN = 1000
    const val BITRATE_CUSTOM_MAX = 800000

    enum class BitratePreset(val label: String, val kbps: Int) {
        AUTO("自动", BITRATE_AUTO),
        M2("2M(清晰)", BITRATE_2M),
        M8("8M(高清)", BITRATE_8M),
        M20("20M(原画)", BITRATE_20M),
        CUSTOM("自定义", -1),
    }

    fun getPresetByKbps(kbps: Int): BitratePreset = when {
        kbps <= 1 -> BitratePreset.AUTO
        kbps <= 3000 -> BitratePreset.M2
        kbps <= 15000 -> BitratePreset.M8
        kbps <= 50000 -> BitratePreset.M20
        else -> BitratePreset.CUSTOM
    }

    fun estimateTrafficMbPerMin(bitrateKbps: Int): Float =
        bitrateKbps.toFloat() / 8f / 60f

    fun formatTrafficEstimate(bitrateKbps: Int): String {
        val mbPerMin = estimateTrafficMbPerMin(bitrateKbps)
        return if (mbPerMin >= 10) {
            "使用流量时,预估消耗 ${mbPerMin.toInt()}M/分钟"
        } else {
            "使用流量时,预估消耗 ${"%.1f".format(mbPerMin)}M/分钟"
        }
    }

    // 自定义SeekBar 线性映射 0~100 → 1M~800M
    fun customProgressToKbps(progress: Int): Int {
        val ratio = progress / 100f
        return (BITRATE_CUSTOM_MIN + (BITRATE_CUSTOM_MAX - BITRATE_CUSTOM_MIN) * ratio).toInt()
            .coerceIn(BITRATE_CUSTOM_MIN, BITRATE_CUSTOM_MAX)
    }

    fun customKbpsToProgress(kbps: Int): Int {
        val ratio = (kbps - BITRATE_CUSTOM_MIN).toFloat() / (BITRATE_CUSTOM_MAX - BITRATE_CUSTOM_MIN)
        return (ratio.coerceIn(0f, 1f) * 100f).toInt()
    }
}
```

---

#### DA-2：NvHTTP 添加显示器缩放 API + 扩展 DisplayInfo

**文件**: `app/src/main/java/com/limelight/nvstream/http/NvHTTP.kt`

**在 `DisplayInfo` 类（L81-85）中添加字段**:

```kotlin
class DisplayInfo(
    val index: Int,
    val name: String,
    val guid: String,
    val isPrimary: Boolean = false,
    val currentScalePercent: Int = 100,
    val supportedScalePercents: List<Int> = emptyList(),
    val scaleSetSupported: Boolean = false,
)
```

**更新 `getDisplays()` 方法（L526-556）的解析逻辑**，从 JSON 中读取新字段：
```kotlin
val isPrimary = displayObj.optBoolean("is_primary", false)
val scaleSetSupported = displayObj.optBoolean("scale_set_supported", false)
val currentScalePercent = displayObj.optInt("current_scale_percent", 100)
val scalesArray = displayObj.optJSONArray("supported_scale_percents")
val supportedScales = if (scalesArray != null) {
    (0 until scalesArray.length()).map { scalesArray.optInt(it) }
} else emptyList()
displays.add(DisplayInfo(i, friendlyName, guid, isPrimary, currentScalePercent, supportedScales, scaleSetSupported))
```

**在 `getDisplays()` 后面添加新方法**（L556之后）：

```kotlin
@Throws(IOException::class, InterruptedException::class)
fun getDisplayScaleOptions(displayName: String? = null, deviceId: String? = null): JSONObject? {
    try {
        val query = StringBuilder()
        displayName?.let { query.append("display_name=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        deviceId?.let {
            if (query.isNotEmpty()) query.append("&")
            query.append("device_id=").append(java.net.URLEncoder.encode(it, "UTF-8"))
        }
        val jsonStr = openHttpConnectionToString(
            httpClientLongConnectTimeout, getHttpsUrl(true),
            "display-scale-options", query.toString()
        )
        return JSONObject(jsonStr)
    } catch (_: Exception) { return null }
}

@Throws(IOException::class, InterruptedException::class)
fun setDisplayScale(scalePercent: Int, displayName: String? = null): Boolean {
    try {
        val query = StringBuilder().append("scale_percent=").append(scalePercent)
        displayName?.let { query.append("&display_name=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        val jsonStr = openHttpConnectionToString(
            httpClientLongConnectTimeout, getHttpsUrl(true),
            "display-scale", query.toString()
        )
        return JSONObject(jsonStr).optBoolean("success", false)
    } catch (_: Exception) { return false }
}
```

**调研 0x5506 控制通道结果**：已检查 `MoonBridge.java`，**没有**任何与 0x5506 / changeResolution / dynamic param 相关的 native 方法。这意味着运行时分辨率修改尚未在 MoonBridge 层实现。因此分辨率设置**降级为** `activity.recreate()` 方式（参考旧 Game.kt / GameMenu.kt 实现）。未来如需支持 0x5506，需在 MoonBridge C 层、JNI 层、Java 层三层添加。

---

#### DA-3：新建 `DisplaySettingsPanel.kt` + 替换调用

**新建文件**: `app/src/main/java/com/alexclin/moonlink/stream/ui/DisplaySettingsPanel.kt`

**框架代码**：

```kotlin
package com.alexclin.moonlink.stream.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.display.BitrateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast

@Composable
fun DisplaySettingsPanel(engine: StreamEngine, onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    DetailScaffold(title = "显示", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // === 分组1：帧率与画面 ===
            item { GroupTitle("帧率与画面") }

            // --- DB-1: 帧率选择 ---
            item { FpsSelector(engine) }

            // --- DB-2: 码率选择行 ---
            item { BitrateSelector(engine) }

            // --- DC-1: 流量估算 ---
            item { TrafficEstimate(pref.bitrate) }

            // --- DB-3: HDR ---
            item { HdrSection(engine) }

            // --- DC-3: VDD + 外接 ---
            item { VddSection(engine) }

            // --- DC-2: 输出缓冲区 ---
            item { OutputBufferSlider(engine) }

            // --- DC-2: MTK ---
            item { MtkSwitch(engine) }

            // --- DC-2: 画面设置开关组 ---
            item { VideoSwitches(engine) }

            Spacer(Modifier.height(8.dp))
            item { HorizontalDivider() }
            Spacer(Modifier.height(8.dp))

            // === 分组2：显示器 ===
            item { GroupTitle("显示器") }

            // --- DD-1: 显示器列表 ---
            item { MonitorList(engine) }

            // --- DD-2: 分辨率 + 缩放 ---
            item { MonitorSettings(engine) }
        }
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium,
         color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(vertical = 8.dp))
}
```

**修改 `SubPanelContainer.kt`**：

在 `when (detailPage)` 分支中：
```kotlin
// 改前:
DetailPage.DISPLAY -> {
    DisplaySection(engine = engine, onBack = { detailPage = DetailPage.MAIN_LIST })
}
// 改后:
DetailPage.DISPLAY -> {
    DisplaySettingsPanel(engine = engine, onBack = { detailPage = DetailPage.MAIN_LIST })
}
```

删除 `import` 中旧的引用，添加：
```kotlin
import com.alexclin.moonlink.stream.ui.DisplaySettingsPanel
```

---

### Batch B：帧率 + 码率 + HDR（DB-1, DB-2, DB-3）

#### DB-1：FpsSelector 实现

```kotlin
@Composable
private fun FpsSelector(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var expanded by remember { mutableStateOf(false) }
    var unlockFps by remember { mutableStateOf(pref.unlockFps) }
    var selectedFps by remember { mutableIntStateOf(pref.fps) }

    // 基础帧率选项
    val baseFpsOptions = listOf(0, 30, 60, 90, 120)  // 0=自动
    val extraFpsOptions = listOf(144, 165)

    val currentFpsText = if (selectedFps == 0) "自动" else "${selectedFps}"

    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
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
                null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 8.dp)) {
                // 基础选项
                baseFpsOptions.forEach { fps ->
                    val label = if (fps == 0) "自动" else "${fps} FPS"
                    FpsRadioItem(fps, label, selectedFps == fps) {
                        selectedFps = it
                        if (it != 0) {
                            pref.fps = it
                            pref.writePreferences(context)
                        }
                        // 自动=0时不覆盖prefConfig
                    }
                }
                // 额外选项（受解锁开关影响）
                if (unlockFps) {
                    extraFpsOptions.forEach { fps ->
                        FpsRadioItem(fps, "${fps} FPS", selectedFps == fps) {
                            selectedFps = it
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
    Row(verticalAlignment = Alignment.CenterVertically,
         modifier = Modifier.fillMaxWidth()
             .clickable { onClick(value) }
             .padding(vertical = 3.dp)) {
        RadioButton(selected = selected, onClick = { onClick(value) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
```

**关于"自动"帧率的说明**：选择"自动"(value=0)时，不清除 `prefConfig.fps`。此时UI显示"自动"，但实际上串流使用上次设置的值或设备原生刷新率（参考旧代码 `StreamSettings.addNativeFrameRateEntry` 检测设备原生刷新率）。用户期望的"自动"行为是：由系统/连接协商决定，不强制特定帧率。

---

#### DB-2：BitrateSelector 实现

```kotlin
@Composable
private fun BitrateSelector(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val presets = BitrateUtils.BitratePreset.entries.toList()

    var selectedPreset by remember { mutableStateOf(BitrateUtils.getPresetByKbps(pref.bitrate)) }
    var abrMode by remember { mutableStateOf(pref.abrMode) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }
    var customKbps by remember { mutableIntStateOf(pref.bitrate.coerceIn(
        BitrateUtils.BITRATE_CUSTOM_MIN, BitrateUtils.BITRATE_CUSTOM_MAX)) }

    // 初始化自定义SeekBar的值
    LaunchedEffect(Unit) {
        if (selectedPreset == BitrateUtils.BitratePreset.CUSTOM) {
            sliderProgress = BitrateUtils.customKbpsToProgress(pref.bitrate).toFloat()
        }
    }

    Column {
        // 码率选择行（水平Chip）
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = {
                        selectedPreset = preset
                        onBitratePresetSelected(engine, preset, context, customKbps)
                    },
                    label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // 自动 → 智能码率模式
        AnimatedVisibility(visible = selectedPreset == BitrateUtils.BitratePreset.AUTO) {
            Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text("智能码率模式", style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.primary)
                val modes = listOf(
                    Triple("quality", "画质优先"),
                    Triple("balanced", "均衡"),
                    Triple("lowLatency", "低延迟"),
                )
                modes.forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                         modifier = Modifier.fillMaxWidth()
                             .clickable { onAbrModeSelected(engine, value, context) }
                             .padding(vertical = 2.dp)) {
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
                    onValueChange = { sliderProgress = it },
                    onValueChangeFinished = {
                        val kbps = BitrateUtils.customProgressToKbps(sliderProgress.toInt())
                        customKbps = kbps
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
        BitrateUtils.BitratePreset.M2 -> {
            setFixedBitrate(engine, pref, BitrateUtils.BITRATE_2M, context)
        }
        BitrateUtils.BitratePreset.M8 -> {
            setFixedBitrate(engine, pref, BitrateUtils.BITRATE_8M, context)
        }
        BitrateUtils.BitratePreset.M20 -> {
            setFixedBitrate(engine, pref, BitrateUtils.BITRATE_20M, context)
        }
        BitrateUtils.BitratePreset.CUSTOM -> {
            setFixedBitrate(engine, pref, customKbps, context)
        }
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
```

---

#### DB-3：HDR 开关

```kotlin
@Composable
private fun HdrSection(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var hdrEnabled by remember { mutableStateOf(pref.enableHdr) }
    var highBrightness by remember { mutableStateOf(pref.enableHdrHighBrightness) }

    Column {
        SettingSwitch("HDR", hdrEnabled) {
            hdrEnabled = it
            pref.enableHdr = it
            pref.writePreferences(context)
        }
        AnimatedVisibility(visible = hdrEnabled) {
            SettingSwitch("  HDR高亮度", highBrightness,
                modifier = Modifier.padding(start = 24.dp)) {
                highBrightness = it
                pref.enableHdrHighBrightness = it
                pref.writePreferences(context)
            }
        }
    }
}
```

---

### Batch C：流量估算 + 画面开关组（DC-1, DC-2, DC-3）

#### DC-1：流量估算

```kotlin
@Composable
private fun TrafficEstimate(bitrateKbps: Int) {
    Text(
        text = BitrateUtils.formatTrafficEstimate(bitrateKbps),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
```

**注意**：`bitrateKbps` 需要响应式跟随当前码率值。在 `DisplaySettingsPanel` 中调用时，传入 `pref.bitrate` 即可，Compose 会自动重组。

---

#### DC-2：输出缓冲区 + MTK + 画面设置

```kotlin
@Composable
private fun OutputBufferSlider(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val currentValue = pref.outputBufferQueueLimit.coerceIn(1, 5)
    var sliderValue by remember { mutableFloatStateOf(currentValue.toFloat()) }

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
                val value = sliderValue.toInt().coerceIn(1, 5)
                pref.outputBufferQueueLimit = value
                pref.writePreferences(context)
            },
            valueRange = 1f..5f,
            steps = 3,
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
        checked = it
        pref.forceMtkMaxOperatingRate = it
        pref.writePreferences(context)
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
        SettingSwitch("拉伸视频", stretch) {
            stretch = it; pref.stretchVideo = it; pref.writePreferences(context)
        }
        SettingSwitch("反转分辨率", reverse) {
            reverse = it; pref.reverseResolution = it; pref.writePreferences(context)
        }
        SettingSwitch("可旋转画面", rotable) {
            rotable = it; pref.rotableScreen = it; pref.writePreferences(context)
        }
    }
}
```

#### DC-3：VDD + 外接显示器

```kotlin
@Composable
private fun VddSection(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    var useVdd by remember { mutableStateOf(false) }
    var useExternal by remember { mutableStateOf(pref.useExternalDisplay) }

    Column {
        SettingSwitch("VDD虚拟显示器", useVdd) {
            useVdd = it
            if (it) Toast.makeText(context, "VDD切换需重启串流生效", Toast.LENGTH_SHORT).show()
        }
        SettingSwitch("使用外接显示器", useExternal) {
            useExternal = it
            pref.useExternalDisplay = it
            pref.writePreferences(context)
        }
    }
}
```

---

### Batch D：显示器分组（DD-1, DD-2）

#### DD-1：显示器列表

```kotlin
@Composable
private fun MonitorList(engine: StreamEngine) {
    val context = LocalContext.current
    var displays by remember { mutableStateOf<List<com.limelight.nvstream.http.NvHTTP.DisplayInfo>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val nvHttp = engine.conn?.createNvHttp()
                val list = nvHttp?.getDisplays() ?: emptyList()
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
                        .clickable { onDisplaySelected(index, context) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isActive,
                        onClick = { onDisplaySelected(index, context) })
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

private fun onDisplaySelected(index: Int, context: android.content.Context) {
    // 显示器切换需重启
    Toast.makeText(context, "显示器切换需重启串流生效", Toast.LENGTH_SHORT).show()
}
```

---

#### DD-2：分辨率 + DPI 缩放（参考旧代码实现）

**实现参考**：
- 预置6分辨率：`arrays.xml` → `resolution_values`（640x360, 854x480, 1280x720, 1920x1080, 2560x1440, 3840x2160）
- Native分辨率：`WindowManager.defaultDisplay.getRealSize()`（参考旧 `PreferenceConfiguration.kt` readPreferences L945-978）
- 自定义分辨率：`SharedPreferences(CUSTOM_RESOLUTIONS_FILE).getStringSet(CUSTOM_RESOLUTIONS_KEY)`（参考旧 `CustomResolutionsPreference.kt`）
- 分辨率切换：首选0x5506控制通道，降级 `StreamEngine.changeResolution()→activity.recreate()`
- 分辨率写入SP key：`list_resolution`（`PreferenceConfiguration.RESOLUTION_PREF_STRING`）

```kotlin
@Composable
private fun MonitorSettings(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig

    // ── 分辨率列表 ──
    // 参考旧 arrays.xml resolution_values + CustomResolutionsPreference
    val standardResolutions = listOf(
        "640x360", "854x480", "1280x720",
        "1920x1080", "2560x1440", "3840x2160",
    )
    // 检测Native分辨率（参考旧PreferenceConfiguration.kt readPreferences）
    val nativeRes by remember {
        mutableStateOf(run {
            val display = (context.getSystemService(Context.WINDOW_SERVICE)
                    as android.view.WindowManager).defaultDisplay
            val size = android.graphics.Point()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                display.getRealSize(size)
            } else {
                display.getSize(size)
            }
            "${size.x}x${size.y}"
        })
    }
    // 读取自定义分辨率（参考旧CustomResolutionsPreference.kt）
    val customResSet by remember {
        mutableStateOf(
            context.getSharedPreferences(
                com.limelight.preferences.CustomResolutionsPreference.CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE,
                Context.MODE_PRIVATE
            ).getStringSet(
                com.limelight.preferences.CustomResolutionsPreference.CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY,
                emptySet()
            )?.sortedBy { it } ?: emptyList()
        )
    }

    val allResolutions = remember {
        standardResolutions +
            listOf("Native ($nativeRes)") +
            customResSet.map { "$it (自定义)" }
    }

    var selectedRes by remember { mutableStateOf("${pref.width}x${pref.height}") }
    var showCustomDialog by remember { mutableStateOf(false) }

    Column {
        // 当前分辨率
        Text("当前分辨率", style = MaterialTheme.typography.labelLarge,
             color = MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Text("${pref.width} × ${pref.height}",
             style = MaterialTheme.typography.bodyMedium,
             modifier = Modifier.padding(bottom = 8.dp))

        // 可选分辨率列表（Chip选择）
        // 参考旧GameMenu.kt L477-529的分辨率切换流程
        Text("切换分辨率", style = MaterialTheme.typography.labelLarge,
             color = MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(bottom = 4.dp))

        // 使用LazyRow展示Chip（或使用FlowLayout自适应换行）
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

        // 添加自定义分辨率按钮
        TextButton(onClick = { showCustomDialog = true }) {
            Text("+ 添加自定义分辨率")
        }

        // 自定义分辨率对话框（参考旧CustomResolutionsPreferenceDialogFragment）
        if (showCustomDialog) {
            CustomResolutionDialog(
                onDismiss = { showCustomDialog = false },
                onConfirm = { width, height ->
                    saveCustomResolution(context, width, height)
                    showCustomDialog = false
                    // TODO: 刷新列表或提示重启
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // DPI 缩放（运行时调用，不持久化）
        // 参考旧NvHTTP.getDisplayScaleOptions/setDisplayScale
        var scalePercent by remember { mutableIntStateOf(100) }
        var scaleSupported by remember { mutableStateOf(false) }
        var supportedScales by remember { mutableStateOf(listOf(100)) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    val nvHttp = engine.conn?.createNvHttp()
                    val displays = nvHttp?.getDisplays() ?: emptyList()
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
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        supportedScales.forEach { scale ->
                            DropdownMenuItem(
                                text = { Text("${scale}%") },
                                onClick = {
                                    expanded = false
                                    scalePercent = scale
                                    kotlinx.coroutines.MainScope().launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                engine.conn?.createNvHttp()?.setDisplayScale(scale)
                                            } catch (_: Exception) {}
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

private fun onResolutionSelected(
    engine: StreamEngine, context: android.content.Context,
    res: String, nativeRes: String,
) {
    val actualRes = if (res == nativeRes) "Native" else res
    // 写入 SharedPreferences（参考旧GameMenu.kt）
    val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().putString(
        com.limelight.preferences.PreferenceConfiguration.RESOLUTION_PREF_STRING,
        actualRes
    ).apply()

    // TODO: 首选通过MoonBridge发送0x5506控制通道消息运行时修改分辨率
    // 如果0x5506不可用，降级为recreate方式
    engine.changeResolution()  // → activity.recreate()
}

private fun saveCustomResolution(context: android.content.Context, width: Int, height: Int) {
    val prefs = context.getSharedPreferences(
        com.limelight.preferences.CustomResolutionsPreference.CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE,
        Context.MODE_PRIVATE
    )
    val set = prefs.getStringSet(
        com.limelight.preferences.CustomResolutionsPreference.CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY,
        mutableSetOf()
    )?.toMutableSet() ?: mutableSetOf()
    set.add("${width}x${height}")
    prefs.edit().putStringSet(
        com.limelight.preferences.CustomResolutionsPreference.CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY,
        set
    ).apply()
    android.widget.Toast.makeText(context, "自定义分辨率已保存，请重新选择", android.widget.Toast.LENGTH_SHORT).show()
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
                // 验证参考旧ResolutionValidator: 宽320-7680, 高240-4320, 需偶数
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
```

---

### Batch E：清理 + 验证（DE-1）

#### 删除旧代码

在 `SubPanelContainer.kt` 中：

1. **删除** `DisplaySection` 整个函数（旧代码 L729-884）
2. **删除** `progressToBitrateKbps` 和 `bitrateToProgress` 函数（L1137-1150）
3. **保留** `SectionTitle` 和 `SettingSwitch`（其他 Section 共用）
4. **确认** 不再引用 `progressToBitrateKbps` / `bitrateToProgress`

#### 全量验证清单

| # | 验证项 | 预期结果 |
|---|--------|---------|
| 1 | 进入"显示"详情页 | 两分组布局，所有控件可见 |
| 2 | 帧率折叠点击 | 展开RadioButton列表 |
| 3 | 帧率 RadioButton 选择 | 选中高亮，写prefConfig |
| 4 | 解锁帧率 ON→144/165出现 | Switch影响可见性 |
| 5 | "自动"帧率选择 | fps=0，UI显示"自动" |
| 6 | 码率 Chip 切换 | 选中高亮，setBitrate调用 |
| 7 | 码率"自动"→ABR模式显示 | 3种模式RadioButton可选 |
| 8 | 码率"自定义"→SeekBar | 1M~800M拖动松开后setBitrate |
| 9 | 流量估算文字变化 | 随码率选择实时更新 |
| 10 | HDR ON→HDR高亮度可见 | AnimatedVisibility |
| 11 | VDD/外接开关 | 切换正常 |
| 12 | 输出缓冲区 SeekBar | 1~5帧，默认2 |
| 13 | MTK开关 | 切换正常 |
| 14 | 拉伸/反转/旋转 3开关 | 切换正常 |
| 15 | 显示器列表加载 | 异步加载显示名称+active标记 |
| 16 | 显示器切换 | Toast提示重启生效 |
| 17 | DPI缩放下拉 | 如果有可用选项，选择后调用API |
| 18 | 旧DisplaySection完全移除 | 无编译错误 |
| 19 | 旧BitrateCardController | 未被修改 |
| 20 | 旧Game.kt串流 | 正常不受影响 |
| 21 | 横竖屏切换 | 面板自适应 |

---

## 需要新建的文件

1. `app/src/main/java/com/alexclin/moonlink/stream/ui/display/BitrateUtils.kt` — 工具类
2. `app/src/main/java/com/alexclin/moonlink/stream/ui/DisplaySettingsPanel.kt` — 完整面板

## 需要修改的文件

1. `app/src/main/java/com/alexclin/moonlink/stream/ui/SubPanelContainer.kt` — 替换旧调用 + 删除旧代码
2. `app/src/main/java/com/limelight/nvstream/http/NvHTTP.kt` — 扩展 DisplayInfo + 添加缩放API方法

## 需要调研的接口

| # | 调研内容 | 影响 |
|---|---------|------|
| 1 | MoonBridge 是否已支持 0x5506 控制通道消息 | 决定分辨率修改的实现路径 |
| 2 | `GET /serverinfo` 中 supportedDisplayMode 的解析 | 决定可选分辨率列表的数据来源 |
| 3 | AdaptiveBitrateService 在 StreamEngine 中是否已初始化 | 决定"自动"码率时 ABR 启动方式 |

---

## 已知限制

1. **HDR排除**：如果旧 DisplaySection 中有 HDR 相关逻辑（`checkbox_enable_hdr`），已迁移到新面板中
2. **VDD/外接**：已从显示器分组移到帧率与画面分组，VDD 需重启生效
3. **分辨率运行时修改**：0x5506 控制通道需 MoonBridge 支持，如不支持则先展示选项+提示
4. **DPI缩放**：仅运行时调用，不持久化到 prefConfig
5. **显示器切换**：需重启串流，使用 `display_name` 启动参数

---

## 旧代码参考索引（实施时必须查阅）

### 自动码率（智能码率）参考

| 文件 | 关键方法/行号 | 参考目的 |
|------|-------------|---------|
| `.../limelight/nvstream/http/AdaptiveBitrateService.kt` | `start()` L66, `stop()` L110, `notifyManualOverride()` L101, `tick()` L174 | ABR完整实现—后台服务生命周期+三种模式行为 |
| `.../limelight/BitrateCardController.kt` | `adjustBitrate()` L229, `setup()` L92 | 手动调码率→conn.setBitrate()+notifyManualOverride联动模式 |
| `.../limelight/Game.kt` | `startAdaptiveBitrateIfEnabled()` L1483, `stopAdaptiveBitrate()` L1509 | ABR服务生命周期管理 |
| `.../limelight/preferences/PreferenceConfiguration.kt` | `enableAdaptiveBitrate` L81, `abrMode` L82, `getDefaultBitrate()` L756 | ABR开关和模式存储 + 默认码率计算 |

**实施要点**：
1. 新面板"自动"→设置 `prefConfig.enableAdaptiveBitrate=true` + 显示Mode选择
2. 新面板"2M/8M/20M/自定义"→设置 `prefConfig.enableAdaptiveBitrate=false` → `conn.setBitrate(kbps)`
3. ABR服务的**启停由StreamEngine在连接/断开时管理**，新面板不直接控制
4. 手动设码率后需调用`notifyManualOverride()`重置ABR内部状态（参考BitrateCardController L241）

### 分辨率设置参考

| 文件 | 关键方法/行号 | 参考目的 |
|------|-------------|---------|
| `res/values/arrays.xml` | `resolution_names` L19, `resolution_values` L56 | 6个预置分辨率值表 |
| `.../limelight/preferences/PreferenceConfiguration.kt` | `RES_360P`~`RES_NATIVE` L665-671, `RESOLUTION_PREF_STRING` L475 | 分辨率常量和SP key |
| `.../limelight/preferences/CustomResolutionsPreference.kt` | `CustomResolutionsConsts` L19, `loadStoredResolutions()` L185, `saveResolutions()` L194 | 自定义分辨率SP: file="custom_resolutions", key="custom_resolutions", 格式StringSet |
| `.../limelight/preferences/CustomResolutionsPreferenceDialogFragment.kt` | 完整UI和验证逻辑 | 自定义分辨率对话框实现参考 |
| `.../limelight/GameMenu.kt` | 分辨率切换 L477-529 | `prefs.edit().putString(RESOLUTION_PREF_STRING,res).apply()` + `game.changeResolution()` |
| `.../limelight/Game.kt` | `changeResolution()` L1200, `onResolutionChanged()` L1618 | `recreate()` 方式切换 + 主机分辨率变化回调 |
| `.../nvstream/http/NvHTTP.kt` | `launchApp()` L602-665 | 分辨率通过 `mode=WxHxFPS` 在启动URL中传递 |
| `.../alexclin/.../stream/engine/StreamEngine.kt` | `changeResolution()` L509, `buildStreamConfiguration()` L331 | 新代码中已有的 `activity.recreate()` 方法 |

**实施要点**：
1. **分辨率列表来源**：从旧 `arrays.xml` 的 `resolution_values` 读取6个标准值 + `WindowManager.defaultDisplay` 检测Native分辨率 + 从 `custom_resolutions` SP读取自定义分辨率
2. **选择分辨率后**：
   - 首选：发送0x5506控制通道消息（需调研MoonBridge支持情况）
   - 降级：写入 `list_resolution` SP + 调用 `StreamEngine.changeResolution()` → `activity.recreate()`（参考旧GameMenu.kt流程）
3. **自定义分辨率管理**：复用旧 `CustomResolutionsConsts` 的 `CUSTOM_RESOLUTIONS_FILE` / `CUSTOM_RESOLUTIONS_KEY` 常量，写入格式兼容旧代码
