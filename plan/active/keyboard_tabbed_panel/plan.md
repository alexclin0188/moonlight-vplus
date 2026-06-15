# 键盘面板三标签页重构 — 参考 UU 远程设计

> 基于 phase5 已完成的 KeyboardSubPanel，重构为 UU 远程风格的三标签页面板。
> 参考目录：`D:\Work\moonlink\design\UURemote\键盘\`

---

## 目标

点击窄面板上的"键盘"后，弹出三标签页面板：
1. **输入法** — 呼出系统输入法全键盘（默认选中）
2. **快捷键** — 预置 12 个 + 自定义快捷键网格（6×2 横屏）
3. **虚拟键盘** — 复用旧代码 `KeyboardUIController`，高度 = 系统键盘高度

---

## 核心设计

### 布局结构（TabBar 底部固定）

```
┌──────────────────────────────┐
│          串流画面             │
│                              │
├──────────────────────────────┤
│ 输入法│快捷键│虚拟键盘│ ✕     │ ← TabBar（位置由缓存键盘高度确定，永远不动）
├──────────────────────────────┤
│                              │
│  输入法 → 系统键盘             │
│  快捷键 → 6×2 网格            │ ← 同一区域，三选一
│  虚拟键盘 → QWERTY（高度=键盘） │
│                              │
└──────────────────────────────┘
```

### 关键技术点

1. **TabBar 位置固定**：用 `WindowInsets.ime.getBottom()` 获取键盘高度并缓存，TabBar 通过 `padding(bottom=缓存高度)` 固定位置
2. **虚拟键盘高度适配**：AndroidView 高度 = 缓存的系统键盘高度
3. **快捷键网格**：横屏 6 列 × 2+ 行，`LazyVerticalGrid` 可滚动
4. **编辑模式**：编辑按钮是网格第一项，点击切换编辑模式，支持删除自定义快捷键和重置排序

---

## 文件变更清单

| 操作 | 路径 | 说明 |
|------|------|------|
| **新建** | `keyboard/ShortcutDefinitions.kt` | 12 个预置快捷键定义 |
| **新建** | `keyboard/VirtualKeyboardBridge.kt` | KeyboardUIController → StreamEngine 适配器 |
| **重写** | `keyboard/KeyboardSubPanel.kt` | 三标签页 UI（底部 TabBar + imePadding） |
| **修改** | `engine/StreamEngine.kt` | +sendKeyboardInputWithModifier +rumbleSingleVibrator |
| **修改** | `ui/StreamOverlay.kt` | 面板 BottomCenter + 缓存键盘高度定位 |
| **修改** | `AndroidManifest.xml` | StreamActivity 加 `adjustResize` |

### 复用已有资源（不修改）

- 旧键盘：`KeyboardUIController.kt`, `KeyboardGestureDetector.kt`
- 旧布局：`layer_6_keyboard.xml`, `layout_keyboard_main/nav/numpad/mini.xml`
- 旧 drawable：`keyboard_*.xml`（13 个）
- 旧样式：`VirtualKeyboardStyle`, `KeyboardKeyRefined` 等
- 快捷键存储：`CustomKeyRepository.kt`, `AddCustomKeyDialog.kt`

---

## 详细设计

### 1. ShortcutDefinitions.kt

12 个预置快捷键，横屏 6×2 排列：

| 行\列 | 1 | 2 | 3 | 4 | 5 | 6 |
|-------|---|---|---|---|---|---|
| **第1行** | Ctrl+C 复制 | Ctrl+V 粘贴 | Ctrl+X 剪切 | Ctrl+A 全选 | Ctrl+Z 撤销 | Win 开始 |
| **第2行** | Ctrl+S 保存 | Win+D 桌面 | Win+Tab 切换 | Win+L 锁定 | Alt+Tab 应用 | Alt+F4 关闭 |

### 2. VirtualKeyboardBridge.kt

适配器：接收 `KeyboardUIController.OnKeyboardEventListener` 回调，转换为 `StreamEngine.sendKeyboardInputWithModifier()` 调用。维护修饰键 bitmask。

### 3. StreamEngine 新增方法

```kotlin
fun sendKeyboardInputWithModifier(keyCode: Short, down: Boolean, modifier: Byte)
fun rumbleSingleVibrator(lowFreq: Short, highFreq: Short, duration: Int)
```

### 4. KeyboardSubPanel.kt（重写）

#### TabBar（底部固定）

```kotlin
// 缓存键盘高度
var cachedKeyboardHeight by remember { mutableIntOf(0) }
val imeHeightPx = WindowInsets.ime.getBottom(density)
LaunchedEffect(imeHeightPx) {
    if (imeHeightPx > 0) cachedKeyboardHeight = imeHeightPx
}
val effectiveHeightDp = with(density) {
    if (imeHeightPx > 0) imeHeightPx.toDp()
    else cachedKeyboardHeight.toDp()
}

Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.align(Alignment.BottomCenter)) {
        // 内容区
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (selectedTab) { 0/1/2 }
        }
        // TabBar
        KeyboardTabBar(...)
    }
    // 底部留空 = 缓存键盘高度
    Spacer(Modifier.height(effectiveHeightDp))
}
```

#### 输入法标签

- 进入时 `LaunchedEffect` 调用 `engine.toggleKeyboard()` 弹出系统键盘
- 内容区只显示提示文字

#### 快捷键标签

- 进入时关闭系统键盘（`hideSoftInputFromWindow`）
- `LazyVerticalGrid(columns = Fixed(6))`
- 第一项：编辑按钮（切换编辑模式）
- 预置快捷键 12 个（不可拖动/删除）
- 自定义快捷键（可删除）
- 编辑模式：重置排序 + 添加自定义

#### 虚拟键盘标签

- 进入时关闭系统键盘
- `AndroidView` 包裹旧 `KeyboardUIController`
- 高度 = `effectiveHeightDp`（与系统键盘一致）

### 5. StreamOverlay.kt 修改

```kotlin
// 键盘面板：BottomCenter 对齐
AnimatedVisibility(
    visible = panelState == PanelState.KEYBOARD_PANEL,
    modifier = Modifier.align(Alignment.BottomCenter),
    // 从底部滑入/滑出动画
) {
    KeyboardSubPanel(engine = engine, onClose = { ... })
}
```

### 6. AndroidManifest.xml

```xml
<activity
    android:name="com.alexclin.moonlink.stream.StreamActivity"
    android:windowSoftInputMode="adjustResize" />
