package com.aintradutor.app

import android.content.Context
import java.io.File

/** Estado compartilhado entre a tela principal, o modo ao vivo e o serviço. */
object Store {
    var ain: AinFile? = null
    var originals: Array<String> = emptyArray()
    var trans: Array<String?> = arrayOfNulls(0)
    var limits: IntArray = IntArray(0) // limite individual por fala (0 = usa o limite geral)
    var globalLimit: Int = 50
    var undo: Array<String?>? = null // cópia das traduções antes do último reajuste
    var showEmpty: Boolean = false // mostrar na lista as falas vazias do .ain
    var stripAccents: Boolean = true // remover acentos e ç ao salvar o .ain

    private var index: HashMap<String, ArrayList<Int>>? = null
    private var keys: Array<String> = emptyArray()

    private fun apply(f: AinFile) {
        ain = f
        originals = Array(f.originals.size) { String(f.originals[it], AinFile.CP932) }
        trans = arrayOfNulls(originals.size)
        limits = IntArray(originals.size)
        undo = null
        index = null
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

    /** Limite de caracteres desta fala: o individual, se existir, senão o geral. */
    fun limitOf(i: Int): Int = if (i in limits.indices && limits[i] > 0) limits[i] else globalLimit

    fun setLimit(i: Int, v: Int) {
        if (i in limits.indices) limits[i] = if (v > 0) v else 0
    }

    fun loadPrefs(ctx: Context) {
        val p = ctx.getSharedPreferences("ain", Context.MODE_PRIVATE)
        globalLimit = p.getInt("limit", 50)
        stripAccents = p.getBoolean("strip", true)
        showEmpty = p.getBoolean("empty", false)
    }

    fun savePrefs(ctx: Context) {
        ctx.getSharedPreferences("ain", Context.MODE_PRIVATE).edit()
            .putInt("limit", globalLimit)
            .putBoolean("strip", stripAccents)
            .putBoolean("empty", showEmpty)
            .apply()
    }

    /** Guarda uma tradução, devolvendo aspas/crase/espaço do início e aspas do fim se faltarem. */
    fun setTrans(i: Int, text: String) {
        if (i !in trans.indices) return
        trans[i] = if (text.isBlank() || text == originals[i]) null else fixMarkers(originals[i], text)
    }

    fun fixMarkers(o: String, t: String): String {
        var r = t
        if (o.isNotEmpty() && (o[0] == '`' || o[0] == '"' || o[0] == ' ') && !r.startsWith(o[0])) {
            r = o[0] + r
        }
        if (o.endsWith("\"") && !r.endsWith("\"")) r += "\""
        return r
    }

    // ---------- achar a fala que está na tela ----------

    fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
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

    private fun ensureIndex() {
        if (index != null) return
        val m = HashMap<String, ArrayList<Int>>()
        for (i in originals.indices) {
            val n = norm(originals[i])
            if (n.length >= 3) m.getOrPut(n) { ArrayList() }.add(i)
        }
        index = m
        keys = m.keys.toTypedArray()
    }

    /** Recebe as linhas lidas pelo OCR e devolve os números das falas encontradas. */
    fun match(lines: List<String>): IntArray {
        ensureIndex()
        val idx = index ?: return IntArray(0)
        val groups = ArrayList<IntArray>()
        for (ln in lines) {
            val n = norm(ln)
            if (n.length < 4) continue
            val ex = idx[n]
            val g = if (ex != null) ex.toIntArray() else fuzzy(n, idx)
            if (g != null && g.size <= 30) groups.add(g)
        }
        if (groups.isEmpty()) return IntArray(0)

        var best = -1
        var bestScore = -1
        for (g in groups) {
            for (c in g) {
                var s = 0
                for (h in groups) if (h.any { Math.abs(it - c) <= 8 }) s++
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
