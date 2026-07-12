package com.alexclin.moonlink.android.home

import android.graphics.Color as AndroidColor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.alexclin.moonlink.android.R
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 CameraX + ML Kit 的 QR 码扫描 Compose 页面。
 *
 * - 使用 CameraX [Preview] 展示相机预览
 * - 使用 [ImageAnalysis] 将帧传给 ML Kit [BarcodeScanner] 进行 QR 码检测
 * - 检测到有效 QR 码后通过 [onQrCodeScanned] 回调返回
 * - 支持跟随设备横竖屏旋转（无方向锁定）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 扫码检测锁：AtomicBoolean 保证多线程安全
    val detectionLock = remember { AtomicBoolean(false) }

    // 后台线程用于 ML Kit 处理
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // 释放后台线程
    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        context.getString(R.string.addpc_qr_scan),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.cd_navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Box(modifier = modifier.padding(innerPadding).background(Color.Black)) {
            // ── CameraX 相机预览 ────────────────────────────
            AndroidView(
                factory = { ctx ->
                PreviewView(ctx).apply {
                    // 黑色背景，防止预览加载前闪烁
                    setBackgroundColor(AndroidColor.BLACK)

                    // 异步获取 CameraProvider
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        // 1) Preview use case
                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(surfaceProvider) }

                        // 2) ML Kit BarcodeScanner（仅识别 QR 码）
                        val barcodeScanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                        )

                        // 3) ImageAnalysis use case — 将帧传递给 ML Kit
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                            // 已检测到结果则不再处理（原子检查和设置）
                            if (detectionLock.getAndSet(true)) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            processImageProxy(imageProxy, barcodeScanner) { rawValue ->
                                // 回调已在主线程（ML Kit 默认主线程回调），直接传给 Activity
                                onQrCodeScanned(rawValue)
                            }
                        }

                        // 4) 绑定到 LifecycleOwner，自动管理相机
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis,
                            )
                        } catch (e: Exception) {
                            // Camera 绑定失败（权限未获准时已 finish，此处不做特殊处理）
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

            // ── 扫码框 ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp),
                    ),
            )

            // ── 底部提示文字 ────────────────────────────────
            Text(
                text = context.getString(R.string.qr_scan_prompt),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp)
                    .padding(horizontal = 32.dp),
            )
        }
    }
}

/**
 * 处理 [ImageProxy] 帧：转成 ML Kit [InputImage] 并执行条形码检测。
 */
private fun processImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onResult: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(
        mediaImage,
        imageProxy.imageInfo.rotationDegrees,
    )

    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                barcode.rawValue?.let { value ->
                    onResult(value)
                    break // 只取第一个结果
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
