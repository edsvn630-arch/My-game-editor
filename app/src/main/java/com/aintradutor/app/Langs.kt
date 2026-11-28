package com.aintradutor.app

import android.content.Context
import androidx.appcompat.app.AlertDialog
import java.text.Collator
import java.util.Locale

/** Idiomas do Google Tradutor (código, nome em português). */
object Langs {
    class L(val code: String, val name: String)

    private val raw = listOf(
        "af" to "Africâner", "sq" to "Albanês", "de" to "Alemão", "am" to "Amárico", "ar" to "Árabe",
        "hy" to "Armênio", "as" to "Assamês", "ay" to "Aimará", "az" to "Azerbaijano", "bm" to "Bambara",
        "eu" to "Basco", "bn" to "Bengali", "bho" to "Bhojpuri", "be" to "Bielorrusso", "my" to "Birmanês",
        "bs" to "Bósnio", "bg" to "Búlgaro", "kn" to "Canarês", "ca" to "Catalão", "kk" to "Cazaque",
        "ceb" to "Cebuano", "zh-CN" to "Chinês (simplificado)", "zh-TW" to "Chinês (tradicional)",
        "si" to "Cingalês", "ko" to "Coreano", "co" to "Corso", "kri" to "Krio", "hr" to "Croata",
        "ku" to "Curdo (kurmanji)", "ckb" to "Curdo (sorani)", "da" to "Dinamarquês", "dv" to "Divehi",
        "doi" to "Dogri", "sk" to "Eslovaco", "sl" to "Esloveno", "es" to "Espanhol", "eo" to "Esperanto",
        "et" to "Estoniano", "ee" to "Ewe", "tl" to "Filipino", "fi" to "Finlandês", "fr" to "Francês",
        "fy" to "Frísio", "gd" to "Gaélico escocês", "cy" to "Galês", "gl" to "Galego", "ka" to "Georgiano",
        "el" to "Grego", "gn" to "Guarani", "gu" to "Guzerate", "ht" to "Crioulo haitiano", "ha" to "Hauçá",
        "haw" to "Havaiano", "iw" to "Hebraico", "hi" to "Hindi", "hmn" to "Hmong", "nl" to "Holandês",
        "hu" to "Húngaro", "ig" to "Igbo", "ilo" to "Ilocano", "id" to "Indonésio", "en" to "Inglês",
        "yo" to "Iorubá", "ga" to "Irlandês", "is" to "Islandês", "it" to "Italiano", "ja" to "Japonês",
        "jw" to "Javanês", "yi" to "Iídiche", "km" to "Khmer", "rw" to "Kinyarwanda", "gom" to "Concani",
        "ky" to "Quirguiz", "lo" to "Laosiano", "la" to "Latim", "lv" to "Letão", "ln" to "Lingala",
        "lt" to "Lituano", "lg" to "Luganda", "lb" to "Luxemburguês", "mk" to "Macedônio", "mai" to "Maithili",
        "ms" to "Malaio", "ml" to "Malaiala", "mg" to "Malgaxe", "mt" to "Maltês", "mi" to "Maori",
        "mr" to "Marata", "mni-Mtei" to "Meiteilon (manipuri)", "lus" to "Mizo", "mn" to "Mongol",
        "ne" to "Nepalês", "no" to "Norueguês", "ny" to "Nianja (chichewa)", "or" to "Oriá", "om" to "Oromo",
        "ps" to "Pachto", "fa" to "Persa", "pl" to "Polonês", "pt" to "Português", "pa" to "Punjabi",
        "qu" to "Quíchua", "ro" to "Romeno", "ru" to "Russo", "sm" to "Samoano", "sa" to "Sânscrito",
        "nso" to "Sepedi", "sr" to "Sérvio", "st" to "Sesoto", "sn" to "Shona", "sd" to "Sindi",
        "so" to "Somali", "su" to "Sundanês", "sw" to "Suaíli", "sv" to "Sueco", "tg" to "Tadjique",
        "th" to "Tailandês", "ta" to "Tâmil", "cs" to "Tcheco", "te" to "Telugu", "ti" to "Tigrínia",
        "ts" to "Tsonga", "tr" to "Turco", "tk" to "Turcomeno", "tt" to "Tártaro", "ak" to "Twi",
        "uk" to "Ucraniano", "ug" to "Uigur", "ur" to "Urdu", "uz" to "Uzbeque", "vi" to "Vietnamita",
        "xh" to "Xhosa", "zu" to "Zulu"
    )

    val all: List<L> by lazy {
        val c = Collator.getInstance(Locale("pt", "BR"))
        raw.map { L(it.first, it.second) }.sortedWith { a, b -> c.compare(a.name, b.name) }
    }

    fun nameOf(code: String): String =
        if (code == "auto") "Detectar idioma" else all.firstOrNull { it.code == code }?.name ?: code

    /** Idiomas escritos sem espaço entre as palavras: o texto é dividido por caractere. */
    fun spaceless(code: String): Boolean = code in setOf("zh-CN", "zh-TW", "ja", "th", "lo", "km", "my")

    fun pick(ctx: Context, title: String, includeAuto: Boolean, onPick: (String) -> Unit) {
        val list = (if (includeAuto) listOf(L("auto", "Detectar idioma")) else emptyList()) + all
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setItems(list.map { it.name }.toTypedArray()) { _, which -> onPick(list[which].code) }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
