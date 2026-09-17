package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.Settings
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Готовит Word-файл из загруженного конспекта, не пересобирая его.
 *
 * Чужой документ — это своя вёрстка, таблицы, схемы и рисунки. Воспроизвести их
 * генерацией нельзя, поэтому берём исходный файл целиком и подставляем в него
 * только то, что меняется от занятия к занятию: дату, подразделение, руководителя.
 * Всё остальное остаётся байт в байт как было.
 */
object DocxRewrite {

    private val paragraph = Regex("<w:p\\b[^>]*>.*?</w:p>", RegexOption.DOT_MATCHES_ALL)
    private val textNode = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
    private val propsNode = Regex("<w:pPr>.*?</w:pPr>", RegexOption.DOT_MATCHES_ALL)
    private val runProps = Regex("<w:rPr>.*?</w:rPr>", RegexOption.DOT_MATCHES_ALL)

    /** Пустая линейка даты: «____» августа 2026 года, «___» ________ 20___ г. */
    private val blankDate = Regex(
        "[«\"]?\\s*_{2,}\\s*[»\"]?\\s*(_{2,}|[А-Яа-я]{3,10})?\\s*(20\\s*_{2,}|20\\d\\d)\\s*(года|г\\.?)?"
    )

    data class Result(val ok: Boolean, val replaced: Int)

    fun rewrite(source: File, out: OutputStream, plan: LessonPlan, s: Settings): Result {
        var replaced = 0
        return runCatching {
            ZipFile(source).use { zip ->
                val docEntry = zip.getEntry("word/document.xml")
                    ?: return Result(false, 0)
                val xml = zip.getInputStream(docEntry).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }

                val patched = paragraph.replace(xml) { m ->
                    val block = m.value
                    val plain = textOf(block)
                    val fixed = substitute(plain, plan, s)
                    if (fixed == null) block else {
                        replaced++
                        rebuild(block, fixed)
                    }
                }

                ZipOutputStream(out).use { zos ->
                    val names = zip.entries().toList()
                    names.forEach { e ->
                        if (e.isDirectory) return@forEach
                        zos.putNextEntry(ZipEntry(e.name))
                        if (e.name == "word/document.xml") {
                            zos.write(patched.toByteArray(Charsets.UTF_8))
                        } else {
                            zip.getInputStream(e).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }
            Result(true, replaced)
        }.getOrElse { Result(false, replaced) }
    }

    /** Текст абзаца без разметки: он может быть разрезан на десяток кусков. */
    private fun textOf(block: String): String =
        textNode.findAll(block)
            .joinToString("") { it.groupValues[1] }
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()

    /** Возвращает новый текст абзаца или null, если абзац трогать не нужно. */
    private fun substitute(text: String, plan: LessonPlan, s: Settings): String? {
        if (text.isEmpty()) return null
        val i = plan.input

        val date = i.date.trim()
        if (date.isNotEmpty() && blankDate.containsMatchIn(text) && text.length < 60) {
            return blankDate.replace(text, date)
        }

        val unit = i.unitName.ifBlank { s.unitName }.trim()
        if (unit.isNotEmpty() && Regex("^с\\s+\\S.{2,60}$").matches(text) && !text.contains("_")) {
            return "с $unit"
        }

        val leader = i.leader.ifBlank { s.leader }.trim()
        if (leader.isNotEmpty() && Regex("^руководител[ья]\\s+занятия\\s*:?$", RegexOption.IGNORE_CASE)
                .matches(text)
        ) {
            return "Руководитель занятия $leader"
        }

        return null
    }

    /**
     * Ставит новый текст в абзац, сохраняя его оформление: берём свойства абзаца
     * и первого куска текста, остальные куски убираем.
     */
    private fun rebuild(block: String, text: String): String {
        val pPr = propsNode.find(block)?.value.orEmpty()
        val rPr = runProps.find(block)?.value.orEmpty()
        val open = block.substringBefore('>') + ">"
        return open + pPr +
            "<w:r>" + rPr + "<w:t xml:space=\"preserve\">" + esc(text) + "</w:t></w:r>" +
            "</w:p>"
    }

    private fun esc(t: String) = t
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
