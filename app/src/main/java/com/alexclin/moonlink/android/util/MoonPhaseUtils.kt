package com.alexclin.moonlink.android.util

import java.util.Calendar
import java.util.TimeZone

/**
 * 月相工具类
 * 提供月相计算、图标获取、信息查询等功能
 */
object MoonPhaseUtils {

    class MoonPhaseInfo(
        val poeticTitle: String,
        val name: String,
        val description: String,
        val icon: String
    )

    enum class MoonPhaseType(val displayName: String, private val minPhase: Double, private val maxPhase: Double) {
        NEW_MOON("New Moon", 0.0, 0.0625),
        WAXING_CRESCENT("Waxing Crescent", 0.0625, 0.1875),
        FIRST_QUARTER("First Quarter", 0.1875, 0.3125),
        WAXING_GIBBOUS("Waxing Gibbous", 0.3125, 0.4375),
        FULL_MOON("Full Moon", 0.4375, 0.5625),
        WANING_GIBBOUS("Waning Gibbous", 0.5625, 0.6875),
        LAST_QUARTER("Last Quarter", 0.6875, 0.8125),
        WANING_CRESCENT("Waning Crescent", 0.8125, 0.9375);

        fun isInRange(phase: Double): Boolean = phase in minPhase..<maxPhase
    }

    /**
     * 计算月相（0-1，0为新月，0.5为满月）
     * 使用简化的天文算法
     */
    fun calculateMoonPhase(date: Calendar): Double {
        val baseDate = Calendar.getInstance().apply {
            set(2000, Calendar.JANUARY, 6, 18, 14, 0)
        }
        val timeDiff = date.timeInMillis - baseDate.timeInMillis
        val daysDiff = timeDiff / (24.0 * 60.0 * 60.0 * 1000.0)
        val moonCycle = 29.530588853
        var phase = (daysDiff % moonCycle) / moonCycle
        if (phase < 0) phase += 1.0
        return phase
    }

    fun getCurrentMoonPhase(): Double =
        calculateMoonPhase(Calendar.getInstance(TimeZone.getDefault()))

    fun getMoonPhaseType(phase: Double): MoonPhaseType =
        MoonPhaseType.entries.firstOrNull { it.isInRange(phase) } ?: MoonPhaseType.NEW_MOON

    fun getMoonPhaseIcon(phase: Double): String = when (getMoonPhaseType(phase)) {
        MoonPhaseType.NEW_MOON -> "🌑"
        MoonPhaseType.WAXING_CRESCENT -> "🌒"
        MoonPhaseType.FIRST_QUARTER -> "🌓"
        MoonPhaseType.WAXING_GIBBOUS -> "🌔"
        MoonPhaseType.FULL_MOON -> "🌕"
        MoonPhaseType.WANING_GIBBOUS -> "🌖"
        MoonPhaseType.LAST_QUARTER -> "🌗"
        MoonPhaseType.WANING_CRESCENT -> "🌘"
    }

    fun getMoonPhasePoeticTitle(phase: Double): String = when (getMoonPhaseType(phase)) {
        MoonPhaseType.NEW_MOON -> "🌑 New Moon · New Beginnings"
        MoonPhaseType.WAXING_CRESCENT -> "🌒 Crescent Appears · Hope Grows"
        MoonPhaseType.FIRST_QUARTER -> "🌓 First Quarter · Balance"
        MoonPhaseType.WAXING_GIBBOUS -> "🌔 Waxing Gibbous · Harvest Beckons"
        MoonPhaseType.FULL_MOON -> "🌕 Full Moon · Culmination"
        MoonPhaseType.WANING_GIBBOUS -> "🌖 Waning Gibbous · Gratitude"
        MoonPhaseType.LAST_QUARTER -> "🌗 Last Quarter · Reflection"
        MoonPhaseType.WANING_CRESCENT -> "🌘 Waning Crescent · Renewal"
    }

    fun getMoonPhaseDescription(phase: Double): String = when (getMoonPhaseType(phase)) {
        MoonPhaseType.NEW_MOON -> "The moon is aligned with the sun, invisible.\nSymbolizes new beginnings and rebirth."
        MoonPhaseType.WAXING_CRESCENT -> "The right side of the moon begins to glow.\nSymbolizes growth and budding hope."
        MoonPhaseType.FIRST_QUARTER -> "Half of the moon is illuminated.\nSymbolizes balance and decision-making."
        MoonPhaseType.WAXING_GIBBOUS -> "Most of the moon is illuminated.\nSymbolizes approaching fullness and harvest."
        MoonPhaseType.FULL_MOON -> "The moon is fully illuminated.\nSymbolizes completion, achievement and celebration."
        MoonPhaseType.WANING_GIBBOUS -> "The moon begins to darken.\nSymbolizes release and gratitude."
        MoonPhaseType.LAST_QUARTER -> "Half of the moon is dark.\nSymbolizes reflection and introspection."
        MoonPhaseType.WANING_CRESCENT -> "The moon is nearly invisible.\nSymbolizes endings and preparing for a new cycle."
    }

    fun getMoonPhaseInfo(phase: Double): MoonPhaseInfo = MoonPhaseInfo(
        poeticTitle = getMoonPhasePoeticTitle(phase),
        name = getMoonPhaseType(phase).displayName,
        description = getMoonPhaseDescription(phase),
        icon = getMoonPhaseIcon(phase)
    )

    fun getCurrentMoonPhaseInfo(): MoonPhaseInfo = getMoonPhaseInfo(getCurrentMoonPhase())

    fun getMoonPhasePercentage(phase: Double): Double = phase * 100

    fun getDaysInMoonCycle(phase: Double): Int = (phase * 29.530588853).toInt()

    fun isFullMoon(phase: Double, tolerance: Double): Boolean =
        Math.abs(phase - 0.5) < tolerance

    fun isNewMoon(phase: Double, tolerance: Double): Boolean =
        phase < tolerance || phase > (1.0 - tolerance)
}
