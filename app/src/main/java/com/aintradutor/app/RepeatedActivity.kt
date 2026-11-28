package com.aintradutor.app

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Falas repetidas: copia a tradução de um trecho para os trechos idênticos ainda sem tradução. */
class RepeatedActivity : AppCompatActivity() {

    private lateinit var etMin: EditText
    private lateinit var info: TextView

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun minLen() = etMin.text.toString().trim().toIntOrNull() ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Falas repetidas"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(24))

        root.addView(
            Ui.button(this, "Desfazer última ação", false) { undo() },
            Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8)
        )

        val card = Ui.card(this)
        card.addView(Ui.heading(this, "Falas repetidas"))
        card.addView(
            Ui.secondary(
                this,
                "Copia a tradução de um trecho para os trechos idênticos que ainda não foram traduzidos. " +
                    "Só mexe em trechos sem nenhuma tradução e ignora grupos com traduções diferentes."
            ),
            Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 10)
        )
        etMin = Ui.input(this, "Ignorar trechos menores que (caracteres)")
        etMin.inputType = InputType.TYPE_CLASS_NUMBER
        etMin.setText("8")
        card.addView(etMin, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))
        val row = LinearLayout(this)
        row.addView(Ui.button(this, "Analisar", false) { analyze() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        row.addView(Ui.button(this, "Preencher repetidas", true) { fill() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
        card.addView(row)
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
        analyze()
    }

    private fun analyze() {
        if (Store.ain == null) {
            info.text = "Abra um .ain no app primeiro"
            return
        }
        val m = minLen()
        info.text = "Analisando..."
        Thread {
            val st = Dup.analyze(m)
            runOnUiThread {
                info.text = "${st.groups} grupos de falas repetidas  •  " +
                    "${st.fill} falas sem tradução podem ser preenchidas  •  " +
                    "${st.conflicts} grupos com traduções diferentes (ignorados)"
            }
        }.start()
    }

    private fun fill() {
        if (Store.ain == null) {
            toast("Abra um .ain primeiro")
            return
        }
        val m = minLen()
        Thread {
            val st = Dup.apply(m)
            Store.persist(applicationContext)
            runOnUiThread {
                toast(if (st.fill == 0) "Nada para preencher" else "${st.fill} falas preenchidas")
                analyze()
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
        analyze()
    }
}
