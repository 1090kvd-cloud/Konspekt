package ru.vzvod.konspekt.data

import org.json.JSONArray
import org.json.JSONObject
import ru.vzvod.konspekt.model.LessonInput

/** Одно занятие в JSON. Общий формат для архива на телефоне и для файла обмена. */
object LessonJson {

    fun write(i: LessonInput): JSONObject = JSONObject()
        .put("id", i.id)
        .put("createdAt", i.createdAt)
        .put("disciplineId", i.disciplineId)
        .put("themeNo", i.themeNo)
        .put("topic", i.topic)
        .put("lessonNo", i.lessonNo)
        .put("lessonTitle", i.lessonTitle)
        .put("timeLabel", i.timeLabel)
        .put("minutes", i.minutes)
        .put("place", i.place)
        .put("method", i.method)
        .put("unitName", i.unitName)
        .put("date", i.date)
        .put("leader", i.leader)
        .put("questionCount", i.questionCount)
        .put("customQuestions", JSONArray(i.customQuestions))
        .put("includeHandout", i.includeHandout)
        .put("includeControl", i.includeControl)
        .put("includeSafety", i.includeSafety)
        .put("note", i.note)
        .put("edits", JSONObject(i.edits))

    fun read(o: JSONObject): LessonInput {
        val cq = o.optJSONArray("customQuestions") ?: JSONArray()
        val questions = ArrayList<String>(cq.length())
        for (k in 0 until cq.length()) questions.add(cq.optString(k, ""))
        return LessonInput(
            id = o.optString("id", "").ifBlank { System.nanoTime().toString() },
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            disciplineId = o.optString("disciplineId", "general"),
            topic = o.optString("topic", ""),
            themeNo = o.optString("themeNo", "1"),
            lessonNo = o.optString("lessonNo", "1"),
            lessonTitle = o.optString("lessonTitle", ""),
            timeLabel = o.optString("timeLabel", ""),
            minutes = o.optInt("minutes", 90),
            place = o.optString("place", ""),
            method = o.optString("method", ""),
            unitName = o.optString("unitName", ""),
            date = o.optString("date", ""),
            leader = o.optString("leader", ""),
            questionCount = o.optInt("questionCount", 3),
            customQuestions = questions.filter { it.isNotBlank() },
            includeHandout = o.optBoolean("includeHandout", false),
            includeControl = o.optBoolean("includeControl", true),
            includeSafety = o.optBoolean("includeSafety", true),
            note = o.optString("note", ""),
            edits = readEdits(o.optJSONObject("edits"))
        )
    }

    private fun readEdits(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = HashMap<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = o.optString(k, "")
            if (v.isNotBlank()) out[k] = v
        }
        return out
    }
}
