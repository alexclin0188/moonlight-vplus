# 显示设置子面板重构计划

> 创建日期：2026-06-16 | 状态：ready | 版本：1.2
> 前置依赖：stream_ui_completion（已完成）

---

## 一、需求概述

将现有 `DisplaySection`（`SubPanelContainer.kt` L729-884）完全重写为**两分组布局**。

### 分组1：帧率与画面

| # | 控件 | 实现说明 |
|---|------|---------|
| 1 | **帧率选择** | 默认折叠"视频帧率 自动/当前值"，展开RadioButton(自动/30/60/90/120/144/165)+解锁帧率Switch |
| 2 | **码率选择行** | 水平Chip: [自动][2M(清晰)][8M(高清)][20M(原画)][自定义] |
| 2a | ┗自动→智能码率 | 画质优先/均衡/低延迟 RadioButton—参考旧AdaptiveBitrateService |
| 2b | ┗自定义→SeekBar | 1M~800M, 松开实时 `setBitrate()`—参考旧BitrateCardController |
| 3 | **流量估算** | 小字"使用流量时,预估消耗{d}M/分钟" (kbps/8/60) |
| 4 | **输出缓冲区大小** | SeekBar 1~5帧, 默认2 |
| 5 | **MTK专属选项** | Switch |
| 6 | **HDR** | Switch(启用HDR) + HDR高亮度(子Switch)—保留旧逻辑 |
| 7 | **VDD虚拟显示器** | Switch—从旧显示器分组移入 |
| 8 | **外接显示器** | Switch—从旧显示器分组移入 |
| 9 | **拉伸视频** | Switch |
| 10 | **反转分辨率** | Switch |
| 11 | **可旋转画面** | Switch |

### 分组2：显示器

| # | 控件 | 实现说明 |
|---|------|---------|
| 1 | **显示器列表** | `NvHTTP.getDisplays()` 异步获取，RadioButton单选切换，active标记 |
| 2 | **分辨率设置** | 参考旧代码实现：预置6标准分辨率+Native+自定义分辨率(custom_resolutions SP)；首选控制通道0x5506运行时修改，降级为recreate重启 |
| 3 | **DPI缩放** | Dropdown选择 (`POST /display-scale`)，运行时调，不持久化 |

---

## 二、关键设计决策

### 2.1 "自动"帧率行为

参考旧代码 `StreamSettings.kt` 中 `addNativeFrameRateEntry()` 实现：

```kotlin
// 旧代码（StreamSettings.kt L876-894）
private fun addNativeFrameRateEntry(framerate: Float) {
    val frameRateRounded = framerate.roundToInt()
    if (frameRateRounded == 0) return
    val pref = findPreference<ListPreference>(PreferenceConfiguration.FPS_PREF_STRING)!!
    val fpsValue = frameRateRounded.toString()
    // 检测设备显示刷新率，如果不在现有帧率列表中则动态添加
    if (pref.entryValues.any { it.toString() == fpsValue }) {
        nativeFramerateShown = false; return
    }
    appendPreferenceEntry(pref, fpsName, fpsValue)
    nativeFramerateShown = true
}
```

**"自动"的含义**：选择"自动"时，不清除已有的 `list_fps` 值，**不做覆盖操作**，保留系统/连接协商的默认帧率。

实现要点：
- 在帧率折叠态显示 `自动/60`，其中数字部分读取 `prefConfig.fps`
- 选中"自动"时，`prefConfig.fps` 保持不变（不做写入），UI显示为"自动"
- 解锁帧率开关影响144/165的可见性（同 `checkbox_unlock_fps` 逻辑）

### 2.2 分辨率设置 — 参考旧代码实现路径

分辨率设置分两层实现：

**第一层（首选）：控制通道0x5506运行时修改**
- 根据Sunshine协议API-SS-07，type=0分辨率不支持HTTP，必须通过控制通道0x5506
- 如果MoonBridge支持0x5506，运行时即可生效，无需重启

