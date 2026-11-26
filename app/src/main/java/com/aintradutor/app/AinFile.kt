package com.aintradutor.app

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Arquivo .ain da AliceSoft (System 4, container AI2 com zlib).
 * Só mexe na seção MSG0 (as falas); o resto do arquivo é copiado igual.
 */
class AinFile private constructor(
    private val raw: ByteArray,
    private val msgStart: Int,
    private val msgEnd: Int,
    val originals: List<ByteArray>
) {

    /** Monta um .ain novo. translations[i] == null/vazio mantém a fala original. */
    fun build(translations: Array<String?>, strip: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream(raw.size + 1_000_000)
        out.write(raw, 0, msgStart + 8)
        for (i in originals.indices) {
            val t = translations[i]
            out.write(if (t.isNullOrEmpty()) originals[i] else encode(t, strip))
            out.write(0)
        }
        out.write(raw, msgEnd, raw.size - msgEnd)
        val body = out.toByteArray()

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

        fun parse(data: ByteArray): AinFile {
            require(
                data.size > 16 &&
                    data[0] == 0x41.toByte() &&
                    data[1] == 0x49.toByte() &&
                    data[2] == 0x32.toByte()
            ) { "Não é um arquivo .ain (cabeçalho AI2)" }

            val usize = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(8)
            val raw = ByteArray(usize)
            val inf = Inflater()
            inf.setInput(data, 16, data.size - 16)
            var off = 0
            while (off < usize) {
                val n = inf.inflate(raw, off, usize - off)
                if (n == 0 && (inf.finished() || inf.needsInput())) break
                off += n
            }
            inf.end()
            require(off == usize) { "Falha ao descomprimir o .ain" }

            val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            var p = indexOf(raw, "MSG0", 0)
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
                        if (ok && q + 4 <= raw.size &&
                            NEXT.contains(String(raw, q, 4, Charsets.ISO_8859_1))
                        ) {
                            return AinFile(raw, p, q, list)
                        }
                    }
                }
                p = indexOf(raw, "MSG0", p + 1)
            }
            throw IllegalArgumentException("Seção de falas (MSG0) não encontrada")
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
