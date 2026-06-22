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
import androidx.compose.ui.unit.dp
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
            errorMessage = "名称不能为空"
            return
        }

        val codes = hexCodesText
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (codes.isEmpty()) {
            errorMessage = "请输入至少一个键码"
            return
        }

        // 检查格式
        for (code in codes) {
            if (!code.startsWith("0x") && !code.startsWith("0X")) {
                errorMessage = "键码 \"$code\" 格式错误，须以 0x 开头"
                return
            }
            try {
                code.substring(2).toInt(16)
            } catch (_: NumberFormatException) {
                errorMessage = "键码 \"$code\" 不是有效的十六进制数"
                return
            }
        }

        // 重复名称检查
        if (CustomKeyRepository.hasDuplicateName(context, trimmedName)) {
            errorMessage = "名称 \"$trimmedName\" 已存在"
            return
        }

        // 执行保存
        when (val result = CustomKeyRepository.save(context, trimmedName, codes)) {
            is SaveResult.Success -> {
                errorMessage = null
                onSaved()
            }
            is SaveResult.DuplicateName -> {
                errorMessage = "名称 \"${result.name}\" 已存在"
            }
            is SaveResult.Error -> {
                errorMessage = result.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加自定义按键")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("按键名称") },
                    placeholder = { Text("如：发送 F11") },
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
                    label = { Text("键码（十六进制，逗号分隔）") },
                    placeholder = { Text("如：0x7A 或 0x5B,0x44") },
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
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
