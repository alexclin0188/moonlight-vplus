# 按键映射方案 — 详细执行计划

> 创建日期：2026-06-18 | 状态：prepare | 版本：1.0
> 参考设计：`MoonLink_Detailed_Design.md` §5.3.3（启用按键映射）、§5.3.3.1（方案选择）、§5.3.3.2（编辑器）、§6.7（按键配置管理）
> 代码现状：`plan/active/stream_ui_completion/plan.md` 反映了 streamplan 当前完成度

---

## 一、代码现状审计

### 1.1 已完成（streamplan 交付）

| 组件 | 状态 | 说明 |
|------|------|------|
| StreamActivity + StreamEngine | ✅ | 串流视频音频正常 |
| 悬浮按钮 + 竖向窄条面板 | ✅ | 交互完整 |
| 操作子面板框架 + 列表↔详情导航 | ✅ | AnimatedContent 滑动切换 |
| 快捷操作行 QuickActionRow | ✅ | 固定+可配+编辑+拖拽 |
| 键盘子面板 KeyboardSubPanel | ✅ | 4 标签页完整 |
| 触控模式 TouchModeSection | ✅ | 4 Chip + 子项联动 |
| 平移缩放 PanZoomSection | ✅ | Switch + 逻辑 |
| 显示详情 DisplaySection | ✅ | 帧率/码率/显示器/HDR/画面 |
| 主机设置 HostSettingsSection | ✅ | 7 开关 |
| 体感助手 GyroSection | ✅ | 灵敏度/反转/激活键 |
| 更多 MoreSection | ✅ | 性能图层 + 自动隐藏 |
| 返回键 + 防抖 | ✅ | 多级逻辑 |
| **KeyMappingSection（空壳）** | 🔶 | Switch 存在但未连 engine，3 个子入口未接入真实功能 |

### 1.2 待实现（本计划范围）

| 组件 | 设计文档 | 说明 |
|------|----------|------|
| Preference 改名 | §5.3.3 | 新 key `checkbox_enable_key_mapping` |
| KeyMappingSection 连接 | §5.3.3 | Switch 连 engine，开启自动切触控板 |
| 全屏方案选择页面 | §5.3.3.1 | 搜索/导入/新建/列表/虚拟手柄方案 |
| 全屏编辑器 | §5.3.3.2 | Compose 壳 + 旧 ElementController 嵌入 |
| 其它设置 | §5.3.3.3 | 触控/灵敏度/震动/透明度/颜色等 |
| Settings Tab：按键配置管理 | §6.7 | 4 个入口（导入/导出 .mdat + .mkmp） |
| .mkmp 格式定义 | §6.7.3/6.7.4 | 简化独立 JSON 格式 |
| 虚拟手柄方案合并 | §5.3.3.1 | OSC 配置并入方案体系 |
| 旧代码清理 | §6.9 | 删除 `category_crown_features` |

---

## 二、用户决策记录（已确认）

| 问题 | 决策 |
|------|------|
| 改造范围 | **路径 B**：对齐 streamplan 大重构，新模式 → StreamActivity + Compose |
| 方案页面实现方式 | **新的 Compose 全屏页面**，编辑器核心复用旧 ElementController |
| 设置页面位置 | **新 MoonLink 设置 Tab**（Compose 页面） |
| 改名策略 | **彻底改名**：新 Pref key + 全部字符串资源更新 |
| 虚拟手柄方案归属 | **合并为按键映射方案**，作为内置方案 |
| .mkmp 格式 | **简化独立结构**（不同 .mdat） |
| 时序 | **独立并行工作流** |

---

## 三、Batch A：基础重构（Preference + 命名空间）

> 无外部依赖，可立即开始。后序 Batch 都依赖此 Batch。

### T-01：新增 Preference key

**文件**：`com.limelight.preferences.PreferenceConfiguration.kt`

新增字段和 Pref key：

