# Handoff: code_review_fixes（可独立执行版）

> 本文件包含 build agent 执行此计划所需的所有信息。
> **不依赖当前会话上下文** — 所有文件路径、API、代码模式均已包含。
> 版本：1.0

---

## 项目环境

- **项目**: Android Kotlin + Jetpack Compose
- **新代码包**: `com.alexclin.moonlink.stream.*`
- **旧代码包**: `com.limelight.*`（只读引用，不做修改）
- **工作目录**: `app/src/main/java/com/alexclin/moonlink/stream/`

---

## 关键约束

1. **新建文件** — 允许新建 `DetailScaffold.kt` 共享组件文件
2. **不修改 `com.limelight.*` 包** — 只引用其公开 API
3. **修复粒度** — 每个修复应为最小的、可独立验证的变更

---

## 执行批次

### Batch A：严重修复 — keyboardEvent（CR-1, CR-2）

**文件**: `app/src/main/java/com/alexclin/moonlink/stream/engine/StreamEngine.kt`

#### CR-1：替换行为编码（L1381）

```kotlin
// 当前（错误）
conn?.sendKeyboardInput(keyCode, if (buttonDown) 0x01 else 0x00, 0x00, 0x00)

// 修复后
conn?.sendKeyboardInput(keyCode,
    if (buttonDown) com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN
    else com.limelight.nvstream.input.KeyboardPacket.KEY_UP,
    0x00, 0x00)
```

**关键**：`KeyboardPacket.KEY_DOWN = 0x03`，`KeyboardPacket.KEY_UP = 0x04`。
**参考**：同一文件中 `sendKeys()` 方法（~L960-965）已正确使用这些常量。

#### CR-2：内部修饰键追踪

与 CR-1 在同一位置，在同一函数中追加修饰键追踪逻辑：

```kotlin
// StreamEngine 中新增
private var currentModifiers: Byte = 0

private fun getKeyModifierByEvdevCode(keyCode: Short): Byte = when (keyCode.toInt()) {
    0x2A, 0x36 -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_SHIFT
    0x1D        -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_CTRL
    0x38        -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_ALT
    0x5B        -> com.limelight.nvstream.input.KeyboardPacket.MODIFIER_META
    else        -> 0
}

// 修改后完整的 keyboardEvent
override fun keyboardEvent(buttonDown: Boolean, keyCode: Short) {
    val modifierBit = getKeyModifierByEvdevCode(keyCode)
    if (modifierBit != 0.toByte()) {
        currentModifiers = if (buttonDown) {
            (currentModifiers.toInt() or modifierBit.toInt()).toByte()
        } else {
            (currentModifiers.toInt() and modifierBit.toInt().inv()).toByte()
        }
    }
    conn?.sendKeyboardInput(keyCode,
        if (buttonDown) com.limelight.nvstream.input.KeyboardPacket.KEY_DOWN
        else com.limelight.nvstream.input.KeyboardPacket.KEY_UP,
        currentModifiers, 0)
}
```

**实施步骤**：在 `StreamEngine` 中新增：
1. `getKeyModifierByEvdevCode(keyCode: Short): Byte` — 将 evdev scancode 映射为修饰键位掩码
2. `currentModifiers: Byte` 状态变量 — 追踪当前按住的所有修饰键
3. 在 `keyboardEvent()` 中：先检查 `keyCode` 是否是修饰键 → 更新 `currentModifiers` → 发送事件时传入 `currentModifiers`

**修饰键映射表**（evdev scancode → KeyboardPacket modifier bit）：

| 修饰键 | evdev scancode | Modifier Bit |
|--------|---------------|-------------|
| Shift (左/右) | `0x2A` / `0x36` | `MODIFIER_SHIFT` |
| Ctrl (左) | `0x1D` | `MODIFIER_CTRL` |
| Alt (左) | `0x38` | `MODIFIER_ALT` |
| Win/Meta (左) | `0x5B` | `MODIFIER_META` |

**注意**：这些是 Linux evdev scancode 常量，与 `getKeyModifier()` 中的 `KeyboardTranslator.VK_*` 常量不同。两个方法服务于不同的调用路径。

---

### Batch B：功能补齐 — VDD/外接显示器开关（CR-3）

**文件**: `app/src/main/java/com/alexclin/moonlink/stream/ui/display/DisplaySettingsPanel.kt`

在 `HdrSection` 之后、`VideoSwitches` 之前添加 `VddSection`：

**Composable 实现**：

```kotlin
@Composable
private fun VddSection(engine: StreamEngine) {
    val context = LocalContext.current
    val pref = engine.prefConfig
    val displayPrefs = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
    var useVdd by remember { mutableStateOf(displayPrefs.getBoolean("vdd_enabled", false)) }
    var useExternal by remember { mutableStateOf(pref.useExternalDisplay) }

    Column {
        SettingSwitch("VDD虚拟显示器", useVdd) {
            useVdd = it
            displayPrefs.edit().putBoolean("vdd_enabled", it).apply()
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

**在 `DisplaySettingsPanel` 主体中调用**（HdrSection 之后）：
```kotlin
// DB-3: HDR
item { HdrSection(engine) }

// CR-3: VDD + 外接
item { VddSection(engine) }

