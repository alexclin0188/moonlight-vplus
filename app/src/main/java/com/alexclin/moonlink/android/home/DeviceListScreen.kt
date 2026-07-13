package com.alexclin.moonlink.android.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.alexclin.moonlink.android.theme.statusOffline
import com.alexclin.moonlink.android.theme.statusOnline
import com.alexclin.moonlink.android.theme.windowsBlue
import com.alexclin.moonlink.android.stream.StreamActivity
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.BackgroundOverlay
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.alexclin.moonlink.android.util.PlatformBinding

import com.alexclin.moonlink.android.util.ServerHelper
import com.alexclin.moonlink.android.util.Iperf3Tester
import com.alexclin.moonlink.android.util.CacheHelper
import com.limelight.nvstream.wol.WakeOnLanSender
import com.alexclin.moonlink.android.home.loadCachedAppList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Menu action model ─────────────────────────────────────────────

data class DeviceMenuAction(
    val id: String,
    val labelResId: Int,
    val isVisible: (ComputerDetails) -> Boolean,
)

private val ALL_MENU_ACTIONS = listOf(
    DeviceMenuAction("pair",           R.string.pcview_menu_pair_pc)            { it.pairState != PairingManager.PairState.PAIRED },
    // TODO: 2.0 版本再实现 — 唤醒/睡眠/重启
    // DeviceMenuAction("wol",            R.string.pcview_menu_send_wol)           { it.state == ComputerDetails.State.OFFLINE },
    DeviceMenuAction("delete",         R.string.pcview_menu_delete_pc)          { it.pairState == PairingManager.PairState.PAIRED },
    DeviceMenuAction("resume",         R.string.menu_resume_stream)             { it.state == ComputerDetails.State.ONLINE && it.runningGameId != 0 },
    DeviceMenuAction("quit",           R.string.action_quit_app)                  { it.state == ComputerDetails.State.ONLINE && it.runningGameId != 0 },
    DeviceMenuAction("applist",        R.string.pcview_menu_app_list)           { true },
    DeviceMenuAction("detail",         R.string.pcview_menu_details)            { true },
    // DeviceMenuAction("sleep",          R.string.send_sleep_command)             { it.state == ComputerDetails.State.ONLINE },
    // DeviceMenuAction("restart",        R.string.action_restart)                 { it.state == ComputerDetails.State.ONLINE },
    DeviceMenuAction("iperf",          R.string.action_network_bandwidth_test)         { true },
    DeviceMenuAction("webui",          R.string.pcview_menu_open_webui)         { true },
    DeviceMenuAction("disable_ipv6",   R.string.pcview_menu_disable_ipv6)       { true },
    DeviceMenuAction("nettest",        R.string.pcview_menu_test_network)       { true },
    DeviceMenuAction("secondary_screen", R.string.pcview_menu_secondary_screen) {
        it.state == ComputerDetails.State.ONLINE &&
        it.pairState == PairingManager.PairState.PAIRED
    },
    DeviceMenuAction("gs_eol",         R.string.action_gamestream_eol)                { it.nvidiaServer },
)

