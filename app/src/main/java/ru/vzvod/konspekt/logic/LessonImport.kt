package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.model.LessonInput
import java.util.UUID

/**
 * Разбор уже написанного плана-конспекта из файла.
 *
 * Главное правило: ничего не терять. Весь текст хода занятия ложится в правки
 * дословно, вместе со временем частей, поэтому при сборке документ выглядит так же,
 * как в исходном файле. Шаблоны подставляются только туда, где в файле пусто.
 */
object LessonImport {

    data class Outcome(val lesson: LessonInput?, val note: String)

    private val introMark = Regex("вводная\\s+часть", RegexOption.IGNORE_CASE)
    private val mainMark = Regex("основная\\s+часть", RegexOption.IGNORE_CASE)
    private val outroMark = Regex("заключительная\\s+часть", RegexOption.IGNORE_CASE)
    private val runMark = Regex("^ход\\s+занятия", RegexOption.IGNORE_CASE)

    fun parse(text: String, fileName: String): Outcome {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Outcome(null, "$fileName — пустой файл")

        val whole = lines.joinToString("\n")
        val disciplineId = guessDiscipline(whole.take(1500).lowercase())

        val themeNo = numberAfter(lines, "ТЕМА")
        val topic = afterColon(lines, Regex("^ТЕМА\\b", RegexOption.IGNORE_CASE))
        val lessonNo = numberAfter(lines, "ЗАНЯТИЕ")
        val lessonTitle = afterColon(lines, Regex("^ЗАНЯТИЕ\\b", RegexOption.IGNORE_CASE))

        val place = afterColon(lines, Regex("^Мест[оа]\\b", RegexOption.IGNORE_CASE))
        val timeLine = afterColon(lines, Regex("^Врем[яени]+\\b", RegexOption.IGNORE_CASE))
        val provision = afterColon(lines, Regex("^Материальн", RegexOption.IGNORE_CASE))
        val goals = headedBlock(lines, Regex("^ЦЕЛ[ИЬ]\\b", RegexOption.IGNORE_CASE))
        val safety = headedBlock(lines, Regex("^Требования безопасн", RegexOption.IGNORE_CASE))

        val edits = HashMap<String, String>()
        if (goals.isNotEmpty()) edits[Generator.Keys.GOALS] = goals.joinToString("\n")
        if (safety.isNotEmpty()) edits[Generator.Keys.SAFETY] = safety.joinToString("\n")
        if (provision.isNotBlank()) {
            edits[Generator.Keys.PROVISION] = provision
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        }

        // Ход занятия: вводная, основная с вопросами, заключительная — дословно.
        val run = runSection(lines)
        run.intro?.let { edits[Generator.Keys.INTRO_CONTENT] = it.body }
        run.intro?.minutes?.let { edits[Generator.Keys.INTRO_MIN] = it.toString() }
        run.outro?.let { edits[Generator.Keys.OUTRO_CONTENT] = it.body }
        run.outro?.minutes?.let { edits[Generator.Keys.OUTRO_MIN] = it.toString() }

        val questions = ArrayList<String>()
        run.questions.forEachIndexed { i, q ->
            val n = i + 1
            questions.add(q.title)
            edits[Generator.Keys.qTitle(n)] = q.title
            if (q.body.isNotBlank()) edits[Generator.Keys.qContent(n)] = q.body
            q.minutes?.let { edits[Generator.Keys.qMinutes(n)] = it.toString() }
        }

        val total = parseMinutes(timeLine).takeIf { it > 0 }
            ?: run.totalMinutes().takeIf { it > 0 }
            ?: 90

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
            minutes = total,
            timeLabel = timeLine,
            place = place,
            method = "",
            unitName = "",
            date = "",
            leader = "",
            questionCount = questions.size.coerceIn(1, 6),
            customQuestions = questions,
            includeHandout = false,
            includeControl = false,
            includeSafety = safety.isNotEmpty(),
            note = "",
            edits = edits
        )

