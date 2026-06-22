package com.alexclin.moonlink.android.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import com.alexclin.moonlink.android.stream.ui.panels.SchemeInfo
import com.alexclin.moonlink.android.stream.ui.panels.loadUserSchemes
import com.alexclin.moonlink.android.stream.ui.ScreenScaleHelper
import com.limelight.utils.MathUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KeyMappingScreen() {
    val context = LocalContext.current

    // ── 加载方案列表 ──
    var schemes by remember { mutableStateOf<List<SchemeInfo>>(emptyList()) }

    LaunchedEffect(Unit) { schemes = loadUserSchemes(context) }

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
            val json = buildMkmpJson(context, db, scheme)
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

    // ── .mdat 旧王冠配置导入 ──
    var showImportMdatDialog by remember { mutableStateOf(false) }
    var importMdatJson by remember { mutableStateOf("") }
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
            // 验证是否为有效的旧版配置文件（ExportFile 格式）
            val root = JSONObject(json)
            if (!root.has("version") || !root.has("settings") || !root.has("elements")) {
                Toast.makeText(context, "这不是有效的旧王冠配置文件", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            importMdatJson = json
            showImportMdatDialog = true
        } catch (e: Exception) {
            Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ════════════════════════════════════════════
    // 页面主体
    // ════════════════════════════════════════════
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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

            // ── 从旧王冠配置导入 (.mdat) ──
            item {
                ClickablePreference(
                    title = "从旧王冠配置导入",
                    summary = "从旧版 Crown 导出的 .mdat 文件导入按键映射方案",
                    onClick = {
                        mdatOpenLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
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
                    schemes = loadUserSchemes(context)
                    Toast.makeText(context, "方案「$name」已导入", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                showImportMkmpDialog = false
            },
            onDismiss = { showImportMkmpDialog = false },
        )
    }

    // ════════════════════════════════════════════
    // Dialog：导入旧王冠配置 (.mdat)
    // ════════════════════════════════════════════
    if (showImportMdatDialog) {
        ImportMdatDialog(
            json = importMdatJson,
            schemes = schemes,
            onConfirm = { name, overrideTargetId ->
                try {
                    importMdatFromJson(context, importMdatJson, name, overrideTargetId)
                    schemes = loadUserSchemes(context)
                    Toast.makeText(context, "方案「$name」已从旧配置导入", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                showImportMdatDialog = false
            },
            onDismiss = { showImportMdatDialog = false },
        )
    }
}

// ════════════════════════════════════════════════════════════
//  通用组件（使用 PreferenceComponents 中的 ClickablePreference）
// ════════════════════════════════════════════════════════════

@Composable
private fun SchemeSelectionDialog(
    title: String,
    schemes: List<SchemeInfo>,
    onSelect: (SchemeInfo) -> Unit,
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
//  .mkmp 导入弹窗
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

// ════════════════════════════════════════════════════════════
//  .mkmp 构建与解析（T-14）
// ════════════════════════════════════════════════════════════

/**
 * 构建 .mkmp 简化 JSON。
 * 不含 MD5/版本号等内部元数据。
 */
private fun buildMkmpJson(context: android.content.Context, db: SuperConfigDatabaseHelper, scheme: SchemeInfo): String {
    val configJson = JSONObject()
    val configAttrs = listOf(
        "touch_enable" to "boolean",
        "touch_mode" to "boolean",
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
        put("version", 2)  // v2: 增加 sourceWidth/sourceHeight 用于屏幕参数换算
        put("name", scheme.name)
        put("created", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        // 写入源设备屏幕尺寸，供导入时坐标缩放
        try {
            val (w, h) = ScreenScaleHelper.getDeviceScreenSize(context)
            put(ScreenScaleHelper.KEY_SOURCE_WIDTH, w)
            put(ScreenScaleHelper.KEY_SOURCE_HEIGHT, h)
        } catch (_: Exception) { }
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

    // ── 读取源设备屏幕尺寸，计算缩放 ──
    val sourceWidth = root.optInt(ScreenScaleHelper.KEY_SOURCE_WIDTH, 0)
    val sourceHeight = root.optInt(ScreenScaleHelper.KEY_SOURCE_HEIGHT, 0)

    val db = SuperConfigDatabaseHelper(context)

    // 覆盖模式：先删除旧数据，复用原有 config_id
    if (overrideTargetId != 0L) {
        db.deleteConfig(overrideTargetId)
    }

    val newConfigId = if (overrideTargetId != 0L) overrideTargetId else System.currentTimeMillis()

    // ── 获取目标设备屏幕尺寸 ──
    val (targetWidth, targetHeight) = try {
        ScreenScaleHelper.getDeviceScreenSize(context)
    } catch (_: Exception) { Pair(0, 0) }

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

    // 写入 elements（带屏幕参数换算）
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
        // 对每个元素应用坐标缩放
        if (sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0) {
            ScreenScaleHelper.scaleElementContentValues(
                elValues, sourceWidth, sourceHeight, targetWidth, targetHeight
            )
        }
        db.insertElement(elValues)
    }
}

// ════════════════════════════════════════════════════════════
//  旧王冠配置 (.mdat) 导入弹窗
// ════════════════════════════════════════════════════════════

/**
 * 从旧版 Crown 导出的 .mdat 文件中解析按键方案名称。
 * .mdat 是 ExportFile JSON 格式：{ version, settings, elements, md5, ... }
 * 方案名称存储在 settings 对象的 config_name 字段中。
 */
private fun parseMdatSchemeName(json: String): String {
    return try {
        val root = JSONObject(json)
        val settingsStr = root.optString("settings", "{}")
        val settingsObj = JSONObject(settingsStr)
        settingsObj.optString(
            PageConfigController.COLUMN_STRING_CONFIG_NAME,
            settingsObj.optString("config_name", "旧 Crown 方案")
        )
    } catch (_: Exception) { "旧 Crown 方案" }
}

@Composable
private fun ImportMdatDialog(
    json: String,
    schemes: List<SchemeInfo>,
    onConfirm: (name: String, overrideTargetId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val parsedName = remember { parseMdatSchemeName(json) }
    var name by remember { mutableStateOf(parsedName) }
    var selectedId by remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入旧王冠配置") },
        text = {
            Column {
                Text(
                    "检测到旧版 Crown 配置文件，将导入其中的按键映射方案。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))

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
//  旧王冠配置 (.mdat) 导入逻辑
// ════════════════════════════════════════════════════════════

/**
 * 从旧版 Crown 导出的 .mdat 文件导入按键映射方案。
 *
 * .mdat 格式与 [SuperConfigDatabaseHelper.exportConfig] 输出的 ExportFile 一致，
 * 但版本号可能不同（旧 Crown 为 version 8，当前为 1）。
 *
 * 处理流程：
 * 1. 解析 settings 中旧的 config_name 并用新名称替换
 * 2. 将 version 重写为当前 DATABASE_VERSION (1)
 * 3. 重新计算 MD5 校验和
 * 4. 调用 [SuperConfigDatabaseHelper.importConfig] 执行实际导入
 *    （包含元素引用修复、屏幕坐标缩放、normalizeGlobalStyleSettings 等完整处理）
 *
 * @param overrideTargetId 不为 0L 时覆盖该已有方案，否则创建新方案
 */
private fun importMdatFromJson(
    context: android.content.Context,
    json: String,
    newName: String,
    overrideTargetId: Long = 0L,
) {
    val root = JSONObject(json)

    // 提取并更新 settings 中的方案名称
    val settingsStr = root.getString("settings")
    val elementsStr = root.getString("elements")
    val sourceWidth = root.optInt("sourceWidth", 0)
    val sourceHeight = root.optInt("sourceHeight", 0)

    val settingsObj = JSONObject(settingsStr)
    settingsObj.put(PageConfigController.COLUMN_STRING_CONFIG_NAME, newName)
    // 移除 config_id 让 importConfig 自动分配新的（避免冲突）
    settingsObj.remove(PageConfigController.COLUMN_LONG_CONFIG_ID)
    val updatedSettingsStr = settingsObj.toString()

    val db = SuperConfigDatabaseHelper(context)

    // 覆盖模式：先删除旧方案数据
    if (overrideTargetId != 0L) {
        db.deleteConfig(overrideTargetId)
    }

    // 构建新的 ExportFile JSON（version=1, 全新 MD5）
    val CURRENT_DB_VERSION = 1  // SuperConfigDatabaseHelper.DATABASE_VERSION
    val md5 = MathUtils.computeMD5("$CURRENT_DB_VERSION$updatedSettingsStr$elementsStr")

    val exportObj = JSONObject().apply {
        put("version", CURRENT_DB_VERSION)
        put("settings", updatedSettingsStr)
        put("elements", elementsStr)
        put("md5", md5)
        if (sourceWidth > 0) put("sourceWidth", sourceWidth)
        if (sourceHeight > 0) put("sourceHeight", sourceHeight)
    }

    val result = db.importConfig(exportObj.toString())
    if (result != 0) {
        throw Exception(
            when (result) {
                -1 -> "文件格式错误"
                -2 -> "文件校验失败（已损坏）"
                -3 -> "版本不兼容"
                else -> "导入失败 (错误: $result)"
            }
        )
    }
}
