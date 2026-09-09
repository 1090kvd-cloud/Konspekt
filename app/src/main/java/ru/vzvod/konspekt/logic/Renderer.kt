package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.Settings

/** Превращает LessonPlan в текст для копирования и в HTML для печати/PDF. */
object Renderer {

    fun fileName(plan: LessonPlan): String {
        val safe = plan.input.topic.trim()
            .replace(Regex("[^\\p{L}\\p{N} \\-]"), "")
            .replace(' ', '_')
            .take(40)
            .ifBlank { "konspekt" }
        return "План-конспект_$safe"
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
        appendLine("проведения занятия по предмету «${plan.disciplineName}»")
        val unit = i.unitName.ifBlank { s.unitName }
        if (unit.isNotBlank()) appendLine("с личным составом: $unit")
        if (i.date.isNotBlank()) appendLine("Дата проведения: ${i.date}")
        appendLine()
        appendLine("Тема: ${i.topic}")
        if (i.lessonNo.isNotBlank()) appendLine("Занятие: ${i.lessonNo}")
        appendLine("Время: ${i.minutes} мин.")
        appendLine("Место проведения: ${plan.place}")
        appendLine("Метод проведения: ${plan.method}")
        appendLine()
        appendLine("УЧЕБНЫЕ ВОПРОСЫ:")
        plan.questions.forEach { appendLine("${it.index}. ${it.title} — ${it.minutes} мин.") }
        appendLine()
        appendLine("ЦЕЛИ ЗАНЯТИЯ:")
        appendLine("Учебные:")
        plan.eduGoals.forEach { appendLine("  — $it") }
        appendLine("Воспитательные:")
        plan.upGoals.forEach { appendLine("  — $it") }
        if (plan.metGoals.isNotEmpty()) {
            appendLine("Методические:")
            plan.metGoals.forEach { appendLine("  — $it") }
        }
        appendLine()
        appendLine("РУКОВОДСТВА И ПОСОБИЯ:")
        plan.references.forEach { appendLine("  — $it") }
        appendLine()
        appendLine("МАТЕРИАЛЬНОЕ ОБЕСПЕЧЕНИЕ:")
        plan.materials.forEach { appendLine("  — $it") }
        if (plan.safety.isNotEmpty()) {
            appendLine()
            appendLine("ТРЕБОВАНИЯ БЕЗОПАСНОСТИ:")
            plan.safety.forEach { appendLine("  — $it") }
        }
        appendLine()
        appendLine("=".repeat(52))
        appendLine("ХОД ЗАНЯТИЯ")
        appendLine("=".repeat(52))
        appendLine()
        appendLine("I. ${plan.intro.title.uppercase()} — ${plan.intro.minutes} мин.")
        appendLine()
        appendLine("Действия руководителя:")
        plan.intro.leader.forEach { appendLine("  • $it") }
        appendLine("Действия обучаемых:")
        plan.intro.trainee.forEach { appendLine("  • $it") }
        appendLine()
        appendLine("II. ОСНОВНАЯ ЧАСТЬ — ${plan.mainMinutes} мин.")
        plan.questions.forEach { q ->
            appendLine()
            appendLine("Учебный вопрос № ${q.index}. ${q.title} — ${q.minutes} мин.")
            appendLine("Действия руководителя:")
            q.leader.forEach { appendLine("  • $it") }
            appendLine("Действия обучаемых:")
            q.trainee.forEach { appendLine("  • $it") }
        }
        appendLine()
        appendLine("III. ${plan.outro.title.uppercase()} — ${plan.outro.minutes} мин.")
        appendLine()
        appendLine("Действия руководителя:")
        plan.outro.leader.forEach { appendLine("  • $it") }
        appendLine("Действия обучаемых:")
        plan.outro.trainee.forEach { appendLine("  • $it") }
        if (i.note.isNotBlank()) {
            appendLine()
            appendLine("ПРИМЕЧАНИЯ РУКОВОДИТЕЛЯ:")
            appendLine(i.note)
        }
        appendLine()
        appendLine("Руководитель занятия: ${i.leader.ifBlank { s.leader }.ifBlank { "____________________________" }}")
        appendLine("«____» ______________ 20____ г.        _______________")
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

    fun everything(plan: LessonPlan, s: Settings): String = buildString {
        append(planText(plan, s))
        if (plan.handout.isNotEmpty()) {
            appendLine(); appendLine(); append(handoutText(plan, s))
        }
        if (plan.control.isNotEmpty()) {
            appendLine(); appendLine(); append(controlText(plan))
        }
    }

    // ---------- HTML для печати / PDF ----------

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun ul(items: List<String>) =
        if (items.isEmpty()) "" else items.joinToString("", "<ul>", "</ul>") { "<li>${esc(it)}</li>" }

    fun html(plan: LessonPlan, s: Settings): String {
        val i = plan.input
        val unit = i.unitName.ifBlank { s.unitName }
        val sb = StringBuilder()
        sb.append(
            """
            <html><head><meta charset="utf-8"><style>
            body{font-family:'Times New Roman',serif;font-size:12pt;line-height:1.35;color:#000;margin:18px}
            h1{font-size:14pt;text-align:center;margin:6px 0}
            h2{font-size:12.5pt;border-bottom:1.5pt solid #000;padding-bottom:2px;margin:16px 0 6px}
            h3{font-size:12pt;margin:12px 0 4px}
            .app{text-align:right;white-space:pre-line;margin-bottom:14px}
            .meta p{margin:2px 0}
            ul{margin:2px 0 6px 0;padding-left:20px}
            li{margin:1px 0}
            .sign{margin-top:26px}
            .lbl{font-style:italic;margin:6px 0 0}
            .page{page-break-before:always}
            table{width:100%;border-collapse:collapse;margin-top:4px}
            td{border:0.8pt solid #000;vertical-align:top;padding:4px 6px;width:50%}
            th{border:0.8pt solid #000;padding:4px 6px;font-size:11pt}
            .fill{border-bottom:0.8pt solid #666;display:inline-block;min-width:60%}
            </style></head><body>
            """.trimIndent()
        )
        sb.append("<div class=\"app\">УТВЕРЖДАЮ\n${esc(s.approver.ifBlank { "Командир роты" })}\n____________________\n«___» __________ 20__ г.</div>")
        sb.append("<h1>ПЛАН-КОНСПЕКТ<br>проведения занятия по предмету «${esc(plan.disciplineName)}»</h1>")
        sb.append("<div class=\"meta\">")
        if (unit.isNotBlank()) sb.append("<p><b>Подразделение:</b> ${esc(unit)}</p>")
        sb.append("<p><b>Тема:</b> ${esc(i.topic)}</p>")
        if (i.lessonNo.isNotBlank()) sb.append("<p><b>Занятие:</b> ${esc(i.lessonNo)}</p>")
        if (i.date.isNotBlank()) sb.append("<p><b>Дата:</b> ${esc(i.date)}</p>")
        sb.append("<p><b>Время:</b> ${i.minutes} мин.</p>")
        sb.append("<p><b>Место проведения:</b> ${esc(plan.place)}</p>")
        sb.append("<p><b>Метод проведения:</b> ${esc(plan.method)}</p>")
        sb.append("</div>")

        sb.append("<h2>Учебные вопросы</h2><ol>")
        plan.questions.forEach { sb.append("<li>${esc(it.title)} — ${it.minutes} мин.</li>") }
        sb.append("</ol>")

        sb.append("<h2>Цели занятия</h2>")
        sb.append("<p class=\"lbl\">Учебные:</p>").append(ul(plan.eduGoals))
        sb.append("<p class=\"lbl\">Воспитательные:</p>").append(ul(plan.upGoals))
        if (plan.metGoals.isNotEmpty()) sb.append("<p class=\"lbl\">Методические:</p>").append(ul(plan.metGoals))

        sb.append("<h2>Руководства и пособия</h2>").append(ul(plan.references))
        sb.append("<h2>Материальное обеспечение</h2>").append(ul(plan.materials))
        if (plan.safety.isNotEmpty()) sb.append("<h2>Требования безопасности</h2>").append(ul(plan.safety))

        sb.append("<h2>Ход занятия</h2>")
        fun stage(title: String, minutes: Int, leader: List<String>, trainee: List<String>) {
            sb.append("<h3>$title — $minutes мин.</h3>")
            sb.append("<table><tr><th>Действия руководителя занятия</th><th>Действия обучаемых</th></tr>")
            sb.append("<tr><td>${ul(leader)}</td><td>${ul(trainee)}</td></tr></table>")
        }
        stage("I. ${plan.intro.title}", plan.intro.minutes, plan.intro.leader, plan.intro.trainee)
        sb.append("<h3>II. Основная часть — ${plan.mainMinutes} мин.</h3>")
        plan.questions.forEach { q ->
            stage("Учебный вопрос № ${q.index}. ${esc(q.title)}", q.minutes, q.leader, q.trainee)
        }
        stage("III. ${plan.outro.title}", plan.outro.minutes, plan.outro.leader, plan.outro.trainee)

        if (i.note.isNotBlank()) sb.append("<h2>Примечания руководителя</h2><p>${esc(i.note)}</p>")

        sb.append("<div class=\"sign\"><p>Руководитель занятия: ${esc(i.leader.ifBlank { s.leader })} <span class=\"fill\"></span></p>")
        sb.append("<p>«___» __________ 20__ г.</p></div>")

        if (plan.handout.isNotEmpty()) {
            sb.append("<div class=\"page\"></div><h1>РАЗДАТОЧНЫЙ МАТЕРИАЛ</h1>")
            sb.append("<p><b>Тема:</b> ${esc(i.topic)}</p>")
            plan.handout.forEach { b ->
                sb.append("<h3>${esc(b.title)}</h3>").append(ul(b.items))
            }
        }
        if (plan.control.isNotEmpty()) {
            sb.append("<div class=\"page\"></div><h1>КОНТРОЛЬНЫЕ ВОПРОСЫ</h1>")
            sb.append("<p><b>Тема:</b> ${esc(i.topic)}</p><ol>")
            plan.control.forEach { sb.append("<li>${esc(it)}</li>") }
            sb.append("</ol>")
            sb.append("<p><b>Критерии оценки.</b> «5» — полный правильный ответ; «4» — несущественные неточности; «3» — ответ после наводящих вопросов; «2» — материал не усвоен.</p>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}
