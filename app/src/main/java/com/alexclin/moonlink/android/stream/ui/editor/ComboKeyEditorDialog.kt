package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.util.ToastUtil
import android.widget.Toast

/**
 * 组合键创建对话框。
 *
 * 组合键（DigitalCombineButton）支持最多 5 个键值映射：
 * - 单击（主键，element_value）
 * - 上滑（element_up_value）
 * - 下滑（element_down_value）
 * - 左滑（element_left_value）
 * - 右滑（element_right_value）
 *
 * 直接显示新建表单，无列表/编辑模式。
 */
@Composable
fun ComboKeyEditorDialog(
    title: String = "新建组合键",
    initialElement: EditorElement? = null,
    onSaveNew: (EditorElement) -> Unit,
    onDismiss: () -> Unit,
    existingTextNames: Set<String> = emptySet(),
    isCreateMode: Boolean = false,
) {
    var newText by remember { mutableStateOf(initialElement?.text ?: "") }
    var newMainValue by remember { mutableStateOf(initialElement?.value ?: "k29") }
    var newUpValue by remember { mutableStateOf(initialElement?.upValue ?: "") }
    var newDownValue by remember { mutableStateOf(initialElement?.downValue ?: "") }
    var newLeftValue by remember { mutableStateOf(initialElement?.leftValue ?: "") }
    var newRightValue by remember { mutableStateOf(initialElement?.rightValue ?: "") }
    var showKeyPickerForSlot by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(
                indication = null,
                interactionSource = MutableInteractionSource()
            ) { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.5f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp,6.dp,16.dp,16.dp)) {
                // ── 标题行（标题 + 取消/保存按钮） ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        // 新建模式下必须选择主键（单击）
                        if (isCreateMode && newMainValue.isBlank()) {
                            ToastUtil.show(context, "请先选择单击键值", Toast.LENGTH_SHORT)
                            return@TextButton
                        }
                        // 名称查重
                        if (isDuplicateElementName(newText, existingTextNames)) {
                            val trimmedText = newText.trim()
                            ToastUtil.show(context, "已存在同名元素「$trimmedText」，请修改名称", Toast.LENGTH_SHORT)
                            return@TextButton
                        }
                        // 内部方向值互斥校验
                        val dupMsg = findDuplicateKeyValues(mapOf(
                            "单击" to newMainValue,
                            "上滑" to newUpValue,
                            "下滑" to newDownValue,
                            "左滑" to newLeftValue,
                            "右滑" to newRightValue,
                        ))
                        if (dupMsg != null) {
                            ToastUtil.show(context, "方向值重复：$dupMsg", Toast.LENGTH_SHORT)
                            return@TextButton
                        }
                        val newEl = EditorElement(
                            elementId = initialElement?.elementId ?: 0L,
                            configId = initialElement?.configId ?: 0L,
                            type = ElementType.DIGITAL_COMBINE_BUTTON,
                            text = newText,
                            value = newMainValue.ifBlank { "k29" },
                            upValue = newUpValue,
                            downValue = newDownValue,
                            leftValue = newLeftValue,
                            rightValue = newRightValue,
                            width = 100,
                            height = 100,
                        )
                        onSaveNew(newEl)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存", style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                // 按钮文字 | 单击（一行均分宽度，样式同下方键值槽位）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 左：按钮文字
                    Column(modifier = Modifier.weight(1f)) {
                        Text("按钮文字", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // 用 BasicTextField 代替 OutlinedTextField 以匹配 KeySlotItem 的紧凑高度
                            androidx.compose.foundation.text.BasicTextField(
                                value = newText,
                                onValueChange = { newText = it.take(10) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                            // 按钮文字为空时以单击键值名作为 hint
                            if (newText.isEmpty()) {
                                val hintText = "组合键${getKeyLabelByValue(newMainValue) ?: newMainValue}"
                                if (hintText.isNotEmpty()) {
                                    Text(
                                        hintText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                    // 右：单击
                    KeySlotItem(
                        label = "单击", value = newMainValue,
                        onClick = { showKeyPickerForSlot = "new_main" },
                        icon = Icons.Default.Keyboard,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ↑ 上滑 | ↓ 下滑（一行均分宽度）
                KeySlotRowPair(
                    label1 = "↑ 上滑", value1 = newUpValue, onClick1 = { showKeyPickerForSlot = "new_up" }, icon1 = Icons.Default.ArrowUpward,
                    label2 = "↓ 下滑", value2 = newDownValue, onClick2 = { showKeyPickerForSlot = "new_down" }, icon2 = Icons.Default.ArrowDownward,
                )

                // ← 左滑 | → 右滑（一行均分宽度）
                KeySlotRowPair(
                    label1 = "← 左滑", value1 = newLeftValue, onClick1 = { showKeyPickerForSlot = "new_left" }, icon1 = Icons.Default.ChevronLeft,
                    label2 = "→ 右滑", value2 = newRightValue, onClick2 = { showKeyPickerForSlot = "new_right" }, icon2 = Icons.Default.ChevronRight,
                )


            }
        }
    }

    // ── 键值选择器弹窗 ──
    val slot = showKeyPickerForSlot
    if (slot != null) {
        KeyValuePickerDialog(
            onSelect = { value, _ ->
                when (slot) {
                    "new_main" -> newMainValue = value
                    "new_up" -> newUpValue = value
                    "new_down" -> newDownValue = value
                    "new_left" -> newLeftValue = value
                    "new_right" -> newRightValue = value
                }
                showKeyPickerForSlot = null
            },
            onDismiss = { showKeyPickerForSlot = null },
        )
    }
}


