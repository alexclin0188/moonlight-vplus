package com.alexclin.moonlink.device.overview

import android.content.Context
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.CacheHelper
import com.limelight.nvstream.http.NvHTTP
import java.io.StringReader

/**
 * ViewModel-like holder for the overview page's data.
 * Kept as a plain class since the data comes from the shared Activity state.
 */
data class DeviceOverviewState(
    val computer: ComputerDetails,
    val appList: List<NvApp>,
    val useLastSettings: Boolean = true,
)

/**
 * Loads the cached app list for a given computer from disk.
 */
fun loadCachedAppList(context: Context, uuid: String?): List<NvApp> {
    if (uuid == null) return emptyList()
    return try {
        val stream = CacheHelper.openCacheFileForInput(context.cacheDir, "applist", uuid)
            ?: return emptyList()
        val rawXml = CacheHelper.readInputStreamToString(stream)
        if (rawXml.isNullOrBlank()) return emptyList()
        NvHTTP.getAppListByReader(StringReader(rawXml))
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Finds the computer with the given UUID from the shared list.
 */
fun findComputer(computers: List<ComputerDetails>, uuid: String): ComputerDetails? {
    return computers.find { it.uuid == uuid }
}
