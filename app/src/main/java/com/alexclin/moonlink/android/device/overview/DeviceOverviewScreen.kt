package com.alexclin.moonlink.android.device.overview

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.alexclin.moonlink.android.util.ToastUtil
import com.alexclin.moonlink.android.R
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexclin.moonlink.android.theme.windowsBlue
import com.alexclin.moonlink.android.home.DeviceBoxArt
import com.alexclin.moonlink.android.theme.statusOffline
import com.alexclin.moonlink.android.theme.statusOnline
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.alexclin.moonlink.android.home.fetchAndCacheAppListAndBoxArt
import com.alexclin.moonlink.android.home.getDefaultQuickStartApp
import com.alexclin.moonlink.android.home.loadCachedAppList
import com.alexclin.moonlink.android.stream.StreamActivity
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.device.SunshineWebUiActivity
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.alexclin.moonlink.android.util.PlatformBinding
import com.alexclin.moonlink.android.util.Iperf3Tester
import com.alexclin.moonlink.android.util.AppSettingsManager
import com.alexclin.moonlink.android.util.ServerHelper
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
import androidx.core.content.edit
import androidx.core.net.toUri

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
    var displaysInfo by remember(uuid) { mutableStateOf<List<NvHTTP.DisplayInfo>?>(null) }

    // ── 选中显示器会话状态（页面重建后重置，与 AppView 一致） ──
    var selectedDisplayGuid by remember(uuid) { mutableStateOf<String?>(null) }
    var selectedDisplayName by remember(uuid) { mutableStateOf<String?>(null) }
    var selectedVddEnabled by remember(uuid) { mutableStateOf(false) }

    // ── 进入概要页时立即触发该设备的重新探测 ──
    // 标记为 UNKNOWN 并重启轮询 Job，无需等待下一个轮询周期。
    LaunchedEffect(uuid) {
        managerBinder?.invalidateStateForComputer(uuid)
    }

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
            if (!fetched.isNullOrEmpty()) {
                appList = fetched
            }
        }
    }

    // ── 主动拉取 Sunshine displays 列表 ─────────────────
    // 设备在线时尝试调用 Sunshine 扩展 API /displays，
    // 成功则在桌面缩略图中间展示显示器矩形块，点击以该 displayName 启动。
    LaunchedEffect(uuid, computer?.state) {
        if (computer != null && computer.state == ComputerDetails.State.ONLINE &&
            managerBinder != null && computer.activeAddress != null && !computer.nvidiaServer
        ) {
            withContext(Dispatchers.IO) {
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
                    val displays = httpConn.getDisplays()
                    displaysInfo = displays.ifEmpty {
                        null
                    }
                } catch (_: Exception) {
                    // 调用失败则忽略，不展示矩形小方块
                    displaysInfo = null
                }
            }
        } else {
            displaysInfo = null
        }
    }

    // ── 显示器列表变化时，校验已存储的选中显示器是否仍在列表中 ──
    // 不在则清空存储（说明该显示器已被拔除或重命名）
    LaunchedEffect(displaysInfo) {
        val list = displaysInfo ?: return@LaunchedEffect
        val storedGuid = selectedDisplayGuid ?: return@LaunchedEffect
        val exists = list.any { d -> (d.guid.ifEmpty { d.name }) == storedGuid }
        if (!exists) {
            selectedDisplayGuid = null
            selectedDisplayName = null
            selectedVddEnabled = false
        }
    }

    // Quick actions dialog (merged with former MoreActions)
    var showQuickActions by remember { mutableStateOf(false) }

    val snackBarHostState = remember { SnackbarHostState() }

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
                Text(stringResource(R.string.title_device_not_found), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text(stringResource(R.string.btn_back)) }
            }
        }
        return
    }

    val isOnline = computer.state == ComputerDetails.State.ONLINE
    val isPaired = computer.pairState == PairingManager.PairState.PAIRED
    val canShowActions = isOnline && isPaired
    val showQuickActionButton = isPaired // 离线时也展示快捷操作按钮，但仅隐藏部分选项

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
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
                            DesktopThumbnailBox(
                                modifier = Modifier.weight(1f),
                                clipRadius = 12.dp,
                                showLoadingDots = false,
                                computer = computer,
                                isOnline = isOnline,
                                displaysInfo = displaysInfo,
                                selectedDisplayGuid = selectedDisplayGuid,
                                selectedVddEnabled = selectedVddEnabled,
                                managerBinder = managerBinder,
                                onDisplaySelected = { guid, name ->
                                    selectedDisplayGuid = guid
                                    selectedDisplayName = name
                                    selectedVddEnabled = false
                                    computer.useVdd = false
                                    launchStreamFromOverview(
                                        context, computer, managerBinder,
                                        forceResume = computer.runningGameId != 0,
                                        displayName = name,
                                        screenCombinationMode = -1,
                                    )
                                },
                                onVddSelected = {
                                    selectedVddEnabled = true
                                    selectedDisplayGuid = null
                                    selectedDisplayName = null
                                    computer.useVdd = true
                                    launchStreamFromOverview(
                                        context, computer, managerBinder,
                                        forceResume = computer.runningGameId != 0,
                                        vddScreenMode = 3,
                                    )
                                },
                            )

                            // ── 操作按钮行 ─────────────────────
                            DesktopActionRow(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                dividerHeight = 48.dp,
                                showQuickActionButton = showQuickActionButton,
                                onShowQuickActions = { showQuickActions = true },
                                onNavigateToDetail = onNavigateToDetail,
                                onNavigateToStreamSettings = onNavigateToStreamSettings,
                            )
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
                                stringResource(R.string.label_quick_launch),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(12.dp))

                            QuickLaunchGrid(
                                appList = appList,
                                uuid = uuid,
                                computer = computer,
                                context = context,
                                managerBinder = managerBinder,
                            )
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
                                stringResource(R.string.label_no_apps_available),
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.5f),
                    ) {
                        // ── Thumbnail area ─────────────────
                        DesktopThumbnailBox(
                            modifier = Modifier.weight(1f),
                            clipRadius = 16.dp,
                            showLoadingDots = true,
                            computer = computer,
                            isOnline = isOnline,
                            displaysInfo = displaysInfo,
                            selectedDisplayGuid = selectedDisplayGuid,
                            selectedVddEnabled = selectedVddEnabled,
                            managerBinder = managerBinder,
                            onDisplaySelected = { guid, name ->
                                selectedDisplayGuid = guid
                                selectedDisplayName = name
                                selectedVddEnabled = false
                                computer.useVdd = false
                                launchStreamFromOverview(
                                    context, computer, managerBinder,
                                    forceResume = computer.runningGameId != 0,
                                    displayName = name,
                                    screenCombinationMode = -1,
                                )
                            },
                            onVddSelected = {
                                selectedVddEnabled = true
                                    selectedDisplayGuid = null
                                    selectedDisplayName = null
                                    computer.useVdd = true
                                    launchStreamFromOverview(
                                        context, computer, managerBinder,
                                        forceResume = computer.runningGameId != 0,
                                        vddScreenMode = 3,
                                    )
                                },
                            )

                            // ── Button row ─────────────────────
                            DesktopActionRow(
                                dividerHeight = 56.dp,
                            showQuickActionButton = showQuickActionButton,
                            onShowQuickActions = { showQuickActions = true },
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToStreamSettings = onNavigateToStreamSettings,
                        )
                    }
                }

                // ── Quick launch section ──────────────────────
                if (canShowActions && appList.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.label_quick_launch),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    QuickLaunchGrid(
                        appList = appList,
                        uuid = uuid,
                        computer = computer,
                        context = context,
                        managerBinder = managerBinder,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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
            snackBarHostState = snackBarHostState,
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
                    contentDescription = stringResource(R.string.cd_running),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val displayName = app.appName.let {
            if (it.startsWith("Stream ", ignoreCase = true)) "Stream" else it
        }
        Text(
            displayName,
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
    snackBarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val isOnline = computer.state == ComputerDetails.State.ONLINE
    val hasRunningGame = computer.runningGameId != 0
    // TODO: 2.0 版本恢复 — 关机/重启确认对话框状态
    // var showRestartConfirm by remember { mutableStateOf(false) }
    // var showShutdownConfirm by remember { mutableStateOf(false) }

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
                    stringResource(R.string.title_quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                // ── 以下操作 2.0 版本再实现 ──
                // if (isOnline) {
                //     DialogActionRow(stringResource(R.string.action_shutdown)) {
                //     showShutdownConfirm = true
                // }
                // }
                // if (isOnline) {
                //     DialogActionRow(stringResource(R.string.action_sleep)) {
                //     if (activity != null && managerBinder != null) {
                //         ServerHelper.pcSleep(activity, computer, managerBinder, null)
                //     }
                //     onDismiss()
                // }
                // }
                // if (isOnline) {
                //     DialogActionRow(stringResource(R.string.action_restart)) {
                //     showRestartConfirm = true
                // }
                // }
                if (isOnline) {
                    DialogActionRow(stringResource(R.string.action_secondary_stream)) {
                        computer.useVdd = true
                        launchStreamFromOverview(
                            context = context,
                            computer = computer,
                            managerBinder = managerBinder,
                            forceVdd = true,
                            forceResume = computer.runningGameId != 0,
                            vddScreenMode = 2,
                        )
                        onDismiss()
                    }
                }
                if (isOnline) {
                    DialogActionRow(stringResource(R.string.action_open_web_admin)) {
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
                // TODO: 2.0 版本再实现
                // HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                // if (!isOnline) {
                //     DialogActionRow(stringResource(R.string.pcview_menu_send_wol)) {
                //         scope.launch(Dispatchers.IO) {
                //             try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) { /* offline expected */ }
                //         }
                //         onDismiss()
                //     }
                // }
                DialogActionRow(stringResource(R.string.action_delete_computer)) {
                    managerBinder?.removeComputer(computer)
                    onDismiss()
                }
                if (isOnline && hasRunningGame) {
                    DialogActionRow(stringResource(R.string.action_quit_app)) {
                        if (context is android.app.Activity && managerBinder != null) {
                            val app = NvApp().apply { appId = computer.runningGameId; appName = "Running" }
                            ServerHelper.doQuit(context, computer, app, managerBinder, null)
                        }
                        onDismiss()
                    }
                }
                DialogActionRow(stringResource(R.string.action_network_bandwidth_test)) {
                    val iperfActivity = context as? android.app.Activity
                    if (iperfActivity != null) {
                        try {
                            val ip = ServerHelper.getCurrentAddressFromComputer(computer).address
                            Iperf3Tester(iperfActivity, ip).show()
                        } catch (e: java.io.IOException) {
                            ToastUtil.show(context, context.getString(R.string.toast_address_unavailable), Toast.LENGTH_SHORT)
                        }
                    } else {
                        ToastUtil.show(context, context.getString(R.string.toast_cannot_get_address), Toast.LENGTH_SHORT)
                    }
                    onDismiss()
                }
                DialogActionRow(stringResource(R.string.pcview_menu_disable_ipv6)) {
                    computer.ipv6Disabled = !computer.ipv6Disabled
                    managerBinder?.updateComputer(computer)
                    onDismiss()
                }
                DialogActionRow(stringResource(R.string.pcview_menu_test_network)) {
                    if (context is android.app.Activity) {
                        ServerHelper.doNetworkTest(context)
                    }
                    onDismiss()
                }
                if (computer.nvidiaServer) {
                    DialogActionRow(stringResource(R.string.action_gamestream_eol)) {
                        val intent = Intent(Intent.ACTION_VIEW,
                            "https://github.com/moonlight-stream/moonlight-android/wiki/GameStream-EOL".toUri())
                        context.startActivity(intent)
                        onDismiss()
                    }
                }
            }
        }
    }

    // TODO: 2.0 版本恢复 — 关机/重启确认对话框
    // ConfirmationDialog(
    //     show = showShutdownConfirm,
    //     titleRes = R.string.action_shutdown,
    //     messageRes = R.string.action_shutdown_confirm,
    //     onDismiss = { showShutdownConfirm = false },
    //     onConfirm = {
    //         showShutdownConfirm = false
    //         if (activity != null && managerBinder != null) {
    //             scope.launch {
    //                 try {
    //                     val address = ServerHelper.getCurrentAddressFromComputer(computer)
    //                     val httpConn = NvHTTP(
    //                         address,
    //                         computer.httpsPort,
    //                         managerBinder.getUniqueId(),
    //                         android.os.Build.MODEL,
    //                         computer.serverCert,
    //                         PlatformBinding.getCryptoProvider(context)
    //                     )
    //                     withContext(Dispatchers.IO) {
    //                         val success = httpConn.pcShutdown()
    //                         withContext(Dispatchers.Main) {
    //                             snackBarHostState.showSnackbar(
    //                                 if (success) context.getString(R.string.toast_shutdown_sent) else context.getString(R.string.toast_shutdown_failed)
    //                             )
    //                         }
    //                     }
    //                 } catch (e: Exception) {
    //                     snackBarHostState.showSnackbar(context.getString(R.string.toast_shutdown_exception) + ": ${e.message}")
    //                 }
    //             }
    //         }
    //         onDismiss()
    //     },
    // )
    //
    // ConfirmationDialog(
    //     show = showRestartConfirm,
    //     titleRes = R.string.action_restart,
    //     messageRes = R.string.action_restart_confirm,
    //     onDismiss = { showRestartConfirm = false },
    //     onConfirm = {
    //         showRestartConfirm = false
    //         if (activity != null && managerBinder != null) {
    //             ServerHelper.pcRestart(activity, computer, managerBinder, null)
    //         }
    //         onDismiss()
    //     },
    // )
}

// TODO: 2.0 版本恢复
// @Composable
// private fun ConfirmationDialog(
//     show: Boolean,
//     titleRes: Int,
//     messageRes: Int,
//     onDismiss: () -> Unit,
//     onConfirm: () -> Unit,
// ) {
//     if (show) {
//         AlertDialog(
//             onDismissRequest = onDismiss,
//             title = { Text(stringResource(titleRes)) },
//             text = { Text(stringResource(messageRes)) },
//             confirmButton = {
//                 TextButton(onClick = onConfirm) {
//                     Text(stringResource(R.string.dialog_button_confirm))
//                 }
//             },
//             dismissButton = {
//                 TextButton(onClick = onDismiss) {
//                     Text(stringResource(R.string.editor_cancel))
//                 }
//             },
//         )
//     }
// }

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

// ── Desktop thumbnail area (shared by landscape & portrait) ─────────

/**
 * @param clipRadius 缩略图顶部圆角半径 — 横屏 12.dp，竖屏 16.dp
 * @param showLoadingDots 是否显示配对加载动画 — 仅竖屏需要
 * @param onDisplaySelected 用户点击物理显示器 Chip 时的回调
 * @param onVddSelected 用户点击 VDD Chip 时的回调
 */
@Composable
private fun DesktopThumbnailBox(
    clipRadius: Dp,
    showLoadingDots: Boolean,
    modifier: Modifier = Modifier,
    computer: ComputerDetails,
    isOnline: Boolean,
    displaysInfo: List<NvHTTP.DisplayInfo>?,
    selectedDisplayGuid: String?,
    selectedVddEnabled: Boolean,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    onDisplaySelected: (guid: String, name: String) -> Unit,
    onVddSelected: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = clipRadius, topEnd = clipRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = isOnline) {
                val forceResume = computer.runningGameId != 0
                launchStreamFromOverview(
                    context, computer, managerBinder,
                    forceResume = forceResume,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Desktop box art
        DeviceBoxArt(
            uuid = computer.uuid,
            isOnline = isOnline,
            modifier = Modifier.fillMaxSize(),
            clipShape = RoundedCornerShape(topStart = clipRadius, topEnd = clipRadius),
        )

        // Status badge (top-start)
        val badgeColor = if (isOnline) statusOnline else statusOffline
        val badgeText  = if (isOnline) stringResource(R.string.pcview_menu_header_online) else stringResource(R.string.pcview_menu_header_offline)
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

        // Loading dots for unpaired UNKNOWN (marquee animation) — 仅竖屏
        if (showLoadingDots) {
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

        // Display selector chips (Sunshine displays API)
        DisplayChipRow(
            displaysInfo = displaysInfo,
            selectedDisplayGuid = selectedDisplayGuid,
            selectedVddEnabled = selectedVddEnabled,
            onDisplaySelected = onDisplaySelected,
            onVddSelected = onVddSelected,
        )

        // Bottom overlay: "进入桌面 >"（仅在线时显示）
        if (isOnline) {
            Text(
                stringResource(R.string.label_enter_desktop),
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
}

// ── Action button row (shared by landscape & portrait) ────────────

@Composable
private fun DesktopActionRow(
    dividerHeight: Dp,
    showQuickActionButton: Boolean,
    modifier: Modifier = Modifier,
    onShowQuickActions: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onNavigateToStreamSettings: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (showQuickActionButton) {
            OverviewActionButton(
                icon = Icons.Default.Bolt,
                label = stringResource(R.string.title_quick_actions),
                modifier = Modifier.weight(1f),
                onClick = onShowQuickActions,
            )
        }
        VerticalDivider(modifier = Modifier.height(dividerHeight))
        OverviewActionButton(
            icon = Icons.Default.Info,
            label = stringResource(R.string.title_device_detail),
            modifier = Modifier.weight(1f),
            onClick = onNavigateToDetail,
        )
        VerticalDivider(modifier = Modifier.height(dividerHeight))
        OverviewActionButton(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.title_stream_settings),
            modifier = Modifier.weight(1f),
            onClick = onNavigateToStreamSettings,
        )
    }
}

// ── Quick launch grid (shared by landscape & portrait) ────────────

@Composable
private fun QuickLaunchGrid(
    appList: List<NvApp>,
    uuid: String,
    computer: ComputerDetails,
    context: Context,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    modifier: Modifier = Modifier,
) {
    if (appList.isEmpty()) return

    val columns = 4
    val visibleApps = appList.filter {
        it.appId != NvApp.DESKTOP_APP_ID && !"Desktop".equals(it.appName, ignoreCase = true)
    }

    Column(modifier = modifier) {
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
                            launchStreamFromOverview(context, computer, managerBinder, app = app, forceResume = computer.runningGameId != 0)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Display chip row (horizontally scrollable chips) ────────────────

@Composable
private fun DisplayChipRow(
    displaysInfo: List<NvHTTP.DisplayInfo>?,
    selectedDisplayGuid: String?,
    selectedVddEnabled: Boolean,
    onDisplaySelected: (guid: String, name: String) -> Unit,
    onVddSelected: () -> Unit,
) {
    // 检测列表中是否有 Zako HDR 虚拟显示器
    val zakoDisplay = displaysInfo?.find { it.name.contains("Zako HDR", ignoreCase = true) }
    // 物理显示器列表（排除 Zako HDR）
    val physicalDisplays = displaysInfo?.filter { it != zakoDisplay }

    if (physicalDisplays.isNullOrEmpty() && !selectedVddEnabled && zakoDisplay == null) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 物理显示器 Chip（排除 Zako HDR）
        if (!physicalDisplays.isNullOrEmpty()) {
            items(physicalDisplays, key = { it.guid.ifEmpty { it.name } }) { display ->
                val guid = display.guid.ifEmpty { display.name }
                DisplayChip(
                    display = display,
                    isSelected = guid == selectedDisplayGuid,
                    onClick = { onDisplaySelected(guid, display.name) },
                )
            }
        }
        // VDD Chip（如果存在 Zako HDR 则显示其名称，否则显示"虚拟显示器"）
        item(key = "__vdd__") {
            VddChip(
                isSelected = selectedVddEnabled,
                onClick = onVddSelected,
                name = zakoDisplay?.name ?: stringResource(R.string.label_virtual_display),
            )
        }
    }
}

// ── Display chip (physical display) ──────────────────────────────

@Composable
private fun DisplayChip(
    display: NvHTTP.DisplayInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val borderMod = if (isSelected)
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
    else if (display.isPrimary)
        Modifier.border(1.dp, Color(0xFFFFC107), RoundedCornerShape(6.dp))
    else
        Modifier

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor.copy(alpha = 0.5f),
        modifier = Modifier
            .widthIn(min = 56.dp, max = 110.dp)
            .height(50.dp)
            .then(borderMod)
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            // "显示器" + 主屏星标（蓝色文字）
            Text(
                text = buildString {
                    append(stringResource(R.string.label_display))
                    if (display.isPrimary) append(" ★")
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            // 第二行: DisplayName
            Text(
                text = display.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── VDD chip ────────────────────────────────────────────────────

@Composable
private fun VddChip(
    isSelected: Boolean,
    onClick: () -> Unit,
    name: String = stringResource(R.string.label_virtual_display),
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val borderMod = if (isSelected)
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
    else
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor.copy(alpha = 0.5f),
        modifier = Modifier
            .widthIn(min = 56.dp, max = 96.dp)
            .height(50.dp)
            .then(borderMod)
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.label_virtual_display),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Monitor,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Stream launcher (overview page) ───────────────────────────────

private fun launchStreamFromOverview(
    context: Context,
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    app: NvApp? = null,
    forceResume: Boolean = false,
    displayName: String? = null,
    forceVdd: Boolean = false,
    vddScreenMode: Int = -1,
    screenCombinationMode: Int? = null,
) {
    if (managerBinder == null) return

    // ── PiP 模式检测 ────────────────────────────────────
    if (StreamEngine.currentPipActivity != null && !StreamEngine.currentPipActivity!!.isFinishing) {
        val pipUuid = StreamEngine.currentPipUuid
        if (pipUuid != null && pipUuid == computer.uuid) {
            // 同一设备：关闭 PiP Activity，然后启动新流
            ToastUtil.show(context, context.getString(R.string.toast_pip_recover), Toast.LENGTH_SHORT)
            StreamEngine.currentPipActivity!!.finish()
        } else {
            // 不同设备：关闭 PiP Activity 停止旧流。
            // finish() 会触发旧 Activity 的 onDestroy → engine.release() 完整清理。
            // 但 onDestroy 是异步执行的，主动清除静态引用避免新流启动时读到脏数据。
            ToastUtil.show(context, context.getString(R.string.toast_pip_stopped), Toast.LENGTH_SHORT)
            StreamEngine.currentPipActivity!!.finish()
            StreamEngine.currentPipActivity = null
            StreamEngine.currentPipUuid = null
        }
    }

    if (computer.state != ComputerDetails.State.ONLINE) {
        ToastUtil.show(context, context.getString(R.string.toast_device_offline_waking), Toast.LENGTH_SHORT)
        try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) { /* offline expected */ }
        return
    }

    val targetApp = app ?: getDefaultQuickStartApp(computer, context) ?: run {
        ToastUtil.show(context, context.getString(R.string.toast_no_app_to_launch), Toast.LENGTH_SHORT)
        return
    }

    // ── 屏幕组合模式：vddScreenMode 优先，否则使用传参（不再从 HostSettings 回退）──
    val resolvedScreenMode = if (vddScreenMode != -1) vddScreenMode else screenCombinationMode ?: -1

    // ── 计算有效 VDD 状态，不变异 computer.useVdd ──
    val effectiveUseVdd = forceVdd || (vddScreenMode != -1)

    // ── 使用新版 StreamActivity ──
    // useLastSettings 标志传递给 StreamActivity，由 StreamEngine 在初始化时应用
    val useLastSettings = AppSettingsManager(context).isUseLastSettingsEnabled
    val intent = createStreamIntent(
        context, computer, targetApp, managerBinder,
        useLastSettings, forceResume, displayName,
        screenCombinationMode = resolvedScreenMode,
        vddScreenMode = vddScreenMode,
        effectiveUseVdd = effectiveUseVdd,
    )
    context.startActivity(intent)
}

/**
 * 构造跳转新版 [StreamActivity] 的 Intent。
 *
 * Extras 键名与旧版 [com.limelight.Game] 保持一致。
 */
fun createStreamIntent(
    context: Context,
    computer: ComputerDetails,
    app: NvApp,
    managerBinder: ComputerManagerService.ComputerManagerBinder,
    useLastSettings: Boolean,
    forceResume: Boolean = false,
    displayName: String? = null,
    screenCombinationMode: Int = -1,
    vddScreenMode: Int = -1,
    effectiveUseVdd: Boolean = false,
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
        putExtra("usevdd", effectiveUseVdd)
        if (!displayName.isNullOrEmpty()) {
            putExtra("DisplayName", displayName)
        }
        // 传递屏幕组合模式（vddScreenMode 优先覆盖）
        val effectiveScreenMode = if (vddScreenMode != -1) vddScreenMode else screenCombinationMode
        val effectiveForceVdd = effectiveUseVdd
        if (effectiveScreenMode != -1) {
            if (effectiveForceVdd) {
                putExtra("VDD screen combination mode", effectiveScreenMode)
            } else {
                putExtra("Screen combination mode", effectiveScreenMode)
            }
        }
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
