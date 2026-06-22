# MoonLink 包迁移与清理方案

## 一、需求概述

将 MoonLink 的构建标识和 `com.alexclin.moonlink` 主包迁移到 `com.alexclin.moonlink.android` 新命名空间，同时：

1. **`com.limelight.*` 保持不动** — 不迁移包名，只处理必要的构建配置和资源文件
2. **`com.alexclin.moonlink` → `com.alexclin.moonlink.android`** — 主包名迁移
3. **applicationId 更新** — 全新独立 applicationId
4. **数据库重置** — 版本号改为 1，删除所有升级路径和导入兼容代码
5. **兼容代码移除** — 移除旧 Crown 桥接层残留

---

## 二、技术方案

### 2.1 构建配置变更

| 配置项 | 当前值 | 新值 |
|--------|-------|------|
| `namespace` | `com.limelight` | `com.alexclin.moonlink.android` |
| nonRoot `applicationId` | `com.limelight` | `com.alexclin.moonlink.android` |
| root `applicationId` | `com.limelight.root` | `com.alexclin.moonlink.android.root` |

**`namespace` 变更的关键影响**：
- `R` 资源类将从 `com.limelight.R` 变为 `com.alexclin.moonlink.android.R`
- 所有 `import com.limelight.R` 必须同步更新为 `import com.alexclin.moonlink.android.R`
- `AndroidManifest.xml` 中的 `.ClassName` 相对引用将解析到新 namespace 下，需改为全限定类名

### 2.2 AndroidManifest 处理

**问题**：`namespace = 'com.alexclin.moonlink.android'` 后，Manifest 中形如 `.Game`、`.PcView` 的引用会解析到新 namespace 下，但实际类仍在 `com.limelight.*` 包中。

**处理方案**：将所有 `.ClassName` 相对引用改为全限定类名。

#### 需要改为全限定类名的条目（共 21 处）

| # | 当前相对写法 | 目标全限定类名 |
|---|-------------|--------------|
| 1 | `.LimelightApplication` | `com.limelight.LimelightApplication` |
| 2 | `.PosterContentProvider` | `com.limelight.PosterContentProvider` |
| 3 | `.PcView` | `com.limelight.PcView` |
| 4 | `.ShortcutTrampoline` | `com.limelight.ShortcutTrampoline` |
| 5 | `.AppView` | `com.limelight.AppView` |
| 6 | `.SunshineWebUiActivity` | `com.limelight.SunshineWebUiActivity` |
| 7 | `.preferences.StreamSettings` | `com.limelight.preferences.StreamSettings` |
| 8 | `.preferences.CapabilityDiagnosticActivity` | `com.limelight.preferences.CapabilityDiagnosticActivity` |
| 9 | `.preferences.AddComputerManually` | `com.limelight.preferences.AddComputerManually` |
| 10 | `.Game` | `com.limelight.Game` |
| 11 | `.discovery.DiscoveryService` | `com.limelight.discovery.DiscoveryService` |
| 12 | `.computers.ComputerManagerService` | `com.limelight.computers.ComputerManagerService` |
| 13 | `.binding.input.driver.UsbDriverService` | `com.limelight.binding.input.driver.UsbDriverService` |
| 14 | `.services.KeyboardAccessibilityService` | `com.limelight.services.KeyboardAccessibilityService` |
| 15 | `.services.StreamNotificationService` | `com.limelight.services.StreamNotificationService` |
| 16 | `.utils.UpdateDownloadReceiver` | `com.limelight.utils.UpdateDownloadReceiver` |
| 17 | `.HelpActivity` | `com.limelight.HelpActivity` |
| 18 | `.AppSelectionActivity` | `com.limelight.AppSelectionActivity` |
| 19 | `.widget.GameListWidgetProvider` | `com.limelight.widget.GameListWidgetProvider` |
| 20 | `.widget.GameListWidgetService` | `com.limelight.widget.GameListWidgetService` |
| 21 | `.widget.WidgetConfigurationActivity` | `com.limelight.widget.WidgetConfigurationActivity` |

