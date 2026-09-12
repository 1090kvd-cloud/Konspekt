package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.Settings

/**
 * Вывод документа по форме образца:
 * шапка с грифом утверждения, ТЕМА и ЗАНЯТИЕ прописными, цели нумерованным списком,
 * ход занятия таблицей из четырёх граф.
 */
object Renderer {

    fun fileName(plan: LessonPlan): String {
        val safe = plan.input.topic.trim()
            .replace(Regex("[^\\p{L}\\p{N} \\-]"), "")
            .replace(' ', '_')
            .take(40)
            .ifBlank { "konspekt" }
        return "План-конспект_$safe"
    }

    /** Время в шапке: своя запись руководителя, иначе — расчётная. */
    fun timeOf(plan: LessonPlan): String =
        plan.input.timeLabel.ifBlank { Generator.timeText(plan.input.minutes) }

    private fun lessonTitle(plan: LessonPlan): String {
        val own = plan.input.lessonTitle.trim()
        if (own.isNotEmpty()) return own
        return plan.questions.joinToString(" ") { it.title.trimEnd('.') + "." }
    }

    // ---------- ТЕКСТ ----------

    fun planText(plan: LessonPlan, s: Settings): String = buildString {
        val i = plan.input
        appendLine("УТВЕРЖДАЮ")
        appendLine(s.approver.ifBlank { "Командир роты" })
        appendLine("____________________________")
        appendLine("«____» ______________ 20____ г.")
        appendLine()
        appendLine("ПЛАН-КОНСПЕКТ")
        appendLine("проведения занятия по ${plan.dative}")
        val unit = i.unitName.ifBlank { s.unitName }
        if (unit.isNotBlank()) appendLine("с $unit")
        appendLine()
        appendLine("ТЕМА ${i.themeNo}: ${i.topic.uppercase()}")
        appendLine("ЗАНЯТИЕ ${i.lessonNo}: ${lessonTitle(plan).uppercase()}")
        appendLine()
        appendLine("ЦЕЛИ:")
        plan.goals.forEachIndexed { k, g -> appendLine("${k + 1}. $g") }
        appendLine()
        appendLine("Время: ${timeOf(plan)};    «____» ____________ 20____ г.")
        appendLine("Место: ${plan.place}.")
        appendLine("Материальное обеспечение: ${plan.provision.joinToString(", ")}.")
        if (plan.safety.isNotEmpty()) {
            appendLine()
            appendLine("Требования безопасности:")
            plan.safety.forEach { appendLine("  — $it") }
        }
        appendLine()
        appendLine("ХОД ЗАНЯТИЯ")
        appendLine("=".repeat(52))

        fun block(no: String, title: String, minutes: Int, content: List<String>, trainee: List<String>) {
            appendLine()
            appendLine("$no. $title — $minutes мин.")
            appendLine("Содержание учебных вопросов:")
            content.forEach { appendLine("  • $it") }
            appendLine("Действия обучаемых:")
            trainee.forEach { appendLine("  • $it") }
        }

        block("1", plan.intro.title, plan.intro.minutes, plan.intro.content, plan.intro.trainee)
        appendLine()
        appendLine("2. Основная часть — ${plan.mainMinutes} мин.")
        plan.questions.forEach { q ->
            appendLine()
            appendLine("  ${q.index}. ${q.title} — ${q.minutes} мин.")
            appendLine("  Содержание учебных вопросов:")
            q.content.forEach { appendLine("    • $it") }
            appendLine("  Действия обучаемых:")
            q.trainee.forEach { appendLine("    • $it") }
        }
        block("3", plan.outro.title, plan.outro.minutes, plan.outro.content, plan.outro.trainee)

        if (i.note.isNotBlank()) {
            appendLine()
            appendLine("ПРИМЕЧАНИЯ РУКОВОДИТЕЛЯ:")
            appendLine(i.note)
        }
        appendLine()
        appendLine()
        appendLine("Руководитель занятия")
        appendLine(i.leader.ifBlank { s.leader })
        appendLine("____________________________")
    }

    fun handoutText(plan: LessonPlan, s: Settings): String = buildString {
        appendLine("РАЗДАТОЧНЫЙ МАТЕРИАЛ")
        appendLine("Предмет: ${plan.disciplineName}")
        appendLine("Тема: ${plan.input.topic}")
        val unit = plan.input.unitName.ifBlank { s.unitName }
        if (unit.isNotBlank()) appendLine("Подразделение: $unit")
        appendLine("-".repeat(52))
        plan.handout.forEach { b ->
            appendLine()
            appendLine(b.title.uppercase())
            b.items.forEach { appendLine("  $it") }
        }
    }

