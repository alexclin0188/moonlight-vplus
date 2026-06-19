package com.alexclin.moonlink.stream.ui.panels

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.limelight.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper
import org.json.JSONObject

/**
 * 虚拟手柄配置页面（子面板内页面）。
 *
 * 配置项对应旧"屏幕控制按钮设置"，剔除"显示屏幕控制按钮"开关。
 * 数据存储：config 表的 osc_* 列（已从 SharedPreferences 迁移到数据库）。
 */
@Composable
fun VirtualControllerConfigPanel(
    engine: StreamEngine,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { SuperConfigDatabaseHelper(context) }
    val configId = engine.currentSchemeConfigId

    // 从数据库读取当前配置值
    var vibrate by remember { mutableStateOf(
        ((db.queryConfigAttribute(configId, "osc_vibrate", 1L) as? Long) ?: 1L) == 1L
    ) }
    var opacity by remember { mutableFloatStateOf(
        (((db.queryConfigAttribute(configId, "osc_opacity", 90L) as? Long) ?: 90L).toFloat() / 100f)
    ) }
    var onlyL3R3 by remember { mutableStateOf(
        ((db.queryConfigAttribute(configId, "osc_only_l3r3", 0L) as? Long) ?: 0L) == 1L
    ) }
    var showGuide by remember { mutableStateOf(
        ((db.queryConfigAttribute(configId, "osc_show_guide", 1L) as? Long) ?: 1L) == 1L
    ) }
    var halfHeight by remember { mutableStateOf(
        ((db.queryConfigAttribute(configId, "osc_half_height", 1L) as? Long) ?: 1L) == 1L
    ) }

    fun saveToDb() {
        val cv = android.content.ContentValues()
        cv.put("osc_vibrate", if (vibrate) 1L else 0L)
        cv.put("osc_opacity", (opacity * 100).toLong())
        cv.put("osc_only_l3r3", if (onlyL3R3) 1L else 0L)
        cv.put("osc_show_guide", if (showGuide) 1L else 0L)
        cv.put("osc_half_height", if (halfHeight) 1L else 0L)
        db.updateConfig(configId, cv)
    }

    DetailScaffold(title = "虚拟手柄配置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // 启用震动
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("启用震动", Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it; saveToDb() })
                }
            }

            // 透明度 Slider
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("透明度", Modifier.weight(1f))
                        Text("${(opacity * 100).toInt()}%",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        onValueChangeFinished = { saveToDb() },
                        valueRange = 0f..1f,
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

            // 只显示 L3 和 R3
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("只显示 L3 和 R3", Modifier.weight(1f))
                    Switch(checked = onlyL3R3, onCheckedChange = { onlyL3R3 = it; saveToDb() })
                }
            }

            // 显示 Guide 按钮
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("显示 Guide 按钮", Modifier.weight(1f))
                    Switch(checked = showGuide, onCheckedChange = { showGuide = it; saveToDb() })
                }
            }

            // 竖屏模式半高
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("竖屏模式半高", Modifier.weight(1f))
                    Switch(checked = halfHeight, onCheckedChange = { halfHeight = it; saveToDb() })
                }
            }

            // 重置布局按钮
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            // 清除当前的 osc_element_layout，后续会从默认重建
                            val cv = android.content.ContentValues()
                            cv.putNull("osc_element_layout")
                            db.updateConfig(configId, cv)
                            Toast.makeText(context, "屏幕控制按钮布局已重置", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "重置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重置屏幕控制按钮布局")
                }
            }
        }
    }
}
