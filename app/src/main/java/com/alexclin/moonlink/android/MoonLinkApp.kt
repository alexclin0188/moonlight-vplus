package com.alexclin.moonlink.android

import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.android.device.detail.DeviceDetailScreen
import com.alexclin.moonlink.android.device.overview.DeviceOverviewScreen
import com.alexclin.moonlink.android.device.streamsettings.AudioCategory
import com.alexclin.moonlink.android.device.streamsettings.DeviceStreamSettingsScreen
import com.alexclin.moonlink.android.device.streamsettings.DisplayCategory
import com.alexclin.moonlink.android.device.streamsettings.DisplaySwitchesCategory
import com.alexclin.moonlink.android.device.streamsettings.GyroCategory
import com.alexclin.moonlink.android.device.streamsettings.HostCategory
import com.alexclin.moonlink.android.device.streamsettings.HostSettingsManager
import com.alexclin.moonlink.android.device.streamsettings.OtherCategory
import com.alexclin.moonlink.android.device.streamsettings.TouchModeCategory
import com.alexclin.moonlink.android.home.DeviceListScreen
import com.alexclin.moonlink.android.navigation.MoonLinkRoute
import com.alexclin.moonlink.android.settings.*
import com.alexclin.moonlink.android.stream.engine.DeviceStateManager
import com.alexclin.moonlink.android.vpn.VpnScreen
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.preferences.BackgroundSource
import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.ColorFilterTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ── Bottom tab descriptor ─────────────────────────────────────────

