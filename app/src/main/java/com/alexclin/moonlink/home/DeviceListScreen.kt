package com.alexclin.moonlink.home

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexclin.moonlink.theme.statusOffline
import com.alexclin.moonlink.theme.statusOnline
import com.alexclin.moonlink.theme.windowsBlue
import com.alexclin.moonlink.theme.macosGray
import com.limelight.Game
import com.limelight.R
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.binding.PlatformBinding
import com.limelight.preferences.AddComputerManually
import com.limelight.utils.ServerHelper
import com.limelight.nvstream.wol.WakeOnLanSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ── Menu action model ─────────────────────────────────────────────

data class DeviceMenuAction(
    val id: String,
    val label: String,
    val isVisible: (ComputerDetails) -> Boolean,
)

private val ALL_MENU_ACTIONS = listOf(
    DeviceMenuAction("pair",           "配对 / 取消配对")        { true },
    DeviceMenuAction("wol",            "发送网络唤醒 (WoL)")     { it.state == ComputerDetails.State.OFFLINE },
    DeviceMenuAction("delete",         "删除设备")              { true },
    DeviceMenuAction("resume",         "恢复串流")              { it.state == ComputerDetails.State.ONLINE && it.runningGameId != 0 },
    DeviceMenuAction("quit",           "退出应用")              { it.state == ComputerDetails.State.ONLINE && it.runningGameId != 0 },
    DeviceMenuAction("applist",        "完整应用列表")           { true },
    DeviceMenuAction("detail",         "查看详情")              { true },
    DeviceMenuAction("sleep",          "睡眠")                 { it.state == ComputerDetails.State.ONLINE },
    DeviceMenuAction("vdd",            "副屏幕 (VDD)")          { true },
    DeviceMenuAction("iperf",          "网络测试 (iPerf3)")      { true },
    DeviceMenuAction("webui",          "打开 Sunshine 网页管理") { true },
    DeviceMenuAction("disable_ipv6",   "为此设备禁用 IPv6")      { true },
    DeviceMenuAction("nettest",        "网络连通测试")           { true },
    DeviceMenuAction("gs_eol",         "GameStream EOL 说明")   { true },
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
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Separate paired / unpaired
    val paired   = computers.filter { it.pairState == com.limelight.nvstream.http.PairingManager.PairState.PAIRED }
        .sortedWith(compareByDescending<ComputerDetails> { it.state == ComputerDetails.State.ONLINE }
            .thenBy { it.name?.lowercase() })
    val unpaired = computers.filter { it.pairState != com.limelight.nvstream.http.PairingManager.PairState.PAIRED }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        "未发现设备",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        context.startActivity(Intent(context, AddComputerManually::class.java))
                    }) {
                        Text("手动添加")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Paired devices ─────────────────────
                if (paired.isNotEmpty()) {
                    item { SectionHeader("可控设备") }
                    items(paired, key = { it.uuid ?: it.name.orEmpty() }) { computer ->
                        DeviceCard(
                            computer = computer,
                            managerBinder = managerBinder,
                            onStream = { launchStream(context, computer, managerBinder) },
                            onClickInfo = { onNavigateToOverview(computer.uuid.orEmpty()) },
                            onNavigateToDetail = { onNavigateToDetail(computer.uuid.orEmpty()) },
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                        )
                    }
                }

                // ── Unpaired devices ───────────────────
                if (unpaired.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "未配对设备",
                            showProgress = true,
                        )
                    }
                    items(unpaired, key = { it.uuid ?: it.name.orEmpty() }) { computer ->
                        DeviceCard(
                            computer = computer,
                            managerBinder = managerBinder,
                            onStream = { launchStream(context, computer, managerBinder) },
                            onClickInfo = { onNavigateToOverview(computer.uuid.orEmpty()) },
                            onNavigateToDetail = { onNavigateToDetail(computer.uuid.orEmpty()) },
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                        )
                    }
                }
            }
        }
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
) {
    val context = LocalContext.current
    val isOnline = computer.state == ComputerDetails.State.ONLINE
    val isUnknown = computer.state == ComputerDetails.State.UNKNOWN

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
            modifier = Modifier.fillMaxWidth(),
        ) {
            // ── Thumbnail area (left, clickable → stream) ──
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .aspectRatio(1.618f)
                    .clickable(enabled = isOnline, onClick = onStream),
            ) {
                // Box art from cache (or placeholder)
                DeviceBoxArt(
                    uuid = computer.uuid,
                    isOnline = isOnline,
                    modifier = Modifier.fillMaxSize(),
                )

                // Status badge (bottom-start)
                if (!isUnknown) {
                    val badgeColor = if (isOnline) statusOnline else statusOffline
                    val badgeText  = if (isOnline) "在线" else "离线"
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
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

                // Loading dots for UNKNOWN (marquee animation)
                if (isUnknown) {
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

            // ── Info area (right, clickable → overview, long click → menu) ──
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = onClickInfo,
                        onLongClick = { showMenu = true },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = computer.name ?: "未知设备",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // OS type icon
                    val isMac = computer.name?.lowercase()?.let {
                        it.contains("mac") || it.contains("darwin")
                    } ?: false
                    val osIcon = if (isMac) Icons.Default.LaptopMac else Icons.Default.DesktopWindows
                    val osColor = if (isMac) macosGray else windowsBlue
                    Icon(
                        imageVector = osIcon,
                        contentDescription = if (isMac) "macOS" else "Windows",
                        modifier = Modifier.size(16.dp),
                        tint = osColor.copy(alpha = if (isOnline) 1f else 0.5f),
                    )

                    // Running game indicator
                    if (computer.runningGameId != 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "运行中",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "运行中",
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
        }
    }

    // ── Context menu ───────────────────────────────────
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        val visibleActions = ALL_MENU_ACTIONS.filter { it.isVisible(computer) }
        visibleActions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
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
                    )
                },
            )
        }
    }
}

