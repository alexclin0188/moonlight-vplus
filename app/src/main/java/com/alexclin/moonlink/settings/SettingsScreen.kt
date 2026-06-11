package com.alexclin.moonlink.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.limelight.widget.WidgetConfigurationActivity

/**
 * Settings category list — Tab 3 of the main page.
 * Each item navigates to a sub-screen for that category.
 */
data class SettingsCategory(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val route: String,
)

val SETTINGS_CATEGORIES = listOf(
    SettingsCategory("ui",          "界面设置",       Icons.Default.Palette,         "settings_ui"),
    SettingsCategory("audio",       "音频设置",       Icons.Default.VolumeUp,        "settings_audio"),
    SettingsCategory("gamepad",     "手柄设置",       Icons.Default.Gamepad,         "settings_gamepad"),
    SettingsCategory("input",       "输入设置",       Icons.Default.Keyboard,        "settings_input"),
    SettingsCategory("multitouch",  "多点触控设置",    Icons.Default.TouchApp,        "settings_multitouch"),
    SettingsCategory("connection",  "连接设置",       Icons.Default.Cable,           "settings_connection"),
    SettingsCategory("scene",       "场景预设",       Icons.Default.Slideshow,       "settings_scene"),
    SettingsCategory("keymapping",  "按键配置管理",    Icons.Default.Tune,            "settings_keymapping"),
    SettingsCategory("widget",      "桌面小部件",     Icons.Default.Widgets,         "_widget"),
    SettingsCategory("help",        "帮助",          Icons.Default.HelpOutline,     "settings_help"),
)

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
    ) {
            items(SETTINGS_CATEGORIES.size) { index ->
                val cat = SETTINGS_CATEGORIES[index]
                ListItem(
                    headlineContent = {
                        Text(cat.title, style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingContent = {
                        Icon(
                            cat.icon,
                            contentDescription = cat.title,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (cat.route == "_widget") {
                                // Launch widget configuration activity directly
                                context.startActivity(
                                    Intent(context, WidgetConfigurationActivity::class.java)
                                )
                            } else {
                                onNavigate(cat.route)
                            }
                        },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
}

