package com.alexclin.moonlink.android.device.overview

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.theme.windowsBlue
import com.alexclin.moonlink.android.home.DeviceBoxArt
import com.alexclin.moonlink.android.theme.statusOffline
import com.alexclin.moonlink.android.theme.statusOnline
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.alexclin.moonlink.android.home.fetchAndCacheAppListAndBoxArt
import com.alexclin.moonlink.android.stream.StreamActivity
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.limelight.AppView
import com.limelight.SunshineWebUiActivity
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.binding.PlatformBinding
import com.limelight.utils.Iperf3Tester
import com.limelight.utils.AppSettingsManager
import com.limelight.utils.ServerHelper
import com.limelight.nvstream.wol.WakeOnLanSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeviceOverviewScreen(
    uuid: String,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    computers: List<ComputerDetails>,
    onBack: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onNavigateToStreamSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val computer = findComputer(computers, uuid)
    var appList by remember(uuid) { mutableStateOf(loadCachedAppList(context, uuid)) }

    // ── 主动拉取 app list & box art ─────────────────────
    // 如果缓存中没有 app list 且设备在线+已配对，则异步从主机拉取并缓存。
    // 这解决了快速启动区不展示（Bug 3）以及 box art 缩略图无法加载（Bug 2）的问题。
    LaunchedEffect(uuid, computer?.state, computer?.pairState) {
        if (computer != null && appList.isEmpty() &&
            computer.state == ComputerDetails.State.ONLINE &&
            computer.pairState == PairingManager.PairState.PAIRED &&
            managerBinder != null
        ) {
            val fetched = fetchAndCacheAppListAndBoxArt(context, computer, managerBinder)
            if (fetched != null && fetched.isNotEmpty()) {
                appList = fetched
            }
        }
    }

    // Quick actions dialog (merged with former MoreActions)
    var showQuickActions by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // ── 横屏检测 ──────────────────────────────────────
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // ── 未配对设备直接退出 ────────────────────────────
    if (computer != null && computer.pairState != PairingManager.PairState.PAIRED) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    if (computer == null) {
        // Device not found
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("设备未找到", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("返回") }
            }
        }
        return
    }

    val isOnline = computer.state == ComputerDetails.State.ONLINE
    val isPaired = computer.pairState == PairingManager.PairState.PAIRED
    val canShowActions = isOnline && isPaired
    val showQuickActionButton = isPaired // 离线时也展示快捷操作按钮，但仅隐藏部分选项

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        if (isLandscape) {
            // ── 横屏：全宽标题栏 + 下方左右两栏 ──────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(paddingValues),
            ) {
                // ── 内容区 ───────────────────────────────
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    // ── 左侧：桌面缩略图 + 操作按钮 (50%) ──
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 16.dp, end = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // ── 桌面缩略图 (自适应剩余高度，无内边距，仅顶部圆角) ────────
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = isOnline) {
                                        val forceResume = computer.runningGameId != 0
                                        launchStreamFromOverview(context, computer, managerBinder, forceResume = forceResume)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                // Desktop box art
                                DeviceBoxArt(
                                    uuid = computer.uuid,
                                    isOnline = isOnline,
                                    modifier = Modifier.fillMaxSize(),
                                    clipShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                )

                                // Status badge (top-start)
                                val badgeColor = if (isOnline) statusOnline else statusOffline
                                val badgeText  = if (isOnline) "在线" else "离线"
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
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

                                // OS icon + IP at top-center
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.DesktopWindows,
                                        contentDescription = "Windows",
                                        modifier = Modifier.size(20.dp),
                                        tint = windowsBlue,
                                    )
                                    computer.activeAddress?.let { addr ->
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = addr.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.9f),
                                            maxLines = 1,
                                        )
                                    }
                                }

                                // Bottom overlay: "进入桌面 >"（仅在线时显示）
                                if (isOnline) {
                                    Text(
                                        "进入桌面 >",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 16.dp),
                                    )
                                }

                                // Offline overlay
                                if (!isOnline) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                    )
                                }
                            }

                            // ── 操作按钮行 ─────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                if (showQuickActionButton) {
                                    OverviewActionButton(
                                        icon = Icons.Default.Bolt,
                                        label = "快捷操作",
                                        modifier = Modifier.weight(1f),
                                        onClick = { showQuickActions = true },
                                    )
                                }
                                VerticalDivider(modifier = Modifier.height(48.dp))
                                OverviewActionButton(
                                    icon = Icons.Default.Info,
                                    label = "设备详情",
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToDetail,
                                )
                                VerticalDivider(modifier = Modifier.height(48.dp))
                                OverviewActionButton(
                                    icon = Icons.Default.Settings,
                                    label = "串流设置",
                                    modifier = Modifier.weight(1f),
                                    onClick = onNavigateToStreamSettings,
                                )
                            }
                        }
                    }

                    // ── 右侧：快速启动 (50%) ─────────────────
                    if (appList.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                "快速启动",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(12.dp))

                            // App grid — 4 columns
                            val columns = 4
                            val visibleApps = appList.filter {
                                it.appId != NvApp.DESKTOP_APP_ID
                            }

                            Column {
                                visibleApps.chunked(columns).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                    ) {
                                        row.forEach { app ->
                                            AppIconItem(
                                                app = app,
                                                uuid = uuid,
                                                isRunning = computer.runningGameId != 0 && computer.runningGameId == app.appId,
                                                onClick = {
                                                    launchStreamFromOverview(context, computer, managerBinder, app = app)
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat(columns - row.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 无快速启动时显示提示
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "暂无应用，请确保设备在线并已配对",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            // ── 竖屏：传统纵向布局 ────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(paddingValues)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Main card ─────────────────────────────
            Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column {
                        // ── Thumbnail area ─────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = isOnline) {
                                    val forceResume = computer.runningGameId != 0
                                    launchStreamFromOverview(context, computer, managerBinder, forceResume = forceResume)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Desktop box art
                            DeviceBoxArt(
                                uuid = computer.uuid,
                                isOnline = isOnline,
                                modifier = Modifier.fillMaxSize(),
                                clipShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            )

                            // Status badge (bottom-start)
                            val badgeColor = if (isOnline) statusOnline else statusOffline
                            val badgeText  = if (isOnline) "在线" else "离线"
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
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

                            // Loading dots for unpaired UNKNOWN (marquee animation)
                            val isUnknown = computer.state == ComputerDetails.State.UNKNOWN
                            val isPairedUnknown = computer.pairState == PairingManager.PairState.PAIRED
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
                                                .size(8.dp)
                                                .graphicsLayer { this.alpha = alpha }
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(50),
                                                ),
                                        )
                                    }
                                }
                            }

                            // OS icon + IP at top-start area
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.DesktopWindows,
                                    contentDescription = "Windows",
                                    modifier = Modifier.size(20.dp),
                                    tint = windowsBlue,
                                )
                                computer.activeAddress?.let { addr ->
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = addr.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 1,
                                    )
                                }
                            }

                            // Bottom overlay: "进入桌面 >"（仅在线时显示）
                            if (isOnline) {
                                Text(
                                    "进入桌面 >",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp),
                                )
                            }

                            // Offline overlay
                            if (!isOnline) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                )
                            }
                        }

                        // ── Button row ─────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (showQuickActionButton) {
                                OverviewActionButton(
                                    icon = Icons.Default.Bolt,
                                    label = "快捷操作",
                                    modifier = Modifier.weight(1f),
                                    onClick = { showQuickActions = true },
                                )
                            }
                            VerticalDivider(modifier = Modifier.height(56.dp))
                            OverviewActionButton(
                                icon = Icons.Default.Info,
                                label = "设备详情",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToDetail,
                            )
                            VerticalDivider(modifier = Modifier.height(56.dp))
                            OverviewActionButton(
                                icon = Icons.Default.Settings,
                                label = "串流设置",
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToStreamSettings,
                            )
                        }
                    }
                }

                // ── Quick launch section ──────────────────────
                if (canShowActions && appList.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "快速启动",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))

                    // App grid — 4 columns
                    val columns = 4
                    val visibleApps = appList.filter {
                        it.appId != NvApp.DESKTOP_APP_ID // Exclude Desktop from grid
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        visibleApps.chunked(columns).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                row.forEach { app ->
                                    AppIconItem(
                                        app = app,
                                        uuid = uuid,
                                        isRunning = computer.runningGameId != 0 && computer.runningGameId == app.appId,
                                        onClick = {
                                            launchStreamFromOverview(context, computer, managerBinder, app = app)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                // Fill remaining slots
                                repeat(columns - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ── Quick Actions Dialog ────────────────────────────
    if (showQuickActions) {
        QuickActionsDialog(
            computer = computer,
            managerBinder = managerBinder,
            snackbarHostState = snackbarHostState,
            scope = scope,
            onDismiss = { showQuickActions = false },
        )
    }

    // ── Note: former MoreActions items are merged into QuickActionsDialog ──
}

// ── Action button in the card's bottom row ────────────────────────

@Composable
private fun OverviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── App icon loader ─────────────────────────────────────────────────

/** Memory cache for app icons: <uuid_appId> → Bitmap */
private val iconCache = object : LruCache<String, Bitmap>(100) {
    override fun sizeOf(key: String, value: Bitmap): Int = 1
}

@Composable
private fun AppIconImage(
    appId: Int,
    uuid: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(appId, uuid) { mutableStateOf<Bitmap?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(appId, uuid) {
        withContext(Dispatchers.IO) {
            val cacheKey = "${uuid}_$appId"
            val cached = synchronized(iconCache) { iconCache.get(cacheKey) }
            if (cached != null) {
                bitmap = cached
                isLoaded = true
                return@withContext
            }
            val file = java.io.File(context.cacheDir, "boxart/$uuid/$appId.png")
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    synchronized(iconCache) { iconCache.put(cacheKey, bmp) }
                    bitmap = bmp
                    isLoaded = true
                }
            }
        }
    }

    if (isLoaded && bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            Icons.Default.Apps,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

// ── App grid item ─────────────────────────────────────────────────

@Composable
private fun AppIconItem(
    app: NvApp,
    uuid: String,
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // App icon — loaded from disk cache with fallback
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AppIconImage(
                    appId = app.appId,
                    uuid = uuid,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isRunning) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "运行中",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.appName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ── Quick Actions Dialog ──────────────────────────────────────────

@Composable
private fun QuickActionsDialog(
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val isOnline = computer.state == ComputerDetails.State.ONLINE
    val hasRunningGame = computer.runningGameId != 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "快捷操作",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                if (isOnline) {
                    DialogActionRow("重启") {
                    if (activity != null && managerBinder != null) {
                        scope.launch {
                            try {
                                val address = ServerHelper.getCurrentAddressFromComputer(computer)
                                val httpConn = NvHTTP(
                                    address,
                                    computer.httpsPort,
                                    managerBinder.getUniqueId(),
                                    android.os.Build.MODEL,
                                    computer.serverCert,
                                    PlatformBinding.getCryptoProvider(context)
                                )
                                withContext(Dispatchers.IO) {
                                    val success = httpConn.pcRestart()
                                    withContext(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar(
                                            if (success) "重启命令已发送" else "重启失败"
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("重启异常: ${e.message}")
                            }
                        }
                    }
                    onDismiss()
                }
                }
                if (isOnline) {
                    DialogActionRow("关机") {
                    if (activity != null && managerBinder != null) {
                        scope.launch {
                            try {
                                val address = ServerHelper.getCurrentAddressFromComputer(computer)
                                val httpConn = NvHTTP(
                                    address,
                                    computer.httpsPort,
                                    managerBinder.getUniqueId(),
                                    android.os.Build.MODEL,
                                    computer.serverCert,
                                    PlatformBinding.getCryptoProvider(context)
                                )
                                withContext(Dispatchers.IO) {
                                    val success = httpConn.pcShutdown()
                                    withContext(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar(
                                            if (success) "关机命令已发送" else "关机失败"
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("关机异常: ${e.message}")
                            }
                        }
                    }
                    onDismiss()
                }
                }
                if (isOnline) {
                    DialogActionRow("睡眠") {
                    if (activity != null && managerBinder != null) {
                        ServerHelper.pcSleep(activity, computer, managerBinder, null)
                    }
                    onDismiss()
                }
                }
                if (isOnline) {
                    DialogActionRow("打开 Web 管理（Sunshine）") {
                    val addr = computer.activeAddress?.address
                    val url = if (addr != null) "https://$addr:${computer.httpsPort}" else ""
                    val intent = Intent(context, SunshineWebUiActivity::class.java).apply {
                        putExtra(SunshineWebUiActivity.EXTRA_URL, url)
                        putExtra(SunshineWebUiActivity.EXTRA_TITLE, computer.name ?: "Sunshine")
                        computer.serverCert?.let { putExtra(SunshineWebUiActivity.EXTRA_SERVER_CERT, it.encoded) }
                    }
                    context.startActivity(intent)
                    onDismiss()
                }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                if (!isOnline) {
                    DialogActionRow("发送 Wake-On-LAN 请求") {
                        scope.launch(Dispatchers.IO) {
                            try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) { /* offline expected */ }
                        }
                        onDismiss()
                    }
                }
                DialogActionRow("删除电脑") {
                    managerBinder?.removeComputer(computer)
                    onDismiss()
                }
                if (isOnline && hasRunningGame) {
                    DialogActionRow("退出应用") {
                        if (context is android.app.Activity && managerBinder != null) {
                            val app = NvApp().apply { appId = computer.runningGameId; appName = "Running" }
                            ServerHelper.doQuit(context, computer, app, managerBinder, null)
                        }
                        onDismiss()
                    }
                }
                DialogActionRow("网络带宽测试 (iPerf3)") {
                    val iperfActivity = context as? android.app.Activity
                    if (iperfActivity != null) {
                        try {
                            val ip = ServerHelper.getCurrentAddressFromComputer(computer).address
                            Iperf3Tester(iperfActivity, ip).show()
                        } catch (e: java.io.IOException) {
                            Toast.makeText(context, "设备地址不可用，请确认设备在线", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "无法获取设备地址", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
                DialogActionRow("禁用 IPv6") {
                    computer.ipv6Disabled = !computer.ipv6Disabled
                    managerBinder?.updateComputer(computer)
                    onDismiss()
                }
                DialogActionRow("测试网络连接") {
                    if (context is android.app.Activity) {
                        ServerHelper.doNetworkTest(context)
                    }
                    onDismiss()
                }
                if (computer.nvidiaServer) {
                    DialogActionRow("NVIDIA GameStream 终止服务") {
                        val intent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/moonlight-stream/moonlight-android/wiki/GameStream-EOL"))
                        context.startActivity(intent)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogActionRow(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Stream launcher (overview page) ───────────────────────────────

/**
 * Gets the default app for quick-start (thumbnail click with no specific app).
 * Uses DesktopSpecialApp if supported, otherwise falls back to cached app list
 * and prefers "Desktop" app (case-insensitive) or the first available app.
 */
private fun getDefaultQuickStartApp(computer: ComputerDetails, context: Context): NvApp? {
    if (computer.supportsDesktopSpecialApp) {
        return NvApp(NvApp.DESKTOP_APP_NAME, NvApp.DESKTOP_APP_ID, false)
    }

    val appList = loadCachedAppList(context, computer.uuid)
    if (appList.isEmpty()) return null

    // Prefer "Desktop" app if present, otherwise use first available
    return appList.find { it.appName.equals("Desktop", ignoreCase = true) } ?: appList.first()
}

private fun launchStreamFromOverview(
    context: Context,
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    app: NvApp? = null,
    forceResume: Boolean = false,
) {
    if (managerBinder == null) return
    val activity = context as? android.app.Activity ?: return

    // ── PiP 模式检测 ────────────────────────────────────
    if (StreamEngine.currentPipActivity != null && !StreamEngine.currentPipActivity!!.isFinishing) {
        val pipUuid = StreamEngine.currentPipUuid
        if (pipUuid != null && pipUuid == computer.uuid) {
            // 同一设备：关闭 PiP Activity，然后启动新流
            Toast.makeText(context, "从画中画恢复串流…", Toast.LENGTH_SHORT).show()
            StreamEngine.currentPipActivity!!.finish()
        } else {
            // 不同设备：关闭 PiP Activity 停止旧流
            Toast.makeText(context, "已停止另一台设备的画中画串流…", Toast.LENGTH_SHORT).show()
            StreamEngine.currentPipActivity!!.finish()
        }
    }

    if (computer.state != ComputerDetails.State.ONLINE) {
        Toast.makeText(context, "设备离线，正在尝试唤醒…", Toast.LENGTH_SHORT).show()
        try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) { /* offline expected */ }
        return
    }

    val targetApp = app ?: getDefaultQuickStartApp(computer, context) ?: run {
        Toast.makeText(context, "无可启动的应用，请先打开应用列表加载", Toast.LENGTH_SHORT).show()
        return
    }

    // ── 使用新版 StreamActivity ──
    // useLastSettings 标志传递给 StreamActivity，由 StreamEngine 在初始化时应用
    val useLastSettings = AppSettingsManager(context).isUseLastSettingsEnabled
    val intent = createStreamIntent(context, computer, targetApp, managerBinder, useLastSettings, forceResume)
    context.startActivity(intent)
}

/**
 * 构造跳转新版 [StreamActivity] 的 Intent。
 *
 * Extras 键名与旧版 [com.limelight.Game] 保持一致。
 */
private fun createStreamIntent(
    context: Context,
    computer: ComputerDetails,
    app: NvApp,
    managerBinder: ComputerManagerService.ComputerManagerBinder,
    useLastSettings: Boolean,
    forceResume: Boolean = false,
): Intent {
    return Intent(context, StreamActivity::class.java).apply {
        putExtra("Host", computer.activeAddress?.address)
        putExtra("Port", computer.activeAddress?.port ?: 0)
        putExtra("HttpsPort", computer.httpsPort)
        putExtra("AppName", app.appName)
        putExtra("AppId", app.appId)
        putExtra("HDR", app.isHdrSupported())
        putExtra("UUID", computer.uuid)
        putExtra("PcName", computer.name)
        putExtra("UniqueId", managerBinder.getUniqueId())
        app.cmdList?.let { putExtra("CmdList", it.toString()) }
        computer.serverCert?.let { putExtra("ServerCert", it.encoded) }
        putExtra("ForceResumeCurrentSession", forceResume)
        putExtra("PairName", computer.getPairName(context))
        putExtra("usevdd", computer.useVdd)
        if (useLastSettings) {
            val uuid = computer.uuid
            if (uuid != null) {
                val settingsManager = AppSettingsManager(context)
                val lastSettings = settingsManager.getAppLastSettings(uuid, app)
                if (lastSettings != null) {
                    AppSettingsManager.addLastSettingsToIntent(this, lastSettings)
                }
            }
        }
    }
}
