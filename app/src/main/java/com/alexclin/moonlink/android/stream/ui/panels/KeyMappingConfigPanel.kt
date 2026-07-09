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
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.DetailScaffold
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerDialog
import com.alexclin.moonlink.android.stream.ui.editor.ColorPickerItem
import com.alexclin.moonlink.android.stream.data.ConfigColumns
import com.alexclin.moonlink.android.stream.data.ElementColumns
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

    // ── 统一颜色配置 ──
    var unifiedColorEnabled by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, ConfigColumns.COLUMN_BOOLEAN_UNIFIED_COLOR_ENABLED, "false") as? String) ?: "false"
        )
    ) }
    // 统一颜色值（从最小 ID 元素读取，或用户通过颜色编辑器修改）
    var unifiedNormalColor by remember { mutableIntStateOf(0xF0888888.toInt()) }
    var unifiedPressedColor by remember { mutableIntStateOf(0xF00000FF.toInt()) }
    var unifiedBackgroundColor by remember { mutableIntStateOf(0x00FFFFFF) }
    var unifiedNormalTextColor by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var unifiedPressedTextColor by remember { mutableIntStateOf(0xFFCCCCCC.toInt()) }
    var showUnifiedColorPicker by remember { mutableStateOf(false) }

    // ── 运行时同步：将 DB 值同步到 engine 运行时状态（打开面板时执行一次） ──
    LaunchedEffect(Unit) {
        syncConfigToEngine(engine, touchEnabled, gameVibrator, buttonVibrator, wheelSpeed, enhancedTouch, globalOpacity)
        // 如果统一颜色已开启，从最小 element_id 元素加载实际颜色值显示在色块上
        if (unifiedColorEnabled) {
            applyUnifiedColorsFromMinIdElement(context, configId, db) { n, p, bg, nt, pt ->
                unifiedNormalColor = n; unifiedPressedColor = p
                unifiedBackgroundColor = bg; unifiedNormalTextColor = nt; unifiedPressedTextColor = pt
            }
        }
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
        // 统一颜色配置不持久化值，仅持久化开关状态
        cv.put(ConfigColumns.COLUMN_BOOLEAN_UNIFIED_COLOR_ENABLED, unifiedColorEnabled.toString())
        db.updateConfig(configId, cv)

        syncConfigToEngine(engine, touchEnabled, gameVibrator, buttonVibrator, wheelSpeed, enhancedTouch, globalOpacity)
    }

    DetailScaffold(title = stringResource(R.string.config_title), onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 触控开关
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.config_touch_switch), Modifier.weight(1f))
                    Switch(checked = touchEnabled, modifier = Modifier.scale(0.8f), onCheckedChange = { touchEnabled = it; saveToDb() })
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 游戏震动
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.config_game_vibration), Modifier.weight(1f))
                    Switch(checked = gameVibrator, modifier = Modifier.scale(0.8f), onCheckedChange = { gameVibrator = it; saveToDb() })
                }
            }

            // 按键震动
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.config_button_vibration), Modifier.weight(1f))
                    Switch(checked = buttonVibrator, modifier = Modifier.scale(0.8f), onCheckedChange = { buttonVibrator = it; saveToDb() })
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 鼠标滚轮速度（数值越小滚动越快，120-val）
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.config_mouse_wheel_speed), Modifier.weight(1f))
                        Text(if (wheelSpeed < 40) stringResource(R.string.config_speed_fast) else if (wheelSpeed > 80) stringResource(R.string.config_speed_slow) else stringResource(R.string.config_speed_medium),
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
                    Text(stringResource(R.string.config_enhanced_touch), Modifier.weight(1f))
                    Switch(checked = enhancedTouch, modifier = Modifier.scale(0.8f), onCheckedChange = { enhancedTouch = it; saveToDb() })
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 全局不透明度
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.config_global_opacity), Modifier.weight(1f))
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

            // 统一颜色配置
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.config_unified_colors), Modifier.weight(1f))
                    Switch(checked = unifiedColorEnabled, modifier = Modifier.scale(0.8f), onCheckedChange = {
                        unifiedColorEnabled = it
                        if (it) {
                            // 开启：以最小 element_id 元素的颜色为基准，应用到所有元素
                            applyUnifiedColorsFromMinIdElement(context, configId, db) { n, p, bg, nt, pt ->
                                unifiedNormalColor = n; unifiedPressedColor = p
                                unifiedBackgroundColor = bg; unifiedNormalTextColor = nt; unifiedPressedTextColor = pt
                            }
                            engine.reloadOverlay()
                        }
                        saveToDb()
                    })
                }
            }

            // 统一颜色选择器（仅开关打开时显示）
            if (unifiedColorEnabled) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp).clickable { showUnifiedColorPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.config_unified_color_picker), Modifier.weight(1f))
                        // 五个色块
                        val chipModifier = Modifier.size(28.dp).padding(2.dp)
                        Box(chipModifier.background(Color(unifiedNormalColor), RoundedCornerShape(3.dp)).border(1.dp, Color.Gray, RoundedCornerShape(3.dp)))
                        Box(chipModifier.background(Color(unifiedPressedColor), RoundedCornerShape(3.dp)).border(1.dp, Color.Gray, RoundedCornerShape(3.dp)))
                        Box(chipModifier.background(Color(unifiedBackgroundColor), RoundedCornerShape(3.dp)).border(1.dp, Color.Gray, RoundedCornerShape(3.dp)))
                        Box(chipModifier.background(Color(unifiedNormalTextColor), RoundedCornerShape(3.dp)).border(1.dp, Color.Gray, RoundedCornerShape(3.dp)))
                        Box(chipModifier.background(Color(unifiedPressedTextColor), RoundedCornerShape(3.dp)).border(1.dp, Color.Gray, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }

    // ── 统一颜色编辑器 ──
    if (showUnifiedColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.config_unified_color_picker),
            items = listOf(
                ColorPickerItem(stringResource(R.string.editor_color_normal), "normal", unifiedNormalColor),
                ColorPickerItem(stringResource(R.string.editor_color_pressed), "pressed", unifiedPressedColor),
                ColorPickerItem(stringResource(R.string.editor_color_background), "bg", unifiedBackgroundColor),
                ColorPickerItem(stringResource(R.string.editor_color_normal_text), "normalText", unifiedNormalTextColor),
                ColorPickerItem(stringResource(R.string.editor_color_pressed_text), "pressedText", unifiedPressedTextColor),
            ),
            onSave = { result ->
                val map = result.toMap()
                unifiedNormalColor = map["normal"] ?: unifiedNormalColor
                unifiedPressedColor = map["pressed"] ?: unifiedPressedColor
                unifiedBackgroundColor = map["bg"] ?: unifiedBackgroundColor
                unifiedNormalTextColor = map["normalText"] ?: unifiedNormalTextColor
                unifiedPressedTextColor = map["pressedText"] ?: unifiedPressedTextColor
                // 应用到所有元素
                applyUnifiedColorsToAllElements(context, configId, db,
                    unifiedNormalColor, unifiedPressedColor, unifiedBackgroundColor,
                    unifiedNormalTextColor, unifiedPressedTextColor)
                showUnifiedColorPicker = false
                engine.reloadOverlay()
            },
            onDismiss = { showUnifiedColorPicker = false },
        )
    }
}

