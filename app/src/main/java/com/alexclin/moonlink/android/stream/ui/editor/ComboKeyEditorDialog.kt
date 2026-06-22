package com.alexclin.moonlink.android.stream.ui.editor

import android.content.ContentValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.binding.input.advance_setting.element.Element

/**
 * 组合键编辑器对话框。
 *
 * 组合键（DigitalCombineButton）支持最多 5 个键值映射：
 * - 单击（主键，element_value）
 * - 上滑（element_up_value）
 * - 下滑（element_down_value）
 * - 左滑（element_left_value）
 * - 右滑（element_right_value）
 *
 * 用户可以在此对话框中创建新组合键、编辑现有组合键的各个方向键值。
 */
@Composable
fun ComboKeyEditorDialog(
    existingComboKeys: List<EditorElement>,
    onSaveNew: (EditorElement) -> Unit,
    onSaveExisting: (EditorElement) -> Unit,
    onDeleteExisting: ((EditorElement) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    // ── 编辑状态 ──
    var editingElement by remember { mutableStateOf<EditorElement?>(null) }
    var showNewForm by remember { mutableStateOf(false) }
    var showKeyPickerForSlot by remember { mutableStateOf<String?>(null) } // "main" / "up" / "down" / "left" / "right"

    // 新建表单状态
    var newText by remember { mutableStateOf("组合键") }
    var newMainValue by remember { mutableStateOf("k29") }
    var newUpValue by remember { mutableStateOf("") }
    var newDownValue by remember { mutableStateOf("") }
    var newLeftValue by remember { mutableStateOf("") }
    var newRightValue by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(
                indication = null,
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
            ) { /* 不关闭，由内部按钮关闭 */ },
        contentAlignment = Alignment.Center,
    ) {
        // ── 编辑现有组合键 ──
        if (editingElement != null) {
            ComboKeyEditForm(
                element = editingElement!!,
                onSlotValueChange = { slot, value ->
                    editingElement = editingElement!!.copy(
                        value = if (slot == "main") value else editingElement!!.value,
                        upValue = if (slot == "up") value else editingElement!!.upValue,
                        downValue = if (slot == "down") value else editingElement!!.downValue,
                        leftValue = if (slot == "left") value else editingElement!!.leftValue,
                        rightValue = if (slot == "right") value else editingElement!!.rightValue,
                    )
                },
                onShowKeyPicker = { showKeyPickerForSlot = it },
                onSave = {
                    val el = editingElement!!
                    onSaveExisting(el)
                    editingElement = null
                },
                onDelete = {
                    onDeleteExisting?.invoke(editingElement!!)
                    editingElement = null
                },
                onCancel = { editingElement = null },
            )
        }
        // ── 新建组合键表单 ──
        else if (showNewForm) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("新建组合键", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showNewForm = false }) {
                            Icon(Icons.Default.Close, null, Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it.take(10) },
                        label = { Text("按钮文字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("键值方向", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    KeySlotRow("单击", newMainValue, { showKeyPickerForSlot = "new_main" }, Icons.Default.Keyboard)
                    KeySlotRow("↑ 上滑", newUpValue, { showKeyPickerForSlot = "new_up" }, Icons.Default.ArrowUpward)
                    KeySlotRow("↓ 下滑", newDownValue, { showKeyPickerForSlot = "new_down" }, Icons.Default.ArrowDownward)
                    KeySlotRow("← 左滑", newLeftValue, { showKeyPickerForSlot = "new_left" }, Icons.Default.ChevronLeft)
                    KeySlotRow("→ 右滑", newRightValue, { showKeyPickerForSlot = "new_right" }, Icons.Default.ChevronRight)

                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val newEl = EditorElement(
                            elementId = 0L, // 占位，由调用方分配
                            configId = 0L,
                            type = ElementType.DIGITAL_COMBINE_BUTTON,
                            text = newText.ifBlank { "组合键" },
                            value = newMainValue.ifBlank { "k29" },
                            upValue = newUpValue,
                            downValue = newDownValue,
                            leftValue = newLeftValue,
                            rightValue = newRightValue,
                            width = 100,
                            height = 100,
                        )
                        onSaveNew(newEl)
                        showNewForm = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("创建组合键")
                    }
                }
            }
        }
        // ── 列表视图 ──
        else {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).fillMaxSize(0.8f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── 标题 ──
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Keyboard, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("组合键编辑", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "关闭", Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider()

                    // ── 新建按钮 ──
                    OutlinedButton(
                        onClick = {
                            newText = "组合键"
                            newMainValue = "k29"
                            newUpValue = ""
                            newDownValue = ""
                            newLeftValue = ""
                            newRightValue = ""
                            showNewForm = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建组合键")
                    }
                    HorizontalDivider()

                    // ── 列表 ──
                    if (existingComboKeys.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无组合键，点击上方按钮创建",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(existingComboKeys, key = { it.elementId }) { combo ->
                                ComboKeyListItem(
                                    element = combo,
                                    onClick = { editingElement = combo },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 键值选择器弹窗 ──
    val slot = showKeyPickerForSlot
    if (slot != null) {
        KeyValuePickerDialog(
            onSelect = { value, _ ->
                when (slot) {
                    "new_main" -> { newMainValue = value }
                    "new_up" -> { newUpValue = value }
                    "new_down" -> { newDownValue = value }
                    "new_left" -> { newLeftValue = value }
                    "new_right" -> { newRightValue = value }
                    "main" -> editingElement = editingElement?.copy(value = value)
                    "up" -> editingElement = editingElement?.copy(upValue = value)
                    "down" -> editingElement = editingElement?.copy(downValue = value)
                    "left" -> editingElement = editingElement?.copy(leftValue = value)
                    "right" -> editingElement = editingElement?.copy(rightValue = value)
                }
                showKeyPickerForSlot = null
            },
            onDismiss = { showKeyPickerForSlot = null },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  组合键编辑表单
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ComboKeyEditForm(
    element: EditorElement,
    onSlotValueChange: (slot: String, value: String) -> Unit,
    onShowKeyPicker: (slot: String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.9f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                Text("编辑组合键", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("\"${element.text}\" — ${element.elementId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Text("键值方向（点击选择）", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            KeySlotRow("单击", element.value, { onShowKeyPicker("main") }, Icons.Default.Keyboard)
            KeySlotRow("↑ 上滑", element.upValue, { onShowKeyPicker("up") }, Icons.Default.ArrowUpward)
            KeySlotRow("↓ 下滑", element.downValue, { onShowKeyPicker("down") }, Icons.Default.ArrowDownward)
            KeySlotRow("← 左滑", element.leftValue, { onShowKeyPicker("left") }, Icons.Default.ChevronLeft)
            KeySlotRow("→ 右滑", element.rightValue, { onShowKeyPicker("right") }, Icons.Default.ChevronRight)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("删除此组合键", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  组合键列表项
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ComboKeyListItem(
    element: EditorElement,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 文字
        Column(modifier = Modifier.weight(1f)) {
            Text(element.text.ifBlank { "组合键" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            val slots = buildString {
                append("单击: ${getKeyLabelByValue(element.value) ?: element.value}")
                if (element.upValue.isNotEmpty()) append(" ↑:${getKeyLabelByValue(element.upValue) ?: element.upValue}")
                if (element.downValue.isNotEmpty()) append(" ↓:${getKeyLabelByValue(element.downValue) ?: element.downValue}")
                if (element.leftValue.isNotEmpty()) append(" ←:${getKeyLabelByValue(element.leftValue) ?: element.leftValue}")
                if (element.rightValue.isNotEmpty()) append(" →:${getKeyLabelByValue(element.rightValue) ?: element.rightValue}")
            }
            Text(slots, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 maxLines = 1)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  键值槽位行
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun KeySlotRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val keyLabel = getKeyLabelByValue(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (value.isNotEmpty()) (keyLabel ?: value) else "点击选择",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (value.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
