package ru.vzvod.konspekt.logic

import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.Settings

/**
 * Нормоконтроль: проверяет документ до того, как его проверит начальник.
 *
 * Приложение знает установленную форму, поэтому может заметить то, из-за чего
 * конспект обычно возвращают на доработку: не сходится время, нет требований
 * безопасности на практическом занятии, пустая шапка, вопросы без содержания.
 * Ничего не исправляется само — руководитель решает, что здесь уместно.
 */
object Review {

    enum class Level { ERROR, WARNING }

    data class Note(val level: Level, val text: String)

    /** Сколько минут расхождения считать допустимым округлением. */
    private const val TIME_TOLERANCE = 1

    private val fieldPractical = listOf(
        "поле", "плац", "полигон", "директриса", "городок", "стрельбищ", "местност"
    )
    private val practicalMethods = listOf(
        "практическ", "тактико-строев", "тренировк", "строев"
    )

    fun check(plan: LessonPlan, s: Settings): List<Note> {
        val out = ArrayList<Note>()
        val i = plan.input

        // --- шапка ---
        if (i.date.isBlank()) out.add(Note(Level.ERROR, "Не указана дата проведения"))
        if (i.unitName.ifBlank { s.unitName }.isBlank()) {
            out.add(Note(Level.ERROR, "Не указано подразделение"))
        }
        if (i.leader.ifBlank { s.leader }.isBlank()) {
            out.add(Note(Level.ERROR, "Не указан руководитель занятия"))
        }
        if (plan.place.isBlank()) out.add(Note(Level.ERROR, "Не указано место проведения"))
        if (i.topic.isBlank()) out.add(Note(Level.ERROR, "Не указана тема"))

        // --- время ---
        val parts = plan.intro.minutes + plan.mainMinutes + plan.outro.minutes
        if (kotlin.math.abs(parts - i.minutes) > TIME_TOLERANCE) {
            out.add(
                Note(
                    Level.ERROR,
                    "Время не сходится: в шапке ${i.minutes} мин, по частям $parts мин"
                )
            )
        }
        plan.questions.forEach { q ->
            if (q.minutes < 5) {
                out.add(Note(Level.WARNING, "На вопрос ${q.index} отведено меньше 5 минут"))
            }
        }
        if (plan.intro.minutes < 5) {
            out.add(Note(Level.WARNING, "Вводная часть короче 5 минут"))
        }
        if (plan.outro.minutes < 5) {
            out.add(Note(Level.WARNING, "Заключительная часть короче 5 минут"))
        }

        // --- содержание ---
        if (plan.goals.isEmpty()) out.add(Note(Level.ERROR, "Не сформулированы цели"))
        if (plan.questions.isEmpty()) {
            out.add(Note(Level.ERROR, "Нет учебных вопросов"))
        }
        plan.questions.forEach { q ->
            if (q.title.isBlank()) {
                out.add(Note(Level.ERROR, "Вопрос ${q.index} без наименования"))
            }
            if (q.content.isEmpty()) {
                out.add(Note(Level.ERROR, "Вопрос ${q.index} без содержания"))
            } else if (q.content.all(::templateLine)) {
                out.add(
                    Note(
                        Level.WARNING,
                        "Вопрос ${q.index}: только методические действия, нет самого материала"
                    )
                )
            }
        }
        val titles = plan.questions.map { it.title.trim().lowercase() }.filter { it.isNotEmpty() }
        if (titles.size != titles.toSet().size) {
            out.add(Note(Level.WARNING, "Есть одинаковые наименования учебных вопросов"))
        }

        // --- обеспечение и безопасность ---
        if (plan.provision.isEmpty()) {
            out.add(Note(Level.WARNING, "Не указано материальное обеспечение"))
        }
        if (practical(plan) && plan.safety.isEmpty()) {
            out.add(
                Note(
                    Level.ERROR,
                    "Занятие практическое, но требований безопасности нет"
                )
            )
        }

        // --- согласованность целей и вопросов ---
        if (plan.goals.isNotEmpty() && plan.questions.isNotEmpty()) {
            val goalWords = words(plan.goals.joinToString(" "))
            val questionWords = words(plan.questions.joinToString(" ") { it.title })
            if (goalWords.isNotEmpty() && questionWords.isNotEmpty() &&
                goalWords.intersect(questionWords).isEmpty()
            ) {
                out.add(
                    Note(
                        Level.WARNING,
                        "Цели и учебные вопросы не пересекаются по смыслу — проверьте формулировки"
                    )
                )
            }
        }

        return out
    }

    fun errors(notes: List<Note>) = notes.count { it.level == Level.ERROR }

    /** Занятие с выходом в поле или с отработкой приёмов. */
    private fun practical(plan: LessonPlan): Boolean {
        val place = plan.place.lowercase()
        val method = plan.method.lowercase()
        return fieldPractical.any { place.contains(it) } ||
            practicalMethods.any { method.contains(it) }
    }

    /** Строка вида «Излагаю материал вопроса» — действие руководителя, а не материал. */
    private fun templateLine(line: String): Boolean {
        val l = line.trim().lowercase()
        return listOf(
            "излагаю", "довожу", "показываю", "организую", "контролирую", "провожу",
            "подвожу", "добиваюсь", "обхожу", "метод отработки", "при грубых"
        ).any { l.startsWith(it) }
    }

    private fun words(text: String): Set<String> =
        Regex("[\\p{L}]{5,}").findAll(text.lowercase()).map { it.value.take(6) }.toSet()
}