#### 已为全限定类名无需改的（5 处）

| 当前写法 | 说明 |
|---------|------|
| `com.alexclin.moonlink.MoonLinkMainActivity` | 随主包迁移 → `com.alexclin.moonlink.android.MoonLinkMainActivity` |
| `com.alexclin.moonlink.stream.StreamActivity` | 随主包迁移 → `com.alexclin.moonlink.android.stream.StreamActivity` |
| `com.google.firebase.provider.FirebaseInitProvider` | 第三方库，不动 |
| `androidx.core.content.FileProvider` | 平台类，不动 |
| `com.easytier.jni.EasyTierVpnService` | 独立包，不动 |

#### Flavor manifest

- `app/src/nonRoot/AndroidManifest.xml` — 只有 `android:label`，无类引用，无需修改
- `app/src/root/AndroidManifest.xml` — 只有 `android:label` + `extractNativeLibs`，无需修改

### 2.3 `com.alexclin.moonlink` 主包迁移

**`com.alexclin.moonlink` → `com.alexclin.moonlink.android`**

- 涉及 ~68 个 Kotlin 源文件
- 包括：按键映射 UI/引擎、StreamEngine、StreamActivity、新版 Compose 首页等
- 这些文件原本就在 `com.alexclin.moonlink` 包下，迁移到 `com.alexclin.moonlink.android` 子包

### 2.4 `import com.limelight.R` → `import com.alexclin.moonlink.android.R`

`namespace` 改为 `com.alexclin.moonlink.android` 后，`R` 资源类生成在新包下。

**全局替换即可**，共 **72 处**引用，涉及 **62 个文件**：

#### `com.alexclin.moonlink.*`（4 个文件）

| 文件 | 行号 |
|------|------|
| `.../moonlink/home/DeviceListScreen.kt` | 49 |
| `.../moonlink/home/PairingHelper.kt` | 4 |
| `.../moonlink/settings/WidgetSettingsScreen.kt` | 21 |
| `.../moonlink/stream/StreamActivity.kt` | 28 |

#### `com.limelight` 根包（2 个文件）

`CrashReportPrompt.kt:20`, `.../nvstream/NvConnection.kt:28`

#### `com.limelight.binding.*`（16 个文件）

`AudioDiagnostics.kt`, `MicrophoneManager.kt`, `ControllerContext.kt`, `InputCaptureManager.kt`, `UsbDriverService.kt`, `VirtualController.java`, `VirtualControllerElement.java`, `ControllerManager.kt`, `KeyboardUIController.kt`, `ItemPageSuperMenu.kt`, `PageSuperMenuController.kt`, `PageDeviceController.kt`, `PageConfigController.java`, `SuperPagesController.kt`, `NumberSeekbar.kt`, 以及 `ElementController.java`, `DigitalCommonButton.java`, `DigitalSwitchButton.java`, `DigitalMovableButton.java`, `DigitalPad.java`, `DigitalStick.java`, `DigitalCombineButton.java`, `AnalogStick.java`, `GroupButton.java`, `WheelPad.java`, `InvisibleAnalogStick.java`, `InvisibleDigitalStick.java`

#### `com.limelight.preferences.*`（11 个文件）

`StreamSettings.kt`, `SeekBarPreference.kt`, `SeekBarPreferenceDialogFragment.kt`, `IconListPreference.kt`, `IconListPreferenceDialogFragment.kt`, `AboutDialogPreference.kt`, `PerfOverlayDisplayItemsPreference.kt`, `CustomResolutionsPreference.kt`, `CustomResolutionsPreferenceDialogFragment.kt`, `ConfirmDeleteOscPreference.kt`, `ConfirmDeleteOscDialogFragment.kt`, `DynamicPerfOverlayPositionPreference.kt`, `AddComputerManually.kt`, `CapabilityDiagnosticActivity.kt`

#### `com.limelight.ui.*`（3 个文件）

`AdapterFragment.kt:12`, `CursorView.kt:10`, `FloatBallManager.kt:22`

#### `com.limelight.utils.*`（13 个文件）