// ── Main screen composable ────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceListScreen(
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    computers: List<ComputerDetails>,
    snackbarHostState: SnackbarHostState,
    onNavigateToOverview: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAppList: ((String) -> Unit)? = null,
    onComputerRemoved: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    var isPairingLoading by remember { mutableStateOf(false) }

    // ── Add-device menu state & QR launcher ──────────────────────
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddManuallyDialog by remember { mutableStateOf(false) }

    // ── Trigger to force DeviceBoxArt to reload from disk ────────
    var refreshTrigger by remember { mutableStateOf(0) }

    val qrCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val contents = result.data?.getStringExtra(QrScannerActivity.EXTRA_SCAN_RESULT)?.trim()
            ?: return@rememberLauncherForActivityResult

        val uri = contents.toUri()
        if ("moonlight" != uri.scheme || "pair" != uri.host) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(com.alexclin.moonlink.android.R.string.qr_invalid_code)) }
            return@rememberLauncherForActivityResult
        }

        val host = uri.getQueryParameter("host")
            ?: run { scope.launch { snackbarHostState.showSnackbar(context.getString(com.alexclin.moonlink.android.R.string.qr_invalid_code)) }; return@rememberLauncherForActivityResult }
        val pin = uri.getQueryParameter("pin")
            ?: run { scope.launch { snackbarHostState.showSnackbar(context.getString(com.alexclin.moonlink.android.R.string.qr_invalid_code)) }; return@rememberLauncherForActivityResult }
        val portStr = uri.getQueryParameter("port")
        val port = if (portStr != null) { try { portStr.toInt() } catch (_: NumberFormatException) { NvHTTP.DEFAULT_HTTP_PORT } } else { null }

        isPairingLoading = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    handleQrPairResult(context, host, pin, port, managerBinder)
                }
                when (result) {
                    is PairQrResult.Success -> {
                        snackbarHostState.showSnackbar(context.getString(com.alexclin.moonlink.android.R.string.addpc_success))
                    }
                    is PairQrResult.Error -> {
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(context.getString(R.string.toast_pair_error, e.message ?: e.javaClass.simpleName))
            } finally {
                isPairingLoading = false
            }
        }
    }
    // ── 主动获取 box art 缩略图（异步并发展）──────────────
    // 为所有在线+已配对且无缓存的设备，自动拉取应用列表并下载缩略图到磁盘。
    // 使用 rememberSaveable 避免 tab 切换时重复执行。
    var hasPrefetched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasPrefetched || managerBinder == null) return@LaunchedEffect

        val targets = computers.filter {
            it.state == ComputerDetails.State.ONLINE &&
            it.pairState == PairingManager.PairState.PAIRED &&
            it.uuid != null
        }
        if (targets.isEmpty()) return@LaunchedEffect

        hasPrefetched = true

        // 并发检查所有设备的缓存，跳过已有缓存的
        val uncached = withContext(Dispatchers.IO) {
            targets.filter { computer ->
                val dir = CacheHelper.openPath(false, context.cacheDir, "boxart", computer.uuid!!)
                val appListFile = CacheHelper.openPath(false, context.cacheDir, "applist", computer.uuid!!)
                !(dir.isDirectory() && (dir.listFiles()?.isNotEmpty() == true || appListFile.exists()))
            }
        }
        if (uncached.isEmpty()) return@LaunchedEffect

        // 异步并发拉取所有无缓存的设备
        coroutineScope {
            val results = uncached.map { computer ->
                async(Dispatchers.IO) {
                    computer.uuid to fetchAndCacheAppListAndBoxArt(context, computer, managerBinder!!)
                }
            }
            var needsRefresh = false
            for (deferred in results) {
                val (uuid, result) = deferred.await()
                if (result != null && uuid != null) {
                    invalidateBoxArtCache(uuid)
                    needsRefresh = true
                }
            }
            if (needsRefresh) refreshTrigger++
        }
    }

    // Separate paired / unpaired
    val paired   = computers.filter { it.pairState == com.limelight.nvstream.http.PairingManager.PairState.PAIRED }
        .sortedWith(compareByDescending<ComputerDetails> { it.state == ComputerDetails.State.ONLINE }
            .thenBy { it.name?.lowercase() })
    val unpaired = computers.filter { it.pairState != com.limelight.nvstream.http.PairingManager.PairState.PAIRED }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundOverlay()

        // Pairing loading indicator at top
        if (isPairingLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(16.dp),
                strokeWidth = 2.dp,
            )
        }

        if (computers.isEmpty()) {
            // ── Empty state ───────────────────────────────
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DevicesOther,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        context.getString(R.string.no_devices_found),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        }

    if (computers.isNotEmpty()) {
        val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

        if (isLandscape) {
                // ── 横屏：左右固定分栏 ─────────────────
                // 两列各占一半宽度（weight(1f)），无论另一侧是否有内容
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // 可控设备为空且未配对设备不为空 → 未配对设备移到左侧展示
                    val showUnpairedLeft = paired.isEmpty() && unpaired.isNotEmpty()

                    if (!showUnpairedLeft) {
                        // Left: paired devices（空列表时仅留空列，不撑满）
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item { SectionHeader(context.getString(R.string.section_paired_devices)) }
                            items(paired, key = { it.uuid ?: it.name.orEmpty() }) { computer ->
                                DeviceCard(
                                    computer = computer,
                                    managerBinder = managerBinder,
                                    onStream = { launchStream(context, computer, managerBinder, forceResume = computer.runningGameId != 0) },
                                    onClickInfo = { onNavigateToOverview(computer.uuid.orEmpty()) },
                                    onNavigateToDetail = { onNavigateToDetail(computer.uuid.orEmpty()) },
                                    snackbarHostState = snackbarHostState,
                                    scope = scope,
                                    refreshKey = refreshTrigger,
                                    setPairingLoading = { isPairingLoading = it },
                                    onComputerRemoved = onComputerRemoved,
                                    onNavigateToAppList = onNavigateToAppList,
                                )
                            }
                        }

                        Spacer(Modifier.width(16.dp))
                    }



                    // Right: unpaired devices
                    // 当 showUnpairedLeft 时，此列占据左侧位置（weight 放在 Spacer 前的第一列）
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (unpaired.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = context.getString(R.string.section_unpaired_devices),
                                )
                            }
                            items(unpaired, key = { it.uuid ?: it.name.orEmpty() }) { computer ->
                                DeviceCard(
                                    computer = computer,
                                    managerBinder = managerBinder,
                                    onStream = { launchStream(context, computer, managerBinder, forceResume = computer.runningGameId != 0) },
                                    onClickInfo = { onNavigateToOverview(computer.uuid.orEmpty()) },
                                    onNavigateToDetail = { onNavigateToDetail(computer.uuid.orEmpty()) },
                                    snackbarHostState = snackbarHostState,
                                    scope = scope,
                                    refreshKey = refreshTrigger,
                                    setPairingLoading = { isPairingLoading = it },
                                    onComputerRemoved = onComputerRemoved,
                                    onNavigateToAppList = onNavigateToAppList,
                                )
                            }
                        }
                    }

                    // showUnpairedLeft 时，左测已隐藏，右测占了左侧位置，需补一个空列占右侧位置
                    if (showUnpairedLeft) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 空列，保持布局对称
                        }
                    }
                }
            } else {
                // ── 竖屏：单列列表 ─────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ── Paired devices ─────────────────────
                    if (paired.isNotEmpty()) {
                        item(key = "header_paired") { SectionHeader(context.getString(R.string.section_paired_devices)) }
                        items(paired, key = { it.uuid ?: it.name.orEmpty() }) { computer ->
                            DeviceCard(
                                computer = computer,
                                managerBinder = managerBinder,
                                onStream = { launchStream(context, computer, managerBinder, forceResume = computer.runningGameId != 0) },
                                onClickInfo = { onNavigateToOverview(computer.uuid.orEmpty()) },
                                onNavigateToDetail = { onNavigateToDetail(computer.uuid.orEmpty()) },
                                snackbarHostState = snackbarHostState,
                                scope = scope,
                                refreshKey = refreshTrigger,
                                setPairingLoading = { isPairingLoading = it },
                                onComputerRemoved = onComputerRemoved,
                                onNavigateToAppList = onNavigateToAppList,
                            )
                        }
                    }

                    // ── Unpaired devices ───────────────────
                    if (unpaired.isNotEmpty()) {
                        item(key = "header_unpaired") {
                            SectionHeader(
                                title = context.getString(R.string.section_unpaired_devices),
                            )
                        }
                        items(unpaired, key = { it.uuid ?: it.name.orEmpty() }) { computer ->
                            DeviceCard(
                                computer = computer,
                                managerBinder = managerBinder,
                                onStream = { launchStream(context, computer, managerBinder, forceResume = computer.runningGameId != 0) },
                                onClickInfo = { onNavigateToOverview(computer.uuid.orEmpty()) },
                                onNavigateToDetail = { onNavigateToDetail(computer.uuid.orEmpty()) },
                                snackbarHostState = snackbarHostState,
                                scope = scope,
                                refreshKey = refreshTrigger,
                                setPairingLoading = { isPairingLoading = it },
                                onComputerRemoved = onComputerRemoved,
                                onNavigateToAppList = onNavigateToAppList,
                            )
                        }
                    }
                }
            }
        }

        // ── 底部居中 FAB + 弹出菜单（从 FAB 上方展开动画） ──
        val density = LocalDensity.current
        val expandedStates = remember { MutableTransitionState(false) }
        expandedStates.targetState = showAddMenu
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .zIndex(10f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FloatingActionButton(
                onClick = { showAddMenu = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = context.getString(R.string.cd_add_device))
            }

            // 自定义弹窗带动画 — 从 FAB 上方展开/收起
            if (expandedStates.currentState || expandedStates.targetState) {
                Popup(
                    onDismissRequest = { showAddMenu = false },
                    alignment = Alignment.BottomCenter,
                    offset = IntOffset(0, with(density) { (-72).dp.roundToPx() }),
                    properties = PopupProperties(focusable = true),
                ) {
                    AnimatedVisibility(
                        visibleState = expandedStates,
                        enter = fadeIn() + scaleIn(
                            initialScale = 0.5f,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                        ),
                        exit = fadeOut() + scaleOut(
                            targetScale = 0.5f,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                        ),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.width(IntrinsicSize.Min),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(context.getString(com.alexclin.moonlink.android.R.string.addpc_manual)) },
                                    onClick = {
                                        showAddMenu = false
                                        showAddManuallyDialog = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(context.getString(com.alexclin.moonlink.android.R.string.addpc_qr_scan)) },
                                    onClick = {
                                        showAddMenu = false
                                        val intent = Intent(context, QrScannerActivity::class.java)
                                        qrCodeLauncher.launch(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 手动添加电脑对话框 ──
    if (showAddManuallyDialog) {
        AddComputerManuallyDialog(
            managerBinder = managerBinder,
            onDismiss = { showAddManuallyDialog = false },
            onSuccess = { msg ->
                showAddManuallyDialog = false
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
            onError = { msg ->
                showAddManuallyDialog = false
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
        )
    }
}

// ── Section header ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, showProgress: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (showProgress) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Marquee text (不换行，过长时左右缓慢滚动) ───────────────────

@Composable
private fun MarqueeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    var textWidth by remember { mutableStateOf(0f) }
    var containerWidth by remember { mutableStateOf(0f) }
    val offsetX = remember { Animatable(0f) }

    val needsScroll = textWidth > containerWidth && containerWidth > 0f

    LaunchedEffect(needsScroll, textWidth, containerWidth) {
        if (needsScroll) {
            val scrollDistance = textWidth - containerWidth
            while (true) {
                // Pause at start
                offsetX.snapTo(0f)
                delay(1500)
                // Slowly scroll to the end
                offsetX.animateTo(
                    targetValue = -scrollDistance,
                    animationSpec = tween(
                        durationMillis = (scrollDistance * 20).toInt().coerceIn(2000, 8000),
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
                // Pause at end
                delay(1500)
                // Slowly scroll back
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = (scrollDistance * 20).toInt().coerceIn(2000, 8000),
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .clipToBounds()
            .graphicsLayer { translationX = offsetX.value }
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { textLayoutResult ->
                textWidth = textLayoutResult.size.width.toFloat()
            },
        )
    }
}

// ── Device card ───────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    onStream: () -> Unit,
    onClickInfo: () -> Unit,
    onNavigateToDetail: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    refreshKey: Int = 0,
    setPairingLoading: (Boolean) -> Unit = {},
    onComputerRemoved: ((String) -> Unit)? = null,
    onNavigateToAppList: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isOnline = computer.state == ComputerDetails.State.ONLINE
    val isUnknown = computer.state == ComputerDetails.State.UNKNOWN
    val isPaired = computer.pairState == PairingManager.PairState.PAIRED
    val isPairedUnknown = isUnknown && isPaired

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // ── Thumbnail area (left, clickable) ──
            // 已配对 → 直接串流；未配对 → 弹出配对 PIN 码
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clickable(enabled = isOnline) {
                        if (isPaired) {
                            onStream()
                        } else {
                            handleMenuAction(
                                actionId = "pair",
                                computer = computer,
                                context = context,
                                managerBinder = managerBinder,
                                snackbarHostState = snackbarHostState,
                                scope = scope,
                                onNavigateToDetail = onNavigateToDetail,
                                setLoading = setPairingLoading,
                                onComputerRemoved = onComputerRemoved,
                                onNavigateToAppList = onNavigateToAppList,
                            )
                        }
                    },
            ) {
                // Box art from cache (or placeholder)
                DeviceBoxArt(
                    uuid = computer.uuid,
                    isOnline = isOnline,
                    modifier = Modifier.fillMaxSize(),
                    refreshKey = refreshKey,
                )

                // Status badge (bottom-start) – shown for known states or paired+unknown
                if (!isUnknown || isPairedUnknown) {
                    val badgeColor = if (isOnline) statusOnline else statusOffline
                    val badgeText  = if (isOnline) context.getString(R.string.pcview_menu_header_online) else context.getString(R.string.pcview_menu_header_offline)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                badgeColor.copy(alpha = 0.85f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }

                // Grayscale overlay for offline
                if (!isOnline) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                    )
                }

                // Loading dots for unpaired UNKNOWN (marquee animation)
                if (isUnknown && !isPairedUnknown) {
                    val infiniteTransition = rememberInfiniteTransition(label = "loading")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        repeat(3) { index ->
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, delayMillis = index * 200),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "dot$index",
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .graphicsLayer { this.alpha = alpha }
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(50),
                                    ),
                            )
                        }
                    }
                }
            }

            // ── Info area (right, clickable) ──
            // 已配对 → 进入概要页；未配对 → 弹出配对 PIN 码
            Row(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = {
                            if (isPaired) {
                                onClickInfo()
                            } else {
                                handleMenuAction(
                                    actionId = "pair",
                                    computer = computer,
                                    context = context,
                                    managerBinder = managerBinder,
                                    snackbarHostState = snackbarHostState,
                                    scope = scope,
                                    onNavigateToDetail = onNavigateToDetail,
                                    setLoading = setPairingLoading,
                                    onComputerRemoved = onComputerRemoved,
                                    onNavigateToAppList = onNavigateToAppList,
                                )
                            }
                        },
                        onLongClick = { showMenu = true },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    MarqueeText(
                        text = computer.name ?: context.getString(R.string.device_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // OS type icon
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = context.getString(R.string.cd_windows),
                            modifier = Modifier.size(16.dp),
                            tint = windowsBlue.copy(alpha = if (isOnline) 1f else 0.5f),
                        )

                        // Running game indicator
                        if (computer.runningGameId != 0) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = context.getString(R.string.cd_running),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                context.getString(R.string.cd_running),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Address info
                    computer.activeAddress?.let { addr ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = addr.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = context.getString(R.string.subpanel_title_more),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ── Context menu ───────────────────────────────────
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        // ── Header: 主机名 - 在线/离线 ────────────────
        val statusText = if (isOnline) context.getString(R.string.pcview_menu_header_online) else context.getString(R.string.pcview_menu_header_offline)
        val headerColor = if (isOnline) statusOnline else Color.Black
        DropdownMenuItem(
            text = {
                Text(
                    text = "${computer.name ?: context.getString(R.string.device_unknown)} - $statusText",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = headerColor,
                )
            },
            onClick = { showMenu = false },   // 仅点击关闭菜单
            enabled = false,                   // 不可点击
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        val visibleActions = ALL_MENU_ACTIONS.filter { it.isVisible(computer) }
        visibleActions.forEach { action ->
            DropdownMenuItem(
                text = { Text(context.getString(action.labelResId)) },
                onClick = {
                    showMenu = false
                    handleMenuAction(
                        actionId = action.id,
                        computer = computer,
                        context = context,
                        managerBinder = managerBinder,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                        onNavigateToDetail = onNavigateToDetail,
                        setLoading = setPairingLoading,
                        onComputerRemoved = onComputerRemoved,
                        onNavigateToAppList = onNavigateToAppList,
                    )
                },
            )
        }
    }
}

// TODO: 2.0 版本恢复
// private fun showConfirmDialog(
//     activity: android.app.Activity,
//     titleRes: Int,
//     messageRes: Int,
//     onConfirm: () -> Unit,
// ) {
//     android.app.AlertDialog.Builder(activity)
//         .setTitle(activity.getString(titleRes))
//         .setMessage(activity.getString(messageRes))
//         .setPositiveButton(activity.getString(R.string.dialog_button_confirm)) { _, _ ->
//             onConfirm()
//         }
//         .setNegativeButton(activity.getString(R.string.editor_cancel), null)
//         .show()
// }

// ── Menu action handler ───────────────────────────────────────────

private fun handleMenuAction(
    actionId: String,
    computer: ComputerDetails,
    context: android.content.Context,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigateToDetail: () -> Unit,
    setLoading: (Boolean) -> Unit = {},
    onComputerRemoved: ((String) -> Unit)? = null,
    onNavigateToAppList: ((String) -> Unit)? = null,
) {
    val activity = context as? android.app.Activity ?: return

    when (actionId) {
        "delete" -> {
            managerBinder?.removeComputer(computer)
            computer.uuid?.let { onComputerRemoved?.invoke(it) }
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.toast_device_deleted, computer.name))
            }
        }
        "wol" -> {
            scope.launch(Dispatchers.IO) {
                try {
                    WakeOnLanSender.sendWolPacket(computer)
                    with(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(context.getString(R.string.toast_wol_sent))
                    }
                } catch (e: Exception) {
                    with(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(context.getString(R.string.toast_wol_failed, e.message ?: ""))
                    }
                }
            }
        }
        // TODO: 2.0 版本恢复 — 睡眠
        // "sleep" -> {
        //     if (managerBinder != null) {
        //         showConfirmDialog(activity, R.string.action_sleep, R.string.action_sleep_confirm) {
        //             ServerHelper.pcSleep(activity, computer, managerBinder, null)
        //         }
        //     }
        // }
        // TODO: 2.0 版本恢复 — 重启
        // "restart" -> {
        //     if (managerBinder != null) {
        //         showConfirmDialog(activity, R.string.action_restart, R.string.action_restart_confirm) {
        //             ServerHelper.pcRestart(activity, computer, managerBinder, null)
        //         }
        //     }
        // }
        "quit" -> {
            if (managerBinder != null) {
                val runningApp = NvApp().apply {
                    appId = computer.runningGameId
                    appName = "Running App"
                }
                ServerHelper.doQuit(activity, computer, runningApp, managerBinder, null)
            }
        }
        "detail" -> {
            onNavigateToDetail()
        }
        "pair" -> {
            doPair(activity, computer, managerBinder, snackbarHostState, scope, setLoading)
        }
        "resume" -> {
            launchStream(context, computer, managerBinder, forceResume = true)
        }
        "applist" -> {
            // Navigate to Compose AppListScreen
            val uuid = computer.uuid ?: return
            onNavigateToAppList?.invoke(uuid)
        }
        "webui" -> {
            val addr = computer.activeAddress?.address ?: return
            val url = "https://$addr:${computer.httpsPort}"
            val intent = android.content.Intent(context, com.alexclin.moonlink.android.device.SunshineWebUiActivity::class.java).apply {
                putExtra(com.alexclin.moonlink.android.device.SunshineWebUiActivity.EXTRA_URL, url)
                putExtra(com.alexclin.moonlink.android.device.SunshineWebUiActivity.EXTRA_TITLE, computer.name ?: "Sunshine")
                computer.serverCert?.let { putExtra(com.alexclin.moonlink.android.device.SunshineWebUiActivity.EXTRA_SERVER_CERT, it.encoded) }
            }
            context.startActivity(intent)
        }
        "nettest" -> {
            ServerHelper.doNetworkTest(activity)
        }
        "secondary_screen" -> {
            computer.useVdd = true
            launchStream(context, computer, managerBinder, vddScreenMode = 2)
        }
        "iperf" -> {
            try {
                val ip = ServerHelper.getCurrentAddressFromComputer(computer).address
                com.alexclin.moonlink.android.util.Iperf3Tester(activity, ip).show()
            } catch (e: java.io.IOException) {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.unable_to_get_pc_address, e.message ?: ""))
                }
            }
        }
        "disable_ipv6" -> {
            computer.ipv6Disabled = !computer.ipv6Disabled
            managerBinder?.updateComputer(computer)
            scope.launch {
                val msgRes = if (computer.ipv6Disabled) R.string.pcview_ipv6_disabled else R.string.pcview_ipv6_enabled
                snackbarHostState.showSnackbar("${context.getString(msgRes)}: ${computer.name}")
            }
        }
        "gs_eol" -> {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/moonlight-stream/moonlight-android/wiki/GameStream-EOL"))
            context.startActivity(intent)
        }
    }
}

