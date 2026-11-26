package com.aintradutor.app

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Tradução automática inglês → português feita no aparelho (ML Kit).
 * Traduz o trecho inteiro (a fala completa, não linha por linha) e distribui o resultado
 * nas linhas do trecho. O resultado fica marcado como "rascunho" até você revisar.
 * Todas as funções são bloqueantes: chame fora da thread principal.
 */
object Auto {

    private var translator: Translator? = null

    private fun get(): Translator {
        val t = translator
        if (t != null) return t
        val n = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                .build()
        )
        translator = n
        return n
    }

    /** Baixa o modelo de tradução se ainda não estiver no aparelho (precisa de internet só nessa vez). */
    fun ensureModel() {
        Tasks.await(get().downloadModelIfNeeded(DownloadConditions.Builder().build()))
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
        val out = clean(Tasks.await(get().translate(src)), unit, orig)
        val r = Wrap.distribute(unit, orig, out, { Store.limitOf(it) }, true)
        val lines = r.lines ?: return false
        for (j in unit.indices) {
            Store.trans[unit[j]] = lines[j]
            Store.drafts[unit[j]] = true
        }
        return true
    }
}
