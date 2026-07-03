package com.alexclin.moonlink.android.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alexclin.moonlink.android.R

/**
 * Connection settings page — stream connection configuration items.
 *
 * Contains 4 config items:
 * - Auto-resume stream (checkbox_resume_stream)
 * - Keep connection alive (checkbox_extreme_resume, depends on auto-resume)
 * - Background audio playback (checkbox_background_audio, depends on keep-alive)
 * - Obtain public IP (checkbox_enable_stun, independent)
 */
@Composable
fun ConnectionSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            CheckBoxPreference(
                key = "checkbox_resume_stream",
                title = stringResource(R.string.title_checkbox_resume_stream),
                summary = stringResource(R.string.summary_checkbox_resume_stream),
                defaultValue = false,
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_extreme_resume",
                title = stringResource(R.string.title_extreme_resume),
                summary = stringResource(R.string.summary_extreme_resume),
                defaultValue = false,
                dependency = "checkbox_resume_stream",
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_background_audio",
                title = stringResource(R.string.title_background_audio),
                summary = stringResource(R.string.summary_background_audio),
                defaultValue = false,
                dependency = "checkbox_extreme_resume",
            )
        }
        item {
            CheckBoxPreference(
                key = "checkbox_enable_stun",
                title = stringResource(R.string.title_enable_stun),
                summary = stringResource(R.string.summary_enable_stun),
                defaultValue = false,
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.fillMaxSize())
        }
    }
}
