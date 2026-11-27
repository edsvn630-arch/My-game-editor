package com.aintradutor.app

import android.content.Context
import java.io.File

/** Estado compartilhado entre as telas, o modo ao vivo e o serviço. */
object Store {
    var ain: AinFile? = null
    var originals: Array<String> = emptyArray()
    var trans: Array<String?> = arrayOfNulls(0)
    var limits: IntArray = IntArray(0) // limite individual por fala (0 = usa o limite geral)
    var drafts: BooleanArray = BooleanArray(0) // true = tradução automática ainda não revisada
    var globalLimit: Int = 50
    var undo: Array<String?>? = null // cópia das traduções antes da última ação em lote
    var showEmpty: Boolean = false // mostrar na lista as falas vazias do .ain
    var stripAccents: Boolean = true // remover acentos e ç ao salvar o .ain
    var gameDir: String? = null // pasta do jogo (Uri da árvore do Android)
    var gameName: String = "" // nome do arquivo .ain dentro da pasta do jogo
    var openedName: String = "" // nome do .ain que foi aberto

    private var index: HashMap<String, ArrayList<Int>>? = null
    private var keys: Array<String> = emptyArray()
    private var unitsCache: List<IntArray>? = null
    private var unitIdx: IntArray = IntArray(0)

    private fun apply(f: AinFile) {
        ain = f
        originals = Array(f.originals.size) { String(f.originals[it], AinFile.CP932) }
        trans = arrayOfNulls(originals.size)
        limits = IntArray(originals.size)
        drafts = BooleanArray(originals.size)
        undo = null
        index = null
        unitsCache = null
    }

    /** Abre um .ain novo (zera as traduções) e guarda uma cópia no app. */
    fun load(ctx: Context, data: ByteArray) {
        apply(AinFile.parse(data))
        File(ctx.filesDir, "current.ain").writeBytes(data)
        persist(ctx)
    }

    /** Recupera o último .ain e as traduções caso o app tenha sido fechado pelo sistema. */
    fun restore(ctx: Context): Boolean {
        if (ain != null) return true
        val f = File(ctx.filesDir, "current.ain")
        if (!f.exists()) return false
        return try {
            apply(AinFile.parse(f.readBytes()))
            val t = File(ctx.filesDir, "trans.csv")
            if (t.exists()) {
                for (r in Csv.parse(t.readText())) {
                    val id = r.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                    val s = r.getOrNull(1) ?: continue
                    if (id in trans.indices && s.isNotEmpty()) trans[id] = s
                }
            }
            val lf = File(ctx.filesDir, "limits.csv")
            if (lf.exists()) {
                for (r in Csv.parse(lf.readText())) {
                    val id = r.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                    val v = r.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                    if (id in limits.indices && v > 0) limits[id] = v
                }
            }
            val df = File(ctx.filesDir, "drafts.csv")
            if (df.exists()) {
                for (line in df.readLines()) {
                    val id = line.trim().toIntOrNull() ?: continue
                    if (id in drafts.indices) drafts[id] = true
                }
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    @Synchronized
    fun persist(ctx: Context) {
        val sb = StringBuilder("ID,Traducao\n")
        for (i in trans.indices) {
            val t = trans[i]
            if (t != null) sb.append(i).append(',').append(Csv.escape(t)).append('\n')
        }
        File(ctx.filesDir, "trans.csv").writeText(sb.toString())
        val lb = StringBuilder("ID,Limite\n")
        for (i in limits.indices) {
            if (limits[i] > 0) lb.append(i).append(',').append(limits[i]).append('\n')
        }
        File(ctx.filesDir, "limits.csv").writeText(lb.toString())
        val db = StringBuilder()
        for (i in drafts.indices) if (drafts[i] && trans[i] != null) db.append(i).append('\n')
        File(ctx.filesDir, "drafts.csv").writeText(db.toString())
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
        val p = ctx.getSharedPreferences("ain", Context.MODE_PRIVATE)
        globalLimit = p.getInt("limit", 50)
        stripAccents = p.getBoolean("strip", true)
        showEmpty = p.getBoolean("empty", false)
        gameDir = p.getString("gdir", null)
        gameName = p.getString("gname", "") ?: ""
    }

    fun savePrefs(ctx: Context) {
        ctx.getSharedPreferences("ain", Context.MODE_PRIVATE).edit()
            .putInt("limit", globalLimit)
            .putBoolean("strip", stripAccents)
            .putBoolean("empty", showEmpty)
            .putString("gdir", gameDir)
            .putString("gname", gameName)
            .apply()
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
