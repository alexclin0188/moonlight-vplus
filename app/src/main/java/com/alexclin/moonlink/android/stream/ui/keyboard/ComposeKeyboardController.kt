package com.alexclin.moonlink.android.stream.ui.keyboard

import android.content.Context
import com.alexclin.moonlink.android.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ────────────────────────────────────────────────────────────────────────────
// 修饰键状态
// ────────────────────────────────────────────────────────────────────────────

private const val MOD_NEUTRAL = 0
private const val MOD_SINGLE = 1
private const val MOD_LOCKED = 2

private const val DOUBLE_TAP_TIMEOUT_MS = 250L
private const val HOLD_THRESHOLD_MS = 200L

// ────────────────────────────────────────────────────────────────────────────
// 主 Composable
// ────────────────────────────────────────────────────────────────────────────

/**
 * Compose 版本的浮动虚拟键盘控制器。
 *
 * 当作为嵌入式内容渲染在 [KeyboardSubPanel] 中时，
 * 父容器已通过 [KeyboardSubPanel] 的 IME 高度监听提供正确高度，
 * 本组件自动填充父容器空间。
 *
 * @param bridge 按键事件桥接器
 * @param onHide 用户点击隐藏按钮时的回调
 * @param onSwitchToTab 切换到 KeyboardSubPanel 其他标签的回调（0=系统输入法, 1=快捷键）
 */
