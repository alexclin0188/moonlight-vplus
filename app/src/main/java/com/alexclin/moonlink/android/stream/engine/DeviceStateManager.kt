package com.alexclin.moonlink.android.stream.engine

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
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

    /** 上次 collectLatest 的 Job，bind 重入时取消旧协程防止累积 */
    private var collectJob: Job? = null

    /**
     * 绑定 [ComputerManagerBinder] 并开始收集设备更新。
     *
     * 每次 bind 都会重新启动轮询和 Flow 收集，支持 Service 断开重连场景：
     * - App 后台时 Service 被系统杀死 → onServiceDisconnected → onServiceConnected
     * - 新 binder 需要重新调用 startPolling() 并建立新的 Flow 收集
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
        // 始终启动轮询并建立 Flow 收集
        // 旧 Service 的 Flow 已随 serviceScope.cancel() 终止，不重启则设备状态永不更新
        b.startPolling()
        // 兜底：进程被杀重建 / Service 重连场景下，onResume 可能在 binder 就绪前触发，
        // forceRefresh 沦为空操作。bind() 一定能拿到有效 binder，此处强制刷新一次，
        // 确保返回主页后已配对设备在几秒内更新为正确在线状态。
        b.forceRefresh()
        collectJob?.cancel()
        collectJob = scope.launch {
            // 收集设备更新（状态变化、新设备加入）
            launch {
                b.computerUpdates.collectLatest { details ->
                    val idx = devices.indexOfFirst { it.uuid == details.uuid }
                    if (idx >= 0) {
                        devices[idx] = details
                    } else {
                        devices.add(details)
                    }
                }
            }
            // 收集设备移除信号（未配对设备离线、网络丢失等）
            launch {
                b.computerRemovals.collectLatest { uuid ->
                    devices.removeAll { it.uuid == uuid }
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
        collectJob = null
        scope.cancel()
    }
}
