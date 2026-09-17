package ru.vzvod.konspekt.data

import android.content.Context
import android.content.Intent
import ru.vzvod.konspekt.model.LessonInput
import java.io.File

/**
 * Передача конспектов из рук в руки.
 *
 * Один взводный написал — остальные получили готовое занятие себе в архив.
 * Уходит обычный файл: мессенджером, по Bluetooth, на флешке. Интернет не нужен,
 * приложение на той стороне узнаёт формат само и добавляет занятие к своим,
 * ничего не затирая.
 */
object ShareLesson {

    private fun dir(context: Context): File =
        File(context.cacheDir, "share").apply { if (!exists()) mkdirs() }

    /** Имя файла из темы занятия: получателю сразу понятно, что пришло. */
    private fun fileName(items: List<LessonInput>): String {
        if (items.size > 1) return "konspekty-${items.size}.json"
        val title = items.first().let { it.lessonTitle.ifBlank { it.topic } }
        val safe = title
            .replace(Regex("[^\\p{L}\\p{N} \\-]"), "")
            .trim()
            .replace(' ', '_')
            .take(60)
            .ifEmpty { "konspekt" }
        return "$safe.json"
    }

    fun send(context: Context, items: List<LessonInput>): Boolean = runCatching {
        if (items.isEmpty()) return false
        // Старые выгрузки не копим: каталог обмена чистится перед каждой отправкой.
        dir(context).listFiles()?.forEach { it.delete() }

        val file = File(dir(context), fileName(items))
        file.writeText(LessonsJson.encode(items))

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, FileVault.uriFor(context, file))
            putExtra(
                Intent.EXTRA_SUBJECT,
                if (items.size > 1) "Конспекты: ${items.size}"
                else items.first().let { it.lessonTitle.ifBlank { it.topic } }
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                intent,
                if (items.size > 1) "Отправить конспекты" else "Отправить конспект"
            )
        )
        true
    }.getOrDefault(false)
}