// ── Menu action handler ───────────────────────────────────────────

private fun handleMenuAction(
    actionId: String,
    computer: ComputerDetails,
    context: android.content.Context,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigateToDetail: () -> Unit,
) {
    val activity = context as? android.app.Activity ?: return

    when (actionId) {
        "delete" -> {
            managerBinder?.removeComputer(computer)
            scope.launch {
                snackbarHostState.showSnackbar("已删除设备: ${computer.name}")
            }
        }
        "wol" -> {
            scope.launch(Dispatchers.IO) {
                try {
                    WakeOnLanSender.sendWolPacket(computer)
                    with(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("已发送唤醒包")
                    }
                } catch (e: Exception) {
                    with(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("唤醒失败: ${e.message}")
                    }
                }
            }
        }
        "sleep" -> {
            if (managerBinder != null) {
                ServerHelper.pcSleep(activity, computer, managerBinder, null)
            }
        }
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
            if (computer.pairState == PairingManager.PairState.PAIRED) {
                doUnpair(activity, computer, managerBinder, snackbarHostState, scope)
            } else {
                doPair(activity, computer, managerBinder, snackbarHostState, scope)
            }
        }
        "resume" -> {
            launchStream(context, computer, managerBinder, forceResume = true)
        }
        "applist" -> {
            // Navigate to AppView for full app list
            val intent = android.content.Intent(context, com.limelight.AppView::class.java).apply {
                putExtra(com.limelight.AppView.NAME_EXTRA, computer.name)
                putExtra(com.limelight.AppView.UUID_EXTRA, computer.uuid)
            }
            context.startActivity(intent)
        }
        "vdd" -> {
            computer.useVdd = true
            launchStream(context, computer, managerBinder)
            computer.useVdd = false
        }
        "webui" -> {
            val addr = computer.activeAddress?.address ?: return
            val url = "https://$addr:${computer.httpsPort}"
            val intent = android.content.Intent(context, com.limelight.SunshineWebUiActivity::class.java).apply {
                putExtra(com.limelight.SunshineWebUiActivity.EXTRA_URL, url)
                putExtra(com.limelight.SunshineWebUiActivity.EXTRA_TITLE, computer.name ?: "Sunshine")
                computer.serverCert?.let { putExtra(com.limelight.SunshineWebUiActivity.EXTRA_SERVER_CERT, it.encoded) }
            }
            context.startActivity(intent)
        }
        "nettest" -> {
            ServerHelper.doNetworkTest(activity)
        }
        "iperf" -> {
            try {
                val ip = ServerHelper.getCurrentAddressFromComputer(computer).address
                com.limelight.utils.Iperf3Tester(activity, ip).show()
            } catch (e: java.io.IOException) {
                scope.launch {
                    snackbarHostState.showSnackbar("无法获取设备地址: ${e.message}")
                }
            }
        }
        "disable_ipv6" -> {
            computer.ipv6Disabled = !computer.ipv6Disabled
            managerBinder?.updateComputer(computer)
            scope.launch {
                val msg = if (computer.ipv6Disabled) "已禁用 IPv6" else "已启用 IPv6"
                snackbarHostState.showSnackbar("$msg: ${computer.name}")
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
) {
    if (managerBinder == null) return
    val activity = context as? android.app.Activity ?: return

    if (computer.state != ComputerDetails.State.ONLINE) {
        android.widget.Toast.makeText(context, "设备离线，正在尝试唤醒…", android.widget.Toast.LENGTH_SHORT).show()
        try { WakeOnLanSender.sendWolPacket(computer) } catch (_: Exception) {}
        return
    }

    // Prepare a copy with address resolved (matches PcView.prepareComputerWithAddress)
    val target = ComputerDetails(computer)
    if (target.activeAddress == null) {
        target.activeAddress = target.selectBestAddress()
    }
    if (target.activeAddress == null) {
        android.widget.Toast.makeText(context, "设备地址不可用", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val desktopApp = NvApp(NvApp.DESKTOP_APP_NAME, NvApp.DESKTOP_APP_ID, false)
    ServerHelper.doStart(activity, desktopApp, target, managerBinder, forceResume)
}

// ── Pairing ───────────────────────────────────────────────────────

private fun doPair(
    activity: android.app.Activity,
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
        android.widget.Toast.makeText(activity, "设备离线，无法配对", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    if (managerBinder == null) return

    android.widget.Toast.makeText(activity, "正在配对…", android.widget.Toast.LENGTH_SHORT).show()

    scope.launch {
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
                    android.app.AlertDialog.Builder(activity)
                        .setTitle("配对")
                        .setMessage("请在主机上输入以下 PIN 码:\n\n$pin\n\n（主机屏幕上会显示输入框）")
                        .setCancelable(false)
                        .setPositiveButton("确定", null)
                        .show()
                }

                val pm = httpConn.pairingManager
                val pairResult = pm.pair(httpConn.getServerInfo(true), pin)

                when (pairResult.state) {
                    PairingManager.PairState.PAIRED -> {
                        managerBinder.getComputer(computer.uuid!!)?.serverCert = pm.pairedCert
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
                result == "already_paired" -> "设备已配对"
                result.startsWith("success") -> {
                    val pairName = result.substringAfter("success:", "")
                    if (pairName.isNotEmpty()) {
                        activity.getSharedPreferences("pair_name_map", android.content.Context.MODE_PRIVATE)
                            .edit().putString(computer.uuid, pairName).apply()
                    }
                    managerBinder.invalidateStateForComputer(computer.uuid!!)
                    "配对成功"
                }
                result == "pin_wrong" -> "PIN 码错误"
                result == "failed_in_game" -> "配对失败：主机正在运行游戏"
                result == "failed" -> "配对失败"
                result == "in_progress" -> "配对已在进行中"
                else -> "配对失败"
            }

            with(Dispatchers.Main) {
                snackbarHostState.showSnackbar(msg)
            }
        } catch (e: Exception) {
            with(Dispatchers.Main) {
                snackbarHostState.showSnackbar("配对异常: ${e.message}")
            }
        }
    }
}

private fun doUnpair(
    activity: android.app.Activity,
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
        android.widget.Toast.makeText(activity, "设备离线，无法取消配对", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    if (managerBinder == null) return

    scope.launch {
        val msg = kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val httpConn = NvHTTP(
                    ServerHelper.getCurrentAddressFromComputer(computer),
                    computer.httpsPort,
                    managerBinder.getUniqueId(),
                    android.os.Build.MODEL,
                    computer.serverCert,
                    PlatformBinding.getCryptoProvider(activity),
                )
                if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                    httpConn.unpair()
                    if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED)
                        "取消配对成功"
                    else
                        "取消配对失败"
                } else {
                    "设备未配对"
                }
            } catch (e: Exception) {
                "取消配对异常: ${e.message}"
            }
        }
        with(Dispatchers.Main) {
            snackbarHostState.showSnackbar(msg)
        }
    }
}
