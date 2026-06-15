# 串流UI完善计划 — 基于代码现状全面重规划

> 创建日期：2026-06-15 | 状态：completed | 版本：1.4
> 原 streamplan 参考：`plan/streamplan/05_阶段4_核心功能分组.md` 等

---

## 一、代码现状审计

### 1.1 已完整实现 ✅

| 阶段 | 内容 | 验证状态 |
|------|------|----------|
| 阶段0 | 项目骨架 + StreamEngine + StreamActivity | ✅ 串流视频音频正常 |
| 阶段1 | 悬浮按钮 + 竖向窄条面板 | ✅ 交互完整 |
| 阶段2 | 操作子面板框架 + 列表↔详情导航 | ✅ AnimatedContent 滑动切换 |
| 阶段3 | 快捷操作行 QuickActionRow | ✅ 固定按钮+可配按钮+编辑弹窗+拖拽排序 |
| 阶段4 | 核心功能分组（触控模式+平移缩放+显示详情） | ✅ Batch B + Batch C 已完成 |
| 阶段5 | 键盘面板(4标签) + 按键发送 + 直接动作 | ✅ 三标签页全部功能可用 |
| 阶段6/7 | 收尾 + 测试 | ⏳ 待实施 |

**阶段5已实现清单**：
- `KeyboardSubPanel` — 4标签（输入法/快捷键/虚拟键盘/主机键盘），TabBar+键盘高度缓存
- `ShortcutDefinitions` — 14个预置快捷键（6×2网格 + 编辑按钮）
- `VirtualKeyboardBridge` — 旧 KeyboardUIController → StreamEngine 适配器
- `CustomKeyRepository` — SharedPreferences CRUD，兼容旧 GameMenu
- `AddCustomKeyDialog` / `DeleteCustomKeyDialog` — 完整弹窗
- `StreamEngine` — 11个按键便捷方法 + `sendKeyboardInputWithModifier()`
- 窄面板"桌面"→ `Win+D`，"窗口"→ `Win+Tab` 直接动作 ✅

### 1.2 仅有空壳 🔶（阶段4核心功能分组）

所有分组在主列表中有可见入口，但功能未实现，位于 `SubPanelContainer.kt` 内联：

| 条目 | Composable | 路径 | 实际状态 |
|------|-----------|------|----------|
| 平移缩放 | `PanZoomSection` | inline | 仅 label"平移缩放"，无 Switch |
| 触控模式 | `TouchModeSection` | inline | 仅 label"触控模式"，无 Chip |
| 按键映射 | `KeyMappingSection` | inline | Switch 存在但未连接 engine（本地变量空转） |
| **快捷操作 >** | `ShortcutActionsSection` | inline | **仅占位文字，入口需改向** |
| 显示 > | `DisplaySection` | inline | 仅占位文字"显示设置" |
| 主机设置 > | `HostSettingsSection` | inline | 仅占位文字"主机设置" |
| 外设 > | `PeripheralsDetail` | inline | 仅占位文字"外设设置" |
| — | `DetailPage.KEY_MAPPING` | inline | 空 `{}` lambda，不可达 |
| 体感助手 | `GyroSection` | inline | 仅 label"体感助手" |
| 更多 | `MoreSection` | inline | 仅 label"更多"，不可点击 |

### 1.3 阶段6/7 状态

- **返回键**：`StreamActivity.onBackPressed()` 仅调用 `engine.disconnect()`，无多级返回，无防抖
- **收尾**：主题颜色/空状态/横竖屏规范未做
- **测试**：未进行

---

## 二、快捷操作入口改向（Batch A — 高优先级）

### 问题

子面板主列表"快捷操作 >"条目当前导航到空壳 `ShortcutActionsSection`。既然键盘面板的"快捷键"标签已完整实现了全部快捷键功能（14个预置 + 自定义按键），应该直接复用。

### 改动方案

**3个文件，约30行代码改动**：

1. **`KeyboardSubPanel.kt`** — 添加 `initialTab` 参数
   ```kotlin
   fun KeyboardSubPanel(
       engine: StreamEngine,
       initialTab: Int = 0,  // ← 新增：0=输入法 1=快捷键 2=虚拟键盘 3=主机键盘
       onClose: () -> Unit = {},
       ...
   )
   // selectedTab 初始化改为 remember { mutableIntStateOf(initialTab) }
   ```

2. **`SubPanelContainer.kt`** — 添加回调参数
   ```kotlin
   fun SubPanelContainer(
       engine: StreamEngine,
       onOpenKeyboardShortcuts: () -> Unit = {},  // ← 新增
       modifier: Modifier = Modifier,
   )
   // "快捷操作 >" 的 onClick 改为 onOpenKeyboardShortcuts
   ```

