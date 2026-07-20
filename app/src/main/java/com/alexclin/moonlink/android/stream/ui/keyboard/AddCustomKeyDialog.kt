package com.alexclin.moonlink.android.stream.ui.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.stream.ui.common.CustomKeyRepository
import com.alexclin.moonlink.android.stream.ui.common.SaveResult
import com.alexclin.moonlink.android.stream.ui.editor.EditorDialog
import com.alexclin.moonlink.android.stream.ui.editor.KeyValuePickerDialog
import com.alexclin.moonlink.android.stream.ui.editor.getKeyLabelByValue
import com.limelight.binding.input.KeyboardTranslator

/**
 * 添加自定义快捷键对话框。
 *
 * 用户通过[KeyValuePickerDialog]逐个选择按键（一次选一个，多次选多个），
 * 名称根据所选按键的键值名自动生成（如 "Ctrl+Space"），
 * 并输入功能说明后保存至 SharedPreferences。
 *
 * @param onDismiss 关闭对话框回调
 * @param onSaved 保存成功回调（可用于刷新列表）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCustomKeyDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val keyboardTranslator = remember { KeyboardTranslator() }

    var description by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val selectedPickerValues = remember { mutableStateListOf<String>() }
    var showKeyPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    /** 根据已选按键的标签生成名称（如 ["k29","k62"] → "A+Space"） */
    fun generateName(): String {
        if (selectedPickerValues.isEmpty()) return ""
        val labels = selectedPickerValues.mapNotNull { getKeyLabelByValue(it) }
        return labels.joinToString("+")
    }

    /** 如果名称重复，自动追加编号 */
    fun resolveName(context: android.content.Context, baseName: String): String {
        if (!CustomKeyRepository.hasDuplicateName(context, baseName)) return baseName
        var suffix = 2
        while (CustomKeyRepository.hasDuplicateName(context, "$baseName ($suffix)")) {
            suffix++
        }
        return "$baseName ($suffix)"
    }

    fun validateAndSave() {
        if (selectedPickerValues.isEmpty()) {
            errorMessage = context.getString(R.string.customkey_error_keys_empty)
            return
        }

        // 将选择器的值转换为 VK 十六进制码
        val hexCodes = selectedPickerValues.mapNotNull { value ->
            if (value.startsWith("k") && value.length > 1) {
                val keyCode = value.substring(1).toIntOrNull()
                if (keyCode != null) {
                    val vkCode = keyboardTranslator.translate(keyCode, -1).toInt()
                    if (vkCode != 0) "0x${(vkCode and 0xFF).toString(16).uppercase()}" else null
                } else null
            } else null
        }
        if (hexCodes.isEmpty()) {
            errorMessage = context.getString(R.string.customkey_error_keys_empty)
            return
        }

        // 自动生成名称，并处理重名
        val baseName = generateName()
        val finalName = resolveName(context, baseName)

        // 执行保存
        when (val result = CustomKeyRepository.save(context, finalName, description.trim(), hexCodes)) {
            is SaveResult.Success -> {
                errorMessage = null
                onSaved()
            }
            is SaveResult.DuplicateName -> {
                // 理论上不会走到这里，因为 resolveName 已处理重名
                errorMessage = context.getString(R.string.customkey_error_name_duplicate, result.name)
            }
            is SaveResult.Error -> {
                errorMessage = result.message
            }
        }
    }

    EditorDialog(
        title = stringResource(R.string.customkey_dialog_title),
        onDismiss = onDismiss,
        onCancel = onDismiss,
        onSave = { validateAndSave() },
        modifier = Modifier.fillMaxWidth(0.5f).wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 已选按键区域（最上方） ──
            Text(
                text = stringResource(R.string.customkey_label_selected_keys),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (selectedPickerValues.isEmpty()) {
                // 空状态提示
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showKeyPicker = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.customkey_hint_add_key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // 已选按键标签流式布局
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    selectedPickerValues.forEachIndexed { index, value ->
                        val label = getKeyLabelByValue(value) ?: value
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium,
                                )
                                IconButton(
                                    onClick = { selectedPickerValues.removeAt(index) },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.editor_content_desc_delete),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 添加更多按键按钮
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showKeyPicker = true },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.customkey_hint_add_key),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                // 自动生成的名称提示
                if (selectedPickerValues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = generateName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 功能说明输入 ──
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    errorMessage = null
                },
                label = { Text(stringResource(R.string.customkey_label_description)) },
                placeholder = { Text(stringResource(R.string.customkey_hint_description)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    // ── 键值选择器弹窗 ──
    if (showKeyPicker) {
        KeyValuePickerDialog(
            onSelect = { value, _ ->
                // 仅添加键盘按键（kXX 格式），避免添加无 VK 映射的键
                if (value.startsWith("k") && value !in selectedPickerValues) {
                    selectedPickerValues.add(value)
                }
                showKeyPicker = false
            },
            onDismiss = { showKeyPicker = false },
        )
    }
}
