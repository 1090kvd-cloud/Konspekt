package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.model.HandoutBlock
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.QuestionBlock
import ru.vzvod.konspekt.model.Stage
import kotlin.math.max
import kotlin.math.min

/**
 * Собирает документ по форме план-конспекта: вводная — основная — заключительная,
 * содержание от первого лица, действия обучаемых отдельной графой.
 * Генерация детерминированная: одинаковый ввод — одинаковый конспект.
 */
object Generator {

    /** В образцах вводная и заключительная части — по 5 минут независимо от общей продолжительности. */
    private const val EDGE = 5

    /** Ключи разделов, которые можно править руками. */
    object Keys {
        const val GOALS = "goals"
        const val PROVISION = "provision"
        const val SAFETY = "safety"
        const val CONTROL = "control"
        const val INTRO_CONTENT = "intro.content"
        const val INTRO_TRAINEE = "intro.trainee"
        const val OUTRO_CONTENT = "outro.content"
        const val OUTRO_TRAINEE = "outro.trainee"
        fun qTitle(n: Int) = "q$n.title"
        fun qContent(n: Int) = "q$n.content"
        fun qTrainee(n: Int) = "q$n.trainee"
    }

    /** Текст правки -> список пунктов. Пустые строки отбрасываются. */
    fun lines(text: String): List<String> =
        text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * @param applyEdits false — собрать документ начисто, как его предлагает шаблон.
     *        Нужно экрану правки, чтобы показать исходный вариант.
     */
    fun build(input: LessonInput, applyEdits: Boolean = true): LessonPlan {
        val d = Library.byId(input.disciplineId)
        val topic = input.topic.trim().ifBlank { "тема занятия" }
        fun t(s: String) = s.replace("{t}", topic)

        val total = max(20, input.minutes)
        val edge = if (total >= 30) EDGE else 5
        val main = max(5, total - edge * 2)

        val titles = questionTitles(input, d, ::t)
        val slices = split(main, titles.size)

        val place = input.place.ifBlank { d.places.first() }
        val method = input.method.ifBlank { d.methods.first() }

        val edits = if (applyEdits) input.edits else emptyMap()
        fun list(key: String, fallback: List<String>): List<String> {
            val text = edits[key] ?: return fallback
            return lines(text)
        }
        fun one(key: String, fallback: String): String =
            edits[key]?.trim()?.ifBlank { null } ?: fallback

        val questions = titles.mapIndexed { i, title ->
            val n = i + 1
            QuestionBlock(
                index = n,
                title = one(Keys.qTitle(n), title),
                minutes = slices[i],
                content = list(Keys.qContent(n), questionContent(i, titles.size, d, method)),
                trainee = list(Keys.qTrainee(n), traineeActions(i, titles.size))
            )
        }

        val intro = introStage(edge, input, topic).let {
            it.copy(
                content = list(Keys.INTRO_CONTENT, it.content),
                trainee = list(Keys.INTRO_TRAINEE, it.trainee)
            )
        }
        val outro = outroStage(edge).let {
            it.copy(
                content = list(Keys.OUTRO_CONTENT, it.content),
                trainee = list(Keys.OUTRO_TRAINEE, it.trainee)
            )
        }

        return LessonPlan(
            input = input,
            disciplineName = d.name,
            dative = d.dative.ifBlank { Renderer.disciplineCase(d.name) },
            place = place,
            method = method,
            goals = list(Keys.GOALS, buildGoals(d, ::t, topic)),
            provision = list(Keys.PROVISION, (d.materials + d.references).distinct()),
            safety = list(Keys.SAFETY, if (input.includeSafety) d.safety else emptyList()),
            intro = intro,
            questions = questions,
            outro = outro,
            handout = if (input.includeHandout) handout(d.handout, topic, titles) else emptyList(),
            control = list(Keys.CONTROL, if (input.includeControl) d.control.map(::t).take(4) else emptyList())
        )
    }

