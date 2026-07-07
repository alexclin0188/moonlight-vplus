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
import com.alexclin.moonlink.android.stream.ui.common.CustomKeyRepository
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
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                        onDeleteCustomKey = { showDeleteDialog = true },
                        onResetCustomKeys = {
                            val namesToDelete = customKeys.map { it.name }
                            CustomKeyRepository.delete(context, namesToDelete)
                            customKeys = emptyList()
                            isEditMode = false
                        },
                        onHideKeyboard = { hideKeyboard() },
                    )
                    2 -> ComposeKeyboardController(
                        bridge = keyboardBridge,
                        onHide = onClose,
                        maxHeightDp = effectiveHeightDp,
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

    if (showDeleteDialog) {
        DeleteCustomKeyDialog(
            onDismiss = { showDeleteDialog = false },
            onDeleted = {
                customKeys = CustomKeyRepository.loadAll(context)
                showDeleteDialog = false
            },
        )
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
    onDeleteCustomKey: () -> Unit,
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
            PresetShortcutButton(
                preset = preset,
                onClick = {
                    engine.sendKeys(preset.keys.toShortArray())
                },
            )
        }

        // ── 自定义快捷键（编辑模式下可删除） ──
        itemsIndexed(customKeys) { _, customKey ->
            CustomShortcutButton(
                customKey = customKey,
                isEditMode = isEditMode,
                onClick = {
                    engine.sendKeys(customKey.keys.toShortArray())
                },
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
            if (customKeys.isNotEmpty()) {
                item(span = { GridItemSpan(1) }) {
                    ShortcutActionButton(
                        icon = Icons.Default.Close,
                        label = stringResource(R.string.editor_content_desc_delete),
                        onClick = onDeleteCustomKey,
                    )
                }
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
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isEditMode) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
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
 * 预置快捷键按钮。
 */
@Composable
private fun PresetShortcutButton(
    preset: PresetShortcut,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(preset.descriptionResId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 自定义快捷键按钮。
 */
@Composable
private fun CustomShortcutButton(
    customKey: com.alexclin.moonlink.android.stream.ui.common.CustomKeyData,
    isEditMode: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(enabled = !isEditMode, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isEditMode) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = customKey.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            if (isEditMode) {
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


