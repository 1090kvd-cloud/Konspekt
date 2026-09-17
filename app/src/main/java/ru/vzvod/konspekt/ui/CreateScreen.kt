package ru.vzvod.konspekt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.data.Materials
import ru.vzvod.konspekt.logic.AutoFill
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.data.Program
import ru.vzvod.konspekt.model.ProgramItem
import ru.vzvod.konspekt.model.Settings
import ru.vzvod.konspekt.logic.Generator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    form: FormState,
    settings: Settings,
    archive: List<LessonInput>,
    onBuild: () -> Unit,
    busy: Boolean = false,
    onSeries: () -> Unit,
    onNew: () -> Unit,
    onNext: (ProgramItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val discipline = Library.byId(form.disciplineId)
    var showDetails by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTopic by remember { mutableStateOf(false) }
    // Сетка предметов раскрыта только пока предмет не выбран осмысленно.
    var pickDiscipline by remember { mutableStateOf(form.disciplineId == "general") }

    // Значения из настроек сразу видны в форме, а не только в готовом документе.
    LaunchedEffect(settings.unitName, settings.leader) {
        if (form.editingId == null) {
            if (form.unitName.isBlank()) form.unitName = settings.unitName
            if (form.leader.isBlank()) form.leader = settings.leader
        }
    }

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Программа знает, какое занятие следующее: вводить нечего.
        val next = Program.next()
        if (next != null) {
            Section(
                "Следующее по программе",
                "Осталось занятий: ${Program.remaining()} · ${Program.remainingMinutes() / 45} ч"
            ) {
                Text(
                    "ТЕМА ${next.themeNo}: ${next.topic}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (next.lessonTitle.isNotBlank()) {
                    Text(
                        "ЗАНЯТИЕ ${next.lessonNo}: ${next.lessonTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${Library.byId(next.disciplineId).name} · ${next.minutes} мин",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onNext(next) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Собрать это занятие") }
            }
        }

        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp)) {
            TextButton(onClick = onNew) { Text("Новое занятие") }
            TextButton(onClick = onSeries, enabled = form.topic.isNotBlank()) {
                Text("Серия по теме")
            }
        }

        Section("Предмет обучения") {
            if (pickDiscipline) {
                TileGrid(Library.all) { d, m ->
                    ChoiceTile(
                        icon = disciplineIcon(d.id),
                        label = d.short,
                        selected = d.id == form.disciplineId,
                        modifier = m
                    ) {
                        form.disciplineId = d.id
                        pickDiscipline = false
                    }
                }
            } else {
                CollapsedChoice(
                    icon = disciplineIcon(discipline.id),
                    label = discipline.name,
                    hint = "Нажмите, чтобы выбрать другой предмет"
                ) { pickDiscipline = true }
            }
        }

        val attached = Materials.forDiscipline(form.disciplineId).size
        if (attached > 0) {
            Text(
                "Приложено материалов по предмету: $attached. " +
                    "Их наименования войдут в материальное обеспечение, " +
                    "а текст можно взять при правке конспекта.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        Section("Тема занятия", "Идёт в документ строкой «ТЕМА N:»") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = form.themeNo,
                    onValueChange = { form.themeNo = it },
                    label = { Text("Тема №") },
                    singleLine = true,
                    modifier = Modifier.width(104.dp)
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = form.lessonNo,
                    onValueChange = { form.lessonNo = it },
                    label = { Text("Занятие №") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = form.topic,
                onValueChange = { form.topic = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Тема") },
                placeholder = { Text("Материальная часть автомата, ручных гранат, боеприпасы") },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyLarge
            )
            // Что уже писали по этому предмету — подставляется одним нажатием.
            val hints = AutoFill.suggestTopics(archive, form.disciplineId, form.topic)
            if (hints.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Уже было по этому предмету:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                hints.forEach { h ->
                    Text(
                        h,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { form.topic = h }
                            .padding(vertical = 8.dp)
                    )
                }
            }
            if (discipline.topics.isNotEmpty()) {
                TextButton(onClick = { pickTopic = true }) { Text("Выбрать типовую тему") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = form.lessonTitle,
                onValueChange = { form.lessonTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название занятия (необязательно)") },
                placeholder = { Text("Пусто — соберётся из учебных вопросов") },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        Section("Время", timeBreakdown(form.minutes, form.effectiveQuestionCount())) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(45, 50, 90, 120, 180).forEach { m ->
                    FilterChip(
                        selected = form.minutes == m,
                        onClick = { form.minutes = m },
                        label = { Text("$m мин") }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Точное время",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Stepper(
                    value = form.minutes,
                    onChange = { form.minutes = it },
                    range = 15..360,
                    step = 5,
                    suffix = " мин"
                )
            }
        }

        Section(
            "Учебные вопросы",
            if (form.customQuestions.isEmpty()) "Сформулируются по предмету и теме" else "Заданы вручную"
        ) {
            if (form.customQuestions.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Сколько вопросов",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Stepper(
                        value = form.questionCount,
                        onChange = { form.questionCount = it },
                        range = 1..6
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    discipline.questionTemplates.first()
                        .replace("{t}", form.topic.ifBlank { "тема занятия" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            form.customQuestions.forEachIndexed { idx, q ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { form.customQuestions[idx] = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Вопрос ${idx + 1}") },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { form.customQuestions.removeAt(idx) }) {
                        Icon(Icons.Filled.Close, "Убрать вопрос", modifier = Modifier.size(20.dp))
                    }
                }
            }

            TextButton(
                onClick = { form.customQuestions.add("") },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Написать вопрос вручную")
            }
        }

        TextButton(
            onClick = { showDetails = !showDetails },
            modifier = Modifier.padding(start = 20.dp)
        ) {
            Icon(
                if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(if (showDetails) "Свернуть дополнительное" else "Шапка, место, метод, комплект")
        }

        AnimatedVisibility(visible = showDetails) {
            Column {
                Section("Шапка документа", "Пустые поля подставятся из настроек") {
                    OutlinedTextField(
                        value = form.unitName,
                        onValueChange = { form.unitName = it },
                        label = { Text("Подразделение") },
                        placeholder = {
                            Text(settings.unitName.ifBlank { "личным составом 1 мсв" })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = form.date,
                        onValueChange = { form.date = it },
                        label = { Text("Дата") },
                        trailingIcon = {
                            IconButton(onClick = { pickDate = true }) {
                                Icon(Icons.Filled.DateRange, "Выбрать дату")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        TextButton(onClick = {
                            form.date = formatDate(System.currentTimeMillis())
                        }) { Text("Сегодня") }
                        TextButton(onClick = {
                            form.date = formatDate(System.currentTimeMillis() + 86_400_000L)
                        }) { Text("Завтра") }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = form.leader,
                        onValueChange = { form.leader = it },
                        label = { Text("Руководитель занятия") },
                        placeholder = {
                            Text(settings.leader.ifBlank { "лейтенант Иванов И. И." })
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Section("Место проведения") {
                    FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                        discipline.places.forEach { p ->
                            FilterChip(
                                selected = form.place == p,
                                onClick = { form.place = if (form.place == p) "" else p },
                                label = { Text(p) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = form.place,
                        onValueChange = { form.place = it },
                        label = { Text("Или впишите своё") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Section("Метод проведения") {
                    FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                        discipline.methods.forEach { m ->
                            FilterChip(
                                selected = form.method == m,
                                onClick = { form.method = if (form.method == m) "" else m },
                                label = { Text(m) }
                            )
                        }
                    }
                }

                Section("Что вложить в комплект") {
                    ToggleRow(
                        "Раздаточный материал",
                        "Собирается из вписанного вами содержания",
                        form.includeHandout
                    ) { form.includeHandout = it }
                    ThinRule(Modifier.padding(vertical = 4.dp))
                    ToggleRow(
                        "Контрольные вопросы",
                        "Для опроса в заключительной части",
                        form.includeControl
                    ) { form.includeControl = it }
                    ThinRule(Modifier.padding(vertical = 4.dp))
                    ToggleRow(
                        "Требования безопасности",
                        "Отдельный раздел и пункт инструктажа",
                        form.includeSafety
                    ) { form.includeSafety = it }
                }

                Section("Примечания", "Попадут отдельным разделом в конспект") {
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = { form.note = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Что учесть именно на этом занятии") }
                    )
                }
            }
        }

        // Место под закреплённую кнопку, чтобы она не перекрывала последний блок.
        Spacer(Modifier.height(96.dp))
    }

    // Кнопка всегда на виду: до неё не нужно долистывать длинную форму.
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Button(
            onClick = onBuild,
            enabled = form.topic.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                when {
                    busy -> "Собираю: ищу по методичкам…"
                    form.topic.isBlank() -> "Введите тему занятия"
                    else -> "Собрать конспект"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    }

    if (pickTopic) {
        TopicPickDialog(
            topics = discipline.topics,
            onPick = { form.topic = it; pickTopic = false },
            onDismiss = { pickTopic = false }
        )
    }

    if (pickDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { form.date = pickedDate(it) }
                    pickDate = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { pickDate = false }) { Text("Отмена") }
            }
        ) { DatePicker(state = state) }
    }
}

/** Календарь отдаёт полночь по UTC — форматируем в той же зоне, иначе съедет день. */
private fun pickedDate(millis: Long): String {
    val fmt = SimpleDateFormat("d MMMM yyyy 'г.'", Locale("ru"))
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(millis))
}

/** Сегодня и завтра берутся по часам телефона. */
private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy 'г.'", Locale("ru")).format(Date(millis))

/** Живой расчёт: показывает, как время разложится по частям занятия. */
private fun timeBreakdown(minutes: Int, questions: Int): String {
    val total = maxOf(20, minutes)
    val edge = Generator.edgeMinutes(total)
    val main = maxOf(5, total - edge * 2)
    val slices = Generator.split(main, questions)
    return "Вводная $edge · основная $main (${slices.joinToString("+")}) · заключительная $edge"
}

/** Типовые темы предмета: выбрать быстрее, чем набирать. Текст потом правится. */
@Composable
private fun TopicPickDialog(
    topics: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Типовые темы") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                topics.forEach { t ->
                    Text(
                        t,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(t) }
                            .padding(vertical = 10.dp)
                    )
                    ThinRule()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
