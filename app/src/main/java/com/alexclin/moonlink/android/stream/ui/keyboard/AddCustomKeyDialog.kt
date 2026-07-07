package com.alexclin.moonlink.android.stream.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.stream.ui.common.CustomKeyRepository
import com.alexclin.moonlink.android.stream.ui.common.SaveResult

/**
 * 添加自定义按键对话框。
 *
 * 用户输入名称和以逗号分隔的十六进制键码（如 "0x7A,0x44"），
 * 经格式验证和重复名称检查后保存至 SharedPreferences。
 *
 * @param onDismiss 关闭对话框回调
 * @param onSaved 保存成功回调（可用于刷新列表）
 */
@Composable
fun AddCustomKeyDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var hexCodesText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validateAndSave() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            errorMessage = context.getString(R.string.customkey_error_name_empty)
            return
        }

        val codes = hexCodesText
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (codes.isEmpty()) {
            errorMessage = context.getString(R.string.customkey_error_codes_empty)
            return
        }

        // 检查格式
        for (code in codes) {
            if (!code.startsWith("0x") && !code.startsWith("0X")) {
                errorMessage = context.getString(R.string.customkey_error_code_format, code)
                return
            }
            try {
                code.substring(2).toInt(16)
            } catch (_: NumberFormatException) {
                errorMessage = context.getString(R.string.customkey_error_code_invalid, code)
                return
            }
        }

        // 重复名称检查
        if (CustomKeyRepository.hasDuplicateName(context, trimmedName)) {
            errorMessage = context.getString(R.string.customkey_error_name_duplicate, trimmedName)
            return
        }

        // 执行保存
        when (val result = CustomKeyRepository.save(context, trimmedName, codes)) {
            is SaveResult.Success -> {
                errorMessage = null
                onSaved()
            }
            is SaveResult.DuplicateName -> {
                errorMessage = context.getString(R.string.customkey_error_name_duplicate, result.name)
            }
            is SaveResult.Error -> {
                errorMessage = result.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.customkey_dialog_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.customkey_label_name)) },
                    placeholder = { Text(stringResource(R.string.customkey_hint_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && name.trim().isEmpty(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hexCodesText,
                    onValueChange = {
                        hexCodesText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.customkey_label_codes)) },
                    placeholder = { Text(stringResource(R.string.customkey_hint_codes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && hexCodesText.isBlank(),
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
        },
        confirmButton = {
            TextButton(onClick = { validateAndSave() }) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}