```kotlin
// 新增常量（替代旧的 ONSCREEN_KEYBOARD_PREF_STRING）
const val KEY_MAPPING_ENABLED_PREF_STRING = "checkbox_enable_key_mapping"

// 新增字段（替代 onscreenKeyboard，旧字段保留但不再使用）
var keyMappingEnabled = false

// readPreferences() 中读取新 key
keyMappingEnabled = prefs.getBoolean(KEY_MAPPING_ENABLED_PREF_STRING, false)

// writePreferences() 写入新 key
editor.putBoolean(KEY_MAPPING_ENABLED_PREF_STRING, keyMappingEnabled)
```

> **设计意图**：旧 `checkbox_show_onscreen_keyboard`（`ONSCREEN_KEYBOARD_PREF_STRING`）和旧 `onscreenKeyboard` 字段**不再维护**。旧 Game.kt 入口后续将被废弃，无需做向后兼容。新代码只关心新 key。

### T-02：StreamEngine 适配

**文件**：`com.alexclin.moonlink.stream.engine.StreamEngine.kt`

```kotlin
// isCrownFeatureEnabled 读取新字段
val isCrownFeatureEnabled: Boolean
    get() = prefConfig.keyMappingEnabled

fun setCrownFeatureEnabled(enabled: Boolean) {
    prefConfig.keyMappingEnabled = enabled
    prefConfig.writePreferences(activity)
    if (enabled) {
        if (controllerManager != null) {
            controllerManager?.show()
        } else {
            initializeControllerManager()
        }
    } else {
        controllerManager?.hide()
    }
}
```

> 旧 `prefConfig.onscreenKeyboard` 的 setter 内部委托到此逻辑，防止旧代码路径出现不一致。

### T-03：字符串资源全面改名

**文件**：`res/values/strings.xml`, `res/values-zh-rCN/strings.xml`

搜索所有包含 `王冠`、`crown`、`checkbox_show_onscreen_keyboard` 的字符串资源并改名：

| 旧文案 | 新文案 |
|--------|--------|
| 王冠的超级功能 | 启用按键映射 |
| 王冠功能 | 按键映射 |
| 王冠配置 | 按键映射方案配置 |
| checkbox_show_onscreen_keyboard | checkbox_enable_key_mapping |

**原则**：
- 现有字符串 ID 可直接更新（Compose 中引用资源 ID，改后引用自动更新）
- 旧字符串资源**不删除**，标记 `tools:ignore="UnusedResources"` 
- 仅改 UI 面向用户的字符串资源，类名/日志 tag 不改（旧入口废弃后一并清理）

---

## 四、Batch B：KeyMappingSection + 方案选择全屏页面

> 前置依赖：Batch A，streamplan Phase 2（SubPanelContainer 存在）
> 产出：按键映射开关真实可用，方案选择页面可浏览和切换

### T-04：KeyMappingSection 连接真实逻辑

**文件**：`SubPanelContainer.kt` 内联的 `KeyMappingSection`

```kotlin
@Composable
fun KeyMappingSection(engine: StreamEngine) {
    var enabled by remember { mutableStateOf(engine.isCrownFeatureEnabled) }
    
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // 顶层开关
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TextFields, contentDescription = null,
                 modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("启用按键映射", style = MaterialTheme.typography.bodyMedium,
                 modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { value ->
                    enabled = value
                    engine.setCrownFeatureEnabled(value)
                    if (value) {
                        // 自动切换触控板模式
                        engine.prefConfig.enableEnhancedTouch = false
                        engine.prefConfig.enableNativeMousePointer = false
                        engine.prefConfig.touchscreenTrackpad = true
                        engine.prefConfig.writePreferences(engine.activity)
                    }
                }
            )
        }
        
        // 开启后：3 个子入口
        AnimatedVisibility(visible = enabled) {
            Column(modifier = Modifier.padding(start = 28.dp)) {
                // 1. 切换按键映射方案 → 全屏页面
                TextButton(onClick = { /* 通知外部打开全屏方案选择页 */ },
                           modifier = Modifier.fillMaxWidth()) {
                    Text("切换按键映射方案 >")
                }
                // 2. 编辑当前方案 → 全屏编辑器
                TextButton(onClick = { /* 通知外部打开全屏编辑器 */ },
                           modifier = Modifier.fillMaxWidth()) {
                    Text("编辑当前方案 >")
                }
                // 3. 其它设置 → 展开
                ExpandableOtherSettings(engine = engine)
            }
        }
    }
}
```

