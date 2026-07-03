package com.alexclin.moonlink.android.device.overview

import android.content.Context
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.alexclin.moonlink.android.util.CacheHelper
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
 * Finds the computer with the given UUID from the shared list.
 */
fun findComputer(computers: List<ComputerDetails>, uuid: String): ComputerDetails? {
    return computers.find { it.uuid == uuid }
}
