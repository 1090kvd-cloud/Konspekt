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
class Store(private val context: Context) {

    private val file = File(context.filesDir, "konspekt.json")

    var settings by mutableStateOf(Settings())
        private set

    val archive = mutableStateListOf<LessonInput>()

    /** Незаконченное занятие. Переживает выход из приложения, в архив не попадает. */
    var draft by mutableStateOf<LessonInput?>(null)
        private set

    fun saveDraft(item: LessonInput?) {
        draft = item
        persist()
    }

    init {
        load()
    }

    fun updateSettings(s: Settings) {
        settings = s
        persist()
    }

    /** Копия занятия под новым номером — основа для следующего конспекта. */
    fun duplicate(item: LessonInput): LessonInput {
        val copy = item.copy(
            id = java.util.UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis()
        )
        archive.add(0, copy)
        persist()
        return copy
    }

    fun save(item: LessonInput) {
        val idx = archive.indexOfFirst { it.id == item.id }
        if (idx >= 0) archive[idx] = item else archive.add(0, item)
        // Занятие легло в архив — черновик больше не нужен.
        if (draft?.id == item.id) draft = null
        persist()
    }

    /** Отметка о проведении: список перестаёт быть свалкой из старого и предстоящего. */
    fun toggleConducted(id: String) {
        val idx = archive.indexOfFirst { it.id == id }
        if (idx < 0) return
        val item = archive[idx]
        archive[idx] = item.copy(
            conductedAt = if (item.conductedAt > 0) 0L else System.currentTimeMillis()
        )
        persist()
    }

    fun delete(id: String) {
        // Вместе с занятием убираем и его исходный файл, чтобы не копился мусор.
        archive.firstOrNull { it.id == id }?.sourceName?.let { Sources.remove(context, it) }
        archive.removeAll { it.id == id }
        persist()
        cleanupImages()
    }

    fun clearArchive() {
        archive.forEach { Sources.remove(context, it.sourceName) }
        archive.clear()
        persist()
    }

    /** Перечитать всё с диска — после восстановления из резервной копии. */
    fun reload() = load()

    /** Рисунки, на которые не ссылается ни одно занятие, больше не нужны. */
    private fun cleanupImages() {
        val used = HashSet<String>()
        (archive + listOfNotNull(draft)).forEach { item ->
            used += ru.vzvod.konspekt.logic.DocxImages.namesIn(item.edits.values)
        }
        ru.vzvod.konspekt.logic.DocxImages.cleanup(context, used)
    }

    private fun load() {
        runCatching {
            if (!file.exists()) {
                // Первый запуск: кладём показательный конспект, чтобы было что открыть.
                archive.add(DemoLesson.build())
                return
            }
            val root = JSONObject(file.readText())
            val s = root.optJSONObject("settings")
            if (s != null) {
                settings = Settings(
                    leader = s.optString("leader", ""),
                    unitName = s.optString("unitName", ""),
                    approver = s.optString("approver", "Командир роты"),
                    defaultMinutes = s.optInt("defaultMinutes", 90),
                    darkTheme = s.optBoolean("darkTheme", false),
                    landscape = s.optBoolean("landscape", false)
                )
            }
            root.optJSONObject("draft")?.let { draft = fromJson(it) }
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
                    .put("landscape", settings.landscape)
            )
            draft?.let { root.put("draft", toJson(it)) }
            val arr = JSONArray()
            archive.forEach { arr.put(toJson(it)) }
            root.put("archive", arr)
            file.writeText(root.toString())
        }
    }

    private fun toJson(i: LessonInput) = LessonJson.write(i)

    private fun fromJson(o: JSONObject) = LessonJson.read(o)
}
