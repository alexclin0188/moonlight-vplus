package com.alexclin.moonlink.android.settings

import android.content.ContentValues
import android.net.Uri
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.stream.data.ConfigColumns
import com.alexclin.moonlink.android.stream.data.KeymappingDatabaseHelper
import com.alexclin.moonlink.android.stream.ui.panels.SchemeInfo
import com.alexclin.moonlink.android.stream.ui.panels.loadUserSchemes
import com.alexclin.moonlink.android.stream.ui.ScreenScaleHelper
import com.alexclin.moonlink.android.util.MathUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.alexclin.moonlink.android.R

@Composable
fun KeyMappingScreen() {
    val context = LocalContext.current

    var schemes by remember { mutableStateOf<List<SchemeInfo>>(emptyList()) }
    LaunchedEffect(Unit) { schemes = loadUserSchemes(context) }

    // ── .mlk export ──
    var showExportMlkDialog by remember { mutableStateOf(false) }
    var exportMlkTarget by remember { mutableLongStateOf(0L) }

    val mlkCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val db = KeymappingDatabaseHelper(context)
            val scheme = schemes.find { it.configId == exportMlkTarget } ?: return@rememberLauncherForActivityResult
            val json = buildMlkJson(context, db, scheme)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
            ToastUtil.show(context, context.getString(R.string.toast_scheme_exported), Toast.LENGTH_SHORT)
        } catch (e: Exception) {
            ToastUtil.show(context, context.getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_SHORT)
        }
    }

    // ── .mlk import ──
    var showImportMlkDialog by remember { mutableStateOf(false) }
    var importMlkJson by remember { mutableStateOf("") }
    val mlkOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            if (json.isBlank()) {
                ToastUtil.show(context, context.getString(R.string.toast_file_empty), Toast.LENGTH_SHORT)
                return@rememberLauncherForActivityResult
            }
            val root = JSONObject(json)
            // 兼容性校验：优先检查 format 字段，如果没有则通过结构判断（兼容旧版导出的文件）
            val format = root.optString("format")
            if (format.isNotEmpty() && format != "mlk") {
                ToastUtil.show(context, context.getString(R.string.toast_invalid_mlk), Toast.LENGTH_SHORT)
                return@rememberLauncherForActivityResult
            }
            if (format.isEmpty() && (!root.has("config") || !root.has("elements"))) {
                ToastUtil.show(context, context.getString(R.string.toast_invalid_mlk), Toast.LENGTH_SHORT)
                return@rememberLauncherForActivityResult
            }
            importMlkJson = json
            showImportMlkDialog = true
        } catch (e: Exception) {
            ToastUtil.show(context, context.getString(R.string.toast_file_read_failed, e.message ?: ""), Toast.LENGTH_SHORT)
        }
    }

    // ── .mdat legacy Crown import ──
    var showImportMdatDialog by remember { mutableStateOf(false) }
    var importMdatJson by remember { mutableStateOf("") }
    val mdatOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            if (json.isBlank()) {
                ToastUtil.show(context, context.getString(R.string.toast_file_empty), Toast.LENGTH_SHORT)
                return@rememberLauncherForActivityResult
            }
            val root = JSONObject(json)
            if (!root.has("version") || !root.has("settings") || !root.has("elements")) {
                ToastUtil.show(context, context.getString(R.string.toast_invalid_legacy_config), Toast.LENGTH_SHORT)
                return@rememberLauncherForActivityResult
            }
            importMdatJson = json
            showImportMdatDialog = true
        } catch (e: Exception) {
            ToastUtil.show(context, context.getString(R.string.toast_file_read_failed, e.message ?: ""), Toast.LENGTH_SHORT)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

            item {
                ClickablePreference(
                    title = stringResource(R.string.title_export_key_mapping),
                    summary = stringResource(R.string.summary_export_key_mapping),
                    onClick = { if (schemes.isNotEmpty()) showExportMlkDialog = true },
                )
            }
            item {
                ClickablePreference(
                    title = stringResource(R.string.title_import_key_mapping),
                    summary = stringResource(R.string.summary_import_key_mapping),
                    onClick = { mlkOpenLauncher.launch(arrayOf("*/*")) },
                )
            }
            item {
                ClickablePreference(
                    title = stringResource(R.string.title_import_from_legacy_key_mapping),
                    summary = stringResource(R.string.summary_import_from_legacy_key_mapping),
                    onClick = { mdatOpenLauncher.launch(arrayOf("application/json", "*/*")) },
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ── Dialog: Export .mlk ──
    if (showExportMlkDialog) {
        SchemeSelectionDialog(
            title = stringResource(R.string.title_export_mlk_dialog),
            schemes = schemes,
            onSelect = { scheme ->
                exportMlkTarget = scheme.configId
                showExportMlkDialog = false
                val safeName = scheme.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                mlkCreateLauncher.launch("${safeName}.mlk")
            },
            onDismiss = { showExportMlkDialog = false },
        )
    }

    // ── Dialog: Import .mlk ──
    if (showImportMlkDialog) {
        ImportMlkDialog(
            json = importMlkJson,
            schemes = schemes,
            onConfirm = { name, overrideTargetId ->
                try {
                    importMlkFromJson(context, importMlkJson, name, overrideTargetId)
                    schemes = loadUserSchemes(context)
                    ToastUtil.show(context, context.getString(R.string.toast_scheme_imported, name), Toast.LENGTH_SHORT)
                } catch (e: Exception) {
                    ToastUtil.show(context, context.getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_SHORT)
                }
                showImportMlkDialog = false
            },
            onDismiss = { showImportMlkDialog = false },
        )
    }

    // ── Dialog: Import legacy Crown .mdat ──
    if (showImportMdatDialog) {
        ImportMdatDialog(
            json = importMdatJson,
            schemes = schemes,
            onConfirm = { name, overrideTargetId ->
                try {
                    importMdatFromJson(context, importMdatJson, name, overrideTargetId)
                    schemes = loadUserSchemes(context)
                    ToastUtil.show(context, context.getString(R.string.toast_scheme_imported_from_legacy, name), Toast.LENGTH_SHORT)
                } catch (e: Exception) {
                    ToastUtil.show(context, context.getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_SHORT)
                }
                showImportMdatDialog = false
            },
            onDismiss = { showImportMdatDialog = false },
        )
    }
}

// ── Scheme selection dialog ──

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
                onClick = { schemes.find { it.configId == selectedId }?.let { onSelect(it) } },
                enabled = schemes.any { it.configId == selectedId },
            ) { Text(stringResource(R.string.btn_export)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

// ── .mlk import dialog ──

@Composable
private fun ImportMlkDialog(
    json: String,
    schemes: List<SchemeInfo>,
    onConfirm: (name: String, overrideTargetId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val defaultName = context.getString(R.string.label_import_scheme)
    val parsedName = remember(json, defaultName) {
        try { JSONObject(json).optString("name", defaultName) }
        catch (_: Exception) { defaultName }
    }
    var name by remember { mutableStateOf(parsedName) }
    var selectedId by remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_import_key_mapping)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text(stringResource(R.string.hint_scheme_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.label_overwrite_existing), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = 0L }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedId == 0L, onClick = { selectedId = 0L })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.option_import_as_new), style = MaterialTheme.typography.bodyMedium)
                }

                schemes.forEach { scheme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = scheme.configId }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedId == scheme.configId, onClick = { selectedId = scheme.configId })
                        Spacer(Modifier.width(8.dp))
                        Text(scheme.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { val n = name.trim(); if (n.isNotEmpty()) onConfirm(n, selectedId) },
                enabled = name.trim().isNotEmpty(),
            ) { Text(stringResource(R.string.btn_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

// ── .mlk build & parse ──

private fun buildMlkJson(context: android.content.Context, db: KeymappingDatabaseHelper, scheme: SchemeInfo): String {
    val configJson = JSONObject()
    val configAttrs = listOf(
        "touch_enable" to "boolean",
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
                "boolean" -> configJson.put(key, value.toString())
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
        put("format", "mlk")
        put("version", 2)
        put("name", scheme.name)
        put("created", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
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

private fun importMlkFromJson(context: android.content.Context, json: String, newName: String, overrideTargetId: Long = 0L) {
    val root = JSONObject(json)
    val configObj = root.optJSONObject("config") ?: JSONObject()
    val elementsArray = root.optJSONArray("elements") ?: JSONArray()
    val sourceWidth = root.optInt(ScreenScaleHelper.KEY_SOURCE_WIDTH, 0)
    val sourceHeight = root.optInt(ScreenScaleHelper.KEY_SOURCE_HEIGHT, 0)
    val db = KeymappingDatabaseHelper(context)

    if (overrideTargetId != 0L) {
        db.deleteConfig(overrideTargetId)
    }

    val newConfigId = if (overrideTargetId != 0L) overrideTargetId else System.currentTimeMillis()
    val (targetWidth, targetHeight) = try {
        ScreenScaleHelper.getDeviceScreenSize(context)
    } catch (_: Exception) { Pair(0, 0) }

    // 旧列黑名单（已废弃，导入时跳过）
    val deprecatedConfigKeys = setOf("global_border_color", "global_text_color")
    val configValues = ContentValues().apply {
        put(ConfigColumns.COLUMN_LONG_CONFIG_ID, newConfigId)
        put(ConfigColumns.COLUMN_STRING_CONFIG_NAME, newName)
        for (key in configObj.keys()) {
            if (key in deprecatedConfigKeys) continue
            val v = configObj.opt(key)
            when (v) {
                is Boolean -> put(key, v.toString())
                is Int -> put(key, v)
                is String -> put(key, v)
                is Long -> put(key, v)
                is Double -> put(key, v.toFloat())
            }
        }
        if (!containsKey(ConfigColumns.COLUMN_BOOLEAN_TOUCH_ENABLE)) {
            put(ConfigColumns.COLUMN_BOOLEAN_TOUCH_ENABLE, "true")
        }
    }
    db.insertConfig(configValues)

    val existingIds = db.queryAllElementIds(newConfigId).toSet()
    var elementIdCounter = 1L
    while (elementIdCounter in existingIds) elementIdCounter++
    for (i in 0 until elementsArray.length()) {
        val elObj = elementsArray.getJSONObject(i)
        val elType = elObj.optInt("element_type", -1)
        if (elType == 4 || elType == 54) continue
        val elValues = ContentValues()
        elValues.put("config_id", newConfigId)
        elValues.put("element_id", elementIdCounter++)
        for (key in elObj.keys()) {
            if (key == "config_id" || key == "element_id" || key == "_id") continue
            val v = elObj.opt(key)
            when (v) {
                is Boolean -> elValues.put(key, v)
                is Number -> elValues.put(key, (v as Number).toLong())
                is String -> elValues.put(key, v)
            }
        }
        if (sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0) {
            ScreenScaleHelper.scaleElementContentValues(elValues, sourceWidth, sourceHeight, targetWidth, targetHeight)
        }
        db.insertElement(elValues)
    }
}

// ── Legacy Crown .mdat import dialog ──

private fun parseMdatSchemeName(json: String): String {
    return try {
        val root = JSONObject(json)
        val settingsStr = root.optString("settings", "{}")
        val settingsObj = JSONObject(settingsStr)
        settingsObj.optString(ConfigColumns.COLUMN_STRING_CONFIG_NAME,
            settingsObj.optString("config_name", "Old Crown Scheme"))
    } catch (_: Exception) { "Old Crown Scheme" }
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
        title = { Text(stringResource(R.string.title_import_legacy_key_mapping_dialog)) },
        text = {
            Column {
                Text(stringResource(R.string.desc_legacy_key_mapping_detected), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text(stringResource(R.string.hint_scheme_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.label_overwrite_existing), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = 0L }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedId == 0L, onClick = { selectedId = 0L })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.option_import_as_new), style = MaterialTheme.typography.bodyMedium)
                }

                schemes.forEach { scheme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = scheme.configId }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedId == scheme.configId, onClick = { selectedId = scheme.configId })
                        Spacer(Modifier.width(8.dp))
                        Text(scheme.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { val n = name.trim(); if (n.isNotEmpty()) onConfirm(n, selectedId) },
                enabled = name.trim().isNotEmpty(),
            ) { Text(stringResource(R.string.btn_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}

// ── Legacy Crown .mdat import logic ──

private fun importMdatFromJson(
    context: android.content.Context,
    json: String,
    newName: String,
    overrideTargetId: Long = 0L,
) {
    val root = JSONObject(json)
    val settingsStr = root.getString("settings")
    val elementsStr = root.getString("elements")
    val sourceWidth = root.optInt("sourceWidth", 0)
    val sourceHeight = root.optInt("sourceHeight", 0)

    val settingsObj = JSONObject(settingsStr)
    settingsObj.put(ConfigColumns.COLUMN_STRING_CONFIG_NAME, newName)
    settingsObj.remove(ConfigColumns.COLUMN_LONG_CONFIG_ID)
    val updatedSettingsStr = settingsObj.toString()

    val db = KeymappingDatabaseHelper(context)
    if (overrideTargetId != 0L) {
        db.deleteConfig(overrideTargetId)
    }

    val CURRENT_DB_VERSION = 1
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
                -1 -> context.getString(R.string.error_file_format)
                -2 -> context.getString(R.string.error_file_checksum)
                -3 -> context.getString(R.string.error_version_incompatible)
                else -> context.getString(R.string.error_import_failed_code, result)
            }
        )
    }

    val importedConfigId = if (overrideTargetId != 0L) overrideTargetId else {
        db.queryAllConfigIds().maxOrNull() ?: return
    }
    cleanupDeletedElementTypes(db, importedConfigId)
}

private fun cleanupDeletedElementTypes(db: KeymappingDatabaseHelper, configId: Long) {
    val deletedTypes = setOf(4, 54)
    val elementIds = db.queryAllElementIds(configId) ?: return
    for (eid in elementIds) {
        val attrs = db.queryAllElementAttributes(configId, eid) ?: continue
        val elementType = attrs["element_type"]
        if (elementType is Number && elementType.toInt() in deletedTypes) {
            db.deleteElement(configId, eid)
        }
    }
}