**关键改动**：
- 通知外部打开全屏页：通过回调传递到 `StreamOverlay` → `StreamActivity`，用 `fullScreenPage` 状态控制
- 开启按键映射时自动切触控板模式（Toast 提示）

### T-05：全屏方案选择页面 (Compose)

**文件**：`com.alexclin.moonlink.stream.ui/panels/KeyMappingSchemeSelector.kt`

```
┌────────────────────────────────┐
│ ← 返回   按键映射方案           │  ← 顶部标题栏
│ ┌─────────────────────────┐   │
│ │ 🔍 搜索方案名           │   │  ← 搜索栏
│ ├─────────────────────────┤   │
│ │ [导入 .mkmp] [+ 新建]   │   │  ← 操作按钮行
│ ├─────────────────────────┤   │
│ │ ○ 虚拟手柄方案（默认）    │   │  ← 内置方案，始终存在
│ │   ├ 启用震动            │   │     ← 选中时展开配置
│ │   ├ 透明度 ████░░ 50%   │   │
│ │   ├ 只显示 L3 和 R3     │   │
│ │   ├ 显示 Guide 按钮     │   │
│ │   └ 竖屏模式半高         │   │
│ ├─────────────────────────┤   │
│ │ ● 我的射击方案          │   │  ← 当前选中（高亮）
│ │   [重命名] [导出] [删除] │   │     ← 长按或点击 ... 显示
│ ├─────────────────────────┤   │
│ │ ○ 我的MOBA方案          │   │
│ │   [重命名] [导出] [删除] │   │
│ └─────────────────────────┘   │
│                               │
└────────────────────────────────┘
```

**数据流**：
```kotlin
// 1. 加载方案列表
val dbHelper = SuperConfigDatabaseHelper(context)
val configIds = dbHelper.queryAllConfigIds()  // Long[]
val configNames = configIds.map { dbHelper.queryConfigAttribute(it, "config_name") }
val currentConfigId = prefs.getLong("current_config_id", 0L)

// 2. 选择方案
fun onSelectScheme(configId: Long) {
    prefs.edit { putLong("current_config_id", configId) }
    engine.controllerManager?.pageConfigController?.initConfig()
    engine.controllerManager?.refreshLayout()
}

// 3. 新建方案
fun onCreateScheme(name: String) {
    val configId = System.currentTimeMillis()
    val values = ContentValues().apply {
        put("config_name", name)
        // ... 默认值
    }
    dbHelper.insertConfig(configId, values)
    onSelectScheme(configId)
}

// 4. 删除方案（default 不可删）
fun onDeleteScheme(configId: Long) {
    dbHelper.deleteConfig(configId)
    if (currentConfigId == configId) {
        // 回退到 default (0L) 或下一个可用方案
    }
}

// 5. 导入 .mkmp
fun onImportMkmp(uri: Uri) {
    // 解析 → 弹出命名确认 → 写入数据库
}
```

### T-06：方案 CRUD 逻辑

**文件**：与 T-05 同文件

- **新建方案 Dialog**：TextInput（1-10 字符）→ 确认 → `System.currentTimeMillis()` 生成 config_id → 写入数据库
- **重命名 Dialog**：当前方案名预填 → 修改 → `updateConfig()`
- **删除确认 Dialog**：不可删除 default（config_id = 0L）
- **单选切换**：选中项写入 `current_config_id`
- **方案变更时**：`engine.controllerManager?.refreshLayout()` 重新加载元素

### T-07：虚拟手柄方案内置

在方案列表最顶部固定显示"虚拟手柄方案"（config_id = 0L 即 default 方案）。

选中时展示其配置项：

