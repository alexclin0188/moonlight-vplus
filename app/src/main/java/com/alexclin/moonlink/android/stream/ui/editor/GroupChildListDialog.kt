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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 组按键（GroupButton）子元素管理对话框。
 *
 * 功能：
 * - 展示当前组按键的所有子元素列表（含类型和名称）
 * - 添加新的子元素（从当前方案中未在组内的元素中选择）
 * - 移除子元素
 * - 调整子元素顺序（上移/下移）
 *
 * @param groupElement 当前的组按键元素
 * @param allElements  当前方案的所有元素（用于查找子元素详情和可选列表）
 * @param editorState  编辑器状态（用于加载元素详情）
 * @param onSave       保存修改后的组按键元素（[EditorElement.value] 已更新）
 * @param onDismiss    关闭对话框
 */
@Composable
fun GroupChildListDialog(
    groupElement: EditorElement,
    allElements: List<EditorElement>,
    editorState: EditorState,
    onSave: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
) {
    // 解析当前子元素 ID 列表（跳过首位的 "-1" 占位）
    val initialChildIds = remember {
        groupElement.value
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it != -1L }
            .toMutableList()
    }
    var childIds by remember { mutableStateOf(initialChildIds) }
    var showAddPicker by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<Long?>(null) }

    // 根据 ID 查找元素详情
    fun findElementById(id: Long): EditorElement? = allElements.find { it.elementId == id }

    // 可以添加的元素：不是当前组、不是已存在的子元素、不是 GroupButton（禁止嵌套）
    val availableElements = remember(allElements, childIds, groupElement.elementId) {
        allElements.filter { el ->
            el.elementId != groupElement.elementId
                && el.elementId !in childIds
                && el.type != ElementType.GROUP_BUTTON
        }
    }

    // ── 构建新的 value 字符串 ──
    fun buildValueString(): String {
        if (childIds.isEmpty()) return "-1"
        return "-1," + childIds.joinToString(",")
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
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
            ) {
                // ── 标题栏 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "子按键管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(20.dp))
                    }
                }

                Text(
                    "组：「${groupElement.text.ifBlank { "GROUP" }}」 — ${childIds.size} 个子元素",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── 子元素列表 ──
                if (childIds.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("暂无子元素，点击下方「添加」按钮添加",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    ) {
                        itemsIndexed(childIds, key = { _, id -> id }) { index, childId ->
                            val child = findElementById(childId)
                            ChildItemRow(
                                index = index,
                                childElement = child,
                                totalCount = childIds.size,
                                onMoveUp = {
                                    if (index > 0) {
                                        val mutable = childIds.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index - 1]
                                        mutable[index - 1] = temp
                                        childIds = mutable
                                    }
                                },
                                onMoveDown = {
                                    if (index < childIds.size - 1) {
                                        val mutable = childIds.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index + 1]
                                        mutable[index + 1] = temp
                                        childIds = mutable
                                    }
                                },
                                onRemove = { showRemoveConfirm = childId },
                            )
                            if (index < childIds.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 操作按钮 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showAddPicker = true },
                        enabled = availableElements.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加子按键")
                    }
                    Button(
                        onClick = {
                            val updated = groupElement.copy(value = buildValueString())
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "提示：组按键的子元素不可为另一组按键",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }

    // ── 添加子元素选择器 ──
    if (showAddPicker) {
        AddChildPickerDialog(
            availableElements = availableElements,
            onSelect = { selectedId ->
                val mutable = childIds.toMutableList()
                if (selectedId !in mutable) {
                    mutable.add(selectedId)
                    childIds = mutable
                }
                showAddPicker = false
            },
            onDismiss = { showAddPicker = false },
        )
    }

    // ── 删除确认 ──
    val removeId = showRemoveConfirm
    if (removeId != null) {
        val removeEl = findElementById(removeId)
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = null },
            title = { Text("移除子按键") },
            text = {
                Text("确定将「${removeEl?.text?.ifBlank { removeEl?.type?.displayName ?: "元素" }}」从该组中移除吗？\n（元素不会被删除）")
            },
            confirmButton = {
                Button(
                    onClick = {
                        childIds = childIds.toMutableList().apply { remove(removeId) }
                        showRemoveConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = null }) { Text("取消") }
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  子元素行
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChildItemRow(
    index: Int,
    childElement: EditorElement?,
    totalCount: Int,
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
        // 序号
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        // 类型标签
        val typeLabel = childElement?.type?.displayName?.let { t ->
            when {
                t.length <= 4 -> t
                else -> t.take(4)
            }
        } ?: "?"
        Text(
            typeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        Spacer(Modifier.width(8.dp))

        // 元素名称 / 文字
        Text(
            childElement?.text?.ifBlank { "(${childElement?.type?.displayName ?: "未知"})" } ?: "未知元素",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // ── 操作按钮 ──
        // 上移
        IconButton(
            onClick = onMoveUp,
            enabled = index > 0,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "上移",
                modifier = Modifier.size(16.dp),
                tint = if (index > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
        // 下移
        IconButton(
            onClick = onMoveDown,
            enabled = index < totalCount - 1,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "下移",
                modifier = Modifier.size(16.dp),
                tint = if (index < totalCount - 1) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
        // 删除
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Default.Delete, contentDescription = "移除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  添加子元素选择器
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AddChildPickerDialog(
    availableElements: List<EditorElement>,
    onSelect: (Long) -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("选择要添加的子按键",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭",
                            modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                ) {
                    items(availableElements.size) { index ->
                        val el = availableElements[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(el.elementId) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                el.type.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                el.text.ifBlank { "(${el.type.displayName})" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (index < availableElements.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "共 ${availableElements.size} 个可用元素",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
