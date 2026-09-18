package ru.vzvod.konspekt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vzvod.konspekt.logic.Generator
import ru.vzvod.konspekt.logic.MaterialSearch
import ru.vzvod.konspekt.model.LessonInput

/** Черновик одного учебного вопроса: его можно переписать, разделить или слить с соседом. */
private class QuestionDraft(title: String, content: String, trainee: String, minutes: String) {
    var title by mutableStateOf(title)
    var content by mutableStateOf(content)
    var trainee by mutableStateOf(trainee)
    var minutes by mutableStateOf(minutes)
}

/**
 * Правка готового документа.
 *
 * Меняется не только текст, но и структура: учебные вопросы добавляются, удаляются,
 * объединяются и делятся. Разбор загруженного конспекта не всегда угадывает границы
 * вопросов — поправить их должно быть можно здесь, а не заводить занятие заново.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    input: LessonInput,
    onCancel: () -> Unit,
    onSave: (LessonInput) -> Unit
) {
    val plan = remember(input.id) { Generator.build(input) }
    val raw = remember(input.id) { Generator.build(input, applyEdits = false) }

    var goals by remember(input.id) { mutableStateOf(plan.goals.join()) }
    var provision by remember(input.id) { mutableStateOf(plan.provision.join()) }
    var safety by remember(input.id) { mutableStateOf(plan.safety.join()) }
    var introContent by remember(input.id) { mutableStateOf(plan.intro.content.join()) }
    var introTrainee by remember(input.id) { mutableStateOf(plan.intro.trainee.join()) }
    var outroContent by remember(input.id) { mutableStateOf(plan.outro.content.join()) }
    var outroTrainee by remember(input.id) { mutableStateOf(plan.outro.trainee.join()) }
    var control by remember(input.id) { mutableStateOf(plan.control.join()) }
    var totalMinutes by remember(input.id) { mutableStateOf(input.minutes.toString()) }
    var introMinutes by remember(input.id) { mutableStateOf(plan.intro.minutes.toString()) }
    var outroMinutes by remember(input.id) { mutableStateOf(plan.outro.minutes.toString()) }

    val questions = remember(input.id) {
        mutableStateListOf<QuestionDraft>().apply {
            plan.questions.forEach {
                add(
                    QuestionDraft(
                        title = it.title,
                        content = it.content.join(),
                        trainee = it.trainee.join(),
                        minutes = it.minutes.toString()
                    )
                )
            }
        }
    }

    var picking by remember { mutableStateOf<Int?>(null) }
    var splitting by remember { mutableStateOf<Int?>(null) }

    fun collect(): LessonInput {
        val edits = HashMap<String, String>()
        fun put(key: String, value: String, default: List<String>) {
            val v = value.trim()
            if (v.isNotEmpty() && v != default.join().trim()) edits[key] = v
        }
        put(Generator.Keys.GOALS, goals, raw.goals)
        put(Generator.Keys.PROVISION, provision, raw.provision)
        put(Generator.Keys.SAFETY, safety, raw.safety)
        put(Generator.Keys.INTRO_CONTENT, introContent, raw.intro.content)
        put(Generator.Keys.INTRO_TRAINEE, introTrainee, raw.intro.trainee)
        put(Generator.Keys.OUTRO_CONTENT, outroContent, raw.outro.content)
        put(Generator.Keys.OUTRO_TRAINEE, outroTrainee, raw.outro.trainee)
        put(Generator.Keys.CONTROL, control, raw.control)
        edits[Generator.Keys.INTRO_MIN] = minutesOf(introMinutes, plan.intro.minutes)
        edits[Generator.Keys.OUTRO_MIN] = minutesOf(outroMinutes, plan.outro.minutes)

        questions.forEachIndexed { i, q ->
            val n = i + 1
            if (q.title.isNotBlank()) edits[Generator.Keys.qTitle(n)] = q.title.trim()
            if (q.content.isNotBlank()) edits[Generator.Keys.qContent(n)] = q.content.trim()
            if (q.trainee.isNotBlank()) edits[Generator.Keys.qTrainee(n)] = q.trainee.trim()
            q.minutes.trim().toIntOrNull()?.let { edits[Generator.Keys.qMinutes(n)] = it.toString() }
        }

        return input.copy(
            minutes = totalMinutes.trim().toIntOrNull()?.coerceIn(1, 600) ?: input.minutes,
            questionCount = questions.size.coerceIn(1, 6),
            customQuestions = questions.map { it.title.trim() }.filter { it.isNotEmpty() },
            edits = edits
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Правка конспекта", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Строка — один пункт документа",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Отменить") }
                },
                actions = {
                    IconButton(onClick = { onSave(collect()) }) {
                        Icon(Icons.Filled.Done, "Сохранить правки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Время правится здесь же: после сборки часто выясняется,
            // что на вопрос нужно не десять минут, а двенадцать.
            Section("Время занятия", "Любое число минут, не обязательно кратное пяти") {
                Row {
                    MinutesField("Всего", totalMinutes) { totalMinutes = it }
                    Spacer(Modifier.width(10.dp))
                    MinutesField("Вводная", introMinutes) { introMinutes = it }
                    Spacer(Modifier.width(10.dp))
                    MinutesField("Заключит.", outroMinutes) { outroMinutes = it }
                }
                val sum = (introMinutes.toIntOrNull() ?: 0) +
                    (outroMinutes.toIntOrNull() ?: 0) +
                    questions.sumOf { it.minutes.toIntOrNull() ?: 0 }
                val total = totalMinutes.toIntOrNull() ?: 0
                Spacer(Modifier.height(6.dp))
                Text(
                    if (sum == total) "По частям: $sum мин — сходится"
                    else "По частям: $sum мин, в шапке: $total мин — не сходится",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sum == total) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
            }

            Section("Цели занятия", "Нумерация проставится сама") {
                Field(goals) { goals = it }
                ResetLink(goals, raw.goals.join()) { goals = raw.goals.join() }
            }
            Section("Материальное обеспечение", "Каждая строка — отдельный пункт") {
                Field(provision) { provision = it }
                ResetLink(provision, raw.provision.join()) { provision = raw.provision.join() }
            }
            if (raw.safety.isNotEmpty() || safety.isNotBlank()) {
                Section("Требования безопасности", "Каждая строка — отдельный пункт") {
                    Field(safety) { safety = it }
                    ResetLink(safety, raw.safety.join()) { safety = raw.safety.join() }
                }
            }

            Section("Вводная часть", "От первого лица: «Проверяю…», «Довожу…»") {
                Field(introContent) { introContent = it }
                Spacer(Modifier.height(10.dp))
                Label("Действия обучаемых")
                Field(introTrainee) { introTrainee = it }
            }

            questions.forEachIndexed { idx, q ->
                Section(
                    "Учебный вопрос № ${idx + 1}",
                    "Наименование, содержание и время"
                ) {
                    OutlinedTextField(
                        value = q.title,
                        onValueChange = { q.title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Наименование") },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedTextField(
                            value = q.minutes,
                            onValueChange = { v -> q.minutes = v.filter { it.isDigit() }.take(3) },
                            label = { Text("Минут") },
                            singleLine = true,
                            modifier = Modifier.width(110.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Label("Содержание")
                    Field(q.content) { q.content = it }
                    Spacer(Modifier.height(10.dp))
                    Label("Действия обучаемых")
                    Field(q.trainee) { q.trainee = it }

                    Spacer(Modifier.height(6.dp))
                    ThinRule()
                    Spacer(Modifier.height(4.dp))
                    QuestionTools(
                        canSplit = q.content.lines().count { it.isNotBlank() } > 1,
                        canMerge = idx < questions.lastIndex && questions.size > 1,
                        canDelete = questions.size > 1,
                        onSearch = { picking = idx },
                        onSplit = { splitting = idx },
                        onMerge = {
                            val next = questions[idx + 1]
                            q.content = (q.content.trimEnd() + "\n" + next.content.trim()).trim()
                            q.trainee = (q.trainee.trimEnd() + "\n" + next.trainee.trim()).trim()
                            q.minutes = ((q.minutes.toIntOrNull() ?: 0) +
                                (next.minutes.toIntOrNull() ?: 0)).toString()
                            questions.removeAt(idx + 1)
                        },
                        onDelete = { questions.removeAt(idx) }
                    )
                }
            }

            if (questions.size < 6) {
                TextButton(
                    onClick = {
                        questions.add(QuestionDraft("Новый учебный вопрос", "", "", "10"))
                    },
                    modifier = Modifier.padding(start = 20.dp)
                ) { Text("Добавить учебный вопрос") }
            }

            Section("Заключительная часть", "Каждая строка — отдельный пункт") {
                Field(outroContent) { outroContent = it }
                Spacer(Modifier.height(10.dp))
                Label("Действия обучаемых")
                Field(outroTrainee) { outroTrainee = it }
            }

            if (raw.control.isNotEmpty() || control.isNotBlank()) {
                Section("Контрольные вопросы", "Каждая строка — отдельный вопрос") {
                    Field(control) { control = it }
                    ResetLink(control, raw.control.join()) { control = raw.control.join() }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    picking?.let { idx ->
        MaterialsPickDialog(
            disciplineId = input.disciplineId,
            query = questions[idx].title + " " + input.topic,
            onDismiss = { picking = null },
            onInsert = { chunk ->
                val cur = questions[idx].content.trimEnd()
                questions[idx].content = if (cur.isEmpty()) chunk else "$cur\n$chunk"
                picking = null
            }
        )
    }

    splitting?.let { idx ->
        SplitDialog(
            lines = questions[idx].content.lines().filter { it.isNotBlank() },
            onDismiss = { splitting = null },
            onSplit = { at ->
                val all = questions[idx].content.lines().filter { it.isNotBlank() }
                val head = all.take(at)
                val tail = all.drop(at)
                val minutes = questions[idx].minutes.toIntOrNull() ?: 10
                questions[idx].content = head.joinToString("\n")
                questions[idx].minutes = (minutes / 2).coerceAtLeast(5).toString()
                questions.add(
                    idx + 1,
                    QuestionDraft(
                        title = tail.first().take(120),
                        content = tail.drop(1).joinToString("\n"),
                        trainee = "",
                        minutes = (minutes - minutes / 2).coerceAtLeast(5).toString()
                    )
                )
                splitting = null
            }
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ResetLink(current: String, default: String, onReset: () -> Unit) {
    if (current.trim() != default.trim()) {
        TextButton(onClick = onReset) { Text("Вернуть шаблонный текст") }
    }
}

@Composable
private fun QuestionTools(
    canSplit: Boolean,
    canMerge: Boolean,
    canDelete: Boolean,
    onSearch: () -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row {
            TextButton(onClick = onSearch) { Text("Взять из материалов") }
            if (canSplit) TextButton(onClick = onSplit) { Text("Разделить") }
        }
        Row {
            if (canMerge) TextButton(onClick = onMerge) { Text("Слить со следующим") }
            if (canDelete) TextButton(onClick = onDelete) { Text("Удалить вопрос") }
        }
    }
}

/** Выбор строки, с которой начинается новый учебный вопрос. */
@Composable
private fun SplitDialog(
    lines: List<String>,
    onDismiss: () -> Unit,
    onSplit: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("С какой строки начать новый вопрос?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Выбранная строка станет наименованием нового вопроса, " +
                        "всё после неё — его содержанием.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                lines.forEachIndexed { i, line ->
                    if (i == 0) return@forEachIndexed
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSplit(i) }
                            .padding(vertical = 8.dp)
                    )
                    ThinRule()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Показывает куски приложенных методичек, где встречаются слова из учебного вопроса.
 * Что именно брать — решает руководитель занятия.
 */
