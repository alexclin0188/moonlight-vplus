# 代码审查修复计划

> 创建日期：2026-06-18 | 状态：ready | 版本：1.0
> 前置依赖：display_settings_redesign（已完成）
> 驱动来源：代码审查报告（共 10 项发现）

---

## 一、问题清单及修复优先级

| # | 严重性 | 文件 | 问题描述 | 修复策略 |
|---|--------|------|----------|---------|
| **CR-1** | 🔴 **严重** | `StreamEngine.kt:1381` | `keyboardEvent()` 使用 `0x01`/`0x00`（Android KeyEvent 编码）而非协议规定的 `KeyboardPacket.KEY_DOWN(0x03)`/`KEY_UP(0x04)`，导致按键事件损坏 | 替换行为编码常量 |
| **CR-2** | 🟡 **中** | `StreamEngine.kt:1381` | `keyboardEvent()` 修饰键始终为 `0x00`，快捷键组合无法正确发送 | 参考 `sendKeys` 方法，提取修饰键计算逻辑 |
| **CR-3** | 🟡 **中** | `DisplaySettingsPanel.kt` | VDD 虚拟显示器开关 + 外接显示器开关缺失（计划 DC-3 任务未完成） | 新增 `VddSection` Composable，参考旧 DisplaySection 实现 |
| **CR-4** | 🟢 低 | `SubPanelContainer.kt:775` / `DisplaySettingsPanel.kt:1286` | 两处 `private DetailScaffold` 实现有差异（Spacer、图标尺寸），导致导航栏视觉不一致 | 提取为共享组件，统一实现 |
| **CR-5** | 🟢 低 | `DisplaySettingsPanel.kt:920-944` | `FramePacingSelector` 的 `RadioButton.onClick` 与父级 `Row.clickable` 各执行一次 `writePreferences()`，双重写入 | RadioButton 设为空操作，由 Row 统一处理 |
| **CR-6** | 🟢 低 | `DisplaySettingsPanel.kt:872-875` | `VideoSwitches`：`writeDirectPrefs` 仅为拉伸视频调用；可旋转画面未设 `displaySettingsRestartPending` | 补全调用 |
| **CR-7** | 🟢 低 | `DisplaySettingsPanel.kt:171,353` | 自动收起计时器（5s）可能打断用户正在进行的操作 | 移除或增加交互重置 |
| **CR-8** | 🟢 低 | `BitrateUtils.kt:27` | `getPresetByKbps` 阈值 `kbps <= 1` 应改为 `kbps <= 0` | 修改边界值 |

### 不影响修复的条目

**#10（信息性）** — ABR 监听器注册逻辑正确，无需变更。

**#8（selectedRes 不随外部变化）** — 重启后值正确，当前生命周期内无外部修改路径，延迟处理。

---

## 二、技术方案

### CR-1：keyboardEvent 行为编码修复

**现状**：
```kotlin
// 当前错误代码
conn?.sendKeyboardInput(keyCode, if (buttonDown) 0x01 else 0x00, 0x00, 0x00)
```

**修复后**：
```kotlin
conn?.sendKeyboardInput(keyCode,
    if (buttonDown) com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN
    else com.limelight.nvstream.input.KeyboardPacket.KEY_UP,
    0x00, 0x00)
```

协议值确认：
- `KeyboardPacket.KEY_DOWN: Byte = 0x03`
- `KeyboardPacket.KEY_UP: Byte = 0x04`
- 当前使用 `0x01 = KeyEvent.ACTION_DOWN` / `0x00 = KeyEvent.ACTION_UP`，属于 Android 内部常量，与 Sunshine 协议不兼容

**验证方式**：物理键盘/蓝牙键盘输入场景下，按键事件能被 Sunshine 主机正确识别。

---

### CR-2：keyboardEvent 修饰键修复（用户确认：全部修复）

**方案**：采用路径 C — 在 `StreamEngine` 内部增加修饰键追踪状态。

**实现原理**：`EvdevListener` 接口不可更改（属于 `com.limelight.binding.input.evdev` 旧包）。但 `keyboardEvent()` 收到的是一条连续的按键事件流，修饰键（Shift/Ctrl/Alt/Meta）的按下/释放会作为独立事件发送。通过追踪这些修饰键的当前状态，可以在发送非修饰键按键时正确填充修饰键位掩码。

**实施代码**：

```kotlin
// 在 StreamEngine 中新增修饰键常量映射（参考已有 getKeyModifier 方法）
private fun getKeyModifierByEvdevCode(keyCode: Short): Byte = when (keyCode.toInt()) {
    // 从 KeyboardTranslator 常量映射
    0x2A, 0x36 -> KeyboardPacket.MODIFIER_SHIFT  // LSHIFT, RSHIFT
    0x1D        -> KeyboardPacket.MODIFIER_CTRL   // LCTRL
    0x38        -> KeyboardPacket.MODIFIER_ALT    // LALT
    0x5B        -> KeyboardPacket.MODIFIER_META   // LWIN (Meta)
    else        -> 0
}

// 修饰键追踪状态
private var currentModifiers: Byte = 0

// 在 keyboardEvent 中整合修饰键追踪 + 事件发送
override fun keyboardEvent(buttonDown: Boolean, keyCode: Short) {
    val modifierBit = getKeyModifierByEvdevCode(keyCode)
    if (modifierBit != 0.toByte()) {
        // 更新修饰键状态
        currentModifiers = if (buttonDown) {
            (currentModifiers.toInt() or modifierBit.toInt()).toByte()
        } else {
            (currentModifiers.toInt() and modifierBit.toInt().inv()).toByte()
        }
    }
    // 发送按键事件（含 CR-1 修复 + 修饰键）
    conn?.sendKeyboardInput(keyCode,
        if (buttonDown) {
            com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN
        } else {
            com.limelight.nvstream.input.KeyboardPacket.KEY_UP
        },
        currentModifiers, 0)
}
```

