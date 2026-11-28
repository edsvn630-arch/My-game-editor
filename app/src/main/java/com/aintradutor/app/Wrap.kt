package com.aintradutor.app

/**
 * Reajuste de quebras de linha.
 *
 * Cada linha do diálogo é uma entrada separada do .ain, e o jogo só mostra o que cabe
 * nelas. Um "trecho" é o conjunto de entradas que formam um mesmo texto contínuo
 * (a fala e suas linhas de continuação). O reajuste junta a tradução do trecho e
 * redistribui as palavras nas MESMAS entradas, respeitando o limite de cada uma.
 * O número de entradas nunca muda.
 */
object Wrap {

    class Result(val lines: List<String>?, val reason: String)

    private const val END_MARKS = ".!?…\"”)]~♪ー"

    private fun isNarrationContinuation(prev: String, s: String): Boolean {
        val p = prev.trimEnd()
        if (p.isEmpty()) return false
        val c = s[0]
        return c.isLowerCase() && c.code < 128 && END_MARKS.indexOf(p.last()) < 0
    }

    /** Agrupa as entradas (pelo texto original) em trechos contínuos. */
    fun units(orig: Array<String>): List<IntArray> {
        val out = ArrayList<IntArray>()
        var cur = ArrayList<Int>()
        var prev = -1
        for (i in orig.indices) {
            val s = orig[i]
            if (s.isEmpty()) continue
            val c = s[0]
            val cont = cur.isNotEmpty() && (c == ' ' || c == '`' || isNarrationContinuation(orig[prev], s))
            if (cont) {
                cur.add(i)
            } else {
                if (cur.isNotEmpty()) out.add(cur.toIntArray())
                cur = ArrayList()
                cur.add(i)
            }
            prev = i
        }
        if (cur.isNotEmpty()) out.add(cur.toIntArray())
        return out
    }

    private fun prefsOf(unit: IntArray, orig: Array<String>): Array<String> =
        Array(unit.size) { j ->
            val c = orig[unit[j]][0]
            if (j > 0 && (c == ' ' || c == '`')) c.toString() else ""
        }

    /**
     * Redistribui a tradução do trecho nas suas entradas.
     * Devolve as novas linhas, ou null com o motivo (incompleta / pouco texto / não cabe).
     */
    fun rewrap(unit: IntArray, orig: Array<String>, trans: Array<String?>, capOf: (Int) -> Int): Result {
        val prefs = prefsOf(unit, orig)
        val sb = StringBuilder()
        for (j in unit.indices) {
            val t = trans[unit[j]] ?: return Result(null, "incompleta")
            val body = if (prefs[j].isNotEmpty() && t.startsWith(prefs[j])) t.substring(1) else t
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(body)
        }
        return distribute(unit, orig, sb.toString(), capOf, false)
    }

    /**
     * Divide um texto nas linhas do trecho, minimizando a linha mais cheia (tamanho / limite).
     * Com force = true devolve o melhor resultado mesmo que alguma linha passe do limite.
     */
    fun distribute(unit: IntArray, orig: Array<String>, text: String, capOf: (Int) -> Int, force: Boolean, spaceless: Boolean = false): Result {
        val k = unit.size
        val prefs = prefsOf(unit, orig)
        val words = if (spaceless) {
            text.filter { !it.isWhitespace() }.map { it.toString() }
        } else {
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
        val sepLen = if (spaceless) 0 else 1
        val sep = if (spaceless) "" else " "
        val n = words.size
        if (n < k) return Result(null, "pouco texto")

        val caps = IntArray(k) { j ->
            val c = capOf(unit[j])
            if (c <= 0) 100000 else maxOf(1, c - prefs[j].length)
        }
        val pre = IntArray(n + 1)
        for (i in 0 until n) pre[i + 1] = pre[i] + words[i].length
        fun len(a: Int, e: Int) = pre[e] - pre[a] + (e - a - 1) * sepLen

        val inf = Double.MAX_VALUE
        val dp = Array(k) { DoubleArray(n + 1) { inf } }
        val from = Array(k) { IntArray(n + 1) { -1 } }
        for (e in 1..n) dp[0][e] = len(0, e).toDouble() / caps[0]
        for (j in 1 until k) {
            for (e in (j + 1)..n) {
                for (a in j until e) {
                    val p = dp[j - 1][a]
                    if (p == inf) continue
                    val r = maxOf(p, len(a, e).toDouble() / caps[j])
                    if (r < dp[j][e]) {
                        dp[j][e] = r
                        from[j][e] = a
                    }
                }
            }
        }
        val best = dp[k - 1][n]
        if (best == inf) return Result(null, "pouco texto")
        if (best > 1.0 && !force) {
            val total = len(0, n) - (k - 1) * sepLen
            return Result(null, "não cabe (texto $total / cabem ${caps.sum()})")
        }
        val cuts = IntArray(k + 1)
        cuts[k] = n
        var end = n
        for (j in k - 1 downTo 1) {
            val a = from[j][end]
            cuts[j] = a
            end = a
        }
        cuts[0] = 0
        val lines = ArrayList<String>()
        for (j in 0 until k) {
            lines.add(prefs[j] + words.subList(cuts[j], cuts[j + 1]).joinToString(sep))
        }
        return Result(lines, "")
    }

    /** Reajusta todos os trechos que têm alguma linha acima do limite. Devolve [reajustados, não couberam]. */
    fun rewrapAll(): IntArray {
        val orig = Store.originals
        val trans = Store.trans
        Store.undo = trans.copyOf()
        var ok = 0
        var fail = 0
        for (u in units(orig)) {
            var over = false
            for (i in u) {
                val t = trans[i]
                if (t != null && t.length > Store.limitOf(i)) {
                    over = true
                    break
                }
            }
            if (!over) continue
            val r = rewrap(u, orig, trans) { Store.limitOf(it) }
            val lines = r.lines
            if (lines != null) {
                for (j in u.indices) trans[u[j]] = lines[j]
                ok++
            } else {
                fail++
            }
        }
        return intArrayOf(ok, fail)
    }
}
