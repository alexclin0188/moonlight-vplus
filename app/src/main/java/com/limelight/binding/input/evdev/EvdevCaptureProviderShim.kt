package com.limelight.binding.input.evdev

import android.app.Activity
import com.limelight.binding.input.capture.InputCaptureProvider

object EvdevCaptureProviderShim {
    fun isCaptureProviderSupported(): Boolean {
        return false
    }

    // evdev capture provider removed (non-root build only)
    fun createEvdevCaptureProvider(activity: Activity, listener: EvdevListener): InputCaptureProvider {
        throw UnsupportedOperationException("Evdev capture provider is not available (root build removed)")
    }
}
