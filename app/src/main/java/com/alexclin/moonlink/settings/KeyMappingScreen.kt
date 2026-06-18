package com.alexclin.moonlink.settings

import android.content.ContentValues
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 方案数据模型 ──
private data class ConfigScheme(
    val configId: Long,
    val name: String,
)

@Composable
fun KeyMappingScreen() {
    val context = LocalContext.current

    // ── 加载方案列表 ──
    var schemes by remember { mutableStateOf<List<ConfigScheme>>(emptyList()) }

    fun refreshSchemes() {
        try {
            val db = SuperConfigDatabaseHelper(context)
            val ids = db.queryAllConfigIds()
            schemes = ids.map { id ->
                val name = db.queryConfigAttribute(id, PageConfigController.COLUMN_STRING_CONFIG_NAME, "未命名") as? String ?: "未命名"
                ConfigScheme(id, name)
            }
        } catch (_: Exception) { }
    }

    LaunchedEffect(Unit) { refreshSchemes() }

    // ── .mdat 导出 ──
    var showExportMdatDialog by remember { mutableStateOf(false) }
    var exportMdatTarget by remember { mutableLongStateOf(0L) }

    val mdatCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val db = SuperConfigDatabaseHelper(context)
            val json = db.exportConfig(exportMdatTarget)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "按键配置已导出 (.mdat)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── .mdat 导入 ──
    var showImportMdatDialog by remember { mutableStateOf(false) }
    var importMdatJson by remember { mutableStateOf("") }
    var importMdatMergeTarget by remember { mutableLongStateOf(0L) }
    var importMdatMode by remember { mutableStateOf("new") }  // "new" or "merge"

    val mdatOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            if (json.isBlank()) {
                Toast.makeText(context, "文件内容为空", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            importMdatJson = json
            showImportMdatDialog = true
        } catch (e: Exception) {
            Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── .mkmp 导出 ──
    var showExportMkmpDialog by remember { mutableStateOf(false) }
    var exportMkmpTarget by remember { mutableLongStateOf(0L) }

    val mkmpCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val db = SuperConfigDatabaseHelper(context)
            val scheme = schemes.find { it.configId == exportMkmpTarget } ?: return@rememberLauncherForActivityResult
            val json = buildMkmpJson(db, scheme)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "按键方案已导出 (.mkmp)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── .mkmp 导入 ──
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

    // ════════════════════════════════════════════
    // 页面主体
    // ════════════════════════════════════════════
    Column(modifier = Modifier.fillMaxSize()) {
        // 标题
        Text(
            "按键配置管理",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // ── 导出按键配置 (.mdat) ──
            item {
                ClickablePreference(
                    title = "导出按键配置",
                    summary = "将按键配置导出为 .mdat 文件（含设置 + 元素）",
                    onClick = { if (schemes.isNotEmpty()) showExportMdatDialog = true },
                )
            }

            // ── 导入按键配置 (.mdat) ──
            item {
                ClickablePreference(
                    title = "导入按键配置",
                    summary = "从 .mdat 文件导入按键配置（新建或合并）",
                    onClick = {
                        mdatOpenLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

            // ── 导出按键方案 (.mkmp) ──
            item {
                ClickablePreference(
                    title = "导出按键方案",
                    summary = "将按键方案导出为 .mkmp 文件（简化格式）",
                    onClick = { if (schemes.isNotEmpty()) showExportMkmpDialog = true },
                )
            }

            // ── 导入按键方案 (.mkmp) ──
            item {
                ClickablePreference(
                    title = "导入按键方案",
                    summary = "从 .mkmp 文件导入按键方案",
                    onClick = {
                        mkmpOpenLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ════════════════════════════════════════════
    // Dialog：导出 .mdat
    // ════════════════════════════════════════════
    if (showExportMdatDialog) {
        SchemeSelectionDialog(
            title = "导出按键配置 (.mdat)",
            schemes = schemes,
            onSelect = { scheme ->
                exportMdatTarget = scheme.configId
                showExportMdatDialog = false
                // 文件名建议
                val safeName = scheme.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                mdatCreateLauncher.launch("${safeName}.mdat")
            },
            onDismiss = { showExportMdatDialog = false },
        )
    }

    // ════════════════════════════════════════════
    // Dialog：导入 .mdat（合并确认）
    // ════════════════════════════════════════════
    if (showImportMdatDialog) {
        ImportMdatDialog(
            json = importMdatJson,
            schemes = schemes,
            mode = importMdatMode,
            onModeChange = { importMdatMode = it },
            mergeTarget = importMdatMergeTarget,
            onMergeTargetChange = { importMdatMergeTarget = it },
            onConfirm = { mode, targetId ->
                try {
                    val db = SuperConfigDatabaseHelper(context)
                    val result = if (mode == "merge") {
                        db.mergeConfig(importMdatJson, targetId)
                    } else {
                        db.importConfig(importMdatJson)
                    }
                    when (result) {
                        -1 -> Toast.makeText(context, "文件格式错误", Toast.LENGTH_SHORT).show()
                        -2 -> Toast.makeText(context, "文件已被篡改或损坏", Toast.LENGTH_SHORT).show()
                        -3 -> Toast.makeText(context, "版本不匹配", Toast.LENGTH_SHORT).show()
                        else -> {
                            refreshSchemes()
                            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                showImportMdatDialog = false
            },
            onDismiss = { showImportMdatDialog = false },
        )
    }

    // ════════════════════════════════════════════
    // Dialog：导出 .mkmp
    // ════════════════════════════════════════════
    if (showExportMkmpDialog) {
        SchemeSelectionDialog(
            title = "导出按键方案 (.mkmp)",
            schemes = schemes,
            onSelect = { scheme ->
                exportMkmpTarget = scheme.configId
                showExportMkmpDialog = false
                val safeName = scheme.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                mkmpCreateLauncher.launch("${safeName}.mkmp")
            },
            onDismiss = { showExportMkmpDialog = false },
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
}

// ════════════════════════════════════════════════════════════
//  通用组件（使用 PreferenceComponents 中的 ClickablePreference）
// ════════════════════════════════════════════════════════════

@Composable
private fun SchemeSelectionDialog(
    title: String,
    schemes: List<ConfigScheme>,
    onSelect: (ConfigScheme) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableLongStateOf(schemes.firstOrNull()?.configId ?: 0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                schemes.forEach { scheme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = scheme.configId }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedId == scheme.configId,
                            onClick = { selectedId = scheme.configId },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(scheme.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    schemes.find { it.configId == selectedId }?.let { onSelect(it) }
                },
                enabled = schemes.any { it.configId == selectedId },
            ) { Text("导出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ════════════════════════════════════════════════════════════
//  .mdat 导入确认弹窗（T-13）
// ════════════════════════════════════════════════════════════

@Composable
private fun ImportMdatDialog(
    json: String,
    schemes: List<ConfigScheme>,
    mode: String,
    onModeChange: (String) -> Unit,
    mergeTarget: Long,
    onMergeTargetChange: (Long) -> Unit,
    onConfirm: (mode: String, targetId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var localMode by remember { mutableStateOf(mode) }
    var localTarget by remember { mutableStateOf(mergeTarget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入按键配置") },
        text = {
            Column {
                // 新建方案
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { localMode = "new" }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = localMode == "new", onClick = { localMode = "new" })
                    Spacer(Modifier.width(8.dp))
                    Text("新建方案", style = MaterialTheme.typography.bodyMedium)
                }

                // 合并到所选方案
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { localMode = "merge" }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = localMode == "merge", onClick = { localMode = "merge" })
                    Spacer(Modifier.width(8.dp))
                    Text("合并到所选方案", style = MaterialTheme.typography.bodyMedium)
                }

                if (localMode == "merge") {
                    schemes.filter { it.configId != 0L }.forEach { scheme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { localTarget = scheme.configId }
                                .padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = localTarget == scheme.configId,
                                        onClick = { localTarget = scheme.configId })
                            Spacer(Modifier.width(8.dp))
                            Text(scheme.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localMode, localTarget) }) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ════════════════════════════════════════════════════════════
//  .mkmp 导入弹窗（T-15 前置集成）
// ════════════════════════════════════════════════════════════

@Composable
private fun ImportMkmpDialog(
    json: String,
    schemes: List<ConfigScheme>,
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

// ════════════════════════════════════════════════════════════
//  .mkmp 构建与解析（T-14）
// ════════════════════════════════════════════════════════════

/**
 * 构建 .mkmp 简化 JSON。
 * 不含 MD5/版本号等内部元数据。
 */
private fun buildMkmpJson(db: SuperConfigDatabaseHelper, scheme: ConfigScheme): String {
    val configJson = JSONObject()
    val configAttrs = listOf(
        "touch_enable" to "boolean",
        "touch_mode" to "boolean",
        "touch_sense" to "int",
        "mouse_wheel_speed" to "int",
        "game_vibrator" to "boolean",
        "button_vibrator" to "boolean",
        "enhanced_touch" to "boolean",
        "global_opacity" to "int",
    )
    for ((key, type) in configAttrs) {
        val value = db.queryConfigAttribute(scheme.configId, key, null)
        if (value != null) {
            when (type) {
                "boolean" -> configJson.put(key, java.lang.Boolean.parseBoolean(value.toString()))
                "int" -> configJson.put(key, (value as Number).toInt())
                else -> configJson.put(key, value.toString())
            }
        }
    }

    val elementIds = db.queryAllElementIds(scheme.configId) ?: emptyList()
    val elementsArray = JSONArray()
    for (eid in elementIds) {
        val attrs = db.queryAllElementAttributes(scheme.configId, eid) ?: continue
        val el = JSONObject()
        for ((k, v) in attrs) {
            // 保留元素属性的原始类型（Boolean/Number/String）
            when (v) {
                is Boolean -> el.put(k, v)
                is Number -> el.put(k, v)
                is String -> el.put(k, v)
                else -> el.put(k, v.toString())
            }
        }
        elementsArray.put(el)
    }

    val root = JSONObject().apply {
        put("format", "mkmp")
        put("version", 1)
        put("name", scheme.name)
        put("created", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        put("config", configJson)
        put("elements", elementsArray)
    }
    return root.toString(2)
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
