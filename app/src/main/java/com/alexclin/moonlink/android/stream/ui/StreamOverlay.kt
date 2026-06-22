package com.alexclin.moonlink.android.stream.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlin.math.roundToInt
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.stream.ui.common.PanelAnimations
import com.alexclin.moonlink.android.stream.ui.keyboard.KeyboardSubPanel
import com.alexclin.moonlink.android.stream.ui.keyboard.VirtualKeyboardBridge
import com.limelight.preferences.PerfOverlayDisplayItemsPreference
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.MoonPhaseUtils
import com.limelight.utils.UiHelper
import com.limelight.binding.video.PerformanceInfo
import android.content.Context
import android.os.SystemClock
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import com.limelight.binding.input.advance_setting.KeyboardUIController
import androidx.activity.compose.BackHandler
import com.alexclin.moonlink.android.stream.ui.panels.KeyMappingSchemeSelector
import com.alexclin.moonlink.android.stream.ui.panels.KeyMappingEditor
import android.view.View

/** 面板展开状态 */
enum class PanelState {
    /** 仅悬浮按钮可见 */
    HIDDEN,
    /** 竖向窄条可见 */
    VERTICAL_BAR,
    /** 竖向窄条 + 操作子面板可见 */
    SUB_PANEL,
    /** 竖向窄条 + 键盘子面板可见 */
    KEYBOARD_PANEL,
}

/** 全屏覆盖页面状态 */
enum class FullScreenPage {
    /** 按键映射方案选择器 */
    KEY_MAPPING_SCHEME_SELECTOR,
    /** 按键映射方案编辑器 */
    KEY_MAPPING_EDITOR,
}

/**
 * 串流界面 overlay 根容器。
 *
 * 管理悬浮按钮、竖向窄条、操作子面板、键盘子面板的显示/隐藏状态。
 *
 * 交互规则：
 * - HIDDEN → 点击悬浮按钮 → VERTICAL_BAR
 * - VERTICAL_BAR → 点击"操作" → SUB_PANEL（toggle）
 * - VERTICAL_BAR → 点击"键盘" → KEYBOARD_PANEL（toggle）
 * - VERTICAL_BAR → 点击"桌面"/"窗口" → HIDDEN（直接动作）
 * - 任意面板展开时点击串流画面区域 → HIDDEN
 */
