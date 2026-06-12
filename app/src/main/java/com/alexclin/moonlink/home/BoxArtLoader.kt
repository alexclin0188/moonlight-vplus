package com.alexclin.moonlink.home

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
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.utils.CacheHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Module-level memory cache for device box art, keyed by computer UUID.
 * Shared across all recompositions.
 */
private val boxArtMemoryCache = ConcurrentHashMap<String, Bitmap>()
private val loadingUuids = ConcurrentHashMap.newKeySet<String>()

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
) {
    val context = LocalContext.current
    var bitmap by remember(uuid) { mutableStateOf<Bitmap?>(null) }

    // Async load from disk if not in memory cache
    LaunchedEffect(uuid, isOnline) {
        if (uuid == null || bitmap != null) return@LaunchedEffect
        if (uuid in loadingUuids) return@LaunchedEffect

        // Check memory cache first
        boxArtMemoryCache[uuid]?.let {
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
                boxArtMemoryCache[uuid] = loaded
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