| 配置项 | 控件 | Preference Key |
|--------|------|----------------|
| 启用震动 | Switch | `checkbox_vibrate_osc` |
| 透明度 | Slider 0-100% | `seekbar_osc_opacity` |
| 只显示 L3 和 R3 | Switch | `checkbox_only_show_L3R3` |
| 显示 Guide 按钮 | Switch | `checkbox_show_guide_button` |
| 竖屏模式半高 | Switch | `checkbox_half_height_osc_portrait` |
| 重置屏幕控制按钮布局 | Button | (调用 VirtualController 重置方法) |

**实现注意**：
- 这些 Preference key 目前属于旧 `VirtualController`（`com.limelight.binding.input.virtual_controller`）
- 新方案页面**直接读写**同一个 SharedPreferences key，两边互通
- 当用户切换到其他自定义方案时，虚拟手柄配置项隐藏（或变灰）

---

## 五、Batch C：全屏编辑器

> 前置依赖：Batch B（方案选择页面存在）
> 产出：可进入全屏编辑器，编辑并保存方案

### T-08：全屏编辑器 Compose 壳

**文件**：`com.alexclin.moonlink.stream.ui/panels/KeyMappingEditor.kt`

```
┌────────────────────────────────────┐
│ [← 保存退出]  方案名称 ✎  [添加] [组合键] │  ← 顶部工具栏
│                              [≡ 网格]│     （元素选中时展开属性编辑区）
├────────────────────────────────────┤
│                                    │
│                                    │
│         [旧 ElementController]      │  ← AndroidView 嵌入
│         元素编辑画布               │     element_touch_view
│                                    │
│                                    │
│  编辑网格宽度 ████████░░ 8 列      │  ← 底部工具栏（未选中时）
└────────────────────────────────────┘
```

```kotlin
@Composable
fun KeyMappingEditor(
    engine: StreamEngine,
    onClose: () -> Unit
) {
    var selectedElement by remember { mutableStateOf<Element?>(null) }
    var gridWidth by remember { mutableIntStateOf(8) }
    var isPanelOnTop by remember { mutableStateOf(true) }

    // 进入编辑模式
    LaunchedEffect(Unit) {
        engine.controllerManager?.elementController?.changeMode(ElementController.Mode.Edit)
        engine.controllerManager?.elementController?.open()
    }

    // 退出时保存并关闭
    fun exitEditor() {
        engine.controllerManager?.elementController?.close()
        engine.controllerManager?.elementController?.changeMode(ElementController.Mode.Normal)
        onClose()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏（根据 isPanelOnTop 决定位置） ──
            EditorToolbar(
                schemeName = "...",
                onNameChange = { /* update config_name */ },
                onAddElement = { /* 弹出添加元素选择器 */ },
                onAddComboKey = { /* 弹出组合键编辑 */ },
                onExit = { exitEditor() },
                showPropertyPanel = selectedElement != null,
                propertyPanel = { elementPropertyEditor(selectedElement) },
                modifier = Modifier.align(if (isPanelOnTop) Alignment.Top else Alignment.Bottom)
            )

            // ── 旧元素编辑器嵌入 ──
            AndroidView(
                factory = { ctx ->
                    // 获取 element_touch_view 并 attach 到 Compose
                    engine.controllerManager?.elementController?.view ?: View(ctx)
                },
                modifier = Modifier.weight(1f)
            )
        }

        // ── 底栏：网格宽度滑块 ──
        if (selectedElement == null) {
            Slider(
                value = gridWidth.toFloat(),
                onValueChange = { gridWidth = it.roundToInt() },
                valueRange = 1f..10f,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}
```

### T-09：集成旧 ElementController

**关键挑战**：旧 `ElementController` 是一个 Java 类，直接操作 View 层次。在 Compose 中嵌入需要：

1. **获取旧 View**：`elementController.view` 返回 `View`（即 `layer_2_element.xml` 的根布局）
2. **包裹为 AndroidView**：Compose 中通过 `AndroidView` 嵌入
3. **事件桥接**：Compose 选中元素 → 通知旧系统选中 → 属性编辑面板展示元素详情