@Composable
fun StreamOverlay(
    engine: StreamEngine,
    connectionStage: String? = null,
) {
    var panelState by remember { mutableStateOf(PanelState.HIDDEN) }
    var activeEntry by remember { mutableStateOf<String?>(null) }
    var keyboardInitialTab by remember { mutableIntStateOf(0) }
    var fabOffset by remember { mutableStateOf(Offset.Zero) }
    var showFloatingKeyboard by remember { mutableStateOf(false) }
    var detailPage by remember { mutableStateOf(DetailPage.MAIN_LIST) }
    var fullScreenPage by remember { mutableStateOf<FullScreenPage?>(null) }

    // ── 全屏页面 BackHandler ──
    if (fullScreenPage != null) {
        BackHandler {
            fullScreenPage = null
        }
    }

    // 全屏页面状态同步到 engine，供 StreamActivity 等外部组件判断
    LaunchedEffect(fullScreenPage) {
        engine.isFullScreenPageActive = fullScreenPage != null
    }

    // 操作面板自动隐藏：窄面板开启且用户无操作时2秒自动隐藏
    LaunchedEffect(panelState) {
        val mode = engine.prefConfig.toolPanelAutoHideMode
        if (mode == 1 && panelState == PanelState.VERTICAL_BAR) {
            delay(2000)
            if (panelState == PanelState.VERTICAL_BAR && engine.prefConfig.toolPanelAutoHideMode == 1) {
                panelState = PanelState.HIDDEN
                activeEntry = null
            }
        }
    }

    val onToggle = {
        if (panelState == PanelState.HIDDEN) {
            panelState = PanelState.VERTICAL_BAR
        } else {
            panelState = PanelState.HIDDEN
        }
    }

    val onEntryClick: (String) -> Unit = { entry ->
        when (entry) {
            "operations" -> {
                if (panelState == PanelState.SUB_PANEL) {
                    panelState = PanelState.VERTICAL_BAR
                    activeEntry = null
                } else {
                    panelState = PanelState.SUB_PANEL
                    activeEntry = "operations"
                }
            }
            "keyboard" -> {
                if (panelState == PanelState.KEYBOARD_PANEL) {
                    panelState = PanelState.VERTICAL_BAR
                    activeEntry = null
                } else {
                    panelState = PanelState.KEYBOARD_PANEL
                    activeEntry = "keyboard"
                }
            }
            "show_desktop" -> {
                engine.sendWinD()
                panelState = PanelState.HIDDEN
                activeEntry = null
            }
            "show_windows" -> {
                engine.sendWinTab()
                panelState = PanelState.HIDDEN
                activeEntry = null
            }
        }
    }

    // ── 返回键多级处理（300ms防抖） ──
    var lastBackPressTime by rememberSaveable { mutableLongStateOf(0L) }
    val backPressDebounceMs = 300L
    BackHandler(enabled = panelState != PanelState.HIDDEN && !showFloatingKeyboard) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressTime < backPressDebounceMs) return@BackHandler
        lastBackPressTime = now

        when (panelState) {
            PanelState.SUB_PANEL -> {
                if (detailPage != DetailPage.MAIN_LIST) {
                    if (engine.displaySettingsRestartPending && !engine.activity.isFinishing) {
                        engine.changeResolution()
                    }
                    detailPage = DetailPage.MAIN_LIST
                    return@BackHandler
                }
                if (engine.displaySettingsRestartPending && !engine.activity.isFinishing) {
                    engine.changeResolution()
                    return@BackHandler
                }
                panelState = PanelState.VERTICAL_BAR
                activeEntry = null
            }
            PanelState.KEYBOARD_PANEL -> {
                panelState = PanelState.VERTICAL_BAR
                activeEntry = null
            }
            PanelState.VERTICAL_BAR -> {
                panelState = PanelState.HIDDEN
                activeEntry = null
            }
            PanelState.HIDDEN -> { /* 由 Activity 的 onBackPressed 处理退出 */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 连接进度 overlay（匹配旧版 FullscreenProgressOverlay 设计） ──
        AnimatedVisibility(
            visible = connectionStage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ConnectionProgressOverlay(connectionStage = connectionStage)
        }

        // ── 性能监控面板（置于面板层之下，不遮挡悬浮按钮/面板/键盘） ──
        val perfPosition = engine.prefConfig.perfOverlayPosition
        val perfAlign = when (perfPosition) {
            PreferenceConfiguration.PerfOverlayPosition.TOP -> Alignment.TopCenter
            PreferenceConfiguration.PerfOverlayPosition.BOTTOM -> Alignment.BottomCenter
            PreferenceConfiguration.PerfOverlayPosition.TOP_LEFT -> Alignment.TopStart
            PreferenceConfiguration.PerfOverlayPosition.TOP_RIGHT -> Alignment.TopEnd
            PreferenceConfiguration.PerfOverlayPosition.BOTTOM_LEFT -> Alignment.BottomStart
            PreferenceConfiguration.PerfOverlayPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
        if (fullScreenPage == null) {
            PerformanceOverlay(engine = engine, modifier = Modifier.align(perfAlign))
        }

        // ── 面板外点击关闭层 ──
        if (panelState != PanelState.HIDDEN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        // 点击外部关闭时，如有待重启变更加载设置
                        if (panelState == PanelState.SUB_PANEL && engine.displaySettingsRestartPending && !engine.activity.isFinishing) {
                            engine.changeResolution()
                            return@clickable
                        }
                        detailPage = DetailPage.MAIN_LIST
                        panelState = PanelState.HIDDEN
                        activeEntry = null
                    },
            )
        }

        // ── 编辑器打开时不显示悬浮按钮/面板/键盘（避免视觉干扰） ──
        val showFloatingUI = fullScreenPage == null

        // ── 悬浮按钮（编辑器打开时隐藏） ──
        if (showFloatingUI) {
            AnimatedVisibility(
                visible = panelState == PanelState.HIDDEN && !showFloatingKeyboard,
                enter = PanelAnimations.fabEnter,
                exit = PanelAnimations.fabExit,
            ) {
                FloatingActionButton(
                    visible = true,
                    initialOffsetX = fabOffset.x,
                    initialOffsetY = fabOffset.y,
                    onToggle = onToggle,
                    onPositionChanged = { x, y -> fabOffset = Offset(x, y) },
                    opacity = engine.fabOpacity,
                )
            }
        }

        // ── 浮动虚拟键盘覆盖层（编辑器打开时也会显示，但编辑器模式下很少触发） ──
        if (showFloatingKeyboard) {
            BackHandler {
                showFloatingKeyboard = false
            }
            val bridge = remember { VirtualKeyboardBridge(engine) }
            val keyboardContainer = remember { mutableStateOf<FrameLayout?>(null) }

            LaunchedEffect(showFloatingKeyboard) {
                if (showFloatingKeyboard) {
                    while (true) {
                        delay(500)
                        val container = keyboardContainer.value
                        if (container != null && container.visibility != View.VISIBLE) {
                            showFloatingKeyboard = false
                            break
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        val container = FrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                        }
                        keyboardContainer.value = container
                        val kUI = KeyboardUIController(container, bridge, ctx)
                        kUI.show()
                        container
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── 窄条面板（横屏→右侧竖向，竖屏→底部横向；编辑器打开时隐藏） ──
        if (showFloatingUI) {
            val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            AnimatedVisibility(
                visible = panelState != PanelState.HIDDEN && panelState != PanelState.KEYBOARD_PANEL,
                enter = PanelAnimations.verticalBarEnter,
                exit = PanelAnimations.verticalBarExit,
                modifier = Modifier.align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter),
            ) {
                if (isLandscape) {
                    VerticalBar(
                        activeEntry = activeEntry,
                        onEntryClick = onEntryClick,
                        subPanelVisible = panelState == PanelState.SUB_PANEL,
                    )
                } else {
                    HorizontalBar(
                        activeEntry = activeEntry,
                        onEntryClick = onEntryClick,
                    )
                }
            }
        }

        // ── 操作子面板（编辑器打开时隐藏） ──
        if (showFloatingUI) {
            val density = LocalDensity.current
            val barWidthPx = with(density) { 60.dp.toPx().roundToInt() }
            val subPanelEnter = remember(barWidthPx) {
                slideInHorizontally(
                    initialOffsetX = { _ -> barWidthPx },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200))
            }
            val subPanelExit = remember(barWidthPx) {
                slideOutHorizontally(
                    targetOffsetX = { _ -> barWidthPx },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150))
            }
            AnimatedVisibility(
                visible = panelState == PanelState.SUB_PANEL,
                enter = subPanelEnter,
                exit = subPanelExit,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                SubPanelContainer(
                    engine = engine,
                    detailPage = detailPage,
                    onDetailPageChange = { detailPage = it },
                    onOpenKeyboardShortcuts = {
                        panelState = PanelState.KEYBOARD_PANEL
                        activeEntry = "keyboard"
                        keyboardInitialTab = 1
                    },
                    onOpenFullScreenPage = { fullScreenPage = it },
                    modifier = Modifier.offset(x = (-60).dp),
                )
            }
        }

        // ── 全屏覆盖页面（方案选择/编辑器） ──
        // 编辑器模式下：不显示黑色背景，让串流画面透出；非编辑器模式下保持黑色背景
        fullScreenPage?.let { page ->
            when (page) {
                FullScreenPage.KEY_MAPPING_SCHEME_SELECTOR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { /* 不关闭，由内部返回按钮或 BackHandler 处理 */ }
                            ),
                    ) {
                        KeyMappingSchemeSelector(
                            engine = engine,
                            onClose = { fullScreenPage = null },
                            onOpenEditor = { fullScreenPage = FullScreenPage.KEY_MAPPING_EDITOR },
                        )
                    }
                }
                FullScreenPage.KEY_MAPPING_EDITOR -> {
                    // 编辑器以串流画面为背景 — 无黑色遮罩，只由 KeyMappingEditor 自身半透明背景控制
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { /* 由内部保存/取消按钮或 BackHandler 处理 */ }
                            ),
                    ) {
                        KeyMappingEditor(
                            engine = engine,
                            onClose = { fullScreenPage = null },
                        )
                    }
                }
            }
        }

        // ── 键盘面板（横向填满全屏底部 tabbar 模式；编辑器打开时隐藏） ──
        if (showFloatingUI) {
            val keyboardEnter = remember {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200))
            }
            val keyboardExit = remember {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150))
            }
            AnimatedVisibility(
                visible = panelState == PanelState.KEYBOARD_PANEL,
                enter = keyboardEnter,
                exit = keyboardExit,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) {
                    KeyboardSubPanel(
                        engine = engine,
                        initialTab = keyboardInitialTab,
                        onClose = {
                            panelState = PanelState.VERTICAL_BAR
                            activeEntry = null
                        },
                        onCloseToHidden = {
                            panelState = PanelState.HIDDEN
                            activeEntry = null
                        },
                        onShowFloatingKeyboard = {
                            showFloatingKeyboard = true
                        },
                    )
                }
            }
        }
    }
}