**第二层（降级/备用）：activity.recreate()方式**
- 参考旧 `GameMenu.kt` 和 `Game.kt` 中的实现：
  ```kotlin
  // GameMenu.kt 中分辨率切换
  prefs.edit().putString(RESOLUTION_PREF_STRING, resString).apply()
  game.changeResolution()
  
  // Game.kt changeResolution()
  fun changeResolution() {
      isChangingResolution = true
      this.recreate()
  }
  ```
- 旧代码中通过 `activity.recreate()` 完全重启串流Activity实现切换
- 新面板中选中分辨率后，如果0x5506不可用，调用 `StreamEngine.changeResolution()`（已有此方法）

### 2.3 DPI 缩放

- 仅运行时调用，**不**持久化到 `prefConfig`
- 通过 `NvHTTP.getDisplayScaleOptions()` 获取支持列表
- 通过 `NvHTTP.setDisplayScale()` 设置
- 仅在 `scale_set_supported == true` 时展示缩放选项

---

## 三、旧代码参考：自动码率（智能码率）

### 3.1 ABR 生命周期（旧代码参考）

```
用户启用 "checkbox_adaptive_bitrate" → prefConfig.enableAdaptiveBitrate = true

连接建立时:
  → ConnectionCallbackHandler 调用 game.startAdaptiveBitrateIfEnabled()
  → 创建 AdaptiveBitrateService(periodic tick 1s)
  → service.start(prefConfig.bitrate, prefConfig.abrMode)

串流中:
  手动调码率 → BitrateCardController.adjustBitrate()
    → conn.setBitrate() → prefConfig.bitrate = newBitrate
    → adaptiveBitrateService.notifyManualOverride(newBitrate)  // 重置ABR状态

  ABR自动调 → AdaptiveBitrateService.tick() 每秒检测
    → Server模式: 发网络统计给Sunshine → 服务器决策
    → Local模式: 基于丢包率PID控制
    → applyBitrateInternal() → 通知listener更新UI + applyBitrateLocally()

断开时:
  → stopAdaptiveBitrate() → 关闭服务端ABR → 恢复初始码率 → 关闭线程池
```

### 3.2 关键参考链接

| 旧代码位置 | 内容 | 实现参考 |
|-----------|------|---------|
| `PreferenceConfiguration.kt` L81-82 | `enableAdaptiveBitrate` / `abrMode` 字段 | 读写方式 |
| `PreferenceConfiguration.kt` L1001-1002 | ABR从SP读取逻辑 | 读取方式 |
| `AdaptiveBitrateService.kt` | 完整ABR实现(server+local双模式) | 后台服务模式 |
| `BitrateCardController.kt` L229-288 | `adjustBitrate()`: 手动调码率+ABR联动 | notifyManualOverride模式 |
| `Game.kt` L1483-1506 | `startAdaptiveBitrateIfEnabled()` | 生命周期管理 |
| `Game.kt` L1509-1512 | `stopAdaptiveBitrate()` | 生命周期管理 |
| `NvConnection.kt` L627-663 | `setBitrate()` | 网络调用封装 |

### 3.3 新面板中的码率-ABR交互逻辑

新面板选中"自动"码率时：
```
用户点击 [自动] Chip
  → prefConfig.enableAdaptiveBitrate = true
  → 显示智能码率模式 RadioButton (quality/balanced/lowLatency)
  → 模式变更 → prefConfig.abrMode = 新值 → prefConfig.writePreferences()
  → ABR服务已由StreamEngine在连接时启动，读取最新的enableAdaptiveBitrate和abrMode
```

新面板选中预设/自定义码率时：
```
用户点击 [2M]/[8M]/[20M]/[自定义]
  → prefConfig.enableAdaptiveBitrate = false
  → prefConfig.bitrate = newKbps
  → conn.setBitrate(newKbps, null)  // 实时生效
  → 如果ABR已运行: adaptiveBitrateService?.notifyManualOverride(newKbps)
  → 注意: ABR服务的启停由StreamEngine在连接/断开时管理，新面板不直接控制ABR服务启停
```

---

## 四、旧代码参考：分辨率设置

### 4.1 预置分辨率列表

参考旧代码 `arrays.xml`：

