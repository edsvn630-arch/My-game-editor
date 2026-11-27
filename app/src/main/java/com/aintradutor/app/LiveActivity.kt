package com.aintradutor.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged

class LiveActivity : AppCompatActivity() {

    private val pageSize = 8
    private var start = 0
    private var nextStart = 0
    private var highlights: Set<Int> = emptySet()

    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private lateinit var etFind: EditText
    private lateinit var scroll: ScrollView
    private lateinit var ocrBox: LinearLayout
    private val shownIdx = ArrayList<Int>()

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Cena ao vivo"
        Store.loadPrefs(applicationContext)
        Store.restore(applicationContext)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val nav = LinearLayout(this)
        nav.addView(Ui.button(this, "◀  Anteriores", false) { goPrev() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        nav.addView(Ui.button(this, "Próximas  ▶", false) { goNext() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        nav.addView(
            Ui.button(this, "Voltar ao jogo", true) {
                Store.persistAsync(applicationContext)
                moveTaskToBack(true)
            },
            Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f)
        )
        root.addView(nav, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 10, 12, 6))

        val findRow = LinearLayout(this)
        findRow.gravity = Gravity.CENTER_VERTICAL
        etFind = Ui.input(this, "Buscar fala (original ou tradução)")
        findRow.addView(etFind, Ui.lp(this, 0, dp(48), 0, 0, 8, 0, 1f))
        findRow.addView(Ui.button(this, "Ir", false) { find() }, Ui.lp(this, Ui.WRAP, dp(48)))
        root.addView(findRow, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 0, 12, 6))

        root.addView(
            Ui.button(this, "Sugerir tradução (auto) para as falas desta tela", false) { suggest() },
            Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 0, 12, 6)
        )

