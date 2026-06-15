package com.alexclin.moonlink.stream.ui.keyboard

import android.graphics.Rect
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.common.CustomKeyRepository
import com.limelight.binding.input.advance_setting.KeyboardUIController

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
 * │  虚拟键盘 → QWERTY            │
 * │  主机键盘 → TabBar 消失 + 键盘 │
 * │                              │
 * └──────────────────────────────┘
 * ```
 *
 * @param engine 串流引擎
 * @param onClose 关闭面板回调（回到竖条状态）
 * @param onCloseToHidden 关闭面板回调（回到悬浮按钮状态，用于主机键盘模式）
 */
@Composable
fun KeyboardSubPanel(
    engine: StreamEngine,
    onClose: () -> Unit = {},
    onCloseToHidden: () -> Unit = onClose,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    // ── Tab 状态 ──
    var selectedTab by remember { mutableIntStateOf(0) }

    // ── 缓存键盘高度（px） ──
    var cachedKeyboardHeightPx by remember { mutableIntStateOf(0) }

    // 通过 ViewTreeObserver 监听键盘高度（兼容所有 API 级别）
    DisposableEffect(view) {
        val rootView = view.rootView
        val rect = Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keyboardHeight = screenHeight - rect.bottom
            if (keyboardHeight > 0 && keyboardHeight != cachedKeyboardHeightPx) {
                cachedKeyboardHeightPx = keyboardHeight
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

    // ── 主布局 ──
    run {
        // ── 正常模式：TabBar（顶部） + 内容区（高度 = 系统键盘高度） ──
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── TabBar（顶部固定） ──
            KeyboardTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab == 3) {
                        // 主机键盘：发送快捷键唤起远端屏幕键盘，然后关闭面板
                        // Windows: Win+Ctrl+O 唤起系统屏幕键盘
                        // macOS: Cmd+F5 唤起 VoiceOver（含屏幕键盘）
                        engine.sendToggleHostKeyboard()
                        onCloseToHidden()
                    } else {
                        selectedTab = tab
                    }
                },
                onClose = onClose,
            )

            // ── 内容区（高度 = 系统键盘高度） ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(effectiveHeightDp)
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
                    2 -> VirtualKeyboardTabContent(
                        engine = engine,
                        cachedKeyboardHeightDp = effectiveHeightDp,
                        onHideKeyboard = { hideKeyboard() },
                    )
                }
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

private val TAB_LABELS = listOf("输入法", "快捷键", "虚拟键盘", "主机键盘")

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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TAB_LABELS.forEachIndexed { index, label ->
                val isSelected = selectedTab == index
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
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
 * 进入时弹出系统键盘，内容区显示提示文字。
 */
@Composable
private fun ImeTabContent(
    engine: StreamEngine,
) {
    LaunchedEffect(Unit) {
        engine.toggleKeyboard()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "使用系统输入法输入文字",
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
    customKeys: List<com.alexclin.moonlink.stream.ui.common.CustomKeyData>,
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
                    label = "添加",
                    onClick = onAddCustomKey,
                )
            }
            if (customKeys.isNotEmpty()) {
                item(span = { GridItemSpan(1) }) {
                    ShortcutActionButton(
                        icon = Icons.Default.Close,
                        label = "删除",
                        onClick = onDeleteCustomKey,
                    )
                }
            }
            item(span = { GridItemSpan(1) }) {
                ShortcutActionButton(
                    icon = Icons.Default.Refresh,
                    label = "重置",
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
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isEditMode) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "编辑",
                modifier = Modifier.size(20.dp),
                tint = if (isEditMode) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isEditMode) "完成" else "编辑",
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
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
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
                text = preset.description,
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
    customKey: com.alexclin.moonlink.stream.ui.common.CustomKeyData,
    isEditMode: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(enabled = !isEditMode, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isEditMode) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
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
                    contentDescription = "删除",
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
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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

// ────────────────────────────────────────────────────────────────────────────
// Tab 2: 虚拟键盘
// ────────────────────────────────────────────────────────────────────────────

/**
 * 虚拟键盘标签内容。
 *
 * 使用 AndroidView 包裹旧 [KeyboardUIController]，高度 = 缓存的系统键盘高度。
 */
@Composable
private fun VirtualKeyboardTabContent(
    engine: StreamEngine,
    cachedKeyboardHeightDp: Dp,
    onHideKeyboard: () -> Unit,
) {
    val context = LocalContext.current
    val bridge = remember { VirtualKeyboardBridge(engine) }

    LaunchedEffect(Unit) {
        onHideKeyboard()
    }

    AndroidView(
        factory = { ctx ->
            val container = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            KeyboardUIController(container, bridge, ctx)
            container
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = cachedKeyboardHeightDp),
        update = { /* KeyboardUIController 内部管理视图状态 */ },
    )
}