```

---

## 执行批次

### Batch 1：基础设施（3 项）
1. StreamEngine 新增 `sendKeyboardInputWithModifier()` + `rumbleSingleVibrator()`
2. 创建 `ShortcutDefinitions.kt`（12 个预置快捷键）
3. 创建 `VirtualKeyboardBridge.kt`（适配器）

### Batch 2：KeyboardSubPanel 重写（3 项）
4. TabBar + 缓存键盘高度 + 输入法标签
5. 快捷键标签（LazyVerticalGrid 6列 + 编辑模式 + 添加/删除/重置）
6. 虚拟键盘标签（AndroidView + 高度适配）

### Batch 3：集成与适配（2 项）
7. StreamOverlay 面板 BottomCenter 对齐
8. 横竖屏适配调试

### Batch 4：验证（2 项）
9. 功能验证：三标签切换、输入法弹出、快捷键发送、虚拟键盘按键、编辑模式
10. 回归验证：原有功能不受影响

---

## 验证清单

1. 点击窄面板"键盘" → 三标签页面板从底部弹出
2. 默认选中"输入法" → 系统键盘弹出，TabBar 在键盘上方
3. TabBar 位置由键盘高度确定，切换标签后位置不变
4. 点击"快捷键" → 系统键盘收起，6×2 快捷键网格显示在原键盘位置
5. 点击预置快捷键 → 对应组合键发送到主机
6. 点击"编辑" → 进入编辑模式，自定义快捷键显示删除按钮
7. 点击"添加自定义" → 弹出添加弹窗
8. 点击"重置排序" → 清空自定义快捷键
9. 内容超出时可上下滚动
10. 点击"虚拟键盘" → 显示 QWERTY 虚拟键盘，高度 = 系统键盘高度
11. 虚拟键盘按键可正常发送到主机
12. 虚拟键盘分页切换（Main/Nav/Num/Mini）正常
13. 点击关闭按钮 → 面板关闭
