package com.alexclin.moonlink.stream.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.common.MoonLinkQuickActions
import com.alexclin.moonlink.stream.ui.common.getActionIcon
import com.alexclin.moonlink.stream.ui.common.getActionLabel
import com.alexclin.moonlink.stream.ui.panels.QuickActionRow
import com.limelight.QuickActionRegistry

enum class DetailPage {
    MAIN_LIST,
    DISPLAY,
    HOST_SETTINGS,
    SHORTCUT_ACTIONS,
    PERIPHERALS,
    KEY_MAPPING,
    QUICK_ACTION_EDITOR,
}

@Composable
fun SubPanelContainer(engine: StreamEngine, modifier: Modifier = Modifier) {
    var detailPage by remember { mutableStateOf(DetailPage.MAIN_LIST) }
    val context = LocalContext.current
    var configIds: List<String> by remember { mutableStateOf(QuickActionRegistry.loadConfig(context)) }
    val configuration = LocalConfiguration.current
    val panelWidth = (configuration.screenWidthDp.dp * 0.45f).coerceIn(280.dp, 400.dp)

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 60.dp)
            .width(panelWidth),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        AnimatedContent(
            targetState = detailPage,
            transitionSpec = {
                if (targetState == DetailPage.MAIN_LIST) {
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            initialOffsetX = { -it / 3 },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200)),
                        initialContentExit = slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200)),
                    )
                } else {
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200)),
                        initialContentExit = slideOutHorizontally(
                            targetOffsetX = { -it / 3 },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200)),
                    )
                }
            }
        ) { page ->
            when (page) {
                DetailPage.MAIN_LIST -> {
                    MainListView(
                        engine = engine,
                        configIds = configIds,
                        onEditActionClick = { detailPage = DetailPage.QUICK_ACTION_EDITOR },
                        onNavigate = { detailPage = it },
                    )
                }
                DetailPage.DISPLAY -> {
                    DisplaySection(
                        onBack = { detailPage = DetailPage.MAIN_LIST },
                    )
                }
                DetailPage.HOST_SETTINGS -> {
                    HostSettingsSection(
                        onBack = { detailPage = DetailPage.MAIN_LIST },
                    )
                }
                DetailPage.SHORTCUT_ACTIONS -> {
                    ShortcutActionsSection(
                        onBack = { detailPage = DetailPage.MAIN_LIST },
                    )
                }
                DetailPage.PERIPHERALS -> {
                    PeripheralsDetail(
                        onBack = { detailPage = DetailPage.MAIN_LIST },
                    )
                }
                DetailPage.KEY_MAPPING -> {}
                DetailPage.QUICK_ACTION_EDITOR -> {
                    QuickActionEditorPage(
                        configIds = configIds,
                        onSave = { newIds ->
                            QuickActionRegistry.saveConfig(context, newIds)
                            configIds = newIds
                            detailPage = DetailPage.MAIN_LIST
                        },
                        onBack = { detailPage = DetailPage.MAIN_LIST },
                    )
                }
            }
        }
    }
}

// ── Main List ──

