package com.alexclin.moonlink.android.stream.ui.panels

import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.alexclin.moonlink.android.stream.engine.StreamEngine

/**
 * 按键映射方案选择全屏页面。
 *
 * - 搜索栏
 * - 新建按钮（直接跳转编辑器，不弹 Dialog）
 * - 方案列表（圆角 Chip）
 *   - 短按 → 选中方案 + 返回子面板
 *   - 长按 → 确认弹窗 → 删除（仅用户方案）
 * - 内置方案固定置顶（代码生成，不存 DB）
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
    var currentConfigId by remember { mutableLongStateOf(prefs.getLong(StreamEngine.PREF_CURRENT_CONFIG_ID, 0L)) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        schemes = loadUserSchemes(context)
        isLoading = false
    }

    // ── 选择方案（选中后自动返回子面板，刷新覆盖层） ──
    fun selectScheme(configId: Long) {
        currentConfigId = configId
        prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, configId).apply()
        try {
            engine.setKeyMappingEnabled(true)
        } catch (_: Exception) { }
        onClose()
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
                            val db = com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper(context)
                            db.deleteConfig(target.configId)
                            schemes = loadUserSchemes(context)
                            if (currentConfigId == target.configId) {
                                selectScheme(0L)
                            }
                            ToastUtil.show(context, "已删除", Toast.LENGTH_SHORT)
                        } catch (e: Exception) {
                            ToastUtil.show(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT)
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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    "按键映射方案",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                Button(onClick = {
                    // 设置哨兵值标记新建模式
                    prefs.edit().putLong(StreamEngine.PREF_CURRENT_CONFIG_ID, -1L).apply()
                    onOpenEditor()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }

            // ── 方案列表（每行三个 Chip，内置方案 + 用户方案） ──
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 内置方案（代码固定生成，不存 DB）
                val builtIn = SchemeInfo(configId = 0L, name = "内置虚拟手柄方案", isDefault = true)
                val showBuiltIn = searchQuery.isBlank()
                    || "内置虚拟手柄方案".contains(searchQuery, ignoreCase = true)
                val userSchemes = schemes
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
                                    isSelected = scheme.configId == currentConfigId,
                                    onSelect = { selectScheme(scheme.configId) },
                                    onLongPress = if (scheme.configId == 0L || scheme.isDefault) null
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
//  方案 Chip
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SchemeChip(
    name: String,
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
                Icons.Default.Gamepad,
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
