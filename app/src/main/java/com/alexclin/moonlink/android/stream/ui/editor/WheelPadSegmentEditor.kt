package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** WheelPad 的一个分段：键值 + 可选显示名称 */
private data class Segment(
    val value: String,
    val name: String,
)

/**
 * WheelPad 分区段编辑对话框。
 *
 * 显示所有分区的列表，支持添加/移除/排序/编辑名称和引用。
 */
@Composable
fun WheelPadSegmentEditor(
    element: EditorElement,
    allElements: List<EditorElement>,
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialSegments = remember {
        element.value
            .split(",")
            .filter { it.isNotBlank() }
            .map { segmentStr ->
                val parts = segmentStr.split("|", limit = 2)
                Segment(
                    value = parts[0].trim(),
                    name = if (parts.size > 1) parts[1].trim() else "",
                )
            }
            .toMutableList()
    }
    var segments by remember { mutableStateOf(initialSegments) }

    var showKeyPicker by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }
    var editingSegmentIndex by remember { mutableIntStateOf(-1) }
    var showRemoveConfirm by remember { mutableStateOf<Int?>(null) }

    val availableGroupButtons = remember(allElements, segments) {
        allElements.filter { it.type == ElementType.GROUP_BUTTON }
    }

    fun getDisplayText(value: String): String {
        if (value.startsWith("gb")) {
            val groupId = value.substring(2).toLongOrNull()
            val group = allElements.find { it.elementId == groupId }
            if (group != null) return "[组] ${group.text.ifBlank { "GROUP" }}"
            return "[无效的组]"
        }
        if (value.isBlank() || value == "null") return "空"
        val label = getKeyLabelByValue(value)
        return label ?: value
    }

    fun buildValueString(): String {
        return segments.joinToString(",") { seg ->
            if (seg.name.isNotBlank()) "${seg.value}|${seg.name}" else seg.value
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                // ── 标题 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("滚轮分段编辑",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(20.dp))
                    }
                }
                Text("${segments.size} 个分段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── 列表 ──
                if (segments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center) {
                        Text("暂无分段，点击下方按钮添加",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    ) {
                        itemsIndexed(segments, key = { index, _ -> index }) { index, segment ->
                            SegmentRow(
                                index = index,
                                name = segment.name,
                                displayText = getDisplayText(segment.value),
                                totalCount = segments.size,
                                onNameChange = { newName ->
                                    segments = segments.toMutableList().apply {
                                        this[index] = segment.copy(name = newName)
                                    }
                                },
                                onEditValue = {
                                    editingSegmentIndex = index
                                    if (segment.value.startsWith("gb")) showGroupPicker = true
                                    else showKeyPicker = true
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        val m = segments.toMutableList()
                                        val t = m[index]; m[index] = m[index - 1]; m[index - 1] = t
                                        segments = m
                                    }
                                },
                                onMoveDown = {
                                    if (index < segments.size - 1) {
                                        val m = segments.toMutableList()
                                        val t = m[index]; m[index] = m[index + 1]; m[index + 1] = t
                                        segments = m
                                    }
                                },
                                onRemove = { showRemoveConfirm = index },
                            )
                            if (index < segments.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── 添加按钮 ──
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingSegmentIndex = -1; showKeyPicker = true },
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加按键")
                    }
                    OutlinedButton(onClick = { editingSegmentIndex = -1; showGroupPicker = true },
                        enabled = availableGroupButtons.isNotEmpty(),
                        modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加组按键")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── 保存 ──
                Button(onClick = { onSave(element.copy(value = buildValueString())) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
        }
    }

    // ── 键值选择器 ──
    if (showKeyPicker) {
        KeyValuePickerDialog(
            onSelect = { selectedValue, _ ->
                val ns = Segment(value = selectedValue, name = "")
                segments = if (editingSegmentIndex in segments.indices) {
                    segments.toMutableList().apply { this[editingSegmentIndex] = ns }
                } else {
                    (segments + ns).toMutableList()
                }
                showKeyPicker = false; editingSegmentIndex = -1
            },
            onDismiss = { showKeyPicker = false; editingSegmentIndex = -1 },
        )
    }

    // ── GroupButton 选择器 ──
    if (showGroupPicker) {
        GroupButtonPickerDialog(
            groupButtons = availableGroupButtons,
            onSelect = { gb ->
                val ns = Segment(value = "gb${gb.elementId}", name = gb.text.ifBlank { "组" })
                segments = if (editingSegmentIndex in segments.indices) {
                    segments.toMutableList().apply { this[editingSegmentIndex] = ns }
                } else {
                    (segments + ns).toMutableList()
                }
                showGroupPicker = false; editingSegmentIndex = -1
            },
            onDismiss = { showGroupPicker = false; editingSegmentIndex = -1 },
        )
    }

    // ── 删除确认 ──
    val removeIdx = showRemoveConfirm
    if (removeIdx != null && removeIdx in segments.indices) {
        val seg = segments[removeIdx]
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = null },
            title = { Text("移除分段") },
            text = { Text("确定移除分区 ${removeIdx + 1}「${getDisplayText(seg.value)}」吗？") },
            confirmButton = {
                Button(onClick = { segments = segments.toMutableList().apply { removeAt(removeIdx) }; showRemoveConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = null }) { Text("取消") } },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  分段行
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SegmentRow(
    index: Int,
    name: String,
    displayText: String,
    totalCount: Int,
    onNameChange: (String) -> Unit,
    onEditValue: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            // 可点击的值显示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onEditValue)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(displayText,
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text("编辑",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp)
            }
            // 名称输入（无 decorationBox）
            Spacer(Modifier.height(2.dp))
            BasicTextField(
                value = name,
                onValueChange = { if (it.length <= 20) onNameChange(it) },
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "上移",
                modifier = Modifier.size(14.dp),
                tint = if (index > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
        IconButton(onClick = onMoveDown, enabled = index < totalCount - 1, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "下移",
                modifier = Modifier.size(14.dp),
                tint = if (index < totalCount - 1) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "移除",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  GroupButton 选择器
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun GroupButtonPickerDialog(
    groupButtons: List<EditorElement>,
    onSelect: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("选择组按键",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (groupButtons.isEmpty()) {
                    Text("当前方案中暂无组按键",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    ) {
                        itemsIndexed(groupButtons) { index, gb ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(gb) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("组",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(gb.text.ifBlank { "GROUP" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("ID: ${gb.elementId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            if (index < groupButtons.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("共 ${groupButtons.size} 个组按键",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
