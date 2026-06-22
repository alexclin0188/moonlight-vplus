package com.alexclin.moonlink.android.stream.engine

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 设备在线状态管理器。
 *
 * 维护所有已发现设备的 Compose 可观察列表 [devices]，
 * 通过订阅 [ComputerManagerService.ComputerManagerBinder.computerUpdates] 自动更新。
 *
 * 使用方式：
 * ```
 * val mgr = DeviceStateManager(context, binder)
 * val list: List<ComputerDetails> = mgr.devices  // Compose 可观察
 * ```
 */
class DeviceStateManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    /** Compose 可观察的设备列表 — UI 层可直接读取 */
    val devices: SnapshotStateList<ComputerDetails> = mutableStateListOf()

    /** 当前绑定的 ComputerManagerBinder，用于调用 forceRefresh/add/remove 等 */
    var binder: ComputerManagerService.ComputerManagerBinder? = null
        private set

    /** 是否已开始收集设备更新 */
    private var collecting = false

    /**
     * 绑定 [ComputerManagerBinder] 并开始收集设备更新。
     *
     * @param b 已连接的 ComputerManagerBinder
     * @param initialDevices 初始设备列表（来自 binder.getAllComputers()）
     */
    fun bind(b: ComputerManagerService.ComputerManagerBinder, initialDevices: List<ComputerDetails>? = null) {
        binder = b
        if (initialDevices != null) {
            devices.clear()
            devices.addAll(initialDevices)
        }
        if (!collecting) {
            collecting = true
            b.startPolling()
            scope.launch {
                b.computerUpdates.collectLatest { details ->
                    val idx = devices.indexOfFirst { it.uuid == details.uuid }
                    if (idx >= 0) {
                        devices[idx] = details
                    } else {
                        devices.add(details)
                    }
                }
            }
        }
    }

    /** 按 uuid 查找设备，未找到返回 null */
    fun getDevice(uuid: String): ComputerDetails? {
        return devices.firstOrNull { it.uuid == uuid }
    }

    /** 获取某个 uuid 设备的 Compose 可观察状态快照（返回当前引用，UI 通过 devices 列表整体观察） */
    fun findDeviceIndex(uuid: String): Int {
        return devices.indexOfFirst { it.uuid == uuid }
    }

    /** 强制刷新所有设备在线状态（委托给 binder.forceRefresh） */
    fun forceRefresh() {
        binder?.forceRefresh()
    }

    /** 清理资源 */
    fun release() {
        binder?.stopPolling()
        binder = null
        collecting = false
        scope.cancel()
    }
}
