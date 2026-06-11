package com.alexclin.moonlink.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class ScenePreset(
    val slot: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val videoFormat: String,
    val enableHdr: Boolean,
    val enablePerfOverlay: Boolean,
)

private fun loadPresets(context: Context): List<ScenePreset> {
    val prefs = context.getSharedPreferences("SceneConfigs", Context.MODE_PRIVATE)
    return (1..5).mapNotNull { slot ->
        val json = prefs.getString("scene_$slot", null) ?: return@mapNotNull null
        try {
            val obj = JSONObject(json)
            ScenePreset(
                slot = slot,
                name = obj.optString("name", "预设 $slot"),
                width = obj.optInt("width", 1920),
                height = obj.optInt("height", 1080),
                fps = obj.optInt("fps", 60),
                bitrate = obj.optInt("bitrate", 20000),
                videoFormat = obj.optString("videoFormat", "AUTO"),
                enableHdr = obj.optBoolean("enableHdr", false),
                enablePerfOverlay = obj.optBoolean("enablePerfOverlay", false),
            )
        } catch (_: Exception) { null }
    }
}

private fun savePreset(context: Context, preset: ScenePreset) {
    val obj = JSONObject().apply {
        put("name", preset.name)
        put("width", preset.width)
        put("height", preset.height)
        put("fps", preset.fps)
        put("bitrate", preset.bitrate)
        put("videoFormat", preset.videoFormat)
        put("enableHdr", preset.enableHdr)
        put("enablePerfOverlay", preset.enablePerfOverlay)
    }
    context.getSharedPreferences("SceneConfigs", Context.MODE_PRIVATE)
        .edit().putString("scene_${preset.slot}", obj.toString()).apply()
}

private fun deletePreset(context: Context, slot: Int) {
    context.getSharedPreferences("SceneConfigs", Context.MODE_PRIVATE)
        .edit().remove("scene_$slot").apply()
}

private fun applyPreset(context: Context, preset: ScenePreset) {
    val res = "${preset.width}x${preset.height}"
    Prefs.of(context).edit().apply {
        putString("list_resolution", res)
        putString("list_fps", preset.fps.toString())
        putInt("seekbar_bitrate_kbps", preset.bitrate)
        putString("video_format", preset.videoFormat.lowercase())
        putBoolean("checkbox_enable_hdr", preset.enableHdr)
        putBoolean("checkbox_enable_perf_overlay", preset.enablePerfOverlay)
        apply()
    }
}

private fun getCurrentConfig(context: Context): ScenePreset {
    val prefs = Prefs.of(context)
    val res = prefs.getString("list_resolution", "1920x1080") ?: "1920x1080"
    val parts = res.split("x")
    return ScenePreset(
        slot = 0,
        name = "",
        width = parts.getOrNull(0)?.toIntOrNull() ?: 1920,
        height = parts.getOrNull(1)?.toIntOrNull() ?: 1080,
        fps = prefs.getString("list_fps", "60")?.toIntOrNull() ?: 60,
        bitrate = prefs.getInt("seekbar_bitrate_kbps", 20000),
        videoFormat = prefs.getString("video_format", "auto")?.uppercase() ?: "AUTO",
        enableHdr = prefs.getBoolean("checkbox_enable_hdr", false),
        enablePerfOverlay = prefs.getBoolean("checkbox_enable_perf_overlay", false),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScenePresetsScreen() {
    val context = LocalContext.current
    var presets by remember { mutableStateOf(loadPresets(context)) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ScenePreset?>(null) }

    if (presets.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无保存的场景预设", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(presets.size) { index ->
                val p = presets[index]
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text("${p.width}x${p.height}@${p.fps}FPS / ${p.bitrate/1000.0}Mbps / ${p.videoFormat}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                applyPreset(context, p)
                                Toast.makeText(context, "已应用场景预设: ${p.name}", Toast.LENGTH_SHORT).show()
                            },
                            onLongClick = { deleteTarget = p },
                        ),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text("保存当前配置")
                }
            }
        }
    }

    // Save dialog
    if (showSaveDialog) {
        val current = getCurrentConfig(context)
        val allFull = presets.size >= 5
        var nameInput by remember { mutableStateOf("") }
        var overrideSlot by remember { mutableIntStateOf(-1) }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存场景预设") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 20) nameInput = it },
                        label = { Text("预设名称 (1-20字符)") },
                        singleLine = true,
                    )
                    if (allFull) {
                        Spacer(Modifier.height(12.dp))
                        Text("槽位已满，选择要覆盖的预设:", style = MaterialTheme.typography.bodyMedium)
                        presets.forEach { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { overrideSlot = p.slot },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = overrideSlot == p.slot, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(p.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val slot = if (allFull) overrideSlot else (1..5).firstOrNull { s -> presets.none { it.slot == s } } ?: 1
                        val preset = current.copy(slot = slot, name = nameInput)
                        savePreset(context, preset)
                        presets = loadPresets(context)
                        showSaveDialog = false
                        Toast.makeText(context, "已保存预设: ${nameInput}", Toast.LENGTH_SHORT).show()
                    },
                    enabled = nameInput.isNotBlank() && (!allFull || overrideSlot > 0),
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
            },
        )
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除预设") },
            text = { Text("确定要删除预设「${target.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    deletePreset(context, target.slot)
                    presets = loadPresets(context)
                    deleteTarget = null
                    Toast.makeText(context, "已删除预设: ${target.name}", Toast.LENGTH_SHORT).show()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}
