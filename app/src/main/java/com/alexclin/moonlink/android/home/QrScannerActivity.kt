package com.alexclin.moonlink.android.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.theme.MoonLinkTheme

/**
 * 基于 CameraX + ML Kit 的 QR 码扫描 Activity。
 *
 * 替换旧的 [com.journeyapps.barcodescanner.ScanContract] 实现，
 * 使用 Compose 构建自定义扫码 UI，支持横竖屏跟随。
 *
 * 启动方式：
 * ```
 * val intent = Intent(context, QrScannerActivity::class.java)
 * launcher.launch(intent)
 * ```
 *
 * 返回结果通过 Intent extra "SCAN_RESULT" 获取扫码文本。
 */
class QrScannerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCAN_RESULT = "SCAN_RESULT"
    }

    private val permissionGranted = mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted.value = granted
        if (!granted) {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 运行时检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            permissionGranted.value = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MoonLinkTheme {
                // 权限未获准时不渲染相机预览（权限请求完成后自动重组触发渲染）
                if (permissionGranted.value) {
                    QrScannerScreen(
                        onQrCodeScanned = { result ->
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(EXTRA_SCAN_RESULT, result)
                            )
                            finish()
                        },
                        onBack = { finish() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
