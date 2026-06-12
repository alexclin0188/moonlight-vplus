package com.alexclin.moonlink.device.overview

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.theme.macosGray
import com.alexclin.moonlink.theme.windowsBlue
import com.alexclin.moonlink.home.DeviceBoxArt
import com.alexclin.moonlink.theme.statusOffline
import com.alexclin.moonlink.theme.statusOnline
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.limelight.AppView
import com.limelight.SunshineWebUiActivity
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.utils.Iperf3Tester
import com.limelight.utils.AppSettingsManager
import com.limelight.utils.ServerHelper
import com.limelight.nvstream.wol.WakeOnLanSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val computer = findComputer(computers, uuid)
    val appList  = remember(uuid) { loadCachedAppList(context, uuid) }
    val appSettingsManager = remember { AppSettingsManager(context) }
    var useLastSettings by remember { mutableStateOf(appSettingsManager.isUseLastSettingsEnabled) }

    // Quick actions dialog
    var showQuickActions by remember { mutableStateOf(false) }
    // More actions dialog
    var showMoreActions by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                                launchStreamFromOverview(context, computer, managerBinder, useLastSettings, appSettingsManager)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Desktop box art
                        DeviceBoxArt(
                            uuid = computer.uuid,
                            isOnline = isOnline,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Status badge (bottom-start)
                        val badgeColor = if (isOnline) statusOnline else statusOffline
                        val badgeText  = if (isOnline) "在线" else "离线"
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
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

                        // OS icon at top-center
                        val isMac = computer.name?.lowercase()?.let {
                            it.contains("mac") || it.contains("darwin")
                        } ?: false
                        Icon(
                            if (isMac) Icons.Default.LaptopMac else Icons.Default.DesktopWindows,
                            contentDescription = if (isMac) "macOS" else "Windows",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .size(28.dp),
                            tint = if (isMac) macosGray else windowsBlue,
                        )

                        // Bottom overlay: "进入桌面 >" + checkbox
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "进入桌面 >",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    useLastSettings = !useLastSettings
                                    appSettingsManager.setUseLastSettingsEnabled(useLastSettings)
                                },
                            ) {
                                Checkbox(
                                    checked = useLastSettings,
                                    onCheckedChange = {
                                        useLastSettings = it
                                        appSettingsManager.setUseLastSettingsEnabled(it)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "以最近一次配置启动",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                )
                            }
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
                        OverviewActionButton(
                            icon = Icons.Default.Bolt,
                            label = "快捷操作",
                            modifier = Modifier.weight(1f),
                            onClick = { showQuickActions = true },
                        )
                        VerticalDivider(modifier = Modifier.height(56.dp))
                        OverviewActionButton(
                            icon = Icons.Default.Info,
                            label = "设备详情",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToDetail,
                        )
                        VerticalDivider(modifier = Modifier.height(56.dp))
                        OverviewActionButton(
                            icon = Icons.Default.MoreHoriz,
                            label = "更多",
                            modifier = Modifier.weight(1f),
                            onClick = { showMoreActions = true },
                        )
                    }
                }
            }

            // ── Quick launch section ──────────────────────
            if (appList.isNotEmpty()) {
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
                                        launchStreamFromOverview(context, computer, managerBinder, useLastSettings, appSettingsManager, app)
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

    // ── Quick Actions Dialog ────────────────────────────
    if (showQuickActions) {
        QuickActionsDialog(
            computer = computer,
            managerBinder = managerBinder,
            snackbarHostState = snackbarHostState,
            onDismiss = { showQuickActions = false },
        )
    }

    // ── More Actions Dialog ─────────────────────────────
    if (showMoreActions) {
        MoreActionsDialog(
            computer = computer,
            managerBinder = managerBinder,
            snackbarHostState = snackbarHostState,
            scope = scope,
            appSettingsManager = appSettingsManager,
            onDismiss = { showMoreActions = false },
            onNavigateToDetail = {
                showMoreActions = false
                onNavigateToDetail()
            },
        )
    }
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
private val iconCache = mutableMapOf<String, Bitmap>()

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
            val cached = iconCache[cacheKey]
            if (cached != null) {
                bitmap = cached
                isLoaded = true
                return@withContext
            }
            val file = java.io.File(context.cacheDir, "boxart/$uuid/$appId.png")
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    iconCache[cacheKey] = bmp
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
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快捷操作") },
        text = {
            Column {
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
                DialogActionRow("睡眠") {
                    if (activity != null && managerBinder != null) {
                        ServerHelper.pcSleep(activity, computer, managerBinder, null)
                    }
                    onDismiss()
                }
                DialogActionRow("打开 Sunshine 网页管理") {
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ── More Actions Dialog (reuses the 14-item menu) ─────────────────

@Composable
private fun MoreActionsDialog(
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    appSettingsManager: AppSettingsManager,
    onDismiss: () -> Unit,
    onNavigateToDetail: () -> Unit,
) {
    val context = LocalContext.current
    val isOnline = computer.state == ComputerDetails.State.ONLINE
    val hasRunningGame = computer.runningGameId != 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更多操作") },
        text = {
            Column {
                DialogActionRow("配对 / 取消配对") {
                    if (managerBinder != null) {
                        scope.launch(Dispatchers.IO) {
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
                                if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                                    // Already paired → unpair
                                    httpConn.unpair()
                                    val newState = httpConn.getPairState()
                                    val msg = if (newState == PairingManager.PairState.NOT_PAIRED) "已取消配对" else "取消配对失败"
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    // Not paired → pair
                                    val pin = PairingManager.generatePinString()
                                    withContext(Dispatchers.Main) {
                                        android.app.AlertDialog.Builder(context)
                                            .setTitle("配对")
                                            .setMessage("请在主机上输入以下 PIN 码:\n\n$pin\n\n（主机屏幕上会显示输入框）")
                                            .setCancelable(false)
                                            .setPositiveButton("确定", null)
                                            .show()
                                    }
                                    val pm = httpConn.pairingManager
                                    val pairResult = pm.pair(httpConn.getServerInfo(true), pin)
                                    val resultMsg = when (pairResult.state) {
                                        PairingManager.PairState.PAIRED -> "配对成功"
                                        PairingManager.PairState.PIN_WRONG -> "PIN 码错误"
                                        PairingManager.PairState.FAILED -> "配对失败"
                                        PairingManager.PairState.ALREADY_IN_PROGRESS -> "配对已在进行中"
                                        else -> "配对失败"
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, resultMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "配对异常: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    onDismiss()
                }
                if (!isOnline) {
                    DialogActionRow("发送网络唤醒 (WoL)") {
                        scope.launch(Dispatchers.IO) {
                            try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) {}
                        }
                        onDismiss()
                    }
                }
                DialogActionRow("删除设备") {
                    managerBinder?.removeComputer(computer)
                    onDismiss()
                }
                if (isOnline && hasRunningGame) {
                    DialogActionRow("恢复串流") {
                        launchStreamFromOverview(context, computer, managerBinder, false, appSettingsManager, forceResume = true)
                        onDismiss()
                    }
                    DialogActionRow("退出应用") {
                        if (context is android.app.Activity && managerBinder != null) {
                            val app = NvApp().apply { appId = computer.runningGameId; appName = "Running" }
                            ServerHelper.doQuit(context, computer, app, managerBinder, null)
                        }
                        onDismiss()
                    }
                }
                DialogActionRow("完整应用列表") {
                    val intent = Intent(context, AppView::class.java).apply {
                        putExtra(AppView.NAME_EXTRA, computer.name)
                        putExtra(AppView.UUID_EXTRA, computer.uuid)
                    }
                    context.startActivity(intent)
                    onDismiss()
                }
                DialogActionRow("查看详情") {
                    onNavigateToDetail()
                }
                if (isOnline) {
                    DialogActionRow("睡眠") {
                        if (context is android.app.Activity && managerBinder != null) {
                            ServerHelper.pcSleep(context, computer, managerBinder, null)
                        }
                        onDismiss()
                    }
                }
                DialogActionRow("副屏幕 (VDD)") {
                    computer.useVdd = true
                    launchStreamFromOverview(context, computer, managerBinder, false, appSettingsManager)
                    computer.useVdd = false
                    onDismiss()
                }
                DialogActionRow("网络测试 (iPerf3)") {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        val ip = ServerHelper.getCurrentAddressFromComputer(computer).address
                        Iperf3Tester(activity, ip).show()
                    } else {
                        Toast.makeText(context, "无法获取设备地址", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
                DialogActionRow("打开 Sunshine 网页管理") {
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
                DialogActionRow("为此设备禁用 IPv6") {
                    computer.ipv6Disabled = !computer.ipv6Disabled
                    managerBinder?.updateComputer(computer)
                    onDismiss()
                }
                DialogActionRow("网络连通测试") {
                    if (context is android.app.Activity) {
                        ServerHelper.doNetworkTest(context)
                    }
                    onDismiss()
                }
                DialogActionRow("GameStream EOL 说明") {
                    val intent = Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/moonlight-stream/moonlight-android/wiki/GameStream-EOL"))
                    context.startActivity(intent)
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
    useLastSettings: Boolean,
    appSettingsManager: AppSettingsManager,
    app: NvApp? = null,
    forceResume: Boolean = false,
) {
    if (managerBinder == null) return
    val activity = context as? android.app.Activity ?: return

    if (computer.state != ComputerDetails.State.ONLINE) {
        Toast.makeText(context, "设备离线，正在尝试唤醒…", Toast.LENGTH_SHORT).show()
        try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) {}
        return
    }

    val targetApp = app ?: getDefaultQuickStartApp(computer, context) ?: run {
        Toast.makeText(context, "无可启动的应用，请先打开应用列表加载", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = if (useLastSettings) {
        appSettingsManager.createStartIntentWithLastSettingsIfEnabled(
            activity, targetApp, computer, managerBinder,
            forceResumeCurrentSession = forceResume
        )
    } else {
        ServerHelper.createStartIntent(
            activity, targetApp, computer, managerBinder,
            forceResumeCurrentSession = forceResume
        )
    }
    context.startActivity(intent)
}
