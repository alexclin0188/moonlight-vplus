package com.alexclin.moonlink.android.util

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.alexclin.moonlink.android.BuildConfig

/**
 * Crashlytics 管理器（全局单例）。
 *
 * 职责：
 * - 封装 Firebase Crashlytics 操作
 * - 提供自定义键值对设置（崩溃时显示上下文信息）
 * - 提供日志面包屑记录（崩溃时显示操作路径）
 * - 提供非致命异常上报（错误分类和统计）
 * - 无 GMS 设备自动降级
 *
 * 设计要点：
 * - 所有操作 try-catch 包裹，无 GMS 设备不会崩溃
 * - debug 构建禁用所有 Crashlytics 操作
 * - 用户可通过 SharedPreferences 手动关闭 Analytics（与 AnalyticsManager 共享设置）
 * - 异步执行，不影响主线程
 *
 * ⚠️ 重要：
 * - 不要上报隐私敏感信息（如用户输入、密码等）
 * - 日志频率必须限制，避免影响性能
 * - 使用 [Crashlytics.log] 记录面包屑，使用 [Crashlytics.recordException] 上报异常
 *
 * @see <a href="https://firebase.google.com/docs/crashlytics">Firebase Crashlytics 文档</a>
 */
class CrashlyticsManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    init {
        // debug 构建禁用 Crashlytics
        if (BuildConfig.DEBUG) {
            crashlytics.setCrashlyticsCollectionEnabled(false)
            Log.d(TAG, "Crashlytics disabled in debug build")
        } else {
            // 检查用户是否禁用了 Analytics
            try {
                val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
                val analyticsEnabled = prefs.getBoolean("checkbox_enable_analytics", true)
                crashlytics.setCrashlyticsCollectionEnabled(analyticsEnabled)
                if (!analyticsEnabled) {
                    Log.d(TAG, "Crashlytics disabled by user preference")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check analytics preference: ${e.message}")
            }
        }
    }

    // ── 自定义键值对 ──────────────────────────────────────────

    /**
     * 设置用户状态键值对（崩溃时显示用户类型）。
     *
     * @param userTier 用户等级："free" 或 "premium"
     * @param firstOpenDate 首次打开日期（格式：yyyy-MM-dd）
     * @param hasCompletedFirstStream 是否完成首次串流
     */
    fun setUserKeys(
        userTier: String,
        firstOpenDate: String? = null,
        hasCompletedFirstStream: Boolean = false,
    ) {
        if (!canExecuteCrashlytics()) return
        try {
            crashlytics.setCustomKey(KEY_USER_TIER, userTier)
            if (firstOpenDate != null) {
                crashlytics.setCustomKey(KEY_FIRST_OPEN_DATE, firstOpenDate)
            }
            crashlytics.setCustomKey(KEY_HAS_COMPLETED_FIRST_STREAM, hasCompletedFirstStream)
            Log.d(TAG, "User keys set: tier=$userTier, firstOpen=$firstOpenDate, firstStream=$hasCompletedFirstStream")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set user keys: ${e.message}")
        }
    }

    /**
     * 设置广告状态键值对（崩溃时显示广告上下文）。
     *
     * @param adInitialized 广告 SDK 是否已初始化
     * @param lastAdType 最后展示的广告类型："app_open" 或 "interstitial"
     * @param adLoadFailed 广告加载是否失败
     * @param consentStatus 同意状态："required"、"not_required" 或 "obtained"
     */
    fun setAdKeys(
        adInitialized: Boolean = false,
        lastAdType: String? = null,
        adLoadFailed: Boolean = false,
        consentStatus: String? = null,
    ) {
        if (!canExecuteCrashlytics()) return
        try {
            crashlytics.setCustomKey(KEY_AD_INITIALIZED, adInitialized)
            if (lastAdType != null) {
                crashlytics.setCustomKey(KEY_LAST_AD_TYPE, lastAdType)
            }
            crashlytics.setCustomKey(KEY_AD_LOAD_FAILED, adLoadFailed)
            if (consentStatus != null) {
                crashlytics.setCustomKey(KEY_CONSENT_STATUS, consentStatus)
            }
            Log.d(TAG, "Ad keys set: initialized=$adInitialized, lastType=$lastAdType, loadFailed=$adLoadFailed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set ad keys: ${e.message}")
        }
    }

