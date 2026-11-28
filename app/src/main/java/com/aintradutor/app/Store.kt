package com.aintradutor.app

import android.content.Context
import java.io.File

/** Estado compartilhado entre as telas, o modo ao vivo e o serviço. Cada jogo é um "projeto". */
object Store {
    var ain: AinFile? = null
    var originals: Array<String> = emptyArray()
    var trans: Array<String?> = arrayOfNulls(0)
    var limits: IntArray = IntArray(0) // limite individual por fala (0 = usa o limite geral)
    var drafts: BooleanArray = BooleanArray(0) // true = tradução automática ainda não revisada
    var flags: BooleanArray = BooleanArray(0) // true = marcada por você para revisar
    var sOriginals: Array<String> = emptyArray() // textos do sistema (STR0)
    var sTrans: Array<String?> = arrayOfNulls(0)
    var globalLimit: Int = 50
    var undo: Array<String?>? = null // cópia das traduções antes da última ação em lote
    var showEmpty: Boolean = false // mostrar na lista as falas vazias do .ain
    var stripAccents: Boolean = true // remover acentos e ç ao salvar o .ain
    var gameDir: String? = null // pasta do jogo do projeto atual (Uri da árvore do Android)
    var gameName: String = "" // nome do arquivo .ain dentro da pasta do jogo
    var openedName: String = "" // nome do .ain que foi aberto
    var projectId: String = "" // projeto (jogo) atual
    var srcLang: String = "en" // idioma de origem da tradução automática
    var tgtLang: String = "pt" // idioma de destino

