package ru.vzvod.konspekt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vzvod.konspekt.logic.MaterialSearch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.logic.Generator
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.LessonPlan

private data class EditField(
    val key: String,
    val label: String,
    val hint: String,
    val default: String,
    val singleLine: Boolean = false,
    /** Если задан — под полем появляется поиск по приложенным материалам. */
    val query: String? = null
)

/**
 * Правка готового документа. Каждый раздел — обычное текстовое поле,
 * строка соответствует пункту. Сохраняется только то, что отличается от шаблона,
 * поэтому «Вернуть шаблонный текст» всегда работает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    input: LessonInput,
    onCancel: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val raw = remember(input.id) { Generator.build(input, applyEdits = false) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val fields = remember(input.id) { fieldsOf(raw) }
    val texts = remember(input.id) {
        mutableStateListOf<String>().apply {
            fields.forEach { add(input.edits[it.key] ?: it.default) }
        }
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
                    IconButton(onClick = {
                        val result = HashMap<String, String>()
                        fields.forEachIndexed { i, f ->
                            val value = texts[i]
                            if (value.trim() != f.default.trim() && value.isNotBlank()) {
                                result[f.key] = value
                            }
                        }
                        onSave(result)
                    }) { Icon(Icons.Filled.Done, "Сохранить правки") }
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
            fields.forEachIndexed { i, f ->
                Section(f.label, f.hint) {
                    OutlinedTextField(
                        value = texts[i],
                        onValueChange = { texts[i] = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = f.singleLine,
                        minLines = if (f.singleLine) 1 else 3,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        if (f.query != null) {
                            TextButton(onClick = { picking = i }) { Text("Взять из материалов") }
                        }
                        if (texts[i].trim() != f.default.trim()) {
                            TextButton(onClick = { texts[i] = f.default }) {
                                Text("Вернуть шаблонный текст")
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = { fields.forEachIndexed { i, f -> texts[i] = f.default } },
                modifier = Modifier.padding(start = 20.dp)
            ) { Text("Сбросить все правки") }

            Spacer(Modifier.height(40.dp))
        }
    }

    picking?.let { idx ->
        MaterialsPickDialog(
            disciplineId = input.disciplineId,
            query = fields[idx].query.orEmpty(),
            onDismiss = { picking = null },
            onInsert = { chunk ->
                val current = texts[idx].trimEnd()
                texts[idx] = if (current.isEmpty()) chunk else current + "\n" + chunk
                picking = null
            }
        )
    }
}

private fun fieldsOf(plan: LessonPlan): List<EditField> {
    val out = ArrayList<EditField>()
    val perLine = "Каждая строка — отдельный пункт"

    out += EditField(Generator.Keys.GOALS, "Цели занятия", "Нумерация проставится сама", plan.goals.join())
    out += EditField(Generator.Keys.PROVISION, "Материальное обеспечение", perLine, plan.provision.join())
    if (plan.safety.isNotEmpty()) {
        out += EditField(Generator.Keys.SAFETY, "Требования безопасности", perLine, plan.safety.join())
    }

    out += EditField(
        Generator.Keys.INTRO_CONTENT, "Вводная часть — содержание",
        "От первого лица: «Проверяю…», «Довожу…»", plan.intro.content.join()
    )
    out += EditField(
        Generator.Keys.INTRO_TRAINEE, "Вводная часть — действия обучаемых",
        perLine, plan.intro.trainee.join()
    )

    plan.questions.forEach { q ->
        out += EditField(
            Generator.Keys.qTitle(q.index), "Учебный вопрос № ${q.index}",
            "Наименование вопроса", q.title, singleLine = true
        )
        out += EditField(
            Generator.Keys.qContent(q.index), "Вопрос № ${q.index} — содержание",
            "Сюда вставляется материал из методички", q.content.join(),
            query = q.title + " " + plan.input.topic
        )
        out += EditField(
            Generator.Keys.qTrainee(q.index), "Вопрос № ${q.index} — действия обучаемых",
            perLine, q.trainee.join()
        )
    }

    out += EditField(
        Generator.Keys.OUTRO_CONTENT, "Заключительная часть — содержание",
        perLine, plan.outro.content.join()
    )
    out += EditField(
        Generator.Keys.OUTRO_TRAINEE, "Заключительная часть — действия обучаемых",
        perLine, plan.outro.trainee.join()
    )

    if (plan.control.isNotEmpty()) {
        out += EditField(
            Generator.Keys.CONTROL, "Контрольные вопросы", perLine, plan.control.join(),
            query = plan.input.topic
        )
    }
    return out
}

private fun List<String>.join() = joinToString("\n")

/**
 * Показывает куски приложенных методичек, где встречаются слова из учебного вопроса.
 * Отмеченные вставляются в поле. Что именно брать — решает руководитель занятия.
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
                result == null -> Text(
                    "Читаю материалы…",
                    style = MaterialTheme.typography.bodyMedium
                )

                result.fragments.isEmpty() -> Column {
                    Text(
                        "Подходящих кусков не нашлось.",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        "Найдено по словам из учебного вопроса. Проверьте текст перед вставкой.",
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