@Composable
fun ComposeKeyboardController(
    bridge: VirtualKeyboardBridge,
    onHide: () -> Unit,
    maxHeightDp: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    onSwitchToTab: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }

    // ── 键盘模式状态 ──
    // 每次进入虚拟键盘都从默认全键盘开始（不持久化 Mini 模式状态）
    var isMiniMode by remember { mutableStateOf(false) }
    var isNumpadMode by remember { mutableStateOf(false) }
    var miniPanel by remember { mutableStateOf("alpha") } // alpha, num, pc

    // ── 修饰键状态 ──
    val modifierStates = remember {
        mutableStateMapOf(
            KeyboardLayouts.KEY_LCTRL to MOD_NEUTRAL,
            KeyboardLayouts.KEY_RCTRL to MOD_NEUTRAL,
            KeyboardLayouts.KEY_LSHIFT to MOD_NEUTRAL,
            KeyboardLayouts.KEY_RSHIFT to MOD_NEUTRAL,
            KeyboardLayouts.KEY_LALT to MOD_NEUTRAL,
            KeyboardLayouts.KEY_RALT to MOD_NEUTRAL,
            KeyboardLayouts.KEY_LWIN to MOD_NEUTRAL,
        )
    }
    val physicallyHeldModifiers = remember { mutableSetOf<Int>() }

    // ── 外观状态 ──
    // 每次进入虚拟键盘默认 80% 不透明度（slider 值 60 → 不透明度 = 60/100+0.2 = 0.8），不持久化
    var opacityProgress by remember { mutableFloatStateOf(60f) }
    var keyboardHeightPx by remember { mutableIntStateOf(-1) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showResizeHandle by remember { mutableStateOf(false) }

    // ── 按下弹窗 ──
    var popupKey by remember { mutableStateOf<String?>(null) }
    var popupOffsetX by remember { mutableFloatStateOf(0f) }
    var popupOffsetY by remember { mutableFloatStateOf(0f) }
    var popupVisible by remember { mutableStateOf(false) }

    // ── 触觉反馈 ──
    val triggerHaptic: (String) -> Unit = remember(bridge) {
        { type ->
            val duration = if (type == "toggle" || type == "heavy") 20 else 10
            bridge.rumbleSingleVibrator(1000.toShort(), 1000.toShort(), duration)
        }
    }

    // ── 按键事件处理 ──
    val onKeyAction: (Int, Boolean, Boolean) -> Unit = remember(modifierStates, physicallyHeldModifiers) {
        { keyCode, isPress, isDoubleTap ->
            if (isDoubleTap && KeyboardLayouts.MODIFIER_KEYS.contains(keyCode)) {
                // Double tap on modifier: reset to neutral
                triggerHaptic("heavy")
                modifierStates[keyCode] = MOD_NEUTRAL
                physicallyHeldModifiers.remove(keyCode)
                bridge.sendKeyEvent(false, keyCode.toShort())
                return@remember
            }

            if (KeyboardLayouts.MODIFIER_KEYS.contains(keyCode)) {
                if (isPress) {
                    triggerHaptic("toggle")
                    val currentState = modifierStates[keyCode] ?: MOD_NEUTRAL
                    if (currentState == MOD_NEUTRAL) {
                        modifierStates[keyCode] = MOD_SINGLE
                        physicallyHeldModifiers.add(keyCode)
                        bridge.sendKeyEvent(true, keyCode.toShort())
                    } else {
                        val newState = (currentState + 1) % 3
                        modifierStates[keyCode] = newState
                        when (newState) {
                            MOD_NEUTRAL -> bridge.sendKeyEvent(false, keyCode.toShort())
                            MOD_SINGLE -> bridge.sendKeyEvent(true, keyCode.toShort())
                            MOD_LOCKED -> {} // locked, no send
                        }
                    }
                }
            } else {
                if (isPress) {
                    triggerHaptic("normal")
                    bridge.sendKeyEvent(true, keyCode.toShort())
                }
            }
        }
    }

    val onKeyRelease: (Int, Boolean) -> Unit = remember(modifierStates, physicallyHeldModifiers) {
        { keyCode, wasHeld ->
            if (KeyboardLayouts.MODIFIER_KEYS.contains(keyCode)) {
                physicallyHeldModifiers.remove(keyCode)
                if (wasHeld) {
                    // Hold release: if SINGLE, demote to NEUTRAL
                    val currentState = modifierStates[keyCode] ?: MOD_NEUTRAL
                    if (currentState == MOD_SINGLE) {
                        modifierStates[keyCode] = MOD_NEUTRAL
                        bridge.sendKeyEvent(false, keyCode.toShort())
                    }
                }
            } else {
                bridge.sendKeyEvent(false, keyCode.toShort())
                // Reset single modifiers when non-modifier key is released
                resetSingleModifiers(modifierStates, physicallyHeldModifiers, bridge)
                popupVisible = false
            }
        }
    }

    // ── 持久化 ──
    fun savePosition() {
        prefs.edit()
            .putFloat("keyboard_x", offsetX)
            .putFloat("keyboard_y", offsetY)
            .apply()
    }

    fun saveHeight() {
        if (keyboardHeightPx > 0) {
            prefs.edit().putInt("keyboard_height", keyboardHeightPx).apply()
        }
    }

    // ── 修饰键 UI 颜色 ──
    fun modifierColor(keyCode: Int): Color {
        val state = modifierStates[keyCode] ?: MOD_NEUTRAL
        return when (state) {
            MOD_SINGLE -> Color(0xFF007AFF)    // iOS blue (#007AFF, matches old drawable)
            MOD_LOCKED -> Color(0xFF5722)       // Orange-red (#FF5722, matches old drawable)
            else -> Color.Transparent
        }
    }

    // ── 返回全屏模式 ──
    val exitMiniMode = {
        isMiniMode = false
        offsetX = 0f
        offsetY = 0f
        showResizeHandle = false
    }

    // ── 进入 Mini 模式 ──
    val enterMiniMode = {
        isMiniMode = true
        miniPanel = "alpha"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // Mini mode: full-screen height for dragging
                // Main/Num mode: constrained to IME keyboard height (maxHeightDp)
                if (isMiniMode) Modifier.fillMaxHeight()
                else if (maxHeightDp != androidx.compose.ui.unit.Dp.Unspecified) Modifier.height(maxHeightDp)
                else Modifier.fillMaxHeight()
            )
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // ── Keyboard content ──
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .alpha(opacityProgress / 100f + 0.2f)
                .padding(if (isMiniMode) 20.dp else 0.dp),
        ) {
            if (isMiniMode) {
                MiniKeyboardContent(
                    miniPanel = miniPanel,
                    modifierStates = modifierStates,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = { label, x, y ->
                        popupKey = label
                        popupOffsetX = x
                        popupOffsetY = y
                        popupVisible = true
                    },
                    onPopupHide = { popupVisible = false },
                    onSwitchPanel = { miniPanel = it },
                    onHide = {
                        savePosition()
                        onHide()
                    },
                    onToggleFull = exitMiniMode,
                    onDragHandleDrag = { dx, dy ->
                        offsetX += dx
                        offsetY += dy
                    },
                    onDragEnd = { savePosition() },
                    modifierColor = ::modifierColor,
                )
            } else {
                FullKeyboardContent(
                    isNumpadMode = isNumpadMode,
                    showResizeHandle = showResizeHandle,
                    keyboardHeightPx = keyboardHeightPx,
                    modifierStates = modifierStates,
                    opacityProgress = opacityProgress,
                    onOpacityChange = { opacityProgress = it },
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = { label, x, y ->
                        popupKey = label
                        popupOffsetX = x
                        popupOffsetY = y
                        popupVisible = true
                    },
                    onPopupHide = { popupVisible = false },
                    onShowNumpad = { isNumpadMode = true },
                    onBackToFull = { isNumpadMode = false },
                    onEnterMini = enterMiniMode,
                    onSwitchToTab = onSwitchToTab,
                    onHide = {
                        onHide()
                    },
                    onToggleResize = { showResizeHandle = !showResizeHandle },
                    onResize = { newHeight ->
                        keyboardHeightPx = newHeight
                    },
                    onResizeEnd = { saveHeight() },
                    modifierColor = ::modifierColor,
                    onHeightMeasured = { if (keyboardHeightPx <= 0) keyboardHeightPx = it },
                )
            }
        }

        // Key popup overlay (root-level, positioned via root coordinates)
        AnimatedVisibility(
            visible = popupVisible && popupKey != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .offset {
                    IntOffset(
                        (popupOffsetX - 25.dp.toPx()).roundToInt(),
                        (popupOffsetY - 64.dp.toPx()).roundToInt()
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp, 64.dp)
                    .background(
                        Color(0xFFFFFFFF),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = popupKey ?: "",
                    color = Color.Black,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Full Keyboard Content
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullKeyboardContent(
    isNumpadMode: Boolean,
    showResizeHandle: Boolean,
    keyboardHeightPx: Int,
    modifierStates: Map<Int, Int>,
    opacityProgress: Float,
    onOpacityChange: (Float) -> Unit,
    onKeyAction: (Int, Boolean, Boolean) -> Unit,
    onKeyRelease: (Int, Boolean) -> Unit,
    onPopupShow: (String, Float, Float) -> Unit,
    onPopupHide: () -> Unit,
    onShowNumpad: () -> Unit,
    onBackToFull: () -> Unit,
    onEnterMini: () -> Unit,
    onSwitchToTab: ((Int) -> Unit)?,
    onHide: () -> Unit,
    onToggleResize: () -> Unit,
    onResize: (Int) -> Unit,
    onResizeEnd: () -> Unit,
    modifierColor: (Int) -> Color,
    onHeightMeasured: (Int) -> Unit,
) {
    val density = LocalDensity.current
    @Suppress("LocalContextGetResourceValueCall")
    val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels
    val resizeMinH = screenHeightPx / 5
    val resizeMaxH = screenHeightPx * 4 / 5

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (keyboardHeightPx > 0) Modifier.height(with(density) { keyboardHeightPx.toDp() })
                else Modifier.fillMaxHeight()
            )
            .onGloballyPositioned { coords ->
                if (keyboardHeightPx <= 0) {
                    onHeightMeasured(coords.size.height)
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1C1C1E)),
    ) {
        // Resize handle
        if (showResizeHandle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { onResizeEnd() },
                            onVerticalDrag = { _, dragAmount ->
                                val newH = keyboardHeightPx.coerceAtLeast(1) - dragAmount.roundToInt()
                                if (newH in resizeMinH..resizeMaxH) {
                                    onResize(newH)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .background(Color(0x88FFFFFF), RoundedCornerShape(2.dp)),
                )
            }
        }

        // ── Unified layout: Left sidebar + Content + Right sidebar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Left sidebar — 虚拟 | 快捷 | 系统
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .padding(2.dp),
            ) {
                // stringResource for sidebar labels
                val sidebarVirtual = stringResource(R.string.keyboard_sidebar_virtual)
                val sidebarShortcuts = stringResource(R.string.keyboard_sidebar_shortcuts)
                val sidebarSystem = stringResource(R.string.keyboard_sidebar_system)

                // "虚拟" — always active (both full keyboard and numpad are sub-states)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SidebarTab(sidebarVirtual, true) {
                        // If in numpad mode, return to full keyboard
                        if (isNumpadMode) onBackToFull()
                    }
                }
                // "快捷" — switch to shortcuts tab, hide virtual keyboard
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SidebarTab(sidebarShortcuts, false) {
                        onSwitchToTab?.invoke(1)
                    }
                }
                // "系统" — switch to system IME tab, hide virtual keyboard
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SidebarTab(sidebarSystem, false) {
                        onSwitchToTab?.invoke(0)
                    }
                }
            }

            // Page container — weight(1f) fills remaining space between sidebars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp),
            ) {
                if (isNumpadMode) {
                    NumpadFullWidthContent(
                        modifierStates = modifierStates,
                        onKeyAction = onKeyAction,
                        onKeyRelease = onKeyRelease,
                        onPopupShow = onPopupShow,
                        onPopupHide = onPopupHide,
                        modifierColor = modifierColor,
                        onBackToFull = onBackToFull,
                    )
                } else {
                    KeyboardPageContent(
                        page = KeyboardLayouts.MAIN,
                        modifierStates = modifierStates,
                        onKeyAction = onKeyAction,
                        onKeyRelease = onKeyRelease,
                        onPopupShow = onPopupShow,
                        onPopupHide = onPopupHide,
                        modifierColor = modifierColor,
                        actionCallbacks = mapOf(
                            KeyboardLayouts.ACTION_SHOW_NUMPAD to onShowNumpad,
                            KeyboardLayouts.ACTION_ENTER_MINI to onEnterMini,
                        ),
                    )
                }
            }

            // Right sidebar
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SidebarAction("✕") { onHide() }

                Spacer(Modifier.height(5.dp))
                VerticalOpacitySlider(
                    value = opacityProgress,
                    onValueChange = onOpacityChange,
                    modifier = Modifier
                        .width(44.dp)
                        .weight(1f)
                        .padding(bottom = 5.dp),
                )
                Text(
                    "${(opacityProgress + 20f).roundToInt()}%\nOpacity",
                    color = Color(0x88FFFFFF),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun VerticalOpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Slider value range: 0..80
    // Actual opacity = sliderValue / 100f + 0.2f  (range: 0.2..1.0)
    val sliderRange = 0f..80f
    val inactiveTrackColor = Color.White.copy(alpha = 0.2f)
    val activeTrackColor = Color.White.copy(alpha = 0.7f)
    val thumbColor = Color.White

    val fraction = value / sliderRange.endInclusive
    val thumbSize = 16.dp
    val trackWidth = 4.dp
    val density = LocalDensity.current

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    var sliderHeightPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .onGloballyPositioned { sliderHeightPx = it.size.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val frac = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        val newValue = frac * sliderRange.endInclusive
                        currentOnValueChange(newValue.coerceIn(sliderRange))
                    },
                    onVerticalDrag = { _, dragAmount ->
                        // bottom=min, top=max: drag DOWN (positive) → decrease, drag UP (negative) → increase
                        val delta = -dragAmount / size.height * sliderRange.endInclusive
                        currentOnValueChange((currentValue + delta).coerceIn(sliderRange))
                    },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Inactive track (full height)
        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight()
                .align(Alignment.Center)
                .background(inactiveTrackColor, RoundedCornerShape(2.dp)),
        )
        // Active track (from bottom, proportional to value)
        Box(
            modifier = Modifier
                .width(trackWidth)
                .fillMaxHeight(fraction)
                .align(Alignment.BottomCenter)
                .background(activeTrackColor, RoundedCornerShape(2.dp)),
        )
        // Thumb: bottom = min (slider 0), top = max (slider 80).
        // Active track grows upward from bottom, thumb position mirrors this.
        val travelDistance = sliderHeightPx - with(density) { thumbSize.toPx() }
        Box(
            modifier = Modifier
                .offset { IntOffset(0, ((1f - fraction) * travelDistance).roundToInt()) }
                .size(thumbSize)
                .background(thumbColor, CircleShape),
        )
    }
}

