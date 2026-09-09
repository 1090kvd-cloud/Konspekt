package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.data.Disciplines
import ru.vzvod.konspekt.model.HandoutBlock
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.QuestionBlock
import ru.vzvod.konspekt.model.Stage
import kotlin.math.max
import kotlin.math.min

/**
 * Собирает документ из введённых данных.
 * Генерация детерминированная: одинаковый ввод — одинаковый конспект.
 */
object Generator {

    fun build(input: LessonInput): LessonPlan {
        val d = Disciplines.byId(input.disciplineId)
        val topic = input.topic.trim().ifBlank { "тема занятия" }
        fun t(s: String) = s.replace("{t}", topic)

        val total = max(15, input.minutes)
        val intro = clampRound(total * 10 / 100, 5, 15)
        val outro = clampRound(total * 8 / 100, 5, 10)
        val main = max(5, total - intro - outro)

        val titles = questionTitles(input, d, ::t)
        val slices = split(main, titles.size)

        val place = input.place.ifBlank { d.places.first() }
        val method = input.method.ifBlank { d.methods.first() }

        val questions = titles.mapIndexed { i, title ->
            QuestionBlock(
                index = i + 1,
                title = title,
                minutes = slices[i],
                leader = leaderActions(i, titles.size, d, method),
                trainee = traineeActions(i, titles.size)
            )
        }

        return LessonPlan(
            input = input,
            disciplineName = d.name,
            place = place,
            method = method,
            eduGoals = pick(d.eduGoals, min(3, d.eduGoals.size), topic).map(::t),
            upGoals = pick(d.upGoals, 2, topic).map(::t),
            metGoals = pick(d.metGoals, 1, topic).map(::t),
            materials = d.materials,
            references = d.references,
            safety = if (input.includeSafety) d.safety else emptyList(),
            intro = introStage(intro, input, topic),
            questions = questions,
            outro = outroStage(outro),
            handout = if (input.includeHandout) handout(d.handout, topic, titles) else emptyList(),
            control = if (input.includeControl) d.control.map(::t) else emptyList()
        )
    }

    // --- учебные вопросы ---

    private fun questionTitles(
        input: LessonInput,
        d: ru.vzvod.konspekt.model.Discipline,
        t: (String) -> String
    ): List<String> {
        val custom = input.customQuestions.map { it.trim() }.filter { it.isNotEmpty() }
        if (custom.isNotEmpty()) return custom
        val n = input.questionCount.coerceIn(1, 6)
        val src = d.questionTemplates
        return (0 until n).map { t(src[it % src.size]) }
    }

    // --- ход занятия ---

    private fun introStage(minutes: Int, input: LessonInput, topic: String): Stage {
        val leader = mutableListOf(
            "Принимаю доклад заместителя командира взвода о готовности подразделения к занятию.",
            "Проверяю наличие личного состава по списку, внешний вид, экипировку и наличие рабочих тетрадей.",
            "Проверяю усвоение материала предыдущего занятия — опрашиваю 2–3 обучаемых, выставляю оценки.",
            "Объявляю тему занятия «$topic», учебные вопросы, цели и порядок проведения занятия."
        )
        if (input.includeSafety) {
            leader += "Провожу инструктаж по требованиям безопасности, довожу сигналы прекращения занятия, отмечаю в журнале под роспись."
        }
        leader += "Проверяю наличие и исправность материального обеспечения занятия."

        val trainee = listOf(
            "Строятся в установленном месте, докладывают о готовности к занятию.",
            "Записывают в рабочие тетради дату, тему и учебные вопросы занятия.",
            "Отвечают на контрольные вопросы по предыдущему занятию.",
            "Расписываются в журнале инструктажа по требованиям безопасности."
        )
        return Stage("Вводная часть", minutes, leader, trainee)
    }

