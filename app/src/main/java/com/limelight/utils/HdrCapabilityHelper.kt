@file:Suppress("DEPRECATION")
package com.limelight.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings

/**
 * HDR capability helpers — restored minimal version for device capability diagnostics
 * and brightness reporting to the streaming server.
 */
object HdrCapabilityHelper {

    /** Brightness range result for server-side reporting. */
    data class BrightnessRange(val min: Float, val max: Float, val avg: Float) {
        fun toInts(): IntArray = intArrayOf(min.toInt(), max.toInt(), avg.toInt())
    }

    /** HDR type support flags. */
    data class HdrTypeSupport(
        val hasDolbyVision: Boolean = false,
        val hasHdr10: Boolean = false,
        val hasHlg: Boolean = false,
        val hasHdr10Plus: Boolean = false,
    ) {
        val rawTypes: List<Int> get() = buildList {
            if (hasDolbyVision) add(android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
            if (hasHdr10) add(android.view.Display.HdrCapabilities.HDR_TYPE_HDR10)
            if (hasHlg) add(android.view.Display.HdrCapabilities.HDR_TYPE_HLG)
            if (hasHdr10Plus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                add(android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS)
        }
    }

    /** Full HDR capability info. */
    data class FullCapabilityInfo(
        val isScreenHdr: Boolean = false,
        val isWideColorGamut: Boolean = false,
        val typeSupport: HdrTypeSupport = HdrTypeSupport(),
        val brightness: BrightnessInfo = BrightnessInfo(),
    )

    /** Brightness details. */
    data class BrightnessInfo(
        val maxLuminance: Float = 0f,
        val minLuminance: Float = 0f,
        val maxAvgLuminance: Float = 0f,
        val isDefault: Boolean = true,
        val isFromHdrCaps: Boolean = false,
        val isHdrSdrRatioAvailable: Boolean = false,
        val hdrSdrRatio: Float = 0f,
        val highestHdrSdrRatio: Float = 0f,
        val isComputedFromRatio: Boolean = false,
        val computedPeakBrightness: Float = 0f,
    )

    /**
     * Get full HDR capability information for this device.
     */
    fun getFullCapabilityInfo(context: Context): FullCapabilityInfo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return FullCapabilityInfo()
        }

        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(0)
            ?: return FullCapabilityInfo()

        val hdrCaps = display.hdrCapabilities ?: return FullCapabilityInfo()
        val supportedTypes = hdrCaps.supportedHdrTypes.toSet()

        val typeSupport = HdrTypeSupport(
            hasDolbyVision = supportedTypes.contains(android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION),
            hasHdr10 = supportedTypes.contains(android.view.Display.HdrCapabilities.HDR_TYPE_HDR10),
            hasHlg = supportedTypes.contains(android.view.Display.HdrCapabilities.HDR_TYPE_HLG),
            hasHdr10Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    supportedTypes.contains(android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS),
        )

        val isScreenHdr = typeSupport.rawTypes.isNotEmpty()

        val isWideColorGamut = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            displayManager.getDisplay(0)?.isWideColorGamut ?: false
        } else false

        val brightness = computeBrightnessInfo(display, hdrCaps)

        return FullCapabilityInfo(
            isScreenHdr = isScreenHdr,
            isWideColorGamut = isWideColorGamut,
            typeSupport = typeSupport,
            brightness = brightness,
        )
    }

    /**
     * Get [min, max, avg] brightness values for server-side reporting.
     */
    fun getBrightnessRangeAsInts(context: Context): IntArray {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(0)
            ?: return intArrayOf(0, 500, 300)

        val hdrCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            display.hdrCapabilities
        } else null

        val info = if (hdrCaps != null) {
            computeBrightnessInfo(display, hdrCaps)
        } else BrightnessInfo()

        return intArrayOf(
            info.minLuminance.toInt(),
            info.maxLuminance.toInt().coerceAtLeast(1),
            info.maxAvgLuminance.toInt().coerceAtLeast(1),
        )
    }

    /**
     * Get current system brightness level (0..255), or -1 if unavailable.
     */
    fun getSystemBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Check if auto-brightness is enabled.
     */
    fun isAutoBrightnessEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE
                ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else false
        } catch (_: Exception) {
            false
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    @SuppressLint("NewApi")
    private fun computeBrightnessInfo(
        display: android.view.Display,
        hdrCaps: android.view.Display.HdrCapabilities
    ): BrightnessInfo {
        val maxLum = hdrCaps.desiredMaxLuminance.toFloat()
        val minLum = hdrCaps.desiredMinLuminance.toFloat()
        val avgLum = hdrCaps.desiredMaxAverageLuminance.toFloat()
        val defaultLum = maxLum <= 0f || maxLum > 10000f

        return BrightnessInfo(
            maxLuminance = if (defaultLum) 500f else maxLum,
            minLuminance = if (defaultLum) 0.5f else minLum,
            maxAvgLuminance = if (defaultLum) 300f else avgLum,
            isDefault = defaultLum,
            isFromHdrCaps = !defaultLum,
            isHdrSdrRatioAvailable = false,
            hdrSdrRatio = 0f,
            highestHdrSdrRatio = 0f,
        )
    }
}