        return Outcome(lesson, note(fileName, topic, lessonTitle, questions, goals, run))
    }

    private fun note(
        fileName: String,
        topic: String,
        lessonTitle: String,
        questions: List<String>,
        goals: List<String>,
        run: Run
    ): String {
        val found = ArrayList<String>()
        if (topic.isNotBlank() || lessonTitle.isNotBlank()) found.add("тема")
        if (goals.isNotEmpty()) found.add("цели")
        if (questions.isNotEmpty()) found.add("вопросов: ${questions.size}")
        val chars = run.questions.sumOf { it.body.length }
        if (chars > 0) found.add("содержания: ${chars / 1000} тыс. знаков")
        return if (found.isEmpty()) "$fileName — разметка не узнана, текст сохранён целиком"
        else "$fileName — ${found.joinToString(", ")}"
    }

    // --- ход занятия ---

    private data class Part(val title: String, val body: String, val minutes: Int?)

    private data class Run(
        val intro: Part?,
        val questions: List<Part>,
        val outro: Part?
    ) {
        fun totalMinutes(): Int =
            (intro?.minutes ?: 0) + (outro?.minutes ?: 0) + questions.sumOf { it.minutes ?: 0 }
    }

    private fun runSection(lines: List<String>): Run {
        val start = lines.indexOfFirst { runMark.containsMatchIn(it) }
        val work = if (start >= 0) lines.drop(start + 1) else lines

        val iIntro = work.indexOfFirst { introMark.containsMatchIn(it) }
        val iMain = work.indexOfFirst { mainMark.containsMatchIn(it) }
        val iOutro = work.indexOfFirst { outroMark.containsMatchIn(it) }
        if (iIntro < 0 && iMain < 0) return Run(null, emptyList(), null)

        fun slice(from: Int, to: Int): List<String> {
            if (from < 0) return emptyList()
            val end = if (to > from) to else work.size
            return work.subList(from, end)
        }

        val introLines = slice(iIntro, if (iMain >= 0) iMain else iOutro)
        val mainLines = slice(iMain, iOutro)
        val outroLines = slice(iOutro, -1)

        val intro = introLines.takeIf { it.isNotEmpty() }?.let {
            Part("Вводная часть", strip(it, introMark).joinToString("\n"), minutesIn(it.take(3)))
        }
        val outro = outroLines.takeIf { it.isNotEmpty() }?.let {
            Part("Заключительная часть", strip(it, outroMark).joinToString("\n"), minutesIn(it.take(3)))
        }
        return Run(intro, questions(mainLines), outro)
    }

    /** Основная часть режется по нумерованным заголовкам вопросов. */
    private fun questions(mainLines: List<String>): List<Part> {
        if (mainLines.isEmpty()) return emptyList()
        val body = strip(mainLines, mainMark)
        val headRe = Regex("^(\\d{1,2})[\\.\\)]\\s*(.{6,200})$")

        val heads = ArrayList<Pair<Int, String>>()
        body.forEachIndexed { idx, line ->
            val m = headRe.find(line) ?: return@forEachIndexed
            val n = m.groupValues[1].toIntOrNull() ?: return@forEachIndexed
            if (n == heads.size + 1 && n <= 6) heads.add(idx to m.groupValues[2].trim())
        }

        // Нумерации нет — весь текст основной части идёт одним вопросом, ничего не теряя.
        if (heads.isEmpty()) {
            return listOf(
                Part(
                    "Основная часть",
                    body.joinToString("\n"),
                    minutesIn(mainLines.take(3))
                )
            )
        }

        return heads.mapIndexed { i, (at, rawTitle) ->
            val to = if (i + 1 < heads.size) heads[i + 1].first else body.size
            val chunk = body.subList(at + 1, to)
            val minutes = minutesIn(listOf(rawTitle))
            Part(
                title = rawTitle.replace(Regex("\\s*[—–-]\\s*\\d+\\s*мин\\.?\\s*$"), "").trim(),
                body = chunk.joinToString("\n"),
                minutes = minutes
            )
        }
    }

    /** Убирает строку-заголовок части, оставляя всё остальное как есть. */
    private fun strip(lines: List<String>, head: Regex): List<String> {
        val out = ArrayList<String>(lines)
        if (out.isNotEmpty() && head.containsMatchIn(out[0])) {
            val rest = out[0]
                .replace(head, "")
                .replace(Regex("\\d+\\s*мин\\.?"), "")
                .trim(' ', ':', '.', '—', '-')
            out.removeAt(0)
            if (rest.length > 3) out.add(0, rest)
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun minutesIn(lines: List<String>): Int? = lines
        .firstNotNullOfOrNull { Regex("(\\d{1,3})\\s*мин").find(it)?.groupValues?.get(1) }
        ?.toIntOrNull()
        ?.takeIf { it in 1..600 }

    // --- шапка ---

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

    /** Строки после заголовка до следующего заголовка формы. Без ограничения длины. */
    private fun headedBlock(lines: List<String>, head: Regex): List<String> {
        val start = lines.indexOfFirst { head.containsMatchIn(it) }
        if (start < 0) return emptyList()
        val out = ArrayList<String>()
        lines[start].substringAfter(':', "").trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
        var i = start + 1
        while (i < lines.size) {
            if (isHead(lines[i])) break
            out.add(lines[i])
            i++
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun isHead(line: String): Boolean {
        val l = line.lowercase()
        return l.startsWith("врем") || l.startsWith("мест") || l.startsWith("материальн") ||
            l.startsWith("ход занятия") || l.startsWith("тема") || l.startsWith("занятие") ||
            l.startsWith("руководств") || l.startsWith("требования безопасн") ||
            introMark.containsMatchIn(l) || mainMark.containsMatchIn(l) || outroMark.containsMatchIn(l)
    }

    private fun parseMinutes(value: String): Int {
        if (value.isBlank()) return 0
        val hours = Regex("(\\d+)\\s*час").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)\\s*мин").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 45 + mins
        return if (total in 15..600) total else 0
    }
}