    private fun leaderActions(
        i: Int,
        n: Int,
        d: ru.vzvod.konspekt.model.Discipline,
        method: String
    ): List<String> {
        val hints = d.practiceHints
        val base = mutableListOf("Объявляю учебный вопрос № ${i + 1} и его целевую установку.")
        when {
            i == 0 -> {
                base += "Метод отработки: $method. Излагаю материал вопроса, использую наглядные пособия."
                base += "Добиваюсь ведения обучаемыми записей в рабочих тетрадях, контролирую по рядам."
                base += hints.getOrElse(0) { "Показываю выполнение приёма, поясняя последовательность действий." }
            }
            i == n - 1 && n > 1 -> {
                base += hints.getOrElse(2) { "Организую самостоятельную отработку под контролем командиров отделений." }
                base += "Провожу контрольный опрос 3–4 обучаемых, оцениваю качество усвоения."
                base += "Разбираю характерные ошибки, добиваюсь их устранения."
            }
            else -> {
                base += hints.getOrElse(1) { "Организую тренировку по отделениям под руководством командиров отделений." }
                base += "Обхожу рабочие места, контролирую правильность действий, оказываю помощь отстающим."
                base += "При грубых ошибках останавливаю тренировку, показываю повторно."
            }
        }
        base += "Подвожу краткий итог по вопросу, отвечаю на вопросы обучаемых."
        return base
    }

    private fun traineeActions(i: Int, n: Int): List<String> = when {
        i == 0 -> listOf(
            "Записывают наименование учебного вопроса и основные положения.",
            "Внимательно слушают, наблюдают за показом, уясняют последовательность действий.",
            "Задают уточняющие вопросы руководителю занятия."
        )
        i == n - 1 && n > 1 -> listOf(
            "Самостоятельно выполняют изученные приёмы под контролем командиров отделений.",
            "Отвечают на контрольные вопросы руководителя занятия.",
            "Устраняют указанные недостатки, повторяют действия."
        )
        else -> listOf(
            "Выполняют приёмы (действия) в составе отделений.",
            "Повторяют действия до правильного и уверенного выполнения.",
            "Докладывают командирам отделений о выполнении."
        )
    }

    private fun outroStage(minutes: Int) = Stage(
        "Заключительная часть", minutes,
        listOf(
            "Напоминаю тему, учебные вопросы и цели занятия, указываю степень их достижения.",
            "Провожу разбор занятия: отмечаю лучших, указываю на характерные недостатки и их причины.",
            "Объявляю оценки личному составу, выставляю их в журнал боевой подготовки.",
            "Отвечаю на вопросы обучаемых.",
            "Ставлю задачу на самостоятельную подготовку и указываю литературу для изучения.",
            "Проверяю наличие личного состава и материального обеспечения, организую сдачу имущества.",
            "Объявляю об окончании занятия, подаю команду на следование к месту дальнейших занятий."
        ),
        listOf(
            "Слушают разбор занятия, записывают задание на самостоятельную подготовку.",
            "Сдают полученное имущество, приводят в порядок место занятия.",
            "Строятся в установленном месте по команде командиров отделений."
        )
    )

    // --- раздатка ---

    private fun handout(blocks: List<HandoutBlock>, topic: String, titles: List<String>): List<HandoutBlock> {
        val head = HandoutBlock(
            "Тема и учебные вопросы",
            listOf("Тема: $topic") + titles.mapIndexed { i, s -> "${i + 1}. $s" }
        )
        val tail = HandoutBlock(
            "Место для записей обучаемого",
            List(6) { "________________________________________________________" }
        )
        return listOf(head) + blocks.map { b -> b.copy(items = b.items.map { it.replace("{t}", topic) }) } + tail
    }

    // --- вспомогательное ---

    /** Раскладывает минуты основной части по вопросам кратно 5, остаток — последнему. */
    fun split(main: Int, n: Int): List<Int> {
        if (n <= 0) return emptyList()
        val base = (main / n / 5) * 5
        val result = MutableList(n) { base }
        var rest = main - base * n
        var i = 0
        while (rest >= 5) {
            result[i % n] += 5
            rest -= 5
            i++
        }
        if (rest > 0) result[n - 1] += rest
        return result
    }

    private fun clampRound(v: Int, lo: Int, hi: Int): Int {
        val r = ((v + 2) / 5) * 5
        return r.coerceIn(lo, hi)
    }

    /** Детерминированный выбор n элементов: зависит от темы, но не меняется между запусками. */
    private fun pick(src: List<String>, n: Int, seed: String): List<String> {
        if (src.size <= n) return src
        val start = kotlin.math.abs(seed.hashCode()) % src.size
        return (0 until n).map { src[(start + it) % src.size] }
    }
}