3. **`StreamOverlay.kt`** — 处理新回调
   ```kotlin
   // SubPanelContainer 调用处新增：
   SubPanelContainer(
       engine = engine,
       onOpenKeyboardShortcuts = {
           panelState = PanelState.KEYBOARD_PANEL
           activeEntry = "keyboard"
           keyboardInitialTab = 1  // 快捷键标签
       },
   )
   // KeyboardSubPanel 调用处传入 initialTab
   ```

### 连锁清理（Batch G）

- 删除 `ShortcutActionsSection` 内联 composable（约15行）
- 删除 `DetailPage.SHORTCUT_ACTIONS` 枚举值（但保留序号以兼容）

---

## 三、阶段4核心功能分组实施（Batch B-F）

### Batch B：触控模式 + 平移缩放（优先级高，依赖少）

| Todo | 文件 | 复杂度 | 说明 |
|------|------|--------|------|
| TB-1 | `TouchModeSection` | 中 | 4个 Chip 横向排列（FilterChip），选中态联动 `engine.prefConfig`。经典模式关联"远程鼠标"按钮，触控板模式关联"双击按住"+"本地光标"开关。 |
| TB-3 | `PanZoomSection` | 低 | Switch 连接 `engine.setTouchOverrideEnabled()`。仅 `engine.prefConfig.enablePip` 为 true 时可见。 |

### Batch C：显示详情（优先级高，复杂度最高）

| Todo | 文件 | 复杂度 | 说明 |
|------|------|--------|------|
| TC-1 | `DisplaySection` | **高** | 6个子区域：(1)帧率切换 `list_fps` (2)码率分段滑块60级(Slider+映射表，松开调用`conn.setBitrate()`) (3)多显示器管理(列表+单选+VDD开关+外接开关) (4)自适应流畅度 Switch+子项 (5)HDR开关+子项 (6)画面设置(拉伸/反转/旋转 Switch 组) |

> **注意**：
> - 帧率切换需从 `PreferenceConfiguration` 读取当前值（通过 `list_fps` SP key）
> - 显示器列表通过 `conn.getDisplays()` 异步获取，返回 `List<NvHTTP.DisplayInfo>`（字段: `index`, `name`, `guid`）
> - **NvHTTP 不支持运行时切换显示器**：`display_name` 仅在串流启动时通过 query 参数传递。DisplaySection 中显示器选择修改后提示"需重启串流生效"
> - 码率滑块需复制原 `BitrateCardController` 的 5 段映射表（60 级），松开后调用 `conn.setBitrate(kbps, callback)` 实时生效
> - VDD 虚拟显示器常量 `VIRTUAL_DISPLAY_ID = 212333`，选中时设 `useVdd = true`

### Batch D：主机设置 + 外设详情（优先级高）

| Todo | 文件 | 复杂度 | 说明 |
|------|------|--------|------|
| TD-1 | `HostSettingsSection` | 中 | 7个 Switch 配置项，映射到 `engine.prefConfig` 对应字段。全部为简单开关，无复杂联动。 |
| TD-2 | `PeripheralsDetail` | 中 | 4个子入口（手柄/键盘/鼠标/麦克风），进入后显示对应设置项。手柄→Tab手柄设置，键盘→特殊按键映射，鼠标→双击阈值/修复中键/修复滚轮，麦克风→一级开关+音质+图标颜色。 |

### Batch E：体感助手 + 更多（优先级中）

| Todo | 文件 | 复杂度 | 说明 |
|------|------|--------|------|
| TE-1 | `GyroSection` | 中 | 复用 `GyroCardController` 核心逻辑：(1)总开关 Switch (2)模式 右摇杆/鼠标 (3)灵敏度 Slider 25级 (4)X/Y轴反转 Switch×2 (5)激活按键 始终/LT/RT |
| TE-2 | `MoreSection` | 中 | 展开式区域：(1)性能图层 开关+模式/方向/位置/显示项多选/背景透明度 (2)自动隐藏工具栏 3选1 RadioButton+延时Slider |

### Batch F：按键映射（推迟实施，优先级高，复杂度高）

> **推迟原因**：涉及方案选择(`pageConfigController`)、编辑器(`elementController.changeMode(Edit)`)、其它设置(触控开关/灵敏度/滚轮/震动等)等多个子系统交互，实现复杂度较高，放在核心分组最后实施以降低风险。

| Todo | 文件 | 复杂度 | 说明 |
|------|------|--------|------|
| TB-2 | `KeyMappingSection` | **高** | Switch 连接 `engine.isCrownFeatureEnabled`。**开启时自动将触控模式切换为触控板(`TRACKPAD`)**（调用 `engine.prefConfig` 三项标志 + `writePreferences`），用户需要其它模式可自行在触控模式中调整。开启后 AnimatedVisibility 展开3个子入口：切换方案(`pageConfigController.open()`)、编辑方案(`elementController.changeMode(Edit)`)、其它设置(触控开关/灵敏度/滚轮/震动等)。关闭按键映射时不自动恢复触控模式（由用户自行决定）。 |

