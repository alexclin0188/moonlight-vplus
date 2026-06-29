package com.alexclin.moonlink.android.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 统一 Toast 工具类，所有 Toast 使用均通过此方法显示。
 *
 * 透明度策略：
 * - Android 11+ (API 30+)：Google 禁止自定义系统 Toast View，
 *   因此改用 Activity decorView 叠加自定义半透明浮层实现透明度。
 * - Android 10 及以下：通过 toast.view?.alpha 设置透明度。
 * - 非 Activity 上下文（如 Service）：降级使用系统默认 Toast（无透明度）。
 */
object ToastUtil {

    /** 浮层透明度，0=完全透明，1=完全不透明 */
    private const val ALPHA = 0.6f

    /** 浮层显示时长（毫秒），对应 Toast.LENGTH_SHORT */
    private const val DURATION_SHORT_MS = 2000L

    /** 浮层显示时长（毫秒），对应 Toast.LENGTH_LONG */
    private const val DURATION_LONG_MS = 3500L

    /** 浮层底部偏移（dp），模拟系统 Toast 位置 */
    private const val BOTTOM_MARGIN_DP = 64

    /** 浮层水平内边距（dp） */
    private const val PADDING_H_DP = 24

    /** 浮层垂直内边距（dp） */
    private const val PADDING_V_DP = 12

    /** 浮层圆角半径（dp） */
    private const val CORNER_RADIUS_DP = 24f

    /** 淡入/淡出动画时长（毫秒） */
    private const val FADE_DURATION_MS = 200L

    private val handler = Handler(Looper.getMainLooper())

    /** 当前显示的浮层 View，避免重复弹出叠加 */
    private var currentOverlay: View? = null

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val delayMs = if (duration == Toast.LENGTH_LONG) DURATION_LONG_MS else DURATION_SHORT_MS

        val activity = context.getActivity()
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            showOverlay(activity, message, delayMs)
        } else {
            // 非 Activity 上下文或 Activity 已销毁，降级使用系统 Toast
            // 注意：API 30+ 上 toast.view 为 null，透明度无法生效
            val toast = Toast.makeText(context, message, duration)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                toast.view?.alpha = ALPHA
            }
            toast.show()
        }
    }

    /**
     * 在 Activity 的 decorView 上叠加自定义半透明浮层。
     * 浮层会淡入显示，然后自动淡出移除。
     */
    private fun showOverlay(activity: Activity, message: String, delayMs: Long) {
        // 移除上一个浮层避免叠加
        currentOverlay?.let { removeOverlay(it) }

        val decorView = activity.window.decorView as? android.view.ViewGroup ?: return
        val density = activity.resources.displayMetrics.density

        val textView = TextView(activity).apply {
            text = message
            textSize = 14f // sp
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            maxWidth = (activity.resources.displayMetrics.widthPixels * 0.8).toInt()

            val padH = (PADDING_H_DP * density).toInt()
            val padV = (PADDING_V_DP * density).toInt()
            setPadding(padH, padV, padH, padV)

            // 半透明深色背景
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor((ALPHA * 255).toInt() shl 24 or 0x000000)
                setCornerRadius(CORNER_RADIUS_DP * density)
            }
            background = bg
        }

        val bottomMargin = (BOTTOM_MARGIN_DP * density).toInt()
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, bottomMargin)
        }

        decorView.addView(textView, params)
        currentOverlay = textView

        // 淡入动画
        textView.alpha = 0f
        textView.animate().alpha(1f).setDuration(FADE_DURATION_MS).start()

        // 延迟后淡出并移除
        handler.postDelayed({
            if (textView.parent != null) {
                textView.animate()
                    .alpha(0f)
                    .setDuration(FADE_DURATION_MS)
                    .withEndAction { removeOverlay(textView) }
                    .start()
            }
        }, delayMs)
    }

    private fun removeOverlay(view: View) {
        view.animate().cancel()
        if (view.parent == null) return
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        if (currentOverlay === view) {
            currentOverlay = null
        }
    }

    /**
     * 从 Context 中提取 Activity。
     * 遍历 ContextWrapper 链，查找最内层的 Activity。
     */
    private fun Context.getActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