| 值 | 显示名 | 常量（PreferenceConfiguration.kt） |
|----|--------|-----------------------------------|
| `640x360` | 360p | `RES_360P = "640x360"` |
| `854x480` | 480p | `RES_480P = "854x480"` |
| `1280x720` | 720p | `RES_720P = "1280x720"` |
| `1920x1080` | 1080p | `RES_1080P = "1920x1080"` |
| `2560x1440` | 1440p | `RES_1440P = "2560x1440"` |
| `3840x2160` | 4K | `RES_4K = "3840x2160"` |
| `Native` | 设备原生 | `RES_NATIVE = "Native"` |

### 4.2 自定义分辨率

参考旧 `CustomResolutionsPreference.kt`：

```kotlin
// 自定义分辨率存储在 SharedPreferences
object CustomResolutionsConsts {
    const val CUSTOM_RESOLUTIONS_FILE = "custom_resolutions"
    const val CUSTOM_RESOLUTIONS_KEY = "custom_resolutions"  // StringSet
}

// 读取自定义分辨率
val prefs = context.getSharedPreferences(
    CustomResolutionsConsts.CUSTOM_RESOLUTIONS_FILE, Context.MODE_PRIVATE)
val customSet = prefs.getStringSet(
    CustomResolutionsConsts.CUSTOM_RESOLUTIONS_KEY, emptySet())
// 格式: ["1920x1080", "2560x1440", ...]
```

**新面板实现**：在分辨率列表中增加"自定义分辨率"选项，点击后弹出对话框输入宽高，存储到 `custom_resolutions` SP 中。格式和验证参考旧 `CustomResolutionsPreference.ResolutionValidator`（宽320-7680，高240-4320，需偶数）。

### 4.3 分辨率变更流程

参考旧 `GameMenu.kt` 和 `Game.kt`：

```
用户在分辨率列表中选择 "2560x1440"
  → 写入 SharedPreferences: list_resolution = "2560x1440"
  → 调用 StreamEngine.changeResolution() → activity.recreate()
  → Activity onCreate:
      → prefConfig = PreferenceConfiguration.readPreferences(activity)
        → 从 list_resolution 解析出 prefConfig.width = 2560, prefConfig.height = 1440
      → buildStreamConfiguration()
        → .setResolution(2560, 1440)
      → 连接启动 → 新分辨率生效
```

如果0x5506控制通道可用：
```
用户在分辨率列表中选择 "2560x1440"
  → 通过 MoonBridge 发送 0x5506 消息
  → 主机端运行时切换分辨率（无需断开连接）
  → NvConnectionListener.onResolutionChanged() 回调更新本地状态
```

### 4.4 NvHTTP.launchApp() 中的分辨率传递

```kotlin
// 旧代码（NvHTTP.kt L602-665）
var queryParams = "appid=$appId" +
    "&mode=${streamConfig.reqWidth}x${streamConfig.reqHeight}x$fps" +
    "&resolutionScale=${streamConfig.resolutionScale}" +
    ...
```

其中 `reqWidth / reqHeight` 来自 `StreamConfiguration`：
```kotlin
val reqWidth: Int get() = width * hostResolutionScaleX100 / 100
val reqHeight: Int get() = height * hostResolutionScaleX100 / 100
```

---

## 五、技术方案

### 5.1 文件组织

```
app/src/main/java/com/alexclin/moonlink/stream/ui/
├── SubPanelContainer.kt        // 修改：替换旧DisplaySection→DisplaySettingsPanel；删除旧代码
├── DisplaySettingsPanel.kt     // 新建：完整显示设置面板
└── display/
    └── BitrateUtils.kt         // 新建：码率映射工具类

app/src/main/java/com/limelight/nvstream/http/
└── NvHTTP.kt                   // 修改：添加显示缩放API方法 + 扩展DisplayInfo
```

### 5.2 交互流程图

