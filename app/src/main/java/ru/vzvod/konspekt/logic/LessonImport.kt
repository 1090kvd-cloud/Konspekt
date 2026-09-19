package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.model.LessonInput
import java.util.UUID

/**
 * Разбор уже написанного плана-конспекта из файла.
 *
 * Правило одно: ничего не терять. Весь текст хода занятия переносится дословно,
 * вместе со временем частей, поэтому пересобранный документ содержит то же,
 * что было в файле. Шаблонные формулировки подставляются только туда,
 * где в исходнике ничего нет.
 */
object LessonImport {

    data class Outcome(val lesson: LessonInput?, val note: String)

    private val introMark = Regex("вводная\\s+часть", RegexOption.IGNORE_CASE)
    private val mainMark = Regex("основная\\s+часть", RegexOption.IGNORE_CASE)
    private val outroMark = Regex("заключительная\\s+часть", RegexOption.IGNORE_CASE)
    private val runMark = Regex("^ход\\s+занятия", RegexOption.IGNORE_CASE)
    private val signMark = Regex("^руководител[ья]\\s+занятия", RegexOption.IGNORE_CASE)

    /** Строки таблицы, которые не являются содержанием: время, подпись, линейки. */
    private val noise = listOf(
        Regex("^\\d{1,3}\\s*мин\\.?$"),
        Regex("^[_\\s]+$"),
        Regex("^«___».*20___.*$")
    )

    /** Действия обучаемых: в таблице это соседняя графа, её нельзя мешать с содержанием. */
    private val traineeStarts = listOf(
        "слушают", "строятся", "отвечают", "выполняют", "докладывают", "переводят",
        "задают", "наблюдают", "сдают", "записывают", "конспектируют", "повторяют",
        "устраняют", "самостоятельно выполняют", "прием доклада", "приём доклада",
        "проверка готовности"
    )

    /** Метка рисунка — не шум: она держит место схемы в тексте. */
    private fun isPicture(line: String) = DocxImages.hasMarker(line)

    private fun isNoise(line: String) = noise.any { it.matches(line.trim()) } ||
        signMark.containsMatchIn(line)

    private fun isTrainee(line: String): Boolean {
        val l = line.trim().lowercase()
        return traineeStarts.any { l.startsWith(it) }
    }

    /** Делит плоский текст графы на содержание и действия обучаемых. */
    private fun divide(lines: List<String>): Pair<String, String> {
        val content = ArrayList<String>()
        val trainee = ArrayList<String>()
        lines.forEach { l ->
            when {
                isPicture(l) -> content.add(l)
                isNoise(l) -> Unit
                isTrainee(l) -> if (!trainee.contains(l)) trainee.add(l)
                else -> content.add(l)
            }
        }
        return content.joinToString("\n") to trainee.joinToString("\n")
    }

