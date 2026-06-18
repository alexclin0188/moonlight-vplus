# Handoff: 按键映射方案 (Key Mapping Scheme)

## 当前状态

- **plan.json state**: **approved**（用户已确认执行）
- **前置依赖**: streamplan Phase 2 已就绪（SubPanelContainer + MainListView 存在）
- **代码现状**: KeyMappingSection 是空壳（Switch 未连 engine，3 子入口无实际功能）

## 用户关键决策摘要

| 决策点 | 结论 |
|--------|------|
| 改造范围 | 对齐 streamplan 大重构（StreamActivity + Compose） |
| 方案页面 | 新的 Compose 全屏页面，编辑器核心复用旧 ElementController |
| 设置位置 | 新 MoonLink 设置 Tab（Compose） |
| 改名策略 | 彻底改名（新 Pref key + 字符串） |
| 虚拟手柄 | 合并为方案体系中内置方案 |
| .mkmp 格式 | 简化独立 JSON 结构 |
| 时序 | 独立并行工作流 |

## Batch 分组

| Batch | 内容 | Todo 项 | 前置 |
|-------|------|---------|------|
| A | Pref + 命名改名 | T-01, T-02, T-03 | 无 |
| B | KeyMappingSection + 全屏方案选择 | T-04, T-05, T-06, T-07 | Batch A |
| C | 全屏编辑器 | T-08, T-09, T-10 | Batch B |
| D | Settings Tab 配置管理 | T-11, T-12, T-13, T-14 | Batch A |
| E | .mkmp 格式 + 验证 | T-15, T-16, T-17, T-18 | Batch C, D |

## 关键文件引用

- 设计文档：`../../design/MoonLink_Detailed_Design.md` §5.3.3, §5.3.3.1, §5.3.3.2, §6.7
- streamplan：`../../streamplan/05_阶段4_核心功能分组.md`
- 代码现状：`../../active/stream_ui_completion/plan.md`
- 旧 Crown 核心：`com.limelight.binding.input.advance_setting.ControllerManager`
- 旧 Database：`com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper`

## 注意事项

1. 旧 `Game.kt` 入口保持完整可用，本计划不修改任何 `com.limelight` 核心类。旧入口后续将被废弃。
2. Preference key 使用独立新 key `checkbox_enable_key_mapping`，不与旧 key 同步。
3. `.mkmp` 格式设计需在 Batch E 前由用户确认最终字段结构。
4. 虚拟手柄方案 Pref key 需要确认与 `PreferenceConfiguration.kt` 已有字段的映射关系。
5. Settings Tab 容器框架已存在，只需新增页面入口。
