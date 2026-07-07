package com.alexclin.moonlink.android.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.home.GameListWidgetProvider
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 桌面小组件管理设置页
 *
 * 列出所有已添加到桌面的小组件实例，显示其绑定的电脑，
 * 支持重新绑定或清除绑定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    computers: List<ComputerDetails>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── 查询所有 widget 实例 ────────────────────────────
    var widgetInstances by remember { mutableStateOf<List<WidgetInstance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            widgetInstances = queryWidgetInstances(context)
        }
        loading = false
    }

    // ── 选择电脑对话框 ──────────────────────────────────
    var showComputerPicker by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (widgetInstances.isEmpty()) {
            // ── 无 widget 实例 ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        Icons.Default.Widgets,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.widget_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.widget_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // ── Widget 列表 ───────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // 顶部提示
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = stringResource(R.string.widget_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                items(widgetInstances, key = { it.appWidgetId }) { instance ->
                    WidgetInstanceCard(
                        instance = instance,
                        onBind = { showComputerPicker = instance.appWidgetId },
                        onUnbind = {
                            scope.launch(Dispatchers.IO) {
                                clearWidgetBinding(context, instance.appWidgetId)
                                widgetInstances = queryWidgetInstances(context)
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // 底部留白
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // ── 选择电脑对话框 ──────────────────────────────────
    showComputerPicker?.let { widgetId ->
        val onDismiss = { showComputerPicker = null }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.widget_bind_title)) },
            text = {
                if (computers.isEmpty()) {
                    Text(stringResource(R.string.widget_no_computers))
                } else {
                    Column {
                        computers.forEach { computer ->
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        bindWidgetToComputer(
                                            context = context,
                                            appWidgetId = widgetId,
                                            computer = computer,
                                        )
                                        widgetInstances = queryWidgetInstances(context)
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = computer.name ?: stringResource(R.string.engine_device_name_unknown),
                                    modifier = Modifier.weight(1f),
                                )
                                if (computer.state == ComputerDetails.State.ONLINE) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = stringResource(R.string.pcview_menu_header_online),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_button_cancel))
                }
            },
        )
    }
}

// ── 数据模型 ──────────────────────────────────────────────────────

private data class WidgetInstance(
    val appWidgetId: Int,
    val boundComputerName: String?,
    val boundComputerUuid: String?,
)

// ── Widget 实例卡片 ──────────────────────────────────────────────

@Composable
private fun WidgetInstanceCard(
    instance: WidgetInstance,
    onBind: () -> Unit,
    onUnbind: () -> Unit,
) {
    var showConfirmUnbind by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = instance.boundComputerName ?: stringResource(R.string.widget_unbound),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Text(
                text = "Widget #${instance.appWidgetId}" +
                        if (instance.boundComputerName == null) stringResource(R.string.kme_shared_tap_to_select) else stringResource(R.string.widget_tap_to_change),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                if (instance.boundComputerName != null) Icons.Default.Devices else Icons.Default.Widgets,
                contentDescription = null,
                tint = if (instance.boundComputerName != null)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (instance.boundComputerName != null) {
                IconButton(onClick = { showConfirmUnbind = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.widget_clear_bind),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBind() },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )

    // ── 确认解除绑定对话框 ──────────────────────────────
    if (showConfirmUnbind) {
        AlertDialog(
            onDismissRequest = { showConfirmUnbind = false },
            title = { Text(stringResource(R.string.widget_clear_bind)) },
            text = {
                Text(stringResource(R.string.widget_confirm_unbind, instance.boundComputerName ?: ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmUnbind = false
                    onUnbind()
                }) {
                    Text(stringResource(R.string.dialog_button_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmUnbind = false }) {
                    Text(stringResource(R.string.dialog_button_cancel))
                }
            },
        )
    }
}

// ── SharedPreferences 存取 ──────────────────────────────────────

private const val WIDGET_PREFS_NAME = "widget_prefs"

/** 查询所有 widget 实例及其绑定状态 */
private fun queryWidgetInstances(context: Context): List<WidgetInstance> {
    val prefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
    val provider = ComponentName(context, GameListWidgetProvider::class.java)
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val ids = appWidgetManager.getAppWidgetIds(provider)

    return ids.map { id ->
        val uuid = prefs.getString("widget_${id}_uuid", null)
        val name = prefs.getString("widget_${id}_name", null)
        WidgetInstance(
            appWidgetId = id,
            boundComputerName = name,
            boundComputerUuid = uuid,
        )
    }.sortedByDescending { it.boundComputerName != null } // 已绑定的排前面
}

/** 将 widget 绑定到指定电脑 */
private fun bindWidgetToComputer(
    context: Context,
    appWidgetId: Int,
    computer: ComputerDetails,
) {
    val prefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putString("widget_${appWidgetId}_uuid", computer.uuid)
        .putString("widget_${appWidgetId}_name", computer.name)
        .apply()

    // 刷新 widget 显示
    val appWidgetManager = AppWidgetManager.getInstance(context)
    GameListWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_grid)
}

/** 清除 widget 的绑定 */
private fun clearWidgetBinding(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .remove("widget_${appWidgetId}_uuid")
        .remove("widget_${appWidgetId}_name")
        .apply()

    // 刷新 widget 显示（回到未配置状态）
    val appWidgetManager = AppWidgetManager.getInstance(context)
    GameListWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
}
