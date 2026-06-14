package com.alexclin.moonlink.stream.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.alexclin.moonlink.stream.engine.StreamEngine
import com.alexclin.moonlink.stream.ui.common.PanelAnimations

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
    var panelState by remember { mutableStateOf(PanelState.VERTICAL_BAR) }
    var activeEntry by remember { mutableStateOf<String?>(null) }
    var fabOffset by remember { mutableStateOf(Offset.Zero) }
    var autoHideDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(2000)
        if (!autoHideDone) {
            panelState = PanelState.HIDDEN
            activeEntry = null
            autoHideDone = true
        }
    }

    val onToggle = {
        if (panelState == PanelState.HIDDEN) {
            panelState = PanelState.VERTICAL_BAR
        } else {
            panelState = PanelState.HIDDEN
            autoHideDone = true
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
                autoHideDone = true
            }
            "keyboard" -> {
                if (panelState == PanelState.KEYBOARD_PANEL) {
                    panelState = PanelState.VERTICAL_BAR
                    activeEntry = null
                } else {
                    panelState = PanelState.KEYBOARD_PANEL
                    activeEntry = "keyboard"
                }
                autoHideDone = true
            }
            "show_desktop" -> {
                // TODO: 阶段 5 实现 KeySender.sendWinD(engine.conn)
                panelState = PanelState.HIDDEN
                activeEntry = null
                autoHideDone = true
            }
            "show_windows" -> {
                // TODO: 阶段 5 实现 KeySender.sendWinTab(engine.conn)
                panelState = PanelState.HIDDEN
                activeEntry = null
                autoHideDone = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 连接进度 overlay（阶段更新 + 首帧后自动消失） ──
        AnimatedVisibility(
            visible = connectionStage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xBB000000)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "启动中 ${connectionStage ?: ""}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(32.dp),
                )
            }
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
                        panelState = PanelState.HIDDEN
                        activeEntry = null
                        autoHideDone = true
                    },
            )
        }

        // ── 悬浮按钮 ──
        AnimatedVisibility(
            visible = panelState == PanelState.HIDDEN,
            enter = PanelAnimations.fabEnter,
            exit = PanelAnimations.fabExit,
        ) {
            FloatingActionButton(
                visible = true,
                onToggle = onToggle,
                onPositionChanged = { x, y -> fabOffset = Offset(x, y) },
            )
        }

        // ── 窄条面板（横屏→右侧竖向，竖屏→底部横向） ──
        val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        AnimatedVisibility(
            visible = panelState != PanelState.HIDDEN,
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

        // ── 操作子面板 ──
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
            SubPanelContainer(engine = engine)
        }

        // ── 键盘子面板（阶段 5 实现） ──
        // if (panelState == PanelState.KEYBOARD_PANEL) { KeyboardSubPanel(...) }
    }
}
