package com.mycompany.joyntv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Transparent overlay that draws a mouse pointer. The Fire TV remote has no
 * touch input, so the D-pad moves this pointer and OK "taps" at its position.
 */
class CursorView(context: Context) : View(context) {

    var cursorX = 0f
        private set
    var cursorY = 0f
        private set

    private val density = resources.displayMetrics.density
    private val size = 22 * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        strokeJoin = Paint.Join.ROUND
    }
    private val arrow = Path()

    init {
        isFocusable = false
        isClickable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && oldh == 0) {
            cursorX = w / 2f
            cursorY = h / 2f
        } else {
            clamp()
        }
    }

    fun moveTo(x: Float, y: Float) {
        cursorX = x
        cursorY = y
        clamp()
        invalidate()
    }

    /** Moves the pointer and returns the part of the move blocked by the screen edge. */
    fun moveBy(dx: Float, dy: Float): Pair<Float, Float> {
        val wantX = cursorX + dx
        val wantY = cursorY + dy
        cursorX = wantX
        cursorY = wantY
        clamp()
        invalidate()
        return Pair(wantX - cursorX, wantY - cursorY)
    }

    private fun clamp() {
        cursorX = cursorX.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        cursorY = cursorY.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val x = cursorX
        val y = cursorY
        arrow.reset()
        arrow.moveTo(x, y)
        arrow.lineTo(x, y + size)
        arrow.lineTo(x + size * 0.28f, y + size * 0.75f)
        arrow.lineTo(x + size * 0.45f, y + size * 1.1f)
        arrow.lineTo(x + size * 0.6f, y + size * 1.03f)
        arrow.lineTo(x + size * 0.43f, y + size * 0.68f)
        arrow.lineTo(x + size * 0.75f, y + size * 0.68f)
        arrow.close()
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, outline)
    }
}
