package com.alexclin.moonlink.android.settings

import android.net.Uri
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.settings.BackgroundSource
import com.alexclin.moonlink.android.R
import java.io.File
import java.io.FileOutputStream

@Composable
fun UiSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.of(context) }

    // Track current source reactively — re-read when prefs change
    var currentSource by remember { mutableStateOf(BackgroundSource.current(context)) }

    // API URL state
    var apiUrl by remember { mutableStateOf(prefs.getString(BackgroundSource.KEY_API_URL, "") ?: "") }
    var apiUrlDialogVisible by remember { mutableStateOf(false) }

    // Refresh currentSource when background_source pref changes externally
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == BackgroundSource.KEY_SOURCE) {
                currentSource = BackgroundSource.current(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Source options list built from string resources
    val sourceOptions = remember {
        listOf(
            "auto"   to context.getString(R.string.background_source_auto),
            "picsum" to context.getString(R.string.background_source_picsum),
            "pipw"   to context.getString(R.string.background_source_pipw),
            "api"    to context.getString(R.string.background_source_api),
            "local"  to context.getString(R.string.background_source_local),
            "none"   to context.getString(R.string.background_source_none),
        )
    }

    // ── Local image picker ──────────────────────────────────────
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
                inputStream.use { input ->
                    val destFile = File(context.filesDir, "custom_background_image.png")
                    if (destFile.exists()) destFile.delete()
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(4096)
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            output.write(buffer, 0, length)
                        }
                        output.flush()
                    }
                }
                // 保存路径并切换到本地图片源
                prefs.edit()
                    .putString(BackgroundSource.KEY_LOCAL_PATH,
                        File(context.filesDir, "custom_background_image.png").absolutePath)
                    .apply()
                BackgroundSource.setActivePreservingExtras(context, BackgroundSource.Local)
                currentSource = BackgroundSource.current(context)
                ToastUtil.show(context, context.getString(R.string.toast_background_success), Toast.LENGTH_SHORT)
            } catch (e: Exception) {
                ToastUtil.show(context, context.getString(R.string.toast_image_save_failed, e.message ?: ""), Toast.LENGTH_SHORT)
            }
        } else {
            ToastUtil.show(context, context.getString(R.string.toast_image_select_cancelled), Toast.LENGTH_SHORT)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 1. 语言
        item {
            ListPreference(
                key = "list_languages",
                title = context.getString(R.string.title_language),
                entries = listOf(
                    context.getString(R.string.language_follow_system) to "default",
                    context.getString(R.string.language_simplified_chinese) to "zh",
                    "English" to "en",
                ),
                defaultValue = "default",
            )
        }
        // 2. 主题模式
        item {
            ListPreference(
                key = "list_theme_mode",
                title = context.getString(R.string.title_theme_mode),
                entries = listOf(
                    context.getString(R.string.language_follow_system) to "system",
                    context.getString(R.string.theme_dark) to "dark",
                    context.getString(R.string.theme_light) to "light",
                ),
                defaultValue = "dark",
            )
        }

        // 3. 背景图来源
        item {
            var showSourceDialog by remember { mutableStateOf(false) }
            val currentLabel = sourceOptions.find { it.first == currentSource.prefValue }?.second ?: ""
            ClickablePreference(
                title = context.getString(R.string.title_background_source),
                summary = currentLabel,
                onClick = { showSourceDialog = true },
            )
            if (showSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showSourceDialog = false },
                    title = { Text(context.getString(R.string.title_background_source)) },
                    text = {
                        Column {
                            sourceOptions.forEach { (value, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val src = BackgroundSource.fromPrefValue(value)
                                            BackgroundSource.setActive(context, src)
                                            currentSource = BackgroundSource.current(context)
                                            showSourceDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = value == currentSource.prefValue,
                                        onClick = null,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSourceDialog = false }) { Text(context.getString(R.string.no)) }
                    },
                )
            }
        }

        // 4. 背景图片API — only visible when source is "api"
        if (currentSource is BackgroundSource.Api) {
            item {
                ClickablePreference(
                    title = context.getString(R.string.title_background_image_api),
                    summary = if (apiUrl.isNotEmpty()) apiUrl else context.getString(R.string.summary_background_api_placeholder),
                    onClick = { apiUrlDialogVisible = true },
                )
            }
            if (apiUrlDialogVisible) {
                item {
                var inputText by remember { mutableStateOf(apiUrl) }
                AlertDialog(
                    onDismissRequest = { apiUrlDialogVisible = false },
                    title = { Text(context.getString(R.string.dialog_title_background_image_api)) },
                    text = {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text(context.getString(R.string.label_api_address)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = inputText.trim()
                                if (trimmed.isNotEmpty()) {
                                    prefs.edit()
                                        .putString(BackgroundSource.KEY_API_URL, trimmed)
                                        .apply()
                                } else {
                                    prefs.edit()
                                        .remove(BackgroundSource.KEY_API_URL)
                                        .apply()
                                }
                                BackgroundSource.setActivePreservingExtras(context, BackgroundSource.Api)
                                currentSource = BackgroundSource.current(context)
                                apiUrl = trimmed
                                apiUrlDialogVisible = false
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val trimmed = inputText.trim()
                            if (trimmed.isNotEmpty()) {
                                prefs.edit()
                                    .putString(BackgroundSource.KEY_API_URL, trimmed)
                                    .apply()
                            } else {
                                prefs.edit()
                                    .remove(BackgroundSource.KEY_API_URL)
                                    .apply()
                            }
                            BackgroundSource.setActivePreservingExtras(context, BackgroundSource.Api)
                            currentSource = BackgroundSource.current(context)
                            apiUrl = trimmed
                            apiUrlDialogVisible = false
                        }) { Text(context.getString(R.string.btn_save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { apiUrlDialogVisible = false }) { Text(context.getString(R.string.no)) }
                    },
                )
                }
            }
        }

        // 5. 选择本地图片 — only visible when source is "local"
        if (currentSource is BackgroundSource.Local) {
            item {
                ClickablePreference(
                    title = context.getString(R.string.title_local_image_picker),
                    summary = context.getString(R.string.summary_local_image_picker),
                    onClick = { imagePickerLauncher.launch("image/*") },
                )
            }
        }

        // 6. 恢复默认背景
        item {
            var showDialog by remember { mutableStateOf(false) }
            ClickablePreference(
                title = context.getString(R.string.title_reset_background_image),
                summary = context.getString(R.string.summary_restore_background),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text(context.getString(R.string.dialog_restore_background_title)) },
                    text = { Text(context.getString(R.string.dialog_restore_background_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            // 删除本地文件
                            val file = File(context.filesDir, "custom_background_image.png")
                            if (file.exists()) file.delete()
                            BackgroundSource.setActive(context, BackgroundSource.None)
                            currentSource = BackgroundSource.current(context)
                            apiUrl = ""
                            ToastUtil.show(context, context.getString(R.string.toast_restored_default_background), Toast.LENGTH_SHORT)
                            showDialog = false
                        }) { Text(context.getString(R.string.yes)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) { Text(context.getString(R.string.no)) }
                    },
                )
            }
        }
    }
}
