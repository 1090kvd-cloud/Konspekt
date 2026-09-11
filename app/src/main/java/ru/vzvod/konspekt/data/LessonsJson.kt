package ru.vzvod.konspekt.data

import org.json.JSONArray
import org.json.JSONObject
import ru.vzvod.konspekt.model.LessonInput
import java.util.UUID

/**
 * Обмен готовыми конспектами. Один файл — целая база занятий,
 * которую можно собрать на компьютере и раздать по подразделению.
 *
 * Библиотека предметов (library.json) и конспекты (lessons.json) — разные файлы:
 * первая хранит заготовки формулировок, вторая — сами занятия.
 */
object LessonsJson {

    class BadFile(message: String) : Exception(message)

    fun looksLikeLessons(text: String): Boolean {
        val head = text.trimStart().take(400)
        return head.startsWith("{") && head.contains("\"lessons\"")
    }

    fun decode(text: String): List<LessonInput> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BadFile("Файл не похож на базу конспектов: не удалось разобрать JSON")
        }
        val arr = root.optJSONArray("lessons")
            ?: throw BadFile("В файле нет списка занятий (поле lessons)")
        if (arr.length() == 0) throw BadFile("Список занятий пуст")

        val out = ArrayList<LessonInput>(arr.length())
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: throw BadFile("Занятие № ${k + 1} записано неверно")
            val topic = o.optString("topic", "").trim()
            val title = o.optString("lessonTitle", "").trim()
            if (topic.isEmpty() && title.isEmpty()) {
                throw BadFile("У занятия № ${k + 1} нет ни темы, ни наименования")
            }
            out.add(LessonJson.read(o).copy(
                // Свои ключи, чтобы файл можно было загружать повторно без путаницы.
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis() - k
            ))
        }
        return out
    }

    fun encode(lessons: List<LessonInput>): String {
        val arr = JSONArray()
        lessons.forEach { arr.put(LessonJson.write(it)) }
        return JSONObject()
            .put("version", 1)
            .put("lessons", arr)
            .toString(2)
    }
}