@Composable
private fun MaterialsPickDialog(
    disciplineId: String,
    query: String,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    val context = LocalContext.current
    var outcome by remember { mutableStateOf<MaterialSearch.Outcome?>(null) }
    val chosen = remember { mutableStateListOf<String>() }

    LaunchedEffect(disciplineId, query) {
        outcome = withContext(Dispatchers.IO) {
            MaterialSearch.search(context, disciplineId, query)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Из приложенных материалов") },
        text = {
            val result = outcome
            when {
                result == null -> Text("Читаю материалы…", style = MaterialTheme.typography.bodyMedium)

                result.fragments.isEmpty() -> Column {
                    Text("Подходящих кусков не нашлось.", style = MaterialTheme.typography.bodyMedium)
                    if (result.skipped.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        result.skipped.forEach { (name, why) ->
                            Text(
                                "$name — $why",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Ищется только в текстовых методичках: .docx, .txt, .rtf. " +
                            "Проверьте текст перед вставкой.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    result.fragments.forEach { f ->
                        val picked = chosen.contains(f.text)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (picked) chosen.remove(f.text) else chosen.add(f.text)
                                }
                        ) {
                            Text(
                                (if (picked) "✓ " else "") + f.materialTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (picked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                f.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                            ThinRule(Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen.isNotEmpty(),
                onClick = { onInsert(chosen.joinToString("\n")) }
            ) { Text(if (chosen.isEmpty()) "Вставить" else "Вставить (${chosen.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private fun List<String>.join() = joinToString("\n")

/** Поле для минут: принимает любое число, не только кратное пяти. */
@Composable
private fun MinutesField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(3)) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(104.dp)
    )
}

private fun minutesOf(text: String, fallback: Int): String =
    (text.trim().toIntOrNull()?.coerceIn(1, 600) ?: fallback).toString()
