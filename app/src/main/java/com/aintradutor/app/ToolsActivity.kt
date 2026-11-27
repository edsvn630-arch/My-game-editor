package com.aintradutor.app

import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
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
    private lateinit var etFrom: EditText
    private lateinit var etCount: EditText
    private lateinit var autoInfo: TextView

    @Volatile
    private var stopFlag = false

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun minLen() = etMin.text.toString().trim().toIntOrNull() ?: 0

    private fun numberInput(hint: String, value: String): EditText {
        val e = Ui.input(this, hint)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(value)
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Repetidas, substituir e tradução"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(24))

        // desfazer (vale para a última ação feita aqui, no reajuste de linhas e nas repetidas)
        root.addView(
            Ui.button(this, "Desfazer última ação", false) { undo() },
            Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8)
        )

        // ---------- repetidas ----------
        val dup = Ui.card(this)
        dup.addView(Ui.heading(this, "Falas repetidas"))
        dup.addView(
            Ui.secondary(
                this,
                "Copia a tradução de um trecho para os trechos idênticos que ainda não foram traduzidos. " +
                    "Só mexe em trechos sem nenhuma tradução e ignora grupos com traduções diferentes."
            ),
            Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 10)
        )
        etMin = numberInput("Ignorar trechos menores que (caracteres)", "8")
        dup.addView(etMin, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))
        val rowDup = LinearLayout(this)
        rowDup.addView(Ui.button(this, "Analisar", false) { analyzeDup() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        rowDup.addView(Ui.button(this, "Preencher repetidas", true) { fillDup() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
        dup.addView(rowDup)
        dupInfo = Ui.secondary(this, "")
        dup.addView(dupInfo, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0))
        root.addView(dup, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10))

        // ---------- localizar e substituir ----------
        val rep = Ui.card(this)
        rep.addView(Ui.heading(this, "Localizar e substituir"))
        rep.addView(Ui.secondary(this, "Age só nas traduções, nunca no texto original."), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 10))
        etFind = Ui.input(this, "Localizar")
        rep.addView(etFind, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))
        etRepl = Ui.input(this, "Substituir por")
        rep.addView(etRepl, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 4))
        cbCase = CheckBox(this)
        cbCase.text = "Diferenciar maiúsculas e minúsculas"
        rep.addView(cbCase)
        cbWhole = CheckBox(this)
        cbWhole.text = "Só palavra inteira"
        rep.addView(cbWhole)
        val rowRep = LinearLayout(this)
        rowRep.addView(Ui.button(this, "Contar", false) { countRep() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        rowRep.addView(Ui.button(this, "Substituir tudo", true) { applyRep() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
        rep.addView(rowRep, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0))
        repInfo = Ui.secondary(this, "")
        rep.addView(repInfo, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0))
        root.addView(rep, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10))

        // ---------- tradução automática ----------
        val auto = Ui.card(this)
        auto.addView(Ui.heading(this, "Tradução automática (Google Tradutor)"))
        auto.addView(
            Ui.secondary(
                this,
                "Traduz os trechos que ainda não têm nenhuma tradução e deixa como rascunho para você revisar " +
                    "(aparecem em laranja e em Problemas e reajuste). Precisa de internet."
            ),
            Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 10)
        )
        etFrom = numberInput("A partir da fala #", "0")
        auto.addView(etFrom, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))
        etCount = numberInput("Quantidade de trechos", "100")
        auto.addView(etCount, Ui.lp(this, Ui.MATCH, dp(48), 0, 0, 0, 8))
        val rowAuto = LinearLayout(this)
        rowAuto.addView(Ui.button(this, "Traduzir", true) { autoRun() }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0, 1f))
        rowAuto.addView(Ui.button(this, "Parar", false) { stopFlag = true }, Ui.lp(this, 0, Ui.WRAP, 0, 0, 0, 0, 1f))
        auto.addView(rowAuto)
        autoInfo = Ui.secondary(this, "")
        auto.addView(autoInfo, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0))
        root.addView(auto, Ui.lp(this, Ui.MATCH, Ui.WRAP))

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
                dupInfo.text = "${st.groups} grupos de falas repetidas  •  " +
                    "${st.fill} falas sem tradução podem ser preenchidas  •  " +
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

    // ---------- tradução automática ----------

    private fun autoRun() {
        if (!need()) return
        val from = etFrom.text.toString().trim().toIntOrNull() ?: 0
        val count = etCount.text.toString().trim().toIntOrNull() ?: 100
        stopFlag = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        autoInfo.text = "Conectando ao Google Tradutor..."
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
                        runOnUiThread { autoInfo.text = "Traduzindo... $progress / ${todo.size}" }
                    }
                    if (done % 50 == 0) Store.persist(applicationContext)
                }
                Store.persist(applicationContext)
                val total = done
                runOnUiThread {
                    autoInfo.text = "Pronto: $total trechos traduzidos como rascunho. " +
                        "Revise em Problemas e reajuste > Rascunho automático."
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    analyzeDup()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    autoInfo.text = "Erro: ${e.message}. Confira a conexão com a internet e tente de novo."
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
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
