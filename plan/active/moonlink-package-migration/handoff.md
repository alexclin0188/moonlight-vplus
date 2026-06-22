# Handoff: MoonLink 包迁移与清理

## 目标
将 MoonLink 迁移到独立包名 `com.alexclin.moonlink.android`，清理所有向后兼容代码，简化数据库和导入导出。

## 执行批次

### Batch 1: 包名重命名
- 修改 `app/build.gradle`：applicationId → `com.alexclin.moonlink.android`（root flavor → `.root`）
- 批量替换 `com.limelight` → `com.alexclin.moonlink.android.limelight`
- 批量替换 `com.alexclin.moonlink` → `com.alexclin.moonlink.android`
- 更新 `AndroidManifest.xml`（全 flavor）
- 更新 `proguard-rules.pro`
- 清理 SharedPreferences key（旧 `com.limelight` → 新包名）

### Batch 2: 数据库清理
- `SuperConfigDatabaseHelper.java`：
  - 将 `DATABASE_VERSION` 从 11 重置为 **1**（全新起点）
  - 删除 6 个 `DATABASE_VERSION_1~6` 旧版本常量
  - 删除整个 `onUpgrade()` 方法体
  - 保留 `onCreate()`（当前 V11 schema 创建逻辑不变）和 `upgradeExportedConfig()`

### Batch 3: 兼容代码移除
- 删除 `ControllerManager.kt` 整个文件
- `StreamEngine.kt`：删除 `controllerManager` 属性 + `initializeControllerManager()`
- `SchemeUtils.kt`：删除 `syncToControllers()` 方法
- 删除旧版本选单 UI 选项

### Batch 4: 导入导出精简
- 移除 `.mdat` 导出功能
- 保留 `.mkmp` 新方案导入导出 + 旧 Crown `.mkmp` 兼容导入
- 精简 `SchemeImportExport.kt` 和相关选单

### Batch 5: 验证
- 全量编译检查
- R 资源引用检查
- 安装运行时测试 + 数据库首次初始化 + 旧配置导入测试

## 关键文件清单
- `app/build.gradle`
- `app/src/main/AndroidManifest.xml`
- `app/src/nonRoot/AndroidManifest.xml`（如存在）
- `app/src/root/AndroidManifest.xml`（如存在）
- `com/limelight/binding/input/advance_setting/sqlite/SuperConfigDatabaseHelper.java`
- `com/alexclin/moonlink/stream/engine/StreamEngine.kt`
- `com/alexclin/moonlink/stream/ui/editor/SchemeImportExport.kt`
- `com/alexclin/moonlink/controller/ControllerManager.kt`
- `com/alexclin/moonlink/utils/SchemeUtils.kt`
- `proguard-rules.pro`

## 注意事项
- AS Refactor → Rename Package 是最安全的方式
- 新旧 App 完全独立（data 目录隔离），不做覆盖升级
- 旧 `.mdat` 导入需要 `upgradeExportedConfig()` 正常工作
- 旧的 `com.limelight_preferences` 数据在新 App 中不可见
