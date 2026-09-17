package ru.vzvod.konspekt.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.ProgramItem
import java.io.File
import java.util.UUID

/**
 * Программа боевой подготовки: перечень занятий, которые положено провести.
 *
 * Пока её нет, приложение ждёт, что тему введут руками. Когда она заведена,
 * вводить нечего: приложение само знает, какое занятие следующее, сколько на
 * него часов и по какому предмету. Отметил конспект проведённым — пункт закрылся.
 */
object Program {

    val items = mutableStateListOf<ProgramItem>()

    private fun file(context: Context) = File(context.filesDir, "program.json")

    fun load(context: Context) {
        val f = file(context)
        if (!f.exists()) return
        runCatching {
            val list = decode(f.readText())
            items.clear()
            items.addAll(list)
        }
    }

    private fun persist(context: Context) {
        runCatching { file(context).writeText(encode(items)) }
    }

    /** Ближайшее непроведённое занятие: то, что нужно готовить сегодня. */
    fun next(disciplineId: String? = null): ProgramItem? = items
        .firstOrNull { it.doneBy.isBlank() && (disciplineId == null || it.disciplineId == disciplineId) }

    fun remaining(disciplineId: String? = null): Int = items
        .count { it.doneBy.isBlank() && (disciplineId == null || it.disciplineId == disciplineId) }

    fun total(disciplineId: String? = null): Int = items
        .count { disciplineId == null || it.disciplineId == disciplineId }

    /** Часы, которые остались недобранными по предмету. */
    fun remainingMinutes(disciplineId: String? = null): Int = items
        .filter { it.doneBy.isBlank() && (disciplineId == null || it.disciplineId == disciplineId) }
        .sumOf { it.minutes }

    fun add(context: Context, item: ProgramItem) {
        items.add(item.copy(id = item.id.ifBlank { UUID.randomUUID().toString() }))
        persist(context)
    }

    fun replaceAll(context: Context, list: List<ProgramItem>) {
        items.clear()
        items.addAll(list)
        persist(context)
    }

    fun remove(context: Context, id: String) {
        items.removeAll { it.id == id }
        persist(context)
    }

    fun clear(context: Context) {
        items.clear()
        persist(context)
    }

    /**
     * Ищет пункт программы, которому отвечает занятие: по предмету и теме.
     * Нужен, чтобы отметка «проведено» в архиве закрывала пункт сама.
     */
    fun matching(lesson: LessonInput): ProgramItem? = items.firstOrNull {
        it.disciplineId == lesson.disciplineId &&
            it.topic.trim().equals(lesson.topic.trim(), ignoreCase = true) &&
            it.lessonNo.trim() == lesson.lessonNo.trim()
    }

    /**
     * Приводит программу в соответствие с архивом — в обе стороны.
     *
     * Занятие отмечено проведённым — пункт закрывается. Отметку сняли или конспект
     * удалили — пункт снова открывается. Иначе программа однажды показала бы всё
     * пройденным и больше никогда не сдвинулась.
     */
    fun syncWithArchive(context: Context, archive: List<LessonInput>) {
        val conducted = archive.filter { it.conductedAt > 0 }
        val conductedIds = conducted.map { it.id }.toSet()
        var changed = false

        // Закрываем то, что проведено.
        conducted.forEach { lesson ->
            val item = matching(lesson) ?: return@forEach
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0 && items[idx].doneBy.isBlank()) {
                items[idx] = items[idx].copy(doneBy = lesson.id)
                changed = true
            }
        }

        // Открываем то, чьё занятие больше не проведено или удалено из архива.
        items.forEachIndexed { idx, item ->
            if (item.doneBy.isNotBlank() && item.doneBy !in conductedIds) {
                items[idx] = item.copy(doneBy = "")
                changed = true
            }
        }

        if (changed) persist(context)
    }

    // --- обмен файлом ---

    fun encode(list: List<ProgramItem>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("disciplineId", p.disciplineId)
                    .put("themeNo", p.themeNo)
                    .put("topic", p.topic)
                    .put("lessonNo", p.lessonNo)
                    .put("lessonTitle", p.lessonTitle)
                    .put("minutes", p.minutes)
                    .put("doneBy", p.doneBy)
            )
        }
        return JSONObject().put("version", 1).put("program", arr).toString(2)
    }

    fun looksLikeProgram(text: String): Boolean = runCatching {
        JSONObject(text).has("program")
    }.getOrDefault(false)

    fun decode(text: String): List<ProgramItem> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("program") ?: throw Exception("В файле нет перечня занятий")
        val out = ArrayList<ProgramItem>(arr.length())
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: continue
            val topic = o.optString("topic", "").trim()
            if (topic.isEmpty()) continue
            out.add(
                ProgramItem(
                    id = UUID.randomUUID().toString(),
                    disciplineId = o.optString("disciplineId", "general"),
                    themeNo = o.optString("themeNo", "1"),
                    topic = topic,
                    lessonNo = o.optString("lessonNo", "1"),
                    lessonTitle = o.optString("lessonTitle", ""),
                    minutes = o.optInt("minutes", 90).coerceIn(15, 600),
                    doneBy = o.optString("doneBy", "")
                )
            )
        }
        if (out.isEmpty()) throw Exception("Перечень занятий пуст")
        return out
    }
}
