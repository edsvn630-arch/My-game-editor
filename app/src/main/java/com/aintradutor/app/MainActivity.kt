package com.aintradutor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val M_OPEN = 1
        private const val M_SAVE = 2
        private const val M_SAVE_GAME = 3
        private const val M_GAME_SETUP = 4
        private const val M_IMPORT = 5
        private const val M_EXPORT = 6
        private const val M_LIVE = 7
        private const val M_PROBLEMS = 8
        private const val M_TOOLS = 9
        private const val M_LIMIT = 10
        private const val M_ONLY = 11
        private const val M_STRIP = 12
        private const val M_EMPTY = 13
        private const val M_OCR = 14
        private const val M_PROGRESS = 15
        private const val M_PROJECTS = 16
        private const val M_INFO = 17
    }

    private var shown: List<Int> = emptyList()
    private var onlyUntranslated = false

    private lateinit var adapter: Adapter
    private lateinit var status: TextView
    private lateinit var etSearch: EditText
    private lateinit var progress: ProgressBar
    private lateinit var empty: View
    private lateinit var fab: TextView
    private lateinit var rv: RecyclerView
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
    private val pickGameDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onGameDirPicked(uri)
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

    private fun need(): Boolean {
        if (Store.ain == null) {
            toast("Abra um .ain primeiro (menu ⋮ → Abrir .ain)")
            return false
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = Adapter()
        Store.loadPrefs(applicationContext)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        // busca
        etSearch = Ui.input(this, "Buscar no original ou na tradução")
        root.addView(etSearch, Ui.lp(this, Ui.MATCH, Ui.dp(this, 48), 12, 10, 12, 6))

        // cabeçalho com o progresso geral
        val head = Ui.card(this)
        status = TextView(this)
        status.textSize = 13f
        status.setTextColor(Ui.color(this, R.color.text2))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 1000
        progress.progressDrawable = ContextCompat.getDrawable(this, R.drawable.progress_bar)
        head.addView(status)
        head.addView(progress, Ui.lp(this, Ui.MATCH, Ui.dp(this, 6), 0, 8, 0, 0))
        root.addView(head, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 4, 12, 6))

        // lista + barra de rolagem rápida + estado vazio + botão flutuante
        rv = RecyclerView(this)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.clipToPadding = false
        rv.setPadding(0, dp(4), 0, dp(88))
        val frame = FrameLayout(this)
        frame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        frame.addView(rv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val fastBar = FastScrollBar(this, rv) { pos -> if (pos in shown.indices) "#${shown[pos]}" else "" }
        frame.addView(fastBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bar = fastBar
        empty = buildEmpty()
        frame.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fab = TextView(this)
        fab.text = "▶  Ao vivo"
        fab.textSize = 15f
        fab.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        fab.setTextColor(Ui.color(this, R.color.on_accent))
        fab.background = ContextCompat.getDrawable(this, R.drawable.bg_fab)
        fab.setPadding(dp(20), dp(14), dp(20), dp(14))
        fab.elevation = dp(8).toFloat()
        fab.setOnClickListener { startLive() }
        val fabLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
        fabLp.setMargins(0, 0, dp(56), dp(20))
        frame.addView(fab, fabLp)
        root.addView(frame)

        etSearch.doAfterTextChanged { refresh() }
        setContentView(root)

        // recupera o último projeto, se o app foi fechado
        Thread {
            if (Store.ain == null && Store.restore(applicationContext)) {
                runOnUiThread { refresh() }
            }
        }.start()
    }

    private fun buildEmpty(): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.gravity = Gravity.CENTER
        wrap.setPadding(dp(24), 0, dp(24), dp(48))
        val card = Ui.card(this)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(Ui.heading(this, "Nenhum jogo aberto"))
        val s = Ui.secondary(this, "Abra o arquivo .ain do jogo para começar a traduzir. As traduções ficam salvas separadas por jogo.")
        s.gravity = Gravity.CENTER
        card.addView(s, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 6, 0, 14))
        card.addView(Ui.button(this, "Abrir .ain", true) { openAin.launch(arrayOf("*/*")) })
        wrap.addView(card, Ui.lp(this, Ui.MATCH, Ui.WRAP))
        return wrap
    }

    override fun onResume() {
        super.onResume()
        refresh() // traduções feitas em outras telas aparecem aqui
    }

    // ---------- menu (três pontinhos) ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, M_OPEN, Menu.NONE, "Abrir .ain")
        menu.add(Menu.NONE, M_SAVE, Menu.NONE, "Salvar .ain…")
        menu.add(Menu.NONE, M_SAVE_GAME, Menu.NONE, "Salvar na pasta do jogo")
        menu.add(Menu.NONE, M_GAME_SETUP, Menu.NONE, "Trocar pasta/nome do jogo…")
        menu.add(Menu.NONE, M_IMPORT, Menu.NONE, "Importar CSV")
        menu.add(Menu.NONE, M_EXPORT, Menu.NONE, "Exportar CSV")
        menu.add(Menu.NONE, M_LIVE, Menu.NONE, "Modo ao vivo (sobre o jogo)")
        menu.add(Menu.NONE, M_OCR, Menu.NONE, "OCR: modelos do PaddleOCR")
        menu.add(Menu.NONE, M_PROBLEMS, Menu.NONE, "Problemas e reajuste")
        menu.add(Menu.NONE, M_PROGRESS, Menu.NONE, "Progresso do jogo")
        menu.add(Menu.NONE, M_TOOLS, Menu.NONE, "Repetidas, substituir e tradução automática")
        menu.add(Menu.NONE, M_PROJECTS, Menu.NONE, "Projetos (um por jogo)…")
        menu.add(Menu.NONE, M_INFO, Menu.NONE, "Informações do .ain")
        menu.add(Menu.NONE, M_LIMIT, Menu.NONE, "Limite de caracteres")
        menu.add(Menu.NONE, M_ONLY, Menu.NONE, "Só não traduzidas").isCheckable = true
        menu.add(Menu.NONE, M_STRIP, Menu.NONE, "Remover acentos e ç ao salvar o .ain").isCheckable = true
        menu.add(Menu.NONE, M_EMPTY, Menu.NONE, "Mostrar falas vazias").isCheckable = true
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(M_LIMIT)?.title = "Limite de caracteres: ${Store.globalLimit}"
        menu.findItem(M_ONLY)?.isChecked = onlyUntranslated
        menu.findItem(M_STRIP)?.isChecked = Store.stripAccents
        menu.findItem(M_EMPTY)?.isChecked = Store.showEmpty
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            M_OPEN -> openAin.launch(arrayOf("*/*"))
            M_SAVE -> if (need()) saveAin.launch(if (Store.gameName.isNotBlank()) Store.gameName else Store.projectId + "_PT.ain")
            M_SAVE_GAME -> saveToGame()
            M_GAME_SETUP -> if (need()) setupGame()
            M_IMPORT -> if (need()) openCsv.launch(arrayOf("*/*"))
            M_EXPORT -> if (need()) saveCsv.launch("falas.csv")
            M_LIVE -> startLive()
            M_OCR -> ocrModels()
            M_PROBLEMS -> if (need()) startActivity(Intent(this, ProblemsActivity::class.java))
            M_TOOLS -> if (need()) startActivity(Intent(this, ToolsActivity::class.java))
            M_PROGRESS -> if (need()) startActivity(Intent(this, ProgressActivity::class.java))
            M_PROJECTS -> showProjects()
            M_INFO -> showInfo()
            M_LIMIT -> askLimit()
            M_ONLY -> {
                onlyUntranslated = !onlyUntranslated
                refresh()
            }
            M_STRIP -> {
                Store.stripAccents = !Store.stripAccents
                Store.savePrefs(applicationContext)
            }
            M_EMPTY -> {
                Store.showEmpty = !Store.showEmpty
                Store.savePrefs(applicationContext)
                refresh()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showInfo() {
        val f = Store.ain
        val units = if (f != null) Store.units().size else 0
        AlertDialog.Builder(this)
            .setTitle("Informações do .ain")
            .setMessage(
                if (f == null) "Nenhum .ain aberto."
                else "Projeto: ${Store.projectId}\n${f.describe()}\nTrechos (falas contínuas): $units"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showProjects() {
        val ids = Store.listProjects(applicationContext)
        if (ids.isEmpty()) {
            toast("Nenhum projeto ainda. Abra um .ain pelo menu.")
            return
        }
        val labels = ids.map { if (it == Store.projectId) "$it (atual)" else it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Projetos (um por jogo)")
            .setItems(labels) { _, which ->
                val id = ids[which]
                AlertDialog.Builder(this)
                    .setTitle(id)
                    .setPositiveButton("Abrir") { _, _ ->
                        status.text = "Abrindo..."
                        Thread {
                            Store.persist(applicationContext)
                            Store.switchProject(applicationContext, id)
                            runOnUiThread { refresh() }
                        }.start()
                    }
                    .setNegativeButton("Apagar") { _, _ ->
                        AlertDialog.Builder(this)
                            .setTitle("Apagar \"$id\"?")
                            .setMessage("Apaga as traduções desse projeto. Não dá para desfazer.")
                            .setPositiveButton("Apagar") { _, _ ->
                                Store.deleteProject(applicationContext, id)
                                refresh()
                                toast("Projeto apagado")
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                    .setNeutralButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun askLimit() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setText(Store.globalLimit.toString())
        et.setSelection(et.text.length)
        val box = LinearLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Limite de caracteres por linha")
            .setMessage("Vale para as falas que não têm limite próprio. Use 0 para desligar.")
            .setView(box)
            .setPositiveButton("OK") { _, _ ->
                Store.globalLimit = et.text.toString().trim().toIntOrNull() ?: 0
                Store.savePrefs(applicationContext)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- modo ao vivo ----------

    private fun ocrModels() {
        if (Paddle.isReady(this)) {
            AlertDialog.Builder(this)
                .setTitle("OCR (PaddleOCR)")
                .setMessage("Modelos instalados (${Paddle.sizeMb(this)} MB). O OCR funciona sem internet.")
                .setPositiveButton("Fechar", null)
                .setNegativeButton("Apagar modelos") { _, _ ->
                    Paddle.delete(this)
                    toast("Modelos apagados")
                }
                .show()
        } else {
            downloadOcr(null)
        }
    }

    private fun downloadOcr(then: (() -> Unit)?) {
        val tv = TextView(this)
        tv.text = "Preparando..."
        tv.setPadding(dp(24), dp(16), dp(24), dp(8))
        val dlg = AlertDialog.Builder(this)
            .setTitle("Baixando modelos do OCR")
            .setView(tv)
            .setCancelable(false)
            .show()
        Thread {
            try {
                Paddle.download(applicationContext) { msg -> runOnUiThread { tv.text = msg } }
                runOnUiThread {
                    dlg.dismiss()
                    toast("Modelos instalados. O OCR agora funciona sem internet.")
                    then?.invoke()
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    dlg.dismiss()
                    toast("Erro ao baixar os modelos: ${e.message}")
                }
            }
        }.start()
    }

    private fun startLive() {
        if (!need()) return
        if (!Paddle.isReady(this)) {
            AlertDialog.Builder(this)
                .setTitle("Modelos do OCR")
                .setMessage("O modo ao vivo usa o PaddleOCR, que baixa os modelos uma vez (cerca de 12 MB). Depois funciona sem internet. Baixar agora?")
                .setPositiveButton("Baixar") { _, _ -> downloadOcr { startLive() } }
                .setNegativeButton("Agora não", null)
                .show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Ative \"Aparecer sobre outros apps\" para o Ain Tradutor e escolha Modo ao vivo de novo")
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
                var name = ""
                try {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) name = c.getString(0) ?: ""
                    }
                } catch (e: Throwable) {
                }
                val data = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val resumed = Store.load(applicationContext, data, name)
                Store.openedName = name
                runOnUiThread {
                    refresh()
                    toast(
                        if (resumed > 0) "Projeto \"${Store.projectId}\" retomado: $resumed falas já traduzidas"
                        else "Projeto \"${Store.projectId}\" aberto. ${Store.ain?.describe() ?: ""}"
                    )
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

    // ---------- salvar direto na pasta do jogo ----------

    private fun saveToGame() {
        if (!need()) return
        val dir = Store.gameDir
        if (dir == null || Store.gameName.isBlank()) {
            setupGame()
            return
        }
        doSaveToGame(Uri.parse(dir), Store.gameName)
    }

    private fun setupGame() {
        toast("Escolha a pasta onde está o jogo (a que contém o arquivo .ain)")
        pickGameDir.launch(null)
    }

    private fun onGameDirPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Throwable) {
        }
        Store.gameDir = uri.toString()
        Store.savePrefs(applicationContext)
        askGameName()
    }

    private fun askGameName() {
        val et = EditText(this)
        et.setText(
            if (Store.gameName.isNotBlank()) Store.gameName
            else if (Store.openedName.isNotBlank()) Store.openedName
            else "jogo.ain"
        )
        et.setSelection(et.text.length)
        val box = LinearLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setTitle("Nome do arquivo na pasta do jogo")
            .setMessage("Use o nome que o jogo lê (ex.: Rance01.ain). Se o arquivo já existir, o app guarda uma cópia (.bak) na primeira vez antes de substituir.")
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                val n = et.text.toString().trim()
                val d = Store.gameDir
                if (n.isNotEmpty() && d != null) {
                    Store.gameName = n
                    Store.savePrefs(applicationContext)
                    doSaveToGame(Uri.parse(d), n)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doSaveToGame(treeUri: Uri, name: String) {
        val f = Store.ain ?: return
        status.text = "Salvando na pasta do jogo..."
        val snapshot = Store.trans.copyOf()
        val strip = Store.stripAccents
        Thread {
            try {
                val dir = DocumentFile.fromTreeUri(this, treeUri) ?: throw IllegalStateException("pasta inválida")
                val bytes = f.build(snapshot, strip)
                val existing = dir.findFile(name)
                val target: DocumentFile
                var backedUp = false
                if (existing != null) {
                    if (dir.findFile("$name.bak") == null) {
                        val bak = dir.createFile("application/octet-stream", "$name.bak")
                        if (bak != null) {
                            contentResolver.openInputStream(existing.uri)!!.use { ins ->
                                contentResolver.openOutputStream(bak.uri, "wt")!!.use { out -> ins.copyTo(out) }
                            }
                            backedUp = true
                        }
                    }
                    target = existing
                } else {
                    target = dir.createFile("application/octet-stream", name)
                        ?: throw IllegalStateException("não consegui criar o arquivo")
                }
                contentResolver.openOutputStream(target.uri, "wt")!!.use { it.write(bytes) }
                runOnUiThread {
                    refresh()
                    toast("Salvo em $name (${bytes.size / 1024} KB)" + if (backedUp) ". Cópia do original: $name.bak" else "")
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    refresh()
                    toast("Erro ao salvar na pasta do jogo: ${e.message}. Use \"Trocar pasta/nome do jogo\" e escolha a pasta de novo.")
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
        val only = onlyUntranslated
        val showEmpty = Store.showEmpty
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
        val has = Store.ain != null
        val total = originals.count { it.isNotEmpty() }
        empty.visibility = if (has) View.GONE else View.VISIBLE
        rv.visibility = if (has) View.VISIBLE else View.GONE
        fab.visibility = if (has) View.VISIBLE else View.GONE
        supportActionBar?.subtitle = if (has) Store.projectId else null
        status.text = if (has) "$done de $total traduzidas • ${shown.size} na lista" else "Nenhum jogo aberto"
        progress.progress = if (total == 0) 0 else (done * 1000L / total).toInt()
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

        val cbFlag = CheckBox(this)
        cbFlag.text = "Marcar para revisar"
        cbFlag.isChecked = Store.flags.getOrElse(i) { false }
        box.addView(cbFlag)

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
                if (i in Store.flags.indices) Store.flags[i] = cbFlag.isChecked
                Store.persistAsync(applicationContext)
                refresh()
            }
            .setNeutralButton("Limpar") { _, _ ->
                Store.clear(i)
                Store.persistAsync(applicationContext)
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private class VH(
        v: View,
        val strip: View,
        val a: TextView,
        val b: TextView,
        val draft: TextView,
        val flag: TextView,
        val count: TextView
    ) : RecyclerView.ViewHolder(v)

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
            col.setPadding(dp(12), dp(10), dp(12), dp(10))
            val a = TextView(ctx)
            a.textSize = 13f
            a.setTextColor(Ui.color(ctx, R.color.text2))
            val b = TextView(ctx)
            b.textSize = 16f
            b.setPadding(0, dp(4), 0, 0)
            val meta = LinearLayout(ctx)
            meta.orientation = LinearLayout.HORIZONTAL
            meta.gravity = Gravity.CENTER_VERTICAL
            val draft = Ui.chip(ctx, "rascunho", Ui.color(ctx, R.color.warn_bg), Ui.color(ctx, R.color.warn))
            val flag = Ui.chip(ctx, "⚑ revisar", Ui.color(ctx, R.color.info_bg), Ui.color(ctx, R.color.info))
            val count = Ui.chip(ctx, "", Ui.color(ctx, R.color.tonal), Ui.color(ctx, R.color.text2))
            meta.addView(draft, Ui.lp(ctx, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 6, 0))
            meta.addView(flag, Ui.lp(ctx, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 6, 0))
            meta.addView(count)
            col.addView(a)
            col.addView(b)
            col.addView(meta, Ui.lp(ctx, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 6, 0, 0))
            card.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return VH(card, strip, a, b, draft, flag, count)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val i = shown[position]
            val ctx = holder.itemView.context
            val t = Store.trans[i]
            val lim = Store.limitOf(i)
            val over = t != null && lim > 0 && t.length > lim
            val isDraft = t != null && Store.drafts[i]
            val isFlag = Store.flags[i]

            holder.a.text = "#$i  ${Store.originals[i]}"
            if (t == null) {
                holder.b.text = "Toque para traduzir"
                holder.b.setTextColor(Ui.color(ctx, R.color.text2))
            } else {
                holder.b.text = t
                holder.b.setTextColor(Ui.color(ctx, R.color.text))
            }
            holder.strip.setBackgroundColor(
                Ui.color(
                    ctx,
                    if (t == null) R.color.neutral
                    else if (over) R.color.bad
                    else if (isDraft) R.color.warn
                    else R.color.good
                )
            )
            holder.draft.visibility = if (isDraft) View.VISIBLE else View.GONE
            holder.flag.visibility = if (isFlag) View.VISIBLE else View.GONE
            if (t != null && lim > 0) {
                holder.count.visibility = View.VISIBLE
                holder.count.text = "${t.length}/$lim"
                if (over) Ui.setChip(holder.count, Ui.color(ctx, R.color.bad_bg), Ui.color(ctx, R.color.bad))
                else Ui.setChip(holder.count, Ui.color(ctx, R.color.tonal), Ui.color(ctx, R.color.text2))
            } else {
                holder.count.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { edit(i) }
            holder.itemView.setOnLongClickListener {
                Store.flags[i] = !Store.flags[i]
                Store.persistAsync(applicationContext)
                notifyItemChanged(position)
                toast(if (Store.flags[i]) "Marcada para revisar" else "Marca removida")
                true
            }
        }
    }
}