/** 连接进度 overlay — 进度条垂直居中，Tip 独立层不参与居中计算 */
@Composable
private fun ConnectionProgressOverlay(connectionStage: String?) {
    val context = LocalContext.current
    val tipResIds = remember {
        intArrayOf(
            com.alexclin.moonlink.android.R.string.tip_esc_exit,
            com.alexclin.moonlink.android.R.string.tip_double_tap_mouse,
            com.alexclin.moonlink.android.R.string.tip_long_press_controller,
            com.alexclin.moonlink.android.R.string.tip_volume_keys,
            com.alexclin.moonlink.android.R.string.tip_wallpaper_change,
            com.alexclin.moonlink.android.R.string.tip_5ghz_wifi,
            com.alexclin.moonlink.android.R.string.tip_close_apps,
            com.alexclin.moonlink.android.R.string.tip_home_saves,
            com.alexclin.moonlink.android.R.string.tip_hdr_colors,
            com.alexclin.moonlink.android.R.string.tip_touch_modes,
            com.alexclin.moonlink.android.R.string.tip_custom_keys,
            com.alexclin.moonlink.android.R.string.tip_performance_overlay,
            com.alexclin.moonlink.android.R.string.tip_audio_config,
            com.alexclin.moonlink.android.R.string.tip_external_display,
            com.alexclin.moonlink.android.R.string.tip_virtual_display,
            com.alexclin.moonlink.android.R.string.tip_dynamic_bitrate,
            com.alexclin.moonlink.android.R.string.tip_cards_show,
        )
    }
    val random = remember { Random }
    // connectionStage 变化时换一条新 tip
    val currentTip by remember(connectionStage) {
        mutableStateOf(context.getString(tipResIds[random.nextInt(tipResIds.size)]))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000)),
    ) {
        // ── 核心锚点组（状态文字 + 进度条 + 阶段文字）──
        // 垂直居中，Tip 独立在外层不参与居中计算，避免单行/多行切换导致进度条浮动
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 状态文字（"建立连接中"）
            Text(
                text = context.getString(com.alexclin.moonlink.android.R.string.conn_establishing_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // 进度条（indeterminate 细线效果）
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0x33FFFFFF),
            )

            // 进度文字（连接阶段）
            Text(
                text = connectionStage ?: context.getString(com.alexclin.moonlink.android.R.string.conn_establishing_msg),
                color = Color(0xFFCCCCCC),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }

        // ── Tip 提示（独立层，固定在屏幕下方，不参加居中计算）──
        Text(
            text = currentTip,
            color = Color(0xFFBBBBBB),
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = 48.dp),
        )
    }
}