// ── Stream launcher ───────────────────────────────────────────────

private fun launchStream(
    context: android.content.Context,
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    forceResume: Boolean = false,
    vddScreenMode: Int = -1,
) {
    if (managerBinder == null) return
    val activity = context as? android.app.Activity ?: return

    // ── PiP 模式检测 ────────────────────────────────────
    if (StreamEngine.currentPipActivity != null && !StreamEngine.currentPipActivity!!.isFinishing) {
        val pipUuid = StreamEngine.currentPipUuid
        if (pipUuid != null && pipUuid == computer.uuid) {
            // 同一设备：关闭 PiP Activity（它会清理流），然后启动新流
            ToastUtil.show(context, context.getString(R.string.toast_from_pip_resume), Toast.LENGTH_SHORT)
            StreamEngine.currentPipActivity!!.finish()
            // Activity finish 后继续执行下面的正常启动逻辑
        } else {
            // 不同设备：关闭 PiP Activity 停止旧流，然后启动新流
            ToastUtil.show(context, context.getString(R.string.toast_stopped_other_pip), Toast.LENGTH_SHORT)
            StreamEngine.currentPipActivity!!.finish()
        }
    }

    if (computer.state != ComputerDetails.State.ONLINE) {
        ToastUtil.show(context, context.getString(R.string.toast_device_offline_waking), Toast.LENGTH_SHORT)
        try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) {}
        return
    }

    // Prepare a copy with address resolved (matches PcView.prepareComputerWithAddress)
    val target = ComputerDetails(computer)
    if (target.activeAddress == null) {
        target.activeAddress = target.selectBestAddress()
    }
    if (target.activeAddress == null) {
        ToastUtil.show(context, context.getString(R.string.toast_device_address_unavailable), Toast.LENGTH_SHORT)
        return
    }

    val desktopApp: NvApp
    if (computer.supportsDesktopSpecialApp) {
        desktopApp = NvApp(NvApp.DESKTOP_APP_NAME, NvApp.DESKTOP_APP_ID, false)
    } else {
        var cachedApps = loadCachedAppList(context, computer.uuid)
        if (cachedApps.isEmpty() && managerBinder != null) {
            // 缓存为空时尝试从主机拉取（不下载 box art 以节省时间）
            try {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    fetchAndCacheAppListAndBoxArt(context, target, managerBinder)
                }
            } catch (_: Exception) { /* 拉取失败不阻塞 */ }
            cachedApps = loadCachedAppList(context, computer.uuid)
        }
        if (cachedApps.isEmpty()) {
            ToastUtil.show(
                context,
                context.getString(R.string.toast_no_launchable_app),
                Toast.LENGTH_SHORT
            )
            return
        }
        desktopApp = cachedApps.firstOrNull { it.appName.equals("Desktop", ignoreCase = true) }
            ?: cachedApps.first()
    }

    // ── 跳转新版 StreamActivity ──
    val intent = Intent(context, StreamActivity::class.java).apply {
        putExtra("Host", target.activeAddress?.address)
        putExtra("Port", target.activeAddress?.port ?: 0)
        putExtra("HttpsPort", target.httpsPort)
        putExtra("AppName", desktopApp.appName)
        putExtra("AppId", desktopApp.appId)
        putExtra("HDR", desktopApp.isHdrSupported())
        putExtra("UUID", target.uuid)
        putExtra("PcName", target.name)
        putExtra("UniqueId", managerBinder.getUniqueId())
        desktopApp.cmdList?.let { putExtra("CmdList", it.toString()) }
        target.serverCert?.let { putExtra("ServerCert", it.encoded) }
        putExtra("ForceResumeCurrentSession", forceResume)
        putExtra("PairName", target.getPairName(context))
        // 仅显式副屏串流路径才设 usevdd=true，普通点击始终为 false
        putExtra("usevdd", vddScreenMode != -1)
        if (vddScreenMode != -1) {
            putExtra("VDD screen combination mode", vddScreenMode)
        }
    }
    context.startActivity(intent)
}

