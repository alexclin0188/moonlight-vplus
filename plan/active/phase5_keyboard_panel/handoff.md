# Handoff: 阶段 5 — 键盘子面板 + 按键发送 + 直接动作

## 核心变更

1. **废弃独立 KeySender** → 在 `StreamEngine` 中添加 11 个便捷方法
2. **StreamEngine.sendKeys 延迟** 50ms → **25ms**，加 null 检查
3. **CustomKeyData.keys 类型** `ShortArray` → `List<Short>`
4. **sendRemoteMouseToggle** 使用 VK_N(78) 而非 0x43

## 文件清单

| 操作 | 路径 | 说明 |
|------|------|------|
| 修改 | `stream/engine/StreamEngine.kt` | sendKeys 延迟与安全 + 11 便捷方法 |
| 新建 | `stream/ui/common/CustomKeyRepository.kt` | SharedPreferences 存储 |
| 新建 | `stream/ui/keyboard/KeyboardSubPanel.kt` | 键盘子面板 |
| 新建 | `stream/ui/keyboard/AddCustomKeyDialog.kt` | 添加弹窗 |
| 新建 | `stream/ui/keyboard/DeleteCustomKeyDialog.kt` | 删除弹窗 |
| 修改 | `stream/ui/StreamOverlay.kt` | 集成键盘面板 |

## 执行批次

### Batch 1：StreamEngine 便捷方法
- T1-1: sendKeys 延迟 50→25ms，Runnable 加 `val c = conn ?: return@postDelayed`
- T1-2: 添加 11 个 fun（见 plan.md 5.1 节）
- T1-3: 回归验证 QuickActionRow

### Batch 2：CustomKeyRepository + 弹窗
- T2-1: `CustomKeyRepository.kt` save/delete/loadAll + 输入验证 + 重复名称检查
- T2-2: `AddCustomKeyDialog.kt` 名称 + 十六进制键码输入
- T2-3: `DeleteCustomKeyDialog.kt` 多选列表

### Batch 3：KeyboardSubPanel + 集成
- T3-1: `KeyboardSubPanel.kt` 布局 + 列表 + 弹窗调用
- T3-2: StreamOverlay 替换 TODO + 条件动画（横屏 slideInH/竖屏 slideInV）
- T3-3: 横竖屏调试

### Batch 4：验证
- T4-1: 8 项手动测试 + 横竖屏
- T4-2: 向后兼容（新旧读/写同一 SharedPreferences）

## 关键约束

- **零修改旧代码**：不动 GameMenu.kt、Game.kt、FloatBallHandler
- **SharedPreferences 格式兼容**：`custom_special_keys` / `data` key，JSON 结构不变
- **KeyboardSubPanel 不直接访问 engine.conn**，只调用 engine.sendXxx() 方法

## 风险提示

- sendKeys 延迟改 25ms 可能影响某些主机 → 回归验证 QuickActionRow
- 横竖屏动画条件切换可能引入布局问题
