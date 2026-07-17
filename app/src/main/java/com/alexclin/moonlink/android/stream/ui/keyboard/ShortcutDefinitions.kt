package com.alexclin.moonlink.android.stream.ui.keyboard

/**
 * 预置快捷键定义（UI 中不可删除的固定按钮）。
 *
 * 键码使用 Windows VK_* 格式（与 [com.alexclin.moonlink.android.stream.engine.StreamEngine.sendKeys] 一致）。
 *
 * @param label 快捷键显示名（如 "Ctrl+C"）
 * @param descriptionResId 描述字符串资源 ID
 * @param keys 按键序列（Windows VK 码，修饰键在前）
 */
data class PresetShortcut(
    val label: String,
    val descriptionResId: Int,
    val keys: List<Short>,
)

/**
 * 内置快捷键定义（首次启动初始化到 SP，用户可删除）。
 *
 * 与 raw JSON 中的格式一致，但 description 通过字符串资源支持多语言。
 *
 * @param name 快捷键显示名（如 "F11"）
 * @param descriptionResId 描述字符串资源 ID
 * @param keys 按键序列（Windows VK 码）
 */
data class BuiltinCustomKey(
    val name: String,
    val descriptionResId: Int,
    val keys: List<Short>,
)

object ShortcutDefinitions {

    // Windows VK_* 键码常量（与 KeyboardTranslator 一致）
    private val VK_LCONTROL = 162.toShort()
    private val VK_LSHIFT = 160.toShort()
    private val VK_MENU = 18.toShort()      // Alt
    private val VK_LWIN = 91.toShort()
    private val VK_TAB = 9.toShort()
    private val VK_ESCAPE = 27.toShort()
    private val VK_F4 = 115.toShort()
    private val VK_SPACE = 32.toShort()

    // 字母键（Windows VK = ASCII 大写字母）
    private val VK_A = 65.toShort()
    private val VK_C = 67.toShort()
    private val VK_D = 68.toShort()
    private val VK_L = 76.toShort()
    private val VK_S = 83.toShort()
    private val VK_V = 86.toShort()
    private val VK_X = 88.toShort()
    private val VK_Z = 90.toShort()

    /**
     * 12 个预置快捷键，横屏 6×2 排列。
     *
     * 布局：
     * ```
     * 第1行: 编辑  Ctrl+C  Ctrl+V  Ctrl+X  Ctrl+A  Win
     * 第2行: Ctrl+Z Ctrl+S Win+D  Win+Tab Win+L  Alt+Tab
     * ```
     * 编辑按钮由 UI 层单独渲染，不在此列表中。
     */
    val presets: List<PresetShortcut> = listOf(
        // ── 第1行（编辑按钮由 UI 层处理） ──
        PresetShortcut("Ctrl+C", com.alexclin.moonlink.android.R.string.shortcut_desc_copy,    listOf(VK_LCONTROL, VK_C)),
        PresetShortcut("Ctrl+V", com.alexclin.moonlink.android.R.string.shortcut_desc_paste,    listOf(VK_LCONTROL, VK_V)),
        PresetShortcut("Ctrl+X", com.alexclin.moonlink.android.R.string.shortcut_desc_cut,    listOf(VK_LCONTROL, VK_X)),
        PresetShortcut("Ctrl+A", com.alexclin.moonlink.android.R.string.shortcut_desc_select_all,    listOf(VK_LCONTROL, VK_A)),
        PresetShortcut("Win",    com.alexclin.moonlink.android.R.string.shortcut_desc_start_menu, listOf(VK_LWIN)),

        // ── Row 2 ──
        PresetShortcut("Ctrl+Z",   com.alexclin.moonlink.android.R.string.shortcut_desc_undo,     listOf(VK_LCONTROL, VK_Z)),
        PresetShortcut("Ctrl+S",   com.alexclin.moonlink.android.R.string.editor_save,     listOf(VK_LCONTROL, VK_S)),
        PresetShortcut("Win+D",    com.alexclin.moonlink.android.R.string.shortcut_desc_show_desktop,  listOf(VK_LWIN, VK_D)),
        PresetShortcut("Win+Tab",  com.alexclin.moonlink.android.R.string.shortcut_desc_switch_window,  listOf(VK_LWIN, VK_TAB)),
        PresetShortcut("Win+L",    com.alexclin.moonlink.android.R.string.shortcut_desc_lock_screen,  listOf(VK_LWIN, VK_L)),
        PresetShortcut("Alt+Tab",  com.alexclin.moonlink.android.R.string.shortcut_desc_switch_app,  listOf(VK_MENU, VK_TAB)),

        // ── Row 3 (scrollable) ──
        PresetShortcut("Alt+F4",          com.alexclin.moonlink.android.R.string.shortcut_desc_close_window,   listOf(VK_MENU, VK_F4)),
        PresetShortcut("Ctrl+Shift+Esc",  com.alexclin.moonlink.android.R.string.shortcut_desc_task_manager,  listOf(VK_LCONTROL, VK_LSHIFT, VK_ESCAPE)),
        PresetShortcut("Ctrl+Space",      com.alexclin.moonlink.android.R.string.shortcut_desc_switch_ime,    listOf(VK_LCONTROL, VK_SPACE)),
    )

    /**
     * 内置快捷键列表（首次启动时初始化到 SharedPreferences，用户可删除）。
     *
     * 对应原 raw/default_special_keys.json 中的 6 条定义。
     */
    val builtinCustomKeys: List<BuiltinCustomKey> = listOf(
        BuiltinCustomKey("F11",        com.alexclin.moonlink.android.R.string.custom_key_desc_fullscreen,     listOf(0x7A.toShort())),
        BuiltinCustomKey("Ctrl+V",     com.alexclin.moonlink.android.R.string.custom_key_desc_paste,          listOf(0xA2.toShort(), 0x56.toShort())),
        BuiltinCustomKey("Win+D",      com.alexclin.moonlink.android.R.string.custom_key_desc_desktop,        listOf(0x5B.toShort(), 0x44.toShort())),
        BuiltinCustomKey("Win+G",      com.alexclin.moonlink.android.R.string.custom_key_desc_xbox_game_bar,  listOf(0x5B.toShort(), 0x47.toShort())),
        BuiltinCustomKey("Alt+Home",   com.alexclin.moonlink.android.R.string.custom_key_desc_perf_monitor,   listOf(0x12.toShort(), 0x24.toShort())),
        BuiltinCustomKey("Shift+Tab",  com.alexclin.moonlink.android.R.string.custom_key_desc_steam_overlay,  listOf(0xA0.toShort(), 0x09.toShort())),
    )
}
