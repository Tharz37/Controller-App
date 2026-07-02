package com.example.controller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.controller.model.ControlElement
import com.example.controller.model.ElementType
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a single ControlElement.
 *
 * editable = true  -> used in the preset editor: dragging moves the element,
 *                      a tap fires onTap (used to open a "configure" dialog).
 * editable = false -> used in the controller runner: touches produce live
 *                      values through onLiveValue / onButtonState instead of
 *                      moving anything.
 *
 * Values reported by run mode:
 *  - BUTTON: onButtonState(pressed: Boolean)
 *  - JOYSTICK: onLiveValue(x: Float, y: Float) each in -1f..1f
 *  - SLIDER: onLiveValue(v: Float, 0f) where v is 0f..1f
 *  - DPAD: onDpadDirection(dir: Int) 0..7, or -1 for neutral
 *  - GYRO_STEER: not touch driven, updated externally by the activity
 */
class ElementView(
    context: Context,
    val element: ControlElement,
    private val editable: Boolean
) : View(context) {

    var onMoved: ((nx: Float, ny: Float) -> Unit)? = null
    var onTap: (() -> Unit)? = null
    var onLiveValue: ((a: Float, b: Float) -> Unit)? = null
    var onButtonState: ((pressed: Boolean) -> Unit)? = null
    var onDpadDirection: ((dir: Int) -> Unit)? = null

    /** External live value for gyro steering, -1f..1f, set by the hosting activity. */
    var externalGyroValue: Float = 0f
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#332F6FED")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2F6FED")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2F6FED")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    private var stickX = 0f
    private var stickY = 0f
    private var sliderValue = 0f
    private var pressed = false
    private var downX = 0f
    private var downY = 0f
    private var viewStartX = 0f
    private var viewStartY = 0f

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = RectF(2f, 2f, w - 2f, h - 2f)

        when (element.type) {
            ElementType.BUTTON -> {
                canvas.drawRoundRect(r, 16f, 16f, if (pressed) activePaint else bgPaint)
                canvas.drawRoundRect(r, 16f, 16f, borderPaint)
                canvas.drawText(element.label, w / 2f, h / 2f + 10f, textPaint)
            }
            ElementType.JOYSTICK -> {
                canvas.drawOval(r, bgPaint)
                canvas.drawOval(r, borderPaint)
                val cx = w / 2f + stickX * (w / 2f - 40f)
                val cy = h / 2f + stickY * (h / 2f - 40f)
                canvas.drawCircle(cx, cy, 36f, activePaint)
                canvas.drawText(element.label, w / 2f, h - 12f, textPaint)
            }
            ElementType.SLIDER -> {
                canvas.drawRoundRect(r, 16f, 16f, bgPaint)
                canvas.drawRoundRect(r, 16f, 16f, borderPaint)
                if (element.sliderVertical) {
                    val fillTop = h - (h * sliderValue)
                    canvas.drawRect(RectF(2f, fillTop, w - 2f, h - 2f), activePaint)
                } else {
                    canvas.drawRect(RectF(2f, 2f, w * sliderValue, h - 2f), activePaint)
                }
                canvas.drawText(element.label, w / 2f, h / 2f + 10f, textPaint)
            }
            ElementType.DPAD -> {
                canvas.drawRoundRect(r, 16f, 16f, bgPaint)
                canvas.drawRoundRect(r, 16f, 16f, borderPaint)
                canvas.drawText("+", w / 2f, h / 2f + 12f, textPaint.apply { textSize = 46f })
                textPaint.textSize = 30f
            }
            ElementType.GYRO_STEER -> {
                canvas.drawRoundRect(r, 16f, 16f, bgPaint)
                canvas.drawRoundRect(r, 16f, 16f, borderPaint)
                val cx = w / 2f + externalGyroValue * (w / 2f - 30f)
                canvas.drawRoundRect(RectF(cx - 10f, 6f, cx + 10f, h - 6f), 8f, 8f, activePaint)
                canvas.drawText(element.label, w / 2f, h / 2f + 10f, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editable) return handleEditTouch(event)
        return handleRunTouch(event)
    }

    private fun handleEditTouch(event: MotionEvent): Boolean {
        val parent = parent as? View ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY
                viewStartX = x; viewStartY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val newX = (viewStartX + dx).coerceIn(0f, (parent.width - width).toFloat())
                val newY = (viewStartY + dy).coerceIn(0f, (parent.height - height).toFloat())
                x = newX; y = newY
                onMoved?.invoke(newX / parent.width, newY / parent.height)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.rawX - downX) > 12 || kotlin.math.abs(event.rawY - downY) > 12
                if (!moved) onTap?.invoke()
                return true
            }
        }
        return false
    }

    private fun handleRunTouch(event: MotionEvent): Boolean {
        when (element.type) {
            ElementType.BUTTON -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { pressed = true; onButtonState?.invoke(true); invalidate() }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pressed = false; onButtonState?.invoke(false); invalidate() }
                }
                return true
            }
            ElementType.JOYSTICK -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val nx = ((event.x / width) * 2f - 1f).coerceIn(-1f, 1f)
                        val ny = ((event.y / height) * 2f - 1f).coerceIn(-1f, 1f)
                        stickX = nx; stickY = ny
                        onLiveValue?.invoke(nx, ny)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stickX = 0f; stickY = 0f
                        onLiveValue?.invoke(0f, 0f)
                        invalidate()
                    }
                }
                return true
            }
            ElementType.SLIDER -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val v = if (element.sliderVertical)
                            (1f - event.y / height).coerceIn(0f, 1f)
                        else
                            (event.x / width).coerceIn(0f, 1f)
                        sliderValue = v
                        onLiveValue?.invoke(v, 0f)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        sliderValue = 0f
                        onLiveValue?.invoke(0f, 0f)
                        invalidate()
                    }
                }
                return true
            }
            ElementType.DPAD -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val nx = event.x / width - 0.5f
                        val ny = event.y / height - 0.5f
                        val angle = Math.toDegrees(kotlin.math.atan2(-ny, nx).toDouble())
                        val dist = kotlin.math.sqrt(nx * nx + ny * ny)
                        val dir = if (dist < 0.15f) -1 else (((90 - angle + 360) % 360) / 45.0).toInt() % 8
                        onDpadDirection?.invoke(dir)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onDpadDirection?.invoke(-1)
                }
                return true
            }
            ElementType.GYRO_STEER -> return false // driven externally, not by touch
        }
    }
}