```kotlin
// 监听元素选中事件
class ElementSelectionListener : ElementController.ElementSelectionCallback {
    override fun onElementSelected(element: Element?) {
        selectedElement = element
    }
}

// 在 LaunchedEffect 中注册
DisposableEffect(Unit) {
    engine.controllerManager?.elementController?.setSelectionCallback(listener)
    onDispose {
        engine.controllerManager?.elementController?.setSelectionCallback(null)
    }
}
```

**元素编辑属性面板**（Compose 实现，替代旧 `page_xxx_clean.xml`）：

选中元素时，根据 `element.type` 展示对应的属性编辑器：

| 元素类型 | 可编辑属性 |
|----------|-----------|
| DigitalCommonButton | 按键映射(选择键值/组合键)、按钮文字、文字颜色、背景颜色、大小(宽/高) |
| DigitalSwitchButton | 同上 + ON 态文字/颜色 |
| AnalogStick | 摇杆映射(左/右)、死区范围、外圈大小、是否点击穿透 |
| DigitalPad | 方向数量(4/8)、按键映射、每个方向的键值 |
| 所有元素 | 位置 X/Y (拖拽调整)、宽/高、透明度 |

### T-10：编辑器增强

**网格宽度滑块**：SeekBar 1-10 列，实时调整编辑网格参考线密度
**工具栏自适应**：元素拖到上半屏时工具栏移到底部，反之亦然
**双指滑动**：左右滑动切换编辑面板的吸附位置（左侧/右侧）

---

## 六、Batch D：Settings Tab 按键配置管理

> 前置依赖：Batch A（Preference key 改名）
> 产出：MoonLink 设置中可管理按键映射方案的导入/导出

### T-11：设置页面入口

**文件**：`com.alexclin.moonlink.settings.tabs` 包下新建 `KeyMappingSettingsPage.kt`

在 MoonLink 底部 Tab「设置」的列表中新增入口项"按键配置管理"，点击进入 Compose 页面。

### T-12：导出按键配置 (.mdat)

复用旧 `export_super_config` 逻辑（`SuperConfigDatabaseHelper.exportConfigToJson()`）：

```kotlin
@Composable
fun ExportConfigSection(dbHelper: SuperConfigDatabaseHelper) {
    // 1. 加载所有方案
    val schemes = loadAllSchemes(dbHelper)
    // 2. 展开下拉列表/单选列表选择方案
    // 3. 点击"导出" → SAF ACTION_CREATE_DOCUMENT
    // 4. 写入 JSON（复用旧 export 格式）
}
```

### T-13：导入按键配置 (.mdat)

```kotlin
@Composable
fun ImportConfigSection(dbHelper: SuperConfigDatabaseHelper) {
    // 1. 点击"导入" → SAF ACTION_OPEN_DOCUMENT（选 .mdat）
    // 2. 解析 + MD5 校验 + 版本升级（复用旧逻辑）
    // 3. 弹出合并确认弹窗：
    //    a. 新建方案 → 生成新 config_id 完整写入
    //    b. 合并到所选方案 → 仅 element 合并（丢弃 config）
}
```

### T-14：导出按键方案 (.mkmp)

```kotlin
@Composable
fun ExportSchemeSection(dbHelper: SuperConfigDatabaseHelper) {
    // 1. 方案选择弹窗
    // 2. 确认 → SAF ACTION_CREATE_DOCUMENT → 文件名建议 <方案名>.mkmp
    // 3. 写入简化 JSON 格式（不含 MD5/版本号/内部元数据）
}
```

