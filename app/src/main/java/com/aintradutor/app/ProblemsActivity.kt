package com.aintradutor.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProblemsActivity : AppCompatActivity() {

    private var all: List<Check.Problem> = emptyList()
    private var shownList: List<Check.Problem> = emptyList()
    private var filter = 0

    private lateinit var info: TextView
    private lateinit var adapter: Adapter

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Problemas e reajuste"
        adapter = Adapter()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val row = LinearLayout(this)
        row.addView(Ui.button(this, "Reajustar falas largas", true) { doRewrap() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        row.addView(Ui.button(this, "Desfazer reajuste", false) { undo() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
        root.addView(row, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 10, 12, 6))

        root.addView(
            Ui.button(this, "Aprovar todos os rascunhos", false) { approveAll() },
            Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 0, 12, 6)
        )

        val filterCard = Ui.card(this)
        filterCard.setPadding(dp(10), dp(2), dp(10), dp(2))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Check.names)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                filter = pos
                applyFilter()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        filterCard.addView(spinner)
        root.addView(filterCard, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 0, 12, 6))

        info = Ui.secondary(this, "Verificando...")
        info.setPadding(dp(16), dp(2), dp(16), dp(4))
        root.addView(info)

        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.clipToPadding = false
        rv.setPadding(0, dp(2), 0, dp(24))
        root.addView(rv, Ui.lp(this, Ui.MATCH, 0, 0, 0, 0, 0, 1f))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        Store.loadPrefs(applicationContext)
        Store.restore(applicationContext)
        rescan()
    }

    private fun rescan() {
        if (Store.ain == null) {
            info.text = "Abra um .ain no app primeiro"
            return
        }
        val strip = Store.stripAccents
        Thread {
            val res = Check.scan(strip)
            runOnUiThread {
                all = res
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        shownList = if (filter == 0) all else all.filter { it.kind == filter }
        adapter.notifyDataSetChanged()
        val counts = IntArray(Check.names.size)
        for (p in all) counts[p.kind]++
        val parts = ArrayList<String>()
        for (k in 1 until Check.names.size) if (counts[k] > 0) parts.add("${Check.names[k]}: ${counts[k]}")
        info.text = if (all.isEmpty()) {
            "Nenhum problema encontrado nas falas traduzidas"
        } else {
            "${shownList.size} exibidos  •  " + parts.joinToString("  •  ")
        }
    }

    private fun doRewrap() {
        if (Store.ain == null) {
            toast("Abra um .ain primeiro")
            return
        }
        info.text = "Reajustando..."
        Thread {
            val r = Wrap.rewrapAll()
            Store.persist(applicationContext)
            runOnUiThread {
                toast("${r[0]} trechos reajustados, ${r[1]} não couberam (veja em \"Não cabe\")")
                rescan()
            }
        }.start()
    }

    private fun approveAll() {
        var n = 0
        for (i in Store.drafts.indices) {
            if (Store.drafts[i]) {
                Store.drafts[i] = false
                n++
            }
        }
        Store.persistAsync(applicationContext)
        toast(if (n == 0) "Nenhum rascunho para aprovar" else "$n falas marcadas como revisadas")
        rescan()
    }

    private fun undo() {
        val u = Store.undo
        if (u == null || u.size != Store.trans.size) {
            toast("Nada para desfazer")
            return
        }
        Store.trans = u
        Store.undo = null
        Store.persistAsync(applicationContext)
        toast("Reajuste desfeito")
        rescan()
    }

    private fun open(idx: Int) {
        val i = Intent(this, LiveActivity::class.java)
        i.putExtra("start", idx)
        i.putExtra("found", intArrayOf(idx))
        startActivity(i)
    }

    private fun kindColors(kind: Int): IntArray = when (kind) {
        Check.LIMITE, Check.NAO_CABE -> intArrayOf(R.color.bad_bg, R.color.bad)
        Check.MARCADOR, Check.CHARS, Check.RASCUNHO -> intArrayOf(R.color.warn_bg, R.color.warn)
        else -> intArrayOf(R.color.info_bg, R.color.info)
    }

    private class VH(v: View, val kind: TextView, val id: TextView, val detail: TextView, val text: TextView) :
        RecyclerView.ViewHolder(v)

    private inner class Adapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = shownList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val card = Ui.card(ctx)
            Ui.ripple(card)
            val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(12), dp(4), dp(12), dp(4))
            card.layoutParams = lp
            val head = LinearLayout(ctx)
            head.gravity = Gravity.CENTER_VERTICAL
            val kind = Ui.chip(ctx, "", 0, 0)
            val id = Ui.chip(ctx, "", Ui.color(ctx, R.color.tonal), Ui.color(ctx, R.color.text2))
            val detail = Ui.secondary(ctx, "")
            head.addView(kind, Ui.lp(ctx, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 6, 0))
            head.addView(id, Ui.lp(ctx, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 8, 0))
            head.addView(detail)
            val text = TextView(ctx)
            text.textSize = 15f
            text.setTextColor(Ui.color(ctx, R.color.text))
            text.setPadding(0, dp(6), 0, 0)
            card.addView(head)
            card.addView(text)
            return VH(card, kind, id, detail, text)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = shownList[position]
            val ctx = holder.itemView.context
            val c = kindColors(p.kind)
            holder.kind.text = Check.names[p.kind]
            Ui.setChip(holder.kind, Ui.color(ctx, c[0]), Ui.color(ctx, c[1]))
            holder.id.text = "#${p.idx}"
            holder.detail.text = p.info
            holder.text.text = Store.trans[p.idx] ?: Store.originals[p.idx]

            holder.itemView.setOnClickListener { open(p.idx) }
            holder.itemView.setOnLongClickListener {
                val i = p.idx
                val isDraft = i in Store.drafts.indices && Store.drafts[i]
                val isFlag = i in Store.flags.indices && Store.flags[i]
                if (isDraft || isFlag) {
                    if (isDraft) Store.drafts[i] = false
                    if (isFlag) Store.flags[i] = false
                    Store.persistAsync(applicationContext)
                    toast("Marcada como revisada")
                    rescan()
                }
                true
            }
        }
    }
}
