package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.model.LessonInput
import java.util.UUID

/**
 * Разбор уже написанного плана-конспекта в файле.
 *
 * Ориентируемся на метки стандартной формы: ТЕМА, ЗАНЯТИЕ, ЦЕЛИ, Время, Место,
 * Материальное обеспечение, части хода занятия. Чего в файле нет — остаётся пустым,
 * ничего не додумывается. Всё найденное ложится в правки, чтобы текст сохранился дословно.
 */
object LessonImport {

    data class Outcome(val lesson: LessonInput?, val note: String)

    private val partHeads = listOf(
        "вводная часть", "основная часть", "заключительная часть"
    )

    fun parse(text: String, fileName: String): Outcome {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Outcome(null, "$fileName — пустой файл")

        val whole = lines.joinToString("\n")
        val lower = whole.lowercase()

        val disciplineId = guessDiscipline(lower)
        val themeNo = after(lines, Regex("^ТЕМА\\s*№?\\s*([\\dA-Za-zА-Яа-я,\\.]*)\\s*:", RegexOption.IGNORE_CASE), 1)
        val topic = afterColon(lines, Regex("^ТЕМА\\b", RegexOption.IGNORE_CASE))
        val lessonNo = after(lines, Regex("^ЗАНЯТИЕ\\s*№?\\s*([\\dA-Za-zА-Яа-я,\\.]*)\\s*:", RegexOption.IGNORE_CASE), 1)
        val lessonTitle = afterColon(lines, Regex("^ЗАНЯТИЕ\\b", RegexOption.IGNORE_CASE))

        val place = afterColon(lines, Regex("^Мест[оа]\\b", RegexOption.IGNORE_CASE))
        val provision = afterColon(lines, Regex("^Материальн", RegexOption.IGNORE_CASE))
        val minutes = parseMinutes(afterColon(lines, Regex("^Врем[яени]+\\b", RegexOption.IGNORE_CASE)))

        val goals = block(lines, Regex("^ЦЕЛ[ИЬ]\\b", RegexOption.IGNORE_CASE))
        val questions = numbered(lines)

        val edits = HashMap<String, String>()
        if (goals.isNotEmpty()) edits[Generator.Keys.GOALS] = goals.joinToString("\n")
        if (provision.isNotBlank()) {
            edits[Generator.Keys.PROVISION] =
                provision.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }
        partBlock(lines, "вводная часть")?.let { edits[Generator.Keys.INTRO_CONTENT] = it }
        partBlock(lines, "заключительная часть")?.let { edits[Generator.Keys.OUTRO_CONTENT] = it }

        val effectiveTopic = topic.ifBlank { lessonTitle }.ifBlank {
            fileName.substringBeforeLast('.').replace('_', ' ')
        }

        val lesson = LessonInput(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            disciplineId = disciplineId,
            topic = effectiveTopic,
            themeNo = themeNo.ifBlank { "1" },
            lessonNo = lessonNo.ifBlank { "1" },
            lessonTitle = lessonTitle,
            minutes = minutes,
            place = place,
            method = "",
            unitName = "",
            date = "",
            leader = "",
            questionCount = questions.size.coerceIn(1, 6),
            customQuestions = questions,
            includeHandout = false,
            includeControl = true,
            includeSafety = whole.contains("безопасност", true),
            note = "",
            edits = edits
        )

        val found = buildList {
            if (topic.isNotBlank() || lessonTitle.isNotBlank()) add("тема")
            if (questions.isNotEmpty()) add("учебные вопросы: ${questions.size}")
            if (goals.isNotEmpty()) add("цели")
            if (place.isNotBlank()) add("место")
            if (minutes != 90) add("время")
        }
        val note = if (found.isEmpty()) {
            "$fileName — разметка не узнана, загружено как есть"
        } else {
            "$fileName — ${found.joinToString(", ")}"
        }
        return Outcome(lesson, note)
    }

    // --- разбор отдельных мест ---

    private fun guessDiscipline(lowerText: String): String {
        val head = lowerText.take(1200)
        Library.all.forEach { d ->
            val marks = listOfNotNull(
                d.dative.lowercase().ifBlank { null },
                d.name.lowercase(),
                d.short.lowercase()
            )
            if (marks.any { it.length > 4 && head.contains(it) }) return d.id
        }
        return "general"
    }

    private fun after(lines: List<String>, re: Regex, group: Int): String {
        lines.forEach { l ->
            val m = re.find(l)
            if (m != null) return m.groupValues.getOrElse(group) { "" }.trim().trim(':', '.')
        }
        return ""
    }

    private fun afterColon(lines: List<String>, head: Regex): String {
        lines.forEach { l ->
            if (head.containsMatchIn(l)) {
                val idx = l.indexOf(':')
                if (idx in 0 until l.length - 1) return l.substring(idx + 1).trim()
            }
        }
        return ""
    }

    /** Строки после заголовка до следующего заголовка формы. */
    private fun block(lines: List<String>, head: Regex): List<String> {
        val start = lines.indexOfFirst { head.containsMatchIn(it) }
        if (start < 0) return emptyList()
        val out = ArrayList<String>()
        val first = lines[start].substringAfter(':', "").trim()
        if (first.isNotEmpty()) out.add(first)
        var i = start + 1
        while (i < lines.size && out.size < 12) {
            val l = lines[i]
            if (isHead(l)) break
            out.add(l)
            i++
        }
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun partBlock(lines: List<String>, name: String): String? {
        val start = lines.indexOfFirst { it.lowercase().contains(name) }
        if (start < 0) return null
        val out = ArrayList<String>()
        var i = start + 1
        while (i < lines.size && out.size < 10) {
            val l = lines[i]
            if (partHeads.any { l.lowercase().contains(it) }) break
            if (l.length > 15) out.add(l)
            i++
        }
        return if (out.isEmpty()) null else out.joinToString("\n")
    }

    private fun isHead(line: String): Boolean {
        val l = line.lowercase()
        return l.startsWith("врем") || l.startsWith("мест") || l.startsWith("материальн") ||
            l.startsWith("ход занятия") || l.startsWith("тема") || l.startsWith("занятие") ||
            l.startsWith("руководств") || l.startsWith("требования безопасн") ||
            partHeads.any { l.startsWith(it) }
    }

    /** Учебные вопросы: строки вида «1. Наименование». */
    private fun numbered(lines: List<String>): List<String> {
        val re = Regex("^(\\d{1,2})[\\.\\)]\\s+(.{8,})$")
        val out = LinkedHashMap<Int, String>()
        lines.forEach { l ->
            val m = re.find(l) ?: return@forEach
            val n = m.groupValues[1].toIntOrNull() ?: return@forEach
            if (n !in 1..6) return@forEach
            val body = m.groupValues[2]
                .replace(Regex("\\s*[—-]\\s*\\d+\\s*мин\\.?$"), "")
                .trim()
            if (body.length in 8..200 && !out.containsKey(n)) out[n] = body
        }
        // Берём только сплошную нумерацию с первого вопроса.
        val result = ArrayList<String>()
        var n = 1
        while (out.containsKey(n) && n <= 6) {
            result.add(out.getValue(n)); n++
        }
        return result
    }

    private fun parseMinutes(value: String): Int {
        if (value.isBlank()) return 90
        val hours = Regex("(\\d+)\\s*час").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)\\s*мин").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 45 + mins
        return if (total in 15..480) total else 90
    }
}
