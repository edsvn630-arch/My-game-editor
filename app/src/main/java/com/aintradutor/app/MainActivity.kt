package com.aintradutor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.media.projection.MediaProjectionManager

class MainActivity : AppCompatActivity() {

    private var shown: List<Int> = emptyList()

    private lateinit var adapter: Adapter
    private lateinit var status: TextView
    private lateinit var etSearch: EditText
    private lateinit var etLimit: EditText
    private lateinit var cbOnly: CheckBox
    private lateinit var cbStrip: CheckBox
    private lateinit var cbEmpty: CheckBox
    private var bar: FastScrollBar? = null

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
    private val requestNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        askCapture()
    }
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) {
            val i = Intent(this, LiveService::class.java)
            i.putExtra("code", r.resultCode)
            i.putExtra("data", data)
            ContextCompat.startForegroundService(this, i)
            toast("Modo ao vivo ligado. Abra o jogo e toque no botão azul \"Aa\".")
        } else {
            toast("Captura de tela não autorizada")
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun limit() = etLimit.text.toString().trim().toIntOrNull() ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = Adapter()
        Store.loadPrefs(applicationContext)

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
            if (Store.ain == null) toast("Abra um .ain primeiro") else saveAin.launch("Rance01_PT.ain")
        })
        root.addView(row1)

        val row2 = LinearLayout(this)
        row2.addView(btn("Importar CSV") {
            if (Store.ain == null) toast("Abra um .ain primeiro") else openCsv.launch(arrayOf("*/*"))
        })
        row2.addView(btn("Exportar CSV") {
            if (Store.ain == null) toast("Abra um .ain primeiro") else saveCsv.launch("falas.csv")
        })
        root.addView(row2)

        val row2b = LinearLayout(this)
        row2b.addView(btn("Modo ao vivo (sobre o jogo)") { startLive() })
        row2b.addView(btn("Problemas e reajuste") {
            if (Store.ain == null) toast("Abra um .ain primeiro") else startActivity(Intent(this, ProblemsActivity::class.java))
        })
        root.addView(row2b)

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
        etLimit.setText(Store.globalLimit.toString())
        etLimit.layoutParams = LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT)
        row3.addView(etLimit)
        root.addView(row3)

        cbOnly = CheckBox(this)
        cbOnly.text = "Só não traduzidas"
        cbOnly.setPadding(dp(8), 0, dp(8), 0)
        root.addView(cbOnly)

        cbStrip = CheckBox(this)
        cbStrip.text = "Remover acentos e ç ao salvar o .ain"
        cbStrip.setPadding(dp(8), 0, dp(8), 0)
        cbStrip.isChecked = Store.stripAccents
        root.addView(cbStrip)

        cbEmpty = CheckBox(this)
        cbEmpty.text = "Mostrar falas vazias"
        cbEmpty.setPadding(dp(8), 0, dp(8), 0)
        cbEmpty.isChecked = Store.showEmpty
        root.addView(cbEmpty)

        status = TextView(this)
        status.setPadding(dp(12), dp(4), dp(12), dp(4))
        status.text = "Abra um arquivo .ain"
        root.addView(status)

        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        val frame = FrameLayout(this)
        frame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        rv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        frame.addView(rv)
        val fastBar = FastScrollBar(this, rv) { pos -> if (pos in shown.indices) "#${shown[pos]}" else "" }
        fastBar.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        frame.addView(fastBar)
        bar = fastBar
        root.addView(frame)

        etSearch.doAfterTextChanged { refresh() }
        etLimit.doAfterTextChanged {
            Store.globalLimit = limit()
            Store.savePrefs(applicationContext)
            adapter.notifyDataSetChanged()
        }
        cbOnly.setOnCheckedChangeListener { _, _ -> refresh() }
        cbStrip.setOnCheckedChangeListener { _, checked ->
            Store.stripAccents = checked
            Store.savePrefs(applicationContext)
        }
        cbEmpty.setOnCheckedChangeListener { _, checked ->
            Store.showEmpty = checked
            Store.savePrefs(applicationContext)
            refresh()
        }

        setContentView(root)

        // recupera o último projeto, se o app foi fechado
        Thread {
            if (Store.ain == null && Store.restore(applicationContext)) {
                runOnUiThread { refresh() }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        refresh() // traduções feitas no modo ao vivo aparecem aqui
    }

    // ---------- modo ao vivo ----------

    private fun startLive() {
        if (Store.ain == null) {
            toast("Abra um .ain primeiro")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Ative \"Aparecer sobre outros apps\" para o Ain Tradutor e toque de novo em Modo ao vivo")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            askCapture()
        }
    }

    private fun askCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // ---------- abrir / salvar ----------

    private fun loadAin(uri: Uri) {
        status.text = "Abrindo..."
        Thread {
            try {
                val data = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                Store.load(applicationContext, data)
                runOnUiThread { refresh() }
            } catch (e: Throwable) {
                runOnUiThread {
                    status.text = "Erro ao abrir"
                    toast("Erro: ${e.message}")
                }
            }
        }.start()
    }

    private fun saveAinTo(uri: Uri) {
        val f = Store.ain ?: return
        status.text = "Salvando..."
        val snapshot = Store.trans.copyOf()
        val strip = Store.stripAccents
        Thread {
            try {
                val bytes = f.build(snapshot, strip)
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
                    if (id < 0 || id >= Store.trans.size || t.isEmpty()) continue
                    Store.setTrans(id, t)
                    count++
                }
                Store.persist(applicationContext)
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
                for (i in Store.originals.indices) {
                    if (Store.originals[i].isEmpty()) continue
                    sb.append(i).append(',')
                        .append(Csv.escape(Store.originals[i])).append(',')
                        .append(Csv.escape(Store.trans[i] ?: "")).append('\n')
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
        val originals = Store.originals
        val trans = Store.trans
        val q = etSearch.text.toString().trim()
        val only = cbOnly.isChecked
        val showEmpty = cbEmpty.isChecked
        val list = ArrayList<Int>()
        for (i in originals.indices) {
            if (originals[i].isEmpty() && !showEmpty) continue
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
        bar?.invalidate()
        val done = trans.count { it != null }
        status.text = if (Store.ain == null) {
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
        orig.text = Store.originals[i]
        orig.textSize = 15f
        box.addView(orig)

        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.setText(Store.trans[i] ?: Store.originals[i])
        et.setSelection(et.text.length)
        box.addView(et)

        val row = LinearLayout(this)
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        val counter = TextView(this)
        counter.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(counter)
        val lbl = TextView(this)
        lbl.text = "Limite desta fala:"
        lbl.textSize = 12f
        row.addView(lbl)
        val etLim = EditText(this)
        etLim.inputType = InputType.TYPE_CLASS_NUMBER
        etLim.hint = Store.globalLimit.toString()
        if (Store.limits[i] > 0) etLim.setText(Store.limits[i].toString())
        etLim.layoutParams = LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT)
        row.addView(etLim)
        box.addView(row)

        fun updateCounter() {
            val lim = etLim.text.toString().trim().toIntOrNull() ?: Store.globalLimit
            val n = et.text.length
            counter.text = if (lim > 0) "$n / $lim" else "$n"
            counter.setTextColor(if (lim > 0 && n > lim) 0xFFD32F2F.toInt() else 0xFF777777.toInt())
        }
        et.doAfterTextChanged { updateCounter() }
        etLim.doAfterTextChanged { updateCounter() }
        updateCounter()

        AlertDialog.Builder(this)
            .setTitle("Fala #$i")
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                Store.setLimit(i, etLim.text.toString().trim().toIntOrNull() ?: 0)
                Store.setTrans(i, et.text.toString())
                Store.persistAsync(applicationContext)
                refresh()
            }
            .setNeutralButton("Limpar") { _, _ ->
                Store.trans[i] = null
                Store.persistAsync(applicationContext)
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
            val t = Store.trans[i]
            val lim = Store.limitOf(i)
            holder.a.text = "#$i  ${Store.originals[i]}"
            if (t == null) {
                holder.b.text = "(toque para traduzir)"
                holder.b.setTextColor(0xFF999999.toInt())
            } else {
                holder.b.text = if (lim > 0) "$t  [${t.length}/$lim]" else t
                val over = lim > 0 && t.length > lim
                holder.b.setTextColor(if (over) 0xFFD32F2F.toInt() else 0xFF2E7D32.toInt())
            }
            holder.itemView.setOnClickListener { edit(i) }
        }
    }
}
