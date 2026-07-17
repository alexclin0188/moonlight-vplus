package com.alexclin.moonlink.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.android.stream.engine.DeviceStateManager
import com.alexclin.moonlink.android.stream.engine.StreamEngine
import com.alexclin.moonlink.android.theme.MoonLinkTheme
import com.alexclin.moonlink.android.home.ComputerManagerService
import com.alexclin.moonlink.android.util.LimeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MoonLink 新版主页 Activity — 替代 PcView 作为 App 入口。
 *
 * 使用 singleTask 启动模式，确保全局仅有一个实例。
 * StreamActivity 使用独立的 taskAffinity，使两者处于不同的 task，
 * 这样点击桌面图标恢复主页时不会清除串流 task。
 * 负责绑定 [ComputerManagerService]，通过 [DeviceStateManager] 维护设备列表，
 * 然后将设备管理器向下传递给 Compose UI 树。
 */
class MoonLinkMainActivity : BaseComponentActivity() {

    /** singleTask 下由 onNewIntent 更新的导航目标 UUID（Compose MutableState 保证响应式） */
    private val pendingNavigateToUuid = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 初始化 pending 值
        pendingNavigateToUuid.value = intent.getStringExtra("navigate_to_uuid")

        setContent {
            val binderState = remember { mutableStateOf<ComputerManagerService.ComputerManagerBinder?>(null) }
            val deviceManager = remember { DeviceStateManager() }
            var binderReady by remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        val b = service as ComputerManagerService.ComputerManagerBinder
                        binderState.value = b
                        deviceManager.bind(b, b.getAllComputers())
                        binderReady = true
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        binderState.value = null
                        binderReady = false
                    }
                }

                bindService(
                    Intent(this@MoonLinkMainActivity, ComputerManagerService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE
                )

                onDispose {
                    binderState.value?.stopPolling()
                    unbindService(connection)
                }
            }

            // ── Lifecycle 监听：Activity onResume 时强制刷新设备在线状态 ──
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        deviceManager.forceRefresh()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Read theme preference and observe changes
            val context = LocalContext.current
            val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
            var themeMode by remember { mutableStateOf(prefs.getString("list_theme_mode", "dark") ?: "dark") }

            // Observe preference changes for live theme switching
            DisposableEffect(Unit) {
                val themeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "list_theme_mode") {
                        themeMode = prefs.getString("list_theme_mode", "dark") ?: "dark"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(themeListener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(themeListener) }
            }

            // ── 运行时权限：Android 10-13 需要 ACCESS_COARSE_LOCATION ──
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* 权限结果由 JmDNSDiscoveryAgent 的 SecurityException 处理 */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
            }

            val darkTheme = when (themeMode) {
                "dark"  -> true
                "light" -> false
                else    -> isSystemInDarkTheme() // "system" → follow device
            }

            MoonLinkTheme(darkTheme = darkTheme) {
                MoonLinkApp(
                    managerBinder = binderState.value,
                    deviceManager = deviceManager,
                    onComputerRemoved = { uuid ->
                        deviceManager.devices.removeAll { it.uuid == uuid }
                    },
                    initialNavigateToUuid = pendingNavigateToUuid.value,
                )
            }
        }
    }

    /**
     * singleTask 模式下收到新 Intent 时更新导航目标。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LimeLog.info("MoonLinkMainActivity: onNewIntent 收到唤起")
        pendingNavigateToUuid.value = intent.getStringExtra("navigate_to_uuid")
    }

    /**
     * 点击桌面图标时系统恢复此 Activity 的 task。
     * 由于 StreamActivity 使用独立 taskAffinity 处于不同 task，
     * 主页恢复不会清除串流 task。
     * 如果后台有保活中的串流（极速恢复模式），自动重定向到串流页面。
     */
    override fun onResume() {
        super.onResume()

        val bgActivity = StreamEngine.currentBackgroundStreamActivity
        if (bgActivity != null) {
            // 清除引用，避免重复重定向（无论是否重定向都清除）
            StreamEngine.currentBackgroundStreamActivity = null
            if (!bgActivity.isFinishing && !bgActivity.isDestroyed) {
                LimeLog.info("MoonLinkMainActivity: 检测到后台保活串流，重定向到串流页面")
                // 启动 StreamActivity — 它是 singleTask，会复用已有实例并触发 onNewIntent
                val streamIntent = Intent(this, com.alexclin.moonlink.android.stream.StreamActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(com.alexclin.moonlink.android.stream.StreamIntentKeys.EXTRA_FROM_MAIN_REDIRECT, true)
                }
                startActivity(streamIntent)
                return
            } else {
                LimeLog.info("MoonLinkMainActivity: 后台串流已销毁，清除残留引用")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
