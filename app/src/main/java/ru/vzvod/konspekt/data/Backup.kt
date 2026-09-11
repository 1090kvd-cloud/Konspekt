package ru.vzvod.konspekt.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Резервная копия одним файлом: архив занятий, настройки, библиотека предметов
 * и все приложенные материалы. Нужна при смене телефона — иначе всё придётся набирать заново.
 */
object Backup {

    private val topLevel = listOf("konspekt.json", "library.json", "materials.json")
    private const val MATERIALS = "materials/"

    fun write(context: Context, out: OutputStream): Result<Int> = runCatching {
        var count = 0
        ZipOutputStream(out).use { zip ->
            topLevel.forEach { name ->
                val f = File(context.filesDir, name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
            val dir = File(context.filesDir, "materials")
            dir.listFiles()?.forEach { f ->
                if (f.isFile) {
                    zip.putNextEntry(ZipEntry(MATERIALS + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        count
    }

    fun read(context: Context, input: InputStream): Result<Int> = runCatching {
        var count = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                val name = entry.name
                val target = when {
                    name in topLevel -> File(context.filesDir, name)
                    name.startsWith(MATERIALS) && !name.contains("..") && name.length > MATERIALS.length ->
                        File(context.filesDir, "materials").apply { mkdirs() }
                            .let { File(it, name.substring(MATERIALS.length)) }
                    else -> null
                }
                if (target != null && !entry.isDirectory) {
                    // Проверка на выход за пределы каталога приложения.
                    if (target.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                        target.outputStream().use { zip.copyTo(it) }
                        count++
                    }
                }
                zip.closeEntry()
            }
        }
        if (count == 0) throw Exception("В файле нет данных приложения")
        count
    }
}
