package com.aintradutor.app

/** Verificação das traduções: lista o que pode dar problema no jogo. */
object Check {
    const val LIMITE = 1
    const val NAO_CABE = 2
    const val MARCADOR = 3
    const val CHARS = 4
    const val INCOMPLETA = 5

    val names = arrayOf(
        "Todos",
        "Passa do limite",
        "Não cabe nem reajustando",
        "Marcador faltando",
        "Caracteres que viram ?",
        "Fala incompleta"
    )

    class Problem(val idx: Int, val kind: Int, val info: String)

    fun scan(strip: Boolean): List<Problem> {
        val orig = Store.originals
        val trans = Store.trans
        val out = ArrayList<Problem>()
        val enc = AinFile.CP932.newEncoder()

        for (i in trans.indices) {
            val t = trans[i] ?: continue
            val lim = Store.limitOf(i)
            if (lim > 0 && t.length > lim) out.add(Problem(i, LIMITE, "${t.length}/$lim caracteres"))

            val o = orig[i]
            if (o.isNotEmpty()) {
                val c = o[0]
                if ((c == ' ' || c == '`') && !t.startsWith(c)) {
                    out.add(Problem(i, MARCADOR, if (c == ' ') "falta espaço no começo" else "falta crase no começo"))
                }
                if (o.endsWith("\"") && !t.endsWith("\"")) out.add(Problem(i, MARCADOR, "falta aspas no fim"))
            }

            val shownText = if (strip) AinFile.stripAccents(t) else t
            val bad = LinkedHashSet<Char>()
            for (ch in shownText) if (!enc.canEncode(ch)) bad.add(ch)
            if (bad.isNotEmpty()) out.add(Problem(i, CHARS, "caracteres: ${bad.joinToString(" ")}"))
        }

        for (u in Wrap.units(orig)) {
            var have = 0
            var firstMissing = -1
            var over = false
            for (i in u) {
                val t = trans[i]
                if (t != null) {
                    have++
                    if (t.length > Store.limitOf(i)) over = true
                } else if (firstMissing < 0) {
                    firstMissing = i
                }
            }
            if (have > 0 && have < u.size) {
                out.add(Problem(firstMissing, INCOMPLETA, "faltam ${u.size - have} de ${u.size} falas deste trecho"))
            } else if (have == u.size && over) {
                val r = Wrap.rewrap(u, orig, trans) { Store.limitOf(it) }
                if (r.lines == null && r.reason.startsWith("não cabe")) {
                    out.add(Problem(u[0], NAO_CABE, r.reason))
                }
            }
        }
        out.sortBy { it.idx }
        return out
    }
}