/** 性能监控面板 — 完整实现，与旧版 PerformanceOverlayManager 功能对齐 */
@Composable
private fun PerformanceOverlay(engine: StreamEngine, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var perfInfo by remember { mutableStateOf(engine.latestPerfInfo) }

    // 使用回调驱动更新，替代轮询
    LaunchedEffect(Unit) {
        engine.onPerfInfoUpdate = { perfInfo = it }
    }

    val visible = engine.perfOverlayEnabled
            && perfInfo != null
            && perfInfo!!.renderedFps > 0f

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        val info = perfInfo!!
        val isHorizontal = engine.prefConfig.perfOverlayOrientation == PreferenceConfiguration.PerfOverlayOrientation.HORIZONTAL
        val bgOpacity = (engine.prefConfig.perfOverlayBgOpacity.coerceIn(0, 100)) / 100f
        val bgArgb = (bgOpacity * 255).toInt().coerceIn(0, 255) shl 24 or 0x161616
        val bgColor = Color(bgArgb)
        val isLocked = engine.prefConfig.perfOverlayLocked

        // 月相
        val moonIcon = remember { MoonPhaseUtils.getMoonPhaseIcon(MoonPhaseUtils.getCurrentMoonPhase()) }
        // 带宽
        val bandwidth = engine.bandwidthInfo

        // 检测每个项目是否启用
        val items = remember {
            listOf<Pair<PerfItem, Boolean>>(
                PerfItem.RESOLUTION to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "resolution"),
                PerfItem.DECODER to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "decoder"),
                PerfItem.FPS to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "render_fps"),
                PerfItem.PACKET_LOSS to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "packet_loss"),
                PerfItem.NETWORK to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "network_latency"),
                PerfItem.DECODE to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "decode_latency"),
                PerfItem.HOST to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "host_latency"),
                PerfItem.BATTERY to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "battery"),
                PerfItem.ONE_LOW to PerfOverlayDisplayItemsPreference.isItemEnabled(context, "one_percent_low"),
            )
        }

        // 拖拽状态
        var dragOffset by remember { mutableStateOf(Offset.Zero) }

        // 点击详情对话框
        var detailTitle by remember { mutableStateOf<String?>(null) }
        var detailMessage by remember { mutableStateOf<String?>(null) }

        if (detailTitle != null) {
            AlertDialog(
                onDismissRequest = { detailTitle = null },
                title = { Text(detailTitle!!) },
                text = { Text(detailMessage ?: "") },
                confirmButton = { TextButton(onClick = { detailTitle = null }) { Text("确定") } },
            )
        }

        val textSize = 12.sp
        val textColor = Color.White

        @Composable
        fun renderItem(item: PerfItem) {
            val (valueText, itemColor) = buildItemText(item, info, bandwidth, moonIcon, context)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        detailTitle = item.name
                        detailMessage = buildItemDetail(item, info, bandwidth, context)
                    }
                    .padding(vertical = 1.dp),
            ) {
                Text(item.iconEmoji ?: "", fontSize = textSize)
                Spacer(Modifier.width(4.dp))
                Text(
                    valueText,
                    color = itemColor ?: textColor,
                    fontSize = textSize,
                    fontWeight = if (item == PerfItem.DECODER) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val panelContent = @Composable {
            val container: @Composable (content: @Composable () -> Unit) -> Unit =
                if (isHorizontal) { content -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
                else { content -> Column { content() } }
            container {
                for ((item, enabled) in items) {
                    if (enabled) renderItem(item)
                }
            }
        }

        // 拖拽手势（仅非锁定态可拖拽）
        val dragModifier = if (!isLocked)
            Modifier.pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffset = Offset(dragOffset.x + dragAmount.x, dragOffset.y + dragAmount.y)
                }
            }
        else Modifier

        val contentBox = @Composable {
            Box(
                modifier = dragModifier
                    .background(bgColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                panelContent()
            }
        }

        if (isLocked) {
            contentBox()
        } else {
            Box(modifier = Modifier.offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }) {
                contentBox()
            }
        }
    }
}

