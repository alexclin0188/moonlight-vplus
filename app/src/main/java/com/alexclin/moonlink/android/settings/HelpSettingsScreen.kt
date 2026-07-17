package com.alexclin.moonlink.android.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.BuildConfig
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.util.UpdateManager

/**
 * 帮助 — 3 items per design doc section 6.8.
 */
@Composable
fun HelpSettingsScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
            ListItem(
                headlineContent = { Text(context.getString(R.string.check_for_updates)) },
                supportingContent = { Text(context.getString(R.string.summary_check_for_updates) + ": ${BuildConfig.VERSION_NAME}") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        UpdateManager.checkForUpdates(context, false)
                    },
            )
            ListItem(
                headlineContent = { Text(context.getString(R.string.title_privacy_policy)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/alexclin0188/moonlink/blob/master/PRIVACY_POLICY.md")
                        )
                        context.startActivity(intent)
                    },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
}
