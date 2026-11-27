package com.aintradutor.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Progresso da tradução por trecho do jogo (blocos de 500 entradas). */
class ProgressActivity : AppCompatActivity() {

    private lateinit var headBox: LinearLayout
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Progresso do jogo"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(24))
        headBox = Ui.card(this)
        root.addView(headBox, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8))
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list)
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Store.loadPrefs(applicationContext)
        Store.restore(applicationContext)
        render()
    }

    private fun bar(pct: Int, h: Int): ProgressBar {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progress = pct
        bar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.progress_bar)
        bar.layoutParams = Ui.lp(this, Ui.MATCH, dp(h), 0, 8, 0, 0)
        return bar
    }

    private fun render() {
        list.removeAllViews()
        headBox.removeAllViews()
        val orig = Store.originals
        val trans = Store.trans
        if (orig.isEmpty()) {
            headBox.addView(Ui.heading(this, "Nenhum jogo aberto"))
            headBox.addView(Ui.secondary(this, "Abra um .ain no app primeiro"))
            return
        }
        val block = 500
        var total = 0
        var done = 0
        var drafts = 0
        var flagged = 0
        var a = 0
        while (a < orig.size) {
            val b = minOf(a + block, orig.size) - 1
            var t = 0
            var d = 0
            var dr = 0
            var fl = 0
            var firstOpen = -1
            for (i in a..b) {
                if (orig[i].isEmpty()) continue
                t++
                if (trans[i] != null) {
                    d++
                    if (Store.drafts[i]) dr++
                } else if (firstOpen < 0) {
                    firstOpen = i
                }
                if (Store.flags[i]) fl++
            }
            total += t
            done += d
            drafts += dr
            flagged += fl
            if (t > 0) list.addView(row(a, b, t, d, dr, fl, if (firstOpen >= 0) firstOpen else a), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4))
            a = b + 1
        }
        val pct = if (total == 0) 0 else done * 100 / total
        headBox.addView(Ui.heading(this, "${Store.projectId}  •  $pct%"))
        headBox.addView(Ui.secondary(this, "Traduzidas: $done de $total  •  rascunhos: $drafts  •  marcadas: $flagged"))
        headBox.addView(bar(pct, 8))
        headBox.addView(Ui.secondary(this, "Toque num trecho para ir até a primeira fala sem tradução dele."), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0))
    }

    private fun row(a: Int, b: Int, t: Int, d: Int, dr: Int, fl: Int, go: Int): View {
        val card = Ui.card(this)
        Ui.ripple(card)
        val pct = d * 100 / t
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "#$a – #$b"
        title.textSize = 15f
        title.setTextColor(Ui.color(this, R.color.text))
        val right = Ui.secondary(this, "$pct%  ($d/$t)")
        head.addView(title, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        head.addView(right)
        card.addView(head)
        card.addView(bar(pct, 6))
        if (dr > 0 || fl > 0) {
            val chips = LinearLayout(this)
            if (dr > 0) {
                chips.addView(
                    Ui.chip(this, "rascunhos: $dr", Ui.color(this, R.color.warn_bg), Ui.color(this, R.color.warn)),
                    Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0)
                )
            }
            if (fl > 0) {
                chips.addView(Ui.chip(this, "⚑ $fl", Ui.color(this, R.color.info_bg), Ui.color(this, R.color.info)))
            }
            card.addView(chips, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0))
        }
        card.setOnClickListener {
            val i = Intent(this, LiveActivity::class.java)
            i.putExtra("start", go)
            i.putExtra("found", intArrayOf(go))
            startActivity(i)
        }
        return card
    }
}
