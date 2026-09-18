package ru.vzvod.konspekt.logic

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Рисунки и схемы из загруженного конспекта.
 *
 * В .docx картинки лежат отдельными файлами, а в тексте на них стоит ссылка.
 * Мы проходим документ по порядку, вынимаем картинки к себе и ставим на их
 * место метку. Метка живёт в тексте наравне со строками, поэтому схема
 * остаётся там, где стояла, и при сборке документа встаёт в ту же ячейку
 * таблицы, что и текст рядом с ней.
 */
object DocxImages {

    const val PREFIX = "[[рис:"
    private const val SUFFIX = "]]"

    private val drawing = Regex("<w:(drawing|pict)[ >].*?</w:\\1>", RegexOption.DOT_MATCHES_ALL)
    private val embed = Regex("r:(?:embed|id)=\"(rId\\d+)\"")
    private val relation = Regex("<Relationship[^>]*Id=\"(rId\\d+)\"[^>]*Target=\"([^\"]+)\"[^>]*/?>")

    /** Форматы, которые Android умеет показать. Векторные emf/wmf пропускаем. */
    private val supported = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    fun dir(context: Context): File =
        File(context.filesDir, "lessonimg").apply { if (!exists()) mkdirs() }

    fun file(context: Context, name: String): File? {
        if (name.isBlank() || name.contains('/') || name.contains("..")) return null
        val f = File(dir(context), name)
        return if (f.exists()) f else null
    }

    fun marker(name: String) = "$PREFIX$name$SUFFIX"

    /** Имя файла из метки, либо null, если строка — обычный текст. */
    fun nameIn(line: String): String? {
        val t = line.trim()
        if (!t.startsWith(PREFIX) || !t.endsWith(SUFFIX)) return null
        return t.removePrefix(PREFIX).removeSuffix(SUFFIX).trim().ifEmpty { null }
    }

    fun hasMarker(line: String) = nameIn(line) != null

    /** Текст без меток — там, где рисунок показать нельзя. */
    fun stripMarkers(text: String): String = text
        .lines()
        .joinToString("\n") { if (hasMarker(it)) "[рисунок]" else it }

    /** Все метки, встречающиеся в правках занятия. */
    fun namesIn(texts: Collection<String>): Set<String> {
        val out = HashSet<String>()
        texts.forEach { t -> t.lines().forEach { l -> nameIn(l)?.let { out.add(it) } } }
        return out
    }

    /**
     * Заменяет рисунки в разметке документа на метки и сохраняет сами картинки.
     * @return изменённый document.xml или null, если это не docx.
     */
    fun extract(context: Context, source: File): String? = runCatching {
        ZipFile(source).use { zip ->
            val doc = zip.getEntry("word/document.xml") ?: return null
            val xml = zip.getInputStream(doc).use { it.readBytes().toString(Charsets.UTF_8) }

            val rels = zip.getEntry("word/_rels/document.xml.rels")?.let { entry ->
                val relXml = zip.getInputStream(entry).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                relation.findAll(relXml).associate { m -> m.groupValues[1] to m.groupValues[2] }
            } ?: emptyMap()

            drawing.replace(xml) { m ->
                val rid = embed.find(m.value)?.groupValues?.get(1)
                val target = rid?.let { rels[it] }
                val saved = target?.let { save(zip, it, context) }
                // Метка становится отдельным абзацем: так она не слипнется с текстом.
                if (saved == null) "" else "</w:t></w:r></w:p><w:p><w:r><w:t>${marker(saved)}"
            }
        }
    }.getOrNull()

    private fun save(zip: ZipFile, target: String, context: Context): String? = runCatching {
        val path = "word/" + target.removePrefix("/").removePrefix("word/")
        val entry = zip.getEntry(path) ?: return null
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext !in supported) return null
        val name = "${UUID.randomUUID()}.$ext"
        val out = File(dir(context), name)
        zip.getInputStream(entry).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        name
    }.getOrNull()

    /** Убирает картинки, на которые больше никто не ссылается. */
    fun cleanup(context: Context, used: Set<String>) {
        runCatching {
            dir(context).listFiles()?.forEach { f ->
                if (f.isFile && f.name !in used) f.delete()
            }
        }
    }
}
