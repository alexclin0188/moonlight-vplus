package com.alexclin.moonlink.stream.ui.panels

import android.content.ContentValues
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

    // ── 选择方案（选中后自动返回子面板，刷新覆盖层） ──
    fun selectScheme(configId: Long) {
        currentConfigId = configId
        prefs.edit().putLong(PREF_CURRENT_CONFIG_ID, configId).apply()
        try {
            engine.setCrownFeatureEnabled(true)
        } catch (_: Exception) { }
        onClose()
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
                    // 类型选择 — FilterChip 形式，两选项一行
                    Text("选择方案类型", style = MaterialTheme.typography.labelMedium,
                         modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = newType == SCHEME_TYPE_VIRTUAL_CONTROLLER,
                            onClick = { newType = SCHEME_TYPE_VIRTUAL_CONTROLLER },
                            label = { Text("虚拟手柄方案", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = newType == SCHEME_TYPE_GAME_KEY_MAPPING,
                            onClick = { newType = SCHEME_TYPE_GAME_KEY_MAPPING },
                            label = { Text("游戏按键映射", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.weight(1f),
                        )
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
                        // 检查同名方案
                        val nameExists = schemes.any { it.name.equals(name, ignoreCase = true) }
                        if (nameExists) {
                            Toast.makeText(context, "已存在同名方案「$name」，请修改名称", Toast.LENGTH_SHORT).show()
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
                                // 使用 DefaultElements 生成默认手柄元素并直接写入新方案
                                val config = com.limelight.preferences.PreferenceConfiguration.readPreferences(context)
                                val dm = context.resources.displayMetrics
                                val isPortrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                                val defaultCvs = com.alexclin.moonlink.stream.ui.overlay.DefaultElements.createDefaultElements(
                                    dm.widthPixels, dm.heightPixels, config, isPortrait
                                )
                                var elementIdCounter = System.currentTimeMillis() + 1
                                for (cv in defaultCvs) {
                                    cv.put("config_id", newId)
                                    cv.put("element_id", elementIdCounter++)
                                    db.insertElement(cv)
                                }
                                // 写入 osc_* 配置
                                val updateCv = ContentValues()
                                updateCv.put("osc_vibrate", 1L)
                                updateCv.put("osc_opacity", 90L)
                                db.updateConfig(newId, updateCv)
                            }

                            refreshSchemes()
                            // 设置当前方案为新创建的方案
                            currentConfigId = newId
                            prefs.edit().putLong(PREF_CURRENT_CONFIG_ID, newId).apply()
                            try {
                                engine.setCrownFeatureEnabled(true)
                                engine.reloadOverlay()
                            } catch (_: Exception) { }
                            showNewDialog = false
                            Toast.makeText(context, "方案「$name」已创建", Toast.LENGTH_SHORT).show()
                            onOpenEditor()
                        } catch (e: Exception) {
                            Toast.makeText(context, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
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

            // ── 搜索栏 + 新建按钮（同行，按钮在行尾） ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索方案名", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showNewDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }

            // ── 方案列表（每行三个 Chip，含内置虚拟手柄方案） ──
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 内置虚拟手柄方案
                val builtIn = SchemeInfo(configId = 0L, name = "内置虚拟手柄",
                    schemeType = SCHEME_TYPE_VIRTUAL_CONTROLLER, isDefault = true)
                val showBuiltIn = searchQuery.isBlank()
                    || "内置虚拟手柄".contains(searchQuery, ignoreCase = true)
                val userSchemes = schemes.filter { it.configId != 0L }
                    .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                val allItems = (if (showBuiltIn) listOf(builtIn) else emptyList()) + userSchemes

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 每行三个 Chip
                    val chunks = allItems.chunked(3)
                    items(chunks, key = { it.joinToString("-") { c -> c.configId.toString() } }) { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            chunk.forEach { scheme ->
                                SchemeChip(
                                    name = scheme.name,
                                    typeIcon = if (scheme.schemeType == "virtual_controller")
                                        Icons.Default.Gamepad else Icons.Default.Keyboard,
                                    isSelected = scheme.configId == currentConfigId,
                                    onSelect = { selectScheme(scheme.configId) },
                                    onLongPress = if (scheme.configId == 0L) null
                                                  else ({ deleteTarget = scheme }),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - chunk.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  方案 Chip（Chip 形式，每行三个）
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SchemeChip(
    name: String,
    typeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongPress ?: {},
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                typeIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
