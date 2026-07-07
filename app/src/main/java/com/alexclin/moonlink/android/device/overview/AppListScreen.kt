package com.alexclin.moonlink.android.device.overview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.home.fetchAndCacheAppListAndBoxArt
import com.alexclin.moonlink.android.home.loadCachedAppList
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.alexclin.moonlink.android.stream.StreamActivity
import com.alexclin.moonlink.android.util.AppSettingsManager
import com.alexclin.moonlink.android.util.ServerHelper
import com.alexclin.moonlink.android.util.ToastUtil
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 新版 Compose 应用列表界面，替代旧 [com.limelight.AppView] Activity。
 *
 * 展示已配对 PC 上的全部应用，支持搜索过滤，点击启动串流。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    uuid: String,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    computers: List<ComputerDetails>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val computer = findComputer(computers, uuid)
    var appList by remember(uuid) { mutableStateOf<List<NvApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // ── 加载应用列表 ───────────────────────────────
    LaunchedEffect(uuid) {
        val computerRef = computer ?: run {
            errorMessage = context.getString(R.string.title_device_not_found)
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            var apps = loadCachedAppList(context, uuid)
            if (apps.isEmpty() && managerBinder != null &&
                computerRef.state == ComputerDetails.State.ONLINE &&
                computerRef.pairState == PairingManager.PairState.PAIRED
            ) {
                try {
                    val fetched = fetchAndCacheAppListAndBoxArt(context, computerRef, managerBinder)
                    if (!fetched.isNullOrEmpty()) {
                        apps = fetched
                    }
                } catch (_: Exception) { /* offline expected */ }
            }
            // 过滤掉 Desktop 入口（概览页已有桌面入口）
            val filtered = apps.filter {
                it.appId != NvApp.DESKTOP_APP_ID && !"Desktop".equals(it.appName, ignoreCase = true)
            }
            withContext(Dispatchers.Main) {
                appList = filtered
                if (filtered.isEmpty()) {
                    errorMessage = context.getString(R.string.label_no_apps_available)
                }
                isLoading = false
            }
        }
    }

    // ── 搜索过滤 ───────────────────────────────────
    val filteredApps = remember(appList, searchQuery) {
        if (searchQuery.isBlank()) appList
        else appList.filter { it.appName.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        computer?.name ?: context.getString(R.string.title_device_app_list),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues)
                .padding(paddingValues),
        ) {
            // ── 搜索栏 ─────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(context.getString(R.string.search_apps_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            errorMessage ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                filteredApps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            context.getString(R.string.label_no_apps_available),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(filteredApps, key = { it.appId }) { app ->
                            AppGridItem(
                                app = app,
                                uuid = uuid,
                                isRunning = computer?.runningGameId == app.appId,
                                onClick = {
                                    launchStreamFromAppList(
                                        context = context,
                                        computer = computer,
                                        managerBinder = managerBinder,
                                        app = app,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── App icon loader ─────────────────────────────────────────────────

/** Memory cache for app icons: <uuid_appId> → Bitmap */
private val appIconCache = object : LruCache<String, Bitmap>(100) {
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
            val cached = synchronized(appIconCache) { appIconCache.get(cacheKey) }
            if (cached != null) {
                bitmap = cached
                isLoaded = true
                return@withContext
            }
            val file = File(context.cacheDir, "boxart/$uuid/$appId.png")
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    synchronized(appIconCache) { appIconCache.put(cacheKey, bmp) }
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
            modifier = modifier.padding(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

// ── App grid item ─────────────────────────────────────────────────

@Composable
private fun AppGridItem(
    app: NvApp,
    uuid: String,
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
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
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = stringResource(R.string.cd_running),
                            modifier = Modifier.padding(2.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.appName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Stream launcher ───────────────────────────────────────────────

private fun launchStreamFromAppList(
    context: Context,
    computer: ComputerDetails?,
    managerBinder: ComputerManagerService.ComputerManagerBinder?,
    app: NvApp,
) {
    if (computer == null || managerBinder == null) return
    val activity = context as? android.app.Activity ?: return

    if (computer.state != com.limelight.nvstream.http.ComputerDetails.State.ONLINE) {
        ToastUtil.show(context, context.getString(R.string.toast_device_offline_waking), Toast.LENGTH_SHORT)
        return
    }

    val useLastSettings = AppSettingsManager(context).isUseLastSettingsEnabled
    val intent = com.alexclin.moonlink.android.device.overview.createStreamIntent(
        context, computer, app, managerBinder, useLastSettings
    )
    context.startActivity(intent)
}
