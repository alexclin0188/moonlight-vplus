package com.alexclin.moonlink.android.stream.ui.keyboard

import android.graphics.Rect
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.common.CustomKeyData
import com.alexclin.moonlink.android.stream.ui.common.CustomKeyRepository
import com.alexclin.moonlink.android.stream.ui.editor.EditorDialog
import androidx.compose.ui.res.stringResource

/**
 * 键盘子面板 — 四标签页重构版。
 *
 * 布局结构：
 * ```
 * ┌──────────────────────────────┐
 * │          串流画面             │
 * ├──────────────────────────────┤
 * │ 输入法│快捷键│虚拟键盘│主机键盘│ ✕  │  ← TabBar（顶部固定）
 * ├──────────────────────────────┤
 * │                              │
 * │  输入法 → 系统键盘             │  ← 内容区（高度 = 系统键盘高度）
 * │  快捷键 → 6×2 网格            │
 * │  虚拟键盘 → Compose 虚拟键盘   │
 * │  主机键盘 → TabBar 消失 + 键盘 │
 * │                              │
 * └──────────────────────────────┘
 * ```
 *
 * @param engine 串流引擎
 * @param onClose 关闭面板回调（回到竖条状态）
 * @param onCloseToHidden 关闭面板回调（回到悬浮按钮状态，用于主机键盘模式）
 * @param onTabChanged 标签切换回调（用于父组件适配背景，如虚拟键盘标签时变透明）
 */
