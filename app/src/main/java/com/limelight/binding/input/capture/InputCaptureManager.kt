package com.limelight.binding.input.capture

import android.app.Activity
import com.alexclin.moonlink.android.util.LimeLog
import com.alexclin.moonlink.android.R
import com.limelight.binding.input.evdev.EvdevListener

object InputCaptureManager {
    fun getInputCaptureProvider(activity: Activity, rootListener: EvdevListener): InputCaptureProvider {
        return when {
            AndroidNativePointerCaptureProvider.isCaptureProviderSupported() -> {
                LimeLog.info("Using Android O+ native mouse capture")
                AndroidNativePointerCaptureProvider(activity, activity.findViewById(R.id.surfaceView))
            }
            ShieldCaptureProvider.isCaptureProviderSupported() -> {
                LimeLog.info("Using NVIDIA mouse capture extension")
                ShieldCaptureProvider(activity)
            }
            AndroidPointerIconCaptureProvider.isCaptureProviderSupported() -> {
                // Android N's native capture can't capture over system UI elements
                // so we want to only use it if there's no other option.
                LimeLog.info("Using Android N+ pointer hiding")
                AndroidPointerIconCaptureProvider(activity, activity.findViewById(R.id.surfaceView))
            }
            else -> {
                LimeLog.info("Mouse capture not available")
                NullCaptureProvider()
            }
        }
    }

    /**
     * 获取支持外接显示器的输入捕获提供者
     */
    fun getInputCaptureProviderForExternalDisplay(activity: Activity, rootListener: EvdevListener): InputCaptureProvider {
        return getInputCaptureProvider(activity, rootListener)
    }
}
