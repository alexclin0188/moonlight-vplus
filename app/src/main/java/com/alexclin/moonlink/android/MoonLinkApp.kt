package com.alexclin.moonlink.android

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexclin.moonlink.android.device.detail.DeviceDetailScreen
import com.alexclin.moonlink.android.device.overview.DeviceOverviewScreen
import com.alexclin.moonlink.android.device.streamsettings.DeviceStreamSettingsScreen
import com.alexclin.moonlink.android.home.DeviceListScreen
import com.alexclin.moonlink.android.navigation.MoonLinkRoute
import com.alexclin.moonlink.android.settings.*
import com.alexclin.moonlink.android.stream.engine.DeviceStateManager
import com.alexclin.moonlink.android.vpn.VpnScreen
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails

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

private fun computeTitle(route: String?, deviceManager: DeviceStateManager, uuid: String?): String = when (route) {
    MoonLinkRoute.DeviceList.route       -> "我的设备"
    "tab_vpn"                            -> "虚拟局域网"
    "tab_settings"                       -> "设置"
    MoonLinkRoute.DeviceOverview.route   -> deviceManager.getDevice(uuid ?: "")?.name ?: "设备概要"
    MoonLinkRoute.DeviceDetail.route     -> "设备详情"
    MoonLinkRoute.DeviceStreamSettings.route -> (deviceManager.getDevice(uuid ?: "")?.name ?: "") + "串流设置"
    MoonLinkRoute.SettingsUi.route       -> "界面设置"
    MoonLinkRoute.SettingsAudio.route    -> "音频设置"
    MoonLinkRoute.SettingsGamepad.route  -> "手柄设置"
    MoonLinkRoute.SettingsInput.route    -> "输入设置"
    MoonLinkRoute.SettingsMultitouch.route -> "多点触控设置"
    MoonLinkRoute.SettingsConnection.route -> "连接设置"
    MoonLinkRoute.SettingsScene.route    -> "场景预设"
    MoonLinkRoute.SettingsKeyMapping.route -> "按键映射管理"
    MoonLinkRoute.SettingsHelp.route       -> "帮助"
    MoonLinkRoute.SettingsWidget.route     -> "桌面小部件"
    MoonLinkRoute.SettingsPerformance.route -> "性能与统计分析"
    else                                 -> ""
}

// ── Shared navigation bar tab click ───────────────────────────────

private fun navigateToTab(navController: androidx.navigation.NavController, currentRoute: String?, route: String) {
    if (currentRoute != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

// ── Root composable ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonLinkApp(
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    deviceManager: DeviceStateManager,
    onComputerRemoved: ((String) -> Unit)? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Extract uuid from backStack arguments for routes that need it
    val currentUuid = backStackEntry?.arguments?.getString(MoonLinkRoute.DeviceOverview.ARG_UUID)
        ?: backStackEntry?.arguments?.getString(MoonLinkRoute.DeviceStreamSettings.ARG_UUID)

    // Show bottom bar only on the top-level tabs
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // Top bar configuration derived from current route
    val topBarTitle = computeTitle(currentRoute, deviceManager, currentUuid)
    val showBack = currentRoute != null && currentRoute !in TOP_LEVEL_ROUTES

    // External refresh trigger for VPN screen
    val vpnRefreshTrigger = remember { mutableIntStateOf(0) }

    // Snackbar host state shared across screens
    val snackbarHostState = remember { SnackbarHostState() }

    // ── NavHost content (shared between portrait and landscape) ──
    @Composable
    fun NavHostContent() {
        NavHost(
            navController = navController,
            startDestination = MoonLinkRoute.DeviceList.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Tab 1: 我的设备 ──────────────────────────────
            composable(MoonLinkRoute.DeviceList.route) {
                DeviceListScreen(
                    managerBinder = managerBinder,
                    computers     = deviceManager.devices,
                    snackbarHostState = snackbarHostState,
                    onNavigateToOverview = { uuid ->
                        navController.navigate(MoonLinkRoute.DeviceOverview.createRoute(uuid))
                    },
                    onNavigateToDetail = { uuid ->
                        navController.navigate(MoonLinkRoute.DeviceDetail.createRoute(uuid))
                    },
                    onComputerRemoved = onComputerRemoved,
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
                    managerBinder = managerBinder,
                    computers = deviceManager.devices,
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

            // ── 桌面小组件管理 ────────────────────────────
            composable(MoonLinkRoute.SettingsWidget.route) {
                WidgetSettingsScreen(
                    managerBinder = managerBinder,
                    computers = deviceManager.devices,
                )
            }

            // ── 性能与统计分析 ────────────────────────────
            composable(MoonLinkRoute.SettingsPerformance.route) {
                PerformanceSettingsScreen()
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
                    computers     = deviceManager.devices,
                    onBack        = { navController.popBackStack() },
                    onNavigateToDetail = {
                        navController.navigate(MoonLinkRoute.DeviceDetail.createRoute(uuid))
                    },
                    onNavigateToStreamSettings = {
                        navController.navigate(MoonLinkRoute.DeviceStreamSettings.createRoute(uuid))
                    },
                )
            }

            // ── 串流设置页 ────────────────────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettings.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettings.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettings.ARG_UUID) ?: return@composable
                DeviceStreamSettingsScreen(
                    hostname = deviceManager.getDevice(uuid)?.name ?: "",
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
                    computers     = deviceManager.devices,
                    onBack        = { navController.popBackStack() },
                )
            }
        }
    }

    // ── NavigationRail (landscape left sidebar) ──────────────────
    @Composable
    fun LandscapeNavigationRail() {
        NavigationRail(
            modifier = Modifier.fillMaxHeight().width(80.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TOP_TABS.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationRailItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall) },
                        selected = selected,
                        onClick = { navigateToTab(navController, currentRoute, tab.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = NavigationRailItemDefaults.colors(
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
    }

    Scaffold(
        // ── Unified top bar (隐藏于横屏主页) ────────────
        topBar = {
            val hideTopBar = isLandscape && showBottomBar
            if (!hideTopBar && topBarTitle.isNotEmpty()) {
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
        // ── Bottom tab bar (portrait only) ──────────────────────
        bottomBar = {
            if (!isLandscape && showBottomBar) {
                NavigationBar(
                    tonalElevation = 3.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
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
                            onClick = { navigateToTab(navController, currentRoute, tab.route) },
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
        if (isLandscape && showBottomBar) {
            // ── 横屏：左栏 NavigationRail + 右侧内容 ──────────
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                LandscapeNavigationRail()
                // Right: content area
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHostContent()
                }
            }
        } else {
            // ── 竖屏：传统布局 ───────────────────────────────
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                NavHostContent()
            }
        }
    }
}
