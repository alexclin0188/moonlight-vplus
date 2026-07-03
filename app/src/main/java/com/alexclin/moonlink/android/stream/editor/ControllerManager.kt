package com.alexclin.moonlink.android.stream.editor

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.limelight.Game
import com.alexclin.moonlink.android.R
import com.alexclin.moonlink.android.stream.editor.config.PageConfigController
import com.alexclin.moonlink.android.stream.editor.element.ElementController
import com.alexclin.moonlink.android.stream.editor.sqlite.SuperConfigDatabaseHelper
import com.alexclin.moonlink.android.stream.editor.superpage.SuperPagesController

class ControllerManager(layout: FrameLayout, context: Context) {
    private val advanceSettingView: FrameLayout?
    private val fatherLayout: FrameLayout?
    var pageConfigController: PageConfigController? = null
        get() {
            if (field == null) {
                field = PageConfigController(this, context)
            }
            return field
        }
        private set
    var touchController: TouchController? = null
        get() {
            if (field == null) {
                val layerElement =
                    advanceSettingView!!.findViewById<FrameLayout>(R.id.layer_2_element)
                field = TouchController(
                    (context as Game?)!!,
                    this,
                    layerElement.findViewById<View>(R.id.element_touch_view)
                )
            }
            return field
        }
        private set
    var superPagesController: SuperPagesController? = null
        get() {
            if (field == null) {
                val superPagesBox =
                    advanceSettingView!!.findViewById<FrameLayout>(R.id.super_pages_box)
                field = SuperPagesController(superPagesBox, context)
            }
            return field
        }
        private set
    var pageDeviceController: PageDeviceController? = null
        get() {
            if (field == null) {
                field = PageDeviceController(context, this)
            }
            return field
        }
        private set
    var superConfigDatabaseHelper: SuperConfigDatabaseHelper? = null
        get() {
            if (field == null) {
                field = SuperConfigDatabaseHelper(context)
            }
            return field
        }
        private set
    var elementController: ElementController? = null
        get() {
            if (field == null) {
                val layerElement =
                    advanceSettingView!!.findViewById<FrameLayout?>(R.id.layer_2_element)
                field = ElementController(this, layerElement, context)
            }
            return field
        }
        private set
    @JvmField
    val pageSuperMenuController: PageSuperMenuController?
    var keyboardUIController: KeyboardUIController? = null
        get() {
            if (field == null) {
                val layoutKeyboard =
                    advanceSettingView!!.findViewById<FrameLayout?>(R.id.layer_6_keyboard)
                if (layoutKeyboard != null) {
                    field = KeyboardUIController(advanceSettingView, object : KeyboardUIController.OnKeyboardEventListener {
                        override fun sendKeyEvent(down: Boolean, keyCode: Short) {
                            elementController?.sendKeyEvent(down, keyCode)
                        }

                        override fun rumbleSingleVibrator(lowFreq: Short, highFreq: Short, duration: Int) {
                            elementController?.rumbleButtonVibrator(duration)
                        }
                    }, context)
                }
            }
            return field
        }
    private val context: Context

    init {
        advanceSettingView = layout.findViewById<FrameLayout?>(R.id.advance_setting_view)
        this.fatherLayout = layout
        this.context = context
        pageSuperMenuController = PageSuperMenuController(context, this)
    }


    fun refreshLayout() {
        this.pageConfigController!!.initConfig()
    }

    /**
     * 隐藏王冠功能界面
     */
    fun hide() {
        if (advanceSettingView != null) {
            advanceSettingView.setVisibility(View.GONE)
        }
    }

    /**
     * 显示王冠功能界面
     */
    fun show() {
        if (advanceSettingView != null) {
            advanceSettingView.setVisibility(View.VISIBLE)
        }
    }
}
