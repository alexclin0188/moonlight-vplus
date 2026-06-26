package com.alexclin.moonlink.android.stream.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 紧凑型 Chip（取代 FilterChip，内边距减半）。
 *
 * @param label 芯片文本
 * @param selected 是否选中
 * @param onClick 点击回调
 * @param modifier 外部修饰符
 */
@Composable
fun CompactChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                 else null,
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
            )
        }
    }
}

/**
 * 单选芯片组（紧凑版）。
 *
 * 使用 [CompactChip] 渲染，一行排列；当 [columns] 指定时按指定列数换行。
 *
 * @param options (label, value) 对
 * @param selectedValue 当前选中的值
 * @param onSelect 选中回调
 * @param columns 每行最大列数；null = 所有 chip 在一行内均分
 * @param spacingDp 行间距（dp），默认 6
 */
@Composable
fun ChipSelector(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    columns: Int? = null,
    spacingDp: Int = 6,
) {
    val gapDp = spacingDp.dp
    if (columns != null) {
        // 按指定列数换行
        Column(verticalArrangement = Arrangement.spacedBy(gapDp)) {
            options.chunked(columns).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gapDp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowItems.forEach { (label, value) ->
                        CompactChip(
                            label = label,
                            selected = value == selectedValue,
                            onClick = { onSelect(value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gapDp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (label, value) ->
                CompactChip(
                    label = label,
                    selected = value == selectedValue,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