@Composable
private fun SidebarTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else Color(0x88FFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SidebarAction(label: String, active: Boolean = false, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val bgColor = when {
        isPressed -> Color.White.copy(alpha = 0.30f)
        active -> Color.White.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Box(
        modifier = Modifier
            .width(42.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> isPressed = true
                            PointerEventType.Release -> {
                                if (isPressed) {
                                    isPressed = false
                                    onClick()
                                }
                            }
                            PointerEventType.Exit -> isPressed = false
                            else -> {}
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Mini Keyboard Content
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniKeyboardContent(
    miniPanel: String,
    modifierStates: Map<Int, Int>,
    onKeyAction: (Int, Boolean, Boolean) -> Unit,
    onKeyRelease: (Int, Boolean) -> Unit,
    onPopupShow: (String, Float, Float) -> Unit,
    onPopupHide: () -> Unit,
    onSwitchPanel: (String) -> Unit,
    onHide: () -> Unit,
    onToggleFull: () -> Unit,
    onDragHandleDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    modifierColor: (Int) -> Color,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .aspectRatio(2f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1C1C1E))
            .padding(4.dp),
    ) {
        // Drag handle + modifier keys row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(35.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                KeyboardKeyButton(
                    keyDef = KeyboardKeyDef("Shift", KeyboardLayouts.KEY_LSHIFT, KeyType.MODIFIER),
                    modifierState = modifierStates[KeyboardLayouts.KEY_LSHIFT] ?: MOD_NEUTRAL,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                KeyboardKeyButton(
                    keyDef = KeyboardKeyDef("Ctrl", KeyboardLayouts.KEY_LCTRL, KeyType.MODIFIER),
                    modifierState = modifierStates[KeyboardLayouts.KEY_LCTRL] ?: MOD_NEUTRAL,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                )
            }

            // Drag handle (slightly wider for better grip)
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { _, dragAmount ->
                                onDragHandleDrag(dragAmount.x, dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .background(Color(0x88FFFFFF), RoundedCornerShape(2.dp)),
                )
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                KeyboardKeyButton(
                    keyDef = KeyboardKeyDef("Win", KeyboardLayouts.KEY_LWIN, KeyType.MODIFIER),
                    modifierState = modifierStates[KeyboardLayouts.KEY_LWIN] ?: MOD_NEUTRAL,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                KeyboardKeyButton(
                    keyDef = KeyboardKeyDef("Alt", KeyboardLayouts.KEY_LALT, KeyType.MODIFIER),
                    modifierState = modifierStates[KeyboardLayouts.KEY_LALT] ?: MOD_NEUTRAL,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                )
            }
        }

        // Mini panel content
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            when (miniPanel) {
                "alpha" -> KeyboardPageContent(
                    page = KeyboardLayouts.MINI_ALPHA,
                    modifierStates = modifierStates,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                    actionCallbacks = mapOf(
                        KeyboardLayouts.ACTION_TOGGLE_NUM to { onSwitchPanel("num") },
                        KeyboardLayouts.ACTION_TOGGLE_PC to { onSwitchPanel("pc") },
                        KeyboardLayouts.ACTION_HIDE to { onHide() },
                        KeyboardLayouts.ACTION_TOGGLE_FULL to { onToggleFull() },
                    ),
                )
                "num" -> KeyboardPageContent(
                    page = KeyboardLayouts.MINI_NUM,
                    modifierStates = modifierStates,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                    actionCallbacks = mapOf(
                        KeyboardLayouts.ACTION_BACK_ALPHA to { onSwitchPanel("alpha") },
                        KeyboardLayouts.ACTION_TOGGLE_PC to { onSwitchPanel("pc") },
                        KeyboardLayouts.ACTION_HIDE to { onHide() },
                        KeyboardLayouts.ACTION_TOGGLE_FULL to { onToggleFull() },
                    ),
                )
                "pc" -> MiniPCPanel(
                    modifierStates = modifierStates,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    onSwitchAlpha = { onSwitchPanel("alpha") },
                    modifierColor = modifierColor,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Mini PC Panel (special layout with arrow keys)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniPCPanel(
    modifierStates: Map<Int, Int>,
    onKeyAction: (Int, Boolean, Boolean) -> Unit,
    onKeyRelease: (Int, Boolean) -> Unit,
    onPopupShow: (String, Float, Float) -> Unit,
    onPopupHide: () -> Unit,
    onSwitchAlpha: () -> Unit,
    modifierColor: (Int) -> Color,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Row 1: Esc Tab Home End PrtSc
        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp).padding(bottom = 5.dp),
        ) {
            val keys = listOf(
                KeyboardKeyDef("Esc", KeyboardLayouts.KEY_ESC, KeyType.MODIFIER),
                KeyboardKeyDef("Tab", KeyboardLayouts.KEY_TAB, KeyType.MODIFIER),
                KeyboardKeyDef("Home", KeyboardLayouts.KEY_HOME, KeyType.MODIFIER),
                KeyboardKeyDef("End", KeyboardLayouts.KEY_END, KeyType.MODIFIER),
                KeyboardKeyDef("PrtSc", KeyboardLayouts.KEY_PRTSC, KeyType.MODIFIER),
            )
            keys.forEach { keyDef ->
                KeyboardKeyButton(
                    keyDef = keyDef,
                    modifierState = modifierStates[keyDef.keyCode] ?: MOD_NEUTRAL,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                )
            }
        }

        // Row 2: Left - ABC + Delete | Right - Arrow keys
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left column
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // ABC button
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onSwitchAlpha() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(com.alexclin.moonlink.android.R.string.layout_keyboard_mini_text_3ae7b),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Delete button
                val delDef = KeyboardKeyDef("⌫ Delete", KeyboardLayouts.KEY_FORWARD_DEL, KeyType.MODIFIER)
                KeyboardKeyButton(
                    keyDef = delDef,
                    modifierState = modifierStates[delDef.keyCode] ?: MOD_NEUTRAL,
                    onKeyAction = onKeyAction,
                    onKeyRelease = onKeyRelease,
                    onPopupShow = onPopupShow,
                    onPopupHide = onPopupHide,
                    modifierColor = modifierColor,
                    fixedWidth = 100.dp,
                    fixedHeight = 40.dp,
                )
            }

            // Right - arrow keys grid
            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Up
                    val upDef = KeyboardKeyDef("↑", KeyboardLayouts.KEY_DPAD_UP)
                    KeyboardKeyButton(
                        keyDef = upDef,
                        modifierState = MOD_NEUTRAL,
                        onKeyAction = onKeyAction,
                        onKeyRelease = onKeyRelease,
                        onPopupShow = onPopupShow,
                        onPopupHide = onPopupHide,
                        modifierColor = modifierColor,
                        fixedWidth = 40.dp,
                        fixedHeight = 40.dp,
                    )
                    Row {
                        val leftDef = KeyboardKeyDef("←", KeyboardLayouts.KEY_DPAD_LEFT)
                        KeyboardKeyButton(
                            keyDef = leftDef,
                            modifierState = MOD_NEUTRAL,
                            onKeyAction = onKeyAction,
                            onKeyRelease = onKeyRelease,
                            onPopupShow = onPopupShow,
                            onPopupHide = onPopupHide,
                            modifierColor = modifierColor,
                            fixedWidth = 40.dp,
                            fixedHeight = 40.dp,
                        )
                        val downDef = KeyboardKeyDef("↓", KeyboardLayouts.KEY_DPAD_DOWN)
                        KeyboardKeyButton(
                            keyDef = downDef,
                            modifierState = MOD_NEUTRAL,
                            onKeyAction = onKeyAction,
                            onKeyRelease = onKeyRelease,
                            onPopupShow = onPopupShow,
                            onPopupHide = onPopupHide,
                            modifierColor = modifierColor,
                            fixedWidth = 40.dp,
                            fixedHeight = 40.dp,
                        )
                        val rightDef = KeyboardKeyDef("→", KeyboardLayouts.KEY_DPAD_RIGHT)
                        KeyboardKeyButton(
                            keyDef = rightDef,
                            modifierState = MOD_NEUTRAL,
                            onKeyAction = onKeyAction,
                            onKeyRelease = onKeyRelease,
                            onPopupShow = onPopupShow,
                            onPopupHide = onPopupHide,
                            modifierColor = modifierColor,
                            fixedWidth = 40.dp,
                            fixedHeight = 40.dp,
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Numpad Full-Width Content (left numpad + right 3×3 + back button)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun NumpadFullWidthContent(
    modifierStates: Map<Int, Int>,
    onKeyAction: (Int, Boolean, Boolean) -> Unit,
    onKeyRelease: (Int, Boolean) -> Unit,
    onPopupShow: (String, Float, Float) -> Unit,
    onPopupHide: () -> Unit,
    modifierColor: (Int) -> Color,
    onBackToFull: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
    ) {
        // ── Left: 4-column numpad (4 rows, equal height) ──
        Column(
            modifier = Modifier
                .weight(2.0f)
                .fillMaxHeight(),
        ) {
            KeyboardLayouts.NUMPAD_LEFT_GRID.forEach { rowKeys ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    rowKeys.forEach { keyDef ->
                        Box(
                            modifier = Modifier
                                .weight(keyDef.weight.factor)
                                .fillMaxHeight(),
                        ) {
                            KeyboardKeyButton(
                                keyDef = keyDef,
                                modifierState = modifierStates[keyDef.keyCode] ?: MOD_NEUTRAL,
                                onKeyAction = onKeyAction,
                                onKeyRelease = onKeyRelease,
                                onPopupShow = onPopupShow,
                                onPopupHide = onPopupHide,
                                modifierColor = modifierColor,
                            )
                        }
                    }
                }
            }
        }

        // ── Right: 3×3 special keys ──
        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight(),
        ) {
            KeyboardLayouts.NUMPAD_RIGHT_GRID.forEach { rowKeys ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    rowKeys.forEach { keyDef ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            KeyboardKeyButton(
                                keyDef = keyDef,
                                modifierState = modifierStates[keyDef.keyCode] ?: MOD_NEUTRAL,
                                onKeyAction = onKeyAction,
                                onKeyRelease = onKeyRelease,
                                onPopupShow = onPopupShow,
                                onPopupHide = onPopupHide,
                                modifierColor = modifierColor,
                                fontSize = if (keyDef.type == KeyType.MODIFIER) 10.sp else 11.sp,
                            )
                        }
                    }
                }
            }
        }

        // ── Far right: vertical ">" back-to-full button ──
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onBackToFull() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Back to full keyboard",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Keyboard Page Content
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun KeyboardPageContent(
    page: KeyboardPageDef,
    modifierStates: Map<Int, Int>,
    onKeyAction: (Int, Boolean, Boolean) -> Unit,
    onKeyRelease: (Int, Boolean) -> Unit,
    onPopupShow: (String, Float, Float) -> Unit,
    onPopupHide: () -> Unit,
    modifierColor: (Int) -> Color,
    actionCallbacks: Map<String, () -> Unit> = emptyMap(),
) {
    // ── Regular layout (no right block) ──
    Column(
        modifier = Modifier.fillMaxSize().padding(2.dp),
    ) {
        page.rows.forEach { row ->
            val density = LocalDensity.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(row.heightWeight)
                    .padding(horizontal = with(density) { row.horizontalPaddingDp.dp }),
            ) {
                row.keys.forEach { keyDef ->
                    KeyItem(keyDef, modifierStates, onKeyAction, onKeyRelease, onPopupShow, onPopupHide, modifierColor, actionCallbacks)
                }
            }
        }
    }
}

/** Single key rendering helper. */
@Composable
private fun RowScope.KeyItem(
    keyDef: KeyboardKeyDef,
    modifierStates: Map<Int, Int>,
    onKeyAction: (Int, Boolean, Boolean) -> Unit,
    onKeyRelease: (Int, Boolean) -> Unit,
    onPopupShow: (String, Float, Float) -> Unit,
    onPopupHide: () -> Unit,
    modifierColor: (Int) -> Color,
    actionCallbacks: Map<String, () -> Unit>,
) {
    when (keyDef.type) {
        KeyType.ACTION -> {
            val callback = actionCallbacks[keyDef.actionId]
            Box(Modifier.weight(keyDef.weight.factor).fillMaxHeight()) {
                ActionKeyButton(
                    label = keyDef.label,
                    onClick = { callback?.invoke() },
                )
            }
        }
        KeyType.NORMAL, KeyType.MODIFIER -> {
            if (keyDef.keyCode == 0) {
                Spacer(Modifier.width(4.dp))
            } else {
                Box(Modifier.weight(keyDef.weight.factor).fillMaxHeight()) {
                    KeyboardKeyButton(
                        keyDef = keyDef,
                        modifierState = modifierStates[keyDef.keyCode] ?: MOD_NEUTRAL,
                        onKeyAction = onKeyAction,
                        onKeyRelease = onKeyRelease,
                        onPopupShow = onPopupShow,
                        onPopupHide = onPopupHide,
                        modifierColor = modifierColor,
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Keyboard Key Button
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun KeyboardKeyButton(
    keyDef: KeyboardKeyDef,
    modifierState: Int,
    onKeyAction: (Int, Boolean, Boolean) -> Unit,
    onKeyRelease: (Int, Boolean) -> Unit,
    onPopupShow: (String, Float, Float) -> Unit,
    onPopupHide: () -> Unit,
    modifierColor: (Int) -> Color,
    fixedWidth: androidx.compose.ui.unit.Dp? = null,
    fixedHeight: androidx.compose.ui.unit.Dp? = null,
    fontSize: androidx.compose.ui.unit.TextUnit? = null,
) {
    var isPressed by remember { mutableStateOf(false) }
    var localPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var lastDownTime by remember { mutableLongStateOf(0L) }
    var currentDownTime by remember { mutableLongStateOf(0L) }

    val bgColor = when {
        isPressed && keyDef.type == KeyType.MODIFIER ->
            Color.White.copy(alpha = 0.35f)
        isPressed ->
            Color.White.copy(alpha = 0.25f)
        modifierState != MOD_NEUTRAL && keyDef.type == KeyType.MODIFIER ->
            modifierColor(keyDef.keyCode)
        keyDef.type == KeyType.MODIFIER ->
            Color.White.copy(alpha = 0.12f)
        else ->
            Color.White.copy(alpha = 0.08f)
    }

    val modifier = Modifier
        .then(if (fixedWidth != null) Modifier.width(fixedWidth) else Modifier.fillMaxWidth())
        .then(if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier.fillMaxHeight())
        .padding(1.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(bgColor)
        .onGloballyPositioned { localPos = it.positionInRoot() }
        .pointerInput(keyDef.keyCode) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Press -> {
                            isPressed = true
                            currentDownTime = System.currentTimeMillis()
                            val timeSinceLastDown = currentDownTime - lastDownTime

                            if (timeSinceLastDown < DOUBLE_TAP_TIMEOUT_MS) {
                                onKeyAction(keyDef.keyCode, true, true)
                                lastDownTime = 0L
                            } else {
                                onKeyAction(keyDef.keyCode, true, false)
                                lastDownTime = currentDownTime
                                val display = keyDef.label.split(" ").first()
                                onPopupShow(display, localPos.x, localPos.y)
                            }
                        }
                        PointerEventType.Release -> {
                            isPressed = false
                            val pressDuration = System.currentTimeMillis() - currentDownTime
                            onKeyRelease(keyDef.keyCode, pressDuration >= HOLD_THRESHOLD_MS)
                            onPopupHide()
                        }
                        else -> {}
                    }
                }
            }
        }

    val resolvedFontSize = fontSize ?: when {
        keyDef.keyCode == KeyboardLayouts.KEY_ENTER && keyDef.label.length <= 2 -> 20.sp
        keyDef.type == KeyType.MODIFIER -> 12.sp
        else -> 14.sp
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            keyDef.label.split(" ").first(),
            color = if (keyDef.keyCode == KeyboardLayouts.KEY_ENTER && modifierState == MOD_NEUTRAL) {
                Color(0xFF0A84FF)
            } else {
                Color.White
            },
            fontSize = resolvedFontSize,
            lineHeight = resolvedFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Action Key Button (panel switch, hide, etc.)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionKeyButton(
    label: String,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Helper Functions
// ────────────────────────────────────────────────────────────────────────────

private fun resetSingleModifiers(
    modifierStates: MutableMap<Int, Int>,
    physicallyHeldModifiers: MutableSet<Int>,
    bridge: VirtualKeyboardBridge,
) {
    for (entry in modifierStates.entries.toList()) {
        if (entry.value == MOD_SINGLE) {
            val keyCode = entry.key
            if (physicallyHeldModifiers.contains(keyCode)) continue
            modifierStates[keyCode] = MOD_NEUTRAL
            bridge.sendKeyEvent(false, keyCode.toShort())
        }
    }
}
