package com.aintradutor.app

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tradução automática inglês → português pelo Google Tradutor (precisa de internet).
 * Traduz o trecho inteiro (a fala completa, não linha por linha) e distribui o resultado
 * nas linhas do trecho. O resultado fica marcado como "rascunho" até você revisar.
 * Todas as funções são bloqueantes: chame fora da thread principal.
 *
 * Usa o endereço público do Google Tradutor (sem chave). Ele não é uma API oficial:
 * se pedir muita coisa de uma vez o Google pode limitar por um tempo.
 */
object Auto {

    /** Testa a conexão com uma tradução pequena; lança erro com mensagem clara se falhar. */
    fun ensureReady() {
        try {
            request("hello")
        } catch (e: Exception) {
            throw IOException("sem conexão com o Google Tradutor (${e.message})")
        }
    }

    private fun parse(body: String): String {
        val arr = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val seg = arr.optJSONArray(i) ?: continue
            sb.append(seg.optString(0, ""))
        }
        return sb.toString()
    }

    private fun request(text: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=pt&dt=t&q=$q")
        var last: Exception? = null
        for (attempt in 0 until 3) {
            try {
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 15000
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                val code = c.responseCode
                if (code == 200) {
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    c.disconnect()
                    return parse(body)
                }
                c.disconnect()
                last = IOException("HTTP $code")
            } catch (e: IOException) {
                last = e
            }
            Thread.sleep(800L * (attempt + 1))
        }
        throw last ?: IOException("falha desconhecida")
    }

    /** Textos longos são divididos em pedaços para caber no endereço. */
    private fun translateText(text: String): String {
        if (text.length <= 800) return request(text)
        val parts = ArrayList<String>()
        val cur = StringBuilder()
        for (w in text.split(' ')) {
            if (cur.length + w.length + 1 > 800 && cur.isNotEmpty()) {
                parts.add(cur.toString())
                cur.setLength(0)
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) parts.add(cur.toString())
        return parts.joinToString(" ") { request(it) }
    }

    private fun sourceText(unit: IntArray, orig: Array<String>): String {
        val sb = StringBuilder()
        for (j in unit.indices) {
            var s = orig[unit[j]]
            if (j > 0 && s.isNotEmpty() && (s[0] == ' ' || s[0] == '`')) s = s.substring(1)
            s = s.trim()
            if (s.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s)
        }
        return sb.toString()
    }

    private fun clean(out: String, unit: IntArray, orig: Array<String>): String {
        var r = out.replace('“', '"').replace('”', '"').replace('‘', '\'').replace('’', '\'')
            .replace('«', '"').replace('»', '"')
        r = r.trim()
        val first = orig[unit[0]].firstOrNull()
        if ((first == '"' || first == '(') && !r.startsWith(first)) r = first + r
        val last = orig[unit[unit.size - 1]].trimEnd().lastOrNull()
        if ((last == '"' || last == ')') && !r.endsWith(last)) r += last
        return r
    }

    /** Traduz um trecho e coloca o resultado (como rascunho) nas linhas dele. Devolve true se aplicou. */
    fun translateUnit(unit: IntArray): Boolean {
        val orig = Store.originals
        val src = sourceText(unit, orig)
        if (src.isBlank()) return false
        val out = clean(translateText(src), unit, orig)
        Thread.sleep(120) // evita pedir rápido demais ao Google
        val r = Wrap.distribute(unit, orig, out, { Store.limitOf(it) }, true)
        val lines = r.lines ?: return false
        for (j in unit.indices) {
            Store.trans[unit[j]] = lines[j]
            Store.drafts[unit[j]] = true
        }
        return true
    }
}
