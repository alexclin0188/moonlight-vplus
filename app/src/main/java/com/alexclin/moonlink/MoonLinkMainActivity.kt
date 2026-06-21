package com.alexclin.moonlink

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
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.stream.engine.DeviceStateManager
import com.alexclin.moonlink.theme.MoonLinkTheme
import com.limelight.computers.ComputerManagerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MoonLink 新版主页 Activity — 替代 PcView 作为 App 入口。
 *
 * 负责绑定 [ComputerManagerService]，通过 [DeviceStateManager] 维护设备列表，
 * 然后将设备管理器向下传递给 Compose UI 树。
 */
class MoonLinkMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "list_theme_mode") {
                        themeMode = prefs.getString("list_theme_mode", "dark") ?: "dark"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
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
                )
            }
        }
    }
}