    /**
     * 设置计费状态键值对（崩溃时显示计费上下文）。
     *
     * @param billingConnected BillingClient 是否已连接
     * @param productDetailsLoaded 商品详情是否已加载
     * @param lastPurchaseState 最后购买状态："purchased"、"pending"、"failed" 或 null
     */
    fun setBillingKeys(
        billingConnected: Boolean = false,
        productDetailsLoaded: Boolean = false,
        lastPurchaseState: String? = null,
    ) {
        if (!canExecuteCrashlytics()) return
        try {
            crashlytics.setCustomKey(KEY_BILLING_CONNECTED, billingConnected)
            crashlytics.setCustomKey(KEY_PRODUCT_DETAILS_LOADED, productDetailsLoaded)
            if (lastPurchaseState != null) {
                crashlytics.setCustomKey(KEY_LAST_PURCHASE_STATE, lastPurchaseState)
            }
            Log.d(TAG, "Billing keys set: connected=$billingConnected, detailsLoaded=$productDetailsLoaded")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set billing keys: ${e.message}")
        }
    }

    /**
     * 设置串流状态键值对（崩溃时显示串流上下文）。
     *
     * @param decoderName 解码器名称
     * @param resolution 分辨率（如 "1920x1080"）
     * @param fps 帧率
     * @param networkType 网络类型："wifi"、"mobile"、"ethernet"
     * @param networkQuality 网络质量："excellent"、"good"、"fair"、"poor"
     */
    fun setStreamKeys(
        decoderName: String? = null,
        resolution: String? = null,
        fps: Int? = null,
        networkType: String? = null,
        networkQuality: String? = null,
    ) {
        if (!canExecuteCrashlytics()) return
        try {
            if (decoderName != null) {
                crashlytics.setCustomKey(KEY_DECODER_NAME, decoderName)
            }
            if (resolution != null) {
                crashlytics.setCustomKey(KEY_RESOLUTION, resolution)
            }
            if (fps != null) {
                crashlytics.setCustomKey(KEY_FPS, fps)
            }
            if (networkType != null) {
                crashlytics.setCustomKey(KEY_NETWORK_TYPE, networkType)
            }
            if (networkQuality != null) {
                crashlytics.setCustomKey(KEY_NETWORK_QUALITY, networkQuality)
            }
            Log.d(TAG, "Stream keys set: decoder=$decoderName, resolution=$resolution, fps=$fps")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set stream keys: ${e.message}")
        }
    }

    // ── 日志面包屑 ──────────────────────────────────────────

    /**
     * 记录日志面包屑（崩溃时显示操作路径）。
     *
     * @param message 日志消息
     */
    fun log(message: String) {
        if (!canExecuteCrashlytics()) return
        try {
            crashlytics.log(message)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log breadcrumb: ${e.message}")
        }
    }

    // ── 非致命异常上报 ──────────────────────────────────────

    /**
     * 上报非致命异常（错误分类和统计）。
     *
     * @param throwable 异常对象
     */
    fun recordException(throwable: Throwable) {
        if (!canExecuteCrashlytics()) return
        try {
            crashlytics.recordException(throwable)
            Log.d(TAG, "Exception recorded: ${throwable.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record exception: ${e.message}")
        }
    }

    /**
     * 上报广告错误（结构化错误分类）。
     *
     * @param errorType 错误类型："load_failed" 或 "show_failed"
     * @param adType 广告类型："app_open" 或 "interstitial"
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     */
    fun logAdError(
        errorType: String,
        adType: String,
        errorCode: Int,
        errorMessage: String,
    ) {
        if (!canExecuteCrashlytics()) return
        try {
            val exception = AdException(errorType, adType, errorCode, errorMessage)
            recordException(exception)
            Log.w(TAG, "Ad error logged: $errorType, $adType, code=$errorCode, msg=$errorMessage")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log ad error: ${e.message}")
        }
    }

