package ru.vzvod.konspekt.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.vzvod.konspekt.model.Discipline
import java.io.File

/**
 * Библиотека, с которой работает приложение прямо сейчас.
 *
 * Внутри APK лежит встроенный набор — он есть всегда.
 * Поверх него можно положить свой файл: собранный на компьютере или полученный
 * от товарища через мессенджер. Файл сохраняется на телефоне.
 * Никакой сети: приложение не имеет разрешения на выход в интернет.
 */
object Library {

    const val BUILT_IN_VERSION = 1

    var all by mutableStateOf(Disciplines.all)
        private set
    var version by mutableIntStateOf(BUILT_IN_VERSION)
        private set
    var updated by mutableStateOf("")
        private set
    var origin by mutableStateOf("встроенная")
        private set

    fun byId(id: String): Discipline = all.firstOrNull { it.id == id } ?: all.first()

    private fun file(context: Context) = File(context.filesDir, "library.json")

    /** Вызывается один раз при старте. Битый файл молча игнорируется — остаётся встроенный набор. */
    fun load(context: Context) {
        val f = file(context)
        if (!f.exists()) return
        runCatching { LibraryJson.decode(f.readText()) }
            .onSuccess { apply(it, "файл на телефоне") }
    }

    private fun apply(p: LibraryJson.Parsed, from: String) {
        all = p.disciplines
        version = p.version
        updated = p.updated
        origin = from
    }

    /** Проверяет содержимое, сохраняет и подставляет. Возвращает количество предметов. */
    fun install(context: Context, text: String, from: String): Result<Int> = runCatching {
        val parsed = LibraryJson.decode(text)
        file(context).writeText(text)
        apply(parsed, from)
        parsed.disciplines.size
    }

    fun reset(context: Context) {
        file(context).delete()
        all = Disciplines.all
        version = BUILT_IN_VERSION
        updated = ""
        origin = "встроенная"
    }

    /** Текущая библиотека в формате обмена — чтобы отдать товарищу или править на компьютере. */
    fun export(): String = LibraryJson.encode(version, updated, all)

}
