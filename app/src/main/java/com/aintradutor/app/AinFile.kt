package com.aintradutor.app

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Arquivo .ain da AliceSoft (System 4). Aceita o formato comprimido (cabeçalho AI2, zlib)
 * e o sem compressão (começa com VERS). Mexe só em duas tabelas de texto:
 *  - MSG0: as falas do diálogo;
 *  - STR0: textos do sistema (menus, escolhas, mensagens e também códigos internos).
 * O resto do arquivo é copiado igual.
 */
class AinFile private constructor(
    private val raw: ByteArray,
    private val msgStart: Int,
    private val msgEnd: Int,
    val originals: List<ByteArray>,
    private val strStart: Int,
    private val strEnd: Int,
    val strOriginals: List<ByteArray>,
    val compressed: Boolean,
    val version: Int
) {

    fun describe(): String =
        "AIN versão $version • " + (if (compressed) "comprimido (AI2)" else "sem compressão") +
            " • ${originals.size} entradas de fala (MSG0)" +
            (if (strStart >= 0) " • ${strOriginals.size} textos do sistema (STR0)" else "")

    private fun section(orig: List<ByteArray>, tr: Array<String?>?, strip: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        for (i in orig.indices) {
            val t = tr?.getOrNull(i)
            out.write(if (t.isNullOrEmpty()) orig[i] else encode(t, strip))
            out.write(0)
        }
        return out.toByteArray()
    }

    /**
     * Monta um .ain novo. translations[i] == null/vazio mantém a fala original.
     * sys = traduções dos textos do sistema (STR0), se houver.
     */
    fun build(translations: Array<String?>, strip: Boolean = true, sys: Array<String?>? = null): ByteArray {
        val parts = ArrayList<Triple<Int, Int, ByteArray>>()
        parts.add(Triple(msgStart + 8, msgEnd, section(originals, translations, strip)))
        if (strStart >= 0) parts.add(Triple(strStart + 8, strEnd, section(strOriginals, sys, strip)))
        parts.sortBy { it.first }

        val out = ByteArrayOutputStream(raw.size + 1_000_000)
        var pos = 0
        for ((s, e, b) in parts) {
            out.write(raw, pos, s - pos)
            out.write(b)
            pos = e
        }
        out.write(raw, pos, raw.size - pos)
        val body = out.toByteArray()
        if (!compressed) return body

        val def = Deflater(9)
        def.setInput(body)
        def.finish()
        val comp = ByteArrayOutputStream(body.size / 3 + 1024)
        val buf = ByteArray(65536)
        while (!def.finished()) {
            val n = def.deflate(buf)
            comp.write(buf, 0, n)
        }
        def.end()
        val c = comp.toByteArray()

        val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        header.put(byteArrayOf(0x41, 0x49, 0x32, 0))
        header.putInt(0)
        header.putInt(body.size)
        header.putInt(c.size)
        return header.array() + c
    }

    companion object {
        val CP932: Charset = Charset.forName("windows-31j")
        private val NEXT = listOf("MAIN", "MSG1", "MSGF", "HLL0", "SWI0")

        private class Sec(val tagPos: Int, val end: Int, val list: List<ByteArray>)

        private fun looksLikeTag(raw: ByteArray, q: Int): Boolean {
            if (q + 4 > raw.size) return false
            for (k in 0 until 4) {
                val c = raw[q + k].toInt()
                if (!((c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code))) return false
            }
            return true
        }

        /** Procura uma tabela de textos (tag + quantidade + textos terminados em zero) e confere o fim dela. */
        private fun readSection(raw: ByteArray, tag: String, accept: (ByteArray, Int) -> Boolean): Sec? {
            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            var p = indexOf(raw, tag, 0)
            while (p >= 0) {
                if (p + 8 <= raw.size) {
                    val n = bb.getInt(p + 4)
                    if (n in 1..2_000_000) {
                        val list = ArrayList<ByteArray>(n)
                        var q = p + 8
                        var ok = true
                        for (k in 0 until n) {
                            var e = q
                            while (e < raw.size && raw[e] != 0.toByte()) e++
                            if (e >= raw.size) {
                                ok = false
                                break
                            }
                            list.add(raw.copyOfRange(q, e))
                            q = e + 1
                        }
                        if (ok && accept(raw, q)) return Sec(p, q, list)
                    }
                }
                p = indexOf(raw, tag, p + 1)
            }
            return null
        }

        fun parse(data: ByteArray): AinFile {
            val raw: ByteArray
            val compressed: Boolean
            val head = if (data.size >= 4) String(data, 0, 4, Charsets.ISO_8859_1) else ""
            if (data.size > 16 && data[0] == 0x41.toByte() && data[1] == 0x49.toByte() && data[2] == 0x32.toByte()) {
                val usize = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(8)
                val buf = ByteArray(usize)
                val inf = Inflater()
                inf.setInput(data, 16, data.size - 16)
                var off = 0
                while (off < usize) {
                    val n = inf.inflate(buf, off, usize - off)
                    if (n == 0 && (inf.finished() || inf.needsInput())) break
                    off += n
                }
                inf.end()
                require(off == usize) { "Falha ao descomprimir o .ain" }
                raw = buf
                compressed = true
            } else if (head == "VERS") {
                raw = data
                compressed = false
            } else {
                val shown = head.filter { it.code in 32..126 }
                throw IllegalArgumentException("Formato não reconhecido (o arquivo começa com \"$shown\"). O app lê arquivos .ain da AliceSoft.")
            }

            val version = if (raw.size >= 8 && String(raw, 0, 4, Charsets.ISO_8859_1) == "VERS") {
                ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
            } else {
                -1
            }

            val msg = readSection(raw, "MSG0") { r, q ->
                q + 4 <= r.size && NEXT.contains(String(r, q, 4, Charsets.ISO_8859_1))
            }
            if (msg == null) {
                if (indexOf(raw, "MSG1", 0) >= 0) {
                    throw IllegalArgumentException(
                        "Este .ain (versão $version) parece guardar as falas na seção MSG1, que o app ainda não lê. " +
                            "Me mande o arquivo para eu adicionar o suporte."
                    )
                }
                throw IllegalArgumentException("Não achei a seção de falas (MSG0) neste .ain (versão $version).")
            }
            // textos do sistema (opcional)
            val str = readSection(raw, "STR0") { r, q -> q == r.size || looksLikeTag(r, q) }
            val usable = str != null && (str.tagPos + 8 >= msg.end || str.end <= msg.tagPos)
            return if (str != null && usable) {
                AinFile(raw, msg.tagPos, msg.end, msg.list, str.tagPos, str.end, str.list, compressed, version)
            } else {
                AinFile(raw, msg.tagPos, msg.end, msg.list, -1, -1, emptyList(), compressed, version)
            }
        }

        private fun indexOf(a: ByteArray, s: String, from: Int): Int {
            val t = s.toByteArray(Charsets.ISO_8859_1)
            var i = from
            while (i <= a.size - t.size) {
                var j = 0
                while (j < t.size && a[i + j] == t[j]) j++
                if (j == t.size) return i
                i++
            }
            return -1
        }

        /** O jogo não mostra acentos nem "ç": tira os acentos das letras latinas. */
        fun stripAccents(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) {
                if (c.code in 0xC0..0x17F) {
                    val d = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                    sb.append(d[0])
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        fun encode(s: String, strip: Boolean = true): ByteArray =
            (if (strip) stripAccents(s) else s).toByteArray(CP932)
    }
}
