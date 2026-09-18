package ru.vzvod.konspekt.logic

import android.content.Context
import ru.vzvod.konspekt.model.Material
import java.io.File
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * Достаёт текст из приложенного файла. Никакого разбора смысла — только чтение.
 * Результат кешируется, чтобы не разбирать методичку заново при каждом поиске.
 */
object TextExtract {

    sealed interface Result {
        data class Ok(val text: String) : Result
        data class Unsupported(val reason: String) : Result
    }

    private fun cacheFile(context: Context, id: String) =
        File(File(context.filesDir, "extracted").apply { mkdirs() }, "$id.txt")

    fun extract(context: Context, m: Material): Result {
        val cache = cacheFile(context, m.id)
        if (cache.exists()) {
            val cached = runCatching { cache.readText() }.getOrNull()
            if (!cached.isNullOrBlank()) return Result.Ok(cached)
        }

        val source = File(File(context.filesDir, "materials"), m.storedName)
        if (!source.exists()) return Result.Unsupported("Файл не найден")

        val result = fromFile(source, m.storedName, context)
        if (result is Result.Ok) {
            val clean = withoutTableMarks(result.text)
            runCatching { cache.writeText(clean) }
            return Result.Ok(clean)
        }
        return result
    }

    /**
     * Разбор произвольного файла.
     * @param context нужен для распознавания сканов и снимков; без него они пропускаются.
     */
    fun fromFile(source: File, fileName: String, context: Context? = null): Result {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val result = runCatching {
            when (ext) {
                "txt", "md", "csv", "log" -> Result.Ok(readAsText(source.readBytes()))
                "html", "htm", "xhtml" -> Result.Ok(stripTags(readAsText(source.readBytes())))
                "doc" -> readDoc(source)
                "docx" -> readDocx(source, context)
                "rtf" -> Result.Ok(readRtf(readAsText(source.readBytes())))
                "pdf" -> Result.Unsupported(
                    "PDF приложение не читает. Приложите методичку в .docx или .txt"
                )
                "jpg", "jpeg", "png", "webp", "gif", "bmp" -> Result.Unsupported(
                    "Это изображение — текста в нём нет"
                )
                else -> Result.Unsupported("Формат .$ext не разбирается")
            }
        }.getOrElse { Result.Unsupported("Файл не читается") }

        if (result is Result.Ok) {
            val clean = tidy(result.text)
            if (clean.isBlank()) return Result.Unsupported("В файле не нашлось текста")
            return Result.Ok(clean)
        }
        return result
    }

    /** Читает файл, выбранный пользователем, не копируя его в приложение насовсем. */
    fun fromUri(context: Context, uri: android.net.Uri, fileName: String): Result {
        val tmp = File.createTempFile("import", ".tmp", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return Result.Unsupported("Файл недоступен для чтения")
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            return fromFile(tmp, fileName, context)
        } finally {
            tmp.delete()
        }
    }

    fun forget(context: Context, id: String) {
        runCatching { cacheFile(context, id).delete() }
    }

    // --- форматы ---

    /** .doc бывает двух видов: настоящий двоичный и сохранённый как HTML. */
    private fun readDoc(file: File): Result {
        val head = file.inputStream().use { ByteArray(8).also { b -> it.read(b) } }
        val ole = head.size >= 4 &&
            head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() &&
            head[2] == 0x11.toByte() && head[3] == 0xE0.toByte()
        if (ole) {
            return Result.Unsupported(
                "Старый двоичный .doc не разбирается. Откройте его в Word и сохраните как .docx"
            )
        }
        return Result.Ok(stripTags(readAsText(file.readBytes())))
    }

    /** .docx — это zip, текст лежит в word/document.xml. */
    /** Разделители ячеек и строк таблицы: без них ход занятия слипается в кашу. */
    const val CELL = "\u0001"
    const val ROW = "\u0002"

    private fun readDocx(file: File, context: Context?): Result {
        // Если есть куда сохранить рисунки — берём разметку с метками вместо картинок.
        val xml = (context?.let { DocxImages.extract(it, file) }) ?: plainDocx(file)
            ?: return Result.Unsupported("Внутри .docx нет текстовой части")
        val withBreaks = xml
            .replace(Regex("<w:tab[^>]*/>"), " ")
            .replace(Regex("<w:br[^>]*/>"), "\n")
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("</w:tc>"), CELL)
            .replace(Regex("</w:tr>"), ROW + "\n")
            // Word дробит текст посреди слова: «К» и «омандир» лежат в разных
            // кусках. Между ними тег убираем начисто, иначе слова и числа рвутся.
            .replace(Regex("</w:t>\\s*</w:r>\\s*<w:r[^>]*>(?:<w:rPr>.*?</w:rPr>)?<w:t[^>]*>",
                RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("</w:t>\\s*<w:t[^>]*>"), "")
        return Result.Ok(stripTags(withBreaks))
    }

    /** Для поиска по материалам разметка таблицы не нужна. */
    fun withoutTableMarks(text: String): String =
        text.replace(CELL, "\n").replace(ROW, "\n")

    private fun plainDocx(file: File): String? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use null
            zip.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
        }
    }.getOrNull()

    private fun readRtf(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        var skipDepth = -1
        var depth = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '{' -> { depth++; i++ }
                c == '}' -> {
                    if (skipDepth == depth) skipDepth = -1
                    depth--; i++
                }
                c == '\\' -> {
                    val m = Regex("""\\([a-zA-Z]+)(-?\d+)?""").find(raw, i)
                    if (m != null && m.range.first == i) {
                        val word = m.groupValues[1]
                        val num = m.groupValues[2].toIntOrNull()
                        when (word) {
                            "par", "line", "sect" -> sb.append('\n')
                            "tab" -> sb.append(' ')
                            "u" -> if (num != null) sb.append(num.toChar())
                            // служебные блоки целиком пропускаем
                            "fonttbl", "colortbl", "stylesheet", "info", "pict", "generator" ->
                                skipDepth = depth
                        }
                        i = m.range.last + 1
                        if (i < raw.length && raw[i] == ' ') i++
                    } else if (i + 1 < raw.length && raw[i + 1] == '\'') {
                        val hex = raw.substring(i + 2, minOf(i + 4, raw.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null && skipDepth == -1) {
                            sb.append(String(byteArrayOf(code.toByte()), charset("windows-1251")))
                        }
                        i += 4
                    } else i += 2
                }
                else -> {
                    if (skipDepth == -1) sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    // --- вспомогательное ---

    /** Методички ходят и в UTF-8, и в windows-1251. Пробуем строго, потом откатываемся. */
    fun readAsText(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes

        val strict: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { strict.decode(java.nio.ByteBuffer.wrap(body)).toString() }
            .getOrElse { String(body, charset("windows-1251")) }
    }

    private fun stripTags(html: String): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br[^>]*>"), "\n")
        .replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&laquo;", "«").replace("&raquo;", "»")
        .replace("&mdash;", "—").replace("&ndash;", "–")
        .replace("&quot;", "\"").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">")

    private fun tidy(text: String): String = text
        .replace('\u00A0', ' ')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .lines().joinToString("\n") { it.trim() }
        .trim()
}