private enum class PerfItem(
    val iconEmoji: String? = null,
    val color: Int? = null,
) {
    RESOLUTION(iconEmoji = "🎬", color = 0xFFBB86FC.toInt()),
    DECODER(iconEmoji = "⚙️", color = 0xFF03DAC6.toInt()),
    FPS(iconEmoji = "🖥️", color = 0xFF0DDAF4.toInt()),
    PACKET_LOSS(iconEmoji = "📡"),
    NETWORK(iconEmoji = "🌐", color = 0xFFBCEDD3.toInt()),
    DECODE(iconEmoji = "⏱️", color = 0xFFD597E3.toInt()),
    HOST(iconEmoji = "🖥️", color = 0xFF009688.toInt()),
    BATTERY(iconEmoji = "🔋"),
    ONE_LOW(iconEmoji = "📉", color = 0xFFFF7043.toInt()),
}

private fun buildItemText(
    item: PerfItem, info: PerformanceInfo, bandwidth: String, moonIcon: String, context: Context
): Pair<String, Color?> = when (item) {
    PerfItem.RESOLUTION -> "${info.initialWidth}x${info.initialHeight}@${"%.0f".format(info.totalFps)} $moonIcon" to null
    PerfItem.DECODER -> {
        val shortName = info.decoder?.lowercase()?.let { name ->
            when {
                name.contains("av1") -> "AV1"
                name.contains("avc") || name.contains("h264") -> "AVC"
                name.contains("hevc") || name.contains("h265") -> "HEVC"
                name.contains("vp9") -> "VP9"
                name.contains("vp8") -> "VP8"
                else -> name.substringAfterLast('.').uppercase()
            }
        } ?: "N/A"
        (if (info.isHdrActive) "$shortName HDR" else shortName) to null
    }
    PerfItem.FPS -> "Rx ${"%.0f".format(info.receivedFps)} / Rd ${"%.0f".format(info.renderedFps)} FPS" to null
    PerfItem.PACKET_LOSS -> {
        val text = "${"%.2f".format(info.lostFrameRate)}%"
        val color = if (info.lostFrameRate < 5.0f) Color(0xFF7D9D7D) else Color(0xFFB57D7D)
        text to color
    }
    PerfItem.NETWORK -> {
        val rtt = info.rttInfo.toInt()
        val jitter = (info.rttInfo shr 32).toInt()
        "$bandwidth\u00A0\u00A0\u00A0${jitter}±${rtt}ms" to null
    }
    PerfItem.DECODE -> {
        val isHot = info.decodeTimeMs >= 15
        val emoji = if (isHot) " 🥵" else ""
        "${"%.2f".format(info.decodeTimeMs)}ms$emoji" to null
    }
    PerfItem.HOST -> {
        if (info.framesWithHostProcessingLatency > 0)
            "${"%.1f".format(info.aveHostProcessingLatency)}ms" to null
        else
            "Ver.V+ 🧋" to null
    }
    PerfItem.BATTERY -> {
        val level = UiHelper.getBatteryLevel(context)
        val color = when {
            level > 50 -> Color(0xFF90EE90)
            level > 20 -> Color(0xFFFFA500)
            else -> Color(0xFFFF6B6B)
        }
        "${level}%" to color
    }
    PerfItem.ONE_LOW -> {
        if (info.onePercentLowFps <= 0) {
            "1%Low — FPS" to Color(0xFFFF7043)
        } else {
            val ratio = info.onePercentLowFps / info.renderedFps
            val color = when {
                ratio >= 0.9f -> Color(0xFF90EE90)
                ratio >= 0.6f -> Color(0xFFFFD700)
                else -> Color(0xFFFF6B6B)
            }
            "1%Low ${"%.1f".format(info.onePercentLowFps)} FPS" to color
        }
    }
}

