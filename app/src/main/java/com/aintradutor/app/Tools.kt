package com.aintradutor.app

/** Falas repetidas: copia a tradução de um trecho para os trechos idênticos ainda sem tradução. */
object Dup {

    class Stats(val groups: Int, val fill: Int, val conflicts: Int)

    private fun run(minLen: Int, apply: Boolean): Stats {
        val orig = Store.originals
        val trans = Store.trans
        val groups = HashMap<String, ArrayList<IntArray>>()
        for (u in Wrap.units(orig)) {
            var len = 0
            val sb = StringBuilder()
            for (i in u) {
                len += orig[i].trim().length
                sb.append(orig[i]).append('\u0001')
            }
            if (len < minLen) continue
            groups.getOrPut(sb.toString()) { ArrayList() }.add(u)
        }
        var g = 0
        var fill = 0
        var conflicts = 0
        for (list in groups.values) {
            if (list.size < 2) continue
            g++
            var src: List<String>? = null
            var conflict = false
            for (u in list) {
                if (u.all { trans[it] != null }) {
                    val t = u.map { trans[it]!! }
                    val s = src
                    if (s == null) src = t else if (s != t) conflict = true
                }
            }
            if (conflict) {
                conflicts++
                continue
            }
            val s = src ?: continue
            for (u in list) {
                if (u.all { trans[it] == null }) {
                    fill += u.size
                    if (apply) for (j in u.indices) trans[u[j]] = s[j]
                }
            }
        }
        return Stats(g, fill, conflicts)
    }

    fun analyze(minLen: Int): Stats = run(minLen, false)

    /** Preenche as repetidas (só trechos sem nenhuma tradução). Guarda cópia para desfazer. */
    fun apply(minLen: Int): Stats {
        val st = run(minLen, false)
        if (st.fill == 0) return st
        Store.undo = Store.trans.copyOf()
        run(minLen, true)
        return st
    }
}

/** Localizar e substituir dentro das traduções. */
object Replace {

    class Stats(val occurrences: Int, val lines: Int, val examples: List<Int>)

    fun regex(find: String, matchCase: Boolean, whole: Boolean): Regex {
        val esc = Regex.escape(find)
        val p = if (whole) "(?<![\\p{L}\\p{N}])" + esc + "(?![\\p{L}\\p{N}])" else esc
        return if (matchCase) Regex(p) else Regex(p, RegexOption.IGNORE_CASE)
    }

    fun count(rx: Regex): Stats {
        var occ = 0
        var lines = 0
        val ex = ArrayList<Int>()
        for (i in Store.trans.indices) {
            val t = Store.trans[i] ?: continue
            val n = rx.findAll(t).count()
            if (n > 0) {
                occ += n
                lines++
                if (ex.size < 5) ex.add(i)
            }
        }
        return Stats(occ, lines, ex)
    }

    /** Substitui tudo. Devolve quantas falas mudaram. Guarda cópia para desfazer. */
    fun apply(rx: Regex, repl: String): Int {
        Store.undo = Store.trans.copyOf()
        val r = Regex.escapeReplacement(repl)
        var changed = 0
        for (i in Store.trans.indices) {
            val t = Store.trans[i] ?: continue
            val n = rx.replace(t, r)
            if (n != t) {
                Store.setTrans(i, n)
                changed++
            }
        }
        return changed
    }
}
