package com.aintradutor.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Progresso da tradução por trecho do jogo (blocos de 500 entradas). */
class ProgressActivity : AppCompatActivity() {

    private lateinit var head: TextView
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Progresso do jogo"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(8), dp(12), dp(24))
        head = TextView(this)
        head.textSize = 14f
        head.setPadding(0, 0, 0, dp(8))
        root.addView(head)
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

    private fun render() {
        list.removeAllViews()
        val orig = Store.originals
        val trans = Store.trans
        if (orig.isEmpty()) {
            head.text = "Abra um .ain no app primeiro"
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
            if (t > 0) list.addView(row(a, b, t, d, dr, fl, if (firstOpen >= 0) firstOpen else a))
            a = b + 1
        }
        val pct = if (total == 0) 0 else done * 100 / total
        head.text = "Projeto: ${Store.projectId}\n" +
            "Traduzidas: $done de $total ($pct%) • rascunhos: $drafts • marcadas: $flagged\n" +
            "Toque num trecho para ir até a primeira fala sem tradução dele."
    }

    private fun row(a: Int, b: Int, t: Int, d: Int, dr: Int, fl: Int, go: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(8), 0, dp(8))
        val pct = d * 100 / t
        val title = TextView(this)
        title.textSize = 14f
        title.text = "#$a – #$b  •  $pct%  ($d/$t)" +
            (if (dr > 0) "  • rascunhos: $dr" else "") +
            (if (fl > 0) "  • ⚑ $fl" else "")
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progress = pct
        bar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        box.addView(title)
        box.addView(bar)
        box.setOnClickListener {
            val i = Intent(this, LiveActivity::class.java)
            i.putExtra("start", go)
            i.putExtra("found", intArrayOf(go))
            startActivity(i)
        }
        return box
    }
}
