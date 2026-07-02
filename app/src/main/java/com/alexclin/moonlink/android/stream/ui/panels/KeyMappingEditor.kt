package com.alexclin.moonlink.android.stream.ui.panels

import android.app.Activity
import android.content.ContentValues
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.panels.isSchemeNameDuplicate
import com.alexclin.moonlink.android.stream.ui.editor.CanvasCallbacks
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerDialog
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerItem
import com.alexclin.moonlink.android.stream.ui.editor.ComboKeyEditorDialog
import com.alexclin.moonlink.android.stream.ui.editor.EditorCanvas
import com.alexclin.moonlink.android.stream.ui.editor.EditorClipboard
import com.alexclin.moonlink.android.stream.ui.editor.EditorElement
import com.alexclin.moonlink.android.stream.ui.editor.EditorPropertiesPanel
import com.alexclin.moonlink.android.stream.ui.editor.EditorState
import com.alexclin.moonlink.android.stream.ui.editor.ElementType
import com.alexclin.moonlink.android.stream.ui.editor.TypeSpecificEditorDialog
import com.alexclin.moonlink.android.stream.ui.editor.getKeyLabelByValue
import com.alexclin.moonlink.android.stream.ui.editor.snapToGrid
import com.alexclin.moonlink.android.stream.ui.editor.toContentValues
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import kotlin.math.roundToInt

private const val MAX_SCREEN_PX = 5000
private const val GRID_MIN = 0
private const val GRID_MAX = 200
private const val GRID_MIN_ACTIVE = 20

/**
 * 退出编辑器时的处理逻辑（提取为函数以避免 lambda 内 return 语法问题）。
 * 新建模式：校验名称 → 创建方案 → 保存元素 → 退出。
 * 编辑模式：直接关闭。
 */
private fun doExit(
    context: android.content.Context,
    isNewScheme: Boolean,
    schemeName: String,
    db: SuperConfigDatabaseHelper,
    editorState: EditorState,
    prefs: android.content.SharedPreferences,
    engine: StreamEngine,
    onClose: () -> Unit,
) {
    if (!isNewScheme) {
        // 编辑模式：退出时刷新覆盖层
        engine.reloadOverlay()
        onClose()
        return
    }

    val name = schemeName.trim()
    if (name.isEmpty()) {
        ToastUtil.show(context, "方案名称不能为空", Toast.LENGTH_SHORT)
        return
    }

    // 校验名称不重复
    if (isSchemeNameDuplicate(context, name)) {
        ToastUtil.show(context, "已存在同名方案「$name」，请修改名称", Toast.LENGTH_SHORT)
        return
    }

    // 创建方案
    try {
        val newId = System.currentTimeMillis()
        // 创建 config 记录
        val configCv = ContentValues()
        configCv.put(PageConfigController.COLUMN_LONG_CONFIG_ID, newId)
        configCv.put(PageConfigController.COLUMN_STRING_CONFIG_NAME, name)
        configCv.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_ENABLE, "true")
        configCv.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, "true")
        db.insertConfig(configCv)

        // 迁移所有元素从 -1 到新 configId
        val currentElements = editorState.loadElements()
        db.deleteConfig(-1L) // 删除旧元素
        var eid = System.currentTimeMillis() + 1
        for (el in currentElements) {
            val cv = el.copy(configId = newId, elementId = eid).toContentValues()
            db.insertElement(cv)
            eid++
        }

        // 设为当前方案
        prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, newId).apply()
        engine.setKeyMappingEnabled(true)
        engine.reloadOverlay()
        ToastUtil.show(context, "方案「$name」已创建", Toast.LENGTH_SHORT)
        onClose()
    } catch (e: Exception) {
        ToastUtil.show(context, "创建失败: ${e.message}", Toast.LENGTH_SHORT)
    }
}

// ════════════════════════════════════════════════════════════
//  全屏编辑器（路线 A — 纯 Compose）
// ════════════════════════════════════════════════════════════