// DC-2: 画面设置开关组
item { VideoSwitches(engine) }
```

**验证**：VDD 开关写入 `display_settings` SP 的 `vdd_enabled` 键；外接开关写入 `prefConfig.useExternalDisplay`。

---

### Batch C：重构 — 共享 DetailScaffold（CR-4）

#### C1：新建共享组件文件

**路径**: `app/src/main/java/com/alexclin/moonlink/stream/ui/DetailScaffold.kt`

```kotlin
package com.alexclin.moonlink.stream.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DetailScaffold(
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
```

**注意**：
- 使用 `Modifier.size(20.dp)` 统一图标大小（和 `SubPanelContainer.kt` 的现有版本对齐）
- 保留 `Spacer(Modifier.width(4.dp))` 在 Icon 和 Title 之间
- 该函数为 `public`（非 `private`），供两个包引用

#### C2：修改 `SubPanelContainer.kt`

- **删除** `private fun DetailScaffold(...)`（~L775-804）
- **添加 import**: `import com.alexclin.moonlink.stream.ui.DetailScaffold`
- 确认所有调用方（体感助手 L572、更多 L701、主机设置 L819、外设 L868、外设子页 L880）仍然正常工作

#### C3：修改 `DisplaySettingsPanel.kt`

- **删除** `private fun DetailScaffold(...)`（~L1286-1301）
- **添加 import**: `import com.alexclin.moonlink.stream.ui.DetailScaffold`

---

### Batch D：低优修复（CR-5, CR-6, CR-7, CR-8）

#### CR-5：FramePacingSelector 双重写入

**文件**: `DisplaySettingsPanel.kt` ~L933-941

将 `RadioButton` 的 `onClick` 改为空操作：

```kotlin
RadioButton(
    selected = pref.framePacing == option.value,
    onClick = {},  // 由 Row.clickable 统一处理
)
```

**参考**：同一文件中 `VideoFormatSelector`（~L700-702）已正确使用 `onClick = {}`。

#### CR-6：VideoSwitches 一致性

**文件**: `DisplaySettingsPanel.kt` ~L872-874

```kotlin
// 当前
SettingSwitch("拉伸视频", stretch) { stretch = it; pref.stretchVideo = it; pref.writePreferences(context); writeDirectPrefs(pref, context); engine.displaySettingsRestartPending = true }
SettingSwitch("反转分辨率", reverse) { reverse = it; pref.reverseResolution = it; pref.writePreferences(context); engine.displaySettingsRestartPending = true }
SettingSwitch("可旋转画面", rotable) { rotable = it; pref.rotableScreen = it; pref.writePreferences(context) }

// 修复后
SettingSwitch("拉伸视频", stretch) { stretch = it; pref.stretchVideo = it; pref.writePreferences(context); writeDirectPrefs(pref, context); engine.displaySettingsRestartPending = true }
SettingSwitch("反转分辨率", reverse) { reverse = it; pref.reverseResolution = it; pref.writePreferences(context); writeDirectPrefs(pref, context); engine.displaySettingsRestartPending = true }
SettingSwitch("可旋转画面", rotable) { rotable = it; pref.rotableScreen = it; pref.writePreferences(context); writeDirectPrefs(pref, context); engine.displaySettingsRestartPending = true }
```

**同时检查 `writeDirectPrefs` 函数**（~L505-515），确认是否需要添加 `checkbox_reverse_resolution` / `checkbox_rotable_screen` 键。如果旧 PreferenceScreen 中存在这些键，则补充写入。

#### CR-7：自动收起计时器

**文件**: `DisplaySettingsPanel.kt`

有两处 `LaunchedEffect(expanded)` 自动关闭：
1. `FpsSelector` ~L171-176
2. ABR 模式选择 ~L353-358

**推荐方案**：完全移除这两个 `LaunchedEffect` 块。

**验证**：展开帧率选择 / ABR 模式后不会自动关闭。

#### CR-8：BitrateUtils 阈值

**文件**: `app/src/main/java/com/alexclin/moonlink/stream/ui/display/BitrateUtils.kt` L27

```kotlin
// 当前
kbps <= 1 -> BitratePreset.AUTO

// 修复后
kbps <= 0 -> BitratePreset.AUTO
```

---

## 执行顺序

```
Batch A (CR-1, CR-2) → 严重修复，优先执行
  ↓
Batch B (CR-3) → 功能补齐，独立可验
  ↓
Batch C (CR-4) → 重构，涉及跨文件变更
  ↓
Batch D (CR-5, CR-6, CR-7, CR-8) → 低优修复，无依赖
```

---

## 验证

每个 batch 完成后，项目应能正常编译。组合后执行全量验证：

1. 编译检查：`./gradlew assembleDebug`
2. 键盘输入检查：物理键盘/蓝牙键盘按键事件
3. 显示设置面板检查：VDD/外接开关显示 + 导航栏视觉一致
4. 功能回归：帧率选择、码率选择、帧时序模式、3画面开关

---

## 需新建的文件

1. `app/src/main/java/com/alexclin/moonlink/stream/ui/DetailScaffold.kt` — 共享导航栏组件

## 需修改的文件

1. `app/src/main/java/com/alexclin/moonlink/stream/engine/StreamEngine.kt` — CR-1, CR-2
2. `app/src/main/java/com/alexclin/moonlink/stream/ui/display/DisplaySettingsPanel.kt` — CR-3, CR-5, CR-6, CR-7
3. `app/src/main/java/com/alexclin/moonlink/stream/ui/SubPanelContainer.kt` — CR-4（删除私有函数）
4. `app/src/main/java/com/alexclin/moonlink/stream/ui/display/BitrateUtils.kt` — CR-8
