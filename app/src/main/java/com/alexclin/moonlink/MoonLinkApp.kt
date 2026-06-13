package com.alexclin.moonlink

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexclin.moonlink.device.detail.DeviceDetailScreen
import com.alexclin.moonlink.device.overview.DeviceOverviewScreen
import com.alexclin.moonlink.home.DeviceListScreen
import com.alexclin.moonlink.home.PairQrResult
import com.alexclin.moonlink.home.handleQrPairResult
import com.alexclin.moonlink.navigation.MoonLinkRoute
import com.alexclin.moonlink.settings.*
import com.alexclin.moonlink.vpn.VpnScreen
import com.google.zxing.integration.android.IntentIntegrator
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.utils.ServerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Bottom tab descriptor ─────────────────────────────────────────

private data class TopLevelTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TOP_TABS = listOf(
    TopLevelTab(MoonLinkRoute.DeviceList.route, "我的设备", Icons.Filled.Devices, Icons.Outlined.Devices),
    TopLevelTab("tab_vpn",     "虚拟局域网", Icons.Filled.Wifi,    Icons.Outlined.Wifi),
    TopLevelTab("tab_settings","设置",       Icons.Filled.Settings,Icons.Outlined.Settings),
)

private val TOP_LEVEL_ROUTES = TOP_TABS.map { it.route }

// ── Title helper ──────────────────────────────────────────────────

private fun computeTitle(route: String?, computers: List<ComputerDetails>, uuid: String?): String = when (route) {
    MoonLinkRoute.DeviceList.route       -> "我的设备"
    "tab_vpn"                            -> "虚拟局域网"
    "tab_settings"                       -> "设置"
    MoonLinkRoute.DeviceOverview.route   -> computers.find { it.uuid == uuid }?.name ?: "设备概要"
    MoonLinkRoute.DeviceDetail.route     -> "设备详情"
    MoonLinkRoute.SettingsUi.route       -> "界面设置"
    MoonLinkRoute.SettingsAudio.route    -> "音频设置"
    MoonLinkRoute.SettingsGamepad.route  -> "手柄设置"
    MoonLinkRoute.SettingsInput.route    -> "输入设置"
    MoonLinkRoute.SettingsMultitouch.route -> "多点触控设置"
    MoonLinkRoute.SettingsConnection.route -> "连接设置"
    MoonLinkRoute.SettingsScene.route    -> "场景预设"
    MoonLinkRoute.SettingsKeyMapping.route -> "按键配置管理"
    MoonLinkRoute.SettingsHelp.route     -> "帮助"
    else                                 -> ""
}

