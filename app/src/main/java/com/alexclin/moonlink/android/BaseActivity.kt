package com.alexclin.moonlink.android

import android.app.Activity
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.alexclin.moonlink.android.util.UiHelper

/**
 * 所有继承 [Activity] 的 MoonLink Activity 基类。
 * 自动在 [onCreate] 中调用 [UiHelper.setLocale] 应用用户设置的语言，
 * 并监听语言偏好变更自动重建 Activity，避免每个子类手动重复实现。
 */
open class BaseActivity : Activity() {

    /** 语言变更监听器，用类属性强引用避免被 SharedPreferences 的 WeakHashMap GC */
    private var langChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiHelper.setLocale(this)

        // ── 监听语言设置变更，切换语言时重建 Activity ──
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "list_languages") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+：设置 applicationLocales 由系统触发单次重建，避免手动 recreate() 导致双重重建
                    UiHelper.setLocale(this)
                } else {
                    // API < 33：无自动重建机制，需手动调用 recreate()
                    recreate()
                }
            }
        }
        langChangeListener = listener
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        langChangeListener?.let {
            PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        langChangeListener = null
        super.onDestroy()
    }
}
