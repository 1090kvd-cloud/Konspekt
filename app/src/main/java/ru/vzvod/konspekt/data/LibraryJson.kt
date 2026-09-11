package ru.vzvod.konspekt.data

import org.json.JSONArray
import org.json.JSONObject
import ru.vzvod.konspekt.model.Discipline

/**
 * Формат обмена библиотекой предметов.
 * Файл можно править в обычном текстовом редакторе и передавать между телефонами.
 */
object LibraryJson {

    class BadLibrary(message: String) : Exception(message)

    data class Parsed(val version: Int, val updated: String, val disciplines: List<Discipline>)

    fun decode(text: String): Parsed {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BadLibrary("Файл не похож на библиотеку: не удалось разобрать JSON")
        }
        val version = root.optInt("version", 0)
        if (version <= 0) throw BadLibrary("В файле не указан номер версии (поле version)")

        val arr = root.optJSONArray("disciplines")
            ?: throw BadLibrary("В файле нет списка предметов (поле disciplines)")
        if (arr.length() == 0) throw BadLibrary("Список предметов пуст")

        val list = ArrayList<Discipline>(arr.length())
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: throw BadLibrary("Предмет № ${k + 1} записан неверно")
            list.add(discipline(o, k + 1))
        }
        val ids = list.map { it.id }
        if (ids.size != ids.toSet().size) throw BadLibrary("В файле есть предметы с одинаковым id")
        return Parsed(version, root.optString("updated", ""), list)
    }

    private fun discipline(o: JSONObject, no: Int): Discipline {
        fun req(field: String): String {
            val v = o.optString(field, "").trim()
            if (v.isEmpty()) throw BadLibrary("У предмета № $no не заполнено поле «$field»")
            return v
        }

        val questions = strings(o, "questions")
        if (questions.isEmpty()) throw BadLibrary("У предмета № $no нет ни одного учебного вопроса")

        val name = req("name")
        return Discipline(
            id = req("id"),
            name = name,
            short = o.optString("short", "").ifBlank { name },
            topics = strings(o, "topics"),
            places = strings(o, "places").ifEmpty { listOf("Класс подготовки подразделения") },
            methods = strings(o, "methods").ifEmpty { listOf("Групповое занятие") },
            eduGoals = strings(o, "eduGoals"),
            upGoals = strings(o, "upGoals"),
            questionTemplates = questions,
            materials = strings(o, "materials"),
            references = strings(o, "references"),
            safety = strings(o, "safety"),
            practiceHints = strings(o, "hints"),
            control = strings(o, "control"),
            dative = o.optString("dative", "").trim()
        )
    }

    private fun strings(o: JSONObject, field: String): List<String> {
        val arr = o.optJSONArray(field) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (k in 0 until arr.length()) {
            val v = arr.optString(k, "").trim()
            if (v.isNotEmpty()) out.add(v)
        }
        return out
    }


    fun encode(version: Int, updated: String, disciplines: List<Discipline>): String {
        val arr = JSONArray()
        disciplines.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("name", d.name)
                    .put("short", d.short)
                    .put("topics", JSONArray(d.topics))
                    .put("places", JSONArray(d.places))
                    .put("methods", JSONArray(d.methods))
                    .put("eduGoals", JSONArray(d.eduGoals))
                    .put("upGoals", JSONArray(d.upGoals))
                    .put("questions", JSONArray(d.questionTemplates))
                    .put("materials", JSONArray(d.materials))
                    .put("references", JSONArray(d.references))
                    .put("safety", JSONArray(d.safety))
                    .put("hints", JSONArray(d.practiceHints))
                    .put("control", JSONArray(d.control))
                    .put("dative", d.dative)
            )
        }
        return JSONObject()
            .put("version", version)
            .put("updated", updated)
            .put("disciplines", arr)
            .toString(2)
    }
}