// ── Root composable ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonLinkApp(
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    computers: List<ComputerDetails>,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Extract uuid from backStack arguments for overview/detail routes
    val currentUuid = backStackEntry?.arguments?.getString(MoonLinkRoute.DeviceOverview.ARG_UUID)

    // Show bottom bar only on the top-level tabs
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    // Top bar configuration derived from current route
    val topBarTitle = computeTitle(currentRoute, computers, currentUuid)
    val showBack = currentRoute != null && currentRoute !in TOP_LEVEL_ROUTES

    // External refresh trigger for VPN screen
    val vpnRefreshTrigger = remember { mutableIntStateOf(0) }

    // Snackbar host state shared across screens
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Add-device menu state & QR scanner launcher ──────────────
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddMenu by remember { mutableStateOf(false) }
    var isQrLoading by remember { mutableStateOf(false) }

    val qrCodeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val contents = result.data?.getStringExtra("SCAN_RESULT")?.trim()
        if (contents == null) return@rememberLauncherForActivityResult

        val uri = contents.toUri()
        if ("moonlight" != uri.scheme || "pair" != uri.host) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.qr_invalid_code)) }
            return@rememberLauncherForActivityResult
        }

        val host = uri.getQueryParameter("host")
            ?: run { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.qr_invalid_code)) }; return@rememberLauncherForActivityResult }
        val pin = uri.getQueryParameter("pin")
            ?: run { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.qr_invalid_code)) }; return@rememberLauncherForActivityResult }
        val portStr = uri.getQueryParameter("port")
        val port = if (portStr != null) { try { portStr.toInt() } catch (_: NumberFormatException) { NvHTTP.DEFAULT_HTTP_PORT } } else { null }

        isQrLoading = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    handleQrPairResult(context, host, pin, port, managerBinder)
                }
                when (result) {
                    is PairQrResult.Success -> {
                        snackbarHostState.showSnackbar(context.getString(R.string.addpc_success))
                    }
                    is PairQrResult.Error -> {
                        snackbarHostState.showSnackbar(result.message)
                    }
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("配对异常: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                isQrLoading = false
            }
        }
    }

    Scaffold(
        // ── Unified top bar ───────────────────────────────
        topBar = {
            if (topBarTitle.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text(
                            topBarTitle,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        if (showBack) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = {
                        when (currentRoute) {
                            MoonLinkRoute.DeviceList.route -> {
                                Box {
                                    IconButton(onClick = { showAddMenu = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "添加设备")
                                    }
                                    DropdownMenu(
                                        expanded = showAddMenu,
                                        onDismissRequest = { showAddMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.addpc_manual)) },
                                            onClick = {
                                                showAddMenu = false
                                                context.startActivity(Intent(context, com.limelight.preferences.AddComputerManually::class.java))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.addpc_qr_scan)) },
                                            onClick = {
                                                showAddMenu = false
                                                val integrator = IntentIntegrator(context as Activity)
                                                integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                                                integrator.setPrompt(context.getString(R.string.qr_scan_prompt))
                                                integrator.setBeepEnabled(false)
                                                integrator.setOrientationLocked(false)
                                                qrCodeLauncher.launch(integrator.createScanIntent())
                                            }
                                        )
                                    }
                                }
                            }
                            "tab_vpn" -> {
                                IconButton(onClick = { vpnRefreshTrigger.intValue++ }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新状态")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
        // ── Bottom tab bar (compact) ───────────────────────
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    tonalElevation = 3.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.height(76.dp),
                ) {
                    TOP_TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            selected = selected,
                            modifier = Modifier.padding(vertical = 0.dp),
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = MoonLinkRoute.DeviceList.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                // ── Tab 1: 我的设备 ──────────────────────────────
            // ── Tab 1: 我的设备 ──────────────────────────────
            composable(MoonLinkRoute.DeviceList.route) {
                DeviceListScreen(
                    managerBinder = managerBinder,
                    computers     = computers,
                    snackbarHostState = snackbarHostState,
                    onNavigateToOverview = { uuid ->
                        navController.navigate(MoonLinkRoute.DeviceOverview.createRoute(uuid))
                    },
                    onNavigateToDetail = { uuid ->
                        navController.navigate(MoonLinkRoute.DeviceDetail.createRoute(uuid))
                    },
                )
            }

            // ── Tab 2: 虚拟局域网 ─────────────────────────
            composable("tab_vpn") {
                VpnScreen(
                    externalRefreshTrigger = vpnRefreshTrigger.intValue,
                )
            }

            // ── Tab 3: 设置 ────────────────────────────────
            composable("tab_settings") {
                SettingsScreen(
                    onNavigate = { route ->
                        navController.navigate(route)
                    },
                )
            }

            // ── Settings sub-pages ──────────────────────────
            composable(MoonLinkRoute.SettingsUi.route) {
                UiSettingsScreen()
            }
            composable(MoonLinkRoute.SettingsAudio.route) {
                AudioSettingsScreen()
            }
            composable(MoonLinkRoute.SettingsGamepad.route) {
                GamepadSettingsScreen()
            }
            composable(MoonLinkRoute.SettingsInput.route) {
                InputSettingsScreen()
            }
            composable(MoonLinkRoute.SettingsMultitouch.route) {
                MultitouchSettingsScreen()
            }
            composable(MoonLinkRoute.SettingsConnection.route) {
                ConnectionSettingsScreen()
            }
            composable(MoonLinkRoute.SettingsScene.route) {
                ScenePresetsScreen()
            }
            composable(MoonLinkRoute.SettingsKeyMapping.route) {
                KeyMappingScreen()
            }
            composable(MoonLinkRoute.SettingsHelp.route) {
                HelpSettingsScreen()
            }

            // ── 设备概要页 ────────────────────────────────────
            composable(
                route = MoonLinkRoute.DeviceOverview.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceOverview.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceOverview.ARG_UUID) ?: return@composable
                DeviceOverviewScreen(
                    uuid          = uuid,
                    managerBinder = managerBinder,
                    computers     = computers,
                    onBack        = { navController.popBackStack() },
                    onNavigateToDetail = {
                        navController.navigate(MoonLinkRoute.DeviceDetail.createRoute(uuid))
                    },
                )
            }

            // ── 设备详情页 ────────────────────────────────────
            composable(
                route = MoonLinkRoute.DeviceDetail.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceDetail.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceDetail.ARG_UUID) ?: return@composable
                DeviceDetailScreen(
                    uuid          = uuid,
                    computers     = computers,
                )
            }
        }

        // QR pairing loading indicator at top
        if (isQrLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        }
    }
}
