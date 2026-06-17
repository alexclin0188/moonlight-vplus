package com.alexclin.moonlink.stream.ui.display

import kotlin.math.pow
import kotlin.math.roundToInt

object BitrateUtils {
    // 码率预设值 (kbps)
    const val BITRATE_AUTO = 0
    const val BITRATE_2M = 2000       // 2M(清晰)
    const val BITRATE_8M = 8000       // 8M(高清)
    const val BITRATE_20M = 20000     // 20M(原画)
    const val BITRATE_CUSTOM_MIN = 1000    // 自定义最小值 1M
    const val BITRATE_CUSTOM_MAX = 800000  // 自定义最大值 800M

    enum class BitratePreset(val label: String, val kbps: Int) {
        AUTO("自动", BITRATE_AUTO),
        M2("2M(清晰)", BITRATE_2M),
        M8("8M(高清)", BITRATE_8M),
        M20("20M(原画)", BITRATE_20M),
        CUSTOM("自定义", -1),
    }

    /**
     * 根据码率值(kbps)判断对应的预设模式
     */
    fun getPresetByKbps(kbps: Int): BitratePreset = when {
        kbps <= 1 -> BitratePreset.AUTO
        kbps <= 3000 -> BitratePreset.M2
        kbps <= 15000 -> BitratePreset.M8
        kbps <= 50000 -> BitratePreset.M20
        else -> BitratePreset.CUSTOM
    }

    /**
     * 流量估算：kbps / 8 / 60 = MB/分钟
     */
    fun estimateTrafficMbPerMin(bitrateKbps: Int): Float =
        bitrateKbps.toFloat() / 8f / 60f

    /**
     * 格式化的流量估算文字
     */
    fun formatTrafficEstimate(bitrateKbps: Int): String {
        val mbPerMin = estimateTrafficMbPerMin(bitrateKbps)
        return if (mbPerMin >= 10) {
            "使用流量时,预估消耗 ${mbPerMin.toInt()}M/分钟"
        } else {
            "使用流量时,预估消耗 ${"%.1f".format(mbPerMin)}M/分钟"
        }
    }

    /**
     * 自定义SeekBar指数映射: progress(0~100) → kbps(1M~800M)
     * 前段变化慢（低码率精细调节），后段变化快（高码率快速跨越）。
     * 公式: kbps = min * (max/min)^(progress/100)
     */
    fun customProgressToKbps(progress: Int): Int {
        val ratio = progress.coerceIn(0, 100) / 100f
        val result = BITRATE_CUSTOM_MIN * (BITRATE_CUSTOM_MAX.toFloat() / BITRATE_CUSTOM_MIN).pow(ratio)
        return result.roundToInt().coerceIn(BITRATE_CUSTOM_MIN, BITRATE_CUSTOM_MAX)
    }

    /**
     * 自定义SeekBar指数映射反向: kbps(1M~800M) → progress(0~100)
     * 公式: progress = ln(kbps/min) / ln(max/min) * 100
     */
    fun customKbpsToProgress(kbps: Int): Int {
        val clamped = kbps.coerceIn(BITRATE_CUSTOM_MIN, BITRATE_CUSTOM_MAX).toFloat()
        val ratio = (kotlin.math.ln(clamped / BITRATE_CUSTOM_MIN)
            / kotlin.math.ln(BITRATE_CUSTOM_MAX.toFloat() / BITRATE_CUSTOM_MIN))
        return (ratio * 100f).roundToInt().coerceIn(0, 100)
    }

    /**
     * 将码率值格式化为可读的 Mbps 字符串
     */
    fun formatBitrateMbps(kbps: Int): String {
        return when {
            kbps < 1000 -> "${kbps} kbps"
            kbps % 1000 == 0 -> "${kbps / 1000} Mbps"
            else -> "${"%.1f".format(kbps / 1000f)} Mbps"
        }
    }
}
