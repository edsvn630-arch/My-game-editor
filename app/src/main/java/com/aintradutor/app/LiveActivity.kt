package com.aintradutor.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.loadPrefs(applicationContext)
        Store.restore(applicationContext)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        fun btn(label: String, action: () -> Unit): Button {
            val b = Button(this@LiveActivity)
            b.text = label
            b.textSize = 12f
            b.isAllCaps = false
            b.setOnClickListener { action() }
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            return b
        }

        val row1 = LinearLayout(this)
        row1.addView(btn("◀ Anteriores") { goPrev() })
        row1.addView(btn("Próximas ▶") { goNext() })
        row1.addView(btn("Voltar ao jogo") {
            Store.persistAsync(applicationContext)
            moveTaskToBack(true)
        })
        root.addView(row1)

        val row2 = LinearLayout(this)
        row2.setPadding(dp(8), 0, dp(8), 0)
        etFind = EditText(this)
        etFind.hint = "Buscar fala (em inglês)"
        etFind.inputType = InputType.TYPE_CLASS_TEXT
        etFind.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row2.addView(etFind)
        row2.addView(Button(this).also {
            it.text = "Ir"
            it.setOnClickListener { find() }
        })
        root.addView(row2)

        info = TextView(this)
        info.setPadding(dp(12), dp(4), dp(12), dp(4))
        root.addView(info)

        scroll = ScrollView(this)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(12), 0, dp(12), dp(24))
        scroll.addView(list)
        root.addView(scroll)

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
                shown++
                last = i
            }
            i++
        }
        nextStart = i
        info.text = "Falas #$start a #$last  (em azul: as que estão na tela)"
        scroll.scrollTo(0, 0)
    }

    private fun addRow(i: Int) {
        val o = TextView(this)
        o.text = "#$i  ${Store.originals[i]}"
        o.textSize = 15f
        o.setPadding(dp(4), dp(10), dp(4), 0)
        if (i in highlights) {
            o.setTextColor(0xFF0D47A1.toInt())
            o.setTypeface(null, Typeface.BOLD)
        } else {
            o.setTextColor(0xFF555555.toInt())
        }
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.hint = "Tradução"
        et.setText(Store.trans[i] ?: "")

        val row = LinearLayout(this)
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        val counter = TextView(this)
        counter.textSize = 13f
        counter.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val lbl = TextView(this)
        lbl.text = "Limite:"
        lbl.textSize = 12f
        val etLim = EditText(this)
        etLim.inputType = InputType.TYPE_CLASS_NUMBER
        etLim.hint = Store.globalLimit.toString()
        if (Store.limits[i] > 0) etLim.setText(Store.limits[i].toString())
        etLim.layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT)
        row.addView(counter)
        row.addView(lbl)
        row.addView(etLim)

        fun upd() {
            val lim = Store.limitOf(i)
            val n = et.text.length
            counter.text = if (lim > 0) "$n / $lim" else "$n"
            counter.setTextColor(if (lim > 0 && n > lim) 0xFFD32F2F.toInt() else 0xFF777777.toInt())
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

        list.addView(o)
        list.addView(et)
        list.addView(row)
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
            if (o[i].contains(q, true)) {
                start = i
                highlights = setOf(i)
                render()
                return
            }
        }
        Toast.makeText(this, "Não encontrei", Toast.LENGTH_SHORT).show()
    }
}