@Composable
private fun MainListView(
    engine: StreamEngine,
    configIds: List<String>,
    onEditActionClick: () -> Unit,
    onNavigate: (DetailPage) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        item { QuickActionRow(engine = engine, configIds = configIds, onEditClick = onEditActionClick) }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        if (engine.prefConfig.enablePip) {
            item { PanZoomSection() }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
        }

        item { KeyMappingSection() }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item { TouchModeSection() }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item {
            SectionEntryRow(
                icon = Icons.Default.Tv,
                label = "显示",
                onClick = { onNavigate(DetailPage.DISPLAY) },
            )
        }

        item {
            SectionEntryRow(
                icon = Icons.Default.Computer,
                label = "主机设置",
                onClick = { onNavigate(DetailPage.HOST_SETTINGS) },
            )
        }

        item {
            SectionEntryRow(
                icon = Icons.Default.Bolt,
                label = "快捷操作",
                onClick = { onNavigate(DetailPage.SHORTCUT_ACTIONS) },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item {
            PeripheralsSection(
                onNavigate = { onNavigate(DetailPage.PERIPHERALS) },
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item { GyroSection() }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        item { MoreSection() }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ── Components ──

@Composable
private fun SectionEntryRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Inline Section Composables ──

@Composable
private fun PanZoomSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PanTool,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "平移缩放",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KeyMappingSection() {
    var enabled by remember { mutableStateOf(true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.VideogameAsset,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "启用按键映射",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
}

@Composable
private fun TouchModeSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "触控模式",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PeripheralsSection(
    onNavigate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigate)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Mouse,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "外设",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GyroSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Sensors,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "体感助手",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MoreSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "更多",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.MoreHoriz,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Detail Pages ──

@Composable
private fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider()
        content()
    }
}

@Composable
private fun DisplaySection(onBack: () -> Unit) {
    DetailScaffold(title = "显示", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    "显示设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HostSettingsSection(onBack: () -> Unit) {
    DetailScaffold(title = "主机设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    "主机设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ShortcutActionsSection(onBack: () -> Unit) {
    DetailScaffold(title = "快捷操作", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    "快捷操作设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PeripheralsDetail(onBack: () -> Unit) {
    DetailScaffold(title = "外设", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    "外设设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

// ── Quick Action Editor ──

private fun getAllEditorActionIds(): List<String> {
    val builtinIds = QuickActionRegistry.DEFAULT_IDS.toList()
    val moonlinkIds = listOf(
        MoonLinkQuickActions.TOGGLE_PIP,
        MoonLinkQuickActions.TOGGLE_ADAPTIVE_BITRATE,
        MoonLinkQuickActions.TOGGLE_CONTROL_ONLY,
        MoonLinkQuickActions.TOGGLE_GYRO,
    )
    return builtinIds + moonlinkIds
}

@Composable
private fun QuickActionEditorPage(
    configIds: List<String>,
    onSave: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val allAvailableIds = remember { getAllEditorActionIds() }
    val items = remember(configIds) {
        val result = configIds.toMutableList()
        for (id in allAvailableIds) {
            if (id !in result) result.add(id)
        }
        result.toList()
    }
    val reorderableItems = remember { mutableStateListOf<String>().also { it.addAll(items) } }
    val activeCount = 3.coerceAtMost(reorderableItems.size)
    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 72.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "快捷操作调整",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSave(reorderableItems.toList().take(activeCount)) }) {
                Text("保存")
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(reorderableItems, key = { it }) { id ->
                val index = reorderableItems.indexOf(id)
                val isActive = index < activeCount
                val isDragging = draggedItemKey == id

                Surface(
                    modifier = Modifier
                        .animateItem()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            scaleX = if (isDragging) 1.03f else 1f
                            scaleY = if (isDragging) 1.03f else 1f
                            shadowElevation = if (isDragging) 8f else 0f
                        }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedItemKey = id
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val currentIdx = reorderableItems.indexOf(id)
                                    val threshold = itemHeightPx * 0.5f
                                    if (dragOffset > threshold && currentIdx < reorderableItems.size - 1) {
                                        val tmp = reorderableItems[currentIdx]
                                        reorderableItems[currentIdx] = reorderableItems[currentIdx + 1]
                                        reorderableItems[currentIdx + 1] = tmp
                                        dragOffset -= itemHeightPx
                                    } else if (dragOffset < -threshold && currentIdx > 0) {
                                        val tmp = reorderableItems[currentIdx]
                                        reorderableItems[currentIdx] = reorderableItems[currentIdx - 1]
                                        reorderableItems[currentIdx - 1] = tmp
                                        dragOffset += itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggedItemKey = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggedItemKey = null
                                    dragOffset = 0f
                                },
                            )
                        },
                    shape = RoundedCornerShape(0.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 18.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(12.dp))
                        val icon = getActionIcon(id)
                        if (icon != null) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            getActionLabel(id),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "拖动排序",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 32.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
