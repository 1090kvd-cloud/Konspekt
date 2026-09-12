package ru.vzvod.konspekt.logic

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Рисунки и схемы из .docx.
 *
 * Внутри docx картинки лежат отдельными файлами, а в тексте стоит ссылка на них.
 * Мы проходим документ по порядку, вынимаем картинки к себе и ставим на их место
 * метку вида [[img:имя]]. Дальше метка живёт в тексте конспекта: при сборке
 * документа и при печати на её место снова подставляется рисунок.
 */
object DocxImages {

    const val PREFIX = "[[img:"
    private const val SUFFIX = "]]"

    private val drawing = Regex("<w:(drawing|pict)[ >].*?</w:\\1>", RegexOption.DOT_MATCHES_ALL)
    private val embed = Regex("r:(?:embed|id)=\"(rId\\d+)\"")
    private val relation = Regex(
        "<Relationship[^>]*Id=\"(rId\\d+)\"[^>]*Target=\"([^\"]+)\"[^>]*/?>"
    )

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

    /** Текст без метки — для тех мест, где рисунок показать нельзя. */
    fun stripMarkers(text: String): String = text
        .lines()
        .joinToString("\n") { if (hasMarker(it)) "[рисунок]" else it }

    /**
     * Раскладывает document.xml на текст с метками и вынимает сами картинки.
     * @return готовый для показа текст или null, если это не docx.
     */
    fun extract(context: Context, source: File): String? = runCatching {
        ZipFile(source).use { zip ->
            val doc = zip.getEntry("word/document.xml") ?: return null
            val xml = zip.getInputStream(doc).use { it.readBytes().toString(Charsets.UTF_8) }

            val rels = zip.getEntry("word/_rels/document.xml.rels")?.let { entry ->
                val relXml = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                relation.findAll(relXml).associate { m -> m.groupValues[1] to m.groupValues[2] }
            } ?: emptyMap()

            // Каждый рисунок заменяем меткой — так сохраняется его место в тексте.
            val withMarkers = drawing.replace(xml) { m ->
                val rid = embed.find(m.value)?.groupValues?.get(1)
                val target = rid?.let { rels[it] }
                val saved = target?.let { save(zip, it, context) }
                if (saved == null) "" else "</w:p><w:p>${marker(saved)}</w:p><w:p>"
            }
            withMarkers
        }
    }.getOrNull()

    private fun save(zip: ZipFile, target: String, context: Context): String? = runCatching {
        val path = "word/" + target.removePrefix("/").removePrefix("word/")
        val entry = zip.getEntry(path) ?: return null
        val ext = path.substringAfterLast('.', "png").lowercase()
        if (ext !in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")) return null
        val name = "${UUID.randomUUID()}.$ext"
        val out = File(dir(context), name)
        zip.getInputStream(entry).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        name
    }.getOrNull()

    /** Убирает картинки, на которые больше никто не ссылается. */
    fun cleanup(context: Context, usedNames: Set<String>) {
        runCatching {
            dir(context).listFiles()?.forEach { f ->
                if (f.isFile && f.name !in usedNames) f.delete()
            }
        }
    }

    /** Все метки, встречающиеся в тексте правок занятия. */
    fun namesIn(texts: Collection<String>): Set<String> {
        val out = HashSet<String>()
        texts.forEach { t ->
            t.lines().forEach { line -> nameIn(line)?.let { out.add(it) } }
        }
        return out
    }
}