    private var keys: Array<String> = emptyArray()
    private var unitsCache: List<IntArray>? = null
    private var unitIdx: IntArray = IntArray(0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ain", Context.MODE_PRIVATE)

    private fun apply(f: AinFile) {
        ain = f
        originals = Array(f.originals.size) { String(f.originals[it], AinFile.CP932) }
        trans = arrayOfNulls(originals.size)
        limits = IntArray(originals.size)
        drafts = BooleanArray(originals.size)
        flags = BooleanArray(originals.size)
        sOriginals = Array(f.strOriginals.size) { String(f.strOriginals[it], AinFile.CP932) }
        sTrans = arrayOfNulls(sOriginals.size)
        undo = null
        unitsCache = null
    }

    // ---------- projetos (um por jogo) ----------

    private fun projectsDir(ctx: Context) = File(ctx.filesDir, "projects")
    private fun dirOf(ctx: Context, id: String) = File(projectsDir(ctx), id)

    fun sanitize(name: String): String {
        val base = name.substringBeforeLast('.', name).trim()
        val s = base.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_')
        return if (s.isEmpty()) "jogo" else s.take(40)
    }

    fun listProjects(ctx: Context): List<String> =
        (projectsDir(ctx).listFiles() ?: emptyArray())
            .filter { File(it, "current.ain").exists() }
            .map { it.name }
            .sorted()

    /** Versões antigas guardavam um único jogo na raiz: vira o projeto "jogo". */
    private fun migrateLegacy(ctx: Context) {
        val old = File(ctx.filesDir, "current.ain")
        if (!old.exists()) return
        val d = dirOf(ctx, "jogo")
        d.mkdirs()
        for (n in listOf("current.ain", "trans.csv", "limits.csv", "drafts.csv")) {
            val f = File(ctx.filesDir, n)
            if (f.exists()) f.renameTo(File(d, n))
        }
        val p = prefs(ctx)
        p.edit()
            .putString("project", "jogo")
            .putString("gdir_jogo", p.getString("gdir", null))
            .putString("gname_jogo", p.getString("gname", ""))
            .apply()
    }

    private fun readSidecars(ctx: Context): Int {
        val d = dirOf(ctx, projectId)
        val meta = File(d, "meta.txt")
        if (meta.exists() && meta.readText().trim().toIntOrNull() != originals.size) return 0
        var count = 0
        val t = File(d, "trans.csv")
        if (t.exists()) {
            for (r in Csv.parse(t.readText())) {
                val id = r.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val s = r.getOrNull(1) ?: continue
                if (id in trans.indices && s.isNotEmpty()) {
                    trans[id] = s
                    count++
                }
            }
        }
        val lf = File(d, "limits.csv")
        if (lf.exists()) {
            for (r in Csv.parse(lf.readText())) {
                val id = r.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val v = r.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                if (id in limits.indices && v > 0) limits[id] = v
            }
        }
        val df = File(d, "drafts.csv")
        if (df.exists()) {
            for (line in df.readLines()) {
                val id = line.trim().toIntOrNull() ?: continue
                if (id in drafts.indices) drafts[id] = true
            }
        }
        val ff = File(d, "flags.csv")
        if (ff.exists()) {
            for (line in ff.readLines()) {
                val id = line.trim().toIntOrNull() ?: continue
                if (id in flags.indices) flags[id] = true
            }
        }
        val sf = File(d, "sys_trans.csv")
        if (sf.exists()) {
            for (r in Csv.parse(sf.readText())) {
                val id = r.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val s = r.getOrNull(1) ?: continue
                if (id in sTrans.indices && s.isNotEmpty()) sTrans[id] = s
            }
        }
        return count
    }

    private fun loadGamePrefs(ctx: Context) {
        val p = prefs(ctx)
        gameDir = p.getString("gdir_$projectId", null)
        gameName = p.getString("gname_$projectId", "") ?: ""
    }

    /**
     * Abre um .ain como projeto (o nome do arquivo vira o nome do projeto).
     * Se esse projeto já existia com o mesmo número de falas, as traduções são retomadas.
     * Devolve quantas traduções foram retomadas.
     */
    fun load(ctx: Context, data: ByteArray, name: String): Int {
        val f = AinFile.parse(data)
        apply(f)
        projectId = sanitize(name)
        val d = dirOf(ctx, projectId)
        d.mkdirs()
        File(d, "current.ain").writeBytes(data)
        val resumed = readSidecars(ctx)
        loadGamePrefs(ctx)
        persist(ctx)
        savePrefs(ctx)
        return resumed
    }

    /** Recupera o projeto atual (ou o primeiro que existir) caso o app tenha sido fechado pelo sistema. */
    fun restore(ctx: Context): Boolean {
        if (ain != null) return true
        migrateLegacy(ctx)
        if (projectId.isEmpty()) projectId = prefs(ctx).getString("project", "") ?: ""
        var d = dirOf(ctx, projectId)
        if (projectId.isEmpty() || !File(d, "current.ain").exists()) {
            val l = listProjects(ctx)
            if (l.isEmpty()) return false
            projectId = l[0]
            d = dirOf(ctx, projectId)
        }
        return try {
            apply(AinFile.parse(File(d, "current.ain").readBytes()))
            readSidecars(ctx)
            loadGamePrefs(ctx)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun switchProject(ctx: Context, id: String): Boolean {
        ain = null
        projectId = id
        val ok = restore(ctx)
        if (ok) savePrefs(ctx)
        return ok
    }

    fun deleteProject(ctx: Context, id: String) {
        dirOf(ctx, id).deleteRecursively()
        if (id == projectId) {
            ain = null
            originals = emptyArray()
            trans = arrayOfNulls(0)
            limits = IntArray(0)
            drafts = BooleanArray(0)
            flags = BooleanArray(0)
            sOriginals = emptyArray()
            sTrans = arrayOfNulls(0)
            unitsCache = null
            projectId = ""
            prefs(ctx).edit().remove("project").apply()
        }
    }

    @Synchronized
    fun persist(ctx: Context) {
        if (projectId.isEmpty() || ain == null) return
        val d = dirOf(ctx, projectId)
        d.mkdirs()
        val sb = StringBuilder("ID,Traducao\n")
        for (i in trans.indices) {
            val t = trans[i]
            if (t != null) sb.append(i).append(',').append(Csv.escape(t)).append('\n')
        }
        File(d, "trans.csv").writeText(sb.toString())
        val lb = StringBuilder("ID,Limite\n")
        for (i in limits.indices) {
            if (limits[i] > 0) lb.append(i).append(',').append(limits[i]).append('\n')
        }
        File(d, "limits.csv").writeText(lb.toString())
        val db = StringBuilder()
        for (i in drafts.indices) if (drafts[i] && trans[i] != null) db.append(i).append('\n')
        File(d, "drafts.csv").writeText(db.toString())
        val fb = StringBuilder()
        for (i in flags.indices) if (flags[i]) fb.append(i).append('\n')
        File(d, "flags.csv").writeText(fb.toString())
        val sb2 = StringBuilder("ID,Traducao\n")
        for (i in sTrans.indices) {
            val t = sTrans[i]
            if (t != null) sb2.append(i).append(',').append(Csv.escape(t)).append('\n')
        }
        File(d, "sys_trans.csv").writeText(sb2.toString())
        File(d, "meta.txt").writeText(originals.size.toString())
    }

    fun persistAsync(ctx: Context) {
        val c = ctx.applicationContext
        Thread {
            try {
                persist(c)
            } catch (e: Throwable) {
            }
        }.start()
    }

    // ---------- preferências ----------

    fun loadPrefs(ctx: Context) {
        migrateLegacy(ctx)
        val p = prefs(ctx)
        globalLimit = p.getInt("limit", 50)
        stripAccents = p.getBoolean("strip", true)
        showEmpty = p.getBoolean("empty", false)
        srcLang = p.getString("src", "en") ?: "en"
        tgtLang = p.getString("tgt", "pt") ?: "pt"
        if (projectId.isEmpty()) projectId = p.getString("project", "") ?: ""
        loadGamePrefs(ctx)
    }

    fun savePrefs(ctx: Context) {
        val e = prefs(ctx).edit()
            .putInt("limit", globalLimit)
            .putBoolean("strip", stripAccents)
            .putBoolean("empty", showEmpty)
            .putString("src", srcLang)
            .putString("tgt", tgtLang)
        if (projectId.isNotEmpty()) {
            e.putString("project", projectId)
                .putString("gdir_$projectId", gameDir)
                .putString("gname_$projectId", gameName)
        }
        e.apply()
    }

    // ---------- limites e traduções ----------

    /** Limite de caracteres desta fala: o individual, se existir, senão o geral. */
    fun limitOf(i: Int): Int = if (i in limits.indices && limits[i] > 0) limits[i] else globalLimit

    fun setLimit(i: Int, v: Int) {
        if (i in limits.indices) limits[i] = if (v > 0) v else 0
    }

    /** Guarda uma tradução digitada, devolvendo aspas/crase/espaço do início e aspas do fim se faltarem. */
    fun setTrans(i: Int, text: String) {
        if (i !in trans.indices) return
        trans[i] = if (text.isBlank() || text == originals[i]) null else fixMarkers(originals[i], text)
        drafts[i] = false // quem digitou já revisou
    }

    fun clear(i: Int) {
        if (i !in trans.indices) return
        trans[i] = null
        drafts[i] = false
    }

    /** Tradução de um texto do sistema (sem regras de aspas/crase). */
    fun setSys(i: Int, text: String) {
        if (i !in sTrans.indices) return
        sTrans[i] = if (text.isBlank() || text == sOriginals[i]) null else text
    }

    fun fixMarkers(o: String, t: String): String {
        var r = t
        if (o.isNotEmpty() && (o[0] == '`' || o[0] == '"' || o[0] == ' ') && !r.startsWith(o[0])) {
            r = o[0] + r
        }
        if (o.endsWith("\"") && !r.endsWith("\"")) r += "\""
        return r
    }

    // ---------- trechos (falas contínuas) ----------

    fun units(): List<IntArray> {
        val c = unitsCache
        if (c != null) return c
        val u = Wrap.units(originals)
        val idx = IntArray(originals.size) { -1 }
        for ((k, x) in u.withIndex()) for (i in x) idx[i] = k
        unitIdx = idx
        unitsCache = u
        return u
    }

    fun unitOf(i: Int): IntArray? {
        val u = units()
        val k = unitIdx.getOrNull(i) ?: return null
        return if (k >= 0) u[k] else null
    }

    // ---------- achar a fala que está na tela ----------

    /** Texto "comparável": sem acentos, pontuação e maiúsculas, e sem confusões típicas do OCR (l/1/I, 0/O). */
    fun norm(s: String): String {
        val d = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder(d.length)
        for (c in d) {
            if (c.isLetterOrDigit()) {
                sb.append(
                    when (c) {
                        'l', 'L', '1', 'I' -> 'i'
                        '0', 'O' -> 'o'
                        else -> c.lowercaseChar()
                    }
                )
            }
        }
        return sb.toString()
    }

    /**
     * Recebe as linhas lidas pelo OCR e devolve os números das falas encontradas.
     * Compara com o original E com a tradução de cada fala (o jogo pode estar mostrando qualquer um).
     * Linhas curtas (como o nome de quem fala) pesam pouco; linhas longas decidem a cena.
     */
    fun match(lines: List<String>): IntArray {
        val m = HashMap<String, ArrayList<Int>>()
        for (i in originals.indices) {
            val a = norm(originals[i])
            if (a.length >= 3) m.getOrPut(a) { ArrayList() }.add(i)
            val t = trans[i]
            if (t != null) {
                val b = norm(t)
                if (b.length >= 3 && b != a) m.getOrPut(b) { ArrayList() }.add(i)
            }
        }
        keys = m.keys.toTypedArray()

        for (minLen in intArrayOf(6, 4)) {
            val groups = ArrayList<IntArray>()
            val weights = ArrayList<Int>()
            for (ln in lines) {
                val n = norm(ln)
                if (n.length < minLen) continue
                val ex = m[n]
                val g = if (ex != null) ex.toIntArray() else fuzzy(n, m)
                if (g != null && g.size <= 30) {
                    groups.add(g)
                    weights.add(n.length)
                }
            }
            if (groups.isEmpty()) continue

            var best = -1
            var bestScore = -1
            for (gi in groups.indices) {
                for (c in groups[gi]) {
                    var s = 0
                    for (hi in groups.indices) {
                        if (groups[hi].any { Math.abs(it - c) <= 8 }) s += weights[hi]
                    }
                    if (s > bestScore) {
                        bestScore = s
                        best = c
                    }
                }
            }
            val out = java.util.TreeSet<Int>()
            for (g in groups) {
                val near = g.filter { Math.abs(it - best) <= 8 }
                if (near.isNotEmpty()) out.add(near.minByOrNull { Math.abs(it - best) }!!)
            }
            return out.toIntArray()
        }
        return IntArray(0)
    }

    private fun fuzzy(n: String, idx: HashMap<String, ArrayList<Int>>): IntArray? {
        val maxD = maxOf(1, (n.length * 0.2).toInt())
        var bestD = maxD + 1
        var bestK: String? = null
        for (k in keys) {
            if (bestD == 0) break
            if (Math.abs(k.length - n.length) > maxD) continue
            val d = lev(n, k, bestD - 1)
            if (d < bestD) {
                bestD = d
                bestK = k
            }
        }
        return bestK?.let { idx[it]?.toIntArray() }
    }

    private fun lev(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }
}
