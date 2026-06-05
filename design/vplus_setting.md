# Moonlight X 功能项分析

> 本文档分析当前主页设置功能项（不含EasyTier部分）和串流时游戏菜单的功能项与具体配置项。
> 基于代码分支的当前版本，源码路径 `app/src/main/res/xml/preferences.xml` 和 `GameMenu.kt`。

---

## 一、主页设置页面（StreamSettings）

设置页面采用 `DrawerLayout` + 侧边分类导航 + 搜索功能，共 14 个分类（不含EasyTier）。

### 1. 基本设置 (`category_basic_settings`)

| 配置项     | 类型                          | Key                         | 说明                                  |
| ------- | --------------------------- | --------------------------- | ----------------------------------- |
| 视频分辨率   | ListPreference              | `list_resolution`           | 预设分辨率列表（含原生分辨率），默认 1920x1080        |
| 视频帧数    | ListPreference              | `list_fps`                  | 预设帧率列表（含原生帧率），默认 60                 |
| 视频码率    | SeekBarPreference           | `seekbar_bitrate_kbps`      | 范围 500-800000 kbps，步长 500，默认按Mbps显示 |
| 主机分辨率缩放 | SeekBarPreference           | `seekbar_resolutions_scale` | 范围 50-400%，步长 10，默认 100%            |
| 自定义分辨率  | CustomResolutionsPreference | `custom_resolutions`        | 支持手动添加自定义分辨率（宽x高格式）                 |

### 2. 性能与流畅度 (`category_advanced_features`)

| 配置项         | 类型                 | Key                                     | 说明                           |
| ----------- | ------------------ | --------------------------------------- | ---------------------------- |
| 智能码率        | CheckBoxPreference | `checkbox_adaptive_bitrate`             | 根据丢包/延迟自动调节码率，默认关闭           |
| 智能码率模式      | ListPreference     | `list_abr_mode`                         | 画质优先/均衡/低延迟，默认均衡，依赖智能码率      |
| 视频帧速调节      | ListPreference     | `frame_pacing`                          | 优先最低延迟/平衡/优先视频流畅度/全都要，默认延迟优先 |
| 解锁所有可用帧数    | CheckBoxPreference | `checkbox_unlock_fps`                   | 解锁90/120帧，默认关闭               |
| 允许降低刷新率     | CheckBoxPreference | `checkbox_reduce_refresh_rate`          | 降低刷新率以省电，默认关闭                |
| 输出缓冲区队列大小   | SeekBarPreference  | `seekbar_output_buffer_queue_limit`     | 范围 1-5 帧，默认 2，影响延迟与流畅度平衡     |
| 强制MTK最大操作速率 | CheckBoxPreference | `checkbox_force_mtk_max_operating_rate` | MTK设备专用，默认关闭                 |

### 3. 主机设置 (`category_host_settings`)

| 配置项          | 类型                 | Key                                     | 说明                     |
| ------------ | ------------------ | --------------------------------------- | ---------------------- |
| 自动优化主机设置     | CheckBoxPreference | `checkbox_enable_sops`                  | 允许主机自动更改显示设置，默认开启      |
| 屏幕组合模式       | ListPreference     | `list_screen_combination_mode`          | Sunshine基地适用，默认使用主机端配置 |
| 在电脑上播放声音     | CheckBoxPreference | `checkbox_host_audio`                   | 主机和设备同时输出声音，默认关闭       |
| 断开并退出串流时锁定屏幕 | CheckBoxPreference | `checkbox_lock_screen_after_disconnect` | 断开后自动锁屏，默认关闭           |
| 切换"退出"按钮功能   | CheckBoxPreference | `checkbox_swap_quit_and_disconnect`     | 退出改为仅断开连接，默认关闭         |
| 仅控制模式        | CheckBoxPreference | `checkbox_control_only`                 | 仅控制流，不传输音视频，默认关闭       |
| 同步剪贴板文本      | CheckBoxPreference | `checkbox_clipboard_sync_text`          | 双向文本剪贴板同步，默认关闭         |
| 同步剪贴板图片      | CheckBoxPreference | `checkbox_clipboard_sync_image`         | 图片剪贴板同步，默认关闭           |