```
[子面板] "显示 >" → [DisplaySettingsPanel]
                        │
               ┌────────┴────────┐
          ▼ 帧率与画面         ▼ 显示器
               │                    │
    ┌──┬──┬───┴──┬───┬───┐   ┌────┴────┐
   帧率 码率 HDR  开关 流量  显示器   缩放
    │   │   │   组   估算   列表    设置
    │   │   │         │    │      │
   展开 自动→ HDR高   MTK 切换   分辨率
   Radio ABR  亮度    VDD  →     0x5506
   Button│   外接   重启    或recreate
        自定义→    拉伸/   提示   DPI
        SeekBar    反转/         下拉
        1M-800M    旋转
```

### 5.3 帧率交互

**折叠态**：
```
视频帧率      自动/60     ▶
```

**展开态**：
```
视频帧率      自动/60     ▲
  ○ 自动
  ● 30 FPS
  ○ 60 FPS
  ○ 90 FPS
  ○ 120 FPS
  ○ 144 FPS    ← unlockFps=false时隐藏
  ○ 165 FPS    ← unlockFps=false时隐藏
  ────────────
  解锁帧率            [OFF] ← checkbox_unlock_fps
```

### 5.4 码率交互

```
码率  [自动] [2M] [8M] [20M] [自定义]
│
├─ 自动选中：
│   智能码率模式（参考旧AdaptiveBitrateService）
│   ○ 画质优先 (quality)
│   ● 均衡 (balanced)
│   ○ 低延迟 (lowLatency)
│
├─ 2M/8M/20M选中：
│   无子项展示
│   直接调 conn.setBitrate() 实时生效
│
└─ 自定义选中：
    码率: 30 Mbps (30000 kbps)   ← 参考旧BitrateCardController映射
    ├────●────────────────┤
    1M                   800M
    使用流量时,预估消耗 3.75M/分钟
```

**码率-ABR联动逻辑**（参考旧BitrateCardController.adjustBitrate）：

| 操作 | enableAdaptiveBitrate | 网络调用 | ABR状态 |
|------|----------------------|---------|---------|
| 选"自动" | true | 无（ABR接管） | 正常检测 |
| 选2M/8M/20M | false | `conn.setBitrate()` | `notifyManualOverride()` |
| 选自定义（拖到某值松手） | false | `conn.setBitrate()` | `notifyManualOverride()` |
| 智能码率模式切换 | true | 无（ABR接管） | 仅改prefConfig.abrMode |

### 5.5 显示器分组交互

```
▼ 显示器
  ● DELL U2723QE           [active]
  ○ \\\\.\\DISPLAY2
  ───────────────────────

  当前分辨率: 1920 × 1080

  切换分辨率（参考旧代码resolution list + custom_resolutions）
  [640×360] [854×480] [1280×720] [1920×1080] [2560×1440] [3840×2160]
  [Native (2520×1680)]  ← 动态检测
  + 自定义...            ← 弹出对话框输入宽高（参考旧CustomResolutionsPreference）
  > 选中后：首选0x5506通道发送，降级为recreate

  DPI缩放  100% ▾         ← 仅运行时调用，不持久化
```

---

## 六、任务分解

### Batch A：基础设施

| ID | 任务 | 文件 | 说明 |
|----|------|------|------|
| DA-1 | `BitrateUtils.kt` 工具类 | 新建 `display/BitrateUtils.kt` | 码率预设枚举、流量估算、自定义SeekBar线性映射 |
| DA-2 | NvHTTP扩展示显示器API + 0x5506调研 | 修改 `NvHTTP.kt` | getDisplayScaleOptions/setDisplayScale+DisplayInfo扩展；检查MoonBridge是否支持0x5506 |
| DA-3 | `DisplaySettingsPanel.kt` 框架 | 新建 + 修改 `SubPanelContainer.kt` | 两分组LazyColumn框架，替换旧调用 |

### Batch B：帧率 + 码率(参考旧ABR) + HDR

