package com.aintradutor.app

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var ain: AinFile? = null
    private var originals: Array<String> = emptyArray()
    private var trans: Array<String?> = arrayOfNulls(0)
    private var shown: List<Int> = emptyList()

    private lateinit var adapter: Adapter
    private lateinit var status: TextView
    private lateinit var etSearch: EditText
    private lateinit var etLimit: EditText
    private lateinit var cbOnly: CheckBox

    private val openAin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadAin(uri)
    }
    private val saveAin = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) saveAinTo(uri)
    }
    private val openCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importCsv(uri)
    }
    private val saveCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportCsv(uri)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun limit() = etLimit.text.toString().trim().toIntOrNull() ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = Adapter()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        fun btn(label: String, action: () -> Unit): Button {
            val b = Button(this@MainActivity)
            b.text = label
            b.textSize = 12f
            b.isAllCaps = false
            b.setOnClickListener { action() }
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            return b
        }

        val row1 = LinearLayout(this)
        row1.addView(btn("Abrir .ain") { openAin.launch(arrayOf("*/*")) })
        row1.addView(btn("Salvar .ain") {
            if (ain == null) toast("Abra um .ain primeiro") else saveAin.launch("Rance01_PT.ain")
        })
        root.addView(row1)

        val row2 = LinearLayout(this)
        row2.addView(btn("Importar CSV") {
            if (ain == null) toast("Abra um .ain primeiro") else openCsv.launch(arrayOf("*/*"))
        })
        row2.addView(btn("Exportar CSV") {
            if (ain == null) toast("Abra um .ain primeiro") else saveCsv.launch("falas.csv")
        })
        root.addView(row2)

        val row3 = LinearLayout(this)
        row3.gravity = android.view.Gravity.CENTER_VERTICAL
        row3.setPadding(dp(8), 0, dp(8), 0)
        etSearch = EditText(this)
        etSearch.hint = "Buscar"
        etSearch.inputType = InputType.TYPE_CLASS_TEXT
        etSearch.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row3.addView(etSearch)
        etLimit = EditText(this)
        etLimit.hint = "Limite"
        etLimit.inputType = InputType.TYPE_CLASS_NUMBER
        etLimit.setText("50")
        etLimit.layoutParams = LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT)
        row3.addView(etLimit)
        root.addView(row3)

        cbOnly = CheckBox(this)
        cbOnly.text = "Só não traduzidas"
        cbOnly.setPadding(dp(8), 0, dp(8), 0)
        root.addView(cbOnly)

        status = TextView(this)
        status.setPadding(dp(12), dp(4), dp(12), dp(4))
        status.text = "Abra um arquivo .ain"
        root.addView(status)

        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(rv)

        etSearch.doAfterTextChanged { refresh() }
        etLimit.doAfterTextChanged { adapter.notifyDataSetChanged() }
        cbOnly.setOnCheckedChangeListener { _, _ -> refresh() }

        setContentView(root)
    }

    // ---------- abrir / salvar ----------

    private fun loadAin(uri: Uri) {
        status.text = "Abrindo..."
        Thread {
            try {
                val data = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val f = AinFile.parse(data)
                val o = Array(f.originals.size) { String(f.originals[it], AinFile.CP932) }
                runOnUiThread {
                    ain = f
                    originals = o
                    trans = arrayOfNulls(o.size)
                    refresh()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    status.text = "Erro ao abrir"
                    toast("Erro: ${e.message}")
                }
            }
        }.start()
    }

    private fun saveAinTo(uri: Uri) {
        val f = ain ?: return
        status.text = "Salvando..."
        val snapshot = trans.copyOf()
        Thread {
            try {
                val bytes = f.build(snapshot)
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                runOnUiThread {
                    refresh()
                    toast("Salvo (${bytes.size / 1024} KB)")
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    refresh()
                    toast("Erro ao salvar: ${e.message}")
                }
            }
        }.start()
    }

    // ---------- CSV (colunas: ID, Original, Tradução) ----------

    private fun importCsv(uri: Uri) {
        status.text = "Importando..."
        Thread {
            try {
                val text = contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                val rows = Csv.parse(text)
                var count = 0
                for (r in rows) {
                    val id = r.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                    val t = r.getOrNull(2) ?: continue
                    if (id < 0 || id >= trans.size || t.isEmpty()) continue
                    trans[id] = t
                    count++
                }
                runOnUiThread {
                    refresh()
                    toast("$count falas importadas")
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    refresh()
                    toast("Erro ao importar: ${e.message}")
                }
            }
        }.start()
    }

    private fun exportCsv(uri: Uri) {
        Thread {
            try {
                val sb = StringBuilder("\uFEFF")
                sb.append("ID,Original,Traducao\n")
                for (i in originals.indices) {
                    if (originals[i].isEmpty()) continue
                    sb.append(i).append(',')
                        .append(Csv.escape(originals[i])).append(',')
                        .append(Csv.escape(trans[i] ?: "")).append('\n')
                }
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                runOnUiThread { toast("CSV exportado") }
            } catch (e: Throwable) {
                runOnUiThread { toast("Erro ao exportar: ${e.message}") }
            }
        }.start()
    }

    // ---------- lista ----------

    private fun refresh() {
        val q = etSearch.text.toString().trim()
        val only = cbOnly.isChecked
        val list = ArrayList<Int>()
        for (i in originals.indices) {
            if (originals[i].isEmpty()) continue
            val t = trans[i]
            if (only && t != null) continue
            if (q.isNotEmpty()) {
                val inOrig = originals[i].contains(q, true)
                val inTrans = t?.contains(q, true) ?: false
                if (!inOrig && !inTrans) continue
            }
            list.add(i)
        }
        shown = list
        adapter.notifyDataSetChanged()
        val done = trans.count { it != null }
        status.text = if (ain == null) {
            "Abra um arquivo .ain"
        } else {
            "${shown.size} exibidas • $done traduzidas de ${originals.size}"
        }
    }

    private fun edit(i: Int) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        val orig = TextView(this)
        orig.text = originals[i]
        orig.textSize = 15f
        box.addView(orig)

        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.setText(trans[i] ?: originals[i])
        et.setSelection(et.text.length)
        box.addView(et)

        val counter = TextView(this)
        box.addView(counter)
        fun updateCounter() {
            val lim = limit()
            val n = et.text.length
            counter.text = if (lim > 0) "$n / $lim" else "$n"
            counter.setTextColor(if (lim > 0 && n > lim) 0xFFD32F2F.toInt() else 0xFF777777.toInt())
        }
        et.doAfterTextChanged { updateCounter() }
        updateCounter()

        AlertDialog.Builder(this)
            .setTitle("Fala #$i")
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                val s = et.text.toString()
                trans[i] = if (s.isEmpty() || s == originals[i]) null else s
                refresh()
            }
            .setNeutralButton("Limpar") { _, _ ->
                trans[i] = null
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private class VH(v: View, val a: TextView, val b: TextView) : RecyclerView.ViewHolder(v)

    private inner class Adapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = shown.size

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
            a.setTextColor(0xFF777777.toInt())
            val b = TextView(parent.context)
            b.textSize = 15f
            box.addView(a)
            box.addView(b)
            return VH(box, a, b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val i = shown[position]
            val t = trans[i]
            val lim = limit()
            holder.a.text = "#$i  ${originals[i]}"
            if (t == null) {
                holder.b.text = "(toque para traduzir)"
                holder.b.setTextColor(0xFF999999.toInt())
            } else {
                holder.b.text = t
                val over = lim > 0 && t.length > lim
                holder.b.setTextColor(if (over) 0xFFD32F2F.toInt() else 0xFF2E7D32.toInt())
            }
            holder.itemView.setOnClickListener { edit(i) }
        }
    }
}
