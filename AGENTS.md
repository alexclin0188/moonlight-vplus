# AGENTS.md — MoonLink (moonlink-android)

## 项目简介

本项目是基于 [Moonlight](https://github.com/moonlight-stream/moonlight-android) 的增强版 Android 游戏串流客户端（**MoonLink**）。支持 NVIDIA GameStream / Sunshine 串流协议，在保持上游核心功能的基础上增加了键位映射编辑、虚拟键盘、自定义布局等增强特性。

## 新开发代码位置

所有新增/修改的代码位于 `com.alexclin.moonlink.android` 包下：

```
app/src/main/java/com/alexclin/moonlink/android/
├── device/          — 设备详情、概览、串流设置
├── home/            — 设备列表、配对、封面图加载
├── navigation/      — 导航路由
├── settings/        — 各类设置界面
├── stream/          — 串流核心引擎及 UI
│   ├── engine/      — 串流引擎、状态管理、触摸处理
│   └── ui/          — 覆盖层、编辑器、面板、键盘等
├── theme/           — 主题、颜色、字体
├── util/            — 工具类
└── vpn/             — VPN 相关界面
```

## 旧代码说明

项目中可能包含上游 Moonlight 遗留的旧代码（主要位于 `com.alexclin.moonlink` 包之外或未被本次开发使用的类）。**旧代码主要用作参考，请尽量不要改动**，以免引入非预期的行为或导致与上游合并困难。如确需修改，请在提交信息中说明原因。

## 构建 & 打包 & 安装

- **仅构建 APK**（不连接设备）：
  ```bash
  ./gradlew app:assembleDebug
  ```
- **构建并安装到已连接的设备**：
  ```bash
  # macOS / Linux
  ./gradlew installDebug

  # Windows
  ./gradlew.bat installDebug
  ```
- 构建产物位于 `app/build/outputs/apk/debug/` 目录下。

> 提示：首次构建会自动下载 Gradle 和依赖，请确保网络畅通。如需构建 Release 版本，使用 `./gradlew assembleRelease` 并配置好签名信息（通过 `KEYSTORE_PATH`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 环境变量传入）。

## 通用说明（Android 项目）

- **最低 SDK 版本**: Android 6.0 (API 23)
- **目标 SDK / 编译 SDK**: 36 (Android 16)
- **构建系统**: Gradle 9.6.1 + AGP 9.2.1 (Kotlin DSL)，版本信息见 `gradle/libs.versions.toml`
- **主开发语言**: Kotlin 2.2.10
- **Java 兼容性**: Java 11 (source & target)
- **NDK 版本**: 28.2.13676358
- **UI 框架**: Jetpack Compose + Material3 (Compose BOM 2026.06.01, activity-compose 1.13.0, navigation-compose 2.9.8, lifecycle 2.10.0)
- **IDE 推荐**: Android Studio 最新稳定版
- **代码风格**: 遵循 Kotlin 官方编码规范，提交前请确保无 lint 错误
- **分支策略**: 主要开发在 `moonlink` 分支上进行
- **提交规范**: 提交信息建议使用中文或英文清晰描述变更内容，必要时附带 `Co-Authored-By` 署名

## 构建配置说明

以下是 `app/build.gradle` 和 `gradle.properties` 中需要注意的配置项：

| 配置项 | 说明 |
|---|---|
| `android.builtInKotlin=false` | 在 AGP 9.x 上必须保留，否则 `org.jetbrains.kotlin.android` 插件的 `kotlin {}` 会与 AGP 内置扩展冲突。AGP 10 将移除此项。 |
| `android.newDsl=false` | 在 AGP 9.x 上必须保留，因为 `org.jetbrains.kotlin.android` 插件仍期待 `BaseExtension` 类型。AGP 10 将移除此项。 |
| `resValues = true` (在 `buildFeatures` 中) | 允许在 `buildTypes` 中使用 `resValue`。AGP 9 从 `gradle.properties` 移除了默认行为，需显式声明。 |
| `coreLibraryDesugaringEnabled true` | 在旧版 Android 上支持 Java 8+ API（如 `java.time`、`java.util.Optional` 等）。 |