| ID | 任务 | 文件 | 说明 |
|----|------|------|------|
| DB-1 | 帧率选择模块 | `DisplaySettingsPanel.kt` | 折叠态、RadioButton、解锁帧率、自动帧率逻辑(参考addNativeFrameRateEntry) |
| DB-2 | 码率选择行 | `DisplaySettingsPanel.kt` | 5 Chip；自动→ABR模式(参考旧AdaptiveBitrateService生命周期+notifyManualOverride)；自定义→SeekBar(参考旧adjustBitrate+setBitrate)；预设值直接setBitrate |
| DB-3 | HDR开关 | `DisplaySettingsPanel.kt` | HDR主开关 + HDR高亮度子开关（保留旧prefConfig.enableHdr/enableHdrHighBrightness） |

### Batch C：画面设置开关组

| ID | 任务 | 文件 | 说明 |
|----|------|------|------|
| DC-1 | 流量估算 | `DisplaySettingsPanel.kt` | kbps/8/60动态计算 |
| DC-2 | 输出缓冲区 + MTK + 3画面开关 | `DisplaySettingsPanel.kt` | 5个基本开关 |
| DC-3 | VDD + 外接显示器 | `DisplaySettingsPanel.kt` | 从旧DisplaySection迁移 |

### Batch D：显示器分组（分辨率参考旧代码实现）

| ID | 任务 | 文件 | 说明 |
|----|------|------|------|
| DD-1 | 显示器列表 | `DisplaySettingsPanel.kt` | getDisplays()+RadioButton+active+重启提示 |
| DD-2 | 分辨率 + DPI缩放 | `DisplaySettingsPanel.kt` | (1)预置6分辨率(参考旧arrays.xml)；(2)Native动态(参考旧addNativeResolutionEntry)；(3)自定义分辨率(参考旧CustomResolutionsPreference + custom_resolutions SP)；(4)选中后首选0x5506，降级changeResolution→recreate(参考旧Game.kt)；(5)DPI缩放Dropdown(运行时POST不持久化) |

### Batch E：收尾

| ID | 任务 | 文件 | 说明 |
|----|------|------|------|
| DE-1 | 清理旧代码 + 全量验证 | `SubPanelContainer.kt` | 删除旧DisplaySection+映射函数；回归旧Game.kt不受影响 |

---

## 七、涉及文件变更总清单

| 操作 | 路径 | 说明 |
|------|------|------|
| **新建** | `.../stream/ui/display/BitrateUtils.kt` | 码率映射 + 流量估算公共工具 |
| **新建** | `.../stream/ui/DisplaySettingsPanel.kt` | 完整显示设置面板（替代旧DisplaySection） |
| **修改** | `.../SubPanelContainer.kt` | 替换旧 DisplaySection → DisplaySettingsPanel；删除旧代码(约-150行) |
| **修改** | `.../nvstream/http/NvHTTP.kt` | 扩展DisplayInfo + 添加getDisplayScaleOptions()/setDisplayScale() |
| **不修改** | `com/limelight/BitrateCardController.kt` | 保留旧代码，新建工具函数后内部可引用公共函数 |

---

## 八、数据流与持久化

| UI控件 | Preference Key | 配置字段 | 网络调用/备注 |
|--------|---------------|---------|-------------|
| 帧率 RadioButton | `list_fps` | `prefConfig.fps` | 0=自动(不写入) |
| 解锁帧率 Switch | `checkbox_unlock_fps` | `prefConfig.unlockFps` | 影响144/165可见性 |
| 码率 Chip(2M/8M/20M) | `seekbar_bitrate_kbps` | `prefConfig.bitrate` | + `conn.setBitrate()` 实时 |
| 码率自定义 SeekBar | `seekbar_bitrate_kbps` | `prefConfig.bitrate` | + `conn.setBitrate()` 实时 |
| 智能码率 RadioButton | `list_abr_mode` | `prefConfig.abrMode` | 参考旧ABRService |
| 输出缓冲区 | `seekbar_output_buffer_queue_limit` | `prefConfig.outputBufferQueueLimit` | 1~5帧 |
| MTK Switch | `checkbox_force_mtk_max_operating_rate` | `prefConfig.forceMtkMaxOperatingRate` | |
| HDR Switch | `checkbox_enable_hdr` | `prefConfig.enableHdr` | |
| HDR高亮度 Switch | `checkbox_enable_hdr_high_brightness` | `prefConfig.enableHdrHighBrightness` | |
| VDD虚拟显示器 | (启动参数) | 无字段 | 重启串流生效 |
| 外接显示器 | `use_external_display` | `prefConfig.useExternalDisplay` | |
| 拉伸视频 | `checkbox_stretch_video` | `prefConfig.stretchVideo` | |
| 反转分辨率 | `checkbox_reverse_resolution` | `prefConfig.reverseResolution` | |
| 可旋转画面 | `checkbox_rotable_screen` | `prefConfig.rotableScreen` | |
| 显示器切换 | 启动参数 `display_name` | 无 | 重启串流生效 |
| 分辨率列表 | `list_resolution` | `prefConfig.width/height` | 参考旧resolution_values+custom_resolutions |
| 分辨率修改 | 无 | 无 | 首选0x5506，降级recreate |
| DPI缩放 | 无（不持久化） | 无 | `POST /display-scale` 运行时 |

