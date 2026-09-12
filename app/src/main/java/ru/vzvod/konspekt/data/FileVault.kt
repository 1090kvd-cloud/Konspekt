package ru.vzvod.konspekt.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Файлы, которые приложение хранит у себя: приложенные материалы и оригиналы
 * загруженных конспектов. Раньше это было написано дважды и успело разойтись,
 * теперь вся работа с файлами в одном месте.
 */
object FileVault {

    fun dir(context: Context, name: String): File =
        File(context.filesDir, name).apply { if (!exists()) mkdirs() }

    fun file(context: Context, folder: String, stored: String): File? {
        if (stored.isBlank() || stored.contains('/') || stored.contains("..")) return null
        val f = File(dir(context, folder), stored)
        return if (f.exists()) f else null
    }

    /** Копирует выбранный файл к себе и возвращает внутреннее имя. */
    fun save(context: Context, folder: String, uri: Uri, fileName: String): String? = runCatching {
        val ext = fileName.substringAfterLast('.', "").take(10)
        val stored = if (ext.isEmpty()) UUID.randomUUID().toString()
        else "${UUID.randomUUID()}.$ext"
        val target = File(dir(context, folder), stored)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        stored
    }.getOrNull()

    fun remove(context: Context, folder: String, stored: String) {
        if (stored.isBlank()) return
        runCatching { File(dir(context, folder), stored).delete() }
    }

    fun uriFor(context: Context, f: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", f)

    /** Открывает файл той программой, которая на телефоне отвечает за формат. */
    fun open(context: Context, folder: String, stored: String, mime: String) {
        val f = file(context, folder, stored) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, f), mime)
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

    fun send(context: Context, folder: String, stored: String, mime: String, title: String, chooser: String) {
        val f = file(context, folder, stored) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uriFor(context, f))
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooser))
    }

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: "Документ"

    fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
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
            "djvu" -> "image/vnd.djvu"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
}
