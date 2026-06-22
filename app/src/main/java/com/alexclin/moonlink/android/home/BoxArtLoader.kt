package com.alexclin.moonlink.android.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.limelight.computers.ComputerManagerService
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.utils.CacheHelper
import com.limelight.utils.ServerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/**
 * Module-level memory cache for device box art, keyed by computer UUID.
 * Shared across all recompositions.
 */
private val boxArtMemoryCache = object : LruCache<String, Bitmap>(50) {
    override fun sizeOf(key: String, value: Bitmap): Int = 1
}
private val loadingUuids = ConcurrentHashMap.newKeySet<String>()

/**
 * Invalidate the in-memory box art cache for a given UUID.
 * Call this after downloading new box art to disk so that the next
 * [DeviceBoxArt] composition re-reads from disk instead of stale memory.
 */
fun invalidateBoxArtCache(uuid: String) {
    synchronized(boxArtMemoryCache) { boxArtMemoryCache.remove(uuid) }
    loadingUuids.remove(uuid)
}

/**
 * Composable that displays the box art thumbnail for a device.
 * Uses a three-tier loading strategy matching the old PcGridAdapter:
 * 1. Memory cache (instant)
 * 2. Disk cache (async, on IO dispatcher)
 * 3. Placeholder icon (if no cache available)
 */
@Composable
fun DeviceBoxArt(
    uuid: String?,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    clipShape: RoundedCornerShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    refreshKey: Int = 0,
) {
    val context = LocalContext.current
    var bitmap by remember(uuid) { mutableStateOf<Bitmap?>(null) }

    // Async load from disk if not in memory cache
    LaunchedEffect(uuid, isOnline, refreshKey) {
        if (uuid == null) return@LaunchedEffect
        if (refreshKey == 0 && bitmap != null) return@LaunchedEffect
        if (uuid in loadingUuids) return@LaunchedEffect

        // Check memory cache first
        synchronized(boxArtMemoryCache) { boxArtMemoryCache.get(uuid) }?.let {
            bitmap = it
            return@LaunchedEffect
        }

        // Load from disk asynchronously
        loadingUuids.add(uuid)
        try {
            val loaded = withContext(Dispatchers.IO) {
                loadBoxArtFromDisk(context, uuid)
            }
            if (loaded != null) {
                synchronized(boxArtMemoryCache) { boxArtMemoryCache.put(uuid, loaded) }
                bitmap = loaded
            }
        } finally {
            loadingUuids.remove(uuid)
        }
    }

    Box(
        modifier = modifier
            .clip(clipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null && !bitmap!!.isRecycled) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "桌面缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.DesktopWindows,
                contentDescription = "桌面",
                modifier = Modifier.fillMaxSize(0.4f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isOnline) 0.6f else 0.3f
                ),
            )
        }
    }
}

/**
 * Loads the first valid box art from disk cache for a given computer UUID.
 * Path: <cacheDir>/applist/<uuid> (XML) → <cacheDir>/boxart/<uuid>/<appId>.png
 */
private fun loadBoxArtFromDisk(context: Context, uuid: String): Bitmap? {
    return try {
        val rawAppList = CacheHelper.readInputStreamToString(
            CacheHelper.openCacheFileForInput(context.cacheDir, "applist", uuid)
        )
        if (rawAppList.isNullOrBlank()) return null

        val appList: List<NvApp> = NvHTTP.getAppListByReader(StringReader(rawAppList))
        if (appList.isEmpty()) return null

        for (app in appList) {
            val boxArtFile = CacheHelper.openPath(
                false, context.cacheDir, "boxart", uuid, "${app.appId}.png"
            )
            if (!boxArtFile.exists() || boxArtFile.length() == 0L) continue

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(boxArtFile.absolutePath, options)
            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            options.inJustDecodeBounds = false

            val bmp = BitmapFactory.decodeFile(boxArtFile.absolutePath, options)
            if (bmp != null) return bmp
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun calculateSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    val targetSize = 128
    if (height > targetSize || width > targetSize) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / sampleSize >= targetSize && halfWidth / sampleSize >= targetSize) {
            sampleSize *= 2
        }
    }
    return sampleSize
}

/**
 * 从主机拉取 app list 并下载所有 box art 缩略图到磁盘缓存。
 *
 * 被 [DeviceOverviewScreen] 和 [DeviceListScreen] 复用，
 * 消除两处的重复代码。
 *
 * @return 拉取到的 app 列表；如果网络请求失败或条件不满足则返回 null。
 */
suspend fun fetchAndCacheAppListAndBoxArt(
    context: Context,
    computer: ComputerDetails,
    managerBinder: ComputerManagerService.ComputerManagerBinder,
): List<NvApp>? {
    return withContext(Dispatchers.IO) {
        try {
            val address = ServerHelper.getCurrentAddressFromComputer(computer)
            val http = NvHTTP(
                address,
                computer.httpsPort,
                managerBinder.getUniqueId(),
                android.os.Build.MODEL,
                computer.serverCert,
                PlatformBinding.getCryptoProvider(context)
            )
            val rawXml = http.getAppListRaw()
            val apps = NvHTTP.getAppListByReader(StringReader(rawXml))
            val uuid = computer.uuid ?: return@withContext null

            // 写入 app list 缓存
            CacheHelper.openCacheFileForOutput(
                context.cacheDir, "applist", uuid
            ).use { out ->
                CacheHelper.writeStringToOutputStream(out, rawXml)
            }

            // 下载 box art 到磁盘
            for (app in apps) {
                val boxArtFile = CacheHelper.openPath(
                    false, context.cacheDir, "boxart", uuid, "${app.appId}.png"
                )
                if (boxArtFile.exists() && boxArtFile.length() > 0L) continue
                try {
                    http.getBoxArt(app)?.use { stream ->
                        CacheHelper.openCacheFileForOutput(
                            context.cacheDir, "boxart", uuid, "${app.appId}.png"
                        ).use { out ->
                            CacheHelper.writeInputStreamToOutputStream(stream, out, 20L * 1024 * 1024)
                        }
                    }
                } catch (_: Exception) { /* 跳过单个 app 的封面失败 */ }
            }

            apps
        } catch (_: Exception) {
            null
        }
    }
}