---

## 九、风险与缓解

| 风险 | 缓解 |
|------|------|
| MoonBridge 未实现 0x5506 控制通道消息 | 降级为 `StreamEngine.changeResolution()→activity.recreate()`，参考旧Game.kt实现 |
| 自动码率与手动码率切换时ABR状态不同步 | 参考旧BitrateCardController的 `notifyManualOverride()` 模式 |
| 自定义分辨率 SP 格式与旧代码冲突 | 直接复用旧 `CustomResolutionsConsts` 的 key(file/key)，读写旧代码格式 |
| `NvHTTP.getDisplays()` 当前 `DisplayInfo` 字段不足 | 扩展类添加isPrimary, currentScalePercent等字段 |
| 预置分辨率列表需与旧 `resolution_values` 一致 | 直接复用旧 arrays.xml 中 `resolution_values` 字符串数组 |
| 旧 `adaptiveBitrateService` 在 StreamEngine 中未集成 | 由StreamEngine在连接时管理ABR生命周期，新面板仅控制prefConfig标志位 |

---

## 十、旧代码参考索引（实施时查阅）

### 自动码率参考

| 查阅文件 | 关键方法/行号 | 参考目的 |
|---------|-------------|---------|
| `AdaptiveBitrateService.kt` | `start()` L66, `stop()` L110, `notifyManualOverride()` L101, `tick()` L174, `getStatusText()` L134 | ABR完整实现 |
| `BitrateCardController.kt` | `adjustBitrate()` L229, `setup()` L92 | 手动调码率+ABR联动 |
| `Game.kt` | `startAdaptiveBitrateIfEnabled()` L1483, `stopAdaptiveBitrate()` L1509 | ABR生命周期管理 |
| `PreferenceConfiguration.kt` | `enableAdaptiveBitrate` L81, `abrMode` L82, `getDefaultBitrate()` L756 | 配置字段和默认码率计算 |

### 分辨率参考

| 查阅文件 | 关键方法/行号 | 参考目的 |
|---------|-------------|---------|
| `arrays.xml` | `resolution_names` L19, `resolution_values` L56 | 预置分辨率列表 |
| `PreferenceConfiguration.kt` | `RES_360P`~`RES_NATIVE` L665-671, `RESOLUTION_PREF_STRING` L475 | 分辨率常量 |
| `CustomResolutionsPreference.kt` | `CustomResolutionsConsts` L19, `loadStoredResolutions()` L185, `saveResolutions()` L194 | 自定义分辨率SP读写 |
| `CustomResolutionsPreferenceDialogFragment.kt` | 表单UI和验证 | 自定义分辨率对话框参考 |
| `GameMenu.kt` | 分辨率切换 L477-529 | 分辨率的运行时切换流程 |
| `Game.kt` | `changeResolution()` L1200, `onResolutionChanged()` L1618 | resolution change + recreate |
| `NvHTTP.kt` | `launchApp()` L602 | 分辨率在启动参数中的传递 |
| `StreamEngine.kt` | `changeResolution()` L509, `buildStreamConfiguration()` L331 | 新代码中已有的分辨率方法 |
