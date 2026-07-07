package com.alexclin.moonlink.android.stream.ui.keyboard

/**
 * 预置快捷键定义。
 *
 * 键码使用 Windows VK_* 格式（与 [com.alexclin.moonlink.android.stream.engine.StreamEngine.sendKeys] 一致）。
 *
 * @param label 快捷键显示名（如 "Ctrl+C"）
 * @param description 中文描述（如 "复制"）
 * @param keys 按键序列（Windows VK 码，修饰键在前）
 */
data class PresetShortcut(
    val label: String,
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
        PresetShortcut("Ctrl+V", com.alexclin.moonlink.android.R.string.editor_toolbar_paste,    listOf(VK_LCONTROL, VK_V)),
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
    )
}