**.mkmp JSON 格式定义**：
```json
{
  "format": "mkmp",
  "version": 1,
  "name": "我的射击方案",
  "created": "2026-06-18",
  "config": {
    "touch_enable": true,
    "touch_mode": false,
    "touch_sense": 100,
    "mouse_wheel_speed": 100,
    "game_vibrator": false,
    "button_vibrator": true,
    "enhanced_touch": false,
    "global_opacity": 80,
    "global_border_color": "#FFFFFF",
    "global_text_color": "#FFFFFF"
  },
  "elements": [
    {
      "type": 0,
      "x": 100.0,
      "y": 200.0,
      "width": 60.0,
      "height": 60.0,
      "borderWidth": 2.0,
      "borderColor": "#00B4D8",
      "bgColor": "#1C1B2E",
      "text": "E",
      "textColor": "#E6E1E5",
      "textSize": 14.0,
      "keyValue": "null",
      "keyValueType": 0,
      "touchEnable": true
    }
  ]
}
```

---

## 七、Batch E：格式定义 + 收尾

> 前置依赖：Batch C-D
> 产出：完整功能闭环

### T-15：导入按键方案 (.mkmp)

```kotlin
@Composable
fun ImportSchemeSection(dbHelper: SuperConfigDatabaseHelper) {
    // 1. SAF ACTION_OPEN_DOCUMENT（选 .mkmp）
    // 2. 解析简化 JSON（不需 MD5 校验）
    // 3. 导入确认窗口：
    //    a. 方案名称输入框（默认 = 文件中的 name）
    //    b. 覆盖选择：单选列表（所有已有方案 + "不覆盖，作为新方案导入"）
    //    c. "不覆盖" → 生成新 config_id 写入
    //    d. 选中已有方案 → 二次确认 → 删除旧数据 → 以目标 config_id 写入
    // 4. Toast "导入方案成功" / "覆盖方案成功"
}
```

### T-16：虚拟手柄方案配置互通

确保旧 `VirtualController` 的 Pref key 与新方案页面读写相同 key：

```kotlin
// PreferenceConfiguration 中确保字段映射一致
var vibrateOsc: Boolean
var oscOpacity: Int
var onlyL3R3: Boolean
var showGuideButton: Boolean
var halfHeightOscPortrait: Boolean

// 旧 VirtualController 读取同一个 Pref key
// 新方案页面通过 engine.prefConfig 读写
// 两边自动互通
```

### T-17：旧代码清理

**待删除/修改的旧文件**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `StreamSettings.kt` 中 `category_crown_features` | 删除分类及全部子条目 | 已迁移到新 Settings Tab |
| `preferences.xml` 中王冠相关 Preference | 移除 | 入口已迁移 |
| `strings.xml` 王冠字符串 | 移除或 deprecated | 由新字符串替代 |

**保留不变**（因为旧 Game.kt 仍需使用）：
- `ControllerManager.kt` 及其所有子类
- `SuperConfigDatabaseHelper.java`
- `ElementController.java`
- `VirtualController` 目录

### T-18：全量验证

| 编号 | 验证项 | 预期 |
|------|--------|------|
| V01 | 新 Pref key 读写 | `checkbox_enable_key_mapping` 写入，旧 `checkbox_show_onscreen_keyboard` 仍可读取 |
| V02 | 旧 Game.kt 入口 | 串流正常，旧 Crown 功能不变（通过旧 key 控制） |
| V03 | KeyMappingSection 开关 | Switch 控制 engine.isCrownFeatureEnabled，开启自动切触控板 |
| V04 | 方案选择页面 | 从"切换按键映射方案"进入，方案列表显示正确，可切换 |
| V05 | 新建/重命名/删除方案 | CRUD 完整，default 不可删 |
| V06 | 虚拟手柄方案配置 | 配置项可调，与旧 OSC 互通 |
| V07 | 全屏编辑器 | 可进入并编辑，元素选中时属性面板展示，退出时保存 |
| V08 | 导出 .mdat | SAF 保存，文件中含 config + elements 完整数据 |
| V09 | 导入 .mdat | SAF 选择 → 解析 → 合并确认弹窗 |
| V10 | 导出 .mkmp | 简化 JSON，不含 MD5/版本号 |
| V11 | 导入 .mkmp | 命名 + 覆盖选择 → 写入数据库 |
| V12 | 导入往返测试 | 导出 → 删除 → 导入同一文件 → 方案可用 |
| V13 | 旧 Game.kt 不受影响 | 旧入口串流时相关功能正常 |

