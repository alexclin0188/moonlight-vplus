package com.alexclin.moonlink.stream.ui.panels

import android.content.ContentValues
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.DetailScaffold
import com.limelight.binding.input.advance_setting.config.PageConfigController
import com.limelight.binding.input.advance_setting.element.ElementController
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper

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
    val db = remember { SuperConfigDatabaseHelper(context) }
    val configId = engine.currentSchemeConfigId

    // ── 从数据库读取配置 ──
    var touchEnabled by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, PageConfigController.COLUMN_BOOLEAN_TOUCH_ENABLE, "true") as? String) ?: "true"
        )
    ) }
    var touchSense by remember { mutableIntStateOf(
        ((db.queryConfigAttribute(configId, "touch_sense", 100L) as? Long) ?: 100L).toInt()
    ) }
    var gameVibrator by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, PageConfigController.COLUMN_BOOLEAN_GAME_VIBRATOR, "false") as? String) ?: "false"
        )
    ) }
    var buttonVibrator by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, PageConfigController.COLUMN_BOOLEAN_BUTTON_VIBRATOR, "false") as? String) ?: "false"
        )
    ) }
    var wheelSpeed by remember { mutableIntStateOf(
        ((db.queryConfigAttribute(configId, "mouse_wheel_speed", 20L) as? Long) ?: 20L).toInt()
    ) }
    var enhancedTouch by remember { mutableStateOf(
        java.lang.Boolean.parseBoolean(
            (db.queryConfigAttribute(configId, PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, "false") as? String) ?: "false"
        )
    ) }
    var globalOpacity by remember { mutableIntStateOf(
        ((db.queryConfigAttribute(configId, PageConfigController.COLUMN_INT_GLOBAL_OPACITY, 100L) as? Long) ?: 100L).toInt()
    ) }

    // ── 运行时同步：将 DB 值同步到运行中的控制器（打开面板时执行一次） ──
    LaunchedEffect(Unit) {
        syncToControllers(engine, touchEnabled, touchSense, gameVibrator, buttonVibrator, wheelSpeed, enhancedTouch, globalOpacity)
    }

    // ── 保存到 DB 并同步到控制器 ──
    fun saveToDb() {
        val cv = ContentValues()
        cv.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_ENABLE, touchEnabled.toString())
        cv.put("touch_sense", touchSense.toLong())
        cv.put(PageConfigController.COLUMN_BOOLEAN_GAME_VIBRATOR, gameVibrator.toString())
        cv.put(PageConfigController.COLUMN_BOOLEAN_BUTTON_VIBRATOR, buttonVibrator.toString())
        cv.put("mouse_wheel_speed", wheelSpeed.toLong())
        cv.put(PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, enhancedTouch.toString())
        cv.put(PageConfigController.COLUMN_INT_GLOBAL_OPACITY, globalOpacity.toLong())
        db.updateConfig(configId, cv)

        syncToControllers(engine, touchEnabled, touchSense, gameVibrator, buttonVibrator, wheelSpeed, enhancedTouch, globalOpacity)
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
                    Switch(checked = touchEnabled, onCheckedChange = { touchEnabled = it; saveToDb() })
                }
            }

            // 触控灵敏度
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("触控灵敏度", Modifier.weight(1f))
                        Text("$touchSense", style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = touchSense.toFloat(),
                        onValueChange = { touchSense = it.toInt() },
                        onValueChangeFinished = { saveToDb() },
                        valueRange = 1f..200f,
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 游戏震动
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("游戏震动", Modifier.weight(1f))
                    Switch(checked = gameVibrator, onCheckedChange = { gameVibrator = it; saveToDb() })
                }
            }

            // 按键震动
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("按键震动", Modifier.weight(1f))
                    Switch(checked = buttonVibrator, onCheckedChange = { buttonVibrator = it; saveToDb() })
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
                    Switch(checked = enhancedTouch, onCheckedChange = { enhancedTouch = it; saveToDb() })
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 全局透明度
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("全局透明度", Modifier.weight(1f))
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

            // 颜色配置提示
            item {
                Text(
                    "全局边框颜色和全局文字颜色请在编辑器中逐个元素设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/**
 * 将按键映射方案的全部设置项同步到运行中的 [ControllerManager] 的子控制器。
 *
 * 对应旧 [PageConfigController] 中每个设置的实时同步逻辑。
 */
private fun syncToControllers(
    engine: StreamEngine,
    touchEnabled: Boolean,
    touchSense: Int,
    gameVibrator: Boolean,
    buttonVibrator: Boolean,
    wheelSpeed: Int,
    enhancedTouch: Boolean,
    globalOpacity: Int,
) {
    val cm = engine.controllerManager ?: return
    val ec = cm.elementController
    val tc = cm.touchController

    // 1. 触控开关 — 对应 PageConfigController.loadMouseEnable()
    tc?.enableTouch(touchEnabled)

    // 2. 触控灵敏度 — 对应 PageConfigController.loadMouseSense()
    tc?.adjustTouchSense(touchSense)

    // 3. 游戏震动 — 对应 PageConfigController.loadGameVibrator()
    ec?.setGameVibrator(gameVibrator)

    // 4. 按键震动 — 对应 PageConfigController.loadButtonVibrator()
    ec?.setButtonVibrator(buttonVibrator)

    // 5. 鼠标滚轮速度 — 对应 PageConfigController.loadMouseWheelSpeed()
    // 数值越小滚动越快，所以转换范围
    ElementController.setMouseScrollRepeatInterval(120 - wheelSpeed)

    // 6. 增强触控 — 对应 PageConfigController.loadEnhancedTouch()
    tc?.setEnhancedTouch(enhancedTouch)

    // 7. 全局透明度 — 对应 PageConfigController.loadGlobalStyles()
    ec?.applyGlobalOpacity(globalOpacity)
}

