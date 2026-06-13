package com.alexclin.moonlink.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.navigation.MoonLinkRoute
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails

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
    SettingsCategory("ui",          "界面设置",           Icons.Default.Palette,         "settings_ui"),
    SettingsCategory("performance", "性能与统计分析",      Icons.Default.Speed,           "settings_performance"),
    SettingsCategory("audio",       "音频设置",           Icons.Default.VolumeUp,        "settings_audio"),
    SettingsCategory("gamepad",     "手柄设置",           Icons.Default.Gamepad,         "settings_gamepad"),
    SettingsCategory("input",       "输入设置",           Icons.Default.Keyboard,        "settings_input"),
    SettingsCategory("multitouch",  "多点触控设置",        Icons.Default.TouchApp,        "settings_multitouch"),
    SettingsCategory("connection",  "连接设置",           Icons.Default.Cable,           "settings_connection"),
    SettingsCategory("scene",       "场景预设",           Icons.Default.Slideshow,       "settings_scene"),
    SettingsCategory("keymapping",  "按键配置管理",        Icons.Default.Tune,            "settings_keymapping"),
    SettingsCategory("widget",      "桌面小部件",         Icons.Default.Widgets,         "_widget"),
    SettingsCategory("help",        "帮助",              Icons.Default.HelpOutline,     "settings_help"),
)

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    managerBinder: ComputerManagerService.ComputerManagerBinder? = null,
    computers: List<ComputerDetails>? = null,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    if (isLandscape) {
        LandscapeSettingsContent(
            onNavigate = onNavigate,
            managerBinder = managerBinder,
            computers = computers,
        )
    } else {
        PortraitSettingsList(onNavigate = onNavigate)
    }
}

// ── Portrait: single-column list (existing behavior) ──────────────

@Composable
private fun PortraitSettingsList(
    onNavigate: (String) -> Unit,
) {
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
                            onNavigate(MoonLinkRoute.SettingsWidget.route)
                        } else {
                            onNavigate(cat.route)
                        }
                    },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

// ── Landscape: animated two-pane ────────────────────────────
//   - 无子页面时左栏占满内容区
//   - 展开子页面时左栏收缩到 200.dp，右栏从右侧滑入
//   - 再次点击已展开项时左栏平滑展开将右栏挤出屏幕

@Composable
private fun LandscapeSettingsContent(
    onNavigate: (String) -> Unit,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    computers: List<ComputerDetails>?,
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }

    // cachedKey stays set during the exit animation so the content
    // remains visible while sliding out, then is cleared afterward.
    var cachedKey by remember { mutableStateOf<String?>(null) }
    val displayKey = expandedKey ?: cachedKey

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val isExpanded = expandedKey != null

        val leftWidth by animateDpAsState(
            targetValue = if (isExpanded) 200.dp else maxW,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "leftWidth",
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left: category list (animated width) ────────
            LazyColumn(
                modifier = Modifier
                    .width(leftWidth)
                    .fillMaxHeight(),
            ) {
                items(SETTINGS_CATEGORIES.size) { index ->
                    val cat = SETTINGS_CATEGORIES[index]
                    val expanded = cat.key == expandedKey
                    ListItem(
                        headlineContent = {
                            Text(
                                cat.title,
                                style = if (expanded)
                                    MaterialTheme.typography.bodyLarge
                                else
                                    MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = {
                            Icon(
                                cat.icon,
                                contentDescription = cat.title,
                                tint = if (expanded)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (expanded)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (cat.route == "_widget") {
                                    onNavigate(MoonLinkRoute.SettingsWidget.route)
                                } else {
                                    expandedKey = if (expanded) null else cat.key
                                }
                            },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            // ── Right panel: visible when left side leaves room ──
            if (leftWidth < maxW) {
                Row(modifier = Modifier.fillMaxSize()) {
                    VerticalDivider()
                    Box(modifier = Modifier.fillMaxSize()) {
                        displayKey?.let { key ->
                            SettingsCategoryContent(
                                key = key,
                                managerBinder = managerBinder,
                                computers = computers,
                            )
                        }
                    }
                }
            }
        }
    }

    // Keep cachedKey in sync: set immediately on expand, delay clear on collapse
    LaunchedEffect(expandedKey) {
        if (expandedKey != null) {
            cachedKey = expandedKey
        } else {
            // Delay clearing so the cached content stays visible during exit animation
            kotlinx.coroutines.delay(300L)
            if (expandedKey == null) {
                cachedKey = null
            }
        }
    }
}

// ── Category content router ───────────────────────────────────────

@Composable
private fun SettingsCategoryContent(
    key: String,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    computers: List<ComputerDetails>?,
) {
    when (key) {
        "ui"          -> UiSettingsScreen()
        "performance" -> PerformanceSettingsScreen()
        "audio"       -> AudioSettingsScreen()
        "gamepad"     -> GamepadSettingsScreen()
        "input"       -> InputSettingsScreen()
        "multitouch"  -> MultitouchSettingsScreen()
        "connection"  -> ConnectionSettingsScreen()
        "scene"       -> ScenePresetsScreen()
        "keymapping"  -> KeyMappingScreen()
        "help"        -> HelpSettingsScreen()
    }
}