**注意**：此处 `getKeyModifierByEvdevCode` 使用 evdev scancode 常量来判断修饰键，与现有 `getKeyModifier`（使用 `KeyboardTranslator.VK_*` 常量）不同。这是因为 EvdevCaptureProvider 传入的是 evdev scancode，而 `sendKeys()`/`sendKeyboardInputWithModifier()` 传入的是 Android 键码。

**验证**：root 设备 + 外接键盘，在串流中按下 Shift+A → 主机应识别到大写 A；Ctrl+C → 触发复制。

---

### CR-3：VDD 虚拟显示器 + 外接显示器开关

**实现要点**：
- 在 `DisplaySettingsPanel.kt` 中新增 `VddSection` Composable
- 放置在"帧率与画面"分组的 HDR 之后、输出缓冲区之前（与原计划 DC-3 一致）
- VDD 开关：使用 `display_settings` SP 的 `vdd_enabled` 键（参考 `StreamEngine.initialize()` 第 238-241 行的读取逻辑）
- 外接显示器开关：使用 `prefConfig.useExternalDisplay`（已有字段）
- 外接显示器写入后调用 `pref.writePreferences(context)`
- VDD 切换后弹出 Toast 提示"需重启串流生效"

**参考代码模式**：`MtkSwitch` 的 `SettingSwitch` 模式。

**注意**：当前 `DisplaySettingsPanel.kt` 在 `app/src/main/java/.../stream/ui/display/` 子目录下。需确保 import 路径正确。

#### 布局位置

```
帧率与画面
├── 码率选择行
├── 帧率选择
├── 视频编码格式
├── 分辨率缩放
├── 输出缓冲区
├── 帧时序模式
├── HDR
├── **VDD虚拟显示器** ← 新增
├── **使用外接显示器** ← 新增
├── 3画面开关(拉伸/反转/旋转)
└── MTK专属选项
```

---

### CR-4：提取共享 DetailScaffold 组件

**现状对比**：

| 差异 | `SubPanelContainer.kt` | `DisplaySettingsPanel.kt` |
|------|----------------------|------------------------|
| IconButton 后的 Spacer | `Spacer(Modifier.width(4.dp))` | ❌ 缺失 |
| Icon 尺寸 | `Modifier.size(20.dp)` | ❌ 默认尺寸 |

**方案**：
1. 在 `SubPanelContainer.kt` 中将 `DetailScaffold` 改为 `internal`（或提取到公共文件）
2. `DisplaySettingsPanel.kt` 删除自己的 `private fun DetailScaffold`
3. 统一使用 `SubPanelContainer.kt` 中的版本（包含 Spacer + 图标尺寸）
4. 如果存在跨包可见性问题，提取到 `com.alexclin.moonlink.stream.ui` 包的一个单独文件

**注意**：`SubPanelContainer.kt` 和 `DisplaySettingsPanel.kt` 现在都在 `com.alexclin.moonlink.stream.ui` 包下（DisplaySettingsPanel 实际在 `com.alexclin.moonlink.stream.ui.display` 子包）。

由于包结构：
- `SubPanelContainer.kt` → `com.alexclin.moonlink.stream.ui`
- `DisplaySettingsPanel.kt` → `com.alexclin.moonlink.stream.ui.display`

如果 `DetailScaffold` 在 SubPanelContainer 中保持 `private`，DisplaySettingsPanel 无法访问。
因此需要 **提取到公共文件** 如 `DetailScaffold.kt` 在 `com.alexclin.moonlink.stream.ui` 包，设为 `@Composable fun DetailScaffold(...)`（非 private）。

---

### CR-5：FramePacingSelector 双重写入修复

**现状**（简化）：
```kotlin
Row(
    modifier = Modifier.clickable {
        expanded = false
        pref.framePacing = option.value
        pref.writePreferences(context)  // 写入 #1
    },
) {
    RadioButton(
        onClick = {
            pref.framePacing = option.value
            pref.writePreferences(context)  // 写入 #2
        },
    )
}
```

**修复**：RadioButton 的 `onClick` 设置为 `{}`（空操作），由 `Row.clickable` 统一处理。
```kotlin
RadioButton(
    selected = pref.framePacing == option.value,
    onClick = {},  // 由 Row.clickable 处理
)
```

> 参考：同一文件中 `VideoFormatSelector`（~640-660 行）的 RadioButton 已正确使用 `onClick = {}`。