    fun controlText(plan: LessonPlan): String = buildString {
        appendLine("КОНТРОЛЬНЫЕ ВОПРОСЫ")
        appendLine("Тема: ${plan.input.topic}")
        appendLine("-".repeat(52))
        plan.control.forEachIndexed { idx, q -> appendLine("${idx + 1}. $q") }
        appendLine()
        appendLine("Оценка: «5» — полный, правильный ответ на все вопросы;")
        appendLine("«4» — ответ с несущественными неточностями;")
        appendLine("«3» — ответ на большинство вопросов после наводящих;")
        appendLine("«2» — материал не усвоен.")
    }


    /** «по огневой подготовке», «по тактической подготовке» — родительный падеж названия предмета. */
    fun disciplineCase(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith("ая подготовка") -> lower.dropLast("ая подготовка".length) + "ой подготовке"
            lower.endsWith("подготовка") -> lower.dropLast("подготовка".length) + "подготовке"
            lower.endsWith("уставы") -> "общевоинским уставам"
            lower.endsWith("защита") -> lower.dropLast("защита".length) + "защите"
            lower.endsWith("топография") -> lower.dropLast("топография".length) + "топографии"
            else -> lower
        }
    }

    // ---------- HTML для печати / PDF ----------

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Каталог рисунков задаётся перед печатью: без него метки станут словом «рисунок». */
    var imageDir: java.io.File? = null

    private fun img(name: String): String {
        val dir = imageDir ?: return "<li>[рисунок]</li>"
        val f = java.io.File(dir, name)
        if (!f.exists()) return "<li>[рисунок]</li>"
        return runCatching {
            val bytes = f.readBytes()
            if (bytes.size > 4_000_000) return "<li>[рисунок]</li>"
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val ext = name.substringAfterLast('.', "png").lowercase()
            val mime = if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/$ext"
            "<li style=\"list-style:none;margin-left:-16px\">" +
                "<img src=\"data:$mime;base64,$b64\" style=\"max-width:98%;height:auto\"></li>"
        }.getOrElse { "<li>[рисунок]</li>" }
    }

    private fun ul(items: List<String>) =
        if (items.isEmpty()) "" else items.joinToString("", "<ul>", "</ul>") { item ->
            val name = DocxImages.nameIn(item)
            if (name != null) img(name) else "<li>${esc(item)}</li>"
        }

    fun html(plan: LessonPlan, s: Settings): String {
        val i = plan.input
        val land = s.landscape
        val unit = i.unitName.ifBlank { s.unitName }
        val sb = StringBuilder()

        // Word берёт кодировку из http-equiv; без него кириллица превращается в иероглифы.
        sb.append("<html><head>")
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">")
        sb.append("<meta charset=\"utf-8\"><style>")
        sb.append("@page{size:A4 " + (if (land) "landscape" else "portrait") + ";margin:" + (if (land) "12mm" else "20mm 15mm 15mm 25mm") + "}")
        sb.append("body{font-family:'Times New Roman',serif;font-size:12pt;line-height:1.3;color:#000;margin:0;text-align:justify}")
        sb.append(".approve{margin-left:55%;white-space:pre-line;margin-bottom:18px}")
        sb.append(".line{border-bottom:0.8pt solid #000;height:14px;margin:6px 0}")
        sb.append("h1{font-size:12pt;text-align:center;font-weight:bold;margin:10px 0 2px}")
        sb.append(".sub{text-align:center;margin:0 0 14px}")
        sb.append(".key{font-weight:bold}")
        sb.append("p{margin:3px 0}")
        sb.append(".theme{text-transform:uppercase;text-indent:1.25cm}")
        sb.append(".run{text-align:center;font-weight:bold;margin:16px 0 6px}")
        sb.append("table{width:100%;border-collapse:collapse}")
        sb.append("th,td{border:0.8pt solid #000;padding:4px 6px;vertical-align:top;text-align:left}")
        sb.append("th{text-align:center;font-weight:normal}")
        sb.append("thead{display:table-header-group}tr{page-break-inside:avoid}")
        sb.append("ul{margin:2px 0;padding-left:16px}li{margin:1px 0}")
        sb.append(".q{font-weight:bold;margin-top:8px}")
        sb.append(".sign{margin-top:34px;text-align:center}")
        sb.append(".page{page-break-before:always}")
        sb.append("</style></head><body>")

        sb.append("<div class=\"approve\">УТВЕРЖДАЮ\n${esc(s.approver.ifBlank { "Командир роты" })}")
        sb.append("<div class=\"line\"></div><div class=\"line\"></div>")
        sb.append("«___» ____________ 20___ г.</div>")

        sb.append("<h1>ПЛАН-КОНСПЕКТ</h1>")
        sb.append("<div class=\"sub\">проведения занятия по ${esc(plan.dative)}")
        if (unit.isNotBlank()) sb.append("<br>с ${esc(unit)}")
        sb.append("</div>")

        sb.append("<p class=\"theme\"><span class=\"key\">ТЕМА ${esc(i.themeNo)}:</span> ${esc(i.topic)}</p>")
        sb.append("<p class=\"theme\"><span class=\"key\">ЗАНЯТИЕ ${esc(i.lessonNo)}:</span> ${esc(lessonTitle(plan))}</p>")

        sb.append("<p class=\"key\" style=\"text-indent:1.25cm\">ЦЕЛИ:</p>")
        plan.goals.forEachIndexed { k, g ->
            sb.append("<p style=\"text-indent:1.25cm\">${k + 1}. ${esc(g)}</p>")
        }

        sb.append("<p style=\"text-indent:1.25cm\"><span class=\"key\">Время:</span> ${timeOf(plan)};&nbsp;&nbsp;&nbsp;&nbsp;«___» __________ 20___ г.</p>")
        sb.append("<p style=\"text-indent:1.25cm\"><span class=\"key\">Место:</span> ${esc(plan.place)}.</p>")
        sb.append("<p style=\"text-indent:1.25cm\"><span class=\"key\">Материальное обеспечение:</span> ${esc(plan.provision.joinToString(", "))}.</p>")
        if (plan.safety.isNotEmpty()) {
            sb.append("<p style=\"text-indent:1.25cm\"><span class=\"key\">Требования безопасности:</span></p>")
            sb.append(ul(plan.safety))
        }

        sb.append("<div class=\"run\">ХОД ЗАНЯТИЯ</div>")
        sb.append("<table><thead><tr>")
        sb.append("<th style=\"width:5%\">№<br>п/п</th>")
        sb.append("<th style=\"width:14%\">Учебные вопросы</th>")
        sb.append("<th style=\"width:66%\">Содержание учебных вопросов</th>")
        sb.append("<th style=\"width:15%\">Действия обучаемых</th>")
        sb.append("</tr></thead><tbody>")

        sb.append("<tr><td>1.</td><td>${esc(plan.intro.title)}<br>${plan.intro.minutes} мин.</td>")
        sb.append("<td>${ul(plan.intro.content)}</td><td>${ul(plan.intro.trainee)}</td></tr>")

        val mainContent = StringBuilder()
        val mainTrainee = StringBuilder()
        plan.questions.forEach { q ->
            mainContent.append("<div class=\"q\">${q.index}. ${esc(q.title)} — ${q.minutes} мин.</div>")
            mainContent.append(ul(q.content))
            mainTrainee.append(ul(q.trainee))
        }
        sb.append("<tr><td>2.</td><td>Основная часть<br>${plan.mainMinutes} мин.</td>")
        sb.append("<td>$mainContent</td><td>$mainTrainee</td></tr>")

        sb.append("<tr><td>3.</td><td>${esc(plan.outro.title)}<br>${plan.outro.minutes} мин.</td>")
        sb.append("<td>${ul(plan.outro.content)}</td><td>${ul(plan.outro.trainee)}</td></tr>")
        sb.append("</tbody></table>")

        if (i.note.isNotBlank()) {
            sb.append("<p style=\"margin-top:10px\"><span class=\"key\">Примечания руководителя:</span> ${esc(i.note)}</p>")
        }

        sb.append("<div class=\"sign\">Руководитель занятия<br>${esc(i.leader.ifBlank { s.leader })}")
        sb.append("<div class=\"line\" style=\"width:60%;margin:14px auto\"></div></div>")

        if (plan.handout.isNotEmpty()) {
            sb.append("<div class=\"page\"></div><h1>РАЗДАТОЧНЫЙ МАТЕРИАЛ</h1>")
            sb.append("<p><span class=\"key\">Тема:</span> ${esc(i.topic)}</p>")
            plan.handout.forEach { b ->
                sb.append("<p class=\"key\" style=\"margin-top:10px\">${esc(b.title)}</p>").append(ul(b.items))
            }
        }
        if (plan.control.isNotEmpty()) {
            sb.append("<div class=\"page\"></div><h1>КОНТРОЛЬНЫЕ ВОПРОСЫ</h1>")
            sb.append("<p><span class=\"key\">Тема:</span> ${esc(i.topic)}</p><ol>")
            plan.control.forEach { sb.append("<li>${esc(it)}</li>") }
            sb.append("</ol>")
            sb.append("<p><span class=\"key\">Критерии оценки.</span> «5» — полный правильный ответ; «4» — несущественные неточности; «3» — ответ после наводящих вопросов; «2» — материал не усвоен.</p>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}
