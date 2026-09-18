package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.Settings
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Собирает настоящий .docx — zip с разметкой WordprocessingML.
 *
 * Раньше документ отдавался как HTML с расширением .doc: Word его понимал,
 * а мобильные просмотрщики показывали разметку текстом. Настоящий docx открывают все.
 */
object DocxWriter {

    fun write(out: OutputStream, plan: LessonPlan, s: Settings) {
        ZipOutputStream(out).use { zip ->
            put(zip, "[Content_Types].xml", CONTENT_TYPES)
            put(zip, "_rels/.rels", RELS)
            put(zip, "word/_rels/document.xml.rels", DOC_RELS)
            put(zip, "word/document.xml", document(plan, s))
        }
    }

    private fun put(zip: ZipOutputStream, name: String, body: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(body.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // --- разметка документа ---

    /** Убирает нумерацию, оставшуюся в тексте: документ нумерует пункты сам. */
    private fun unnumbered(line: String) =
        line.replace(Regex("^(\\d{1,2}[\\.\\)]\\s*)+"), "").trim()

    private fun esc(t: String) = t
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Абзац. align: left|center|right, ind — отступ первой строки в твипах. */
    private fun p(
        text: String,
        bold: Boolean = false,
        align: String = "left",
        ind: Int = 0,
        size: Int = 24,
        caps: Boolean = false
    ): String {
        val runProps = buildString {
            append("<w:rPr>")
            append("<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>")
            if (bold) append("<w:b/>")
            if (caps) append("<w:caps/>")
            append("<w:sz w:val=\"$size\"/>")
            append("</w:rPr>")
        }
        val pProps = buildString {
            append("<w:pPr>")
            append("<w:jc w:val=\"$align\"/>")
            if (ind > 0) append("<w:ind w:firstLine=\"$ind\"/>")
            append("<w:spacing w:after=\"0\" w:line=\"264\" w:lineRule=\"auto\"/>")
            append("</w:pPr>")
        }
        return "<w:p>$pProps<w:r>$runProps<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
    }

    /** Строка вида «Время: 1 час 30 мин» — начало полужирным. */
    private fun keyValue(key: String, value: String, ind: Int = 709): String {
        val rf = "<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/>"
        return "<w:p><w:pPr><w:ind w:firstLine=\"$ind\"/>" +
            "<w:spacing w:after=\"0\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr>" +
            "<w:r><w:rPr>$rf<w:b/></w:rPr><w:t xml:space=\"preserve\">$key </w:t></w:r>" +
            "<w:r><w:rPr>$rf</w:rPr><w:t xml:space=\"preserve\">${esc(value)}</w:t></w:r></w:p>"
    }

    /** В ячейке обязан быть хотя бы один абзац, иначе Word считает документ повреждённым. */
    private fun cell(width: Int, content: String): String {
        val body = content.ifBlank { p("") }
        return "<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"pct\"/></w:tcPr>$body</w:tc>"
    }

    private fun bullets(items: List<String>): String =
        if (items.isEmpty()) p("") else items.joinToString("") { p("— $it", size = 22) }

    private fun document(plan: LessonPlan, s: Settings): String {
        val i = plan.input
        val body = StringBuilder()

        // Гриф утверждения
        body.append(p("УТВЕРЖДАЮ", align = "right"))
        body.append(p(s.approver.ifBlank { "Командир роты" }, align = "right"))
        body.append(p("____________________", align = "right"))
        // Дата занятия стоит в грифе утверждения, справа сверху — как в форме.
        body.append(p(Renderer.approvalDate(i.date), align = "right"))
        body.append(p(""))

        body.append(p("ПЛАН-КОНСПЕКТ", bold = true, align = "center"))
        body.append(p("проведения занятия по ${plan.dative}", align = "center"))
        val unit = i.unitName.ifBlank { s.unitName }
        if (unit.isNotBlank()) body.append(p("с $unit", align = "center"))
        body.append(p(""))

        body.append(keyValue("ТЕМА ${i.themeNo}:", i.topic.uppercase()))
        if (i.lessonTitle.isNotBlank()) {
            body.append(keyValue("ЗАНЯТИЕ ${i.lessonNo}:", i.lessonTitle.uppercase()))
        }

        body.append(keyValue("ЦЕЛИ:", ""))
        plan.goals.forEachIndexed { n, g ->
            body.append(p("${n + 1}. ${unnumbered(g)}", ind = 709))
        }

        // В строке «Время» — только время: дата уже стоит в грифе утверждения.
        body.append(keyValue("Время:", i.timeLabel.ifBlank { Generator.timeText(i.minutes) }))
        body.append(keyValue("Место:", plan.place))
        body.append(keyValue("Материальное обеспечение:", plan.provision.joinToString(", ")))
        if (plan.safety.isNotEmpty()) {
            body.append(keyValue("Требования безопасности:", ""))
            body.append(bullets(plan.safety))
        }

        body.append(p(""))
        body.append(p("ХОД ЗАНЯТИЯ", bold = true, align = "center"))

        // Таблица хода занятия: № / учебные вопросы / содержание / действия обучаемых
        body.append("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/>")
        body.append("<w:tblBorders>")
        listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach {
            body.append("<w:$it w:val=\"single\" w:sz=\"6\" w:color=\"000000\"/>")
        }
        body.append("</w:tblBorders></w:tblPr>")

        body.append("<w:tr><w:trPr><w:tblHeader/></w:trPr>")
        body.append(cell(300, p("№ п/п", align = "center", size = 20)))
        body.append(cell(900, p("Учебные вопросы", align = "center", size = 20)))
        body.append(cell(2900, p("Содержание учебных вопросов", align = "center", size = 20)))
        body.append(cell(900, p("Действия обучаемых", align = "center", size = 20)))
        body.append("</w:tr>")

        fun row(no: String, head: String, minutes: Int, content: List<String>, trainee: List<String>) {
            body.append("<w:tr>")
            body.append(cell(300, p(no, size = 22)))
            body.append(cell(900, p(head, bold = true, size = 22) + p("$minutes мин.", size = 20)))
            body.append(cell(2900, content.joinToString("") { p(it, size = 22) }))
            body.append(cell(900, trainee.joinToString("") { p(it, size = 20) }))
            body.append("</w:tr>")
        }

        row("1.", plan.intro.title, plan.intro.minutes, plan.intro.content, plan.intro.trainee)
        plan.questions.forEachIndexed { n, q ->
            row(
                if (n == 0) "2." else "",
                if (n == 0) "Основная часть" else "",
                q.minutes,
                listOf("${q.index}. ${q.title}") + q.content,
                q.trainee
            )
        }
        row("3.", plan.outro.title, plan.outro.minutes, plan.outro.content, plan.outro.trainee)
        body.append("</w:tbl>")

        if (i.note.isNotBlank()) {
            body.append(p(""))
            body.append(keyValue("Примечания:", i.note))
        }

        if (plan.handout.isNotEmpty()) {
            body.append(pageBreak())
            body.append(p("РАЗДАТОЧНЫЙ МАТЕРИАЛ", bold = true, align = "center"))
            plan.handout.forEach { b ->
                body.append(p(b.title, bold = true))
                b.items.forEach { body.append(p(it, size = 22)) }
            }
        }
        if (plan.control.isNotEmpty()) {
            body.append(pageBreak())
            body.append(p("КОНТРОЛЬНЫЕ ВОПРОСЫ", bold = true, align = "center"))
            plan.control.forEachIndexed { n, q -> body.append(p("${n + 1}. $q", ind = 709)) }
        }

        // Подпись руководителя — последней строкой документа, после всех приложений.
        body.append(p(""))
        body.append(p("Руководитель занятия", align = "center"))
        body.append(
            p(
                i.leader.ifBlank { s.leader }.ifBlank { "________________________" },
                align = "center"
            )
        )

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>$body<w:sectPr>${pageSetup(s.landscape)}</w:sectPr></w:body></w:document>"""
    }

    private fun pageBreak() =
        "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"

    private fun pageSetup(landscape: Boolean): String =
        if (landscape) {
            "<w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>" +
                "<w:pgMar w:top=\"850\" w:right=\"850\" w:bottom=\"850\" w:left=\"1134\"/>"
        } else {
            "<w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
                "<w:pgMar w:top=\"1134\" w:right=\"850\" w:bottom=\"850\" w:left=\"1418\"/>"
        }

    // --- служебные части архива ---

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""
}
