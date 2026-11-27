package com.aintradutor.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Barra lateral de rolagem rápida: arraste na borda direita da lista para ir
 * até qualquer ponto. Mostra o número da fala enquanto você arrasta.
 * Toques fora da borda direita passam direto para a lista.
 */
class FastScrollBar(
    ctx: Context,
    private val rv: RecyclerView,
    private val label: (Int) -> String
) : View(ctx) {

    private val d = resources.displayMetrics.density
    private val touchW = 44 * d
    private val thumbW = 8 * d
    private val thumbH = 64 * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var dragging = false
    private var dragFrac = 0f
    private var dragPos = 0

    init {
        textPaint.color = Ui.color(ctx, R.color.on_accent)
        textPaint.textSize = 16 * d
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                invalidate()
            }
        })
    }

    private fun count() = rv.adapter?.itemCount ?: 0

    private fun currentFraction(): Float {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return 0f
        val n = count()
        if (n <= 1) return 0f
        val first = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = lm.findLastVisibleItemPosition().coerceAtLeast(first)
        val visible = last - first + 1
        val denom = maxOf(1, n - visible)
        return (first.toFloat() / denom).coerceIn(0f, 1f)
    }

    override fun onDraw(c: Canvas) {
        if (count() < 20) return
        val f = if (dragging) dragFrac else currentFraction()
        val top = f * (height - thumbH)
        val right = width - 4 * d
        paint.color = if (dragging) Ui.color(context, R.color.accent) else Ui.color(context, R.color.thumb)
        c.drawRoundRect(right - thumbW, top, right, top + thumbH, thumbW / 2, thumbW / 2, paint)
        if (dragging) {
            val t = label(dragPos)
            val tw = textPaint.measureText(t)
            val pad = 12 * d
            val bh = 36 * d
            val bt = (top + thumbH / 2 - bh / 2).coerceIn(0f, height - bh)
            val br = right - thumbW - 8 * d
            paint.color = Ui.color(context, R.color.accent)
            c.drawRoundRect(br - tw - 2 * pad, bt, br, bt + bh, 8 * d, 8 * d, paint)
            c.drawText(t, br - tw - pad, bt + bh / 2 + textPaint.textSize / 3, textPaint)
        }
    }

    private fun move(y: Float) {
        val n = count()
        if (n == 0) return
        val f = ((y - thumbH / 2) / (height - thumbH)).coerceIn(0f, 1f)
        dragFrac = f
        dragPos = (f * (n - 1)).toInt()
        (rv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(dragPos, 0)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (count() < 20 || e.x < width - touchW) return false
                dragging = true
                parent.requestDisallowInterceptTouchEvent(true)
                move(e.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    move(e.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    invalidate()
                    return true
                }
            }
        }
        return false
    }
}