    /** Цели идут сплошным нумерованным списком: сначала учебные, последней — воспитательная. */
    private fun buildGoals(
        d: ru.vzvod.konspekt.model.Discipline,
        t: (String) -> String,
        seed: String
    ): List<String> {
        val learn = pick(d.eduGoals, min(2, d.eduGoals.size), seed).map(t)
        val raise = pick(d.upGoals, 1, seed).map(t)
        return learn + raise
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
        val content = mutableListOf(
            "Принимаю доклад заместителя командира взвода о готовности к занятию.",
            "Проверяю наличие личного состава, готовность к занятию, внешний вид и экипировку.",
            "Проверяю усвоение материала предыдущего занятия, опрашиваю 2–3 обучаемых.",
            "Довожу тему занятия «$topic», учебные вопросы и цели."
        )
        if (input.includeSafety) {
            content += "Довожу требования безопасности и порядок их выполнения, отмечаю в журнале под роспись."
        }
        val trainee = listOf(
            "Строятся в установленном месте, докладывают о наличии личного состава.",
            "Слушают, записывают тему занятия и учебные вопросы.",
            "Отвечают на контрольные вопросы по предыдущему занятию."
        )
        return Stage("Вводная часть", minutes, content, trainee)
    }

    private fun questionContent(
        i: Int,
        n: Int,
        d: ru.vzvod.konspekt.model.Discipline,
        method: String
    ): List<String> {
        val hints = d.practiceHints
        val out = mutableListOf<String>()
        when {
            i == 0 -> {
                out += "Метод отработки: $method. Излагаю материал вопроса, использую наглядные пособия."
                out += "Добиваюсь ведения обучаемыми записей в рабочих тетрадях, контролирую по рядам."
                out += hints.getOrElse(0) { "Показываю выполнение приёма, поясняя последовательность действий." }
            }
            i == n - 1 && n > 1 -> {
                out += hints.getOrElse(2) { "Организую самостоятельную отработку под контролем командиров отделений." }
                out += "Провожу контрольный опрос 3–4 обучаемых, оцениваю качество усвоения."
                out += "Разбираю характерные ошибки, добиваюсь их устранения."
            }
            else -> {
                out += hints.getOrElse(1) { "Организую тренировку по отделениям под руководством командиров отделений." }
                out += "Обхожу рабочие места, контролирую правильность действий, помогаю отстающим."
                out += "При грубых ошибках останавливаю тренировку и показываю повторно."
            }
        }
        out += "Подвожу краткий итог по вопросу, отвечаю на вопросы обучаемых."
        return out
    }

    private fun traineeActions(i: Int, n: Int): List<String> = when {
        i == 0 -> listOf(
            "Слушают, конспектируют материал учебного вопроса.",
            "Наблюдают за показом, уясняют последовательность действий.",
            "Задают уточняющие вопросы."
        )
        i == n - 1 && n > 1 -> listOf(
            "Самостоятельно выполняют изученные приёмы под контролем командиров отделений.",
            "Отвечают на контрольные вопросы, устраняют указанные недостатки."
        )
        else -> listOf(
            "Выполняют приёмы в составе отделений, повторяют до уверенного выполнения.",
            "Докладывают командирам отделений о выполнении."
        )
    }

    private fun outroStage(minutes: Int) = Stage(
        "Заключительная часть", minutes,
        listOf(
            "Напоминаю тему, учебные вопросы и цели занятия, указываю степень их достижения.",
            "Объявляю индивидуальные оценки каждому обучаемому и указываю на основные ошибки.",
            "Отвечаю на вопросы обучаемых.",
            "Даю указания на подготовку к следующему занятию, указываю литературу.",
            "Проверяю наличие личного состава и материального обеспечения, организую сдачу имущества."
        ),
        listOf(
            "Слушают, задают вопросы, записывают литературу.",
            "Сдают полученное имущество, приводят в порядок место занятия."
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

    /** Время в шапке: «2 часа», «1 час 30 мин», «45 мин». */
    fun timeText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val hours = when {
            h == 0 -> ""
            h % 10 == 1 && h % 100 != 11 -> "$h час"
            h % 10 in 2..4 && h % 100 !in 12..14 -> "$h часа"
            else -> "$h часов"
        }
        return when {
            h == 0 -> "$m мин"
            m == 0 -> hours
            else -> "$hours $m мин"
        }
    }

    /** Постоянная часть занятия: вводная и заключительная. */
    fun edgeMinutes(total: Int): Int = if (max(20, total) >= 30) EDGE else 5

    /** Детерминированный выбор n элементов: зависит от темы, но не меняется между запусками. */
    private fun pick(src: List<String>, n: Int, seed: String): List<String> {
        if (src.size <= n) return src
        val start = kotlin.math.abs(seed.hashCode()) % src.size
        return (0 until n).map { src[(start + it) % src.size] }
    }
}
