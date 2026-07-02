# AGENTS.md — Moonlight X (moonlink-android)

## 项目简介

本项目是基于 [Moonlight](https://github.com/moonlight-stream/moonlight-android) 的增强版 Android 游戏串流客户端（别名 **Moonlight X**）。支持 NVIDIA GameStream / Sunshine 串流协议，在保持上游核心功能的基础上增加了键位映射编辑、虚拟键盘、自定义布局等增强特性。

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

- **Windows**: 使用 `gradlew.bat` 构建
  ```bash
  ./gradlew.bat installNoRootDebug
  ```
- **macOS / Linux**: 使用 `gradlew` 构建
  ```bash
  ./gradlew installNoRootDebug
  ```
- 构建产物位于 `app/build/outputs/apk/debug/` 目录下, 该命令会构建并安装apk到设备。


> 提示：首次构建会自动下载 Gradle 和依赖，请确保网络畅通。如需构建 Release 版本，使用 `assembleRelease` 并配置好签名信息。

## 通用说明（Android 项目）

- **最低 SDK 版本**: Android 5.0 (API 21)
- **构建系统**: Gradle (Kotlin DSL)，版本信息见 `gradle/libs.versions.toml`
- **主开发语言**: Kotlin
- **UI 框架**: Jetpack Compose + Material3
- **IDE 推荐**: Android Studio 最新稳定版
- **代码风格**: 遵循 Kotlin 官方编码规范，提交前请确保无 lint 错误
- **分支策略**: 主要开发在 `moonlink` 分支上进行
- **提交规范**: 提交信息建议使用中文或英文清晰描述变更内容，必要时附带 `Co-Authored-By` 署名
