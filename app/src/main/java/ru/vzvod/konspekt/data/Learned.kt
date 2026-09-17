package ru.vzvod.konspekt.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import ru.vzvod.konspekt.model.LessonInput
import java.io.File

/**
 * Формулировки, выученные из ваших же конспектов.
 *
 * Встроенная библиотека написана общевойсковым языком — он верный, но чужой,
 * и поэтому документ читается как машинный. Здесь копится то, как пишете вы:
 * цели, действия руководителя во вводной и заключительной частях, действия
 * обучаемых, контрольные вопросы. Генератор берёт сначала это, и со второго-
 * третьего конспекта по предмету приложение начинает говорить вашими словами.
 *
 * Ничего не придумывается: сохраняются только строки, которые вы написали
 * или приняли, — из загруженных конспектов и из правок.
 */
object Learned {

    /** Ключ: «предмет|раздел». Значение: формулировки, свежие впереди. */
    private val store = mutableStateMapOf<String, List<String>>()

    private const val MAX_PER_KEY = 12
    private const val MIN_LENGTH = 15

    object Part {
        const val GOALS = "goals"
        const val INTRO = "intro"
        const val INTRO_TRAINEE = "introTrainee"
        const val OUTRO = "outro"
        const val OUTRO_TRAINEE = "outroTrainee"
        const val CONTROL = "control"
        const val PROVISION = "provision"
        const val SAFETY = "safety"
    }

    private fun key(disciplineId: String, part: String) = "$disciplineId|$part"

    fun get(disciplineId: String, part: String): List<String> =
        store[key(disciplineId, part)].orEmpty()

    fun has(disciplineId: String, part: String) = get(disciplineId, part).isNotEmpty()

    /** Сколько формулировок приложение выучило по предмету. */
    fun count(disciplineId: String): Int =
        store.entries.filter { it.key.startsWith("$disciplineId|") }.sumOf { it.value.size }

    fun total(): Int = store.values.sumOf { it.size }

    // --- наполнение ---

    /** Подстановка темы: в выученной строке тема заменяется меткой. */
    const val TOPIC_MARK = "{t}"

    /**
     * Убирает из строки тему занятия, ставя на её место метку.
     *
     * Заменяется только тема в кавычках или после слов «по теме», «тему занятия» —
     * там она стоит в именительном падеже и подстановка другой темы читается верно.
     * Если тема вплетена в фразу («изучить устройство автомата»), строка непереносима:
     * подставив другую тему, получим бессмыслицу. Такие строки возвращают null
     * и не запоминаются вовсе.
     */
    fun generalize(line: String, topic: String, lessonTitle: String): String? {
        var out = line
        val marks = listOf(topic, lessonTitle)
            .map { it.trim() }
            .filter { it.length >= 8 }
            .sortedByDescending { it.length }
        if (marks.isEmpty()) return line

        marks.forEach { t ->
            val q = Regex("[«\"]" + Regex.escape(t) + "[»\"]", RegexOption.IGNORE_CASE)
            out = q.replace(out, TOPIC_MARK)
            val after = Regex(
                "(по\\s+теме|тему\\s+занятия|тема\\s+занятия|теме)\\s+" + Regex.escape(t),
                RegexOption.IGNORE_CASE
            )
            out = after.replace(out) { m -> m.groupValues[1] + " " + TOPIC_MARK }
        }

        // Тема осталась вплетённой в падеже — строку переносить нельзя.
        // Буквального совпадения тут не будет («ручных гранат» против «гранатам»),
        // поэтому сравниваем по основам слов: два общих корня и больше — не наш случай.
        if (marks.any { out.contains(it, ignoreCase = true) }) return null
        val fromTopic = stems(marks.joinToString(" "))
        val inLine = stems(out.replace(TOPIC_MARK, " "))
        if (fromTopic.intersect(inLine).size >= 2) return null
        return out
    }

    /** Основы значимых слов: падежи в русском меняют окончание, а не начало. */
    private fun stems(text: String): Set<String> =
        Regex("[А-Яа-яЁё]{5,}").findAll(text)
            .map { it.value.lowercase().take(5) }
            .toSet()

    /** Возвращает выученную строку с подставленной темой текущего занятия. */
    fun applyTopic(line: String, topic: String): String = line.replace(TOPIC_MARK, "«$topic»")

    /** Переносит строку из одного занятия в другое, заменив тему. */
    fun retopic(text: String, fromTopic: String, fromLesson: String, toTopic: String): String =
        text.lines()
            .mapNotNull { generalize(it, fromTopic, fromLesson) }
            .joinToString("\n") { applyTopic(it, toTopic) }

    private fun remember(disciplineId: String, part: String, lines: List<String>) {
        if (lines.isEmpty()) return
        val k = key(disciplineId, part)
        val clean = lines
            .map { it.trim() }
            .filter { it.length >= MIN_LENGTH && it.length <= 400 }
            .filterNot { it.matches(Regex("^\\d{1,3}\\s*мин\\.?$")) }
        if (clean.isEmpty()) return
        // Свежее впереди, повторы убираются: список не растёт бесконечно.
        val merged = (clean + store[k].orEmpty()).distinct().take(MAX_PER_KEY)
        store[k] = merged
    }

    /** Разбирает занятие и запоминает из него всё, что написано человеком. */
    fun learn(context: Context, lesson: LessonInput) {
        val d = lesson.disciplineId
        val e = lesson.edits
        // Строки, привязанные к теме падежом, не запоминаются: перенести их нельзя.
        fun lines(key: String) = e[key]?.lines()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { generalize(it, lesson.topic, lesson.lessonTitle) }
            .orEmpty()

        remember(d, Part.GOALS, lines("goals"))
        remember(d, Part.PROVISION, lines("provision"))
        remember(d, Part.SAFETY, lines("safety"))
        remember(d, Part.INTRO, lines("intro.content"))
        remember(d, Part.INTRO_TRAINEE, lines("intro.trainee"))
        remember(d, Part.OUTRO, lines("outro.content"))
        remember(d, Part.OUTRO_TRAINEE, lines("outro.trainee"))
        remember(d, Part.CONTROL, lines("control"))
        persist(context)
    }

    fun learnAll(context: Context, lessons: List<LessonInput>) {
        lessons.forEach { learn(context, it) }
    }

    fun forget(context: Context, disciplineId: String) {
        store.keys.filter { it.startsWith("$disciplineId|") }.forEach { store.remove(it) }
        persist(context)
    }

    fun clear(context: Context) {
        store.clear()
        persist(context)
    }

    // --- хранение ---

    private fun file(context: Context) = File(context.filesDir, "learned.json")

    fun load(context: Context) {
        val f = file(context)
        if (!f.exists()) return
        runCatching {
            val root = JSONObject(f.readText())
            store.clear()
            root.keys().forEach { k ->
                val arr = root.optJSONArray(k) ?: return@forEach
                val list = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    arr.optString(i, "").takeIf { it.isNotBlank() }?.let { list.add(it) }
                }
                if (list.isNotEmpty()) store[k] = list
            }
        }
    }

    private fun persist(context: Context) {
        runCatching {
            val root = JSONObject()
            store.forEach { (k, v) -> root.put(k, JSONArray(v)) }
            file(context).writeText(root.toString())
        }
    }
}