`UpdateManager.kt:27`, `UiHelper.kt:21`, `TvChannelHelper.kt:22`, `SpinnerDialog.kt:8`, `ShortcutHelper.kt:12`, `ServerHelper.kt:8`, `Dialog.kt:16`, `ColorPickerDialog.kt:27`, `BackgroundImageManager.kt:13`, `Iperf3Tester.kt:20`, `FullscreenProgressOverlay.kt:13`, `EasyTierController.kt:23`, `AppSettingsManager.kt:8`

#### 其他

`.../dialogs/AddressSelectionDialog.kt:14`, `.../widget/WidgetConfigurationActivity.kt:17`, `.../widget/GameListWidgetProvider.kt:14`, `.../widget/GameListRemoteViewsFactory.kt:12`, `.../services/StreamNotificationService.kt:20`, `.../grid/GenericGridAdapter.kt:11`, `.../grid/PcGridAdapter.kt:22`, `.../grid/AppGridAdapter.kt:13`, `.../grid/assets/CachedAppAssetLoader.kt:17`

### 2.5 数据库重置

**文件**：`com/limelight/binding/input/advance_setting/sqlite/SuperConfigDatabaseHelper.java`

从旧版本继承的数据库需要全新开始：

| 项目 | 旧值 | 新值 |
|------|------|------|
| `DATABASE_VERSION` | **11** | **1** |
| `DATABASE_OLD_VERSION_1~6`, `DATABASE_OLD_VERSION_10` | 存在 | 全部删除 |
| `onUpgrade()` | 完整的 v1→v11 增量升级链（~60 行） | 空方法体 |
| `upgradeExportedConfig()` | 导入文件版本兼容升级逻辑（~100 行） | **整个方法删除** |
| `importConfig()`/`mergeConfig()` | 调用 `upgradeExportedConfig()` 兼容旧文件 | 改为校验版本号精确等于 1，否则返回 -3 |
| `onCreate()` | 已有完整 v11 schema（所有字段） | **保持不变** |

**`onCreate()` schema 完整性验证**：逐列比对确认 `onCreate()` 已包含 `onUpgrade()` 中所有增量添加的字段：

element 表追加的列：
- v6 添加 `COLUMN_INT_ELEMENT_FLAG1` → ✅ `onCreate()` 已有
- v7 添加字体颜色/大小三列 → ✅ `onCreate()` 已有
- v8 添加 `extra_attributes` → ✅ `onCreate()` 已有

config 表追加的列：
- v3 添加 `game_vibrator`, `button_vibrator` → ✅ `onCreate()` 已有
- v4 添加 `mouse_wheel_speed` → ✅ `onCreate()` 已有
- v5 添加 `enhanced_touch` → ✅ `onCreate()` 已有
- v9 添加全局样式三列 → ✅ `onCreate()` 已有
- v10 添加 `scheme_type` + 6 个 OSC 列 → ✅ `onCreate()` 已有

### 2.6 JNI 约束（自动满足）

由于 `com.limelight.*` 下所有类 **不迁移**，JNI 相关文件天然不受影响：

| 文件 | 说明 |
|------|------|
| `.../jni/moonlight-core/callbacks.c` | `Java_com_limelight_nvstream_jni_MoonBridge_*` + `FindClass("com/limelight/...")` → 不动 |
| `.../jni/moonlight-core/simplejni.c` | 30 个 `Java_com_limelight_nvstream_jni_MoonBridge_*` → 不动 |
| `.../jni/moonlight-core/OpusEncoder.c` | 3 个 `Java_com_limelight_binding_audio_OpusEncoder_native*` → 不动 |
| `.../jni/moonlight-core/bass_energy_bridge.cpp` | 纯 C++ 计算，无 JNI 函数签名 → 不动 |
| `.../nvstream/jni/MoonBridge.java` | 包名 `com.limelight.nvstream.jni` 不变 |
| `.../binding/audio/OpusEncoder.kt` | 包名 `com.limelight.binding.audio` 不变 |
| `.../jni/Android.mk` + `Application.mk` | 构建配置 → 不动 |
| `.../jni/moonlight-core/Android.mk` | 构建配置 → 不动 |
| `.../jni/evdev_reader/Android.mk` | 构建配置 → 不动 |

