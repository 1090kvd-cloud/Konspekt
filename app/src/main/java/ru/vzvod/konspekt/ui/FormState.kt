package ru.vzvod.konspekt.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.Settings
import java.util.UUID

/** Живое состояние формы. Живёт выше экранов, поэтому ввод не теряется при переходах. */
class FormState(settings: Settings) {

    var editingId: String? = null
    var disciplineId by mutableStateOf("general")

    /** Строка «ТЕМА N:» — раздел программы. */
    var themeNo by mutableStateOf("1")
    var topic by mutableStateOf("")

    /** Строка «ЗАНЯТИЕ N:» — наименование самого занятия. */
    var lessonNo by mutableStateOf("1")
    var lessonTitle by mutableStateOf("")

    var minutes by mutableStateOf(settings.defaultMinutes)
    var questionCount by mutableStateOf(3)
    var place by mutableStateOf("")
    var method by mutableStateOf("")
    var unitName by mutableStateOf("")
    var date by mutableStateOf("")
    var leader by mutableStateOf("")
    var note by mutableStateOf("")
    var includeHandout by mutableStateOf(false)
    var includeControl by mutableStateOf(true)
    var includeSafety by mutableStateOf(true)

    val customQuestions = mutableStateListOf<String>()

    /** Ручные правки текста переносятся при пересборке занятия, иначе они бы пропали. */
    private var edits: Map<String, String> = emptyMap()

    /** Имя исходного файла: при пересборке связь с оригиналом не должна теряться. */
    private var sourceName: String = ""

    fun effectiveQuestionCount(): Int {
        val filled = customQuestions.count { it.isNotBlank() }
        return if (filled > 0) filled else questionCount
    }

    fun toInput(defaults: Settings): LessonInput = LessonInput(
        id = editingId ?: UUID.randomUUID().toString(),
        createdAt = System.currentTimeMillis(),
        disciplineId = disciplineId,
        topic = topic.trim(),
        themeNo = themeNo.trim().ifBlank { "1" },
        lessonNo = lessonNo.trim().ifBlank { "1" },
        // Пустое наименование занятия — лишнее поле для заполнения: берём тему.
        lessonTitle = lessonTitle.trim().ifBlank { topic.trim() },
        minutes = minutes,
        place = place.trim(),
        method = method.trim(),
        unitName = unitName.trim().ifBlank { defaults.unitName },
        date = date.trim(),
        leader = leader.trim().ifBlank { defaults.leader },
        questionCount = questionCount,
        customQuestions = customQuestions.map { it.trim() }.filter { it.isNotEmpty() },
        includeHandout = includeHandout,
        includeControl = includeControl,
        includeSafety = includeSafety,
        note = note.trim(),
        edits = this.edits,
        sourceName = this.sourceName
    )

    fun loadFrom(i: LessonInput) {
        editingId = i.id
        disciplineId = i.disciplineId
        topic = i.topic
        themeNo = i.themeNo
        lessonNo = i.lessonNo
        lessonTitle = i.lessonTitle
        minutes = i.minutes
        questionCount = i.questionCount
        place = i.place
        method = i.method
        unitName = i.unitName
        date = i.date
        leader = i.leader
        note = i.note
        includeHandout = i.includeHandout
        includeControl = i.includeControl
        includeSafety = i.includeSafety
        edits = i.edits
        sourceName = i.sourceName
        customQuestions.clear()
        customQuestions.addAll(i.customQuestions)
    }

    fun reset(settings: Settings) {
        editingId = null
        // Предмет тоже сбрасываем: иначе «новое занятие» выглядит как ничего не произошло.
        disciplineId = "general"
        topic = ""
        themeNo = "1"
        lessonNo = "1"
        lessonTitle = ""
        minutes = settings.defaultMinutes
        questionCount = 3
        place = ""
        method = ""
        unitName = settings.unitName
        date = ""
        leader = settings.leader
        note = ""
        includeHandout = false
        includeControl = true
        includeSafety = true
        edits = emptyMap()
        sourceName = ""
        customQuestions.clear()
    }
}
