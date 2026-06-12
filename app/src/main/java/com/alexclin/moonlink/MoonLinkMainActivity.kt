package com.alexclin.moonlink

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.theme.MoonLinkTheme
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MoonLink 新版主页 Activity — 替代 PcView 作为 App 入口。
 *
 * 负责绑定 [ComputerManagerService]，收集设备更新 Flow，
 * 然后把 binder 和 computers 列表向下传递给 Compose UI 树。
 */
class MoonLinkMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val binderState = remember { mutableStateOf<ComputerManagerService.ComputerManagerBinder?>(null) }
            val computers   = remember { mutableStateListOf<ComputerDetails>() }

            // Track the collection scope so it can be cancelled on dispose
            val collectionScope = remember { mutableStateOf<kotlinx.coroutines.CoroutineScope?>(null) }

            DisposableEffect(Unit) {
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        val b = service as ComputerManagerService.ComputerManagerBinder
                        binderState.value = b
                        val snapshot = b.getAllComputers()
                        computers.addAll(snapshot)
                        b.startPolling()

                        // Collect on a scope that lives as long as the effect
                        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
                        collectionScope.value = scope
                        scope.launch {
                            b.computerUpdates.collectLatest { details ->
                                val idx = computers.indexOfFirst { it.uuid == details.uuid }
                                if (idx >= 0) {
                                    computers[idx] = details
                                } else {
                                    computers.add(details)
                                }
                            }
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        binderState.value = null
                    }
                }

                bindService(
                    Intent(this@MoonLinkMainActivity, ComputerManagerService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE
                )

                onDispose {
                    binderState.value?.stopPolling()
                    collectionScope.value?.cancel()
                    unbindService(connection)
                }
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

            val darkTheme = when (themeMode) {
                "dark"  -> true
                "light" -> false
                else    -> isSystemInDarkTheme() // "system" → follow device
            }

            MoonLinkTheme(darkTheme = darkTheme) {
                MoonLinkApp(
                    managerBinder = binderState.value,
                    computers     = computers,
                )
            }
        }
    }
}
