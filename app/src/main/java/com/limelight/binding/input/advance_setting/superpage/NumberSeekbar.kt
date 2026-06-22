package com.limelight.binding.input.advance_setting.superpage

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

import com.alexclin.moonlink.android.R

class NumberSeekbar : LinearLayout {

    interface OnNumberSeekbarChangeListener {
        fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean)
        fun onStartTrackingTouch(seekBar: SeekBar)
        fun onStopTrackingTouch(seekBar: SeekBar)
    }

    private lateinit var numberSeekbarTitle: TextView
    private lateinit var numberSeekbarMinus: android.view.View
    private lateinit var numberSeekbarNumber: TextView
    private lateinit var numberSeekbarAdd: android.view.View
    private lateinit var numberSeekbarSeekbar: SeekBar
    private var onNumberSeekbarChangeListener: OnNumberSeekbarChangeListener? = null
    private var minValue: Int = 0
    private var maxValue: Int = 100

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        LayoutInflater.from(context).inflate(R.layout.number_seekbar, this, true)

        numberSeekbarTitle = findViewById(R.id.number_seekbar_title)
        numberSeekbarMinus = findViewById(R.id.number_seekbar_minus)
        numberSeekbarNumber = findViewById(R.id.number_seekbar_number)
        numberSeekbarAdd = findViewById(R.id.number_seekbar_add)
        numberSeekbarSeekbar = findViewById(R.id.number_seekbar_seekbar)
        numberSeekbarNumber.text = numberSeekbarSeekbar.progress.toString()

        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.NumberSeekbar, 0, 0)
            try {
                val maxAttrValue = a.getInt(R.styleable.NumberSeekbar_max, 100)
                val minAttrValue = a.getInt(R.styleable.NumberSeekbar_min, 0)
                val progressValue = a.getInt(R.styleable.NumberSeekbar_progress, 0)
                setRange(minAttrValue, maxAttrValue)
                setValueInternal(progressValue, notifyListener = false)

                val title = a.getString(R.styleable.NumberSeekbar_text)
                numberSeekbarTitle.text = title
            } finally {
                a.recycle()
            }
        }

        numberSeekbarSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actualValue = progressFromSeekBar(progress)
                numberSeekbarNumber.text = actualValue.toString()
                onNumberSeekbarChangeListener?.onProgressChanged(seekBar, actualValue, fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                onNumberSeekbarChangeListener?.onStartTrackingTouch(seekBar)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onNumberSeekbarChangeListener?.onStopTrackingTouch(seekBar)
            }
        })

        numberSeekbarMinus.setOnClickListener {
            val value = value
            if (value > minValue) {
                onNumberSeekbarChangeListener?.let { listener ->
                    listener.onStartTrackingTouch(numberSeekbarSeekbar)
                    setValueInternal(value - 1, notifyListener = true)
                    listener.onStopTrackingTouch(numberSeekbarSeekbar)
                }
            }
        }

        numberSeekbarAdd.setOnClickListener {
            val value = value
            if (value < maxValue) {
                setValueInternal(value + 1, notifyListener = true)
                onNumberSeekbarChangeListener?.onStopTrackingTouch(numberSeekbarSeekbar)
            }
        }
    }

    val value: Int
        get() = progressFromSeekBar(numberSeekbarSeekbar.progress)

    fun setValueWithNoCallBack(value: Int) {
        val temp = onNumberSeekbarChangeListener
        onNumberSeekbarChangeListener = null
        setValueInternal(value, notifyListener = false)
        onNumberSeekbarChangeListener = temp
    }

    fun setTitle(title: String) {
        numberSeekbarTitle.text = title
    }

    fun setProgressMax(max: Int) {
        setRange(minValue, max)
    }

    fun setProgressMin(min: Int) {
        setRange(min, maxValue)
    }

    fun setOnNumberSeekbarChangeListener(listener: OnNumberSeekbarChangeListener?) {
        this.onNumberSeekbarChangeListener = listener
    }

    private fun setRange(min: Int, max: Int) {
        minValue = min
        maxValue = max.coerceAtLeast(min)
        numberSeekbarSeekbar.max = maxValue - minValue
        setValueInternal(value, notifyListener = false)
    }

    private fun setValueInternal(value: Int, notifyListener: Boolean) {
        val clampedValue = value.coerceIn(minValue, maxValue)
        val seekBarProgress = progressToSeekBar(clampedValue)
        if (notifyListener || numberSeekbarSeekbar.progress != seekBarProgress) {
            numberSeekbarSeekbar.progress = seekBarProgress
        }
        numberSeekbarNumber.text = clampedValue.toString()
    }

    private fun progressFromSeekBar(progress: Int): Int {
        return minValue + progress
    }

    private fun progressToSeekBar(value: Int): Int {
        return value - minValue
    }
}
