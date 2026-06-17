package com.streamforge.sdk.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Rotating ring spinner — matches the web loading/buffering spinner:
 * a faint full ring (white @8%) with a brand-coloured 90° arc spinning at 0.8s/turn.
 */
internal class SpinnerView(context: Context, brandColor: Int) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val stroke = 3f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = Color.argb((0.08f * 255).toInt(), 255, 255, 255)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = brandColor
    }

    fun setBrandColor(color: Int) { arcPaint.color = color; invalidate() }

    private val rect = RectF()
    private var angle = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = stroke
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        canvas.drawArc(rect, angle, 90f, false, arcPaint)
    }
}

/**
 * Expanding pulse ring behind the spinner — matches the web `pulse-ring`
 * animation (scale 0.8→1.6, fading out, brand @15%).
 */
internal class PingRingView(context: Context, brandColor: Int) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = brandColor
    }
    fun setBrandColor(color: Int) { paint.color = color; invalidate() }

    private var scale = 0.8f
    private var ringAlpha = 0.6f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            val t = it.animatedValue as Float
            scale = 0.8f + t * 0.8f       // 0.8 → 1.6
            ringAlpha = 0.6f * (1f - t)   // 0.6 → 0
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        paint.alpha = (ringAlpha * 255).toInt()
        val r = (width / 2f) * scale
        canvas.drawCircle(width / 2f, height / 2f, r, paint)
    }
}
