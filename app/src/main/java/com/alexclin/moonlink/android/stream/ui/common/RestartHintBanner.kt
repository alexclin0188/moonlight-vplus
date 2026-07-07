package com.alexclin.moonlink.android.stream.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R

/**
 *
 * 在各子面板（显示设置、主机设置等）顶部显示，引导用户关闭面板触发重启。
 */
@Composable
fun RestartHintBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.restart_hint_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}
