package com.alexclin.moonlink.android.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.limelight.preferences.BackgroundSource
import java.io.File
import java.io.FileOutputStream

@Composable
fun UiSettingsScreen() {
    val context = LocalContext.current

    // 本地图片选择器
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
                Prefs.of(context).edit()
                    .putString(BackgroundSource.KEY_LOCAL_PATH,
                        File(context.filesDir, "custom_background_image.png").absolutePath)
                    .apply()
                BackgroundSource.setActivePreservingExtras(context, BackgroundSource.Local)
                Toast.makeText(context, "背景图片设置成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "图片保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "图片选择已取消", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 1. 语言
            item {
                ListPreference(
                    key = "list_languages",
                    title = "语言",
                    entries = listOf(
                        "跟随系统" to "default",
                        "简体中文" to "zh",
                        "English" to "en",
                    ),
                    defaultValue = "default",
                )
            }
            // 2. 主题模式
            item {
                ListPreference(
                    key = "list_theme_mode",
                    title = "主题模式",
                    entries = listOf(
                        "跟随系统" to "system",
                        "深色" to "dark",
                        "浅色" to "light",
                    ),
                    defaultValue = "dark",
                )
            }
            // 3. 选择本地图片
            item {
                ClickablePreference(
                    title = "选择本地图片",
                    summary = "从本地选择背景图片",
                    onClick = { imagePickerLauncher.launch("image/*") },
                )
            }
            // 4. 恢复默认背景
            item {
                var showDialog by remember { mutableStateOf(false) }
                ClickablePreference(
                    title = "恢复默认背景",
                    summary = "将背景图片恢复为默认设置",
                    onClick = { showDialog = true },
                )
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("恢复默认背景") },
                        text = { Text("确定要恢复默认背景图片吗？") },
                        confirmButton = {
                            TextButton(onClick = {
                                // 删除本地文件
                                val file = File(context.filesDir, "custom_background_image.png")
                                if (file.exists()) file.delete()
                                // 原子切换到 Auto 源（自动清除 KEY_LOCAL_PATH、KEY_API_URL，发刷新广播）
                                BackgroundSource.setActive(context, BackgroundSource.Auto)
                                Toast.makeText(context, "已恢复默认背景", Toast.LENGTH_SHORT).show()
                                showDialog = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialog = false }) { Text("取消") }
                        },
                    )
                }
            }

        }
}
