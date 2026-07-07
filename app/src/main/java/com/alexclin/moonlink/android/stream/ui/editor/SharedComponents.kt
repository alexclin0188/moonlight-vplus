package com.alexclin.moonlink.android.stream.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R

// ════════════════════════════════════════════════════════════════════════════
//  单个键值槽位（紧凑布局，标签在上、值在下）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 可点击的键值槽位，标签在上、值在下，点击弹出键值选择器。
 * 用于组合键对话框和类型专属属性设置对话框。
 */
@Composable
internal fun KeySlotItem(
    label: String,
    value: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val keyLabel = getKeyLabelByValue(value)
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (value.isNotEmpty()) (keyLabel ?: value) else stringResource(R.string.kme_shared_tap_to_select),
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

// ════════════════════════════════════════════════════════════════════════════
//  键值槽位对（两个槽位并列，各占一半宽度）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 两个 [KeySlotItem] 并列，各占一半宽度。
 * 用于组合键对话框的上滑/下滑、左滑/右滑配对布局。
 */
@Composable
internal fun KeySlotRowPair(
    label1: String, value1: String, onClick1: () -> Unit, icon1: ImageVector? = null,
    label2: String, value2: String, onClick2: () -> Unit, icon2: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KeySlotItem(
            label = label1, value = value1, onClick = onClick1, icon = icon1,
            modifier = Modifier.weight(1f),
        )
        KeySlotItem(
            label = label2, value = value2, onClick = onClick2, icon = icon2,
            modifier = Modifier.weight(1f),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  方向键值行（↑↓←→，可选标签）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 方向键值 ↑↓←→ 两行布局，可选显示"方向键值"标签。
 * 用于十字键 / 数字摇杆的属性设置对话框。
 */
@Composable
internal fun DirectionValueFields(
    upValue: String,
    downValue: String,
    leftValue: String,
    rightValue: String,
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KeySlotItem(stringResource(R.string.kme_dir_up), upValue,
            onClick = onUpClick,
            icon = Icons.Default.ArrowUpward,
            modifier = Modifier.weight(1f))
        KeySlotItem(stringResource(R.string.kme_dir_down), downValue,
            onClick = onDownClick,
            icon = Icons.Default.ArrowDownward,
            modifier = Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KeySlotItem(stringResource(R.string.kme_dir_left), leftValue,
            onClick = onLeftClick,
            icon = Icons.Default.ChevronLeft,
            modifier = Modifier.weight(1f))
        KeySlotItem(stringResource(R.string.kme_dir_right), rightValue,
            onClick = onRightClick,
            icon = Icons.Default.ChevronRight,
            modifier = Modifier.weight(1f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  小型文字/整数输入框
// ═══════════════════════════════════════════════════════════════════════════════

/** 小型文字输入框 */
@Composable
internal fun SmallTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = 10,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { newVal ->
            if (newVal.length <= maxLength) onValueChange(newVal)
        },
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        ),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 小型整数输入框 */
@Composable
internal fun SmallIntField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = 6,
) {
    val focusManager = LocalFocusManager.current
    var displayValue by remember { mutableStateOf(value) }
    var rejectKey by remember { mutableStateOf(0) }
    LaunchedEffect(value) { displayValue = value }

    key(rejectKey) {
        BasicTextField(
            value = displayValue,
            onValueChange = { newVal ->
                val isValid = (newVal.isEmpty() || newVal.matches(Regex("-?\\d*"))) && newVal.length <= maxLength
                if (isValid) {
                    displayValue = newVal
                    onValueChange(newVal)
                } else {
                    rejectKey++
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            modifier = modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

