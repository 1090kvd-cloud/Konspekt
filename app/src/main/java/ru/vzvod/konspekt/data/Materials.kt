package ru.vzvod.konspekt.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import ru.vzvod.konspekt.model.Material
import java.io.File
import java.util.UUID

/**
 * Справочные материалы: методички, выписки, плакаты, таблицы нормативов.
 *
 * Файл любого формата копируется в память приложения и остаётся там навсегда —
 * даже если исходный удалить или переставить. Открывается той программой,
 * которая на телефоне уже умеет такой формат. Приложение содержимое не читает.
 */
object Materials {

    val all = mutableStateListOf<Material>()

    private const val FOLDER = "materials"

    private fun dir(context: Context): File = FileVault.dir(context, FOLDER)

    private fun index(context: Context) = File(context.filesDir, "materials.json")

    fun forDiscipline(disciplineId: String): List<Material> =
        all.filter { it.disciplineId == null || it.disciplineId == disciplineId }

    fun load(context: Context) {
        val f = index(context)
        if (!f.exists()) return
        runCatching {
            val arr = JSONArray(f.readText())
            all.clear()
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                val m = Material(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    storedName = o.optString("storedName"),
                    mime = o.optString("mime", "application/octet-stream"),
                    size = o.optLong("size", 0L),
                    disciplineId = o.optString("disciplineId", "").ifBlank { null },
                    addedAt = o.optLong("addedAt", 0L)
                )
                if (File(dir(context), m.storedName).exists()) all.add(m)
            }
        }
    }

    private fun persist(context: Context) {
        runCatching {
            val arr = JSONArray()
            all.forEach { m ->
                arr.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("title", m.title)
                        .put("storedName", m.storedName)
                        .put("mime", m.mime)
                        .put("size", m.size)
                        .put("disciplineId", m.disciplineId ?: "")
                        .put("addedAt", m.addedAt)
                )
            }
            index(context).writeText(arr.toString())
        }
    }

    fun add(context: Context, uri: Uri, disciplineId: String?): Result<Material> = runCatching {
        val name = FileVault.displayName(context, uri)
        val id = UUID.randomUUID().toString()
        val stored = FileVault.save(context, FOLDER, uri, name)
            ?: throw Exception("Файл недоступен для чтения")
        val target = File(dir(context), stored)

        val material = Material(
            id = id,
            // В документ уходит наименование, поэтому чистим его сразу.
            title = cleanName(name),
            storedName = stored,
            mime = context.contentResolver.getType(uri) ?: FileVault.mimeOf(name),
            size = target.length(),
            disciplineId = disciplineId,
            addedAt = System.currentTimeMillis()
        )
        all.add(0, material)
        persist(context)
        material
    }

    fun rename(context: Context, id: String, title: String) {
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(title = title.trim().ifBlank { all[idx].title })
            persist(context)
        }
    }

    fun retarget(context: Context, id: String, disciplineId: String?) {
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(disciplineId = disciplineId)
            persist(context)
        }
    }

    fun remove(context: Context, id: String) {
        val m = all.firstOrNull { it.id == id } ?: return
        FileVault.remove(context, FOLDER, m.storedName)
        ru.vzvod.konspekt.logic.TextExtract.forget(context, id)
        all.removeAll { it.id == id }
        persist(context)
    }

    /** Открывает файл тем приложением, которое на телефоне отвечает за формат. */
    fun open(context: Context, m: Material) =
        FileVault.open(context, FOLDER, m.storedName, m.mime)

    /** Передать материал товарищу — тем же способом, что и конспект. */
    fun send(context: Context, m: Material) =
        FileVault.send(context, FOLDER, m.storedName, m.mime, m.title, "Отправить материал")

    /** «План-конспект_материальная_часть.doc» -> «План-конспект материальная часть». */
    fun cleanName(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        return base.replace('_', ' ').replace('-', ' ').trim().ifEmpty { fileName }
    }

    fun sizeText(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format("%.1f МБ", bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} КБ"
        else -> "$bytes Б"
    }

    /** Тип берём из сохранённого файла: наименование уже без расширения. */
    fun kindText(m: Material): String {
        val ext = m.storedName.substringAfterLast('.', "").uppercase()
        if (ext.isNotEmpty() && ext.length <= 5) return ext
        return m.mime.substringAfterLast('/').uppercase()
    }
}