### 2.7 兼容代码移除清单

| 文件 | 移除内容 | 说明 |
|------|---------|------|
| `StreamEngine.kt` | `controllerManager` 属性 + `initializeControllerManager()` | 已为 `@Deprecated` 空实现 |
| `SchemeUtils.kt` | `syncToControllers()` 方法 | 已为 `@Deprecated`，有替代方法 |
| `KeyMappingScreen.kt` / 选单相关 | 旧版本选单选项 | 界面 UI 精简 |

**`com.limelight.*` 下的类（Game.kt、ControllerManager.kt、ElementController.java 等）均保持不动。**

### 2.8 ProGuard 规则更新

`proguard-rules.pro` 中的 `-keep class com.limelight.**` 规则 **不需要修改**（`com.limelight.*` 类未迁移）。但如果 namespace 变更影响到混淆规则中的引用路径，需同步检查。

---

## 三、执行顺序

### Batch 1: 构建配置 + 包名基础
1. 修改 `app/build.gradle`：`namespace` → `com.alexclin.moonlink.android`，`applicationId` 按 flavor 更新
2. 更新 AndroidManifest.xml 中 21 处 `.ClassName` → 全限定名（详见 2.2 清单）
3. `com.alexclin.moonlink` → `com.alexclin.moonlink.android`（~68 个源文件）
4. 全局替换 `import com.limelight.R` → `import com.alexclin.moonlink.android.R`（72 处，详见 2.4 清单）
5. 更新 flavor 的 AndroidManifest（nonRoot/root 中的字符串引用）

### Batch 2: 数据库重置
1. `SuperConfigDatabaseHelper.java`：`DATABASE_VERSION=11` → `1`
2. 删除旧版本常量 `DATABASE_OLD_VERSION_1~6`、`DATABASE_OLD_VERSION_10`
3. 删除 `onUpgrade()` 方法体（保留空桩）
4. 删除 `upgradeExportedConfig()` 整个方法
5. 修改 `importConfig()`/`mergeConfig()` — 版本校验改为精确等于 1 而不是调用升级逻辑
6. `onCreate()` 保持原样（已有完整 schema）

### Batch 3: 兼容代码清理
1. `StreamEngine.kt`：删除 `controllerManager` + `initializeControllerManager()`
2. `SchemeUtils.kt`：删除 `syncToControllers()`
3. 精简旧选单 UI

### Batch 4: 全局验证
1. IDE 编译全项目
2. 检查 R 资源引用
3. 检查所有 Manifest 类名引用正确
4. 安装测试 + 数据库初始化测试

---

## 四、风险与注意事项

1. **`R` 资源引用**：`namespace` 变更后所有 `import com.limelight.R` 都必须改为 `com.alexclin.moonlink.android.R`，遗漏将导致编译错误
2. **Manifest 类名**：所有 activity/service/receiver 的 `android:name` 改为全限定名后，后续新增组件需注意使用全路径
3. **SharedPreferences 清空**：旧 `com.limelight_preferences` 数据在新 App 中不可见
4. **不能再覆盖安装**：新的 applicationId 意味着新 App 与旧版完全隔离
5. **旧导出文件不可导入**：数据库版本重置为 1 后，旧 Crown 导出的文件（version=9/10/11）将因版本号不匹配被拒绝导入

---

## 五、验收标准

- [ ] 编译通过，无错误
- [ ] 安装到设备后 App 能正常启动
- [ ] `com.limelight.*` 类未迁移，保持原包名
- [ ] JNI 入口和 C 函数签名未做任何改动
- [ ] AndroidManifest 中所有类名使用全限定名
- [ ] 数据库首次创建无异常（`DATABASE_VERSION = 1`，无 `onUpgrade`，无 `upgradeExportedConfig`）
- [ ] 所有 `@Deprecated` 兼容代码已移除
- [ ] `import com.limelight.R` 已全部替换为 `com.alexclin.moonlink.android.R`
