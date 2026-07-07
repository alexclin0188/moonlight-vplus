package com.alexclin.moonlink.android.settings

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.navigation.MoonLinkRoute
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.home.ComputerManagerService
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

/** Resolve category title from string resources. Call from within @Composable. */
@Composable
private fun rememberSettingsCategories(context: Context): List<SettingsCategory> = remember {
    listOf(
        SettingsCategory("ui",          context.getString(R.string.category_ui_settings),             Icons.Default.Palette,         "settings_ui"),
        SettingsCategory("performance", context.getString(R.string.category_performance_analytics),    Icons.Default.Speed,           "settings_performance"),
        SettingsCategory("gamepad",     context.getString(R.string.category_gamepad_settings),        Icons.Default.Gamepad,         "settings_gamepad"),
        SettingsCategory("input",       context.getString(R.string.category_input_settings),          Icons.Default.Keyboard,        "settings_input"),
        SettingsCategory("keymapping",  context.getString(R.string.category_key_mapping_features),          Icons.Default.Tune,            "settings_keymapping"),
        SettingsCategory("widget",      context.getString(R.string.category_desktop_widget),          Icons.Default.Widgets,         "_widget"),
        SettingsCategory("connection",  context.getString(R.string.category_connection_settings),     Icons.Default.Lan,             "settings_connection"),
        SettingsCategory("help",        context.getString(R.string.help),                    Icons.Default.HelpOutline,     "settings_help"),
    )
}

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    managerBinder: ComputerManagerService.ComputerManagerBinder? = null,
    computers: List<ComputerDetails>? = null,
) {
    val context = LocalContext.current
    val categories = rememberSettingsCategories(context)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    if (isLandscape) {
        LandscapeSettingsContent(
            categories = categories,
            onNavigate = onNavigate,
            managerBinder = managerBinder,
            computers = computers,
        )
    } else {
        PortraitSettingsList(categories = categories, onNavigate = onNavigate)
    }
}

// ── Landscape: animated two-pane ────────────────────────────
//   - 无子页面时左栏占满内容区
//   - 展开子页面时左栏收缩到 200.dp，右栏从右侧滑入
//   - 再次点击已展开项时左栏平滑展开将右栏挤出屏幕

@Composable
private fun LandscapeSettingsContent(
    categories: List<SettingsCategory>,
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
                items(categories.size) { index ->
                    val cat = categories[index]
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
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
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

@Composable
private fun PortraitSettingsList(
    categories: List<SettingsCategory>,
    onNavigate: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        items(categories.size) { index ->
            val cat = categories[index]
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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        "gamepad"     -> GamepadSettingsScreen()
        "input"       -> InputSettingsScreen()
        "keymapping"  -> KeyMappingScreen()
        "connection"  -> ConnectionSettingsScreen()
        "help"        -> HelpSettingsScreen()
    }
}
