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
    var topic by mutableStateOf("")
    var minutes by mutableStateOf(settings.defaultMinutes)
    var questionCount by mutableStateOf(3)
    var lessonNo by mutableStateOf("1")
    var place by mutableStateOf("")
    var method by mutableStateOf("")
    var unitName by mutableStateOf("")
    var date by mutableStateOf("")
    var leader by mutableStateOf("")
    var note by mutableStateOf("")
    var includeHandout by mutableStateOf(true)
    var includeControl by mutableStateOf(true)
    var includeSafety by mutableStateOf(true)

    val customQuestions = mutableStateListOf<String>()

    fun effectiveQuestionCount(): Int {
        val filled = customQuestions.count { it.isNotBlank() }
        return if (filled > 0) filled else questionCount
    }

    fun toInput(defaults: Settings): LessonInput = LessonInput(
        id = editingId ?: UUID.randomUUID().toString(),
        createdAt = System.currentTimeMillis(),
        disciplineId = disciplineId,
        topic = topic.trim(),
        lessonNo = lessonNo.trim(),
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
        note = note.trim()
    )

    fun loadFrom(i: LessonInput) {
        editingId = i.id
        disciplineId = i.disciplineId
        topic = i.topic
        minutes = i.minutes
        questionCount = i.questionCount
        lessonNo = i.lessonNo
        place = i.place
        method = i.method
        unitName = i.unitName
        date = i.date
        leader = i.leader
        note = i.note
        includeHandout = i.includeHandout
        includeControl = i.includeControl
        includeSafety = i.includeSafety
        customQuestions.clear()
        customQuestions.addAll(i.customQuestions)
    }

    fun reset(settings: Settings) {
        editingId = null
        topic = ""
        minutes = settings.defaultMinutes
        questionCount = 3
        lessonNo = "1"
        place = ""
        method = ""
        unitName = ""
        date = ""
        leader = ""
        note = ""
        includeHandout = true
        includeControl = true
        includeSafety = true
        customQuestions.clear()
    }
}
