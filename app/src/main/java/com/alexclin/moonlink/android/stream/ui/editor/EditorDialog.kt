package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alexclin.moonlink.android.R

/**
 * 统一编辑对话框组件。
 *
 * 结构：
 * ```
 * ┌─────────────────────────────────┐
 * │  标题                  取消  保存 │  ← 标题行（取消/保存可选）
 * ├─────────────────────────────────┤
 * │                                 │  ← HorizontalDivider
 * │       content（由调用方提供）      │
 * │                                 │
 * └─────────────────────────────────┘
 * ```
 *
 * @param title       对话框标题
 * @param onDismiss   关闭对话框回调（点击外部/返回键）
 * @param onCancel    传非 null 时显示"取消"按钮
 * @param onSave      传非 null 时显示"保存"按钮
 * @param saveText    保存按钮文字（空字符串则使用默认 "保存"）
 * @param saveEnabled 保存按钮是否可点击（默认 true）
 * @param modifier    应用到 Surface 的修饰符（调用方可自定义宽高）
 * @param content     对话框主体内容
 */
@Composable
fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    saveText: String = "",
    saveEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .wrapContentHeight()
                .then(modifier),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .imePadding(),
            ) {
                // ── 标题行 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )

                    if (onCancel != null) {
                        TextButton(onClick = onCancel) {
                            Text(
                                stringResource(R.string.editor_cancel),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    if (onSave != null) {
                        TextButton(
                            onClick = onSave,
                            enabled = saveEnabled,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (saveText.isNotEmpty()) saveText
                                else stringResource(R.string.editor_save),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ── 内容区域 ──
                content()
            }
        }
    }
}