### 4. 视频设置 (`category_screen_position`)

| 配置项        | 类型                 | Key                                   | 说明                           |
| ---------- | ------------------ | ------------------------------------- | ---------------------------- |
| 更改编解码器设置   | ListPreference     | `video_format`                        | 自动/首选HEVC/首选AV1/首选H.264，默认自动 |
| 启用HDR      | CheckBoxPreference | `checkbox_enable_hdr`                 | HDR模式串流，默认关闭                 |
| 启用HDR串流高亮度 | CheckBoxPreference | `checkbox_enable_hdr_high_brightness` | HDR时强制最高亮度，依赖HDR， 默认关闭       |
| HDR模式      | ListPreference     | `list_hdr_mode`                       | HDR10(PQ)/HLG，默认HDR10，依赖HDR  |
| 强制完全动态范围视频 | CheckBoxPreference | `checkbox_full_range`                 | 实验性全范围视频，默认关闭                |
| 编解码与屏幕能力检测 | Preference         | `capability_diagnostic`               | 跳转能力检测页面                     |

### 5. 画面设置 (`category_display_behavior`)

| 配置项       | 类型                        | Key                           | 说明                  |
| --------- | ------------------------- | ----------------------------- | ------------------- |
| 启用画中画观察模式 | CheckBoxPreference        | `checkbox_enable_pip`         | 多任务时观看串流，默认关闭       |
| 将画面拉伸至全屏  | CheckBoxPreference        | `checkbox_stretch_video`      | 默认关闭                |
| 反转分辨率     | CheckBoxPreference        | `checkbox_reverse_resolution` | 交换宽高值，默认关闭          |
| 跟随屏幕旋转    | CheckBoxPreference        | `checkbox_rotable_screen`     | 设备旋转同步显示方向，默认关闭     |
| 使用外接显示器   | ExternalDisplayPreference | `use_external_display`        | 外接显示器上显示串流画面，默认关闭   |
| 画面位置      | IconListPreference        | `list_screen_position`        | 屏幕位置选择（含图标），默认居中    |
| 水平偏移      | SeekBarPreference         | `seekbar_screen_offset_x`     | 范围 0-100%，步长 5，默认 0 |
| 垂直偏移      | SeekBarPreference         | `seekbar_screen_offset_y`     | 范围 0-100%，步长 5，默认 0 |

### 6. 界面设置 (`category_ui_settings`)

| 配置项        | 类型                                   | Key                                 | 说明                                 |
| ---------- | ------------------------------------ | ----------------------------------- | ---------------------------------- |
| 语言         | LanguagePreference                   | `list_languages`                    | 语言选择（Android 13+原生/旧版弹窗）           |
| 使用小图标      | SmallIconCheckboxPreference          | `checkbox_small_icon_mode`          | 显示更多项目                             |
| 背景图来源      | ListPreference                       | `background_source`                 | 智能默认/摄影风景/二次元/自定义API/本地图片/不显示，默认智能 |
| 背景图片API    | EditTextPreference                   | `background_image_url`              | 自定义背景API地址                         |
| 选择本地图片     | LocalImagePickerPreference           | `local_image_picker`                | 从相册选择背景                            |
| 恢复默认背景     | ResetBackgroundImagePreference       | `reset_background_image`            | 清除自定义背景设置                          |
| 启用统计分析     | CheckBoxPreference                   | `checkbox_enable_analytics`         | 匿名使用统计，默认开启                        |
| 禁用错误提示     | CheckBoxPreference                   | `checkbox_disable_warnings`         | 串流中禁用连接错误提示，默认关闭                   |
| 性能监控图层     | CheckBoxPreference                   | `checkbox_enable_perf_overlay`      | 实时串流监控数据，默认关闭                      |
| 性能图层方向     | PerfOverlayOrientationPreference     | `list_perf_overlay_orientation`     | 水平/垂直，默认水平                         |
| 性能图层位置     | DynamicPerfOverlayPositionPreference | `list_perf_overlay_position`        | 动态位置选项，默认顶部                        |
| 性能图层显示项目   | PerfOverlayDisplayItemsPreference    | `perf_overlay_display_items`        | 多选：解码器/帧率/丢帧率/网络延时/主机延时/解码时间/带宽等   |
| 性能图层背景透明度  | SeekBarPreference                    | `seekbar_perf_overlay_bg_opacity`   | 范围 0-100%，默认 53%                   |
| 串流完毕显示延迟信息 | CheckBoxPreference                   | `checkbox_enable_post_stream_toast` | 默认关闭                               |

