package com.alexclin.moonlink.android.settings

import android.widget.Toast
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

@Composable
fun UiSettingsScreen() {
    val context = LocalContext.current

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
            // 3. 使用小图标
            item {
                CheckBoxPreference(
                    key = "checkbox_small_icon_mode",
                    title = "使用小图标",
                    summary = "在设备列表中使用较小的应用图标",
                )
            }
            // 4. 背景图来源
            item {
                ListPreference(
                    key = "background_source",
                    title = "背景图来源",
                    entries = listOf(
                        "自动" to "auto",
                        "API" to "api",
                        "本地图片" to "local",
                    ),
                    defaultValue = "auto",
                )
            }
            // 5. 背景图片 API
            item {
                var showDialog by remember { mutableStateOf(false) }
                val prefs = remember { Prefs.of(context) }
                var urlText by remember { mutableStateOf(prefs.getString("background_image_url", "") ?: "") }

                ListItem(
                    headlineContent = { Text("背景图片 API", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("设置背景图片 API 地址", style = MaterialTheme.typography.bodySmall) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("背景图片 API") },
                        text = {
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text("API URL") },
                                singleLine = true,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                prefs.edit().putString("background_image_url", urlText).apply()
                                showDialog = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialog = false }) { Text("取消") }
                        },
                    )
                }
            }
            // 6. 选择本地图片
            item {
                ClickablePreference(
                    title = "选择本地图片",
                    summary = "从本地选择背景图片",
                    onClick = { Toast.makeText(context, "文件选择器功能开发中", Toast.LENGTH_SHORT).show() },
                )
            }
            // 7. 恢复默认背景
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
                                Prefs.of(context).edit().remove("background_image_url").apply()
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
