package com.aintradutor.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProblemsActivity : AppCompatActivity() {

    private var all: List<Check.Problem> = emptyList()
    private var shownList: List<Check.Problem> = emptyList()
    private var filter = 0

    private lateinit var info: TextView
    private lateinit var adapter: Adapter

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Problemas e reajuste"
        adapter = Adapter()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        fun btn(label: String, action: () -> Unit): Button {
            val b = Button(this@ProblemsActivity)
            b.text = label
            b.textSize = 12f
            b.isAllCaps = false
            b.setOnClickListener { action() }
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            return b
        }

        val row = LinearLayout(this)
        row.addView(btn("Reajustar falas largas") { doRewrap() })
        row.addView(btn("Desfazer reajuste") { undo() })
        root.addView(row)

        val row2 = LinearLayout(this)
        row2.addView(btn("Aprovar todos os rascunhos") { approveAll() })
        root.addView(row2)

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Check.names)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                filter = pos
                applyFilter()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(spinner)

        info = TextView(this)
        info.setPadding(dp(12), dp(4), dp(12), dp(4))
        info.text = "Verificando..."
        root.addView(info)

        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(rv)

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
            "${shownList.size} exibidos • " + parts.joinToString(" • ")
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

    private fun open(idx: Int) {
        val i = Intent(this, LiveActivity::class.java)
        i.putExtra("start", idx)
        i.putExtra("found", intArrayOf(idx))
        startActivity(i)
    }

    private class VH(v: View, val a: TextView, val b: TextView) : RecyclerView.ViewHolder(v)

    private inner class Adapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = shownList.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val box = LinearLayout(parent.context)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(12), dp(8), dp(12), dp(8))
            box.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val a = TextView(parent.context)
            a.textSize = 13f
            a.setTypeface(null, Typeface.BOLD)
            a.setTextColor(0xFFD32F2F.toInt())
            val b = TextView(parent.context)
            b.textSize = 15f
            box.addView(a)
            box.addView(b)
            return VH(box, a, b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = shownList[position]
            holder.a.text = "#${p.idx} · ${Check.names[p.kind]} (${p.info})"
            holder.b.text = Store.trans[p.idx] ?: Store.originals[p.idx]
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