---

## 八、风险与缓解

| 风险 | 缓解 |
|------|------|
| 旧 `Game.kt` 仍使用旧 key，两个入口状态不同步 | 旧入口将被废弃，新旧 key 各自独立。用户选择使用新入口时一致走新 key |
| `ElementController` Java 类嵌入 Compose AndroidView 后触摸事件冲突 | 编辑器全屏时拦截所有触摸事件，退出时恢复 |
| `.mkmp` 简化格式缺少版本升级链，旧版文件无法导入新版应用 | 在解析时加入 version 检查，若版本 > 当前支持版本则提示升级 |
| `SuperConfigDatabaseHelper` 的 `exportConfigToJson()` 输出格式复杂，不适合直接作为 .mkmp | .mkmp 使用独立序列化，仅提取 name + config 行 + element 行 |
| 虚拟手柄方案配置（`oscOpacity` 等）与旧 `VirtualController` 分属不同系统 | 统一读写 `PreferenceConfiguration` 的相同字段 |
| 方案选择页面读取 `SuperConfigDatabaseHelper` 时 block 主线程 | DB 查询使用 `Dispatchers.IO` + `withContext` |
| 旧代码 `category_crown_features` 删除后旧 Game.kt 设置页可能报错 | `StreamSettings.kt` 中 Conditional 加载该分类，加 null check |

---

## 九、文件变更清单

### 修改文件

| 操作 | 路径 | 说明 |
|------|------|------|
| 修改 | `PreferenceConfiguration.kt` | + `keyMappingEnabled` 字段 + `KEY_MAPPING_ENABLED_PREF_STRING` 常量 |
| 修改 | `StreamEngine.kt` | `setCrownFeatureEnabled()` 方法, `isCrownFeatureEnabled` 委托到新字段 |
| 修改 | `SubPanelContainer.kt` | KeyMappingSection 连接真实逻辑, + 全屏页面回调 |
| 修改 | `StreamOverlay.kt` | + fullScreenPage 状态, 处理全屏页面打开/关闭 |
| 修改 | `StreamActivity.kt` | + 全屏页面控制逻辑 |
| 修改 | `strings.xml` | 字符串资源改名 |
| 修改 | `preferences.xml` | 移除王冠相关条目 |
| 修改 | `StreamSettings.kt` | 删除 `category_crown_features` |
| 修改 | `settings.gradle`? | 确保新包名被编译 |

### 新建文件

| 操作 | 路径 | 说明 |
|------|------|------|
| 新建 | `stream/ui/panels/KeyMappingSchemeSelector.kt` | 全屏方案选择页面 |
| 新建 | `stream/ui/panels/KeyMappingEditor.kt` | 全屏编辑器 |
| 新建 | `settings/tabs/KeyMappingSettingsPage.kt` | Settings Tab 按键配置管理 |
| 新建 | `stream/ui/common/MkmpFormat.kt` | .mkmp 序列化/反序列化 |

---

## 十、执行时序建议

```
时间轴 (周)
│ 第1周             第2周             第3周
│
├─ Batch A ─────────┤
│  Pref + 命名      │
│                   ├─ Batch B ──────┤
│                   │  KeyMapping    │
│                   │  + 方案选择     │
│                   │                ├─ Batch C ──┤
│                   │                │  编辑器     │
│                   ├─ Batch D ──────┤            │
│                   │  Settings Tab  │            │
│                   │                ├─ Batch E ──┤
│                   │                │  格式+收尾  │
└───────────────────┴────────────────┴────────────┘
```

- Batch A 与 streamplan 剩余工作**无冲突**，可立即开始
- Batch B 等待 streamplan Phase 2 完成后才能集成（但 UI 可并行开发）
- Batch C 依赖 Batch B（方案选择页面必须先存在）
- Batch D 独立于串流页面，可随时开始
- Batch E 依赖所有前置