        ocrBox = Ui.card(this)
        ocrBox.visibility = View.GONE
        root.addView(ocrBox, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 0, 12, 6))

        info = Ui.secondary(this, "")
        info.setPadding(dp(16), dp(2), dp(16), dp(4))
        root.addView(info)

        scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(12), 0, dp(12), dp(24))
        scroll.addView(list)
        root.addView(scroll, Ui.lp(this, Ui.MATCH, 0, 0, 0, 0, 0, 1f))

        setContentView(root)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onPause() {
        super.onPause()
        Store.persistAsync(applicationContext)
    }

    private fun handle(i: Intent) {
        if (i.getBooleanExtra("ocr", false)) renderOcr() else {
            ocrBox.removeAllViews()
            ocrBox.visibility = View.GONE
        }
        val s = i.getIntExtra("start", -1)
        highlights = i.getIntArrayExtra("found")?.toSet() ?: emptySet()
        if (s >= 0) {
            start = s
        } else if (i.hasExtra("found")) {
            Toast.makeText(this, "Não achei essa fala na tela. Use a busca.", Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun render() {
        list.removeAllViews()
        shownIdx.clear()
        val orig = Store.originals
        if (orig.isEmpty()) {
            info.text = "Abra um .ain no app primeiro"
            return
        }
        var shown = 0
        var i = start
        var last = start
        while (i < orig.size && shown < pageSize) {
            if (orig[i].isNotEmpty()) {
                addRow(i)
                shownIdx.add(i)
                shown++
                last = i
            }
            i++
        }
        nextStart = i
        info.text = "Falas #$start a #$last  •  em azul: as que estão na tela"
        scroll.scrollTo(0, 0)
    }

    private fun addRow(i: Int) {
        val card = Ui.card(this)
        val hot = i in highlights

        // cabeçalho: número, rascunho, revisar
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val idChip = Ui.chip(
            this, "#$i",
            Ui.color(this, if (hot) R.color.info_bg else R.color.tonal),
            Ui.color(this, if (hot) R.color.info else R.color.text2)
        )
        val draftChip = Ui.chip(this, "rascunho", Ui.color(this, R.color.warn_bg), Ui.color(this, R.color.warn))
        val spacer = View(this)
        val cbFlag = CheckBox(this)
        cbFlag.text = "⚑ revisar"
        cbFlag.textSize = 12f
        cbFlag.isChecked = Store.flags[i]
        cbFlag.setOnCheckedChangeListener { _, c -> Store.flags[i] = c }
        head.addView(idChip, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0))
        head.addView(draftChip)
        head.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        head.addView(cbFlag)
        card.addView(head)

        val orig = TextView(this)
        orig.text = Store.originals[i]
        orig.textSize = 15f
        orig.setPadding(0, dp(4), 0, dp(8))
        orig.setTextColor(Ui.color(this, if (hot) R.color.info else R.color.text2))
        if (hot) orig.setTypeface(null, Typeface.BOLD)
        card.addView(orig)

        val et = Ui.input(this, "Tradução")
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.setText(Store.trans[i] ?: "")
        card.addView(et, Ui.lp(this, Ui.MATCH, Ui.WRAP))

        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val counter = TextView(this)
        counter.textSize = 13f
        val lbl = Ui.secondary(this, "Limite:")
        val etLim = EditText(this)
        etLim.inputType = InputType.TYPE_CLASS_NUMBER
        etLim.hint = Store.globalLimit.toString()
        etLim.textSize = 14f
        if (Store.limits[i] > 0) etLim.setText(Store.limits[i].toString())
        row.addView(counter, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(lbl, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0))
        row.addView(etLim, LinearLayout.LayoutParams(dp(64), Ui.WRAP))
        card.addView(row, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0))

        fun upd() {
            val lim = Store.limitOf(i)
            val n = et.text.length
            counter.text = if (lim > 0) "$n / $lim" else "$n"
            counter.setTextColor(Ui.color(this, if (lim > 0 && n > lim) R.color.bad else R.color.text2))
            draftChip.visibility = if (Store.drafts[i] && Store.trans[i] != null) View.VISIBLE else View.GONE
        }
        et.doAfterTextChanged {
            Store.setTrans(i, it?.toString() ?: "")
            upd()
        }
        etLim.doAfterTextChanged {
            Store.setLimit(i, it?.toString()?.trim()?.toIntOrNull() ?: 0)
            upd()
        }
        upd()

        list.addView(card, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4))
    }

    private fun renderOcr() {
        ocrBox.removeAllViews()
        ocrBox.visibility = View.VISIBLE
        val head = Ui.heading(this, "O OCR leu")
        head.textSize = 14f
        ocrBox.addView(head)
        ocrBox.addView(Ui.secondary(this, "Toque numa linha azul para buscar no .ain"))
        for (d in Paddle.debug.take(9)) {
            val tv = TextView(this)
            tv.text = d
            tv.textSize = 12f
            tv.setPadding(0, dp(4), 0, dp(4))
            val txt = d.substringAfter(" | ", "")
            if (txt.isNotEmpty()) {
                tv.setTextColor(Ui.color(this, R.color.info))
                tv.setOnClickListener { jump(txt) }
            } else {
                tv.setTextColor(Ui.color(this, R.color.text2))
            }
            ocrBox.addView(tv)
        }
    }

    private fun jump(text: String) {
        val r = Store.match(listOf(text))
        if (r.isNotEmpty()) {
            start = r.min()
            highlights = r.toSet()
            render()
            return
        }
        val q = text.trim()
        val o = Store.originals
        for (i in o.indices) {
            val t = Store.trans[i]
            if (o[i].contains(q, true) || (t != null && t.contains(q, true))) {
                start = i
                highlights = setOf(i)
                render()
                return
            }
        }
        Toast.makeText(this, "Não achei essa linha nas falas", Toast.LENGTH_SHORT).show()
    }

    private fun suggest() {
        if (Store.originals.isEmpty()) return
        val idxs = ArrayList(shownIdx)
        Toast.makeText(this, "Traduzindo com o Google Tradutor...", Toast.LENGTH_LONG).show()
        Thread {
            try {
                Auto.ensureReady()
                val seen = HashSet<Int>()
                var n = 0
                for (i in idxs) {
                    val u = Store.unitOf(i) ?: continue
                    if (!seen.add(u[0])) continue
                    if (u.all { Store.trans[it] == null }) {
                        if (Auto.translateUnit(u)) n++
                    }
                }
                Store.persist(applicationContext)
                runOnUiThread {
                    Toast.makeText(this, "$n trechos sugeridos (rascunho). Revise e corrija.", Toast.LENGTH_LONG).show()
                    render()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, "Erro: ${e.message}. Confira a conexão com a internet.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun goNext() {
        if (nextStart < Store.originals.size) {
            start = nextStart
            highlights = emptySet()
            render()
        }
    }

    private fun goPrev() {
        var c = 0
        var i = start - 1
        var ns = 0
        while (i >= 0) {
            if (Store.originals[i].isNotEmpty()) {
                c++
                if (c == pageSize) {
                    ns = i
                    break
                }
            }
            i--
        }
        start = ns
        highlights = emptySet()
        render()
    }

    private fun find() {
        val q = etFind.text.toString().trim()
        if (q.isEmpty()) return
        val o = Store.originals
        for (i in o.indices) {
            val tr = Store.trans[i]
            if (o[i].contains(q, true) || (tr != null && tr.contains(q, true))) {
                start = i
                highlights = setOf(i)
                render()
                return
            }
        }
        Toast.makeText(this, "Não encontrei", Toast.LENGTH_SHORT).show()
    }
}
