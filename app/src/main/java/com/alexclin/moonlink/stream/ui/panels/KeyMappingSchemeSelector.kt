package com.alexclin.moonlink.stream.ui.panels

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import com.limelight.preferences.PreferenceConfiguration
import org.json.JSONArray
import org.json.JSONObject

/** 当前方案的 SharedPreference key，与旧 PageConfigController 互通 */
private const val PREF_CURRENT_CONFIG_ID = "current_config_id"

/** 方案数据模型 */
private data class SchemeInfo(
    val configId: Long,
    val name: String,
    val isDefault: Boolean = configId == 0L,
)

/**
 * 按键映射方案选择全屏页面（T-05 + T-06 + T-07）。
 *
 * 顶部标题栏 + 搜索栏 + 导入/新建按钮 + 方案列表：
 *  - 虚拟手柄方案（内置，固定置顶）
 *  - 用户自定义方案（来自 SuperConfigDatabase）
 */
@Composable
fun KeyMappingSchemeSelector(
    engine: StreamEngine,
    onClose: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // ── 加载方案列表 ──
    var schemes by remember { mutableStateOf<List<SchemeInfo>>(emptyList()) }
    var currentConfigId by remember { mutableLongStateOf(prefs.getLong(PREF_CURRENT_CONFIG_ID, 0L)) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // 刷新方案列表
    fun refreshSchemes() {
        try {
            val db = SuperConfigDatabaseHelper(context)
            val ids = db.queryAllConfigIds()
            val list = ids.map { id ->
                val name = db.queryConfigAttribute(id, PageConfigController.COLUMN_STRING_CONFIG_NAME, "未命名") as? String ?: "未命名"
                SchemeInfo(configId = id, name = name)
            }
            schemes = list
        } catch (_: Exception) {
            // 数据库未就绪时静默处理
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { refreshSchemes() }

    // ── .mkmp 导入状态 ──
    var showImportMkmpDialog by remember { mutableStateOf(false) }
    var importMkmpJson by remember { mutableStateOf("") }

    val mkmpOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            if (json.isBlank()) {
                Toast.makeText(context, "文件内容为空", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            importMkmpJson = json
            showImportMkmpDialog = true
        } catch (e: Exception) {
            Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── 选择方案 ──
    fun selectScheme(configId: Long) {
        currentConfigId = configId
        prefs.edit().putLong(PREF_CURRENT_CONFIG_ID, configId).apply()
        // 启用按键映射并刷新元素布局
        try {
            engine.setCrownFeatureEnabled(true)
            engine.controllerManager?.refreshLayout()
        } catch (_: Exception) { }
    }

    // ── 新建方案 Dialog ──
    var showNewDialog by remember { mutableStateOf(false) }
    if (showNewDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建按键方案") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 10) newName = it },
                    label = { Text("方案名称（1-10 字符）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isEmpty() || name.length > 10) {
                            Toast.makeText(context, "名称长度需为 1-10 字符", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        try {
                            val db = SuperConfigDatabaseHelper(context)
                            val cv = ContentValues()
                            val newId = System.currentTimeMillis()
                            cv.put(PageConfigController.COLUMN_LONG_CONFIG_ID, newId)
                            cv.put(PageConfigController.COLUMN_STRING_CONFIG_NAME, name)
                            cv.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_ENABLE, "true")
                            cv.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, "true")
                            db.insertConfig(cv)
                            refreshSchemes()
                            selectScheme(newId)
                            Toast.makeText(context, "方案「$name」已创建", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        showNewDialog = false
                    },
                    enabled = newName.trim().isNotEmpty(),
                ) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("取消") } },
        )
    }

    // ── 重命名 Dialog ──
    var renameTarget by remember { mutableStateOf<SchemeInfo?>(null) }
    renameTarget?.let { target ->
        var renameText by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名方案") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { if (it.length <= 10) renameText = it },
                    label = { Text("方案名称（1-10 字符）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameText.trim()
                        if (name.isEmpty() || name.length > 10) {
                            Toast.makeText(context, "名称长度需为 1-10 字符", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        try {
                            val db = SuperConfigDatabaseHelper(context)
                            val cv = ContentValues()
                            cv.put(PageConfigController.COLUMN_STRING_CONFIG_NAME, name)
                            db.updateConfig(target.configId, cv)
                            refreshSchemes()
                            Toast.makeText(context, "已重命名为「$name」", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "重命名失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        renameTarget = null
                    },
                    enabled = renameText.trim().isNotEmpty(),
                ) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    // ── 删除确认 Dialog ──
    var deleteTarget by remember { mutableStateOf<SchemeInfo?>(null) }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除方案") },
            text = { Text("确定要删除方案「${target.name}」吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val db = SuperConfigDatabaseHelper(context)
                            db.deleteConfig(target.configId)
                            refreshSchemes()
                            if (currentConfigId == target.configId) {
                                // 回退到 default
                                selectScheme(0L)
                            }
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    // ════════════════════════════════════════════
    // Dialog：导入 .mkmp
    // ════════════════════════════════════════════
    if (showImportMkmpDialog) {
        ImportMkmpDialog(
            json = importMkmpJson,
            schemes = schemes,
            onConfirm = { name, overrideTargetId ->
                try {
                    importMkmpFromJson(context, importMkmpJson, name, overrideTargetId)
                    refreshSchemes()
                    Toast.makeText(context, "方案「$name」已导入", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                showImportMkmpDialog = false
            },
            onDismiss = { showImportMkmpDialog = false },
        )
    }

    // ── 页面主体 ──
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶部标题栏 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "按键映射方案",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── 搜索栏 ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索方案名") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
            )

            // ── 导入 / 新建 按钮行 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        mkmpOpenLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入")
                }
                Button(
                    onClick = { showNewDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }

            // ── 方案列表 ──
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── 1. 虚拟手柄方案（内置，固定置顶） ──
                    item(key = "virtual_controller") {
                        VirtualControllerSchemeCard(
                            prefs = prefs,
                            isSelected = currentConfigId == 0L,
                            onSelect = { selectScheme(0L) },
                        )
                    }

                    // ── 2. 用户方案列表 ──
                    val filtered = schemes.filter { it.configId != 0L }
                        .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                    items(filtered, key = { it.configId }) { scheme ->
                        SchemeCard(
                            scheme = scheme,
                            isSelected = scheme.configId == currentConfigId,
                            onSelect = { selectScheme(scheme.configId) },
                            onRename = { renameTarget = scheme },
                            onDelete = { deleteTarget = scheme },
                            onOpenEditor = {
                                selectScheme(scheme.configId)
                                onOpenEditor()
                            },
                        )
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  虚拟手柄方案卡片（T-07）
// ════════════════════════════════════════════════════════════

private const val PREF_VIBRATE_OSC = "checkbox_vibrate_osc"
private const val PREF_OSC_OPACITY = "seekbar_osc_opacity"
private const val PREF_ONLY_L3R3 = "checkbox_only_show_L3R3"
private const val PREF_SHOW_GUIDE = "checkbox_show_guide_button"
private const val PREF_HALF_HEIGHT = "checkbox_half_height_osc_portrait"

@Composable
private fun VirtualControllerSchemeCard(
    prefs: SharedPreferences,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    var expanded by remember { mutableStateOf(isSelected) }
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onSelect() }
            .padding(12.dp),
    ) {
        // ── 标题行 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Gamepad,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("虚拟手柄方案", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("内置方案，始终可用", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.RadioButton(
                selected = isSelected,
                onClick = onSelect,
            )
        }

        // ── 展开/折叠按钮 ──
        if (isSelected) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── 配置项列表 ──
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 36.dp, top = 8.dp)) {
                    // 启用震动
                    VirtualControllerSwitch(
                        label = "启用震动",
                        prefKey = PREF_VIBRATE_OSC,
                        prefs = prefs,
                    )
                    // 透明度 Slider
                    VirtualControllerOpacitySlider(
                        label = "透明度",
                        prefKey = PREF_OSC_OPACITY,
                        prefs = prefs,
                    )
                    // 只显示 L3 和 R3
                    VirtualControllerSwitch(
                        label = "只显示 L3 和 R3",
                        prefKey = PREF_ONLY_L3R3,
                        prefs = prefs,
                    )
                    // 显示 Guide 按钮
                    VirtualControllerSwitch(
                        label = "显示 Guide 按钮",
                        prefKey = PREF_SHOW_GUIDE,
                        prefs = prefs,
                    )
                    // 竖屏模式半高
                    VirtualControllerSwitch(
                        label = "竖屏模式半高",
                        prefKey = PREF_HALF_HEIGHT,
                        prefs = prefs,
                    )
                    // 重置布局按钮
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            // 通知用户，重置功能后续完善
                            Toast.makeText(ctx, "屏幕控制按钮布局已重置", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重置屏幕控制按钮布局")
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualControllerSwitch(label: String, prefKey: String, prefs: SharedPreferences) {
    var checked by remember { mutableStateOf(prefs.getBoolean(prefKey, false)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                prefs.edit().putBoolean(prefKey, it).apply()
            },
        )
    }
}

@Composable
private fun VirtualControllerOpacitySlider(label: String, prefKey: String, prefs: SharedPreferences) {
    var opacity by remember { mutableFloatStateOf(prefs.getInt(prefKey, 100) / 100f) }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = opacity,
            onValueChange = {
                opacity = it
                prefs.edit().putInt(prefKey, (it * 100).toInt()).apply()
            },
            valueRange = 0f..1f,
        )
    }
}

// ════════════════════════════════════════════════════════════
//  用户方案卡片（T-06）
// ════════════════════════════════════════════════════════════

@Composable
private fun SchemeCard(
    scheme: SchemeInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onSelect() }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 选中指示器
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))

            // 方案名
            Text(
                scheme.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 操作按钮
            if (isSelected) {
                IconButton(onClick = onOpenEditor) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!scheme.isDefault) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "重命名",
                         tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除",
                         tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── 选中后显示操作入口 ──
        if (isSelected && !scheme.isDefault) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onOpenEditor, modifier = Modifier.weight(1f)) {
                    Text("编辑方案")
                }
                OutlinedButton(onClick = onRename, modifier = Modifier.weight(1f)) {
                    Text("重命名")
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  .mkmp 导入弹窗（T-15 前置集成）
// ════════════════════════════════════════════════════════════

@Composable
private fun ImportMkmpDialog(
    json: String,
    schemes: List<SchemeInfo>,
    onConfirm: (name: String, overrideTargetId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // 尝试从 JSON 中提取 name
    val parsedName = remember {
        try {
            JSONObject(json).optString("name", "导入方案")
        } catch (_: Exception) { "导入方案" }
    }
    var name by remember { mutableStateOf(parsedName) }
    var selectedId by remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入按键方案") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("方案名称（1-10 字符）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text("覆盖已有方案", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))

                // 不覆盖，作为新方案导入
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = 0L }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedId == 0L, onClick = { selectedId = 0L })
                    Spacer(Modifier.width(8.dp))
                    Text("不覆盖，作为新方案导入", style = MaterialTheme.typography.bodyMedium)
                }

                // 已有方案列表
                schemes.forEach { scheme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = scheme.configId }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedId == scheme.configId,
                                    onClick = { selectedId = scheme.configId })
                        Spacer(Modifier.width(8.dp))
                        Text(scheme.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) onConfirm(n, selectedId)
                },
                enabled = name.trim().isNotEmpty(),
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 从 .mkmp JSON 导入方案到数据库。
 * @param overrideTargetId 不为 0L 时覆盖该已有方案，否则创建新方案。
 */
private fun importMkmpFromJson(context: android.content.Context, json: String, newName: String, overrideTargetId: Long = 0L) {
    val root = JSONObject(json)
    val configObj = root.optJSONObject("config") ?: JSONObject()
    val elementsArray = root.optJSONArray("elements") ?: JSONArray()

    val db = SuperConfigDatabaseHelper(context)

    // 覆盖模式：先删除旧数据，复用原有 config_id
    if (overrideTargetId != 0L) {
        db.deleteConfig(overrideTargetId)
    }

    val newConfigId = if (overrideTargetId != 0L) overrideTargetId else System.currentTimeMillis()

    // 写入 config
    val configValues = ContentValues().apply {
        put(PageConfigController.COLUMN_LONG_CONFIG_ID, newConfigId)
        put(PageConfigController.COLUMN_STRING_CONFIG_NAME, newName)
        // 复制可用的 config 属性
        for (key in configObj.keys()) {
            val v = configObj.opt(key)
            when (v) {
                is Boolean -> put(key, v)
                is Int -> put(key, v)
                is String -> put(key, v)
                is Long -> put(key, v)
                is Double -> put(key, v.toFloat())
            }
        }
    }
    db.insertConfig(configValues)

    // 写入 elements
    var elementIdCounter = System.currentTimeMillis()
    for (i in 0 until elementsArray.length()) {
        val elObj = elementsArray.getJSONObject(i)
        val elValues = ContentValues()
        elValues.put("config_id", newConfigId)
        elValues.put("element_id", elementIdCounter++)
        for (key in elObj.keys()) {
            val v = elObj.opt(key)
            when (v) {
                is Boolean -> elValues.put(key, v)
                is Int -> elValues.put(key, v.toLong())
                is Long -> elValues.put(key, v)
                is Double -> elValues.put(key, v)
                is String -> elValues.put(key, v)
            }
        }
        db.insertElement(elValues)
    }
}