### 7. 音频设置 (`category_audio_settings`)

| 配置项       | 类型                 | Key                                 | 说明                                 |
| --------- | ------------------ | ----------------------------------- | ---------------------------------- |
| 环绕声设置     | ListPreference     | `list_audio_config`                 | 立体声/5.1/7.1/7.1.4(Atmos)，默认立体声     |
| 启用系统均衡器支持 | CheckBoxPreference | `checkbox_enable_audiofx`           | 允许音效工作，默认关闭                        |
| 启用空间音频    | CheckBoxPreference | `checkbox_enable_spatializer`       | Android 13+沉浸式3D音频，默认关闭            |
| 启用音频直通    | CheckBoxPreference | `checkbox_enable_audio_passthrough` | PCM/AC3/E-AC3位完美转发到AVR，默认关闭        |
| 直通编解码器    | ListPreference     | `list_audio_codec`                  | 自动/Opus/AC3/E-AC3/PCM，默认自动，依赖音频直通  |
| 直通缓冲区大小   | ListPreference     | `list_audio_passthrough_buffer`     | 低(~96ms)/普通(~160ms)/高(~320ms)，默认普通 |
| 启用音频驱动振动  | CheckBoxPreference | `checkbox_audio_vibration`          | 根据低音和音频事件振动设备，默认关闭                 |
| 振动强度      | SeekBarPreference  | `seekbar_audio_vibration_strength`  | 范围 0-200%，默认 80%，依赖音频振动            |
| 振动路由      | ListPreference     | `list_audio_vibration_mode`         | 振动传递方式，默认auto，依赖音频振动               |
| 场景模式      | ListPreference     | `list_audio_vibration_scene`        | 根据内容类型优化振动，默认0，依赖音频振动              |
| 麦克风重定向    | CheckBoxPreference | `checkbox_enable_mic`               | 实验性功能，需Sunshine 20250720+，默认关闭     |
| 麦克风传输音质   | SeekBarPreference  | `seekbar_mic_bitrate_kbps`          | 范围 32-256 kbps，默认 64，依赖麦克风         |
| 麦克风图标颜色   | ListPreference     | `list_mic_icon_color`               | 颜色方案，默认solid_white，依赖麦克风           |

### 8. 手柄设置 (`category_gamepad_settings`)