// ── Pairing ───────────────────────────────────────────────────────

private fun doPair(
    activity: android.app.Activity,
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    setLoading: (Boolean) -> Unit = {},
) {
    if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
        ToastUtil.show(activity, activity.getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT)
        return
    }
    if (managerBinder == null) return

    setLoading(true)

    scope.launch {
        var pinDialog: android.app.AlertDialog? = null

        try {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val httpConn = NvHTTP(
                    ServerHelper.getCurrentAddressFromComputer(computer),
                    computer.httpsPort,
                    managerBinder.getUniqueId(),
                    android.os.Build.MODEL,
                    computer.serverCert,
                    PlatformBinding.getCryptoProvider(activity),
                )

                if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                    return@withContext "already_paired"
                }

                val pin = PairingManager.generatePinString()

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val dlg = android.app.AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.pair_pairing_title))
                        .setMessage(activity.getString(R.string.pair_pairing_msg) + "\n\n$pin")
                        .setCancelable(false)
                        .setPositiveButton(activity.getString(R.string.ok), null)
                        .create()
                    pinDialog = dlg
                    dlg.show()
                }

                val pm = httpConn.pairingManager
                val pairResult = pm.pair(httpConn.getServerInfo(true), pin)

                // Dismiss the PIN dialog after pairing attempt
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    pinDialog?.dismiss()
                    pinDialog = null
                }

                when (pairResult.state) {
                    PairingManager.PairState.PAIRED -> {
                        managerBinder.getComputer(computer.uuid!!)?.let { c ->
                            c.serverCert = pm.pairedCert
                            c.pairState = PairingManager.PairState.PAIRED
                        }
                        "success:${pairResult.pairName}"
                    }
                    PairingManager.PairState.PIN_WRONG -> "pin_wrong"
                    PairingManager.PairState.FAILED -> {
                        if (computer.runningGameId != 0) "failed_in_game"
                        else "failed"
                    }
                    PairingManager.PairState.ALREADY_IN_PROGRESS -> "in_progress"
                    else -> "unknown"
                }
            }

            val msg = when {
                result == "already_paired" -> activity.getString(R.string.toast_pair_already_paired)
                result.startsWith("success") -> {
                    val pairName = result.substringAfter("success:", "")
                    if (pairName.isNotEmpty()) {
                        activity.getSharedPreferences("pair_name_map", android.content.Context.MODE_PRIVATE)
                            .edit().putString(computer.uuid, pairName).apply()
                    }
                    managerBinder.invalidateStateForComputer(computer.uuid!!)
                    activity.getString(R.string.toast_pair_success)
                }
                result == "pin_wrong" -> activity.getString(R.string.pair_incorrect_pin)
                result == "failed_in_game" -> activity.getString(R.string.pair_pc_ingame)
                result == "failed" -> activity.getString(R.string.pair_fail)
                result == "in_progress" -> activity.getString(R.string.pair_already_in_progress)
                else -> activity.getString(R.string.pair_fail)
            }

            with(Dispatchers.Main) {
                snackbarHostState.showSnackbar(msg)
            }
        } catch (e: Exception) {
            pinDialog?.dismiss()
            with(Dispatchers.Main) {
                snackbarHostState.showSnackbar(activity.getString(R.string.toast_pair_error, e.message ?: ""))
            }
        } finally {
            setLoading(false)
        }
    }
}