private data class TopLevelTab(
    val route: String,
    val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
private fun rememberTopTabs(context: android.content.Context): List<TopLevelTab> = remember {
    listOf(
        TopLevelTab(MoonLinkRoute.DeviceList.route, R.string.tab_my_devices,   Icons.Filled.Devices, Icons.Outlined.Devices),
        TopLevelTab("tab_vpn",     R.string.tab_virtual_lan,   Icons.Filled.Wifi,    Icons.Outlined.Wifi),
        TopLevelTab("tab_settings",R.string.title_settings,     Icons.Filled.Settings,Icons.Outlined.Settings),
    )
}

private val TOP_LEVEL_ROUTES = listOf(
    MoonLinkRoute.DeviceList.route,
    "tab_vpn",
    "tab_settings",
)

// ── Title helper ──────────────────────────────────────────────────

@Composable
private fun computeTitle(context: android.content.Context, route: String?, deviceManager: DeviceStateManager, uuid: String?): String {
    val deviceName = deviceManager.getDevice(uuid ?: "")?.name ?: ""
    return when (route) {
        MoonLinkRoute.DeviceList.route       -> context.getString(R.string.tab_my_devices)
        "tab_vpn"                            -> context.getString(R.string.tab_virtual_lan)
        "tab_settings"                       -> context.getString(R.string.title_settings)
        MoonLinkRoute.DeviceOverview.route   -> deviceName.ifEmpty { context.getString(R.string.title_device_overview) }
        MoonLinkRoute.DeviceDetail.route     -> context.getString(R.string.title_device_detail)
        MoonLinkRoute.DeviceStreamSettings.route -> deviceName + context.getString(R.string.title_stream_settings)
        MoonLinkRoute.DeviceStreamSettingsTouch.route -> deviceName + context.getString(R.string.title_touch_mode)
        MoonLinkRoute.DeviceStreamSettingsDisplay.route -> deviceName + context.getString(R.string.title_display_settings)
        MoonLinkRoute.DeviceStreamSettingsSwitches.route -> deviceName + context.getString(R.string.title_display_switches)
        MoonLinkRoute.DeviceStreamSettingsHost.route -> deviceName + context.getString(R.string.category_host_settings)
        MoonLinkRoute.DeviceStreamSettingsAudio.route -> deviceName + context.getString(R.string.title_sound_settings)
        MoonLinkRoute.DeviceStreamSettingsGyro.route -> deviceName + context.getString(R.string.title_gyro)
        MoonLinkRoute.DeviceStreamSettingsOther.route -> deviceName + context.getString(R.string.title_other_settings)
        MoonLinkRoute.SettingsUi.route       -> context.getString(R.string.category_ui_settings)
        MoonLinkRoute.SettingsGamepad.route  -> context.getString(R.string.category_gamepad_settings)
        MoonLinkRoute.SettingsInput.route    -> context.getString(R.string.category_input_settings)
        MoonLinkRoute.SettingsKeyMapping.route -> context.getString(R.string.category_crown_features)
        MoonLinkRoute.SettingsHelp.route       -> context.getString(R.string.help)
        MoonLinkRoute.SettingsWidget.route     -> context.getString(R.string.category_desktop_widget)
        MoonLinkRoute.SettingsPerformance.route -> context.getString(R.string.category_performance_analytics)
        MoonLinkRoute.SettingsConnection.route  -> context.getString(R.string.category_connection_settings)
        else                                 -> ""
    }
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

// ── Background image overlay ───────────────────────────────────────────

@Composable
internal fun BackgroundOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var backgroundBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Listen for background preference changes to trigger a reload
    DisposableEffect(Unit) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                BackgroundSource.KEY_SOURCE,
                BackgroundSource.KEY_LOCAL_PATH,
                BackgroundSource.KEY_API_URL -> refreshTrigger++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Load background image from the current source
    val configuration = LocalConfiguration.current
    LaunchedEffect(refreshTrigger, configuration.orientation) {
        withContext(Dispatchers.IO) {
            try {
                val source = BackgroundSource.current(context)
                val orientation = configuration.orientation
                val target = source.resolveTarget(context, orientation)

                if (target == null) {
                    withContext(Dispatchers.Main) { backgroundBitmap = null }
                    return@withContext
                }

                val glideTarget: Any = if (target.startsWith("http")) {
                    target
                } else {
                    val f = File(target)
                    if (f.exists()) f else target
                }

                val bitmap = Glide.with(context)
                    .asBitmap()
                    .load(glideTarget)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .apply(RequestOptions.bitmapTransform(BlurTransformation(2, 3)))
                    .transform(ColorFilterTransformation(AndroidColor.argb(120, 0, 0, 0)))
                    .submit()
                    .get()

                withContext(Dispatchers.Main) {
                    backgroundBitmap = bitmap.asImageBitmap()
                }
            } catch (_: Exception) {
                // Background is a nice-to-have; never crash on failure
                withContext(Dispatchers.Main) { backgroundBitmap = null }
            }
        }
    }

    backgroundBitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
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
        ?: backStackEntry?.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsTouch.ARG_UUID)
        ?: backStackEntry?.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsSwitches.ARG_UUID)

    // Show bottom bar only on the top-level tabs
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    // Landscape detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp

    // Top bar configuration derived from current route
    val context = LocalContext.current
    val topBarTitle = computeTitle(context, currentRoute, deviceManager, currentUuid)
    val showBack = currentRoute != null && currentRoute !in TOP_LEVEL_ROUTES

    // External refresh trigger for VPN screen
    val vpnRefreshTrigger = remember { mutableIntStateOf(0) }

    // Device detail page action triggers (copies → triggers → Int increments)
    val detailCopyTrigger = remember { mutableIntStateOf(0) }
    val detailEditTrigger = remember { mutableIntStateOf(0) }

    // Snackbar host state shared across screens
    val snackbarHostState = remember { SnackbarHostState() }

    // ── NavHost content (shared between portrait and landscape) ──
    @Composable
    fun NavHostContent() {
        NavHost(
            navController = navController,
            startDestination = MoonLinkRoute.DeviceList.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(animationSpec = tween(0)) },
            exitTransition = { fadeOut(animationSpec = tween(0)) },
            popEnterTransition = { fadeIn(animationSpec = tween(0)) },
            popExitTransition = { fadeOut(animationSpec = tween(0)) },
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
            composable(MoonLinkRoute.SettingsGamepad.route) {
                GamepadSettingsScreen()
            }
            composable(MoonLinkRoute.SettingsInput.route) {
                InputSettingsScreen()
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

            // ── 连接设置 ─────────────────────────────────
            composable(MoonLinkRoute.SettingsConnection.route) {
                ConnectionSettingsScreen()
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

            // ── 串流设置页（分类列表）──────────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettings.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettings.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettings.ARG_UUID) ?: return@composable
                DeviceStreamSettingsScreen(
                    hostname = deviceManager.getDevice(uuid)?.name ?: "",
                    onNavigateToCategory = { key ->
                        navController.navigate(MoonLinkRoute.DeviceStreamSettings.subRoute(uuid, key))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // ── 串流设置 → 触控模式子页 ──────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettingsTouch.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettingsTouch.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsTouch.ARG_UUID) ?: return@composable
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { HostSettingsManager(ctx.applicationContext) }
                var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }
                TouchModeCategory(
                    settings = settings,
                    onSettingsChange = { settings = it; settingsManager.saveSettings(uuid, it) },
                )
            }

            // ── 串流设置 → 显示设置子页 ──────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettingsDisplay.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettingsDisplay.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsDisplay.ARG_UUID) ?: return@composable
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { HostSettingsManager(ctx.applicationContext) }
                var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }
                DisplayCategory(
                    settings = settings,
                    onSettingsChange = { settings = it; settingsManager.saveSettings(uuid, it) },
                )
            }

            // ── 串流设置 → 画面开关子页 ────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettingsSwitches.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettingsSwitches.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsSwitches.ARG_UUID) ?: return@composable
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { HostSettingsManager(ctx.applicationContext) }
                var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }
                DisplaySwitchesCategory(
                    settings = settings,
                    onSettingsChange = { settings = it; settingsManager.saveSettings(uuid, it) },
                )
            }

            // ── 串流设置 → 主机设置子页 ──────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettingsHost.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettingsHost.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsHost.ARG_UUID) ?: return@composable
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { HostSettingsManager(ctx.applicationContext) }
                var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }
                HostCategory(
                    settings = settings,
                    onSettingsChange = { settings = it; settingsManager.saveSettings(uuid, it) },
                )
            }

            // ── 串流设置 → 声音设置子页 ──────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettingsAudio.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettingsAudio.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsAudio.ARG_UUID) ?: return@composable
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { HostSettingsManager(ctx.applicationContext) }
                var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }
                AudioCategory(
                    settings = settings,
                    onSettingsChange = { settings = it; settingsManager.saveSettings(uuid, it) },
                )
            }

            // ── 串流设置 → 体感子页 ──────────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettingsGyro.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettingsGyro.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsGyro.ARG_UUID) ?: return@composable
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { HostSettingsManager(ctx.applicationContext) }
                var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }
                GyroCategory(
                    settings = settings,
                    onSettingsChange = { settings = it; settingsManager.saveSettings(uuid, it) },
                )
            }

            // ── 串流设置 → 其它设置子页 ──────────────────────
            composable(
                route = MoonLinkRoute.DeviceStreamSettingsOther.route,
                arguments = listOf(navArgument(MoonLinkRoute.DeviceStreamSettingsOther.ARG_UUID) {
                    type = NavType.StringType
                }),
            ) { backStack ->
                val uuid = backStack.arguments?.getString(MoonLinkRoute.DeviceStreamSettingsOther.ARG_UUID) ?: return@composable
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val settingsManager = remember { HostSettingsManager(ctx.applicationContext) }
                var settings by remember(uuid) { mutableStateOf(settingsManager.getSettings(uuid)) }
                OtherCategory(
                    settings = settings,
                    onSettingsChange = { settings = it; settingsManager.saveSettings(uuid, it) },
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
                    managerBinder = managerBinder,
                    onBack        = { navController.popBackStack() },
                    copyTrigger   = detailCopyTrigger.intValue,
                    editTrigger   = detailEditTrigger.intValue,
                )
            }
        }
    }

    // ── NavigationRail (landscape left sidebar) ──────────────────
    @Composable
    fun LandscapeNavigationRail() {
        val topTabs = rememberTopTabs(context)
        NavigationRail(
            modifier = Modifier.fillMaxHeight().width(80.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            windowInsets = WindowInsets(0.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topTabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationRailItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = context.getString(tab.titleResId),
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = { Text(context.getString(tab.titleResId), style = MaterialTheme.typography.labelSmall) },
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.cd_navigate_back))
                            }
                        }
                    },
                    actions = {
                        when (currentRoute) {
                            "tab_vpn" -> {
                                IconButton(onClick = { vpnRefreshTrigger.intValue++ }) {
                                    Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.cd_refresh_status))
                                }
                            }
                            MoonLinkRoute.DeviceDetail.route -> {
                                IconButton(onClick = { detailCopyTrigger.intValue++ }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = context.getString(R.string.copy_details))
                                }
                                IconButton(onClick = { detailEditTrigger.intValue++ }) {
                                    Icon(Icons.Default.Edit, contentDescription = context.getString(R.string.cd_edit_remote))
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
        // ── Bottom tab bar (portrait only) ──────────────────────
        bottomBar = {
            if (!isLandscape && showBottomBar) {
                val topTabs = rememberTopTabs(context)
                NavigationBar(
                    tonalElevation = 3.dp,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ) {
                    topTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = context.getString(tab.titleResId),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = {
                                Text(
                                    context.getString(tab.titleResId),
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
            // ── 横屏主页：左栏 NavigationRail + 右侧内容 ──────────
            // 顶部统一避让状态栏，内部不再有单个组件的避让设置
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                LandscapeNavigationRail()
                // Right: content area — background fills full height, content padded below
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    BackgroundOverlay()
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHostContent()
                    }
                }
            }
        } else {
            // ── 竖屏 / 横屏非主页：背景铺满，内容避开 bars ─────
            Box(modifier = Modifier.fillMaxSize()) {
                BackgroundOverlay()
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    NavHostContent()
                }
            }
        }
    }
}
