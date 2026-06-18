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
import androidx.compose.material.icons.filled.Delete
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
import com.alexclin.moonlink.stream.ui.editor.CanvasCallbacks
import com.alexclin.moonlink.stream.ui.editor.EditorCanvas
import com.alexclin.moonlink.stream.ui.editor.EditorElement
import com.alexclin.moonlink.stream.ui.editor.EditorPropertiesPanel
import com.alexclin.moonlink.stream.ui.editor.EditorState
import com.alexclin.moonlink.stream.ui.editor.ElementType
import com.alexclin.moonlink.stream.ui.editor.SchemeExporter
import com.alexclin.moonlink.stream.ui.editor.snapToGrid
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import kotlin.math.roundToInt

private const val MAX_SCREEN_PX = 5000
private const val PREF_CURRENT_CONFIG_ID = "current_config_id"

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
    var currentConfigId by remember { mutableStateOf(prefs.getLong(PREF_CURRENT_CONFIG_ID, 0L)) }
    val db = remember { SuperConfigDatabaseHelper(context) }
    val editorState = remember(currentConfigId) { EditorState(db, currentConfigId) }

    // ── 状态 ──
    var elements by remember { mutableStateOf<List<EditorElement>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pressedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var schemeName by remember { mutableStateOf("按键方案") }
    var isEditingName by remember { mutableStateOf(false) }
    var gridWidth by remember { mutableIntStateOf(8) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPropertiesPanel by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    // 画布尺寸缓存（用于自动居中）
    var canvasWidthPx by remember { mutableIntStateOf(1080) }
    var canvasHeightPx by remember { mutableIntStateOf(1920) }

    // ── 网格吸附辅助 ──
    val gridCellSize = if (gridWidth > 1 && canvasWidthPx > 0) canvasWidthPx / gridWidth else 0

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

    // ── 复制元素 ──
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
                prefs.edit().putLong(PREF_CURRENT_CONFIG_ID, newConfigId).apply()
                Toast.makeText(context, "已切换到导入的方案", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 退出 ──
    val exitEditor: () -> Unit = { onClose() }

    // ════════════════════════════════════════════════════════
    //  UI
    // ════════════════════════════════════════════════════════

    val selectedElement = elements.find { it.elementId in selectedIds }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏 ──
            val toolbarActions = ToolbarActions(
                addElement = { showAddMenu = true },
                addComboKey = { Toast.makeText(context, "组合键编辑（待实现）", Toast.LENGTH_SHORT).show() },
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