private fun buildItemDetail(
    item: PerfItem, info: PerformanceInfo, bandwidth: String, context: Context
): String = when (item) {
    PerfItem.RESOLUTION -> "Video stream: ${info.initialWidth}x${info.initialHeight} ${"%.2f".format(info.totalFps)} FPS"
    PerfItem.DECODER -> "Decoder: ${info.decoder ?: "N/A"}"
    PerfItem.FPS -> {
        "Incoming frame rate: ${"%.2f".format(info.receivedFps)} FPS\nRendering frame rate: ${"%.2f".format(info.renderedFps)} FPS"
    }
    PerfItem.PACKET_LOSS -> "Packet loss: ${"%.2f".format(info.lostFrameRate)}%"
    PerfItem.NETWORK -> {
        val rtt = info.rttInfo.toInt()
        val jitter = (info.rttInfo shr 32).toInt()
        "Bandwidth: $bandwidth\nRTT: ${rtt}ms\nJitter: ${jitter}ms"
    }
    PerfItem.DECODE -> "Decode latency: ${"%.2f".format(info.decodeTimeMs)} ms"
    PerfItem.HOST -> {
        if (info.framesWithHostProcessingLatency > 0) {
            "Min: ${"%.1f".format(info.minHostProcessingLatency)} ms\n" +
            "Average: ${"%.1f".format(info.aveHostProcessingLatency)} ms\n" +
            "Max: ${"%.1f".format(info.maxHostProcessingLatency)} ms"
        } else "Host latency: N/A (requires V8+ host)"
    }
    PerfItem.BATTERY -> {
        val level = UiHelper.getBatteryLevel(context)
        val charging = UiHelper.isCharging(context)
        "Battery: $level%${if (charging) " (charging)" else ""}"
    }
    PerfItem.ONE_LOW -> {
        if (info.onePercentLowFps <= 0) "1% Low FPS: N/A (insufficient data)"
        else "1% Low FPS: ${"%.2f".format(info.onePercentLowFps)} FPS"
    }
}
