package ru.vzvod.konspekt.data

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
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

    private fun dir(context: Context): File =
        File(context.filesDir, "materials").apply { if (!exists()) mkdirs() }

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
        val cr = context.contentResolver
        val name = displayName(cr, uri) ?: "Материал"
        val id = UUID.randomUUID().toString()
        val ext = name.substringAfterLast('.', "").take(10)
        val stored = if (ext.isEmpty()) id else "$id.$ext"
        val target = File(dir(context), stored)

        val input = cr.openInputStream(uri) ?: throw Exception("Файл недоступен для чтения")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }

        val material = Material(
            id = id,
            // В документ уходит наименование, поэтому чистим его сразу.
            title = cleanName(name),
            storedName = stored,
            mime = cr.getType(uri) ?: guessMime(name),
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
        File(dir(context), m.storedName).delete()
        ru.vzvod.konspekt.logic.TextExtract.forget(context, id)
        all.removeAll { it.id == id }
        persist(context)
    }

    private fun uriFor(context: Context, m: Material): Uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".files",
        File(dir(context), m.storedName)
    )

    /** Открывает файл тем приложением, которое на телефоне отвечает за этот формат. */
    fun open(context: Context, m: Material) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, m), m.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "На телефоне нет программы, открывающей такой файл",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Передать материал товарищу — тем же способом, что и конспект. */
    fun send(context: Context, m: Material) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = m.mime
            putExtra(Intent.EXTRA_STREAM, uriFor(context, m))
            putExtra(Intent.EXTRA_SUBJECT, m.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить материал"))
    }

    fun nameOf(context: Context, uri: Uri): String =
        displayName(context.contentResolver, uri) ?: "Документ"

    private fun displayName(cr: ContentResolver, uri: Uri): String? = runCatching {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "rtf" -> "application/rtf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "zip" -> "application/zip"
        "djvu" -> "image/vnd.djvu"
        else -> "application/octet-stream"
    }

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
