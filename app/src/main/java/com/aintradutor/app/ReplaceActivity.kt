package com.aintradutor.app

import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Localizar e substituir dentro das traduções. */
class ReplaceActivity : AppCompatActivity() {

    private lateinit var etFind: EditText
    private lateinit var etRepl: EditText
    private lateinit var cbCase: CheckBox
    private lateinit var cbWhole: CheckBox
    private lateinit var info: TextView

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Localizar e substituir"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(24))

        root.addView(
            Ui.button(this, "Desfazer última ação", false) { undo() },
            Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8)
        )

        val card = Ui.card(this)
        card.addView(Ui.heading(this, "Localizar e substituir"))
        card.addView(Ui.secondary(this, "Age só nas traduções, nunca no texto original."), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 10))
        etFind = Ui.input(this, "Localizar")
        card.addView(etFind, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))
        etRepl = Ui.input(this, "Substituir por")
        card.addView(etRepl, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 4))
        cbCase = CheckBox(this)
        cbCase.text = "Diferenciar maiúsculas e minúsculas"
        card.addView(cbCase)
        cbWhole = CheckBox(this)
        cbWhole.text = "Só palavra inteira"
        card.addView(cbWhole)
        val row = LinearLayout(this)
        row.addView(Ui.button(this, "Contar", false) { count() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        row.addView(Ui.button(this, "Substituir tudo", true) { apply() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
        card.addView(row, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0))
        info = Ui.secondary(this, "")
        card.addView(info, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0))
        root.addView(card)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Store.loadPrefs(applicationContext)
        Store.restore(applicationContext)
    }

    private fun need(): Boolean {
        if (Store.ain == null) {
            toast("Abra um .ain primeiro")
            return false
        }
        return true
    }

    private fun rx(): Regex? {
        val f = etFind.text.toString()
        if (f.isEmpty()) {
            toast("Digite o que localizar")
            return null
        }
        return Replace.regex(f, cbCase.isChecked, cbWhole.isChecked)
    }

    private fun count() {
        if (!need()) return
        val r = rx() ?: return
        val st = Replace.count(r)
        val sb = StringBuilder("${st.occurrences} ocorrências em ${st.lines} falas")
        for (i in st.examples) {
            val t = Store.trans[i] ?: ""
            sb.append("\n#").append(i).append(": ").append(if (t.length > 60) t.substring(0, 60) + "…" else t)
        }
        info.text = sb.toString()
    }

    private fun apply() {
        if (!need()) return
        val r = rx() ?: return
        val repl = etRepl.text.toString()
        Thread {
            val n = Replace.apply(r, repl)
            Store.persist(applicationContext)
            runOnUiThread {
                toast("$n falas alteradas. Confira em Problemas e reajuste se alguma passou do limite.")
                info.text = ""
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
    }
}
