# Handoff: 键盘面板三标签页重构

## 核心设计

- TabBar 固定在底部，位置由缓存的系统键盘高度确定，切换标签后不变
- 系统键盘 / 快捷键网格 / 虚拟键盘共享 TabBar 下方同一区域
- 虚拟键盘高度 = 系统键盘高度（动态适配）
- 快捷键网格横屏 6×2，编辑按钮为网格第一项

## 文件清单

| 操作 | 路径 | 说明 |
|------|------|------|
| 新建 | `keyboard/ShortcutDefinitions.kt` | 12 个预置快捷键 |
| 新建 | `keyboard/VirtualKeyboardBridge.kt` | 旧键盘→StreamEngine 适配器 |
| 重写 | `keyboard/KeyboardSubPanel.kt` | 三标签页 UI |
| 修改 | `engine/StreamEngine.kt` | +sendKeyboardInputWithModifier +rumbleSingleVibrator |
| 修改 | `ui/StreamOverlay.kt` | 面板 BottomCenter |
| 修改 | `AndroidManifest.xml` | +adjustResize |

## 执行顺序

### Batch 1：基础设施
- T1-1: StreamEngine 新增 2 个方法
- T1-2: ShortcutDefinitions.kt（12 个预置）
- T1-3: VirtualKeyboardBridge.kt

### Batch 2：UI 重写
- T2-1: TabBar + 缓存键盘高度 + 输入法标签
- T2-2: 快捷键标签（6列网格 + 编辑模式）
- T2-3: 虚拟键盘标签（AndroidView + 高度适配）

### Batch 3：集成
- T3-1: StreamOverlay BottomCenter
- T3-2: adjustResize + 横竖屏调试

### Batch 4：验证
- T4-1: 功能验证 13 项
- T4-2: 回归验证

## 约束

- 不修改 KeyboardUIController.kt / KeyboardGestureDetector.kt
- 旧 XML 布局/drawable/style 全部复用
- VirtualKeyboardBridge 只依赖 StreamEngine 公开 API
- 横屏优先，竖屏后续验证完善