@Composable
fun KeyMappingEditor(
    engine: StreamEngine,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var currentConfigId by remember { mutableStateOf(prefs.getLong(StreamEngine.PREF_CURRENT_CONFIG_ID, 0L)) }
    val isNewScheme = currentConfigId == -1L
    val db = remember { SuperConfigDatabaseHelper(context) }
    val editorState = remember(currentConfigId) { EditorState(db, currentConfigId) }

    // ── 状态 ──
    var elements by remember { mutableStateOf<List<EditorElement>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pressedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var schemeName by remember {
        mutableStateOf(
            if (isNewScheme) {
                // 生成"我的按键方案+数字"名称，自动取下一个可用编号
                val existingNames = loadAllSchemeNames(context)
                val prefix = "我的按键方案"
                val maxNum = existingNames.mapNotNull { name ->
                    if (name.startsWith(prefix)) {
                        name.removePrefix(prefix).toIntOrNull()
                    } else null
                }.maxOrNull() ?: 0
                "$prefix${maxNum + 1}"
            } else "按键方案"
        )
    }
    var isEditingName by remember { mutableStateOf(false) }
    var gridWidth by remember { mutableIntStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showElementList by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    // 组合键编辑弹窗
    var showComboKeyEditor by remember { mutableStateOf(false) }
    // 画布尺寸缓存（用于自动居中）
    var canvasWidthPx by remember { mutableIntStateOf(1080) }
    var canvasHeightPx by remember { mutableIntStateOf(1920) }
    // 跨方案剪贴板状态（用于刷新粘贴按钮的可见性）
    var clipboardHasData by remember { mutableStateOf(EditorClipboard.hasData) }
    // 网格滑块展开/收起（由 EditorToolbar 控制，canvasTap 也可收起）
    var showGridSlider by remember { mutableStateOf(false) }
    // 全屏颜色编辑对话框
    var showColorEditor by remember { mutableStateOf(false) }
    var pendingColorEditorElement by remember { mutableStateOf<EditorElement?>(null) }
    // 全屏类型专属属性编辑对话框
    var showTypeSpecificEditor by remember { mutableStateOf(false) }
    var pendingTypeSpecificEditorElement by remember { mutableStateOf<EditorElement?>(null) }

    // ── 网格吸附辅助 ──
    val gridCellSize = if (gridWidth > 1 && canvasWidthPx > 0) canvasWidthPx / gridWidth else 0
    val focusManager = LocalFocusManager.current

    // ── 加载 ──
    fun reloadElements() {
        elements = editorState.loadElements()
        schemeName = editorState.getConfigName()
        isLoading = false
    }
    LaunchedEffect(currentConfigId) { reloadElements() }

    // ── 保存方案名 ──
    fun saveSchemeName(name: String) {
        val cv = ContentValues().apply { put(PageConfigController.COLUMN_STRING_CONFIG_NAME, name) }
        db.updateConfig(currentConfigId, cv)
        schemeName = name
    }

    // ── 复制元素（同方案内快速复制） ──
    fun duplicateSelected() {
        val src = elements.find { it.elementId in selectedIds } ?: return
        val newEl = src.copy(
            elementId = System.currentTimeMillis(),
            centralX = (src.centralX + 30).coerceAtMost(MAX_SCREEN_PX),
            centralY = (src.centralY + 30).coerceAtMost(MAX_SCREEN_PX),
            layer = (elements.maxOfOrNull { it.layer } ?: 50) + 1,
        )
        editorState.addElement(newEl)
        selectedIds = setOf(newEl.elementId)
        reloadElements()
        ToastUtil.show(context, "已复制「${src.text.ifBlank { src.type.displayName }}」", Toast.LENGTH_SHORT)
    }

    // ── 复制到跨方案剪贴板 ──
    fun copySelectedToClipboard() {
        val src = elements.find { it.elementId in selectedIds } ?: return
        EditorClipboard.copy(src)
        clipboardHasData = true
        ToastUtil.show(context, "已复制「${src.text.ifBlank { src.type.displayName }}」到剪贴板", Toast.LENGTH_SHORT)
    }

    // ── 从跨方案剪贴板粘贴 ──
    fun pasteFromClipboard() {
        val existingIds = editorState.loadElements().map { it.elementId }.toSet()
        val result = EditorClipboard.paste(
            configId = currentConfigId,
            existingIds = existingIds,
            offsetX = 30,
            offsetY = 30,
        ) ?: return

        // 插入粘贴的元素
        editorState.addElement(result.rootElement)

        reloadElements()
        selectedIds = setOf(result.rootElement.elementId)

        val summary = "已粘贴「${result.rootElement.text.ifBlank { result.rootElement.type.displayName }}」"
        ToastUtil.show(context, summary, Toast.LENGTH_SHORT)
    }

    // ── 删除元素 ──
    fun deleteSelected() {
        val id = selectedIds.firstOrNull() ?: return
        editorState.deleteElement(id)
        selectedIds = emptySet()
        reloadElements()
    }

    // ── 图层调整 ──
    fun moveLayer(direction: Int) {
        val id = selectedIds.firstOrNull() ?: return
        val idx = elements.indexOfFirst { it.elementId == id }
        if (idx < 0) return
        val el = elements[idx]
        val newLayer = (el.layer + direction).coerceIn(0, 999)
        if (newLayer == el.layer) return
        val updated = el.copy(layer = newLayer)
        editorState.saveElement(updated)
        reloadElements()
    }

    // ── 拖拽/缩放结束保存（带网格吸附） ──
    fun saveElementOnInteractionEnd(id: Long, snap: Boolean = true) {
        val el = elements.find { it.elementId == id } ?: return
        val finalEl = if (snap && gridCellSize > 1) {
            el.copy(
                centralX = snapToGrid(el.centralX, gridCellSize),
                centralY = snapToGrid(el.centralY, gridCellSize),
            )
        } else el
        if (finalEl != el) {
            elements = elements.map { if (it.elementId == id) finalEl else it }
        }
        editorState.saveElement(finalEl)
    }

    // ── 键盘弹出时仅属性面板上移，画布不动 ──
    // 临时将窗口 softInputMode 设为 adjustNothing，防止系统自动调整画布大小；
    // 由 imePadding 仅为属性面板所在的覆盖层添加内边距。
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val originalMode = activity?.window?.attributes?.softInputMode
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {
            originalMode?.let { activity?.window?.setSoftInputMode(it) }
        }
    }

    // ── 键盘 Delete 键处理 ──
    DisposableEffect(Unit) {
        val rootView = (context as? android.app.Activity)?.window?.decorView
        val onKeyListener = android.view.View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL)
            ) {
                if (selectedIds.isNotEmpty()) {
                    showDeleteConfirm = true
                    true
                } else false
            } else false
        }
        rootView?.setOnKeyListener(onKeyListener)
        onDispose { rootView?.setOnKeyListener(null) }
    }

    // ── 保存所有元素到 DB 并退出 ──
    fun saveAllAndExit() {
        // 先确保所有在内存中的元素同步到 DB
        for (el in elements) {
            editorState.saveElement(el)
        }
        doExit(context, isNewScheme, schemeName, db, editorState, prefs, engine, onClose)
    }

    // ── 取消退出（不触发额外保存） ──
    val exitEditor: () -> Unit = {
        doExit(context, isNewScheme, schemeName, db, editorState, prefs, engine, onClose)
    }

    // ── 返回键等同于点击工具栏"取消"按钮 ──
    BackHandler(enabled = true) { exitEditor() }

    // ════════════════════════════════════════════════════════
    //  UI
    // ════════════════════════════════════════════════════════

    val selectedElement = elements.find { it.elementId in selectedIds }
    val hasSelection = selectedElement != null
    // 按钮在上半屏 → 属性面板在底部（面板不遮挡按钮）
    // 按钮在下半屏 → 属性面板在顶部（面板不遮挡按钮）
    val showPanelAtBottom = if (selectedElement != null) {
        selectedElement.centralY <= canvasHeightPx / 2
    } else {
        true
    }

    val toolbarActions = ToolbarActions(
        addElement = { showAddMenu = true },
        showElementList = { showElementList = true },
        clearAll = { showClearAllConfirm = true },
        copyToClipboard = { copySelectedToClipboard() },
        pasteClipboard = { pasteFromClipboard() },
        save = { saveAllAndExit() },
        exit = exitEditor,
        delete = { showDeleteConfirm = true },
        duplicate = { duplicateSelected() },
        layerUp = { moveLayer(1) },
        layerDown = { moveLayer(-1) },
        properties = {},
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Layer 1: 全屏编辑画布 ──
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val density = LocalDensity.current
            LaunchedEffect(maxWidth, maxHeight) {
                with(density) {
                    canvasWidthPx = maxWidth.toPx().roundToInt()
                    canvasHeightPx = maxHeight.toPx().roundToInt()
                }
            }

            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = Color.White.copy(alpha = 0.5f))
                }
                elements.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无元素，点击顶栏「按键」添加", color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    val canvasCallbacks = CanvasCallbacks(
                        elementTap = { id ->
                            selectedIds = if (id in selectedIds) emptySet() else setOf(id)
                        },
                        elementDragStart = { id -> selectedIds = setOf(id) },
                        elementDrag = { id, delta ->
                            elements = elements.map { el ->
                                if (el.elementId == id) {
                                    el.copy(
                                        centralX = (el.centralX + delta.x).roundToInt().coerceIn(0, MAX_SCREEN_PX),
                                        centralY = (el.centralY + delta.y).roundToInt().coerceIn(0, MAX_SCREEN_PX),
                                    )
                                } else el
                            }
                        },
                        elementDragEnd = { saveElementOnInteractionEnd(it, snap = true) },

                        canvasTap = {
                            focusManager.clearFocus()
                            selectedIds = emptySet()
                            showGridSlider = false
                        },
                    )
                    EditorCanvas(
                        elements = elements,
                        selectedIds = selectedIds,
                        pressedIds = pressedIds,
                        gridColumnCount = gridWidth,
                        callbacks = canvasCallbacks,
                    )
                }
            }
        }

        // ── Layer 2: 控件覆盖层 ──
        //     IDLE（无选中）→ 显示工具栏
        //     SELECTED（有选中）→ 显示精简属性面板，工具栏隐藏
        //     属性面板在屏幕上方或下方取决于按键位置
        //     imePadding(): 键盘弹出时属性面板跟随上移，画布不动
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            if (hasSelection) {
                // ── 选中状态：工具栏隐藏，显示属性面板 ──
                // 按钮在上半屏 → 面板在底部；按钮在下半屏 → 面板在顶部
                if (showPanelAtBottom) {
                    Spacer(modifier = Modifier.weight(1f))
                    EditorPropertiesPanel(
                        element = selectedElement!!,
                        atTop = false,
                        onSave = { updated: EditorElement ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                            editorState.saveElement(updated)
                            selectedIds = emptySet()
                            ToastUtil.show(context, "已保存", Toast.LENGTH_SHORT)
                        },
                        onDelete = { showDeleteConfirm = true },
                        onDuplicate = { duplicateSelected() },
                        onOpenColorEditor = { el ->
                            pendingColorEditorElement = el
                            showColorEditor = true
                        },
                        onOpenTypeSpecificEditor = { el ->
                            pendingTypeSpecificEditorElement = el
                            showTypeSpecificEditor = true
                        },
                        onElementChanged = { updated ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                        },
                    )
                } else {
                    EditorPropertiesPanel(
                        element = selectedElement!!,
                        atTop = true,
                        onSave = { updated: EditorElement ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                            editorState.saveElement(updated)
                            selectedIds = emptySet()
                            ToastUtil.show(context, "已保存", Toast.LENGTH_SHORT)
                        },
                        onDelete = { showDeleteConfirm = true },
                        onDuplicate = { duplicateSelected() },
                        onOpenColorEditor = { el ->
                            pendingColorEditorElement = el
                            showColorEditor = true
                        },
                        onOpenTypeSpecificEditor = { el ->
                            pendingTypeSpecificEditorElement = el
                            showTypeSpecificEditor = true
                        },
                        onElementChanged = { updated ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                // ── IDLE 状态：显示工具栏 ──
                EditorToolbar(
                    schemeName = schemeName,
                    isEditingName = isEditingName,
                    onStartEditName = { isEditingName = true },
                    onNameChange = { schemeName = it },
                    onConfirmName = { saveSchemeName(schemeName); isEditingName = false },
                    actions = toolbarActions,
                    hasSelection = false,
                    showSelectionBar = false,
                    clipboardHasData = clipboardHasData,
                    gridWidth = gridWidth,
                    onGridWidthChange = { gridWidth = it },
                    showGridSlider = showGridSlider,
                    onToggleGridSlider = { showGridSlider = !showGridSlider },
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // ── 颜色自定义对话框 ──
        if (showColorEditor && pendingColorEditorElement != null) {
            val el = pendingColorEditorElement!!
            ColorPickerDialog(
                title = "颜色自定义",
                items = listOf(
                    ColorPickerItem("正常色", "normal", el.normalColor),
                    ColorPickerItem("按下色", "pressed", el.pressedColor),
                    ColorPickerItem("背景色", "bg", el.backgroundColor),
                    ColorPickerItem("文字色", "normalText", el.normalTextColor),
                    ColorPickerItem("按下文字色", "pressedText", el.pressedTextColor),
                ),
                onSave = { result ->
                    val map = result.toMap()
                    val updated = el.copy(
                        normalColor = map["normal"] ?: el.normalColor,
                        pressedColor = map["pressed"] ?: el.pressedColor,
                        backgroundColor = map["bg"] ?: el.backgroundColor,
                        normalTextColor = map["normalText"] ?: el.normalTextColor,
                        pressedTextColor = map["pressedText"] ?: el.pressedTextColor,
                    )
                    elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                    editorState.saveElement(updated)
                    // 编辑了单个元素颜色 → 关闭该方案的全局颜色开关
                    try {
                        val cv = android.content.ContentValues()
                        cv.putNull(com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_INT_GLOBAL_BORDER_COLOR)
                        cv.putNull(com.limelight.binding.input.advance_setting.config.PageConfigController.COLUMN_INT_GLOBAL_TEXT_COLOR)
                        db.updateConfig(editorState.configId, cv)
                    } catch (_: Exception) { }
                    showColorEditor = false
                    pendingColorEditorElement = null
                    ToastUtil.show(context, "颜色已更新", Toast.LENGTH_SHORT)
                },
                onDismiss = { showColorEditor = false; pendingColorEditorElement = null },
            )
        }

        // ── 类型专属属性设置对话框 ──
        if (showTypeSpecificEditor && pendingTypeSpecificEditorElement != null) {
            TypeSpecificEditorDialog(
                element = pendingTypeSpecificEditorElement!!,
                onSave = { updated: EditorElement ->
                    elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                    editorState.saveElement(updated)
                    showTypeSpecificEditor = false
                    pendingTypeSpecificEditorElement = null
                    ToastUtil.show(context, "属性已更新", Toast.LENGTH_SHORT)
                },
                onDismiss = { showTypeSpecificEditor = false; pendingTypeSpecificEditorElement = null },
            )
        }

        // ── 按键列表弹窗 ──
        if (showElementList && elements.isNotEmpty()) {
            ElementListDialog(
                elements = elements,
                onSelect = { el ->
                    selectedIds = setOf(el.elementId)
                    showElementList = false
                },
                onDismiss = { showElementList = false },
            )
        }

        // ── 添加菜单 ──
        if (showAddMenu) {
            AddElementMenu(
                onDismiss = { showAddMenu = false },
                onSelect = { type ->
                    showAddMenu = false
                    if (type == ElementType.DIGITAL_COMBINE_BUTTON) {
                        showComboKeyEditor = true
                        return@AddElementMenu
                    }
                    val newEl = editorState.createDefaultElement(type)
                    val centerX = (canvasWidthPx / 2).coerceIn(50, MAX_SCREEN_PX - 50)
                    val centerY = (canvasHeightPx / 2).coerceIn(50, MAX_SCREEN_PX - 50)
                    val elToInsert = newEl.copy(
                        elementId = System.currentTimeMillis(),
                        centralX = centerX,
                        centralY = centerY,
                    )
                    editorState.addElement(elToInsert)
                    selectedIds = setOf(elToInsert.elementId)
                    reloadElements()
                    ToastUtil.show(context, "已添加「${type.displayName}」", Toast.LENGTH_SHORT)
                },
            )
        }

        // ── 组合键编辑弹窗 ──
        if (showComboKeyEditor) {
            val comboKeys = elements.filter { it.type == ElementType.DIGITAL_COMBINE_BUTTON }
            ComboKeyEditorDialog(
                existingComboKeys = comboKeys,
                onSaveNew = { newEl ->
                    val finalEl = newEl.copy(
                        elementId = System.currentTimeMillis(),
                        configId = currentConfigId,
                        centralX = (canvasWidthPx / 2).coerceIn(50, MAX_SCREEN_PX - 50),
                        centralY = (canvasHeightPx / 2).coerceIn(50, MAX_SCREEN_PX - 50),
                        layer = (elements.maxOfOrNull { it.layer } ?: 50) + 1,
                    )
                    editorState.addElement(finalEl)
                    selectedIds = setOf(finalEl.elementId)
                    reloadElements()
                    ToastUtil.show(context, "已创建组合键「${finalEl.text}」", Toast.LENGTH_SHORT)
                    showComboKeyEditor = false
                },
                onSaveExisting = { updatedEl ->
                    editorState.saveElement(updatedEl)
                    ToastUtil.show(context, "组合键已更新", Toast.LENGTH_SHORT)
                    reloadElements()
                    showComboKeyEditor = false
                },
                onDeleteExisting = { targetEl ->
                    editorState.deleteElement(targetEl.elementId)
                    ToastUtil.show(context, "已删除组合键", Toast.LENGTH_SHORT)
                    reloadElements()
                    showComboKeyEditor = false
                },
                onDismiss = { showComboKeyEditor = false },
            )
        }

        // ── 删除确认 ──
        if (showDeleteConfirm) {
            val el = selectedElement
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除元素") },
                text = {
                    Text("确定要删除「${el?.text?.ifBlank { el?.type?.displayName ?: "元素" }}」吗？")
                },
                confirmButton = {
                    Button(
                        onClick = { showDeleteConfirm = false; deleteSelected() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
            )
        }

        // ── 清空所有元素确认 ──
        if (showClearAllConfirm) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirm = false },
                title = { Text("清空所有按键") },
                text = { Text("确定要删除屏幕上所有按键元素吗？此操作不可撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearAllConfirm = false
                            // 删除所有元素
                            for (el in elements) {
                                editorState.deleteElement(el.elementId)
                            }
                            selectedIds = emptySet()
                            reloadElements()
                            ToastUtil.show(context, "已清空所有按键", Toast.LENGTH_SHORT)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("清空") }
                },
                dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("取消") } },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  选中元素信息行
// ════════════════════════════════════════════════════════════

@Composable
private fun SelectionInfoBar(element: EditorElement) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "类型: ${element.type.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "ID: ${element.elementId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "X:${element.centralX} Y:${element.centralY} W:${element.width} H:${element.height} L:${element.layer}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  编辑器工具栏（含选中操作 + 网格滑块）
// ════════════════════════════════════════════════════════════

/** 工具栏操作回调集合 */
private data class ToolbarActions(
    val addElement: () -> Unit = {},
    val showElementList: () -> Unit = {},
    val clearAll: () -> Unit = {},
    val copyToClipboard: () -> Unit = {},
    val pasteClipboard: () -> Unit = {},
    val save: () -> Unit = {},
    val exit: () -> Unit = {},
    val delete: () -> Unit = {},
    val duplicate: () -> Unit = {},
    val layerUp: () -> Unit = {},
    val layerDown: () -> Unit = {},
    val properties: () -> Unit = {},
)

@Composable
private fun EditorToolbar(
    schemeName: String,
    isEditingName: Boolean,
    onStartEditName: () -> Unit,
    onNameChange: (String) -> Unit,
    onConfirmName: () -> Unit,
    actions: ToolbarActions = ToolbarActions(),
    hasSelection: Boolean = false,
    showSelectionBar: Boolean = true,
    clipboardHasData: Boolean = false,
    gridWidth: Int = 8,
    onGridWidthChange: (Int) -> Unit = {},
    showGridSlider: Boolean = false,
    onToggleGridSlider: () -> Unit = {},
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        shadowElevation = 4.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 方案名
                if (isEditingName) {
                    BasicTextField(
                        value = schemeName,
                        onValueChange = { if (it.length <= 10) onNameChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    IconButton(onClick = onConfirmName) {
                        Icon(Icons.Default.Check, contentDescription = "确认",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text(schemeName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).clickable { onStartEditName() }
                            .padding(horizontal = 8.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onStartEditName) {
                        Icon(Icons.Default.BorderColor, contentDescription = "编辑名称",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant)

                TextButton(onClick = actions.addElement) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("按键", style = MaterialTheme.typography.labelMedium)
                }

                TextButton(onClick = actions.showElementList) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("按键列表", style = MaterialTheme.typography.labelMedium)
                }

                HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant)

                // ── 跨方案粘贴按钮（剪贴板有内容时显示） ──
                if (clipboardHasData) {
                    TextButton(onClick = actions.pasteClipboard) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("粘贴", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // ── 网格列数选择（点击展开/隐藏滑块） ──
                TextButton(onClick = onToggleGridSlider) {
                    Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (showGridSlider) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(if (gridWidth == 0) "网格 关闭" else "网格 ${gridWidth}列",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showGridSlider) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        if (showGridSlider) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── 清空 / 取消 / 保存 ──
                TextButton(onClick = actions.clearAll) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(2.dp))
                    Text("清空", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error)
                }

                TextButton(onClick = actions.exit) {
                    Text("取消", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = actions.save,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("保存", style = MaterialTheme.typography.labelMedium)
                }
            }

            // ── 网格滑块（可展开/收起） ──
            AnimatedVisibility(visible = showGridSlider) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (gridWidth == 0) "关闭" else "${GRID_MIN_ACTIVE}列",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = gridWidth.toFloat(),
                            onValueChange = { newValue ->
                                val snapped = when {
                                    newValue < 1f -> 0
                                    newValue in 1f..(GRID_MIN_ACTIVE - 1).toFloat() -> {
                                        // 从 0 向右拖 → 跳到 20；从右侧向左拖 → 归 0
                                        if (gridWidth == 0) GRID_MIN_ACTIVE else 0
                                    }
                                    else -> newValue.roundToInt()
                                }
                                onGridWidthChange(snapped)
                            },
                            valueRange = GRID_MIN.toFloat()..GRID_MAX.toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text("${GRID_MAX}列", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(if (gridWidth == 0) "关闭" else "${gridWidth}列",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── 选中操作栏（有选中元素时显示） ──
            if (hasSelection && showSelectionBar) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = actions.delete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = actions.duplicate, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = actions.copyToClipboard, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.FileCopy, contentDescription = "复制到剪贴板",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = actions.layerUp, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Layers, contentDescription = "上移一层",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = actions.layerDown, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.LayersClear, contentDescription = "下移一层",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = actions.properties, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.BorderColor, contentDescription = "属性",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  选中操作栏（从 EditorToolbar 中提取，用于浮动定位）
// ════════════════════════════════════════════════════════════

@Composable
private fun EditorSelectionBar(
    actions: ToolbarActions,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = actions.delete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = actions.duplicate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = actions.copyToClipboard, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FileCopy, contentDescription = "复制到剪贴板",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = actions.layerUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Layers, contentDescription = "上移一层",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = actions.layerDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.LayersClear, contentDescription = "下移一层",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = actions.properties, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.BorderColor, contentDescription = "属性",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  添加元素菜单（3列网格、无标题、可滚动）
// ════════════════════════════════════════════════════════════

@Composable
private fun AddElementMenu(
    onDismiss: () -> Unit,
    onSelect: (ElementType) -> Unit,
) {
    val menuTypes = ElementType.entries.filter { it != ElementType.UNKNOWN }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x44000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(menuTypes) { type ->
                    Button(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            type.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 按键列表弹窗 — 以 4 列网格展示当前方案的所有按键。
 * 点击某个按键后弹窗消失，该按键变为选中状态。
 * 宽度 80% × 高度 90%，居中显示。
 */
@Composable
private fun ElementListDialog(
    elements: List<EditorElement>,
    onSelect: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x44000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxSize(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── 标题行 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("按键列表 (${elements.size} 个)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider()

                // ── 4 列网格 ──
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(elements, key = { it.elementId }) { el ->
                        val label = buildElementSummary(el)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSelect(el) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(4.dp),
                            ) {
                                // 类型标签
                                Text(
                                    el.type.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(2.dp))
                                // 显示文字
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 根据元素类型生成描述文本，用于按键列表弹窗中展示。
 * - 按钮类：显示键值名
 * - 组合键：显示键值名 + 方向值
 * - 滚轮：显示段值范围（首段 ~ 末段）
 * - 十字键：显示方向值
 * - 摇杆类：显示方向值 + 中值
 */
private fun buildElementSummary(el: EditorElement): String {
    val keyName = getKeyLabelByValue(el.value)
    fun dirLabel(v: String, prefix: String): String? =
        v.ifBlank { null }?.let { "$prefix${getKeyLabelByValue(it) ?: it}" }

    return when (el.type) {
        ElementType.DIGITAL_COMMON_BUTTON,
        ElementType.DIGITAL_SWITCH_BUTTON,
        ElementType.DIGITAL_MOVABLE_BUTTON -> keyName ?: el.value

        ElementType.DIGITAL_COMBINE_BUTTON -> listOfNotNull(
            keyName,
            dirLabel(el.upValue, "↑"),
            dirLabel(el.downValue, "↓"),
            dirLabel(el.leftValue, "←"),
            dirLabel(el.rightValue, "→"),
        ).joinToString(" ")

        ElementType.DIGITAL_PAD -> listOfNotNull(
            dirLabel(el.upValue, "↑"),
            dirLabel(el.downValue, "↓"),
            dirLabel(el.leftValue, "←"),
            dirLabel(el.rightValue, "→"),
        ).joinToString(" ")

        ElementType.ANALOG_STICK,
        ElementType.DIGITAL_STICK,
        ElementType.INVISIBLE_ANALOG_STICK,
        ElementType.INVISIBLE_DIGITAL_STICK -> listOfNotNull(
            dirLabel(el.upValue, "↑"),
            dirLabel(el.downValue, "↓"),
            dirLabel(el.leftValue, "←"),
            dirLabel(el.rightValue, "→"),
            dirLabel(el.middleValue, "中"),
        ).joinToString(" ")

        else -> keyName ?: el.type.displayName
    }
}
