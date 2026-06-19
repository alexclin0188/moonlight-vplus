package com.alexclin.moonlink.stream.ui.panels

import android.content.ContentValues
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.editor.applyResize
import com.alexclin.moonlink.stream.ui.panels.isSchemeNameDuplicate
import com.alexclin.moonlink.stream.ui.editor.CanvasCallbacks
import com.alexclin.moonlink.stream.ui.editor.ComboKeyEditorDialog
import com.alexclin.moonlink.stream.ui.editor.EditorCanvas
import com.alexclin.moonlink.stream.ui.editor.GroupChildListDialog
import com.alexclin.moonlink.stream.ui.editor.EditorClipboard
import com.alexclin.moonlink.stream.ui.editor.EditorElement
import com.alexclin.moonlink.stream.ui.editor.EditorPropertiesPanel
import com.alexclin.moonlink.stream.ui.editor.EditorState
import com.alexclin.moonlink.stream.ui.editor.ElementType
import com.alexclin.moonlink.stream.ui.editor.SchemeExporter
import com.alexclin.moonlink.stream.ui.editor.WheelPadSegmentEditor
import com.alexclin.moonlink.stream.ui.editor.snapToGrid
import com.alexclin.moonlink.stream.ui.editor.toContentValues
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import kotlin.math.roundToInt

