package com.aintradutor.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * OCR com PaddleOCR (PP-OCRv5 mobile) rodando no aparelho via ONNX Runtime.
 *
 * Os modelos NÃO vêm dentro do APK: são baixados uma vez (detector + reconhecedor de
 * alfabeto latino, ~12 MB no total) e depois tudo funciona sem internet.
 *
 * Etapas: detector (DBNet) acha as linhas de texto → recortes → reconhecedor (CTC) lê cada linha.
 * Modelos: PaddlePaddle/PP-OCRv5_mobile_det_onnx e PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx
 * (Apache-2.0, https://github.com/PaddlePaddle/PaddleOCR). ONNX Runtime: MIT.
 */
object Paddle {

    private const val HF = "https://huggingface.co/PaddlePaddle"
    private const val MIRROR = "https://hf-mirror.com/PaddlePaddle"

    private val DET_URLS = listOf(
        "$HF/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx",
        "$MIRROR/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx"
    )
    private val REC_URLS = listOf(
        "$HF/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx",
        "$MIRROR/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx"
    )
    // o dicionário de letras fica no inference.yml (character_dict)
    private val YML_URLS = listOf(
        "$HF/latin_PP-OCRv5_mobile_rec/resolve/main/inference.yml",
        "$HF/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml",
        "$MIRROR/latin_PP-OCRv5_mobile_rec/resolve/main/inference.yml",
        "$MIRROR/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml"
    )

    private var env: OrtEnvironment? = null
    private var det: OrtSession? = null
    private var rec: OrtSession? = null
    private var dict: List<String> = emptyList()

    /** O que o OCR leu na última captura (para o painel de diagnóstico). */
    @Volatile
    var debug: List<String> = emptyList()

    private class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    private class Line(val text: String, val conf: Float)

    fun dir(ctx: Context) = File(ctx.filesDir, "paddle")

    fun isReady(ctx: Context): Boolean {
        val d = dir(ctx)
        return File(d, "det.onnx").length() > 1_000_000 &&
            File(d, "rec.onnx").length() > 1_000_000 &&
            File(d, "rec.yml").length() > 1_000
    }

    fun sizeMb(ctx: Context): Int {
        val d = dir(ctx)
        val total = (d.listFiles() ?: emptyArray()).sumOf { it.length() }
        return (total / (1024 * 1024)).toInt()
    }

    @Synchronized
    fun close() {
        try {
            det?.close()
            rec?.close()
        } catch (e: Throwable) {
        }
        det = null
        rec = null
    }

    fun delete(ctx: Context) {
        close()
        dir(ctx).deleteRecursively()
    }

    // ---------- download ----------

    private fun fetch(urls: List<String>, file: File, minBytes: Long, label: String, progress: (String) -> Unit) {
        var last: Exception? = null
        for (u in urls) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "AliceEditor")
                if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                val tmp = File(file.path + ".part")
                c.inputStream.use { ins ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastTick = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 300) {
                                lastTick = now
                                val mb = String.format("%.1f", done / 1048576.0)
                                val of = if (total > 0) " / " + String.format("%.1f", total / 1048576.0) else ""
                                progress("Baixando $label: $mb$of MB")
                            }
                        }
                    }
                }
                c.disconnect()
                if (tmp.length() < minBytes) throw IOException("arquivo incompleto")
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("não consegui salvar o arquivo")
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("não consegui baixar $label (${last?.message})")
    }

    /** Baixa e confere os modelos. Bloqueante: chame fora da thread principal. */
    fun download(ctx: Context, progress: (String) -> Unit) {
        close()
        val d = dir(ctx)
        d.mkdirs()
        try {
            fetch(DET_URLS, File(d, "det.onnx"), 1_000_000, "detector de texto", progress)
            fetch(REC_URLS, File(d, "rec.onnx"), 1_000_000, "leitor de texto", progress)
            fetch(YML_URLS, File(d, "rec.yml"), 1_000, "dicionário", progress)
            progress("Conferindo os modelos...")
            load(ctx)
        } catch (e: Throwable) {
            delete(ctx)
            throw e
        }
    }

    // ---------- carregar ----------

    @Synchronized
    private fun load(ctx: Context) {
        if (det != null && rec != null) return
        val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val d = dir(ctx)
        val so1 = OrtSession.SessionOptions()
        so1.setIntraOpNumThreads(threads)
        val so2 = OrtSession.SessionOptions()
        so2.setIntraOpNumThreads(threads)
        val newDet = e.createSession(File(d, "det.onnx").absolutePath, so1)
        val newRec = e.createSession(File(d, "rec.onnx").absolutePath, so2)
        val chars = parseDict(File(d, "rec.yml").readText())
        if (chars.size < 50) throw IllegalStateException("dicionário do OCR inválido (${chars.size} letras)")
        det = newDet
        rec = newRec
        dict = chars
    }

    /** Lê a lista character_dict do inference.yml. */
    internal fun parseDict(yml: String): List<String> {
        val out = ArrayList<String>()
        var inDict = false
        for (raw in yml.lines()) {
            if (!inDict) {
                if (raw.trim().startsWith("character_dict:")) inDict = true
                continue
            }
            val t = raw.trimStart()
            if (!t.startsWith("-")) {
                if (t.isBlank()) continue
                break
            }
            var v = t.substring(1)
            if (v.startsWith(" ")) v = v.substring(1)
            out.add(scalar(v.trimEnd('\r', '\n')))
        }
        return out
    }

    private fun scalar(v0: String): String {
        val v = v0.trimEnd()
        if (v.length >= 2 && v[0] == '\'' && v[v.length - 1] == '\'') {
            return v.substring(1, v.length - 1).replace("''", "'")
        }
        if (v.length >= 2 && v[0] == '"' && v[v.length - 1] == '"') {
            return unescape(v.substring(1, v.length - 1))
        }
        return v
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                sb.append(c)
                i++
                continue
            }
            when (val n = s[i + 1]) {
                'n' -> { sb.append('\n'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                '0' -> { sb.append('\u0000'); i += 2 }
                'x', 'u', 'U' -> {
                    val len = if (n == 'x') 2 else if (n == 'u') 4 else 8
                    val hex = if (i + 2 + len <= s.length) s.substring(i + 2, i + 2 + len) else ""
                    val cp = hex.toIntOrNull(16)
                    if (cp != null) {
                        sb.appendCodePoint(cp)
                        i += 2 + len
                    } else {
                        sb.append(n)
                        i += 2
                    }
                }
                else -> { sb.append(n); i += 2 }
            }
        }
        return sb.toString()
    }

    // ---------- reconhecimento ----------

    /** Lê o texto da imagem. Devolve as linhas de cima para baixo. Bloqueante. */
    @Synchronized
    fun recognize(ctx: Context, bmp: Bitmap): List<String> {
        load(ctx)
        val boxes = sortBoxes(detect(bmp))
        val out = ArrayList<String>()
        val dbg = ArrayList<String>()
        dbg.add("tela ${bmp.width}x${bmp.height} • caixas de texto achadas: ${boxes.size}")
        for (b in boxes) {
            val w = b.x1 - b.x0
            val h = b.y1 - b.y0
            if (w < 4 || h < 4) continue
            val crop = Bitmap.createBitmap(bmp, b.x0, b.y0, w, h)
            val r = readLine(crop)
            crop.recycle()
            if (r != null) {
                out.add(r.text.trim())
                dbg.add(String.format("%.2f | %s", r.conf, r.text.trim()))
            } else {
                dbg.add("— (caixa ${w}x$h sem texto legível)")
            }
        }
        debug = dbg
        return out
    }

    private fun detect(bmp: Bitmap): List<Box> {
        val e = env ?: return emptyList()
        val s = det ?: return emptyList()
        val ow = bmp.width
        val oh = bmp.height
        val maxSide = 960
        val ratio = if (maxOf(ow, oh) > maxSide) maxSide.toFloat() / maxOf(ow, oh) else 1f
        val nw = maxOf(32, (ow * ratio / 32f).roundToInt() * 32)
        val nh = maxOf(32, (oh * ratio / 32f).roundToInt() * 32)
        val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
        val area = nw * nh
        val px = IntArray(area)
        scaled.getPixels(px, 0, nw, 0, 0, nw, nh)
        if (scaled !== bmp) scaled.recycle()

        // entrada: BGR, normalização ImageNet
        val data = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            data[i] = (b / 255f - 0.485f) / 0.229f
            data[area + i] = (g / 255f - 0.456f) / 0.224f
            data[2 * area + i] = (r / 255f - 0.406f) / 0.225f
        }

        var prob = FloatArray(0)
        OnnxTensor.createTensor(e, FloatBuffer.wrap(data), longArrayOf(1, 3, nh.toLong(), nw.toLong())).use { t ->
            s.run(mapOf(s.inputNames.iterator().next() to t)).use { res ->
                val fb = (res.get(0) as OnnxTensor).floatBuffer
                prob = FloatArray(fb.remaining())
                fb.get(prob)
            }
        }
        if (prob.size != area) throw IllegalStateException("saída inesperada do detector (${prob.size} / $area)")

        // pós-processamento DB: mapa binário (0,3) → regiões conectadas → caixas
        val thr = 0.3f
        val visited = BooleanArray(area)
        val stack = IntArray(area)
        val sx = ow.toFloat() / nw
        val sy = oh.toFloat() / nh
        val boxes = ArrayList<Box>()
        for (start in 0 until area) {
            if (visited[start] || prob[start] <= thr) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var minX = nw
            var minY = nh
            var maxX = 0
            var maxY = 0
            var cnt = 0
            var sum = 0.0
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % nw
                val y = p / nw
                cnt++
                sum += prob[p]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0) {
                    val q = p - 1
                    if (!visited[q] && prob[q] > thr) { visited[q] = true; stack[sp++] = q }
                }
                if (x < nw - 1) {
                    val q = p + 1
                    if (!visited[q] && prob[q] > thr) { visited[q] = true; stack[sp++] = q }
                }
                if (y > 0) {
                    val q = p - nw
                    if (!visited[q] && prob[q] > thr) { visited[q] = true; stack[sp++] = q }
                }
                if (y < nh - 1) {
                    val q = p + nw
                    if (!visited[q] && prob[q] > thr) { visited[q] = true; stack[sp++] = q }
                }
            }
            if (cnt < 10) continue
            if (sum / cnt < 0.5) continue
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            if (minOf(bw, bh) < 3) continue
            // "unclip" (1,5): a região do mapa é menor que o texto, então expande
            val d = (bw.toFloat() * bh * 1.5f) / (2f * (bw + bh))
            val x0 = ((minX - d) * sx).toInt().coerceIn(0, ow)
            val y0 = ((minY - d) * sy).toInt().coerceIn(0, oh)
            val x1 = ((maxX + 1 + d) * sx).toInt().coerceIn(0, ow)
            val y1 = ((maxY + 1 + d) * sy).toInt().coerceIn(0, oh)
            if (x1 - x0 >= 4 && y1 - y0 >= 4) boxes.add(Box(x0, y0, x1, y1))
        }
        return boxes
    }

    /** Ordem de leitura: de cima para baixo, da esquerda para a direita. */
    private fun sortBoxes(boxes: List<Box>): List<Box> {
        val l = boxes.sortedWith(compareBy({ it.y0 }, { it.x0 })).toMutableList()
        for (i in 0 until l.size - 1) {
            for (j in i downTo 0) {
                if (Math.abs(l[j + 1].y0 - l[j].y0) < 10 && l[j + 1].x0 < l[j].x0) {
                    val t = l[j]
                    l[j] = l[j + 1]
                    l[j + 1] = t
                } else {
                    break
                }
            }
        }
        return l
    }

    private fun readLine(crop: Bitmap): Line? {
        val h = 48
        val w = ceil(h * crop.width.toFloat() / crop.height).toInt().coerceIn(16, 3200)
        val scaled = Bitmap.createScaledBitmap(crop, w, h, true)
        val area = w * h
        val px = IntArray(area)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== crop) scaled.recycle()

        val a = decode(tensorData(px, area, false), w, h)
        var best = a
        if (a.conf < 0.85f) {
            // texto claro sobre fundo escuro: tenta também com as cores invertidas
            val b = decode(tensorData(px, area, true), w, h)
            if (b.conf > a.conf) best = b
        }
        return if (best.text.isBlank()) null else best
    }

    private fun tensorData(px: IntArray, area: Int, invert: Boolean): FloatArray {
        val data = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = px[i]
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF
            if (invert) {
                r = 255 - r
                g = 255 - g
                b = 255 - b
            }
            data[i] = b / 127.5f - 1f
            data[area + i] = g / 127.5f - 1f
            data[2 * area + i] = r / 127.5f - 1f
        }
        return data
    }

    private fun decode(data: FloatArray, w: Int, h: Int): Line {
        val e = env ?: return Line("", 0f)
        val s = rec ?: return Line("", 0f)
        var result = Line("", 0f)
        OnnxTensor.createTensor(e, FloatBuffer.wrap(data), longArrayOf(1, 3, h.toLong(), w.toLong())).use { t ->
            s.run(mapOf(s.inputNames.iterator().next() to t)).use { res ->
                val tensor = res.get(0) as OnnxTensor
                val shape = tensor.info.shape
                val steps = shape[1].toInt()
                val classes = shape[2].toInt()
                val fb = tensor.floatBuffer
                val buf = FloatArray(fb.remaining())
                fb.get(buf)

                // índice 0 = "branco" do CTC; depois as letras do dicionário; por fim o espaço
                val chars = ArrayList<String>(classes)
                chars.add("")
                chars.addAll(dict)
                if (chars.size < classes) chars.add(" ")
                if (chars.size != classes) {
                    throw IllegalStateException("dicionário do OCR não bate com o modelo (${dict.size} letras, modelo com $classes classes)")
                }

                val sb = StringBuilder()
                var last = 0
                var conf = 0f
                var n = 0
                for (step in 0 until steps) {
                    val off = step * classes
                    var best = 0
                    var bv = -1f
                    for (c in 0 until classes) {
                        val v = buf[off + c]
                        if (v > bv) {
                            bv = v
                            best = c
                        }
                    }
                    if (best != 0 && best != last) {
                        sb.append(chars[best])
                        conf += bv
                        n++
                    }
                    last = best
                }
                result = Line(sb.toString(), if (n > 0) conf / n else 0f)
            }
        }
        return result
    }
}