> **自动切换触控板实现要点**：
> ```kotlin
> // 在 Switch onCheckedChange(true) 时：
> engine.prefConfig.enableEnhancedTouch = false
> engine.prefConfig.enableNativeMousePointer = false
> engine.prefConfig.touchscreenTrackpad = true
> engine.prefConfig.writePreferences(engine.activity)
> ```

---

## 四、阶段6 收尾（Batch G）

| Todo | 说明 |
|------|------|
| TF-1 | 返回键多级逻辑：在 `StreamOverlay` 中通过回调通知 `StreamActivity` 当前面板状态。Activity 的 `onBackPressed` 实现：详情→主列表→窄条→隐藏→退出。300ms 防抖。 |
| TF-2 | 清理死代码：删除 `ShortcutActionsSection` 空壳、移除 `SHORTCUT_ACTIONS` 枚举引用。 |
| TF-3 | UI 规范检查：主题颜色一致性、空状态文案补充、横竖屏面板自适应验证。 |

### 返回键逻辑设计

```
面板状态机（由 StreamOverlay 管理，回调通知 Activity）：

onBackPressed 优先级：
  1. 详情页可见 → 回主列表
  2. 子面板可见 → 回竖向窄条
  3. 键盘面板可见 → 回竖向窄条
  4. 竖向窄条可见 → 全隐藏(悬浮按钮)
  5. 全部隐藏 → engine.disconnect()（退出串流）

防抖：SystemClock.elapsedRealtime() 间隔 < 300ms 时忽略
```

---

## 五、阶段7 回归验证（Batch H）

| 编号 | 验证项 | 方法 |
|------|--------|------|
| V01 | 快捷操作入口改向 | 点击子面板"快捷操作>"→确认跳到键盘面板快捷键标签 |
| V02 | 触控模式4选1 | 切换4种模式→选中态+关联子项联动+prefConfig持久化 |
| V03 | 按键映射开关+自动切换 | 开启→自动切换触控板模式 + 子入口展示 + 方案选择/编辑可用 |
| V04 | 显示详情全部子项 | 帧率/码率滑块/显示器选择/HDR/画面设置生效 |
| V05 | 主机设置7开关 | 各开关写入prefConfig，可在旧Game.kt中读取 |
| V06 | 外设详情 | 4个子入口导航+设置项调整 |
| V07 | 体感助手 | 开关/模式/灵敏度/反转/激活键生效 |
| V08 | 更多 | 性能图层开关+自动隐藏选项 |
| V09 | 返回键多级 | 从详情页逐级返回到退出 |
| V10 | 返回键防抖 | 快速连按3次→仅触发一次 |
| V11 | 窄面板桌面/窗口 | 点击→Win+D/Win+Tab发送+面板关闭 |
| V12 | 键盘面板全部 | 4标签切换+快捷键14预置+自定义CRUD+虚拟键盘+主机键盘 |
| V13 | 快捷操作行 | 固定按钮+可配按钮+编辑弹窗+拖拽排序 不变 |
| V14 | 横竖屏切换 | 面板自适应 |
| V15 | 旧Game.kt | 旧入口串流不受影响 |

---

## 六、文件变更总清单

| 操作 | 路径 | 说明 |
|------|------|------|
| 修改 | `ui/keyboard/KeyboardSubPanel.kt` | +`initialTab` 参数 |
| 修改 | `ui/SubPanelContainer.kt` | +`onOpenKeyboardShortcuts` 回调；实现全部空壳（TB-1~TB-2, TC-1~TE-2） |
| 修改 | `ui/StreamOverlay.kt` | 连接快捷操作回调；返回键逻辑 |
| 修改 | `StreamActivity.kt` | 返回键多级逻辑 + 防抖 |
| 不需新建文件 | — | 所有核心分组实现在 `SubPanelContainer.kt` 内联 |

---

## 七、风险与缓解

| 风险 | 缓解 |
|------|------|
| DisplaySection 码率滑块映射表与旧 `BitrateCardController` 不一致 | 直接复制映射表常量，不自行推导 |
| `conn.getDisplays()` 异步调用在页面销毁后回调 | 使用 `DisposableEffect` + 取消标记 |
| 显示器选择需重启串流才能生效（NvHTTP 无运行时切换 API） | DisplaySection 显示器切换后 Toast 提示"需重启串流生效"，不做静默操作 |
| 返回键多级逻辑与 Compose 状态同步 | 在 StreamOverlay 中通过 `LaunchedEffect` 响应 Activity 传入的 externalPanelState |
| 体感助手复用旧 `GyroCardController` 导致耦合 | 仅复用数据读取逻辑，UI 完全 Compose 重写 |
| 按键映射自动切换触控板后用户不知道触控模式已变 | 在 Toast 中明确提示"已自动切换为触控板模式，可在触控模式中更改" |
| 按键映射编辑器(`ElementController`)与 Compose 生命周期不一致 | 使用 `DisposableEffect` 确保编辑器在 composable 销毁时释放 |
