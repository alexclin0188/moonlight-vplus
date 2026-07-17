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
import androidx.compose.material.icons.filled.Delete
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
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.editor.CanvasCallbacks
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerDialog
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerItem
import com.alexclin.moonlink.android.stream.ui.editor.ComboKeyEditorDialog
import com.alexclin.moonlink.android.stream.ui.editor.EditorCanvas
import com.alexclin.moonlink.android.stream.ui.editor.EditorElement
import com.alexclin.moonlink.android.stream.ui.editor.EditorPropertiesPanel
import com.alexclin.moonlink.android.stream.ui.editor.EditorState
import com.alexclin.moonlink.android.stream.ui.editor.ElementType
import com.alexclin.moonlink.android.stream.ui.editor.toDisplayName
import com.alexclin.moonlink.android.stream.ui.editor.getDisplayName
import com.alexclin.moonlink.android.stream.ui.editor.ButtonPropertyDialog
import com.alexclin.moonlink.android.stream.ui.editor.StickPropertyDialog
import com.alexclin.moonlink.android.stream.ui.editor.getKeyLabelByValue
import com.alexclin.moonlink.android.stream.ui.editor.findSpiralPlacement
import com.alexclin.moonlink.android.stream.ui.editor.snapToGrid
import com.alexclin.moonlink.android.stream.ui.editor.toContentValues
import com.alexclin.moonlink.android.stream.ui.editor.collectElementDisplayNames
import com.alexclin.moonlink.android.stream.data.ConfigColumns
import com.alexclin.moonlink.android.stream.data.KeymappingDatabaseHelper
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
    db: KeymappingDatabaseHelper,
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
        ToastUtil.show(context, context.getString(R.string.editor_toast_scheme_name_empty), Toast.LENGTH_SHORT)
        return
    }

    // 校验名称不重复
    if (isSchemeNameDuplicate(context, name)) {
        ToastUtil.show(context, context.getString(R.string.editor_toast_duplicate_scheme, name), Toast.LENGTH_SHORT)
        return
    }

    // 创建方案
    try {
        val newId = System.currentTimeMillis()
        // 创建 config 记录
        val configCv = ContentValues()
        configCv.put(ConfigColumns.COLUMN_LONG_CONFIG_ID, newId)
        configCv.put(ConfigColumns.COLUMN_STRING_CONFIG_NAME, name)
        configCv.put(ConfigColumns.COLUMN_BOOLEAN_TOUCH_ENABLE, "true")
        configCv.put(ConfigColumns.COLUMN_BOOLEAN_TOUCH_MODE, "true")
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
        ToastUtil.show(context, context.getString(R.string.editor_toast_scheme_created, name), Toast.LENGTH_SHORT)
        onClose()
    } catch (e: Exception) {
        ToastUtil.show(context, context.getString(R.string.editor_toast_create_failed, e.message), Toast.LENGTH_SHORT)
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
    val db = remember { KeymappingDatabaseHelper(context) }
    val editorState = remember(currentConfigId) { EditorState(db, currentConfigId) }

    // ── 状态 ──
    var elements by remember { mutableStateOf<List<EditorElement>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pressedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var schemeName by remember {
        mutableStateOf(
            if (isNewScheme) {
                // 生成本地化前缀 + 序号方案名，自动避开重复名称
                val existingNames = loadAllSchemeNames(context)
                val prefix = context.getString(R.string.editor_scheme_prefix)
                var counter = 1
                var candidate = "$prefix$counter"
                while (existingNames.any { it.equals(candidate, ignoreCase = true) }) {
                    counter++
                    candidate = "$prefix$counter"
                }
                candidate
            } else context.getString(R.string.editor_scheme_default_name)
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
    // 网格滑块展开/收起（由 EditorToolbar 控制，canvasTap 也可收起）
    var showGridSlider by remember { mutableStateOf(false) }
    // 全屏颜色编辑对话框
    var showColorEditor by remember { mutableStateOf(false) }
    var pendingColorEditorElement by remember { mutableStateOf<EditorElement?>(null) }
    // 全屏类型专属属性编辑对话框
    var showTypeSpecificEditor by remember { mutableStateOf(false) }
    var pendingTypeSpecificEditorElement by remember { mutableStateOf<EditorElement?>(null) }
    // 组合键编辑（从属性面板进入）
    var editingComboKeyElement by remember { mutableStateOf<EditorElement?>(null) }
    // 新建元素临时状态（先打开对话框设置属性，确认后才创建）
    var pendingNewElement by remember { mutableStateOf<EditorElement?>(null) }

    // ── 网格吸附辅助 ──
    val gridCellSize = if (gridWidth > 1 && canvasWidthPx > 0) canvasWidthPx / gridWidth else 0
    val focusManager = LocalFocusManager.current

    // ── 加载 ──
    fun reloadElements() {
        elements = editorState.loadElements()
        // 新建模式保留已生成的本地化方案名，不覆盖为 DB 的默认值
        if (!isNewScheme) {
            schemeName = editorState.getConfigName(context)
        }
        isLoading = false
    }
    LaunchedEffect(currentConfigId) { reloadElements() }

    // ── 保存方案名 ──
    fun saveSchemeName(name: String) {
        val cv = ContentValues().apply { put(ConfigColumns.COLUMN_STRING_CONFIG_NAME, name) }
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
        val actualId = editorState.addElement(newEl)
        selectedIds = setOf(actualId)
        reloadElements()
        ToastUtil.show(context, context.getString(R.string.editor_toast_duplicated, src.text.ifBlank { src.type.getDisplayName(context) }), Toast.LENGTH_SHORT)
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
    val activity = LocalActivity.current
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

    // ── 取消退出 ──
    // 新建模式：清理临时数据（configId = -1 下的元素），不保存方案，直接关闭
    // 编辑模式：刷新覆盖层并关闭
    val exitEditor: () -> Unit = {
        if (isNewScheme) {
            try { db.deleteConfig(-1L) } catch (_: Exception) { }
            onClose()
        } else {
            doExit(context, isNewScheme, schemeName, db, editorState, prefs, engine, onClose)
        }
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
                    Text(stringResource(R.string.editor_loading), color = Color.White.copy(alpha = 0.5f))
                }
                elements.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.editor_empty_hint), color = Color.White.copy(alpha = 0.5f),
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
                    val panelExistingNames = collectElementDisplayNames(
                        elements, excludeElementId = selectedElement!!.elementId
                    )
                    EditorPropertiesPanel(
                        element = selectedElement!!,
                        atTop = false,
                        onSave = { updated: EditorElement ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                            editorState.saveElement(updated)
                            selectedIds = emptySet()
                            ToastUtil.show(context, context.getString(R.string.editor_toast_saved), Toast.LENGTH_SHORT)
                        },
                        onDelete = { showDeleteConfirm = true },
                        onDuplicate = { duplicateSelected() },
                        onOpenColorEditor = { el ->
                            pendingColorEditorElement = el
                            showColorEditor = true
                        },
                        onOpenTypeSpecificEditor = { el ->
                            if (el.type == ElementType.DIGITAL_COMBINE_BUTTON) {
                                editingComboKeyElement = el
                                showComboKeyEditor = true
                            } else {
                                pendingTypeSpecificEditorElement = el
                                showTypeSpecificEditor = true
                            }
                        },
                        onElementChanged = { updated ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                        },
                        existingTextNames = panelExistingNames,
                    )
                } else {
                    val panelExistingNames = collectElementDisplayNames(
                        elements, excludeElementId = selectedElement!!.elementId
                    )
                    EditorPropertiesPanel(
                        element = selectedElement!!,
                        atTop = true,
                        onSave = { updated: EditorElement ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                            editorState.saveElement(updated)
                            selectedIds = emptySet()
                            ToastUtil.show(context, context.getString(R.string.editor_toast_saved), Toast.LENGTH_SHORT)
                        },
                        onDelete = { showDeleteConfirm = true },
                        onDuplicate = { duplicateSelected() },
                        onOpenColorEditor = { el ->
                            pendingColorEditorElement = el
                            showColorEditor = true
                        },
                        onOpenTypeSpecificEditor = { el ->
                            if (el.type == ElementType.DIGITAL_COMBINE_BUTTON) {
                                editingComboKeyElement = el
                                showComboKeyEditor = true
                            } else {
                                pendingTypeSpecificEditorElement = el
                                showTypeSpecificEditor = true
                            }
                        },
                        existingTextNames = panelExistingNames,
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
                title = stringResource(R.string.editor_title_color_settings),
                items = listOf(
                    ColorPickerItem(stringResource(R.string.editor_color_normal), "normal", el.normalColor),
                    ColorPickerItem(stringResource(R.string.editor_color_pressed), "pressed", el.pressedColor),
                    ColorPickerItem(stringResource(R.string.editor_color_background), "bg", el.backgroundColor),
                    ColorPickerItem(stringResource(R.string.editor_color_normal_text), "normalText", el.normalTextColor),
                    ColorPickerItem(stringResource(R.string.editor_color_pressed_text), "pressedText", el.pressedTextColor),
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
                    // 编辑了单个元素颜色 → 关闭该方案的统一颜色配置开关
                    try {
                        val cv = android.content.ContentValues()
                        cv.put(com.alexclin.moonlink.android.stream.data.ConfigColumns.COLUMN_BOOLEAN_UNIFIED_COLOR_ENABLED, "false")
                        db.updateConfig(editorState.configId, cv)
                    } catch (_: Exception) { }
                    showColorEditor = false
                    pendingColorEditorElement = null
                    ToastUtil.show(context, context.getString(R.string.editor_toast_color_updated), Toast.LENGTH_SHORT)
                },
                onDismiss = { showColorEditor = false; pendingColorEditorElement = null },
            )
        }

        // ── 类型专属属性设置对话框 ──
        if (showTypeSpecificEditor && pendingTypeSpecificEditorElement != null) {
            val el = pendingTypeSpecificEditorElement!!
            val stickTypes = listOf(
                ElementType.DIGITAL_STICK,
                ElementType.INVISIBLE_DIGITAL_STICK,
                ElementType.ANALOG_STICK,
                ElementType.INVISIBLE_ANALOG_STICK,
                ElementType.DIGITAL_PAD,
            )
            val existingTextNames = collectElementDisplayNames(
                elements, excludeElementId = el.elementId
            )
            if (el.type in stickTypes) {
                StickPropertyDialog(
                    title = context.getString(R.string.editor_title_new_element, el.type.getDisplayName(context)),
                    element = el,
                    onSave = { updated: EditorElement ->
                        elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                        editorState.saveElement(updated)
                        showTypeSpecificEditor = false
                        pendingTypeSpecificEditorElement = null
                        ToastUtil.show(context, context.getString(R.string.editor_toast_property_updated), Toast.LENGTH_SHORT)
                    },
                    onDismiss = { showTypeSpecificEditor = false; pendingTypeSpecificEditorElement = null },
                )
            } else {
                ButtonPropertyDialog(
                    title = context.getString(R.string.editor_title_new_element, el.type.getDisplayName(context)),
                    element = el,
                    onSave = { updated: EditorElement ->
                        elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                        editorState.saveElement(updated)
                        showTypeSpecificEditor = false
                        pendingTypeSpecificEditorElement = null
                        ToastUtil.show(context, context.getString(R.string.editor_toast_property_updated), Toast.LENGTH_SHORT)
                    },
                    onDismiss = { showTypeSpecificEditor = false; pendingTypeSpecificEditorElement = null },
                    existingTextNames = existingTextNames,
                )
            }
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
                        // 组合键：沿用已有的创建流程（initialElement=null）
                        editingComboKeyElement = null
                        showComboKeyEditor = true
                    } else {
                        // 其他类型：先打开属性对话框，保存后才实际创建
                        val defaultEl = editorState.createDefaultElement(type)
                        pendingNewElement = defaultEl
                    }
                },
            )
        }

        // ── 新建元素属性设置对话框（非组合键） ──
        if (pendingNewElement != null) {
            val el = pendingNewElement!!
            val stickTypes = listOf(
                ElementType.DIGITAL_STICK,
                ElementType.INVISIBLE_DIGITAL_STICK,
                ElementType.ANALOG_STICK,
                ElementType.INVISIBLE_ANALOG_STICK,
                ElementType.DIGITAL_PAD,
            )
            val title = context.getString(R.string.editor_title_new_element, el.type.getDisplayName(context))
            val existingTextNames = collectElementDisplayNames(elements)
            val onSaveNew: (EditorElement) -> Unit = { updated ->
                val (centerX, centerY) = findSpiralPlacement(
                    existingElements = elements,
                    newElementWidth = updated.width,
                    newElementHeight = updated.height,
                    canvasWidth = canvasWidthPx,
                    canvasHeight = canvasHeightPx,
                    maxScreenPx = MAX_SCREEN_PX,
                )
                val finalEl = updated.copy(
                    elementId = System.currentTimeMillis(),
                    configId = currentConfigId,
                    centralX = centerX,
                    centralY = centerY,
                    layer = (elements.maxOfOrNull { it.layer } ?: 50) + 1,
                )
                val actualId = editorState.addElement(finalEl)
                selectedIds = setOf(actualId)
                reloadElements()
                pendingNewElement = null
                ToastUtil.show(context, context.getString(R.string.editor_toast_created, el.type.getDisplayName(context)), Toast.LENGTH_SHORT)
            }
            if (el.type in stickTypes) {
                StickPropertyDialog(
                    title = title,
                    element = el,
                    onSave = onSaveNew,
                    onDismiss = { pendingNewElement = null },
                    isCreateMode = true,
                )
            } else {
                ButtonPropertyDialog(
                    title = title,
                    element = el,
                    onSave = onSaveNew,
                    onDismiss = { pendingNewElement = null },
                    existingTextNames = existingTextNames,
                    isCreateMode = true,
                )
            }
        }

        // ── 组合键编辑弹窗（新建/修改共用） ──
        if (showComboKeyEditor) {
            val editingEl = editingComboKeyElement
            ComboKeyEditorDialog(
                title = if (editingEl != null) stringResource(R.string.editor_title_combo_property) else stringResource(R.string.editor_title_new_combo),
                initialElement = editingEl,
                existingTextNames = collectElementDisplayNames(
                    elements, excludeElementId = editingEl?.elementId
                ),
                isCreateMode = editingEl == null,
                onSaveNew = { newEl ->
                    if (editingEl != null) {
                        // 修改现有组合键
                        val finalEl = newEl.copy(
                            elementId = editingEl.elementId,
                            configId = editingEl.configId,
                            centralX = editingEl.centralX,
                            centralY = editingEl.centralY,
                            width = editingEl.width,
                            height = editingEl.height,
                            layer = editingEl.layer,
                        )
                        editorState.saveElement(finalEl)
                        reloadElements()
                        ToastUtil.show(context, context.getString(R.string.editor_toast_combo_updated), Toast.LENGTH_SHORT)
                    } else {
                        // 新建组合键
                        val (comboCx, comboCy) = findSpiralPlacement(
                            existingElements = elements,
                            newElementWidth = newEl.width,
                            newElementHeight = newEl.height,
                            canvasWidth = canvasWidthPx,
                            canvasHeight = canvasHeightPx,
                            maxScreenPx = MAX_SCREEN_PX,
                        )
                        val finalEl = newEl.copy(
                            elementId = System.currentTimeMillis(),
                            configId = currentConfigId,
                            centralX = comboCx,
                            centralY = comboCy,
                            layer = (elements.maxOfOrNull { it.layer } ?: 50) + 1,
                        )
                        val actualId = editorState.addElement(finalEl)
                        selectedIds = setOf(actualId)
                        reloadElements()
                        ToastUtil.show(context, context.getString(R.string.editor_toast_combo_created, finalEl.text), Toast.LENGTH_SHORT)
                    }
                    showComboKeyEditor = false
                    editingComboKeyElement = null
                },
                onDismiss = {
                    showComboKeyEditor = false
                    editingComboKeyElement = null
                },
            )
        }

        // ── 删除确认 ──
        if (showDeleteConfirm) {
            val el = selectedElement
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.editor_delete_title)) },
                text = {
                    val name = el?.text?.ifBlank { el?.type?.getDisplayName(context) } ?: "Element"
                    Text(stringResource(R.string.editor_delete_element_format, name))
                },
                confirmButton = {
                    Button(
                        onClick = { showDeleteConfirm = false; deleteSelected() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.editor_content_desc_delete)) }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.editor_cancel)) } },
            )
        }

        // ── 清空所有元素确认 ──
        if (showClearAllConfirm) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirm = false },
                title = { Text(stringResource(R.string.editor_clear_all_title)) },
                text = { Text(stringResource(R.string.editor_clear_all_confirm)) },
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
                            ToastUtil.show(context, context.getString(R.string.editor_toast_cleared_all), Toast.LENGTH_SHORT)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.editor_confirm_clear)) }
                },
                dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text(stringResource(R.string.editor_cancel)) } },
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
                stringResource(R.string.editor_btn_type_settings, element.type.toDisplayName()),
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
                        onValueChange = { if (it.length <= 20) onNameChange(it) },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    IconButton(onClick = onConfirmName) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.editor_content_desc_confirm_name),
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
                        Icon(Icons.Default.BorderColor, contentDescription = stringResource(R.string.editor_content_desc_edit_name),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant)

                TextButton(onClick = actions.addElement) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(stringResource(R.string.editor_toolbar_keys), style = MaterialTheme.typography.labelMedium)
                }

                TextButton(onClick = actions.showElementList) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(stringResource(R.string.editor_toolbar_key_list), style = MaterialTheme.typography.labelMedium)
                }

                HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant)

                // ── 网格列数选择（点击展开/隐藏滑块） ──
                TextButton(onClick = onToggleGridSlider) {
                    Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (showGridSlider) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    Text(if (gridWidth == 0) stringResource(R.string.editor_toolbar_grid_off) else stringResource(R.string.editor_toolbar_grid_cols, gridWidth),
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
                    Text(stringResource(R.string.editor_confirm_clear), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error)
                }

                TextButton(onClick = actions.exit) {
                    Text(stringResource(R.string.editor_cancel), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = actions.save,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelMedium)
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
                        Text(if (gridWidth == 0) stringResource(R.string.editor_grid_slider_off) else stringResource(R.string.editor_grid_cols_format, GRID_MIN_ACTIVE),
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
                        Text(stringResource(R.string.editor_grid_cols_format, GRID_MAX), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(if (gridWidth == 0) stringResource(R.string.editor_grid_slider_off) else stringResource(R.string.editor_grid_cols_format, gridWidth),
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
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.editor_content_desc_delete),
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = actions.duplicate, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.editor_content_desc_duplicate),
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = actions.layerUp, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.editor_content_desc_layer_up),
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = actions.layerDown, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.LayersClear, contentDescription = stringResource(R.string.editor_content_desc_layer_down),
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = actions.properties, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.BorderColor, contentDescription = stringResource(R.string.editor_content_desc_properties),
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
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.editor_content_desc_delete),
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = actions.duplicate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.editor_content_desc_duplicate),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = actions.layerUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.editor_content_desc_layer_up),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = actions.layerDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.LayersClear, contentDescription = stringResource(R.string.editor_content_desc_layer_down),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = actions.properties, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.BorderColor, contentDescription = stringResource(R.string.editor_content_desc_properties),
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
                            type.toDisplayName(),
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
    val context = LocalContext.current
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
                    Text(stringResource(R.string.editor_element_list_title, elements.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close),
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
                        val label = buildElementSummary(el, context)
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
                                    el.type.toDisplayName(),
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
private fun buildElementSummary(el: EditorElement, context: android.content.Context? = null): String {
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
            dirLabel(el.middleValue, "N"),
        ).joinToString(" ")

        else -> keyName ?: (context?.let { el.type.getDisplayName(it) } ?: el.type.name)
    }
}
