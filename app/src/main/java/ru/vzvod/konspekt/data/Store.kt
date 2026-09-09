package ru.vzvod.konspekt.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.Settings
import java.io.File

/** Всё хранится локально в одном JSON-файле. Никакой сети приложение не использует. */
class Store(context: Context) {

    private val file = File(context.filesDir, "konspekt.json")

    var settings by mutableStateOf(Settings())
        private set

    val archive = mutableStateListOf<LessonInput>()

    init {
        load()
    }

    fun updateSettings(s: Settings) {
        settings = s
        persist()
    }

    fun save(item: LessonInput) {
        val idx = archive.indexOfFirst { it.id == item.id }
        if (idx >= 0) archive[idx] = item else archive.add(0, item)
        persist()
    }

    fun delete(id: String) {
        archive.removeAll { it.id == id }
        persist()
    }

    fun clearArchive() {
        archive.clear()
        persist()
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val s = root.optJSONObject("settings")
            if (s != null) {
                settings = Settings(
                    leader = s.optString("leader", ""),
                    unitName = s.optString("unitName", ""),
                    approver = s.optString("approver", "Командир роты"),
                    defaultMinutes = s.optInt("defaultMinutes", 90),
                    darkTheme = s.optBoolean("darkTheme", false)
                )
            }
            val arr = root.optJSONArray("archive") ?: JSONArray()
            archive.clear()
            for (k in 0 until arr.length()) {
                archive.add(fromJson(arr.getJSONObject(k)))
            }
        }
    }

    private fun persist() {
        runCatching {
            val root = JSONObject()
            root.put(
                "settings", JSONObject()
                    .put("leader", settings.leader)
                    .put("unitName", settings.unitName)
                    .put("approver", settings.approver)
                    .put("defaultMinutes", settings.defaultMinutes)
                    .put("darkTheme", settings.darkTheme)
            )
            val arr = JSONArray()
            archive.forEach { arr.put(toJson(it)) }
            root.put("archive", arr)
            file.writeText(root.toString())
        }
    }

    private fun toJson(i: LessonInput) = JSONObject()
        .put("id", i.id)
        .put("createdAt", i.createdAt)
        .put("disciplineId", i.disciplineId)
        .put("topic", i.topic)
        .put("lessonNo", i.lessonNo)
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

    private fun fromJson(o: JSONObject): LessonInput {
        val cq = o.optJSONArray("customQuestions") ?: JSONArray()
        val list = ArrayList<String>(cq.length())
        for (k in 0 until cq.length()) list.add(cq.optString(k, ""))
        return LessonInput(
            id = o.optString("id", System.nanoTime().toString()),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            disciplineId = o.optString("disciplineId", "general"),
            topic = o.optString("topic", ""),
            lessonNo = o.optString("lessonNo", "1"),
            minutes = o.optInt("minutes", 90),
            place = o.optString("place", ""),
            method = o.optString("method", ""),
            unitName = o.optString("unitName", ""),
            date = o.optString("date", ""),
            leader = o.optString("leader", ""),
            questionCount = o.optInt("questionCount", 3),
            customQuestions = list.filter { it.isNotBlank() },
            includeHandout = o.optBoolean("includeHandout", true),
            includeControl = o.optBoolean("includeControl", true),
            includeSafety = o.optBoolean("includeSafety", true),
            note = o.optString("note", "")
        )
    }
}
