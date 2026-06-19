package com.alexclin.moonlink.stream.ui.panels

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

/** 当前方案的 SharedPreference key，与旧 PageConfigController 互通 */
private const val PREF_CURRENT_CONFIG_ID = "current_config_id"

/** 方案类型常量 */
private const val SCHEME_TYPE_VIRTUAL_CONTROLLER = "virtual_controller"
private const val SCHEME_TYPE_GAME_KEY_MAPPING = "game_key_mapping"

/** 方案数据模型 */
private data class SchemeInfo(
    val configId: Long,
    val name: String,
    val schemeType: String = SCHEME_TYPE_GAME_KEY_MAPPING,
    val isDefault: Boolean = configId == 0L,
)

/**
 * 按键映射方案选择全屏页面。
 *
 * - 搜索栏 (保留)
 * - 新建按钮
 * - 方案列表（圆角卡片）
 *   - 短按 → 选中方案 + 返回子面板
 *   - 长按 → 确认弹窗 → 删除（仅用户方案）
 * - 内置虚拟手柄方案固定置顶（选中即选方案，无独立配置展开）
 */
@OptIn(ExperimentalFoundationApi::class)
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

    fun refreshSchemes() {
        try {
            val db = SuperConfigDatabaseHelper(context)
            val ids = db.queryAllConfigIds()
            val list = ids.map { id ->
                val name = db.queryConfigAttribute(id, PageConfigController.COLUMN_STRING_CONFIG_NAME, "未命名") as? String ?: "未命名"
                val type = db.queryConfigAttribute(id, "scheme_type", SCHEME_TYPE_GAME_KEY_MAPPING) as? String ?: SCHEME_TYPE_GAME_KEY_MAPPING
                SchemeInfo(configId = id, name = name, schemeType = type)
            }
            schemes = list
        } catch (_: Exception) { }
        isLoading = false
    }

    LaunchedEffect(Unit) { refreshSchemes() }

    // ── 选择方案 ──
    fun selectScheme(configId: Long) {
        currentConfigId = configId
        prefs.edit().putLong(PREF_CURRENT_CONFIG_ID, configId).apply()
        try {
            engine.setCrownFeatureEnabled(true)
            engine.controllerManager?.refreshLayout()
        } catch (_: Exception) { }
    }

    // ── 新建方案 Dialog ──
    var showNewDialog by remember { mutableStateOf(false) }
    if (showNewDialog) {
        var newName by remember { mutableStateOf("") }
        var newType by remember { mutableStateOf(SCHEME_TYPE_VIRTUAL_CONTROLLER) }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建按键方案") },
            text = {
                Column {
                    // 类型选择
                    Text("选择方案类型", style = MaterialTheme.typography.labelMedium,
                         modifier = Modifier.padding(bottom = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                         modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        RadioButton(selected = newType == SCHEME_TYPE_VIRTUAL_CONTROLLER,
                            onClick = { newType = SCHEME_TYPE_VIRTUAL_CONTROLLER })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("虚拟手柄方案", style = MaterialTheme.typography.bodyMedium)
                            Text("复制内置手柄布局", style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                         modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        RadioButton(selected = newType == SCHEME_TYPE_GAME_KEY_MAPPING,
                            onClick = { newType = SCHEME_TYPE_GAME_KEY_MAPPING })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("游戏按键映射", style = MaterialTheme.typography.bodyMedium)
                            Text("创建空白方案", style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 10) newName = it },
                        label = { Text("方案名称（1-10 字符）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                            val newId = System.currentTimeMillis()
                            val cv = ContentValues()
                            cv.put(PageConfigController.COLUMN_LONG_CONFIG_ID, newId)
                            cv.put(PageConfigController.COLUMN_STRING_CONFIG_NAME, name)
                            cv.put("scheme_type", newType)
                            cv.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_ENABLE, "true")
                            cv.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, "true")
                            db.insertConfig(cv)

                            if (newType == SCHEME_TYPE_VIRTUAL_CONTROLLER) {
                                // 复制内置方案（config_id=0）的 elements 到新方案
                                val defaultElementIds = db.queryAllElementIds(0L)
                                var elementIdCounter = System.currentTimeMillis() + 1
                                for (oldEid in defaultElementIds) {
                                    val attrs = db.queryAllElementAttributes(0L, oldEid)
                                    val elCv = ContentValues()
                                    for ((key, value) in attrs) {
                                        if (key == "_id" || key == "config_id" || key == "element_id") continue
                                        when (value) {
                                            is Long -> elCv.put(key, value)
                                            is String -> elCv.put(key, value)
                                            is Double -> elCv.put(key, value)
                                            is ByteArray -> elCv.put(key, value)
                                        }
                                    }
                                    elCv.put("config_id", newId)
                                    elCv.put("element_id", elementIdCounter++)
                                    db.insertElement(elCv)
                                }
                                // 复制 osc_* 配置
                                val defaultOscVibrate = db.queryConfigAttribute(0L, "osc_vibrate", 1) as? Long ?: 1L
                                val defaultOscOpacity = db.queryConfigAttribute(0L, "osc_opacity", 90) as? Long ?: 90L
                                val defaultOscLayout = db.queryConfigAttribute(0L, "osc_element_layout", null) as? String
                                val updateCv = ContentValues()
                                updateCv.put("osc_vibrate", defaultOscVibrate)
                                updateCv.put("osc_opacity", defaultOscOpacity)
                                if (defaultOscLayout != null) {
                                    updateCv.put("osc_element_layout", defaultOscLayout)
                                }
                                db.updateConfig(newId, updateCv)
                            }

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

    // ── 页面主体 ──
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶部标题栏 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "按键映射方案",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── 搜索栏（保留） ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索方案名") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
            )

            // ── 新建按钮行（移除导入按钮，仅保留新建） ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { showNewDialog = true }) {
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
                            schemeType = SCHEME_TYPE_VIRTUAL_CONTROLLER,
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
                            onLongPress = {
                                if (!scheme.isDefault) {
                                    deleteTarget = scheme
                                }
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
//  内置虚拟手柄方案卡片
// ════════════════════════════════════════════════════════════

@Composable
private fun VirtualControllerSchemeCard(
    schemeType: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { /* 内置方案不可删除 */ },
            )
            .padding(12.dp),
    ) {
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
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  用户方案卡片（圆角矩形 + 长按删除）
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SchemeCard(
    scheme: SchemeInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPress,
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 方案类型图标
            val typeIcon = if (scheme.schemeType == "virtual_controller") Icons.Default.Gamepad
                           else Icons.Default.Keyboard
            Icon(
                if (isSelected) Icons.Default.CheckCircle else typeIcon,
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
        }
    }
}
