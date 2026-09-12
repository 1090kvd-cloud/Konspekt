package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.model.LessonInput
import java.util.UUID

/**
 * Разбор шапки уже написанного плана-конспекта.
 *
 * Сам документ не пересобирается: на печать уходит исходный файл со своей
 * вёрсткой, таблицами и рисунками. Отсюда нужно только то, по чему занятие
 * узнаётся в архиве: предмет, тема, номер занятия, время и место.
 * Ход занятия не разбирается намеренно — разобранный текст всё равно
 * не совпал бы с тем, что печатается, и только сбивал бы с толку.
 */
object LessonImport {

    data class Outcome(val lesson: LessonInput?, val note: String)

    fun parse(text: String, fileName: String): Outcome {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Outcome(null, "$fileName — пустой файл")

        val head = lines.take(60)
        val disciplineId = guessDiscipline(lines.joinToString(" ").take(1500).lowercase())

        val themeNo = numberAfter(head, "ТЕМА")
        val topic = afterColon(head, Regex("^ТЕМА\\b", RegexOption.IGNORE_CASE))
        val lessonNo = numberAfter(head, "ЗАНЯТИЕ")
        val lessonTitle = afterColon(head, Regex("^ЗАНЯТИЕ\\b", RegexOption.IGNORE_CASE))
        val place = afterColon(head, Regex("^Мест[оа]\\b", RegexOption.IGNORE_CASE))
        val timeLine = afterColon(head, Regex("^Врем[яени]+\\b", RegexOption.IGNORE_CASE))

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
            minutes = parseMinutes(timeLine),
            timeLabel = timeLine,
            place = place,
            questionCount = 1,
            includeHandout = false,
            includeControl = false,
            includeSafety = false
        )

        val found = buildList {
            if (topic.isNotBlank()) add("тема")
            if (lessonTitle.isNotBlank()) add("занятие")
            if (place.isNotBlank()) add("место")
            if (timeLine.isNotBlank()) add("время")
            if (disciplineId != "general") add("предмет")
        }
        val note = if (found.isEmpty()) {
            "$fileName — шапка не узнана, файл сохранён как есть"
        } else {
            "$fileName — ${found.joinToString(", ")}"
        }
        return Outcome(lesson, note)
    }

    private fun guessDiscipline(head: String): String {
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

    private fun numberAfter(lines: List<String>, word: String): String {
        val re = Regex("^$word\\s*№?\\s*([\\d,\\.\\-]+)\\s*:", RegexOption.IGNORE_CASE)
        lines.forEach { l ->
            re.find(l)?.let { return it.groupValues[1].trim().trim('.', ':') }
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

    private fun parseMinutes(value: String): Int {
        if (value.isBlank()) return 90
        val hours = Regex("(\\d+)\\s*час").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)\\s*мин").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 45 + mins
        return if (total in 15..600) total else 90
    }
}
