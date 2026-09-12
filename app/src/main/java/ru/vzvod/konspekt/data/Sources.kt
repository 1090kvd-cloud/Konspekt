package ru.vzvod.konspekt.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Оригиналы загруженных конспектов.
 *
 * Чужой документ пересобрать один в один нельзя: вёрстка, таблицы, схемы
 * и рисунки у каждого свои. Поэтому исходный файл лежит рядом с занятием
 * и именно он уходит на печать и в отправку.
 */
object Sources {

    private const val FOLDER = "sources"

    fun file(context: Context, stored: String): File? =
        FileVault.file(context, FOLDER, stored)

    fun save(context: Context, uri: Uri, fileName: String): String? =
        FileVault.save(context, FOLDER, uri, fileName)

    fun remove(context: Context, stored: String) =
        FileVault.remove(context, FOLDER, stored)

    fun open(context: Context, stored: String) =
        FileVault.open(context, FOLDER, stored, FileVault.mimeOf(stored))

    fun send(context: Context, stored: String, title: String) =
        FileVault.send(context, FOLDER, stored, FileVault.mimeOf(stored), title, "Отправить конспект")
}
