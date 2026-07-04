package com.alexclin.moonlink.android.stream.ui.panels

import android.content.ContentValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.DetailScaffold
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerDialog
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerItem
import com.alexclin.moonlink.android.stream.data.ConfigColumns
import com.alexclin.moonlink.android.stream.data.KeymappingDatabaseHelper

/**
 * 按键映射方案配置页面（子面板内页面）。
 *
 * 对应旧 Crown 系统的 PageConfigController，但剔除"触控板模式"开关。
 * 配置项均存储于数据库 config 表。
 *
 * 运行时同步：每次值变更时写入 DB 并立即同步到 [ControllerManager.TouchController] 和 [ElementController]，
 * 打开面板时也从 DB 还原到运行态，保持两端一致。
 */
@Composable
fun KeyMappingConfigPanel(
    engine: StreamEngine,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { KeymappingDatabaseHelper(context) }
    val configId = engine.currentSchemeConfigId

    // ── 从数据库读取配置 ──
    var touchEnabled by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, ConfigColumns.COLUMN_BOOLEAN_TOUCH_ENABLE, "true") as? String) ?: "true"
        )
    ) }
    var gameVibrator by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, ConfigColumns.COLUMN_BOOLEAN_GAME_VIBRATOR, "false") as? String) ?: "false"
        )
    ) }
    var buttonVibrator by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, ConfigColumns.COLUMN_BOOLEAN_BUTTON_VIBRATOR, "false") as? String) ?: "false"
        )
    ) }
    var wheelSpeed by remember { mutableIntStateOf(
        (            (db.queryConfigAttribute(configId, ConfigColumns.COLUMN_INT_MOUSE_WHEEL_SPEED, 20L) as? Long) ?: 20L).toInt()
    ) }
    var enhancedTouch by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, ConfigColumns.COLUMN_BOOLEAN_ENHANCED_TOUCH, "false") as? String) ?: "false"
        )
    ) }
    var globalOpacity by remember { mutableIntStateOf(
        ((db.queryConfigAttribute(configId, ConfigColumns.COLUMN_INT_GLOBAL_OPACITY, 100L) as? Long) ?: 100L).toInt()
    ) }

    // ── 全局颜色 ──
    var useGlobalColors by remember { mutableStateOf(
        db.queryConfigAttribute(configId, ConfigColumns.COLUMN_INT_GLOBAL_BORDER_COLOR, null) != null
    ) }
    var globalBorderColor by remember { mutableIntStateOf(
        ((db.queryConfigAttribute(configId, ConfigColumns.COLUMN_INT_GLOBAL_BORDER_COLOR, null) as? Long) ?: 0xF0888888L).toInt()
    ) }
    var globalTextColor by remember { mutableIntStateOf(
        ((db.queryConfigAttribute(configId, ConfigColumns.COLUMN_INT_GLOBAL_TEXT_COLOR, null) as? Long) ?: 0xFFFFFFFFL).toInt()
    ) }
    var showGlobalBorderPicker by remember { mutableStateOf(false) }
    var showGlobalTextPicker by remember { mutableStateOf(false) }

    // ── 运行时同步：将 DB 值同步到 engine 运行时状态（打开面板时执行一次） ──
    LaunchedEffect(Unit) {
        syncConfigToEngine(engine, touchEnabled, gameVibrator, buttonVibrator, wheelSpeed, enhancedTouch, globalOpacity)
    }

    // ── 保存到 DB 并同步到控制器 ──
    fun saveToDb() {
        val cv = ContentValues()
        cv.put(ConfigColumns.COLUMN_BOOLEAN_TOUCH_ENABLE, touchEnabled.toString())
        cv.put(ConfigColumns.COLUMN_BOOLEAN_GAME_VIBRATOR, gameVibrator.toString())
        cv.put(ConfigColumns.COLUMN_BOOLEAN_BUTTON_VIBRATOR, buttonVibrator.toString())
        cv.put(ConfigColumns.COLUMN_INT_MOUSE_WHEEL_SPEED, wheelSpeed.toLong())
        cv.put(ConfigColumns.COLUMN_BOOLEAN_ENHANCED_TOUCH, enhancedTouch.toString())
        cv.put(ConfigColumns.COLUMN_INT_GLOBAL_OPACITY, globalOpacity.toLong())
        // 全局颜色
        if (useGlobalColors) {
            cv.put(ConfigColumns.COLUMN_INT_GLOBAL_BORDER_COLOR, globalBorderColor.toLong())
            cv.put(ConfigColumns.COLUMN_INT_GLOBAL_TEXT_COLOR, globalTextColor.toLong())
        } else {
            cv.putNull(ConfigColumns.COLUMN_INT_GLOBAL_BORDER_COLOR)
            cv.putNull(ConfigColumns.COLUMN_INT_GLOBAL_TEXT_COLOR)
        }
        db.updateConfig(configId, cv)

        syncConfigToEngine(engine, touchEnabled, gameVibrator, buttonVibrator, wheelSpeed, enhancedTouch, globalOpacity)
    }

    DetailScaffold(title = "按键映射方案配置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 触控开关
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("触控开关", Modifier.weight(1f))
                    Switch(checked = touchEnabled, modifier = Modifier.scale(0.8f), onCheckedChange = { touchEnabled = it; saveToDb() })
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 游戏震动
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("游戏震动", Modifier.weight(1f))
                    Switch(checked = gameVibrator, modifier = Modifier.scale(0.8f), onCheckedChange = { gameVibrator = it; saveToDb() })
                }
            }

            // 按键震动
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("按键震动", Modifier.weight(1f))
                    Switch(checked = buttonVibrator, modifier = Modifier.scale(0.8f), onCheckedChange = { buttonVibrator = it; saveToDb() })
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 鼠标滚轮速度（数值越小滚动越快，120-val）
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("鼠标滚轮速度", Modifier.weight(1f))
                        Text(if (wheelSpeed < 40) "快" else if (wheelSpeed > 80) "慢" else "中",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = wheelSpeed.toFloat(),
                        onValueChange = { wheelSpeed = it.toInt() },
                        onValueChangeFinished = { saveToDb() },
                        valueRange = 1f..119f,
                    )
                }
            }

            // 增强触控
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("增强触控", Modifier.weight(1f))
                    Switch(checked = enhancedTouch, modifier = Modifier.scale(0.8f), onCheckedChange = { enhancedTouch = it; saveToDb() })
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 全局不透明度
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("全局不透明度", Modifier.weight(1f))
                        Text("$globalOpacity%", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = globalOpacity.toFloat(),
                        onValueChange = { globalOpacity = it.toInt() },
                        onValueChangeFinished = { saveToDb() },
                        valueRange = 0f..100f,
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 统一边框和文字颜色
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("统一边框和文字颜色", Modifier.weight(1f))
                    Switch(checked = useGlobalColors, modifier = Modifier.scale(0.8f), onCheckedChange = {
                        useGlobalColors = it
                        if (!it) {
                            // 关闭时清除 DB 中的全局颜色值
                            val cv = ContentValues()
                            cv.putNull(ConfigColumns.COLUMN_INT_GLOBAL_BORDER_COLOR)
                            cv.putNull(ConfigColumns.COLUMN_INT_GLOBAL_TEXT_COLOR)
                            db.updateConfig(configId, cv)
                        } else {
                            saveToDb()
                        }
                    })
                }
            }

            // 全局颜色选择器（仅开关打开时显示）
            if (useGlobalColors) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("全局边框颜色", Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .background(Color(globalBorderColor), RoundedCornerShape(4.dp))
                                .clickable { showGlobalBorderPicker = true }
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showGlobalBorderPicker = true }) {
                            Text("选择颜色", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("全局文字颜色", Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .background(Color(globalTextColor), RoundedCornerShape(4.dp))
                                .clickable { showGlobalTextPicker = true }
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showGlobalTextPicker = true }) {
                            Text("选择颜色", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    // ── 全局边框颜色选择器 ──
    if (showGlobalBorderPicker) {
        ColorPickerDialog(
            title = "全局边框颜色",
            items = listOf(
                ColorPickerItem("边框颜色", "border", globalBorderColor),
            ),
            onSave = { result ->
                globalBorderColor = result.firstOrNull()?.second ?: globalBorderColor
                showGlobalBorderPicker = false
                saveToDb()
                engine.reloadOverlay()
            },
            onDismiss = { showGlobalBorderPicker = false },
        )
    }

    // ── 全局文字颜色选择器 ──
    if (showGlobalTextPicker) {
        ColorPickerDialog(
            title = "全局文字颜色",
            items = listOf(
                ColorPickerItem("文字颜色", "text", globalTextColor),
            ),
            onSave = { result ->
                globalTextColor = result.firstOrNull()?.second ?: globalTextColor
                showGlobalTextPicker = false
                saveToDb()
                engine.reloadOverlay()
            },
            onDismiss = { showGlobalTextPicker = false },
        )
    }
}


