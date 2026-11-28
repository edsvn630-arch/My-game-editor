package com.aintradutor.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Ajudantes para manter o visual igual em todas as telas. */
object Ui {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun color(ctx: Context, id: Int) = ContextCompat.getColor(ctx, id)

    /** Parâmetros de LinearLayout com margens em dp. */
    fun lp(ctx: Context, w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.setMargins(dp(ctx, l), dp(ctx, t), dp(ctx, r), dp(ctx, b))
        return p
    }

    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = ContextCompat.getDrawable(ctx, R.drawable.bg_card)
        c.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        return c
    }

    fun heading(ctx: Context, text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 17f
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.setTextColor(color(ctx, R.color.text))
        return t
    }

    fun secondary(ctx: Context, text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 13f
        t.setTextColor(color(ctx, R.color.text2))
        return t
    }

    fun button(ctx: Context, text: String, primary: Boolean, onClick: () -> Unit): Button {
        val b = Button(ctx)
        b.text = text
        b.isAllCaps = false
        b.textSize = 14f
        b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        b.stateListAnimator = null
        b.background = ContextCompat.getDrawable(ctx, if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_tonal)
        b.setTextColor(color(ctx, if (primary) R.color.on_primary else R.color.primary))
        b.minHeight = dp(ctx, 44)
        b.minimumHeight = dp(ctx, 44)
        b.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0)
        b.setOnClickListener { onClick() }
        return b
    }

    fun input(ctx: Context, hint: String): EditText {
        val e = EditText(ctx)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_TEXT
        e.background = ContextCompat.getDrawable(ctx, R.drawable.bg_search)
        e.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
        e.textSize = 15f
        e.setHintTextColor(color(ctx, R.color.text2))
        e.setTextColor(color(ctx, R.color.text))
        return e
    }

    fun chip(ctx: Context, text: String, bg: Int, fg: Int): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2))
        setChip(t, bg, fg)
        return t
    }

    fun setChip(t: TextView, bg: Int, fg: Int) {
        val g = GradientDrawable()
        g.cornerRadius = dp(t.context, 10).toFloat()
        g.setColor(bg)
        t.background = g
        t.setTextColor(fg)
    }

    /** Efeito de toque (ripple) sobre a própria view. */
    fun ripple(v: View) {
        val tv = TypedValue()
        v.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        v.foreground = ContextCompat.getDrawable(v.context, tv.resourceId)
        v.isClickable = true
    }

    /** Chip de opção que liga/desliga (marcado = azul-claro com borda). */
    fun toggleChip(ctx: Context, text: String, checked: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 13f
        t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        t.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
        setToggle(t, checked)
        t.setOnClickListener { onClick() }
        return t
    }

    fun setToggle(t: TextView, checked: Boolean) {
        val ctx = t.context
        t.background = ContextCompat.getDrawable(ctx, if (checked) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
        t.setTextColor(color(ctx, if (checked) R.color.primary else R.color.text2))
    }

    /** Ícone (vetor) pintado com uma cor do tema. */
    fun icon(ctx: Context, resId: Int, colorId: Int, sizeDp: Int = 22): android.widget.ImageView {
        val v = android.widget.ImageView(ctx)
        v.setImageResource(resId)
        v.setColorFilter(color(ctx, colorId))
        v.layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        return v
    }
}