// ── 统一颜色工具函数 ───────────────────────────────────────────

/**
 * 以当前方案中最小 [element_id] 元素的颜色为基准，应用到所有元素。
 * 通过回调返回读取到的颜色值供 UI 状态更新。
 */
private fun applyUnifiedColorsFromMinIdElement(
    context: android.content.Context,
    configId: Long,
    db: KeymappingDatabaseHelper,
    onColorsRead: (normal: Int, pressed: Int, bg: Int, normalText: Int, pressedText: Int) -> Unit,
) {
    try {
        val elementIds = db.queryAllElementIds(configId)
        if (elementIds.isNullOrEmpty()) return
        // 取最小 element_id 的元素
        val minId = elementIds.min()
        val attrs = db.queryAllElementAttributes(configId, minId) ?: return
        val n = (attrs[ElementColumns.COLUMN_INT_ELEMENT_NORMAL_COLOR] as? Long)?.toInt() ?: 0xF0888888.toInt()
        val p = (attrs[ElementColumns.COLUMN_INT_ELEMENT_PRESSED_COLOR] as? Long)?.toInt() ?: 0xF00000FF.toInt()
        val bg = (attrs[ElementColumns.COLUMN_INT_ELEMENT_BACKGROUND_COLOR] as? Long)?.toInt() ?: 0x00FFFFFF
        val nt = (attrs[ElementColumns.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR] as? Long)?.toInt() ?: 0xFFFFFFFF.toInt()
        val pt = (attrs[ElementColumns.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR] as? Long)?.toInt() ?: 0xFFCCCCCC.toInt()
        onColorsRead(n, p, bg, nt, pt)
        // 应用到所有元素
        applyUnifiedColorsToAllElements(context, configId, db, n, p, bg, nt, pt)
    } catch (_: Exception) { }
}

/**
 * 将统一的 5 个颜色值覆盖写入所有元素的 DB 记录。
 */
private fun applyUnifiedColorsToAllElements(
    context: android.content.Context,
    configId: Long,
    db: KeymappingDatabaseHelper,
    normal: Int,
    pressed: Int,
    bg: Int,
    normalText: Int,
    pressedText: Int,
) {
    try {
        val elementIds = db.queryAllElementIds(configId) ?: return
        for (eid in elementIds) {
            val cv = ContentValues()
            cv.put(ElementColumns.COLUMN_INT_ELEMENT_NORMAL_COLOR, normal.toLong())
            cv.put(ElementColumns.COLUMN_INT_ELEMENT_PRESSED_COLOR, pressed.toLong())
            cv.put(ElementColumns.COLUMN_INT_ELEMENT_BACKGROUND_COLOR, bg.toLong())
            cv.put(ElementColumns.COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, normalText.toLong())
            cv.put(ElementColumns.COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, pressedText.toLong())
            db.updateElement(configId, eid, cv)
        }
    } catch (_: Exception) { }
}