| 配置项              | 类型                 | Key                                  | 说明                        |
| ---------------- | ------------------ | ------------------------------------ | ------------------------- |
| 摇杆死区             | SeekBarPreference  | `seekbar_deadzone`                   | 范围 0-20%，默认 7%            |
| 自动检测手柄           | CheckBoxPreference | `checkbox_multi_controller`          | 禁用则视为一个手柄，默认开启            |
| 通过手柄模拟鼠标         | CheckBoxPreference | `checkbox_mouse_emulation`           | 长按开始键切换鼠标模式，默认开启          |
| 长按开始键功能          | CheckBoxPreference | `checkbox_enable_start_key_menu`     | 长按Start打开游戏菜单/切换鼠标模拟，默认开启 |
| Xbox 360/One手柄驱动 | CheckBoxPreference | `checkbox_usb_driver`                | 内置USB驱动，默认开启              |
| 覆盖安卓手柄支持         | CheckBoxPreference | `checkbox_usb_bind_all`              | 强制接管Xbox手柄，默认关闭，依赖USB驱动   |
| 摇杆控制鼠标移动         | ListPreference     | `analog_scrolling`                   | 左摇杆/右摇杆/默认，依赖手柄模拟鼠标       |
| 用设备震动模拟游戏震动      | CheckBoxPreference | `checkbox_vibrate_fallback`          | 手柄不支持震动时用设备震动模拟，默认关闭      |
| 模拟震动强度           | SeekBarPreference  | `seekbar_vibrate_fallback_strength`  | 范围 0-200%，默认 100%，依赖模拟震动  |
| 反转技能键            | CheckBoxPreference | `checkbox_flip_face_buttons`         | 调转A/B和X/Y，默认关闭            |
| 始终使用触摸板控制鼠标      | CheckBoxPreference | `checkbox_gamepad_touchpad_as_mouse` | 默认关闭                      |
| 允许使用手柄运动传感器      | CheckBoxPreference | `checkbox_gamepad_motion_sensors`    | 默认开启                      |
| 模拟手柄运动传感器支持      | CheckBoxPreference | `checkbox_gamepad_motion_fallback`   | 使用设备内置运动传感器，默认关闭          |

### 9. 输入设置 (`category_input_settings`)

| 配置项          | 类型                 | Key                                               | 说明                           |
| ------------ | ------------------ | ------------------------------------------------- | ---------------------------- |
| 将触控屏作为触控板    | CheckBoxPreference | `checkbox_touchscreen_trackpad`                   | 启用触控板模式，默认关闭                 |
| 启用触控板双击拖动    | CheckBoxPreference | `pref_enable_double_click_drag`                   | 双击并按住后拖动，默认关闭                |
| 双击时间阈值       | SeekBarPreference  | `seekbar_double_tap_time_threshold`               | 范围 25-1000ms，默认 125ms，依赖双击拖动 |
| 显示本地光标       | CheckBoxPreference | `pref_enable_local_cursor_rendering`              | 本地光标渲染，默认关闭                  |
| 特殊按键映射       | CheckBoxPreference | `checkbox_special_key_map`                        | Home→Esc等映射，默认关闭             |
| 修复鼠标中键       | CheckBoxPreference | `checkbox_mouse_middle`                           | 修复中键识别为返回键问题，默认开启            |
| 修复本地鼠标滚轮     | CheckBoxPreference | `checkbox_mouse_wheel`                            | 修复滚轮产生触屏滑动事件问题，默认开启          |
| 触控事件与显示刷新同步  | CheckBoxPreference | `checkbox_sync_touch_event_with_display`          | 默认关闭                         |
| 多点触控模式下键盘切换  | CheckBoxPreference | `checkbox_enable_keyboard_toggle_in_native_touch` | 默认开启                         |
| 轻敲手指数量       | SeekBarPreference  | `seekbar_keyboard_toggle_fingers_native_touch`    | 范围 3-10，默认 3，依赖键盘切换          |
| 启用前进后退鼠标键    | CheckBoxPreference | `checkbox_mouse_nav_buttons`                      | 默认关闭                         |
| 鼠标模式预设       | ListPreference     | `list_native_mouse_mode_preset`                   | 经典/触控板/多点触控/本地鼠标指针，默认经典      |
| 适合远程桌面的鼠标模式  | CheckBoxPreference | `checkbox_absolute_mouse_mode`                    | 默认关闭                         |
| 允许自定义键打开返回菜单 | CheckBoxPreference | `checkbox_enable_esc_menu`                        | Ctrl+Shift+Alt+键打开菜单，默认开启    |
| 返回菜单激活按键     | ListPreference     | `list_esc_menu_key`                               | 选择组合键之一，默认111(Esc)，依赖ESC菜单   |

### 10. 屏幕控制按钮设置 (`category_onscreen_controls`)

