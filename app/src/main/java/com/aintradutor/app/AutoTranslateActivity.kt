package com.aintradutor.app

import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Tradução automática (Google Tradutor) dos trechos que ainda não têm tradução. */
class AutoTranslateActivity : AppCompatActivity() {

    private lateinit var etFrom: EditText
    private lateinit var etCount: EditText
    private lateinit var info: TextView
    private lateinit var status: TextView
    private lateinit var btnSrc: android.widget.Button
    private lateinit var btnTgt: android.widget.Button

    @Volatile
    private var stopFlag = false

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun langLabels() {
        btnSrc.text = "De: ${Langs.nameOf(Store.srcLang)}  ▾"
        btnTgt.text = "Para: ${Langs.nameOf(Store.tgtLang)}  ▾"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Tradução automática"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(24))

        val card = Ui.card(this)
        card.addView(Ui.heading(this, "Google Tradutor"))
        card.addView(
            Ui.secondary(
                this,
                "Traduz os trechos que ainda não têm nenhuma tradução e deixa como rascunho para você revisar " +
                    "(aparecem em laranja e em Problemas e reajuste). Precisa de internet."
            ),
            Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 10)
        )

        // idiomas
        val langRow = LinearLayout(this)
        btnSrc = Ui.button(this, "", false) {
            Langs.pick(this, "Idioma do texto original", true) {
                Store.srcLang = it
                Store.savePrefs(applicationContext)
                langLabels()
            }
        }
        btnTgt = Ui.button(this, "", false) {
            Langs.pick(this, "Traduzir para", false) {
                Store.tgtLang = it
                Store.savePrefs(applicationContext)
                langLabels()
            }
        }
        langRow.addView(btnSrc, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        langRow.addView(btnTgt, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
        card.addView(langRow, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6))
        card.addView(
            Ui.secondary(
                this,
                "O .ain usa a codificação Shift-JIS: letras fora do alfabeto latino e do japonês podem virar " +
                    "\"?\" no jogo (confira em Problemas e reajuste)."
            ),
            Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 0, 10)
        )

        status = Ui.secondary(this, "")
        card.addView(status, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 0, 10))

        etFrom = Ui.input(this, "A partir da fala #")
        etFrom.inputType = InputType.TYPE_CLASS_NUMBER
        etFrom.setText("0")
        card.addView(etFrom, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))
        etCount = Ui.input(this, "Quantidade de trechos")
        etCount.inputType = InputType.TYPE_CLASS_NUMBER
        etCount.setText("100")
        card.addView(etCount, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))

        val row = LinearLayout(this)
        row.addView(Ui.button(this, "Traduzir", true) { run() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        row.addView(Ui.button(this, "Parar", false) { stopFlag = true }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
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
        langLabels()
        refreshStatus()
    }

    private fun refreshStatus() {
        if (Store.ain == null) {
            status.text = "Abra um .ain no app primeiro"
            return
        }
        Thread {
            var open = 0
            for (u in Store.units()) if (u.all { Store.trans[it] == null }) open++
            val total = Store.units().size
            runOnUiThread { status.text = "$open de $total trechos ainda sem nenhuma tradução" }
        }.start()
    }

    private fun run() {
        if (Store.ain == null) {
            toast("Abra um .ain primeiro")
            return
        }
        val from = etFrom.text.toString().trim().toIntOrNull() ?: 0
        val count = etCount.text.toString().trim().toIntOrNull() ?: 100
        stopFlag = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        info.text = "Conectando ao Google Tradutor..."
        Thread {
            try {
                Auto.ensureReady()
                val todo = ArrayList<IntArray>()
                for (u in Store.units()) {
                    if (u[0] < from) continue
                    if (u.all { Store.trans[it] == null }) {
                        todo.add(u)
                        if (todo.size >= count) break
                    }
                }
                var done = 0
                for (u in todo) {
                    if (stopFlag) break
                    Auto.translateUnit(u)
                    done++
                    if (done % 5 == 0) {
                        val progress = done
                        runOnUiThread { info.text = "Traduzindo... $progress / ${todo.size}" }
                    }
                    if (done % 50 == 0) Store.persist(applicationContext)
                }
                Store.persist(applicationContext)
                val total = done
                runOnUiThread {
                    info.text = "Pronto: $total trechos traduzidos como rascunho. " +
                        "Revise em Problemas e reajuste > Rascunho automático."
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    refreshStatus()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    info.text = "Erro: ${e.message}. Confira a conexão com a internet e tente de novo."
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }.start()
    }
}
