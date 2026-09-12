package ru.vzvod.konspekt.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Исходники загруженных конспектов.
 *
 * Разбор вытаскивает из файла текст, но вёрстку, таблицы, схемы и рисунки
 * пересобрать один в один нельзя — тем более из скана, где вся страница
 * это одна картинка. Поэтому исходный файл хранится рядом с занятием:
 * текст можно править и искать по нему, а на печать при необходимости
 * уходит оригинал без единого изменения.
 */
object Sources {

    private fun dir(context: Context): File =
        File(context.filesDir, "sources").apply { if (!exists()) mkdirs() }

    fun file(context: Context, storedName: String): File? {
        if (storedName.isBlank()) return null
        val f = File(dir(context), storedName)
        return if (f.exists()) f else null
    }

    /** Кладёт копию выбранного файла к себе и возвращает внутреннее имя. */
    fun save(context: Context, uri: Uri, fileName: String): String? = runCatching {
        val ext = fileName.substringAfterLast('.', "").take(10)
        val stored = if (ext.isEmpty()) UUID.randomUUID().toString()
        else "${UUID.randomUUID()}.$ext"
        val target = File(dir(context), stored)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        stored
    }.getOrNull()

    fun remove(context: Context, storedName: String) {
        if (storedName.isBlank()) return
        runCatching { File(dir(context), storedName).delete() }
    }

    private fun uriFor(context: Context, f: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", f)

    fun open(context: Context, storedName: String) {
        val f = file(context, storedName) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, f), mimeOf(storedName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Нет программы, открывающей такой файл",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun send(context: Context, storedName: String, title: String) {
        val f = file(context, storedName) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(storedName)
            putExtra(Intent.EXTRA_STREAM, uriFor(context, f))
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить конспект"))
    }

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "rtf" -> "application/rtf"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
}