    fun parse(text: String, fileName: String): Outcome {
        val table = tableRows(text)
        val lines = TextExtract.withoutTableMarks(text)
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Outcome(null, "$fileName — пустой файл")

        val head = lines.take(60)
        val disciplineId = guessDiscipline(lines.joinToString(" ").take(1500).lowercase())

        val themeNo = numberAfter(head, "ТЕМА")
        val topic = afterColon(head, Regex("^ТЕМА\\b", RegexOption.IGNORE_CASE))
        val lessonNo = numberAfter(head, "ЗАНЯТИЕ")
        val lessonTitle = afterColon(head, Regex("^ЗАНЯТИЕ\\b", RegexOption.IGNORE_CASE))
        val place = afterColon(head, Regex("^Мест[оа]\\b", RegexOption.IGNORE_CASE))
        val timeLine = afterColon(head, Regex("^Врем[яени]+\\b", RegexOption.IGNORE_CASE))
        val provision = afterColon(head, Regex("^Материальн", RegexOption.IGNORE_CASE))
        // Руководства и пособия в форме идут отдельной строкой, но в документе
        // приложения это один раздел — материальное обеспечение.
        val guides = afterColon(head, Regex("^Руководств", RegexOption.IGNORE_CASE))
        val goals = headedBlock(lines, Regex("^ЦЕЛ[ИЬ]\\b", RegexOption.IGNORE_CASE))
        val safety = headedBlock(lines, Regex("^Требования безопасн", RegexOption.IGNORE_CASE))

        val edits = HashMap<String, String>()
        if (goals.isNotEmpty()) {
            edits[Generator.Keys.GOALS] = goals.joinToString("\n") { unnumber(it) }
        }
        if (safety.isNotEmpty()) edits[Generator.Keys.SAFETY] = safety.joinToString("\n")
        val allProvision = listOf(provision, guides).filter { it.isNotBlank() }.joinToString("; ")
        if (allProvision.isNotBlank()) {
            edits[Generator.Keys.PROVISION] = allProvision
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        }

        // Если ход занятия свёрстан таблицей, берём его по ячейкам:
        // содержание и действия обучаемых лежат в разных колонках.
        val run = tableRun(table) ?: runSection(lines)
        run.intro?.let {
            edits[Generator.Keys.INTRO_CONTENT] = it.body
            if (it.trainee.isNotBlank()) edits[Generator.Keys.INTRO_TRAINEE] = it.trainee
            it.minutes?.let { m -> edits[Generator.Keys.INTRO_MIN] = m.toString() }
        }
        run.outro?.let {
            edits[Generator.Keys.OUTRO_CONTENT] = it.body
            if (it.trainee.isNotBlank()) edits[Generator.Keys.OUTRO_TRAINEE] = it.trainee
            it.minutes?.let { m -> edits[Generator.Keys.OUTRO_MIN] = m.toString() }
        }

        val questions = ArrayList<String>()
        run.questions.forEachIndexed { i, q ->
            val n = i + 1
            questions.add(q.title)
            edits[Generator.Keys.qTitle(n)] = q.title
            if (q.body.isNotBlank()) edits[Generator.Keys.qContent(n)] = q.body
            if (q.trainee.isNotBlank()) edits[Generator.Keys.qTrainee(n)] = q.trainee
            q.minutes?.let { edits[Generator.Keys.qMinutes(n)] = it.toString() }
        }

        // Время частей надёжнее строки в шапке: в ней часто стоит «1 час» на глазок.
        val total = run.totalMinutes().takeIf { it > 0 }
            ?: parseMinutes(timeLine).takeIf { it > 0 }
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
            questionCount = questions.size.coerceIn(1, 6),
            customQuestions = questions,
            includeHandout = false,
            includeControl = false,
            includeSafety = safety.isNotEmpty(),
            edits = edits
        )

