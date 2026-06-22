# MoonLink 包迁移与清理方案

## 一、需求概述

将 MoonLink 从原有的 `com.limelight` + `com.alexclin.moonlink` 双包体系迁移到独立的 **`com.alexclin.moonlink.android`** 单包体系，同时：

1. **包名变更**：重新申请全新的 applicationId，作为独立 App 安装（不与旧版共存升级）
2. **数据库清理**：去掉所有版本升级路径，安装即创建当前 schema
3. **兼容代码移除**：去掉旧 Crown 桥接层（ControllerManager、syncToControllers 等）
4. **导入导出精简**：只保留新 `.mkmp` 方案导入导出 + 旧 Crown 按键配置兼容导入

---

## 二、技术方案

### 2.1 包名重命名策略

| 旧包名 | 新包名 | 说明 |
|--------|--------|------|
| `com.limelight` | `com.alexclin.moonlink.android.limelight` | Moonlight 核心包整体移入新命名空间 |
| `com.alexclin.moonlink` | `com.alexclin.moonlink.android` | 主包名直接迁移 |

**涉及文件**：
- `app/build.gradle` → applicationId、namespace
- `app/src/main/AndroidManifest.xml` → activity/service/ContentProvider 全类名
- 各 flavor 的 AndroidManifest（root/nonRoot）
- 全部 `.kt` / `.java` 源文件（~218 个）的 package 声明 + import
- `proguard-rules.pro` 中的 -keep 规则（com.limelight / com.alexclin.moonlink 类）
- `app/src/main/res/xml/` 中涉及包名的配置

**策略**：用 AS Refactor → Rename Package 或手动批量替换，优先保证 R 符号引用正确。

### 2.2 Application ID 设计

| Flavor | 当前 applicationId | 新 applicationId |
|--------|-------------------|-----------------|
| nonRoot (主线) | `com.limelight` | `com.alexclin.moonlink.android` |
| root | `com.limelight.root` | `com.alexclin.moonlink.android.root` |

- 移除 `sharedUserId`（旧版共享 UID 已无意义）
- 不同 flavor 通过不同的 applicationId 后缀区分

### 2.3 数据库清理

**文件**：`com/limelight/binding/input/advance_setting/sqlite/SuperConfigDatabaseHelper.java`

**变更**：
```java
// DATABASE_VERSION 从 11 重置为 1（全新起点）
// 删除以下旧版本常量：
//   DATABASE_VERSION_1 ~ DATABASE_VERSION_6
//   OLD_BUTTON_RELATION_NAME, OLD_ELEMENT_RELATION_NAME 等
// 删除整个 onUpgrade() 方法
// onCreate() 保持当前 V11 schema 创建逻辑不变（只是 VERSION 改为 1）
```

**保留**：`upgradeExportedConfig()` 方法（用于导入旧 Crown 配置时进行兼容转换）

> ⚠️ 注意：删除 `onUpgrade()` 后，如果数据库版本变化需要迁移，新版应使用 `androidx.sqlite.db.SupportSQLiteOpenDB.Callback.onUpgrade` 的 onUpgrade-dispatched 方式，或直接让旧数据不可用。

### 2.4 兼容代码移除清单

| 文件 | 移除内容 | 影响 |
|------|---------|------|
| `ControllerManager.kt` | **整个文件**（~150 行，旧 Crown 桥接器） | `StreamEngine` 中引用需更新 |
| `StreamEngine.kt` | `controllerManager` 属性 + `initializeControllerManager()` 方法 | 删除后检查调用点 |
| `SchemeUtils.kt` | `syncToControllers()` 方法 | 删除后检查调用点 |
| `ElementController.kt` | **整个文件**（旧版控制层） | 如果有其他引用需追查 |
| `KeyMappingScreen.kt` / 选单相关 | 旧版本选单选项 | 界面选项精简 |
| 旧列名 fallback 代码 | `getColumnIndex()` 回退逻辑 | 数据库已无旧列名 |

**甄别原则**：
- 被 `@Deprecated` 标记且说明"仅避免旧代码编译错误"的 → 删除
- 旧 Crown 对接代码（controllerManager 桥接） → 删除
- 旧版本 UI 入口（"旧版本按键映射方案"等） → 删除
- 按键映射新功能核心代码 → **保留**（`SchemeEngine`, `SubPanelContainer`, `SchemeEditActivity` 等）

### 2.5 导入/导出精简

**文件**：`com.alexclin.moonlink.stream.ui.editor.SchemeImportExport.kt`

| 功能 | 处理 |
|------|------|
| 新 `.mkmp` 方案导出 | **保留**（当前实现，JSON 格式） |
| 新 `.mkmp` 方案导入 | **保留** |
| 旧 Crown `.mdat` 导入（按键配置部分） | **保留**（兼容旧版本导入） |
| 旧 Crown `.mdat` 导出 | **删除** |
| 旧版本 scheme 列表管理 UI | **删除**多余的选单条目 |

---

## 三、执行顺序（按 batch）

### Batch 1: 包名重命名基础层
1. 修改 `app/build.gradle`（applicationId、namespace、依赖 path）
2. 批量替换 `com.limelight` → `com.alexclin.moonlink.android.limelight`
3. 批量替换 `com.alexclin.moonlink` → `com.alexclin.moonlink.android`
4. 更新 AndroidManifest.xml（所有 flavor + 非 root/root）
5. 更新 proguard-rules.pro
6. 合并/重建 Application 类
7. 清理 SharedPreferences keys（旧包名 `com.limelight` 前缀改为新包名）

### Batch 2: 数据库清理
1. 删除 `SuperConfigDatabaseHelper.onUpgrade()` + 旧版本常量
2. 确认 `onCreate()` 正确创建当前 v11 schema
3. 确认 `upgradeExportedConfig()` 保留且可用

### Batch 3: 兼容代码移除
1. 删除 `ControllerManager.kt`
2. 清理 `StreamEngine.kt`（controllerManager + initializeControllerManager）
3. 清理 `SchemeUtils.kt`（syncToControllers）
4. 清理选单 UI 选项

### Batch 4: 导入导出精简
1. 删除 `.mdat` 导出功能
2. 精简 `SchemeImportExport.kt`
3. 精简旧版本方案管理 UI

### Batch 5: 全局验证
1. IDE 编译全项目
2. 检查 R 资源符号引用
3. 安装测试
4. 数据库初始化测试
5. 旧配置导入测试

---

## 四、风险与注意事项

1. **包名重命名风险**：R 符号在重构后可能失效，建议用 AS 的 Refactor → Rename Package 自动处理
2. **SharedPreferences 清空**：旧 `com.limelight_preferences` 下的数据在新包名下不可见，用户需要重新配置
3. **旧配置导入兼容**：旧 Crown 导出的 `.mdat` 文件需要在升级后的 `upgradeExportedConfig()` 中正确转换
4. **ProGuard 混淆**：如果打开了混淆，需要确保新包名下的所有 GameStream 协议类不被混淆
5. **不再支持共存升级**：旧版用户如果安装新版，是两个完全独立的 App（新旧 data 目录隔离）

---

## 五、验收标准

- [x] 编译通过，无错误
- [x] 安装到设备后 App 能正常启动
- [x] 数据库首次创建无异常（`DATABASE_VERSION = 1`，无 `onUpgrade`）
- [x] 旧 Crown `.mdat` 配置文件可导入新版
- [x] 新 `.mkmp` 方案可正常导入导出
- [x] 所有 `@Deprecated` 兼容代码已移除
- [x] 包名在所有配置（Manifest、ProGuard、flavor、resources）中一致