| 配置项        | 类型                         | Key                                 | 说明                      |
| ---------- | -------------------------- | ----------------------------------- | ----------------------- |
| 显示屏幕控制按钮   | CheckBoxPreference         | `checkbox_show_onscreen_controls`   | 虚拟手柄叠加层，默认关闭            |
| 启用震动       | CheckBoxPreference         | `checkbox_vibrate_osc`              | 屏幕按钮震动，默认开启，依赖OSC       |
| 只显示L3和R3   | CheckBoxPreference         | `checkbox_only_show_L3R3`           | 隐藏其他虚拟按钮，默认关闭，依赖OSC     |
| 显示Guide按钮  | CheckBoxPreference         | `checkbox_show_guide_button`        | 屏幕上显示Guide按钮，默认开启，依赖OSC |
| 竖屏模式半高     | CheckBoxPreference         | `checkbox_half_height_osc_portrait` | 竖屏下半屏显示，默认开启，依赖OSC      |
| 透明度        | SeekBarPreference          | `seekbar_osc_opacity`               | 范围 0-100%，默认 90%，依赖OSC  |
| 重置屏幕控制按钮布局 | ConfirmDeleteOscPreference | —                                   | 重置所有虚拟按钮为默认大小和位置        |

### 11. 多点触控设置 (`category_enhanced_touch`)

| 配置项          | 类型                 | Key                                     | 说明                       |
| ------------ | ------------------ | --------------------------------------- | ------------------------ |
| 打开增强型触控      | CheckBoxPreference | `checkbox_enable_enhanced_touch`        | 默认开启                     |
| 多点触控长按抖动消除区域 | SeekBarPreference  | `seekbar_flat_region_pixels`            | 范围 0-250像素，默认 0，依赖增强触控   |
| 反转增强型触控区     | CheckBoxPreference | `checkbox_enhanced_touch_on_which_side` | 默认关闭（右侧），依赖增强触控          |
| 触点灵敏度分区调控    | SeekBarPreference  | `enhanced_touch_zone_divider`           | 范围 0-100%，默认 50%，依赖增强触控  |
| 触点灵敏度        | SeekBarPreference  | `pointer_velocity_factor`               | 范围 0-500%，默认 100%，依赖增强触控 |

### 12. 悬浮球设置 (`category_float_ball`)

| 配置项    | 类型                 | Key                                   | 说明                          |
| ------ | ------------------ | ------------------------------------- | --------------------------- |
| 启用悬浮球  | CheckBoxPreference | `checkbox_enable_float_ball`          | 默认关闭                        |
| 自动隐藏延迟 | SeekBarPreference  | `seekbar_float_ball_auto_hide_delay`  | 范围 0-5000ms，默认 2000ms，依赖悬浮球 |
| 单击动作   | ListPreference     | `list_float_ball_single_click_action` | 无动作/显示虚拟键盘/显示游戏菜单，默认打开菜单    |
| 双击动作   | ListPreference     | `list_float_ball_double_click_action` | 同上，默认打开键盘                   |
| 长按动作   | ListPreference     | `list_float_ball_long_click_action`   | 同上，默认无                      |

### 13. 连接设置 (`category_connection_settings`)

| 配置项    | 类型                 | Key                         | 说明                       |
| ------ | ------------------ | --------------------------- | ------------------------ |
| 自动恢复串流 | CheckBoxPreference | `checkbox_resume_stream`    | 后台切回时自动恢复，默认关闭           |
| 不断开连接  | CheckBoxPreference | `checkbox_extreme_resume`   | 切换应用/锁屏时保持连接，默认关闭，依赖恢复串流 |
| 后台播放音频 | CheckBoxPreference | `checkbox_background_audio` | 后台时播放音频，默认关闭，依赖不断开连接     |
| 获取公网IP | CheckBoxPreference | `checkbox_enable_stun`      | STUN服务器获取公网访问方式，默认关闭     |

### 14. 王冠的功能区 (`category_crown_features`)