        val found = buildList {
            if (topic.isNotBlank()) add("тема")
            if (lessonTitle.isNotBlank()) add("занятие")
            if (goals.isNotEmpty()) add("цели")
            if (questions.isNotEmpty()) add("вопросов: ${questions.size}")
            val chars = run.questions.sumOf { it.body.length }
            if (chars > 0) add("содержания: ${chars / 1000} тыс. знаков")
        }
        val note = if (found.isEmpty()) "$fileName — разметка не узнана"
        else "$fileName — ${found.joinToString(", ")}"
        return Outcome(lesson, note)
    }

    /** «1. Научить…» -> «Научить…»: номер проставит документ, иначе выйдет «1. 1.». */
    private fun unnumber(line: String): String =
        line.replace(Regex("^\\d{1,2}[\\.\\)]\\s*"), "").trim()

    // --- ход занятия по ячейкам таблицы ---

    private fun tableRows(text: String): List<List<String>> {
        if (!text.contains(TextExtract.ROW)) return emptyList()
        return text.split(TextExtract.ROW)
            .map { row ->
                row.split(TextExtract.CELL)
                    .map { cell -> cell.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") }
                    .filter { it.isNotEmpty() || true }
            }
            .filter { it.any { cell -> cell.isNotBlank() } }
    }

    /**
     * Ожидаемая вёрстка: № | учебные вопросы и время | содержание | действия обучаемых.
     *
     * Внутри ячеек попадаются вложенные таблицы — например перечень огневых задач.
     * Поэтому границы частей ищем по словам «вводная», «основная», «заключительная»,
     * а всё между ними присоединяем к текущей части, а не считаем новым вопросом.
     */
    /**
     * Назначение граф определяется по шапке таблицы, а не по их порядку.
     * Формы различаются: где-то «№ | вопросы | содержание | действия обучаемых»,
     * где-то «№ | вопросы и содержание | время | действия руководителя».
     */
    private data class Columns(val head: Int, val body: Int, val trainee: Int, val minutes: Int)

    private fun columnsOf(header: List<String>): Columns {
        var head = 1
        var body = 2
        var trainee = 3
        var minutes = -1
        header.forEachIndexed { i, cell ->
            val c = cell.lowercase()
            when {
                c.contains("врем") -> minutes = i
                c.contains("действия") -> trainee = i
                c.contains("содержан") && c.contains("вопрос") -> { head = i; body = i }
                c.contains("содержан") -> body = i
                c.contains("вопрос") -> head = i
            }
        }
        return Columns(head, body, trainee, minutes)
    }

    private fun tableRun(rows: List<List<String>>): Run? {
        if (rows.size < 2) return null
        val cols = columnsOf(rows.first())

        data class Acc(var head: String, val body: StringBuilder, val trainee: StringBuilder, var minutes: Int?)

        var current: Acc? = null
        var stage = ""
        var intro: Part? = null
        var outro: Part? = null
        val mains = ArrayList<Acc>()

        fun flush() {
            val acc = current ?: return
            var content = acc.body.toString().trim()
                // «ОСНОВНАЯ ЧАСТЬ» в начале — это название графы, а не содержание.
                .replace(Regex("^(вводная|основная|заключительная)\\s+часть:?\\s*",
                    RegexOption.IGNORE_CASE), "")
                .trim()
            var trainee = acc.trainee.toString().trim()
            // В части форм графа «Действия руководителя» несёт и само содержание,
            // а в графе вопросов стоит только название части. Тогда меняем местами,
            // иначе текст занятия ушёл бы в действия обучаемых и потерялся.
            if (content.length < 40 && trainee.length > content.length + 20) {
                content = trainee
                trainee = ""
            }
            val part = Part(acc.head, content, acc.minutes, trainee)
            when (stage) {
                "intro" -> if (intro == null) intro = part
                "outro" -> outro = part
                else -> mains.add(acc)
            }
            current = null
        }

        rows.forEach { cells ->
            if (cells.size < 3) return@forEach
            val head = cells.getOrElse(cols.head) { "" }
            val body = cells.getOrElse(cols.body) { "" }
            val trainee = cells.getOrElse(cols.trainee) { "" }
            val timeCell = if (cols.minutes >= 0) cells.getOrElse(cols.minutes) { "" } else ""
            if (head.contains("учебные вопросы", true) && body.contains("содержание", true)) return@forEach

            val mark = (head + " " + body.lineSequence().firstOrNull().orEmpty()).lowercase()
            val newStage = when {
                introMark.containsMatchIn(mark) -> "intro"
                outroMark.containsMatchIn(mark) -> "outro"
                mainMark.containsMatchIn(mark) -> "main"
                else -> null
            }
            val clean = body.lines()
                .filterNot { Regex("^\\d{1,3}\\s*мин\\.?$").matches(it.trim()) }
                .joinToString("\n")
                .trim()

            if (newStage != null) {
                flush()
                stage = newStage
                current = Acc(
                    head = when (newStage) {
                        "intro" -> "Вводная часть"
                        "outro" -> "Заключительная часть"
                        else -> ""
                    },
                    body = StringBuilder(clean),
                    trainee = StringBuilder(trainee),
                    // Время может стоять своей графой — тогда берём его оттуда.
                    minutes = minutesIn(listOf(timeCell)) ?: number(timeCell)
                        ?: minutesIn(head.lines() + body.lines().take(2))
                )
            } else {
                // Заключительная часть коротка по смыслу. Всё, что идёт дальше, —
                // это уже другая таблица документа: перечень документов, ведомости.
                // Присоединять их к ходу занятия нельзя.
                if (stage == "outro") return@forEach
                // Продолжение текущей части: вложенная таблица, перенос строки и т. п.
                val acc = current ?: return@forEach
                if (clean.isNotBlank()) {
                    if (acc.body.isNotEmpty()) acc.body.append('\n')
                    acc.body.append(clean)
                }
                if (trainee.isNotBlank()) {
                    if (acc.trainee.isNotEmpty()) acc.trainee.append('\n')
                    acc.trainee.append(trainee)
                }
            }
        }
        flush()

        if (intro == null && mains.isEmpty()) return null

        val questions = mains.map { acc ->
            val first = acc.body.lineSequence().firstOrNull()?.trim().orEmpty()
            val isTitle = first.length in 10..200 && !first.endsWith('.')
            Part(
                title = if (isTitle) first else "Основная часть",
                body = if (isTitle) acc.body.lines().drop(1).joinToString("\n").trim()
                else acc.body.toString().trim(),
                minutes = acc.minutes,
                trainee = acc.trainee.toString().trim()
            )
        }
        return Run(intro, questions.take(6), outro)
    }

    // --- ход занятия ---

    private data class Part(
        val title: String,
        val body: String,
        val minutes: Int?,
        val trainee: String = ""
    )

    private data class Run(val intro: Part?, val questions: List<Part>, val outro: Part?) {
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
        val outroRaw = slice(iOutro, -1)
        // Ниже подписи руководителя документ кончается — в содержание она не идёт.
        val signAt = outroRaw.indexOfFirst { signMark.containsMatchIn(it) }
        val outroLines = if (signAt > 0) outroRaw.take(signAt) else outroRaw

        val intro = introLines.takeIf { it.isNotEmpty() }?.let {
            val (content, trainee) = divide(strip(it, introMark))
            Part("Вводная часть", content, minutesIn(it.take(3)), trainee)
        }
        val outro = outroLines.takeIf { it.isNotEmpty() }?.let {
            val (content, trainee) = divide(strip(it, outroMark))
            Part("Заключительная часть", content, minutesIn(it.take(3)), trainee)
        }
        return Run(intro, questions(mainLines), outro)
    }

    /** Основная часть режется по нумерованным заголовкам; без нумерации идёт целиком. */
    private fun questions(mainLines: List<String>): List<Part> {
        if (mainLines.isEmpty()) return emptyList()
        val body = strip(mainLines, mainMark)
        val headRe = Regex("^(\\d{1,2})[\\.\\)]\\s*(.{6,200})$")

        val heads = ArrayList<Pair<Int, String>>()
        body.forEachIndexed { idx, l ->
            val m = headRe.find(l) ?: return@forEachIndexed
            val n = m.groupValues[1].toIntOrNull() ?: return@forEachIndexed
            if (n == heads.size + 1 && n <= 6) heads.add(idx to m.groupValues[2].trim())
        }

        // Нумерации нет — пробуем разложить по подзаголовкам: в конспектах
        // разделы часто оформлены строкой, оканчивающейся двоеточием.
        if (heads.isEmpty()) {
            val subheads = ArrayList<Pair<Int, String>>()
            body.forEachIndexed { idx, line ->
                val l = line.trim()
                val next = body.getOrNull(idx + 1)?.trim().orEmpty()
                val isSubhead = l.length in 6..95 &&
                    l.endsWith(':') &&
                    l.count { it == '.' } <= 1 &&
                    // Следующая строка должна быть текстом, а не ещё одним
                    // заголовком: иначе получится пустой учебный вопрос.
                    next.length > 40 && !next.endsWith(':')
                if (isSubhead) subheads.add(idx to l.trimEnd(':').trim())
            }
            // Один раздел делить незачем, а больше шести — дробление не по делу.
            if (subheads.size in 2..6) {
                // Текст до первого заголовка — общее вступление, оно идёт
                // в первый вопрос, иначе просто пропало бы.
                val lead = body.subList(0, subheads.first().first)
                return subheads.mapIndexed { i, (at, title) ->
                    val to = if (i + 1 < subheads.size) subheads[i + 1].first else body.size
                    val chunk = if (i == 0) lead + body.subList(at + 1, to)
                    else body.subList(at + 1, to)
                    val (content, trainee) = divide(chunk)
                    Part(title, content, null, trainee)
                }
            }
        }

        if (heads.isEmpty()) {
            val (content, trainee) = divide(body)
            return listOf(
                Part("Основная часть", content, minutesIn(mainLines.take(3)), trainee)
            )
        }

        return heads.mapIndexed { i, (at, rawTitle) ->
            val to = if (i + 1 < heads.size) heads[i + 1].first else body.size
            val (content, trainee) = divide(body.subList(at + 1, to))
            Part(
                title = rawTitle.replace(Regex("\\s*[—–-]\\s*\\d+\\s*мин\\.?\\s*$"), "").trim(),
                body = content,
                minutes = minutesIn(listOf(rawTitle)),
                trainee = trainee
            )
        }
    }

    private fun strip(lines: List<String>, head: Regex): List<String> {
        val out = ArrayList(lines)
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

    /** Графа времени часто содержит одно число без слова «мин». */
    private fun number(cell: String): Int? =
        Regex("^\\s*(\\d{1,3})\\s*$").find(cell.trim())?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 1..600 }

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
        lines.forEach { l -> re.find(l)?.let { return it.groupValues[1].trim().trim('.', ':') } }
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