@Composable
fun KeyboardSubPanel(
    engine: StreamEngine,
    initialTab: Int = 0,
    onClose: () -> Unit = {},
    onCloseToHidden: () -> Unit = onClose,
    onTabChanged: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    // ── Tab 状态 ──
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // ── 同步 tab 变化给父组件 ──
    LaunchedEffect(selectedTab) {
        onTabChanged(selectedTab)
    }

    // ── 缓存键盘高度（px） ──
    var cachedKeyboardHeightPx by remember { mutableIntStateOf(0) }

    // 通过 ViewTreeObserver 监听键盘高度变化。
    // 键盘弹出：缓存高度用于内容区尺寸；键盘收起且当前在输入法标签：自动隐藏面板。
    DisposableEffect(view) {
        val rootView = view.rootView
        val rect = Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keyboardHeight = screenHeight - rect.bottom
            if (keyboardHeight > 0 && keyboardHeight != cachedKeyboardHeightPx) {
                // 只在键盘高度增长时（弹出过程）更新缓存，
                // 避免键盘收起过程中的中间态值覆盖正确的缓存高度
                if (keyboardHeight > cachedKeyboardHeightPx) {
                    cachedKeyboardHeightPx = keyboardHeight
                }
            } else if (keyboardHeight == 0 && cachedKeyboardHeightPx > 0 && selectedTab == 0) {
                // 系统键盘被手动收起且当前仍在输入法标签 → 隐藏面板
                cachedKeyboardHeightPx = 0
                onClose()
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    val effectiveHeightDp = with(density) {
        if (cachedKeyboardHeightPx > 0) cachedKeyboardHeightPx.toDp()
        else 240.dp
    }

    // ── 隐藏系统键盘 ──
    fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // ── 自定义按键列表状态（快捷键标签使用） ──
    var customKeys by remember { mutableStateOf(CustomKeyRepository.loadAll(context)) }
    var isEditMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTargetKey by remember { mutableStateOf<CustomKeyData?>(null) }

    // ── 虚拟键盘 Bridge（标签 2 使用） ──
    val keyboardBridge = remember { VirtualKeyboardBridge(engine) }

    // ── 主布局：TabBar（顶部，虚拟键盘标签下隐藏） + 内容区（高度 = 系统键盘高度） ──
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
            // ── TabBar（顶部固定，虚拟键盘标签下隐藏） ──
            if (selectedTab != 2) {
                KeyboardTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    // 先更新 selectedTab 防止键盘高度监听器在 hideKeyboard 过程中误触发 onClose
                    selectedTab = tab
                    if (tab != 0) {
                        hideKeyboard()
                    }
                    if (tab == 3) {
                        engine.sendToggleHostKeyboard()
                        onCloseToHidden()
                    }
                },
                onClose = onClose,
                )
            }

            // ── 内容区 ──
            // 虚拟键盘标签：填满全屏以支持 Mini 键盘全屏拖动，底部对齐 Main/Num
            // 其他标签：使用系统键盘高度
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (selectedTab == 2) Modifier.fillMaxHeight()
                        else Modifier.height(effectiveHeightDp)
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                when (selectedTab) {
                    0 -> ImeTabContent(engine = engine)
                    1 -> ShortcutsTabContent(
                        engine = engine,
                        customKeys = customKeys,
                        isEditMode = isEditMode,
                        onToggleEditMode = { isEditMode = !isEditMode },
                        onAddCustomKey = { showAddDialog = true },
                        onDeleteCustomKey = { key -> deleteTargetKey = key },
                        onResetCustomKeys = {
                            CustomKeyRepository.resetAll(context)
                            customKeys = CustomKeyRepository.loadAll(context)
                            isEditMode = false
                        },
                        onHideKeyboard = { hideKeyboard() },
                    )
                    2 -> ComposeKeyboardController(
                        bridge = keyboardBridge,
                        onHide = onClose,
                        maxHeightDp = effectiveHeightDp,
                        onSwitchToTab = { tab ->
                            selectedTab = tab
                            if (tab == 0) {
                                // 切换到系统输入法标签时，等待下一帧请求焦点
                            }
                        },
                    )

                }
            }
        }

    // ── 弹窗 ──
    if (showAddDialog) {
        AddCustomKeyDialog(
            onDismiss = { showAddDialog = false },
            onSaved = {
                customKeys = CustomKeyRepository.loadAll(context)
                showAddDialog = false
            },
        )
    }

    deleteTargetKey?.let { key ->
        EditorDialog(
            title = stringResource(R.string.customkey_title_delete_single),
            onDismiss = { deleteTargetKey = null },
            onCancel = { deleteTargetKey = null },
            onSave = {
                CustomKeyRepository.delete(context, listOf(key.name))
                customKeys = CustomKeyRepository.loadAll(context)
                deleteTargetKey = null
            },
            saveText = stringResource(R.string.dialog_button_delete),
            modifier = Modifier.fillMaxWidth(0.4f),
        ) {
            Text(
                text = key.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
            if (key.description.isNotEmpty()) {
                Text(
                    text = key.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// TabBar
// ────────────────────────────────────────────────────────────────────────────

/**
 * 底部固定 TabBar。
 */
@Composable
private fun KeyboardTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tab 标签等宽均分
            Row(modifier = Modifier.weight(1f)) {
                val tabLabels = listOf(
                    stringResource(R.string.keyboard_tab_ime),
                    stringResource(R.string.keyboard_tab_shortcuts),
                    stringResource(R.string.keyboard_tab_virtual),
                    stringResource(R.string.keyboard_tab_host),
                )
                tabLabels.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.wrapContentWidth().height(24.dp).padding(10.dp,0.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Tab 0: 输入法
// ────────────────────────────────────────────────────────────────────────────

/**
 * 输入法标签内容。
 *
 * 包含一个不可见的 [BasicTextField]，自动获取 IME 焦点。
 * 用户通过系统输入法输入的文本会通过 [StreamEngine.sendUtf8Text] 发送到远程主机，
 * 避开键码映射（Android → Windows VK 转换），直接发送 Unicode 文本。
 */
@Composable
private fun ImeTabContent(
    engine: StreamEngine,
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(Unit) {
        // 请求焦点以弹出系统 IME 键盘
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // ── 不可见的文本输入框（接收 IME 输入） ──
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val oldText = textFieldValue.text
                val newText = newValue.text

                // 计算新增的文本（用户输入的字符）
                if (newText.length > oldText.length) {
                    val addedText = newText.substring(oldText.length)
                    engine.sendUtf8Text(addedText)
                }

                // 更新状态
                textFieldValue = newValue

                // 自动清空：保持输入框为空，下次输入仍能检测到新增文本
                if (newText.isNotEmpty()) {
                    textFieldValue = TextFieldValue("")
                }
            },
            modifier = Modifier
                .width(1.dp)    // 宽度极小但 > 0，确保可聚焦
                .height(1.dp)
                .focusRequester(focusRequester),
            singleLine = false,  // 允许检测 Enter（\n），IME 不会自动关闭键盘
            textStyle = TextStyle(
                color = Color.Transparent,
                fontSize = 1.sp,
            ),
            cursorBrush = SolidColor(Color.Transparent),
        )

        // ── 提示文字 ──
        Text(
            text = stringResource(R.string.keyboard_ime_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Tab 1: 快捷键
// ────────────────────────────────────────────────────────────────────────────

/**
 * 快捷键标签内容。
 *
 * - 6 列网格，第一项为编辑按钮
 * - 预置快捷键 12 个（不可删除）
 * - 自定义快捷键（编辑模式下可删除）
 * - 编辑模式：重置排序 + 添加自定义
 */
@Composable
private fun ShortcutsTabContent(
    engine: StreamEngine,
    customKeys: List<com.alexclin.moonlink.android.stream.ui.common.CustomKeyData>,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onAddCustomKey: () -> Unit,
    onDeleteCustomKey: (com.alexclin.moonlink.android.stream.ui.common.CustomKeyData) -> Unit,
    onResetCustomKeys: () -> Unit,
    onHideKeyboard: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onHideKeyboard()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ── 第一项：编辑按钮 ──
        item(span = { GridItemSpan(1) }) {
            ShortcutEditButton(
                isEditMode = isEditMode,
                onClick = onToggleEditMode,
            )
        }

        // ── 预置快捷键（不可删除） ──
        items(ShortcutDefinitions.presets) { preset ->
            ShortcutButton(
                label = preset.label,
                description = stringResource(preset.descriptionResId),
                onClick = { engine.sendKeys(preset.keys.toShortArray()) },
            )
        }

        // ── 自定义快捷键（编辑模式下点按弹出删除确认） ──
        itemsIndexed(customKeys) { _, customKey ->
            ShortcutButton(
                label = customKey.name,
                description = customKey.description.ifEmpty { null },
                onClick = {
                    if (isEditMode) onDeleteCustomKey(customKey)
                    else engine.sendKeys(customKey.keys.toShortArray())
                },
                onDeleteClick = if (isEditMode) ({ onDeleteCustomKey(customKey) }) else null,
                vkCodes = customKey.keys,
            )
        }

        // ── 编辑模式下的操作按钮 ──
        if (isEditMode) {
            item(span = { GridItemSpan(1) }) {
                ShortcutActionButton(
                    icon = Icons.Default.Add,
                    label = stringResource(R.string.btn_add),
                    onClick = onAddCustomKey,
                )
            }
            item(span = { GridItemSpan(1) }) {
                ShortcutActionButton(
                    icon = Icons.Default.Refresh,
                    label = stringResource(R.string.keyboard_reset),
                    onClick = onResetCustomKeys,
                )
            }
        }
    }
}

/**
 * 编辑按钮（网格第一项）。
 */
@Composable
private fun ShortcutEditButton(
    isEditMode: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isEditMode) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.keyboard_edit_mode),
                modifier = Modifier.size(20.dp),
                tint = if (isEditMode) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isEditMode) stringResource(R.string.keyboard_done_mode) else stringResource(R.string.keyboard_edit_mode),
                style = MaterialTheme.typography.labelSmall,
                color = if (isEditMode) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 统一快捷键按钮 — 同时支持预置快捷键和用户自定义快捷键。
 *
 * 第一行展示标签（键名），第二行展示功能说明。
 * 当 [onDeleteClick] 不为 null 时显示 X 删除图标，点按弹出删除确认。
 *
 * @param label 第一行显示的键名（如 "Ctrl+C"）
 * @param description 第二行显示的功能说明（为 null 时显示键码回退）
 * @param onClick 点按时回调（预设快捷键 / 非编辑模式下的自定义快捷键）
 * @param onDeleteClick 传非 null 时显示 X 图标，点按触发删除确认
 * @param vkCodes 自定义快捷键的 VK 码列表（description 为 null 时显示键码回退用）
 */
@Composable
private fun ShortcutButton(
    label: String,
    description: String?,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    vkCodes: List<Short> = emptyList(),
) {
    val hasDelete = onDeleteClick != null
    Surface(
        modifier = Modifier
            .heightIn(min = 46.dp)
            .clickable(onClick = if (hasDelete) onDeleteClick!! else onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (hasDelete) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 第一行：键名
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 第二行：功能说明或键码回退
            val descText = when {
                description != null -> description
                vkCodes.isNotEmpty() -> vkCodes.joinToString("+") { getKeyLabelForVkCode(it) }
                else -> ""
            }
            if (descText.isNotEmpty()) {
                Text(
                    text = descText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasDelete) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // 删除图标
            if (hasDelete) {
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.editor_content_desc_delete),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 编辑模式下的操作按钮（添加/删除/重置）。
 */
@Composable
private fun ShortcutActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  VK 码 → 显示名辅助
// ════════════════════════════════════════════════════════════════════════════

/**
 * 将 Windows VK 码转换为可读的键名标签。
 * 用于无描述的自定义快捷键按钮上显示键名（如 "Ctrl+Shift+Esc"）。
 */
private fun getKeyLabelForVkCode(vkCode: Short): String {
    return when (val code = vkCode.toInt() and 0xFF) {
        8 -> "⌫"
        9 -> "Tab"
        12 -> "Clear"
        13 -> "Enter"
        18 -> "Alt"
        19 -> "Pause"
        20 -> "Caps"
        27 -> "Esc"
        32 -> "Space"
        33 -> "PgUp"
        34 -> "PgDn"
        35 -> "End"
        36 -> "Home"
        37 -> "←"
        38 -> "↑"
        39 -> "→"
        40 -> "↓"
        44 -> "PrtSc"
        45 -> "Ins"
        46 -> "Del"
        47 -> "/"
        59 -> ";"
        61 -> "="
        91 -> "Win"
        92 -> "RWin"
        93 -> "Menu"
        144 -> "NumLk"
        145 -> "ScrLk"
        160 -> "Shift"
        161 -> "RShift"
        162 -> "Ctrl"
        163 -> "RCtrl"
        164 -> "LAlt"
        165 -> "RAlt"
        192 -> "`"
        220 -> "\\"
        221 -> "]"
        222 -> "'"
        // 字母 A-Z (VK_A=65..VK_Z=90)
        in 65..90 -> "${('A' + (code - 65)).toChar()}"
        // 数字 0-9 (VK_0=48..VK_9=57)
        in 48..57 -> "${(code - 48).toInt()}"
        // 小键盘 (VK_NUMPAD0=96..VK_NUMPAD9=105)
        in 96..105 -> "Num${(code - 96)}"
        // 功能键 F1-F12 (VK_F1=112..VK_F12=123)
        in 112..123 -> "F${code - 111}"
        // 其他特殊键（无对应 VK 常量的 OEM 码）
        0x6A -> "*"
        0x6B -> "+"
        0x6D -> "-"
        0x6E -> "."
        0x6F -> "/"
        0xBA -> ";"
        0xBB -> "="
        0xBC -> ","
        0xBD -> "-"
        0xBE -> "."
        0xBF -> "/"
        0xDB -> "["
        else -> "0x${code.toString(16).uppercase()}"
    }
}
