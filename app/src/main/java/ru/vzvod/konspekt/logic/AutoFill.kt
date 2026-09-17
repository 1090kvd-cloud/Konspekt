package ru.vzvod.konspekt.logic

import android.content.Context
import ru.vzvod.konspekt.data.Learned
import ru.vzvod.konspekt.model.LessonInput

/**
 * Заполняет конспект без участия человека там, где это можно сделать уверенно.
 *
 * Два источника, оба — свои. Приложенные методички дают содержание учебных
 * вопросов, архив — формулировки из похожих занятий, которые уже написаны
 * и проверены. Ничего не выдумывается: берётся только существующий текст,
 * и всё подставленное можно поправить или убрать в правке конспекта.
 */
object AutoFill {

    /** Ниже этого порога совпадение считается случайным и не подставляется. */
    private const val MIN_SCORE = 4

    data class Report(
        val input: LessonInput,
        /** Что и откуда подставлено — показывается руководителю занятия. */
        val notes: List<String>
    )

    fun enrich(context: Context, input: LessonInput, archive: List<LessonInput>): Report {
        val notes = ArrayList<String>()
        var edits = HashMap(input.edits)

        // 1. Похожее занятие из архива: свои формулировки лучше любых шаблонных.
        var questionCount = input.questionCount
        var customQuestions = input.customQuestions
        val similar = similar(input, archive)
        if (similar != null) {
            // Та же тема — берём всё. Только похожая — берём переносимое:
            // материал по гранатам не должен уехать в занятие по обороне.
            val sameTopic = similar.topic.trim().equals(input.topic.trim(), ignoreCase = true)
            var taken = 0
            similar.edits.forEach { (key, value) ->
                if (edits.containsKey(key) || value.isBlank()) return@forEach
                if (!sameTopic && !transferable(key)) return@forEach
                edits[key] = if (sameTopic) value
                else Learned.retopic(value, similar.topic, similar.lessonTitle, input.topic)
                taken++
            }
            // Вопросы переносим только при точном совпадении темы: иначе
            // наименования окажутся не про то занятие.
            if (sameTopic && customQuestions.isEmpty() && similar.customQuestions.isNotEmpty()) {
                customQuestions = similar.customQuestions
                questionCount = similar.customQuestions.size.coerceIn(1, 6)
            }
            if (taken > 0) {
                notes.add(
                    if (sameTopic) "Взято из занятия «${shortTitle(similar)}»"
                    else "Формулировки из занятия «${shortTitle(similar)}»"
                )
            }
        }

        // 2. Методички: содержание учебных вопросов, которое ещё не заполнено.
        val titles = if (customQuestions.isNotEmpty()) customQuestions else questionTitles(input)
        titles.forEachIndexed { i, title ->
            val key = Generator.Keys.qContent(i + 1)
            if (edits[key]?.isNotBlank() == true) return@forEachIndexed
            val found = bestFragment(context, input.disciplineId, title, input.topic)
            if (found != null) {
                edits[key] = found.text
                notes.add("Вопрос ${i + 1}: текст из «${found.materialTitle}»")
            }
        }

        return Report(
            input.copy(
                edits = edits,
                customQuestions = customQuestions,
                questionCount = questionCount
            ),
            notes
        )
    }

    /**
     * Темы, которые уже встречались по этому предмету. Набирать заново незачем:
     * приложение помнит, как тема была сформулирована в прошлый раз.
     */
    fun suggestTopics(archive: List<LessonInput>, disciplineId: String, typed: String): List<String> {
        val q = typed.trim().lowercase()
        return archive
            .filter { it.disciplineId == disciplineId && it.topic.isNotBlank() }
            .map { it.topic.trim() }
            .distinct()
            .filter { q.isEmpty() || it.lowercase().contains(q) }
            .filter { !it.equals(typed.trim(), ignoreCase = true) }
            .take(4)
    }

    /**
     * Разделы, которые можно перенести в другую тему: общий порядок занятия,
     * цели, безопасность, обеспечение. Содержание учебных вопросов — нельзя.
     */
    private fun transferable(key: String): Boolean = key in listOf(
        Generator.Keys.GOALS,
        Generator.Keys.SAFETY,
        Generator.Keys.PROVISION,
        Generator.Keys.INTRO_CONTENT,
        Generator.Keys.INTRO_TRAINEE,
        Generator.Keys.OUTRO_CONTENT,
        Generator.Keys.OUTRO_TRAINEE
    )

    /** Занятие по тому же предмету с наибольшим совпадением слов темы. */
    private fun similar(input: LessonInput, archive: List<LessonInput>): LessonInput? {
        val words = keywords(input.topic + " " + input.lessonTitle)
        if (words.isEmpty()) return null
        return archive
            .filter { it.id != input.id && it.disciplineId == input.disciplineId }
            .filter { it.edits.isNotEmpty() }
            .map { it to score(words, it.topic + " " + it.lessonTitle) }
            .filter { it.second >= 2 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun bestFragment(
        context: Context,
        disciplineId: String,
        title: String,
        topic: String
    ): MaterialSearch.Fragment? {
        val found = MaterialSearch.search(context, disciplineId, "$title $topic", limit = 3)
        return found.fragments.firstOrNull { it.score >= MIN_SCORE }
    }

    private fun questionTitles(input: LessonInput): List<String> {
        if (input.customQuestions.isNotEmpty()) return input.customQuestions
        val fromEdits = (1..6).mapNotNull { input.edits[Generator.Keys.qTitle(it)] }
        if (fromEdits.isNotEmpty()) return fromEdits
        return List(input.questionCount) { input.topic }
    }

    private fun keywords(text: String): Set<String> =
        Regex("[\\p{L}\\p{N}]{4,}")
            .findAll(text.lowercase())
            .map { it.value.take(6) }
            .toSet()

    private fun score(words: Set<String>, text: String): Int {
        val low = text.lowercase()
        return words.count { low.contains(it) }
    }

    private fun shortTitle(item: LessonInput): String {
        val t = item.lessonTitle.ifBlank { item.topic }
        return if (t.length > 50) t.take(50) + "…" else t
    }
}