| 配置项     | 类型                 | Key                               | 说明                        |
| ------- | ------------------ | --------------------------------- | ------------------------- |
| 王冠的超级功能 | CheckBoxPreference | `checkbox_show_onscreen_keyboard` | 串流中自定义按键和设置保存到快速切换配置，默认关闭 |
| 导出配置    | ListPreference     | `export_super_config`             | 导出按键和串流设置配置文件             |
| 导入配置    | Preference         | `import_super_config`             | 导入配置文件                    |
| 合并配置    | ListPreference     | `merge_super_config`              | 将新配置合并到现有配置               |

### 15. 帮助 (`category_help`)

| 配置项               | 类型                    | Key                 | 说明        |
| ----------------- | --------------------- | ------------------- | --------- |
| 检查更新              | Preference            | `check_for_updates` | 检查新版本     |
| Moonlight X PC版下载 | WebLauncherPreference | —                   | 跳转PC版下载页面 |
| 隐私政策              | WebLauncherPreference | —                   | 跳转隐私政策页面  |

---

## 二、串流时游戏菜单（GameMenu）

游戏菜单在串流中按返回键、长按Start键或点击悬浮球"显示游戏菜单"动作时弹出。

### 主菜单功能项

#### 快捷按钮栏（顶部水平滚动按钮区）

| 功能      | 说明                                                                                     |
| ------- | -------------------------------------------------------------------------------------- |
| 可配置快捷按钮 | 最多6个，支持：发送Win键、切换音频、切换HDR、切换麦克风、发送睡眠、退出、Tab、Alt+Tab、Alt+F4、切换键盘、切换虚拟手柄、切换性能图层、自定义按键组合等 |
| 编辑模式    | 拖拽排序、删除按钮、添加新按钮                                                                        |
| 恢复默认    | 重置快捷按钮配置                                                                               |

#### 主功能列表

| 功能项       | 说明                         |
| --------- | -------------------------- |
| 屏幕键盘      | 切换本地虚拟键盘显示                 |
| 主机键盘      | 发送Win+Ctrl+O开启主机端键盘        |
| 切换触控模式    | 打开子菜单选择触控模式（当前模式显示在标签中）    |
| 开启/关闭平移缩放 | 切换触控板平移缩放功能                |
| 王冠功能      | 仅在王冠模式启用时显示，打开王冠功能子菜单      |
| 手柄设备选项    | 动态注入，如"启用/禁用手柄鼠标模拟"        |
| 性能监控图层    | 循环切换：关闭→悬浮→固定→关闭（状态显示在标签中） |
| 更改分辨率     | 打开分辨率选择子菜单（预设+自定义）         |
| 虚拟手柄      | 仅在OSC启用时显示，切换虚拟手柄显示        |
| 发送特殊按键    | 打开特殊按键子菜单                  |
| 断开连接      | 仅断开流连接                     |
| 断开并退出串流   | 断开并完全退出（可配置锁屏）             |

#### 功能卡片区（底部可配置卡片）

| 卡片     | 说明                                           |
| ------ | -------------------------------------------- |
| 码率调整卡片 | 分段滑块（0.5-200Mbps，5段60位置），实时调整码率，需要HEVC/AV1编码 |
| 体感助手卡片 | 陀螺仪控制（右摇杆模式/鼠标模式）、灵敏度、轴反转、激活按键               |
| 特殊按键卡片 | 显示自定义按键组合列表，点击发送按键序列                         |
| 卡片配置入口 | 弹出多选对话框，选择显示哪些卡片（码率/体感/特殊按键）                 |

### 触控模式子菜单

| 模式         | 说明                       |
| ---------- | ------------------------ |
| 增强式多点触控    | 触控屏分区域操作（增强区+原生区）        |
| 经典鼠标模式     | 传统模拟鼠标操作                 |
| 触控板模式      | 触控屏作为触控板                 |
| 触控板-双击拖动开关 | 开启/关闭触控板双击拖动功能           |
| 本地光标渲染     | 仅触控板模式下显示，开启/关闭本地光标      |
| 本地鼠标指针     | 使用系统原生鼠标指针               |
| 显示/隐藏远程鼠标  | 发送Ctrl+Alt+Shift+N切换远程鼠标 |