    /**
     * 上报计费错误（结构化错误分类）。
     *
     * @param errorType 错误类型："connection_failed"、"query_failed" 或 "purchase_failed"
     * @param responseCode 响应码
     * @param debugMessage 调试消息
     */
    fun logBillingError(
        errorType: String,
        responseCode: Int,
        debugMessage: String,
    ) {
        if (!canExecuteCrashlytics()) return
        try {
            val exception = BillingException(errorType, responseCode, debugMessage)
            recordException(exception)
            Log.w(TAG, "Billing error logged: $errorType, code=$responseCode, msg=$debugMessage")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log billing error: ${e.message}")
        }
    }

    /**
     * 上报串流错误（结构化错误分类）。
     *
     * @param errorType 错误类型："decoder_crash"、"connection_failed" 或 "stage_failed"
     * @param stage 串流阶段
     * @param errorCode 错误码
     * @param decoderName 解码器名称
     */
    fun logStreamError(
        errorType: String,
        stage: String? = null,
        errorCode: Int? = null,
        decoderName: String? = null,
    ) {
        if (!canExecuteCrashlytics()) return
        try {
            val exception = StreamException(errorType, stage, errorCode, decoderName)
            recordException(exception)
            Log.w(TAG, "Stream error logged: $errorType, stage=$stage, code=$errorCode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log stream error: ${e.message}")
        }
    }

    // ── 用户标识 ──────────────────────────────────────────

    /**
     * 设置用户标识（崩溃时关联用户）。
     *
     * @param userId 用户 ID（匿名化处理）
     */
    fun setUserId(userId: String) {
        if (!canExecuteCrashlytics()) return
        try {
            crashlytics.setUserId(userId)
            Log.d(TAG, "User ID set")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set user ID: ${e.message}")
        }
    }

    // ── 内部方法 ──────────────────────────────────────────

    private fun canExecuteCrashlytics(): Boolean {
        if (BuildConfig.DEBUG) {
            return false
        }
        return true
    }

    // ── 自定义异常类 ──────────────────────────────────────

    /**
     * 广告错误异常（结构化错误分类）。
     */
    class AdException(
        val errorType: String,
        val adType: String,
        val errorCode: Int,
        override val message: String,
    ) : Exception("Ad error: $errorType, adType=$adType, code=$errorCode, msg=$message")

    /**
     * 计费错误异常（结构化错误分类）。
     */
    class BillingException(
        val errorType: String,
        val responseCode: Int,
        override val message: String,
    ) : Exception("Billing error: $errorType, code=$responseCode, msg=$message")

    /**
     * 串流错误异常（结构化错误分类）。
     */
    class StreamException(
        val errorType: String,
        val stage: String?,
        val errorCode: Int?,
        val decoderName: String?,
    ) : Exception("Stream error: $errorType, stage=$stage, code=$errorCode, decoder=$decoderName")

    companion object {
        private const val TAG = "CrashlyticsManager"

        // 用户状态键
        private const val KEY_USER_TIER = "user_tier"
        private const val KEY_FIRST_OPEN_DATE = "first_open_date"
        private const val KEY_HAS_COMPLETED_FIRST_STREAM = "has_completed_first_stream"

        // 广告状态键
        private const val KEY_AD_INITIALIZED = "ad_initialized"
        private const val KEY_LAST_AD_TYPE = "last_ad_type"
        private const val KEY_AD_LOAD_FAILED = "ad_load_failed"
        private const val KEY_CONSENT_STATUS = "consent_status"

        // 计费状态键
        private const val KEY_BILLING_CONNECTED = "billing_connected"
        private const val KEY_PRODUCT_DETAILS_LOADED = "product_details_loaded"
        private const val KEY_LAST_PURCHASE_STATE = "last_purchase_state"

        // 串流状态键
        private const val KEY_DECODER_NAME = "decoder_name"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_FPS = "fps"
        private const val KEY_NETWORK_TYPE = "network_type"
        private const val KEY_NETWORK_QUALITY = "network_quality"

        @Volatile
        private var instance: CrashlyticsManager? = null

        @Synchronized
        fun getInstance(context: Context): CrashlyticsManager {
            if (instance == null) {
                instance = CrashlyticsManager(context.applicationContext)
            }
            return instance!!
        }
    }
}