private const val MAX_SCREEN_PX = 5000

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
        onClose()
        return
    }

    val name = schemeName.trim()
    if (name.isEmpty()) {
        Toast.makeText(context, "方案名称不能为空", Toast.LENGTH_SHORT).show()
        return
    }

    // 校验名称不重复
    if (isSchemeNameDuplicate(context, name)) {
        Toast.makeText(context, "已存在同名方案「$name」，请修改名称", Toast.LENGTH_SHORT).show()
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
        engine.setCrownFeatureEnabled(true)
        engine.reloadOverlay()
        Toast.makeText(context, "方案「$name」已创建", Toast.LENGTH_SHORT).show()
        onClose()
    } catch (e: Exception) {
        Toast.makeText(context, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
    var schemeName by remember { mutableStateOf(if (isNewScheme) "我的方案1" else "按键方案") }
    var isEditingName by remember { mutableStateOf(false) }
    var gridWidth by remember { mutableIntStateOf(8) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPropertiesPanel by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    // 组合键编辑弹窗
    var showComboKeyEditor by remember { mutableStateOf(false) }
    // 组按键子元素管理弹窗
    var showChildManager by remember { mutableStateOf(false) }
    // WheelPad 分段编辑弹窗
    var showWheelPadSegmentEditor by remember { mutableStateOf(false) }
    // 画布尺寸缓存（用于自动居中）
    var canvasWidthPx by remember { mutableIntStateOf(1080) }
    var canvasHeightPx by remember { mutableIntStateOf(1920) }
    // 跨方案剪贴板状态（用于刷新粘贴按钮的可见性）
    var clipboardHasData by remember { mutableStateOf(EditorClipboard.hasData) }

    // ── 网格吸附辅助 ──
    val gridCellSize = if (gridWidth > 1 && canvasWidthPx > 0) canvasWidthPx / gridWidth else 0

    // ── 加载（自动清理 GroupButton 和 WheelPad 的孤儿引用） ──
    fun reloadElements() {
        val raw = editorState.loadElements()
        val afterGroup = editorState.cleanupOrphanedGroupButtonRefs(raw)
        elements = editorState.cleanupOrphanedWheelPadRefs(afterGroup)
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
        Toast.makeText(context, "已复制「${src.text.ifBlank { src.type.displayName }}」", Toast.LENGTH_SHORT).show()
    }

    // ── 复制到跨方案剪贴板（携带 GroupButton 子元素） ──
    fun copySelectedToClipboard() {
        val src = elements.find { it.elementId in selectedIds } ?: return
        val children = if (src.type == ElementType.GROUP_BUTTON) {
            val childIds = src.value
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .filter { it != -1L }
            elements.filter { it.elementId in childIds }
        } else {
            emptyList()
        }
        EditorClipboard.copy(src, children)
        clipboardHasData = true
        val childInfo = if (children.isNotEmpty()) "（含 ${children.size} 个子按键）" else ""
        Toast.makeText(context, "已复制「${src.text.ifBlank { src.type.displayName }}」$childInfo 到剪贴板", Toast.LENGTH_SHORT).show()
    }

    // ── 从跨方案剪贴板粘贴 ──
    fun pasteFromClipboard() {
        val result = EditorClipboard.paste(
            configId = currentConfigId,
            offsetX = 30,
            offsetY = 30,
        ) ?: return

        // 插入所有子元素
        for (child in result.childElements) {
            editorState.addElement(child)
        }

        // 插入根元素（已更新子元素 ID 引用）
        editorState.addElement(result.rootElement)

        reloadElements()
        selectedIds = setOf(result.rootElement.elementId)

        val summary = if (result.childElements.isNotEmpty()) {
            "已粘贴「${result.rootElement.text.ifBlank { result.rootElement.type.displayName }}」及 ${result.childElements.size} 个子按键"
        } else {
            "已粘贴「${result.rootElement.text.ifBlank { result.rootElement.type.displayName }}」"
        }
        Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
    }

    // ── 删除元素（reloadElements 自动清理 GroupButton 的孤儿引用） ──
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

    // ── 导出 / 导入 Launcher ──
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(SchemeExporter.MIME_TYPE)
    ) { uri: Uri? ->
        if (uri != null) {
            val success = SchemeExporter.export(context, db, currentConfigId, uri)
            if (success) {
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            SchemeExporter.import(context, db, uri) { newConfigId ->
                // 切换到新导入的方案
                currentConfigId = newConfigId
                prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, newConfigId).apply()
                Toast.makeText(context, "已切换到导入的方案", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 退出（新建模式需先创建方案） ──
    val exitEditor: () -> Unit = {
        doExit(context, isNewScheme, schemeName, db, editorState, prefs, engine, onClose)
    }

    // ════════════════════════════════════════════════════════
    //  UI
    // ════════════════════════════════════════════════════════

    val selectedElement = elements.find { it.elementId in selectedIds }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏 ──
            val toolbarActions = ToolbarActions(
                addElement = { showAddMenu = true },
                addComboKey = { showComboKeyEditor = true },
                manageChildren = { showChildManager = true },
                copyToClipboard = { copySelectedToClipboard() },
                pasteClipboard = { pasteFromClipboard() },
                exit = exitEditor,
                delete = { showDeleteConfirm = true },
                duplicate = { duplicateSelected() },
                layerUp = { moveLayer(1) },
                layerDown = { moveLayer(-1) },
                properties = { showPropertiesPanel = !showPropertiesPanel },
                export = { exportLauncher.launch("${schemeName.filter { it.isLetterOrDigit() || it == '_' }}.json") },
                import = { importLauncher.launch(arrayOf(SchemeExporter.MIME_TYPE, "*/*")) },
            )
            EditorToolbar(
                schemeName = schemeName,
                isEditingName = isEditingName,
                onStartEditName = { isEditingName = true },
                onNameChange = { schemeName = it },
                onConfirmName = { saveSchemeName(schemeName); isEditingName = false },
                actions = toolbarActions,
                hasSelection = selectedIds.isNotEmpty(),
                isGroupButtonSelected = selectedElement?.type == ElementType.GROUP_BUTTON,
                clipboardHasData = clipboardHasData,
            )

            // ── 选中元素信息行 ──
            selectedElement?.let { el ->
                SelectionInfoBar(element = el)
            }

            // ── 编辑画布 ──
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                            elementResizeStart = { id, _ -> selectedIds = setOf(id) },
                            elementResize = { id, handle, delta ->
                                elements = elements.map { el ->
                                    if (el.elementId == id) applyResize(el, handle, delta)
                                    else el
                                }
                            },
                            elementResizeEnd = { saveElementOnInteractionEnd(it, snap = false) },
                            canvasTap = { selectedIds = emptySet() },
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

            // ── 属性编辑面板（选中元素时展开） ──
            if (showPropertiesPanel) {
                val selEl = selectedElement
                if (selEl != null) {
                    EditorPropertiesPanel(
                        element = selEl,
                        onSave = { updated ->
                            elements = elements.map { if (it.elementId == updated.elementId) updated else it }
                            editorState.saveElement(updated)
                            showPropertiesPanel = false
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { showPropertiesPanel = false },
                        onManageChildren = if (selEl.type == ElementType.GROUP_BUTTON)
                            ({ showPropertiesPanel = false; showChildManager = true })
                        else null,
                        onManageSegments = if (selEl.type == ElementType.WHEEL_PAD)
                            ({ showPropertiesPanel = false; showWheelPadSegmentEditor = true })
                        else null,
                    )
                }
            }

            // ── 底栏 ──
            GridWidthSlider(gridWidth = gridWidth, onGridWidthChange = { gridWidth = it })
        }

        // ── 添加菜单 ──
        if (showAddMenu) {
            AddElementMenu(
                onDismiss = { showAddMenu = false },
                onSelect = { type ->
                    showAddMenu = false
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
                    Toast.makeText(context, "已添加「${type.displayName}」", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "已创建组合键「${finalEl.text}」", Toast.LENGTH_SHORT).show()
                    showComboKeyEditor = false
                },
                onSaveExisting = { updatedEl ->
                    editorState.saveElement(updatedEl)
                    Toast.makeText(context, "组合键已更新", Toast.LENGTH_SHORT).show()
                    reloadElements()
                    showComboKeyEditor = false
                },
                onDeleteExisting = { targetEl ->
                    editorState.deleteElement(targetEl.elementId)
                    Toast.makeText(context, "已删除组合键", Toast.LENGTH_SHORT).show()
                    reloadElements()
                    showComboKeyEditor = false
                },
                onDismiss = { showComboKeyEditor = false },
            )
        }

        // ── WheelPad 分段编辑弹窗 ──
        if (showWheelPadSegmentEditor) {
            val wheelEl = selectedElement
            if (wheelEl != null && wheelEl.type == ElementType.WHEEL_PAD) {
                WheelPadSegmentEditor(
                    element = wheelEl,
                    allElements = elements,
                    onSave = { updated ->
                        editorState.saveElement(updated)
                        reloadElements()
                        Toast.makeText(context, "分段已更新", Toast.LENGTH_SHORT).show()
                        showWheelPadSegmentEditor = false
                    },
                    onDismiss = { showWheelPadSegmentEditor = false },
                )
            } else {
                showWheelPadSegmentEditor = false
            }
        }

        // ── 组按键子元素管理弹窗 ──
        if (showChildManager) {
            val groupEl = selectedElement
            if (groupEl != null && groupEl.type == ElementType.GROUP_BUTTON) {
                GroupChildListDialog(
                    groupElement = groupEl,
                    allElements = elements,
                    editorState = editorState,
                    onSave = { updatedGroup ->
                        editorState.saveElement(updatedGroup)
                        reloadElements()
                        Toast.makeText(context, "子按键列表已更新", Toast.LENGTH_SHORT).show()
                        showChildManager = false
                    },
                    onDismiss = { showChildManager = false },
                )
            } else {
                showChildManager = false
            }
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
//  编辑器工具栏（含选中操作）
// ════════════════════════════════════════════════════════════

/** 工具栏操作回调集合 */
private data class ToolbarActions(
    val addElement: () -> Unit = {},
    val addComboKey: () -> Unit = {},
    val manageChildren: () -> Unit = {},
    val copyToClipboard: () -> Unit = {},
    val pasteClipboard: () -> Unit = {},
    val exit: () -> Unit = {},
    val delete: () -> Unit = {},
    val duplicate: () -> Unit = {},
    val layerUp: () -> Unit = {},
    val layerDown: () -> Unit = {},
    val properties: () -> Unit = {},
    val export: () -> Unit = {},
    val import: () -> Unit = {},
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
    isGroupButtonSelected: Boolean = false,
    clipboardHasData: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ← 退出
                IconButton(onClick = actions.exit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "保存退出",
                        tint = MaterialTheme.colorScheme.primary)
                }

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
                TextButton(onClick = actions.addComboKey) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("组合键", style = MaterialTheme.typography.labelMedium)
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

                TextButton(onClick = actions.export) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("导出", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = actions.import) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("导入", style = MaterialTheme.typography.labelMedium)
                }
            }

            // ── 选中操作栏（有选中元素时显示） ──
            if (hasSelection) {
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
                        // 组按键子元素管理按钮
                        if (isGroupButtonSelected) {
                            IconButton(onClick = actions.manageChildren, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "子按键",
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
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
//  网格宽度滑块
// ════════════════════════════════════════════════════════════

@Composable
private fun GridWidthSlider(gridWidth: Int, onGridWidthChange: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.GridOn, contentDescription = null,
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("网格", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = gridWidth.toFloat(),
                onValueChange = { onGridWidthChange(it.roundToInt()) },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (gridWidth == 0) "关" else "${gridWidth}列",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  添加元素菜单
// ════════════════════════════════════════════════════════════

@Composable
private fun AddElementMenu(
    onDismiss: () -> Unit,
    onSelect: (ElementType) -> Unit,
) {
    val elementTypes = listOf(
        ElementType.DIGITAL_COMMON_BUTTON to "普通按键",
        ElementType.DIGITAL_SWITCH_BUTTON to "开关按键",
        ElementType.DIGITAL_MOVABLE_BUTTON to "可移动按键",
        ElementType.ANALOG_STICK to "摇杆",
        ElementType.DIGITAL_PAD to "方向键",
        ElementType.DIGITAL_COMBINE_BUTTON to "组合键",
        ElementType.GROUP_BUTTON to "组按键",
        ElementType.SIMPLIFY_PERFORMANCE to "性能面板",
        ElementType.WHEEL_PAD to "滚轮面板",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x44000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.padding(32.dp).clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("添加元素", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                elementTypes.forEach { (type, label) ->
                    TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("取消", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
