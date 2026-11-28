package com.aintradutor.app

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Textos do sistema do .ain (tabela STR0): menus, escolhas, mensagens e também códigos internos.
 * Aqui dá para ver e editar todos, não só as falas de diálogo.
 */
class SysTextActivity : AppCompatActivity() {

    private var shown: List<Int> = emptyList()
    private var hideCodes = true
    private var showJp = false
    private var onlyOpen = false

    private lateinit var adapter: Adapter
    private lateinit var etSearch: EditText
    private lateinit var info: TextView
    private lateinit var chipCodes: TextView
    private lateinit var chipJp: TextView
    private lateinit var chipOpen: TextView

    private val codeRx = Regex("^[A-Z0-9_]{3,}$")
    private val jpRx = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff\\uff00-\\uffef]")

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun isCode(s: String): Boolean =
        codeRx.matches(s) || s.startsWith("<") ||
            (s.contains('%') && !s.contains(' ') && s.length < 14) ||
            s.none { it.isLetter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Textos do sistema"
        adapter = Adapter()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val warn = Ui.card(this)
        warn.addView(
            Ui.secondary(
                this,
                "Menus, escolhas e mensagens do próprio jogo. Alguns textos também funcionam como código " +
                    "(nomes de arquivo, chaves). Se algo parar de funcionar, toque no texto e use Restaurar."
            )
        )
        root.addView(warn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 10, 12, 6))

        chipCodes = Ui.toggleChip(this, "Esconder códigos", hideCodes) {
            hideCodes = !hideCodes
            refresh()
        }
        chipJp = Ui.toggleChip(this, "Mostrar japonês", showJp) {
            showJp = !showJp
            refresh()
        }
        chipOpen = Ui.toggleChip(this, "Só não traduzidos", onlyOpen) {
            onlyOpen = !onlyOpen
            refresh()
        }
        val chipRow = LinearLayout(this)
        chipRow.setPadding(dp(12), dp(4), dp(12), 0)
        for (c in listOf(chipCodes, chipJp, chipOpen)) chipRow.addView(c, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0))
        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chipRow)
        root.addView(chipScroll, Ui.lp(this, Ui.MATCH, Ui.WRAP))

        etSearch = Ui.input(this, "Buscar no texto ou na tradução")
        root.addView(etSearch, Ui.lp(this, Ui.MATCH, dp(48), 12, 10, 12, 6))

        info = Ui.secondary(this, "")
        info.setPadding(dp(16), dp(2), dp(16), dp(4))
        root.addView(info)

        val rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.clipToPadding = false
        rv.setPadding(0, dp(2), 0, dp(24))
        root.addView(rv, Ui.lp(this, Ui.MATCH, 0, 0, 0, 0, 0, 1f))

        etSearch.doAfterTextChanged { refresh() }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        Store.loadPrefs(applicationContext)
        Store.restore(applicationContext)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        Store.persistAsync(applicationContext)
    }

    private fun refresh() {
        Ui.setToggle(chipCodes, hideCodes)
        Ui.setToggle(chipJp, showJp)
        Ui.setToggle(chipOpen, onlyOpen)
        val orig = Store.sOriginals
        val tr = Store.sTrans
        if (Store.ain == null) {
            info.text = "Abra um .ain no app primeiro"
            shown = emptyList()
            adapter.notifyDataSetChanged()
            return
        }
        if (orig.isEmpty()) {
            info.text = "Este .ain não tem a tabela de textos do sistema (STR0)."
            shown = emptyList()
            adapter.notifyDataSetChanged()
            return
        }
        val q = etSearch.text.toString().trim()
        val list = ArrayList<Int>()
        for (i in orig.indices) {
            val s = orig[i]
            if (s.isEmpty()) continue
            if (hideCodes && isCode(s)) continue
            if (!showJp && jpRx.containsMatchIn(s)) continue
            val t = tr[i]
            if (onlyOpen && t != null) continue
            if (q.isNotEmpty() && !s.contains(q, true) && !(t?.contains(q, true) ?: false)) continue
            list.add(i)
        }
        shown = list
        adapter.notifyDataSetChanged()
        info.text = "${shown.size} textos na lista  •  ${tr.count { it != null }} traduzidos de ${orig.count { it.isNotEmpty() }}"
    }

    private fun edit(i: Int) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        val orig = TextView(this)
        orig.text = Store.sOriginals[i]
        orig.textSize = 15f
        box.addView(orig)

        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.setText(Store.sTrans[i] ?: Store.sOriginals[i])
        et.setSelection(et.text.length)
        box.addView(et)

        box.addView(
            Ui.button(this, "Traduzir (auto)", false) {
                toast("Traduzindo...")
                Thread {
                    try {
                        val s = Auto.translate(Store.sOriginals[i])
                        runOnUiThread {
                            et.setText(s)
                            et.setSelection(et.text.length)
                        }
                    } catch (e: Throwable) {
                        runOnUiThread { toast("Erro: ${e.message}. Confira a internet.") }
                    }
                }.start()
            },
            Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4)
        )

        AlertDialog.Builder(this)
            .setTitle("Texto #$i")
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                Store.setSys(i, et.text.toString())
                Store.persistAsync(applicationContext)
                refresh()
            }
            .setNeutralButton("Restaurar") { _, _ ->
                Store.sTrans[i] = null
                Store.persistAsync(applicationContext)
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private class VH(v: View, val strip: View, val a: TextView, val b: TextView) : RecyclerView.ViewHolder(v)

    private inner class Adapter : RecyclerView.Adapter<VH>() {
        override fun getItemCount() = shown.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.HORIZONTAL
            card.background = ContextCompat.getDrawable(ctx, R.drawable.bg_card)
            card.clipToOutline = true
            Ui.ripple(card)
            val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(12), dp(4), dp(12), dp(4))
            card.layoutParams = lp
            val strip = View(ctx)
            card.addView(strip, LinearLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT))
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_VERTICAL
            col.setPadding(dp(12), dp(10), dp(12), dp(10))
            val a = TextView(ctx)
            a.textSize = 14f
            a.setTextColor(Ui.color(ctx, R.color.text2))
            val b = TextView(ctx)
            b.textSize = 16f
            b.setPadding(0, dp(4), 0, 0)
            col.addView(a)
            col.addView(b)
            card.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return VH(card, strip, a, b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val i = shown[position]
            val ctx = holder.itemView.context
            val t = Store.sTrans[i]
            holder.a.text = "#$i  ${Store.sOriginals[i]}"
            if (t == null) {
                holder.b.text = "Toque para traduzir"
                holder.b.setTextColor(Ui.color(ctx, R.color.text2))
            } else {
                holder.b.text = t
                holder.b.setTextColor(Ui.color(ctx, R.color.text))
            }
            holder.strip.setBackgroundColor(Ui.color(ctx, if (t == null) R.color.neutral else R.color.good))
            holder.itemView.setOnClickListener { edit(i) }
        }
    }
}