---

### CR-6：VideoSwitches 一致性修复

| 开关 | 当前行为 | 修复操作 |
|------|---------|---------|
| 拉伸视频 | ✅ 有 writeDirectPrefs + restartPending | 不变 |
| 反转分辨率 | ❌ 无 writeDirectPrefs；✅ 有 restartPending | 增加 writeDirectPrefs |
| 可旋转画面 | ❌ 无 writeDirectPrefs；❌ 无 restartPending | 增加 writeDirectPrefs + restartPending |

**注意**：`writeDirectPrefs` 写的是 `checkbox_stretch_video` 键。反转分辨率和可旋转画面是否需要写入旧 SP 键？检查旧 PreferenceScreen 中是否有 `checkbox_reverse_resolution` / `checkbox_rotable_screen` 键。如果旧设置 Activity 依赖这些键，则需要在 `writeDirectPrefs` 中补充。

---

### CR-7：自动收起计时器修复

**现状**：两个 `LaunchedEffect(expanded)` 在展开 5 秒后自动关闭面板。
- `FpsSelector`（第 171 行）
- ABR 模式选择（第 353 行）

**风险**：用户展开后 4.5 秒开始操作，0.5 秒后面板关闭，点击可能落在错误的 Composable 组合状态上。

**修复方案**：

**选项 A**：完全移除自动收起（推荐 — 在触摸屏高频操作场景下利大于弊）

**选项 B**：增加交互重置：
```kotlin
val scope = rememberCoroutineScope()
var lastInteraction by remember { mutableLongStateOf(currentTimeMillis()) }

// RadioButton onClick 中重置
lastInteraction = System.currentTimeMillis()

// 或使用 SnapshotFlow 检测状态变化
LaunchedEffect(expanded) {
    if (expanded) {
        while (true) {
            delay(5000)
            if (System.currentTimeMillis() - lastInteraction >= 5000) {
                expanded = false
                break
            }
        }
    }
}
```

**推荐选项 A** — 简化代码，避免竞态条件。

---

### CR-8：BitrateUtils 阈值修复

```kotlin
// 当前
kbps <= 1 -> BitratePreset.AUTO

// 修复
kbps <= 0 -> BitratePreset.AUTO
```

`BITRATE_AUTO = 0`，阈值应为 `kbps <= 0`。当前 `kbps <= 1` 虽然在实际场景中不会命中（码率值不会等于 1），但属于逻辑不严谨。

---

## 三、影响范围

| 文件 | 变更类型 | 涉及任务 |
|------|---------|---------|
| `StreamEngine.kt` | 修改 | CR-1, CR-2 |
| `DisplaySettingsPanel.kt` (display/) | 修改 | CR-3, CR-5, CR-6, CR-7 |
| `SubPanelContainer.kt` | 修改 | CR-4 |
| **新建** `DetailScaffold.kt` (ui/) | 新建 | CR-4 |
| `BitrateUtils.kt` (display/) | 修改 | CR-8 |

---

## 四、验证清单

| # | 验证项 | 关联任务 | 预期结果 |
|---|--------|---------|---------|
| 1 | 物理键盘按键输入 | CR-1 | Sunshine 主机正确识别 KEY_DOWN/KEY_UP |
| 2 | 修饰键组合（如 Ctrl+C） | CR-2 | 修饰键位正确传递 |
| 3 | VDD 开关切换 | CR-3 | `vdd_enabled` 写入 display_settings SP，Toast 提示重启生效 |
| 4 | 外接显示器开关切换 | CR-3 | `prefConfig.useExternalDisplay` 写入，preferences 持久化 |
| 5 | 所有详情页导航栏视觉一致 | CR-4 | 返回按钮间距、图标大小统一 |
| 6 | FramePacing 选择 | CR-5 | 无双重写入，功能正常 |
| 7 | 可旋转画面切换 | CR-6 | 显示重启待处理标记 |
| 8 | 自动收起不再打断操作 | CR-7 | 展开后无自动关闭行为（或可被交互重置） |
| 9 | 码率预设映射 | CR-8 | `getPresetByKbps(0)` 返回 AUTO |

---

## 五、风险与注意事项

1. **CR-2 修饰键修复**：需要确认 `EvdevListener` 接口的调用方是否持有修饰键信息。如果无法获取，则保持当前行为并记录限制。
2. **CR-3 VDD 持久化键**：`vdd_enabled` 是 `display_settings` SP 中的键，非 `prefConfig` 字段。需确保读写使用正确的 SharedPreferences 实例。
3. **CR-4 包可见性**：`SubPanelContainer.kt` 在 `ui` 包，`DisplaySettingsPanel.kt` 在 `ui.display` 子包。共享组件需放在两个包都能引用的位置。
4. **CR-6 writeDirectPrefs 扩展**：需确认旧 PreferenceScreen 中 `checkbox_reverse_resolution` / `checkbox_rotable_screen` 键是否存在。如果不存在，则不需要补充 writeDirectPrefs。
5. **回归**：修复后需确保所有详情页（体感助手、更多、主机设置、外设、显示设置）导航栏正常。
