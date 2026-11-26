package com.aintradutor.app

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ToolsActivity : AppCompatActivity() {

    private lateinit var etMin: EditText
    private lateinit var dupInfo: TextView
    private lateinit var etFind: EditText
    private lateinit var etRepl: EditText
    private lateinit var cbCase: CheckBox
    private lateinit var cbWhole: CheckBox
    private lateinit var repInfo: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun minLen() = etMin.text.toString().trim().toIntOrNull() ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Repetidas e substituir"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(8), dp(12), dp(24))

        fun heading(t: String): TextView {
            val v = TextView(this@ToolsActivity)
            v.text = t
            v.textSize = 17f
            v.setTypeface(null, android.graphics.Typeface.BOLD)
            v.setPadding(0, dp(16), 0, dp(4))
            return v
        }

        fun btn(label: String, action: () -> Unit): Button {
            val b = Button(this@ToolsActivity)
            b.text = label
            b.textSize = 12f
            b.isAllCaps = false
            b.setOnClickListener { action() }
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            return b
        }

        fun edit(hint: String): EditText {
            val e = EditText(this@ToolsActivity)
            e.hint = hint
            e.inputType = InputType.TYPE_CLASS_TEXT
            return e
        }

        // desfazer (vale para a última ação feita aqui, no reajuste de linhas e nas repetidas)
        val rowUndo = LinearLayout(this)
        rowUndo.addView(btn("Desfazer última ação") { undo() })
        root.addView(rowUndo)

        // ---------- repetidas ----------
        root.addView(heading("Falas repetidas"))
        val explain = TextView(this)
        explain.text = "Copia a tradução de um trecho para os trechos idênticos que ainda não foram traduzidos. " +
            "Só mexe em trechos sem nenhuma tradução e ignora grupos com traduções diferentes."
        explain.textSize = 13f
        root.addView(explain)

        etMin = edit("Ignorar trechos menores que (caracteres)")
        etMin.inputType = InputType.TYPE_CLASS_NUMBER
        etMin.setText("8")
        root.addView(etMin)

        val rowDup = LinearLayout(this)
        rowDup.addView(btn("Analisar") { analyzeDup() })
        rowDup.addView(btn("Preencher repetidas") { fillDup() })
        root.addView(rowDup)

        dupInfo = TextView(this)
        dupInfo.setPadding(0, dp(4), 0, 0)
        root.addView(dupInfo)

        // ---------- localizar e substituir ----------
        root.addView(heading("Localizar e substituir"))
        val explain2 = TextView(this)
        explain2.text = "Age só nas traduções, nunca no texto original."
        explain2.textSize = 13f
        root.addView(explain2)

        etFind = edit("Localizar")
        root.addView(etFind)
        etRepl = edit("Substituir por")
        root.addView(etRepl)

        cbCase = CheckBox(this)
        cbCase.text = "Diferenciar maiúsculas e minúsculas"
        root.addView(cbCase)
        cbWhole = CheckBox(this)
        cbWhole.text = "Só palavra inteira"
        root.addView(cbWhole)

        val rowRep = LinearLayout(this)
        rowRep.addView(btn("Contar") { countRep() })
        rowRep.addView(btn("Substituir tudo") { applyRep() })
        root.addView(rowRep)

        repInfo = TextView(this)
        repInfo.setPadding(0, dp(4), 0, 0)
        root.addView(repInfo)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Store.loadPrefs(applicationContext)
        Store.restore(applicationContext)
        analyzeDup()
    }

    private fun need(): Boolean {
        if (Store.ain == null) {
            toast("Abra um .ain primeiro")
            return false
        }
        return true
    }

    // ---------- repetidas ----------

    private fun analyzeDup() {
        if (Store.ain == null) {
            dupInfo.text = "Abra um .ain no app primeiro"
            return
        }
        val m = minLen()
        dupInfo.text = "Analisando..."
        Thread {
            val st = Dup.analyze(m)
            runOnUiThread {
                dupInfo.text = "${st.groups} grupos de falas repetidas • " +
                    "${st.fill} falas sem tradução podem ser preenchidas • " +
                    "${st.conflicts} grupos com traduções diferentes (ignorados)"
            }
        }.start()
    }

    private fun fillDup() {
        if (!need()) return
        val m = minLen()
        Thread {
            val st = Dup.apply(m)
            Store.persist(applicationContext)
            runOnUiThread {
                toast(if (st.fill == 0) "Nada para preencher" else "${st.fill} falas preenchidas")
                analyzeDup()
            }
        }.start()
    }

    // ---------- localizar e substituir ----------

    private fun rx(): Regex? {
        val f = etFind.text.toString()
        if (f.isEmpty()) {
            toast("Digite o que localizar")
            return null
        }
        return Replace.regex(f, cbCase.isChecked, cbWhole.isChecked)
    }

    private fun countRep() {
        if (!need()) return
        val r = rx() ?: return
        val st = Replace.count(r)
        val sb = StringBuilder("${st.occurrences} ocorrências em ${st.lines} falas")
        for (i in st.examples) {
            val t = Store.trans[i] ?: ""
            sb.append("\n#").append(i).append(": ").append(if (t.length > 60) t.substring(0, 60) + "…" else t)
        }
        repInfo.text = sb.toString()
    }

    private fun applyRep() {
        if (!need()) return
        val r = rx() ?: return
        val repl = etRepl.text.toString()
        Thread {
            val n = Replace.apply(r, repl)
            Store.persist(applicationContext)
            runOnUiThread {
                toast("$n falas alteradas. Confira em Problemas e reajuste se alguma passou do limite.")
                repInfo.text = ""
                analyzeDup()
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
        toast("Ação desfeita")
        analyzeDup()
    }
}
