package com.alexclin.moonlink.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun KeyMappingScreen() {
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ClickablePreference(
                    title = "导出按键配置",
                    summary = "将按键配置导出为 .mdat 文件",
                    onClick = { Toast.makeText(context, "功能开发中", Toast.LENGTH_SHORT).show() },
                )
            }
            item {
                ClickablePreference(
                    title = "导入按键配置",
                    summary = "从 .mdat 文件导入按键配置",
                    onClick = { Toast.makeText(context, "功能开发中", Toast.LENGTH_SHORT).show() },
                )
            }
            item {
                ClickablePreference(
                    title = "按键方案导出",
                    summary = "将按键方案导出为 .mkmp 文件",
                    onClick = { Toast.makeText(context, "功能开发中", Toast.LENGTH_SHORT).show() },
                )
            }
            item {
                ClickablePreference(
                    title = "按键方案导入",
                    summary = "从 .mkmp 文件导入按键方案",
                    onClick = { Toast.makeText(context, "功能开发中", Toast.LENGTH_SHORT).show() },
                )
            }
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "按键配置管理功能正在开发中，敬请期待。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
}
