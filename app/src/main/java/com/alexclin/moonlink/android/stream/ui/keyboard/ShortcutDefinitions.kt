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
    val description: String,
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
        PresetShortcut("Ctrl+C", "复制",    listOf(VK_LCONTROL, VK_C)),
        PresetShortcut("Ctrl+V", "粘贴",    listOf(VK_LCONTROL, VK_V)),
        PresetShortcut("Ctrl+X", "剪切",    listOf(VK_LCONTROL, VK_X)),
        PresetShortcut("Ctrl+A", "全选",    listOf(VK_LCONTROL, VK_A)),
        PresetShortcut("Win",    "开始菜单", listOf(VK_LWIN)),

        // ── 第2行 ──
        PresetShortcut("Ctrl+Z",   "撤销",     listOf(VK_LCONTROL, VK_Z)),
        PresetShortcut("Ctrl+S",   "保存",     listOf(VK_LCONTROL, VK_S)),
        PresetShortcut("Win+D",    "显示桌面",  listOf(VK_LWIN, VK_D)),
        PresetShortcut("Win+Tab",  "切换窗口",  listOf(VK_LWIN, VK_TAB)),
        PresetShortcut("Win+L",    "锁定屏幕",  listOf(VK_LWIN, VK_L)),
        PresetShortcut("Alt+Tab",  "应用切换",  listOf(VK_MENU, VK_TAB)),

        // ── 第3行（滚动可见） ──
        PresetShortcut("Alt+F4",          "关闭窗口",   listOf(VK_MENU, VK_F4)),
        PresetShortcut("Ctrl+Shift+Esc",  "任务管理器",  listOf(VK_LCONTROL, VK_LSHIFT, VK_ESCAPE)),
    )
}
