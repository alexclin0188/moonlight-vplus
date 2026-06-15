package com.alexclin.moonlink.stream.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.stream.ui.common.CustomKeyRepository

/**
 * 删除自定义按键对话框。
 *
 * 展示所有已保存的自定义按键列表，支持多选后批量删除。
 *
 * @param onDismiss 关闭对话框回调
 * @param onDeleted 删除成功回调（可用于刷新列表）
 */
@Composable
fun DeleteCustomKeyDialog(
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current

    val keys = remember { CustomKeyRepository.loadAll(context) }
    val checkedIndices = remember { mutableStateListOf<Int>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun deleteSelected() {
        if (checkedIndices.isEmpty()) {
            errorMessage = "请选择要删除的按键"
            return
        }

        val namesToDelete = checkedIndices.map { keys[it].name }
        val success = CustomKeyRepository.delete(context, namesToDelete)
        if (success) {
            onDeleted()
        } else {
            errorMessage = "删除失败，请重试"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("删除自定义按键")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (keys.isEmpty()) {
                    Text(
                        text = "暂无自定义按键",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    Text(
                        text = "选择要删除的按键：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .padding(top = 8.dp),
                    ) {
                        items(keys.indices.toList()) { index ->
                            val key = keys[index]
                            val isChecked = index in checkedIndices

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            checkedIndices.add(index)
                                        } else {
                                            checkedIndices.remove(index)
                                        }
                                    },
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = key.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "键码: ${key.keys.joinToString(", ") { "0x${it.toInt().and(0xFF).toString(16).uppercase()}" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (index < keys.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { deleteSelected() },
                enabled = keys.isNotEmpty(),
            ) {
                Text(
                    "删除",
                    color = if (keys.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
