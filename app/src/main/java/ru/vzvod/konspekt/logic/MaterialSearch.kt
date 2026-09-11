package ru.vzvod.konspekt.logic

import android.content.Context
import ru.vzvod.konspekt.data.Materials

/**
 * Поиск по приложенным материалам.
 *
 * Приложение не понимает содержание: оно ищет в тексте методички куски,
 * где встречаются слова из наименования учебного вопроса, и предлагает их
 * руководителю занятия. Решение, что вставить в конспект, остаётся за человеком.
 */
object MaterialSearch {

    data class Fragment(
        val materialId: String,
        val materialTitle: String,
        val text: String,
        val score: Int
    )

    data class Outcome(
        val fragments: List<Fragment>,
        /** Материалы, из которых текст достать не вышло: формат и причина. */
        val skipped: List<Pair<String, String>>
    )

    private val stopWords = setOf(
        "для", "при", "его", "как", "что", "это", "или", "они", "все", "теме", "тема",
        "вопрос", "вопросы", "занятия", "занятие", "личного", "состава", "порядок",
        "основные", "общие", "изучаемых", "требования"
    )

    /** Грубая основа слова: окончания в русском меняются, начало — почти нет. */
    private fun stem(word: String): String = word.lowercase().take(6)

    private fun keys(query: String): List<String> =
        Regex("[\\p{L}\\p{N}]{4,}")
            .findAll(query)
            .map { it.value.lowercase() }
            .filter { it !in stopWords }
            .map { stem(it) }
            .distinct()
            .toList()

    fun search(
        context: Context,
        disciplineId: String,
        query: String,
        limit: Int = 10
    ): Outcome {
        val k = keys(query)
        val fragments = ArrayList<Fragment>()
        val skipped = ArrayList<Pair<String, String>>()

        Materials.forDiscipline(disciplineId).forEach { m ->
            when (val r = TextExtract.extract(context, m)) {
                is TextExtract.Result.Unsupported -> skipped.add(m.title to r.reason)
                is TextExtract.Result.Ok -> {
                    if (k.isEmpty()) return@forEach
                    sections(r.text).forEach { (head, body) ->
                        val headLow = head.lowercase()
                        val bodyLow = body.lowercase()
                        // Совпадение в заголовке раздела весит больше: скорее всего это он и есть.
                        val score = 3 * k.count { headLow.contains(it) } +
                            k.count { bodyLow.contains(it) }
                        if (score > 0) {
                            val text = if (head.isBlank()) body else "$head\n$body"
                            fragments.add(
                                Fragment(
                                    materialId = m.id,
                                    materialTitle = m.title,
                                    text = if (text.length > 2000) text.take(2000) + "…" else text,
                                    score = score
                                )
                            )
                        }
                    }
                }
            }
        }

        val best = fragments
            .sortedWith(compareByDescending<Fragment> { it.score }.thenBy { it.text.length })
            .take(limit)
        return Outcome(best, skipped)
    }

    /**
     * Режем методичку на разделы: заголовок и всё до следующего заголовка.
     * Так в конспект попадает цельный раздел, а не случайный абзац из середины.
     */
    private fun sections(text: String): List<Pair<String, String>> {
        val lines = text.lines()
        val out = ArrayList<Pair<String, String>>()
        var head = ""
        val body = StringBuilder()

        fun flush() {
            val b = body.toString().trim()
            if (b.length >= 40 || (head.isNotBlank() && b.isNotEmpty())) out.add(head to b)
            body.setLength(0)
        }

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) {
                if (body.length > 1500) flush()
                return@forEach
            }
            if (isHeading(line)) {
                flush()
                head = line
            } else {
                if (body.isNotEmpty()) body.append('\n')
                body.append(line)
                if (body.length > 1800) flush()
            }
        }
        flush()
        return out.filter { it.second.isNotEmpty() || it.first.isNotEmpty() }
    }

    /** Заголовок раздела: нумерация вида «1.3.», короткая строка без точки в конце, ПРОПИСНЫЕ. */
    private fun isHeading(line: String): Boolean {
        if (line.length > 120) return false
        if (Regex("^\\d+(\\.\\d+)*\\.?\\s+\\p{L}").containsMatchIn(line)) return true
        val letters = line.filter { it.isLetter() }
        if (letters.length >= 6 && letters.all { it.isUpperCase() }) return true
        if (line.length <= 80 && !line.endsWith('.') && !line.endsWith(';') &&
            !line.endsWith(',') && line.count { it == ' ' } in 1..9 &&
            line.firstOrNull()?.isUpperCase() == true
        ) return true
        return false
    }
}