### 特殊按键子菜单

| 按键        | 说明                             |
| --------- | ------------------------------ |
| 已保存的自定义按键 | 从SharedPreferences加载的自定义按键组合列表 |
| 添加自定义按键   | 打开添加对话框，输入名称+键盘按键组合            |
| 删除自定义按键   | 打开删除对话框，多选删除                   |

### 王冠功能子菜单

| 功能        | 说明          |
| --------- | ----------- |
| 显示/隐藏虚拟按键 | 切换王冠虚拟按键可见性 |
| 开启/关闭触控   | 切换触控输入开关    |
| 配置王冠按键    | 进入按键配置模式    |
| 编辑模式      | 进入按键编辑模式    |
| 配置王冠功能    | 退出王冠配置模式    |

### 超级指令区

| 功能     | 说明                                                   |
| ------ | ---------------------------------------------------- |
| 超级指令列表 | 从Sunshine主机端获取的cmdList，每个指令通过`conn.sendSuperCmd()`发送 |
| 空状态    | 无超级指令时显示提示，引导用户使用Sunshine基地版添加                       |

---

## 三、性能监控图层显示项目

性能图层可显示的指标项：

| 指标             | 说明                |
| -------------- | ----------------- |
| 解码器名称          | 当前使用的视频解码器        |
| 分辨率            | 视频流分辨率            |
| 渲染帧率(Rd FPS)   | 实际渲染到屏幕的帧数        |
| 网络接收帧率(Rx FPS) | 从主机接收的视频帧数        |
| 丢包率            | 网络传输中丢失的数据包比例     |
| 网络延迟(RTT)      | 数据包往返时间，含抖动值      |
| 主机处理延迟         | 主机处理帧的最小/最大/平均时间  |
| 解码延迟           | 解码视频帧所需时间         |
| 带宽             | 实时网络传输速度          |
| 电池电量           | 设备电池状态            |
| 1% Low帧        | 第99百分位帧间隔衡量的帧率平滑性 |
| 渲染延迟           | 渲染帧到显示的延迟         |
| 月相             | 彩蛋功能，显示当日月相信息     |

---

## 四、悬浮球手势动作选项

悬浮球支持以下动作配置：

| 动作      | 说明         |
| ------- | ---------- |
| 无动作     | 不执行任何操作    |
| 显示虚拟键盘  | 切换本地虚拟键盘   |
| 显示游戏菜单  | 打开游戏内菜单    |
| 锁屏      | 锁定设备屏幕     |
| 截图      | 截取当前屏幕     |
| 切换悬浮球显隐 | 切换悬浮球自身可见性 |

---

## 五、文件索引

| 文件                                                                       | 说明                  |
| ------------------------------------------------------------------------ | ------------------- |
| `app/src/main/res/xml/preferences.xml`                                   | 设置页面完整XML定义（850行）   |
| `app/src/main/java/com/limelight/preferences/StreamSettings.kt`          | 设置页面Activity（2146行） |
| `app/src/main/java/com/limelight/preferences/PreferenceConfiguration.kt` | 配置管理类（1270行）        |
| `app/src/main/java/com/limelight/GameMenu.kt`                            | 游戏菜单（1819行）         |
| `app/src/main/java/com/limelight/BitrateCardController.kt`               | 码率调整卡片              |
| `app/src/main/java/com/limelight/GyroCardController.kt`                  | 体感控制卡片              |
| `app/src/main/java/com/limelight/PerformanceOverlayManager.kt`           | 性能监控图层（1293行）       |
| `app/src/main/java/com/limelight/FloatBallHandler.kt`                    | 悬浮球处理器              |
| `app/src/main/res/layout/activity_pc_view.xml`                           | 主页布局（含场景预设按钮）       |
